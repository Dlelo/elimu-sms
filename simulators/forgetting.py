"""
Catastrophic-forgetting evaluation for ElimuSMS on-device online learning.

Question: as a learner accumulates corrections, does the on-device
classifier forget what it knew at deployment time? The L2 anchor
regularisation in CompressedTinyML is the safeguard; this script measures
whether it actually works.

Method:
  1. Initialise a fresh model with a fixed seed (factory snapshot).
  2. Lock in the factory predictions on a held-out test set of random
     feature vectors as the "baseline truth."
  3. Apply N online corrections drawn uniformly from the 8 intents
     (worst case: no correlation with the factory predictions).
  4. After every K corrections, re-predict on the test set and measure
     baseline-prediction-retention = fraction unchanged from step 2.
  5. Repeat under two conditions: anchor on (lambda=0.01) and anchor off
     (lambda=0). Report the divergence as evidence that the safeguard
     works.

Output: CSV of (step, retention_with_anchor, retention_without_anchor)
plus a printed summary with bootstrap CIs over multiple seeds.
"""

from __future__ import annotations

import argparse
import csv
import sys
import os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), os.pardir, "cloud-server"))

import numpy as np

from tinyml_ref import TinyMLRef, FEATURE_SIZE
from metrics import bootstrap_ci


def random_features(n: int, rng: np.random.Generator) -> np.ndarray:
    """n binary 26-dim feature vectors with sparsity ~3 active per row."""
    X = (rng.random((n, FEATURE_SIZE)) < 0.12).astype(np.float32)
    return X


def run_one(
    n_corrections: int,
    snapshot_every: int,
    lr: float,
    use_anchor: bool,
    seed: int,
    test_size: int = 200,
) -> list[tuple[int, float]]:
    rng = np.random.default_rng(seed)
    model = TinyMLRef(seed=seed, use_anchor=use_anchor)
    test_X = random_features(test_size, rng)
    baseline = model.predict_batch(test_X).copy()

    snapshots = [(0, 1.0)]  # 100% retention before any correction
    train_X = random_features(n_corrections, rng)
    train_y = rng.integers(0, 8, size=n_corrections)

    for i in range(n_corrections):
        # SGD step: predict to populate cache, then learn
        model.predict(train_X[i])
        model.learn(int(train_y[i]), lr=lr)
        if (i + 1) % snapshot_every == 0:
            current = model.predict_batch(test_X)
            retention = float(np.mean(current == baseline))
            snapshots.append((i + 1, retention))
    return snapshots


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--corrections", type=int, default=200,
                   help="number of online correction steps")
    p.add_argument("--snapshot-every", type=int, default=10)
    p.add_argument("--lr", type=float, default=0.05)
    p.add_argument("--seeds", type=int, default=10,
                   help="number of independent runs for CIs")
    p.add_argument("--out", default="forgetting.csv")
    args = p.parse_args()

    runs_with    = []  # list of [(step, retention), ...] per seed
    runs_without = []
    for seed in range(args.seeds):
        runs_with.append(   run_one(args.corrections, args.snapshot_every,
                                    args.lr, use_anchor=True,  seed=seed))
        runs_without.append(run_one(args.corrections, args.snapshot_every,
                                    args.lr, use_anchor=False, seed=seed))

    # Aggregate into a CSV: step, mean_with, lo_with, hi_with, mean_without, lo_without, hi_without
    steps = [s for s, _ in runs_with[0]]
    rows = []
    for i, step in enumerate(steps):
        with_at_step    = [r[i][1] for r in runs_with]
        without_at_step = [r[i][1] for r in runs_without]
        ci_w = bootstrap_ci(with_at_step,    n_resamples=2000) if len(with_at_step)    > 1 else {"point": with_at_step[0],    "lo": with_at_step[0],    "hi": with_at_step[0]}
        ci_o = bootstrap_ci(without_at_step, n_resamples=2000) if len(without_at_step) > 1 else {"point": without_at_step[0], "lo": without_at_step[0], "hi": without_at_step[0]}
        rows.append((step, ci_w["point"], ci_w["lo"], ci_w["hi"],
                           ci_o["point"], ci_o["lo"], ci_o["hi"]))

    out_path = os.path.join(os.path.dirname(os.path.abspath(__file__)), args.out)
    with open(out_path, "w", newline="") as f:
        w = csv.writer(f)
        w.writerow(["step",
                    "retention_with_anchor",     "lo_with",    "hi_with",
                    "retention_without_anchor",  "lo_without", "hi_without"])
        w.writerows(rows)

    # Compact summary on stdout
    print(f"Forgetting curve over {args.seeds} seeds, {args.corrections} corrections")
    print(f"{'step':>5}  {'with anchor':>22}  {'without anchor':>22}  diff")
    print("-" * 75)
    for step, mw, lw, hw, mo, lo, ho in rows:
        diff = mw - mo
        print(f"{step:>5}  {mw:.3f} [{lw:.3f}, {hw:.3f}]  "
              f"{mo:.3f} [{lo:.3f}, {ho:.3f}]  {diff:+.3f}")
    print(f"\nWrote {out_path}")


if __name__ == "__main__":
    main()
