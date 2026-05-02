"""
Differential-privacy budget accountant for ElimuSMS federated learning.

Each device contributes one delta per FL round under a Gaussian mechanism
with per-coordinate clipping (FederatedLearning.java: CLIP_PER_COORD=0.10,
NOISE_SIGMA=0.05). This module computes per-round (epsilon, delta) cost
and tracks per-device cumulative epsilon using both basic composition
(loose) and advanced composition (Dwork-Rothblum-Vadhan 2010, Theorem
3.20; tighter for many small rounds).

Why a separate ledger module:
  - Honest reporting beats marketing claims. We compute the *actual*
    epsilon induced by the deployed (sensitivity, sigma) pair, not the
    aspirational figure quoted in the proposal abstract.
  - The viva committee will ask "what is the cumulative epsilon after
    8 rounds?" — this module answers that with a citation-grade number.
  - Swappable accountant: the public API (record/query/snapshot) is
    independent of the composition theorem, so a future Renyi-DP /
    moments-accountant upgrade is a drop-in replacement.

Defaults match the J2ME-side constants and the proposal's framing.
"""

from __future__ import annotations

import json
import math
import threading
from pathlib import Path
from typing import Dict, Optional

# Defaults must match FederatedLearning.java and the paper.
DEFAULT_CLIP_PER_COORD = 0.10
DEFAULT_NOISE_SIGMA    = 0.05
DEFAULT_DELTA          = 1e-5
TOTAL_PARAMS           = 428  # CompressedTinyML.TOTAL_PARAMS


# ── Gaussian-mechanism epsilon ──────────────────────────────────────────────

def gaussian_epsilon(sensitivity: float, sigma: float, delta: float) -> float:
    """
    Per-application epsilon for the Gaussian mechanism at noise sigma.

    Dwork & Roth, Theorem 3.22: M(x) = f(x) + N(0, sigma^2) is (epsilon,
    delta)-DP iff sigma >= sensitivity * sqrt(2 ln(1.25/delta)) / epsilon.
    Solving for epsilon at fixed sigma:

        epsilon = sensitivity * sqrt(2 ln(1.25/delta)) / sigma

    This is tight only in the high-noise regime; for low-noise regimes
    the textbook bound understates the achievable privacy. We use this
    bound as a conservative upper bound on per-round epsilon.
    """
    if sigma <= 0:
        return float("inf")
    return sensitivity * math.sqrt(2.0 * math.log(1.25 / delta)) / sigma


def l2_sensitivity_per_coord_clip(clip: float, n_params: int) -> float:
    """
    L2 norm bound after per-coordinate L_inf clip C: sensitivity = C * sqrt(p).

    For our defaults (C=0.10, p=428): sensitivity ≈ 2.07.
    """
    return clip * math.sqrt(n_params)


# ── Per-device budget ───────────────────────────────────────────────────────

class PrivacyBudget:
    """Per-device privacy budget tracker."""

    def __init__(self, sensitivity: float, sigma: float, delta: float):
        self.sensitivity        = sensitivity
        self.sigma              = sigma
        self.delta              = delta
        self.epsilon_per_round  = gaussian_epsilon(sensitivity, sigma, delta)
        self.rounds_participated = 0

    def record_round(self) -> None:
        self.rounds_participated += 1

    def cumulative_epsilon_basic(self) -> float:
        """Basic composition: epsilon_total = N * epsilon_per_round."""
        return self.rounds_participated * self.epsilon_per_round

    def cumulative_epsilon_advanced(self) -> float:
        """
        Advanced composition (Dwork-Rothblum-Vadhan 2010, Theorem 3.20)
        clamped to never exceed the basic-composition bound.

        For T applications of (eps, delta)-DP, the composed mechanism is
        (eps', T*delta + delta')-DP where:

            eps' = sqrt(2T ln(1/delta')) * eps + T * eps * (e^eps - 1)

        Advanced composition is tighter when eps is small (the regime it
        was designed for). At large eps the (e^eps - 1) term explodes,
        making the formula loose — so we always return min(basic,
        advanced). Both are valid upper bounds; pick whichever is tighter.
        """
        T = self.rounds_participated
        if T == 0:
            return 0.0
        eps = self.epsilon_per_round
        basic = T * eps
        # Guard against overflow when eps is large.
        if eps > 50.0:
            return basic
        delta_prime = self.delta
        try:
            advanced = (math.sqrt(2 * T * math.log(1.0 / delta_prime)) * eps
                        + T * eps * (math.exp(eps) - 1.0))
        except OverflowError:
            return basic
        return min(basic, advanced)

    def to_dict(self) -> dict:
        return {
            "rounds":                       self.rounds_participated,
            "sensitivity":                  self.sensitivity,
            "sigma":                        self.sigma,
            "delta":                        self.delta,
            "epsilon_per_round":            self.epsilon_per_round,
            "cumulative_epsilon_basic":     self.cumulative_epsilon_basic(),
            "cumulative_epsilon_advanced":  self.cumulative_epsilon_advanced(),
        }

    @classmethod
    def from_dict(cls, d: dict) -> "PrivacyBudget":
        b = cls(d["sensitivity"], d["sigma"], d["delta"])
        b.rounds_participated = int(d.get("rounds", 0))
        return b


