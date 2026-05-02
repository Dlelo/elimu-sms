"""
Statistical helpers for ElimuSMS evaluation chapters.

Every accuracy / energy / KPSEA number reported in the thesis should be a
range, not a point estimate. This module provides:

    bootstrap_ci(values, alpha=0.05, n_resamples=10_000)
        Non-parametric bootstrap percentile CI for any statistic over a
        sample. Returns (point_estimate, lo, hi).

    paired_bootstrap_diff(a, b, alpha=0.05, n_resamples=10_000)
        Bootstrap CI for the mean difference between paired samples.
        Used for "intervention vs control" KPSEA score deltas (H_3).

    mcnemar(a_correct, b_correct, alpha=0.05, continuity=True)
        McNemar's test for paired classifier agreement on the same items.
        Used to compare TinyML vs rule-based baseline (H_1).
        Returns (chi2, p_value, significant_at_alpha).

    classification_metrics(y_true, y_pred, n_classes)
        Per-class precision/recall/F1 with macro and weighted aggregates.

All functions return plain Python dicts/tuples so results pickle cleanly
into CSV / JSON for thesis tables.
"""

from __future__ import annotations

import math
import random
from typing import Iterable, Sequence

import numpy as np


# ── Bootstrap CIs ────────────────────────────────────────────────────────────

def bootstrap_ci(
    values: Sequence[float],
    alpha: float = 0.05,
    n_resamples: int = 10_000,
    statistic=np.mean,
    seed: int | None = 0,
) -> dict:
    """
    Non-parametric bootstrap percentile CI for a statistic over `values`.

    Returns a dict: {point, lo, hi, n_resamples, alpha, statistic_name}.
    Use for accuracy, energy-per-inference, KPSEA score, or any other
    summary statistic where the underlying distribution is unknown.
    """
    arr = np.asarray(values, dtype=float)
    if arr.size == 0:
        raise ValueError("bootstrap_ci needs at least one observation")
    rng = np.random.default_rng(seed)
    n = arr.size
    point = float(statistic(arr))
    if n == 1:
        return {"point": point, "lo": point, "hi": point,
                "n_resamples": 0, "alpha": alpha,
                "statistic_name": getattr(statistic, "__name__", "stat")}
    samples = np.empty(n_resamples, dtype=float)
    for i in range(n_resamples):
        idx = rng.integers(0, n, size=n)
        samples[i] = statistic(arr[idx])
    lo = float(np.percentile(samples, 100 * alpha / 2))
    hi = float(np.percentile(samples, 100 * (1 - alpha / 2)))
    return {
        "point": point, "lo": lo, "hi": hi,
        "n_resamples": n_resamples, "alpha": alpha,
        "statistic_name": getattr(statistic, "__name__", "stat"),
    }


def paired_bootstrap_diff(
    a: Sequence[float],
    b: Sequence[float],
    alpha: float = 0.05,
    n_resamples: int = 10_000,
    seed: int | None = 0,
) -> dict:
    """
    Bootstrap CI for E[a - b] under paired sampling (each i is one subject).

    Use for pre-vs-post KPSEA scores or intervention-vs-control deltas
    where each pair shares a subject (the H_3 test).

    Returns: {mean_diff, lo, hi, n_pairs, alpha}.
    """
    arr_a = np.asarray(a, dtype=float)
    arr_b = np.asarray(b, dtype=float)
    if arr_a.shape != arr_b.shape:
        raise ValueError(f"shape mismatch: {arr_a.shape} vs {arr_b.shape}")
    diffs = arr_a - arr_b
    ci = bootstrap_ci(diffs, alpha=alpha, n_resamples=n_resamples,
                      statistic=np.mean, seed=seed)
    return {
        "mean_diff": ci["point"],
        "lo":         ci["lo"],
        "hi":         ci["hi"],
        "n_pairs":    int(arr_a.size),
        "alpha":      alpha,
    }


# ── McNemar's test ──────────────────────────────────────────────────────────

