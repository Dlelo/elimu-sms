"""
Property-based fuzz tests for the FL wire format and aggregators.

Run with:
    cloud-server/.venv/bin/pytest cloud-server/tests/

These tests catch the failure modes a viva committee will probe:
  - "what if a malicious device sends a malformed blob?"
  - "what if an honest device's nibble round-trip drifts?"
  - "what if the aggregator's poisoning resistance is just a coincidence?"
"""
from __future__ import annotations

import os
import struct
import sys

import numpy as np
import pytest
from hypothesis import HealthCheck, given, settings, strategies as st

# Make `cloud-server/` importable when pytest is invoked from the repo root.
sys.path.insert(0, os.path.join(os.path.dirname(__file__), os.pardir))

from fl import (
    GLOBAL_SIZE, HEADER_BYTES, NIBBLE_BYTES, PROTOCOL_VERSION, TOTAL_PARAMS,
    UPLOAD_SIZE,
    aggregate, encode_global, nibble_decode, nibble_encode, parse_upload,
)
from classify import classify_intent, INTENT_LABELS


# ── Wire-format size invariants ─────────────────────────────────────────────

def test_constants_consistent():
    """The header + nibble bytes must equal the documented upload size."""
    assert HEADER_BYTES + NIBBLE_BYTES == UPLOAD_SIZE
    assert TOTAL_PARAMS == 428
    assert UPLOAD_SIZE == 235
    assert GLOBAL_SIZE == 1716


# ── parse_upload accepts well-formed blobs and rejects malformed ones ──────

def _make_blob(device_byte: int, anchor_round: int, delta: np.ndarray) -> bytes:
    return (bytes([PROTOCOL_VERSION]) + bytes([device_byte]) * 16
            + struct.pack(">I", anchor_round) + nibble_encode(delta))


def test_parse_upload_roundtrip():
    rng = np.random.default_rng(0)
    delta = rng.uniform(-1, 1, TOTAL_PARAMS).astype(np.float32)
    blob = _make_blob(0x42, 17, delta)
    did, ar, decoded = parse_upload(blob)
    assert did == bytes([0x42]) * 16
    assert ar == 17
    # Quantisation error per coord <= 1/15 = 0.0667
    assert np.max(np.abs(delta - decoded)) <= 0.075


@pytest.mark.parametrize("size", [0, 1, 100, UPLOAD_SIZE - 1, UPLOAD_SIZE + 1, 4096])
def test_parse_upload_rejects_wrong_size(size):
    """Any non-235-byte input must raise."""
    with pytest.raises(ValueError):
        parse_upload(b"\x00" * size)


def test_parse_upload_rejects_bad_protocol_version():
    rng = np.random.default_rng(0)
    delta = rng.uniform(-1, 1, TOTAL_PARAMS).astype(np.float32)
    blob = _make_blob(0x00, 0, delta)
    bad = b"\xFF" + blob[1:]
    with pytest.raises(ValueError):
        parse_upload(bad)


# ── Nibble codec property tests (hypothesis-driven) ────────────────────────

@given(arr=st.lists(
    st.floats(min_value=-1.0, max_value=1.0,
              allow_nan=False, allow_infinity=False, width=32),
    min_size=1, max_size=TOTAL_PARAMS,
))
@settings(max_examples=200, deadline=None,
          suppress_health_check=[HealthCheck.too_slow])
def test_nibble_roundtrip_within_quantisation_step(arr):
    """Encode/decode never errors > 1/15 per coordinate, anywhere in [-1,1]."""
    a = np.asarray(arr, dtype=np.float32)
    encoded = nibble_encode(a)
    decoded = nibble_decode(encoded, len(a))
    assert decoded.shape == a.shape
    err = np.abs(a - decoded)
    # 4-bit uniform quantiser: max error <= step/2 + numerical slack.
    assert err.max() <= 0.075


@given(n=st.integers(min_value=1, max_value=TOTAL_PARAMS))
def test_nibble_zero_input_decodes_near_zero(n):
    encoded = nibble_encode(np.zeros(n, dtype=np.float32))
    decoded = nibble_decode(encoded, n)
    # Quantising 0.0 lands on the nearest grid point (-1/15) ≈ -0.067
    assert np.max(np.abs(decoded)) <= 0.07


