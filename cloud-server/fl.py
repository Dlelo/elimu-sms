"""
Federated learning state and aggregation for ElimuSMS.

The MIDlet posts a 235-byte binary blob per round via POST /v1/fl/upload.
Aggregation is lazy: FedAvg runs when N>=5 deltas have arrived OR
AGGREGATION_INTERVAL has elapsed, whichever comes first. Devices fetch
the current global via GET /v1/fl/global.

Wire format (matches the 4-bit nibble layout of CompressedTinyML.java):

    offset  size   field
    ------------------------------------------------------------------
    0       1      protocol version (0x01)
    1       16     anonymous device id (raw bytes; client-generated UUID)
    17      4      anchor round (big-endian uint32)
    21      214    ceil(428/2) bytes — 4-bit nibbles in [-1, 1]
                   layout: w1[0..311], w2[0..95], b1[0..11], b2[0..7]
                   low nibble = even index, high nibble = odd index
                   nibble n decodes to (n - 7.5) / 7.5
    ------------------------------------------------------------------
    total   235

Global download wire format:

    offset  size   field
    ------------------------------------------------------------------
    0       4      current round (big-endian uint32)
    4       1712   428 float32 values, big-endian
                   layout matches static weights (w1, w2, b1, b2)
    ------------------------------------------------------------------
    total   1716

Persistence: state lives under FL_STATE_DIR (default ./fl-state):
    fl-state/global.npy        — np.float32 array of length 428
    fl-state/round.txt         — current round number
    fl-state/last_agg.txt      — ISO timestamp of last aggregation
    fl-state/deltas/<id>.npy   — most recent delta per device (for FedAvg)
"""

from __future__ import annotations

import logging
import os
import struct
import threading
import time
from pathlib import Path
from typing import Optional

import numpy as np

log = logging.getLogger("elimu-fl")

# ── Constants matching CompressedTinyML.java ─────────────────────────────────
HIDDEN_SIZE  = 12
FEATURE_SIZE = 26
OUTPUT_SIZE  = 8
W1_SIZE = HIDDEN_SIZE * FEATURE_SIZE   # 312
W2_SIZE = OUTPUT_SIZE  * HIDDEN_SIZE   # 96
B1_SIZE = HIDDEN_SIZE                  # 12
B2_SIZE = OUTPUT_SIZE                  # 8
TOTAL_PARAMS = W1_SIZE + W2_SIZE + B1_SIZE + B2_SIZE  # 428

PROTOCOL_VERSION = 0x01
DEVICE_ID_BYTES  = 16
HEADER_BYTES     = 1 + DEVICE_ID_BYTES + 4   # 21
NIBBLE_BYTES     = (TOTAL_PARAMS + 1) // 2   # 214
UPLOAD_SIZE      = HEADER_BYTES + NIBBLE_BYTES  # 235
GLOBAL_SIZE      = 4 + TOTAL_PARAMS * 4      # 1716

# ── Aggregation policy ───────────────────────────────────────────────────────
AGG_THRESHOLD_N        = 5
AGG_INTERVAL_SECONDS   = 7 * 24 * 60 * 60   # one week
STALE_ANCHOR_TOLERANCE = 3                  # accept deltas up to 3 rounds old

# Aggregator selection: env var FL_AGGREGATOR ∈ {mean, trimmed_mean, krum}
#   mean         — vanilla FedAvg; one byzantine device can dominate.
#   trimmed_mean — discard top-k and bottom-k extremes per coordinate.
#                  Tolerates up to k byzantine clients out of n.
#   krum         — pick the single delta closest to (n-f-2) others by L2
#                  distance. Tolerates up to f byzantine clients with
#                  n >= 2f+3 (Blanchard et al. 2017).
TRIMMED_K_FRACTION = 0.20  # discard 20% from each end of every coordinate
KRUM_F_FRACTION    = 0.20  # assume up to 20% of clients are byzantine


# ── Nibble (4-bit) codec — matches CompressedTinyML.getRawWeight() ───────────

def nibble_decode(buf: bytes, n: int) -> np.ndarray:
    """Decode `n` 4-bit values from `buf`. Each nibble n4 -> (n4 - 7.5) / 7.5."""
    out = np.empty(n, dtype=np.float32)
    for i in range(n):
        byte_idx = i // 2
        shift = (i % 2) * 4
        n4 = (buf[byte_idx] >> shift) & 0x0F
        out[i] = (n4 - 7.5) / 7.5
    return out