def mcnemar(
    a_correct: Sequence[bool],
    b_correct: Sequence[bool],
    alpha: float = 0.05,
    continuity: bool = True,
) -> dict:
    """
    McNemar's test for paired classifier agreement on the same items.

    a_correct[i] and b_correct[i] are booleans for whether classifier A
    and B got item i right. Tests H_0: classifiers have equal error
    rates against H_a: rates differ.

    With continuity correction (Edwards 1948) the statistic is
    (|b - c| - 1)^2 / (b + c) where b = A_right & B_wrong, c = A_wrong &
    B_right. Without correction: (b - c)^2 / (b + c). Compares against
    chi^2_1 critical value at alpha (3.841 at alpha=0.05).
    """
    if len(a_correct) != len(b_correct):
        raise ValueError(f"length mismatch: {len(a_correct)} vs {len(b_correct)}")
    a = np.asarray(a_correct, dtype=bool)
    b = np.asarray(b_correct, dtype=bool)
    b_wins =  int(np.sum( a & ~b))  # A right, B wrong
    c_wins =  int(np.sum(~a &  b))  # A wrong, B right
    discordant = b_wins + c_wins
    if discordant == 0:
        return {"chi2": 0.0, "p_value": 1.0, "significant": False,
                "b": b_wins, "c": c_wins, "alpha": alpha}
    if continuity:
        chi2 = (abs(b_wins - c_wins) - 1) ** 2 / discordant
    else:
        chi2 = (b_wins - c_wins) ** 2 / discordant
    # Approximation: p-value via chi^2 with 1 df = erfc(sqrt(chi2/2))
    p_value = math.erfc(math.sqrt(chi2 / 2.0))
    crit_005 = 3.841
    return {
        "chi2":         float(chi2),
        "p_value":      float(p_value),
        "significant":  chi2 > crit_005 if alpha == 0.05 else p_value < alpha,
        "b":            b_wins,
        "c":            c_wins,
        "alpha":        alpha,
        "continuity":   continuity,
    }


# ── Classification metrics with macro/weighted aggregates ───────────────────

def classification_metrics(
    y_true: Sequence[int],
    y_pred: Sequence[int],
    n_classes: int,
) -> dict:
    """
    Per-class precision/recall/F1 plus macro and support-weighted averages.

    Returns a dict with per-class arrays and aggregate scalars. Use for
    intent-classifier evaluation against the keyword cloud-arbitrator.
    """
    yt = np.asarray(y_true, dtype=int)
    yp = np.asarray(y_pred, dtype=int)
    if yt.shape != yp.shape:
        raise ValueError(f"shape mismatch: {yt.shape} vs {yp.shape}")
    accuracy = float(np.mean(yt == yp))
    precision = np.zeros(n_classes, dtype=float)
    recall    = np.zeros(n_classes, dtype=float)
    f1        = np.zeros(n_classes, dtype=float)
    support   = np.zeros(n_classes, dtype=int)
    for k in range(n_classes):
        tp = int(np.sum((yt == k) & (yp == k)))
        fp = int(np.sum((yt != k) & (yp == k)))
        fn = int(np.sum((yt == k) & (yp != k)))
        support[k] = int(np.sum(yt == k))
        precision[k] = tp / (tp + fp) if (tp + fp) > 0 else 0.0
        recall[k]    = tp / (tp + fn) if (tp + fn) > 0 else 0.0
        if precision[k] + recall[k] > 0:
            f1[k] = 2 * precision[k] * recall[k] / (precision[k] + recall[k])
    total_support = int(support.sum())
    macro_f1     = float(f1.mean()) if n_classes else 0.0
    weighted_f1  = float(np.sum(f1 * support) / total_support) if total_support else 0.0
    return {
        "accuracy":     accuracy,
        "precision":    precision.tolist(),
        "recall":       recall.tolist(),
        "f1":           f1.tolist(),
        "support":      support.tolist(),
        "macro_f1":     macro_f1,
        "weighted_f1":  weighted_f1,
        "n_classes":    n_classes,
        "n_samples":    total_support,
    }