# ── encode_global wire-format invariants ───────────────────────────────────

def test_encode_global_fixed_size():
    arr = np.zeros(TOTAL_PARAMS, dtype=np.float32)
    blob = encode_global(arr, current_round=42)
    assert len(blob) == GLOBAL_SIZE
    # First 4 bytes = round number, big-endian.
    assert struct.unpack(">I", blob[:4])[0] == 42


def test_encode_global_round_trip():
    rng = np.random.default_rng(0)
    arr = rng.uniform(-1, 1, TOTAL_PARAMS).astype(np.float32)
    blob = encode_global(arr, 7)
    decoded = np.frombuffer(blob[4:], dtype=">f4")
    assert np.allclose(arr, decoded.astype(np.float32))


# ── Aggregator robustness sanity ───────────────────────────────────────────

def test_mean_is_compromised_by_byzantine():
    """Vanilla mean must be dragged off-target by a strong poisoner."""
    honest = np.full((8, 4), 0.05, dtype=np.float32)
    poison = np.full((2, 4), 50.0, dtype=np.float32)
    all_d  = np.vstack([honest, poison])
    agg = aggregate(all_d, mode="mean")
    assert agg.mean() > 1.0  # very off-target


def test_trimmed_mean_resists_byzantine():
    """trimmed_mean recovers the honest signal under 20% poisoning."""
    rng = np.random.default_rng(0)
    honest = rng.normal(0.05, 0.005, (10, 32)).astype(np.float32)
    poison = rng.normal(50.0, 1.0, (2, 32)).astype(np.float32)
    all_d  = np.vstack([honest, poison])
    agg = aggregate(all_d, mode="trimmed_mean")
    assert abs(agg.mean() - 0.05) < 0.02


def test_krum_resists_byzantine():
    rng = np.random.default_rng(0)
    honest = rng.normal(0.05, 0.005, (10, 32)).astype(np.float32)
    poison = rng.normal(50.0, 1.0, (2, 32)).astype(np.float32)
    all_d  = np.vstack([honest, poison])
    agg = aggregate(all_d, mode="krum")
    assert abs(agg.mean() - 0.05) < 0.02


def test_unknown_aggregator_rejected():
    with pytest.raises(ValueError):
        aggregate(np.zeros((5, 4)), mode="bogus_mode")


# ── Classifier stability ────────────────────────────────────────────────────

@pytest.mark.parametrize("q,expected", [
    ("what is photosynthesis", "science_help"),
    ("5 * 4",                  "math_help"),
    ("habari",                 "greeting"),
    ("kwaheri",                "farewell"),
    ("show my progress",       "progress"),
    ("what is a noun",         "english_help"),
    ("",                       "general_help"),
    ("bogus garbled question", "general_help"),
])
def test_classify_intent_known_cases(q, expected):
    assert classify_intent(q) == expected


@given(q=st.text(max_size=200))
@settings(max_examples=300, deadline=None,
          suppress_health_check=[HealthCheck.too_slow])
def test_classify_intent_always_returns_valid_label(q):
    """No matter what gibberish we throw at it, classify_intent never crashes
    and always returns one of the 8 valid labels."""
    label = classify_intent(q)
    assert label in INTENT_LABELS


# ── SMS chunk reassembly via /sms/inbound ───────────────────────────────────

def _make_chunked_sms(payload: bytes, msg_id: int, max_chunk_bytes: int = 136):
    """Split `payload` into binary SMS chunks following the wire format."""
    parts = [payload[i:i + max_chunk_bytes]
             for i in range(0, len(payload), max_chunk_bytes)]
    total = len(parts)
    return [bytes([0x01, idx, total, msg_id]) + p for idx, p in enumerate(parts)]


