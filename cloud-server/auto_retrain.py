"""
Server-side automatic retraining of the on-device classifier.

This is the production analogue of `elimu-model/train_and_pack.py`. It runs
the exact same training pipeline (same feature extractor, same NumPy MLP,
same quantisation) inside the cloud server, then promotes the resulting
weights into the FL global model — i.e. the same float32 blob that
GET /v1/fl/global serves to devices. Every device's next opportunistic
global pull therefore returns the freshly-trained weights, and applies
them via CompressedTinyML.applyGlobalUpdate(). No manual paste step, no
rebuild.

Triggers (any subset):
  - Manual: POST /v1/admin/retrain                   (callable with curl)
  - Scheduled: AUTO_RETRAIN_INTERVAL_HOURS env var   (e.g., 24)
  - Reactive: hit retrain whenever the privacy ledger sees N submissions
              since the last retrain (set by AUTO_RETRAIN_AFTER_N_DELTAS)

Honest limits:
  - Retraining uses the static corpus shipped with the repo. Federated
    deltas accumulated server-side are *not* mixed into supervised
    retraining (they're already noise-protected; treating them as ground
    truth would defeat that). FedAvg via /v1/fl/upload remains the
    mechanism for incorporating learner corrections.
  - Retraining produces a new global; older devices still need to pull
    it. There is no push-to-device mechanism for fully-offline phones.
"""

from __future__ import annotations

import csv
import logging
import os
import sys
import threading
import time
from typing import Optional

import numpy as np

# Make the elimu-model training pipeline importable from the cloud server.
_REPO_ROOT     = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
_ELIMU_MODEL   = os.path.join(_REPO_ROOT, "elimu-model")
if _ELIMU_MODEL not in sys.path:
    sys.path.insert(0, _ELIMU_MODEL)

from train_and_pack import (  # type: ignore
    extract_features, train_mlp, predict_batch, quantise_to_nibbles,
)

log = logging.getLogger("elimu-retrain")

DEFAULT_CSV = os.path.join(_ELIMU_MODEL, "data", "training_data_enhanced.csv")
TOTAL_PARAMS = 428  # 12*26 + 8*12 + 12 + 8


# ── Core retrain ────────────────────────────────────────────────────────────

def retrain_from_corpus(
    csv_path: str = DEFAULT_CSV,
    fl_state=None,                # FLState; updates global_model + round
    epochs: int = 4000,
    lr: float = 0.05,
    seed: int = 42,
) -> dict:
    """
    Train a fresh model from the labelled corpus and (if fl_state is given)
    promote it into the FL global. Returns a JSON-friendly summary.
    """
    rows = _load_corpus(csv_path)
    if not rows:
        raise RuntimeError(f"corpus is empty: {csv_path}")

    X = np.array([extract_features(t) for t, _ in rows], dtype=np.float32)
    y = np.array([lbl for _, lbl in rows], dtype=np.int64)
    log.info("retrain: %d samples, %d features, %d classes",
             X.shape[0], X.shape[1], len(set(y.tolist())))

    W1, b1, W2, b2 = train_mlp(X, y, epochs=epochs, lr=lr, seed=seed)
    train_acc = float(np.mean(predict_batch(X, W1, b1, W2, b2) == y))

    # Scale uniformly to [-1, 1] (matches train_and_pack quantisation).
    scale = max(
        float(np.abs(W1).max()), float(np.abs(W2).max()),
        float(np.abs(b1).max()), float(np.abs(b2).max()),
        1e-6,
    )
    if scale > 1.0:
        W1 = W1 / scale; b1 = b1 / scale
        W2 = W2 / scale; b2 = b2 / scale

    # Verify the quantised round-trip still classifies sensibly.
    quant_acc = float(np.mean(_quantised_predict(X, W1, b1, W2, b2) == y))

    # Pack into the 428-float layout used by FLState.global_model
    # (W1 row-major, then W2 row-major, then b1, then b2 — same order as
    # CompressedTinyML.computeDeltaFromFLAnchor on the device side).
    flat = np.concatenate([
        W1.flatten(), W2.flatten(), b1, b2,
    ]).astype(np.float32)
    assert flat.size == TOTAL_PARAMS, f"unexpected shape {flat.size}"

    promoted = False
    new_round = None
    if fl_state is not None:
        with fl_state._lock:
            fl_state.global_model = flat
            fl_state.round += 1
            np.clip(fl_state.global_model, -1.0, 1.0, out=fl_state.global_model)
            np.save(fl_state.global_path, fl_state.global_model)
            fl_state._write(fl_state.round_path, str(fl_state.round))
            new_round = fl_state.round
            promoted = True
        log.info("retrain promoted to FL round %d (train_acc=%.3f, quant_acc=%.3f)",
                 new_round, train_acc, quant_acc)

    return {
        "n_samples":     len(rows),
        "train_accuracy": train_acc,
        "quantised_accuracy": quant_acc,
        "promoted":      promoted,
        "new_fl_round":  new_round,
    }


# ── Scheduled background loop ───────────────────────────────────────────────

def start_scheduler(fl_state, interval_hours: float, csv_path: str = DEFAULT_CSV) -> None:
    """Spawn a daemon thread that calls retrain_from_corpus every N hours."""
    if interval_hours <= 0:
        return

    def loop():
        log.info("retrain scheduler started (every %.1fh)", interval_hours)
        while True:
            try:
                time.sleep(interval_hours * 3600)
                retrain_from_corpus(csv_path=csv_path, fl_state=fl_state)
            except Exception:
                log.exception("scheduled retrain failed; will retry next interval")

    t = threading.Thread(target=loop, name="auto-retrain", daemon=True)
    t.start()


# ── Helpers ─────────────────────────────────────────────────────────────────

def _load_corpus(path: str) -> list[tuple[str, int]]:
    rows = []
    with open(path, newline="") as fp:
        reader = csv.DictReader(fp)
        for row in reader:
            text  = (row.get("text") or "").strip()
            label = row.get("intent")
            if not text or label is None:
                continue
            try:
                rows.append((text, int(label)))
            except ValueError:
                continue
    return rows


def _quantised_predict(X, W1, b1, W2, b2):
    """Apply quantise-then-decode to mimic the on-device 4-bit weights."""
    def round_trip(arr):
        nib = quantise_to_nibbles(arr)
        return ((nib - 7.5) / 7.5).astype(np.float32).reshape(arr.shape)
    return predict_batch(X, round_trip(W1), round_trip(b1),
                         round_trip(W2), round_trip(b2))