def nibble_encode(arr: np.ndarray) -> bytes:
    """Encode floats in [-1, 1] as 4-bit nibbles. Inverse of nibble_decode."""
    n = len(arr)
    buf = bytearray((n + 1) // 2)
    for i in range(n):
        v = float(arr[i])
        # clamp and quantize: v -> n4 in [0, 15]
        n4 = int(round(v * 7.5 + 7.5))
        n4 = 0 if n4 < 0 else (15 if n4 > 15 else n4)
        byte_idx = i // 2
        shift = (i % 2) * 4
        buf[byte_idx] |= (n4 & 0x0F) << shift
    return bytes(buf)


# ── Wire format ──────────────────────────────────────────────────────────────

def parse_upload(blob: bytes) -> tuple[bytes, int, np.ndarray]:
    """
    Parse a device upload. Returns (device_id, anchor_round, delta_float32).

    Raises ValueError on malformed input.
    """
    if len(blob) != UPLOAD_SIZE:
        raise ValueError(f"expected {UPLOAD_SIZE} bytes, got {len(blob)}")
    if blob[0] != PROTOCOL_VERSION:
        raise ValueError(f"unsupported protocol version 0x{blob[0]:02x}")
    device_id = blob[1:1 + DEVICE_ID_BYTES]
    anchor_round = struct.unpack(">I", blob[17:21])[0]
    delta = nibble_decode(blob[HEADER_BYTES:], TOTAL_PARAMS)
    return device_id, anchor_round, delta


def encode_global(global_arr: np.ndarray, current_round: int) -> bytes:
    """Pack the float32 global model + round number for /v1/fl/global."""
    if len(global_arr) != TOTAL_PARAMS:
        raise ValueError(f"global must have {TOTAL_PARAMS} params")
    head = struct.pack(">I", int(current_round))
    body = global_arr.astype(">f4").tobytes()
    return head + body


# ── FLState — thread-safe, disk-backed ───────────────────────────────────────

class FLState:
    """
    Holds the current global model and pending per-device deltas.
    All public methods are thread-safe. Mutations persist to disk
    immediately, so server restarts pick up where they left off.
    """

    def __init__(self, state_dir, aggregator: str = "mean"):
        self.dir = Path(state_dir)
        self.deltas_dir = self.dir / "deltas"
        self.deltas_dir.mkdir(parents=True, exist_ok=True)
        self._lock = threading.Lock()
        self.aggregator = aggregator

        self.global_path  = self.dir / "global.npy"
        self.round_path   = self.dir / "round.txt"
        self.last_agg_path = self.dir / "last_agg.txt"

        # Load or initialise global model
        if self.global_path.exists():
            self.global_model = np.load(self.global_path).astype(np.float32)
        else:
            self.global_model = np.zeros(TOTAL_PARAMS, dtype=np.float32)
            np.save(self.global_path, self.global_model)

        self.round = self._read_int(self.round_path, 0)
        self.last_agg = self._read_float(self.last_agg_path, time.time())

    # ── Public API ──────────────────────────────────────────────────────────

    def submit_delta(self, blob: bytes) -> dict:
        """Accept one device upload. May trigger lazy aggregation."""
        device_id, anchor_round, delta = parse_upload(blob)
        with self._lock:
            if self.round - anchor_round > STALE_ANCHOR_TOLERANCE:
                log.warning(
                    "delta from %s targets stale round %d (current %d); accepting anyway",
                    device_id.hex()[:8], anchor_round, self.round,
                )
            self._save_delta(device_id, delta)
            n_pending = len(list(self.deltas_dir.glob("*.npy")))
            triggered = self._maybe_aggregate_locked(n_pending)
            return {
                "round": self.round,
                "pending": n_pending if not triggered else 0,
                "aggregated": triggered,
            }

    def get_global_blob(self) -> bytes:
        """Return the wire-format global model + current round."""
        with self._lock:
            return encode_global(self.global_model, self.round)

    def force_aggregate(self) -> dict:
        """Manual trigger — useful for the SD-card sneakernet collector."""
        with self._lock:
            n_pending = len(list(self.deltas_dir.glob("*.npy")))
            if n_pending == 0:
                return {"round": self.round, "pending": 0, "aggregated": False}
            self._aggregate_locked()
            return {"round": self.round, "pending": 0, "aggregated": True}

    def status(self) -> dict:
        with self._lock:
            n_pending = len(list(self.deltas_dir.glob("*.npy")))
            return {
                "round": self.round,
                "pending": n_pending,
                "agg_threshold_n": AGG_THRESHOLD_N,
                "agg_interval_seconds": AGG_INTERVAL_SECONDS,
                "seconds_since_agg": int(time.time() - self.last_agg),
            }

    # ── Internals ───────────────────────────────────────────────────────────

    def _save_delta(self, device_id: bytes, delta: np.ndarray) -> None:
        # Most recent delta per device wins (1 file per device).
        path = self.deltas_dir / f"{device_id.hex()}.npy"
        np.save(path, delta.astype(np.float32))

    def _maybe_aggregate_locked(self, n_pending: int) -> bool:
        elapsed = time.time() - self.last_agg
        due_by_count = n_pending >= AGG_THRESHOLD_N
        due_by_time  = elapsed >= AGG_INTERVAL_SECONDS and n_pending > 0
        if due_by_count or due_by_time:
            self._aggregate_locked()
            return True
        return False

    def _aggregate_locked(self) -> None:
        delta_files = sorted(self.deltas_dir.glob("*.npy"))
        if not delta_files:
            return
        deltas = np.stack([np.load(p) for p in delta_files])
        agg_delta = aggregate(deltas, mode=self.aggregator).astype(np.float32)
        self.global_model = self.global_model + agg_delta
        # Clip to [-1, 1] so the next round's anchor still fits the nibble range.
        np.clip(self.global_model, -1.0, 1.0, out=self.global_model)
        self.round += 1
        self.last_agg = time.time()
        np.save(self.global_path, self.global_model)
        self._write(self.round_path, str(self.round))
        self._write(self.last_agg_path, str(self.last_agg))
        for p in delta_files:
            p.unlink()
        log.info("aggregated %d deltas → round %d", len(delta_files), self.round)

    @staticmethod
    def _read_int(path: Path, default: int) -> int:
        try:
            return int(path.read_text().strip())
        except (FileNotFoundError, ValueError):
            return default

    @staticmethod
    def _read_float(path: Path, default: float) -> float:
        try:
            return float(path.read_text().strip())
        except (FileNotFoundError, ValueError):
            return default

    @staticmethod
    def _write(path: Path, contents: str) -> None:
        path.write_text(contents)


# ── Robust aggregators ──────────────────────────────────────────────────────

def aggregate(deltas: np.ndarray, mode: str = "mean") -> np.ndarray:
    """
    Aggregate `deltas` (shape [n, p]) into a single 1-D update of length p.

    mode='mean'         — vanilla FedAvg.
    mode='trimmed_mean' — discard top-k and bottom-k per coordinate before
                          averaging. Robust against up to k byzantine clients.
    mode='krum'         — Krum (Blanchard et al. 2017): pick the single
                          delta whose sum-of-squared-distances to its
                          (n - f - 2) closest neighbours is smallest.
                          Robust to up to f byzantine clients with
                          n >= 2f + 3.
    """
    if mode == "mean" or deltas.shape[0] < 3:
        return deltas.mean(axis=0)
    if mode == "trimmed_mean":
        return _trimmed_mean(deltas)
    if mode == "krum":
        return _krum(deltas)
    raise ValueError(f"unknown aggregator: {mode}")


def _trimmed_mean(deltas: np.ndarray) -> np.ndarray:
    n = deltas.shape[0]
    k = max(1, int(n * TRIMMED_K_FRACTION))
    if 2 * k >= n:
        k = (n - 1) // 2
    sorted_per_coord = np.sort(deltas, axis=0)
    trimmed = sorted_per_coord[k : n - k]
    return trimmed.mean(axis=0)


def _krum(deltas: np.ndarray) -> np.ndarray:
    n = deltas.shape[0]
    f = max(1, int(n * KRUM_F_FRACTION))
    if n < 2 * f + 3:
        # Not enough clients for Krum's guarantee — fall back to trimmed mean.
        return _trimmed_mean(deltas)
    # Pairwise squared L2 distances.
    diff = deltas[:, None, :] - deltas[None, :, :]
    sqd  = np.einsum("ijk,ijk->ij", diff, diff)
    np.fill_diagonal(sqd, np.inf)
    # For each i, sum of distances to its (n - f - 2) closest others.
    k_neighbours = n - f - 2
    sorted_d = np.sort(sqd, axis=1)
    scores   = sorted_d[:, :k_neighbours].sum(axis=1)
    chosen   = int(np.argmin(scores))
    return deltas[chosen]