# ── Disk-backed ledger ──────────────────────────────────────────────────────

class PrivacyLedger:
    """
    Per-device privacy budget ledger, persisted to a single JSON file.

    Thread-safe: all mutating operations hold a lock while writing to disk,
    so concurrent FL uploads from different devices don't race.
    """

    def __init__(self, path,
                 sensitivity: Optional[float] = None,
                 sigma: float = DEFAULT_NOISE_SIGMA,
                 delta: float = DEFAULT_DELTA):
        self.path = Path(path)
        self.path.parent.mkdir(parents=True, exist_ok=True)
        self.default_sensitivity = (
            sensitivity if sensitivity is not None
            else l2_sensitivity_per_coord_clip(DEFAULT_CLIP_PER_COORD, TOTAL_PARAMS)
        )
        self.default_sigma = sigma
        self.default_delta = delta
        self.budgets: Dict[str, PrivacyBudget] = {}
        self._lock = threading.Lock()
        self._load()

    def _load(self) -> None:
        if not self.path.exists():
            return
        try:
            data = json.loads(self.path.read_text())
            for did, b in data.items():
                self.budgets[did] = PrivacyBudget.from_dict(b)
        except Exception:
            # corrupt file → start fresh; keep old file aside for forensics
            try:
                self.path.rename(str(self.path) + ".corrupt")
            except Exception:
                pass

    def _save_locked(self) -> None:
        data = {did: b.to_dict() for did, b in self.budgets.items()}
        self.path.write_text(json.dumps(data, indent=2, sort_keys=True))

    def record(self, device_id_hex: str) -> dict:
        with self._lock:
            b = self.budgets.get(device_id_hex)
            if b is None:
                b = PrivacyBudget(
                    self.default_sensitivity,
                    self.default_sigma,
                    self.default_delta,
                )
                self.budgets[device_id_hex] = b
            b.record_round()
            self._save_locked()
            return b.to_dict()

    def query(self, device_id_hex: str) -> dict:
        with self._lock:
            b = self.budgets.get(device_id_hex)
            if b is None:
                return {
                    "rounds": 0,
                    "cumulative_epsilon_basic": 0.0,
                    "cumulative_epsilon_advanced": 0.0,
                }
            return b.to_dict()

    def snapshot(self) -> dict:
        """Cohort-wide summary suitable for the H_5 evaluation chapter."""
        with self._lock:
            per_device = {did: b.to_dict() for did, b in self.budgets.items()}
            if not per_device:
                return {
                    "n_devices": 0,
                    "max_epsilon_basic": 0.0,
                    "max_epsilon_advanced": 0.0,
                    "max_rounds": 0,
                    "per_device": {},
                }
            eps_basic    = [d["cumulative_epsilon_basic"]    for d in per_device.values()]
            eps_advanced = [d["cumulative_epsilon_advanced"] for d in per_device.values()]
            rounds       = [d["rounds"]                       for d in per_device.values()]
            return {
                "n_devices":            len(per_device),
                "max_epsilon_basic":    max(eps_basic),
                "max_epsilon_advanced": max(eps_advanced),
                "max_rounds":           max(rounds),
                "per_device":           per_device,
            }