@pytest.fixture
def flask_client(tmp_path, monkeypatch):
    """Build a Flask test client with stubbed OpenAI + isolated FL state."""
    import sys, types
    mod = types.ModuleType("openai")

    class _MockChoice:
        class message:
            content = "test"
    class _MockComp:
        choices = [_MockChoice()]

    class OpenAI:
        def __init__(self, *a, **k): pass
        @property
        def chat(self): return self
        @property
        def completions(self): return self
        def create(self, **kw): return _MockComp()

    mod.OpenAI = OpenAI
    sys.modules["openai"] = mod

    monkeypatch.setenv("FL_STATE_DIR", str(tmp_path / "fl-state"))
    # Re-import to pick up env var
    if "server" in sys.modules:
        del sys.modules["server"]
    if "fl" in sys.modules:
        del sys.modules["fl"]
    import server
    return server.app.test_client()


def test_sms_inbound_reassembles_two_chunk_delta(flask_client):
    rng = np.random.default_rng(0)
    delta = rng.uniform(-0.1, 0.1, TOTAL_PARAMS).astype(np.float32)
    blob = (bytes([PROTOCOL_VERSION]) + bytes([0x77]) * 16
            + struct.pack(">I", 0) + nibble_encode(delta))
    assert len(blob) == UPLOAD_SIZE  # 235 bytes

    chunks = _make_chunked_sms(blob, msg_id=42)
    assert len(chunks) == 2  # 235 bytes splits into 2 chunks at 136 b/chunk

    # First chunk: should buffer (incomplete).
    r0 = flask_client.post("/sms/inbound", data={
        "from": "+254700111222",
        "data": chunks[0].hex(),
    })
    assert r0.status_code == 202
    assert r0.get_json()["status"] == "buffered"

    # Second chunk: should complete + submit + return privacy ledger.
    r1 = flask_client.post("/sms/inbound", data={
        "from": "+254700111222",
        "data": chunks[1].hex(),
    })
    assert r1.status_code == 200
    body = r1.get_json()
    assert body["pending"] >= 1 or body["aggregated"]
    assert "privacy" in body
    assert body["sender_msisdn_hash"] != "+254700111222"  # hashed, not raw


def test_sms_inbound_rejects_unknown_protocol(flask_client):
    bad = bytes([0xFF, 0, 1, 0]) + b"\x00" * 136
    r = flask_client.post("/sms/inbound", data={
        "from": "+254700111222", "data": bad.hex(),
    })
    assert r.status_code == 400
    assert "unsupported chunk protocol" in r.get_json()["error"]


def test_sms_inbound_rejects_short_chunk(flask_client):
    r = flask_client.post("/sms/inbound", data={
        "from": "+254700111222", "data": "0102",  # 2 bytes only
    })
    assert r.status_code == 400


# ── Server-side automatic retraining ────────────────────────────────────────

def test_auto_retrain_promotes_weights_to_fl_global(tmp_path):
    """Retraining should leave FL global non-trivial and bump the round."""
    import sys, types
    mod = types.ModuleType("openai")

    class _MockChoice:
        class message:
            content = "test"
    class _MockComp:
        choices = [_MockChoice()]

    class OpenAI:
        def __init__(self, *a, **k): pass
        @property
        def chat(self): return self
        @property
        def completions(self): return self
        def create(self, **kw): return _MockComp()

    mod.OpenAI = OpenAI
    sys.modules["openai"] = mod

    # Fresh FL state in an isolated tempdir.
    state_dir = tmp_path / "fl-state"
    if "fl" in sys.modules:
        del sys.modules["fl"]
    if "auto_retrain" in sys.modules:
        del sys.modules["auto_retrain"]
    from fl import FLState
    from auto_retrain import retrain_from_corpus

    state = FLState(str(state_dir))
    initial_round = state.round
    initial_global_norm = float(np.linalg.norm(state.global_model))

    # Tiny epoch budget keeps the test under a few seconds.
    result = retrain_from_corpus(fl_state=state, epochs=20)
    assert result["promoted"] is True
    assert result["new_fl_round"] == initial_round + 1
    assert state.round == initial_round + 1

    # Global model is non-zero after promotion.
    new_norm = float(np.linalg.norm(state.global_model))
    assert new_norm > initial_global_norm
    # Stayed in [-1, 1] (the nibble range expected by CompressedTinyML).
    assert state.global_model.min() >= -1.0
    assert state.global_model.max() <=  1.0
    # Trained accuracy is at least better than chance (1/8 ≈ 0.125).
    assert result["train_accuracy"] > 0.4
