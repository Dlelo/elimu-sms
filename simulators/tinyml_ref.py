"""
NumPy reference implementation of CompressedTinyML.

Mirrors the J2ME CompressedTinyML.java classifier so we can run
defensible offline experiments — forgetting curves, ablations,
poisoning sweeps — without firing up a feature-phone emulator.

Architecture matches the device exactly:
    F=26 features  ->  H=12 hidden  ->  O=8 intents
    ReLU + softmax + cross-entropy loss
    L2 anchor regularisation with strength LAMBDA = 0.01
"""

from __future__ import annotations

import numpy as np

FEATURE_SIZE = 26
HIDDEN_SIZE  = 12
OUTPUT_SIZE  = 8
LAMBDA       = 0.01


class TinyMLRef:
    """Reference classifier mirroring CompressedTinyML.java semantics."""

    def __init__(self, seed: int | None = 0,
                 use_anchor: bool = True,
                 lam: float = LAMBDA):
        self.use_anchor = use_anchor
        self.lam = lam
        rng = np.random.default_rng(seed)
        # Glorot-ish init in [-1, 1] to match the 4-bit-quantised weight range.
        self.w1 = rng.uniform(-1, 1, (HIDDEN_SIZE, FEATURE_SIZE)).astype(np.float32)
        self.w2 = rng.uniform(-1, 1, (OUTPUT_SIZE, HIDDEN_SIZE)).astype(np.float32)
        self.b1 = rng.uniform(-0.5, 0.5, HIDDEN_SIZE).astype(np.float32)
        self.b2 = rng.uniform(-0.5, 0.5, OUTPUT_SIZE).astype(np.float32)
        # Anchor: snapshot of factory defaults; never mutated unless caller
        # explicitly refreshes it. This is the FL anchor in the J2ME code.
        self._snapshot_anchor()
        # Forward-pass cache for backprop (matches lastZ1/lastA1/lastOutput).
        self._cache = None

    def _snapshot_anchor(self):
        self.aw1 = self.w1.copy()
        self.aw2 = self.w2.copy()
        self.ab1 = self.b1.copy()
        self.ab2 = self.b2.copy()

    # ── Forward pass ──────────────────────────────────────────────────────
    def predict(self, x: np.ndarray) -> tuple[int, float, np.ndarray]:
        """Returns (intent, confidence, full_softmax)."""
        z1 = self.w1 @ x + self.b1
        a1 = np.maximum(z1, 0)
        z2 = self.w2 @ a1 + self.b2
        # Numerically-stable softmax
        z2 = z2 - z2.max()
        e  = np.exp(z2)
        p  = e / e.sum()
        self._cache = (x.astype(np.float32), z1, a1, p)
        intent = int(np.argmax(p))
        return intent, float(p[intent]), p

    # ── SGD step with optional anchor regularisation ──────────────────────
    def learn(self, correct_intent: int, lr: float = 0.05):
        """One SGD step on cross-entropy loss + L2 pull toward anchor."""
        if self._cache is None:
            raise RuntimeError("call predict() before learn()")
        x, z1, a1, p = self._cache
        # Output-layer delta: dL/dz2 = p - one_hot(k*)
        d2 = p.copy()
        d2[correct_intent] -= 1.0
        # Hidden-layer delta
        d1 = (self.w2.T @ d2) * (z1 > 0)
        # Anchor pull (L2 toward last-synced global)
        anchor = float(self.lam) if self.use_anchor else 0.0
        # Update W2, b2
        grad_w2 = np.outer(d2, a1) + anchor * (self.w2 - self.aw2)
        grad_b2 =          d2      + anchor * (self.b2 - self.ab2)
        self.w2 -= lr * grad_w2
        self.b2 -= lr * grad_b2
        # Update W1, b1
        grad_w1 = np.outer(d1, x) + anchor * (self.w1 - self.aw1)
        grad_b1 =          d1     + anchor * (self.b1 - self.ab1)
        self.w1 -= lr * grad_w1
        self.b1 -= lr * grad_b1

    # ── Convenience ────────────────────────────────────────────────────────
    def predict_batch(self, X: np.ndarray) -> np.ndarray:
        """Predict intents for a batch of feature vectors. Does not cache."""
        Z1 = X @ self.w1.T + self.b1
        A1 = np.maximum(Z1, 0)
        Z2 = A1 @ self.w2.T + self.b2
        return np.argmax(Z2, axis=1)

    def flatten_weights(self) -> np.ndarray:
        """428-vector matching CompressedTinyML.computeDeltaFromFLAnchor() layout."""
        return np.concatenate([self.w1.ravel(), self.w2.ravel(),
                               self.b1.ravel(), self.b2.ravel()]).astype(np.float32)
