"""
ElimuSMS cloud-tier server.

Single endpoint POST /v1/query that the J2ME MIDlet calls when on-device
classifier confidence falls below CompressedTinyML.CONFIDENCE_THRESHOLD.

Speaks the OpenAI Chat Completions protocol via the `openai` SDK, so any
OpenAI-compatible backend works. Pick the cheapest one that fits your study:

    Backend                LLM_BASE_URL                                            LLM_MODEL                  cost
    ----------------------------------------------------------------------------------------------------------------
    Gemini (free tier)     https://generativelanguage.googleapis.com/v1beta/openai/  gemini-1.5-flash         free / ~$0.00002 per query
    Groq (free tier)       https://api.groq.com/openai/v1                            llama-3.1-8b-instant     free
    OpenRouter             https://openrouter.ai/api/v1                              google/gemini-flash-1.5  ~$0.00002 per query
    Ollama (self-hosted)   http://localhost:11434/v1                                 llama3.2:3b              $0 marginal

The MIDlet contract:
  - Request:  POST /v1/query, application/x-www-form-urlencoded
              q=<question>&grade=6&lang=sw-ke
  - Response: text/plain; charset=utf-8, body is the answer (<= 4 KB).
              No JSON wrapper — SMSManager.readResponse decodes the body
              as UTF-8 and shows it directly.

Run:
    python3 -m venv .venv && source .venv/bin/activate
    pip install -r requirements.txt
    LLM_BASE_URL=https://generativelanguage.googleapis.com/v1beta/openai/ \\
    LLM_API_KEY=<your-gemini-key> \\
    LLM_MODEL=gemini-1.5-flash \\
    python server.py
"""

import logging
import os

from flask import Flask, Response, jsonify, request
from openai import OpenAI

from auto_retrain import retrain_from_corpus, start_scheduler
from classify import classify_intent
from fl import FLState, GLOBAL_SIZE, UPLOAD_SIZE
from privacy import PrivacyLedger

# ── Configuration (env vars) ────────────────────────────────────────────────
LLM_BASE_URL = os.environ.get("LLM_BASE_URL", "http://localhost:11434/v1")
LLM_API_KEY  = os.environ.get("LLM_API_KEY", "ollama")
LLM_MODEL    = os.environ.get("LLM_MODEL", "llama3.2:3b")
LLM_MAX_TOK  = int(os.environ.get("LLM_MAX_TOKENS", "200"))
LLM_TEMP     = float(os.environ.get("LLM_TEMPERATURE", "0.3"))
PORT         = int(os.environ.get("PORT", "8080"))
FL_STATE_DIR  = os.environ.get("FL_STATE_DIR", "./fl-state")
FL_AGGREGATOR = os.environ.get("FL_AGGREGATOR", "mean")  # mean|trimmed_mean|krum
AUTO_RETRAIN_INTERVAL_HOURS = float(
    os.environ.get("AUTO_RETRAIN_INTERVAL_HOURS", "0"))  # 0 disables scheduling
ADMIN_TOKEN = os.environ.get("ADMIN_TOKEN", "")  # protects /v1/admin/*

# Hard char cap on the response body — well under the MIDlet's 4 KB read
# limit even if the model emits 4-byte UTF-8 codepoints.
MAX_RESPONSE_CHARS = 1000

SYSTEM_PROMPT = (
    "You are ElimuSMS, an AI tutor for Kenyan Grade 6 students on the CBC "
    "(Competency-Based Curriculum). Subjects: mathematics, science (living "
    "things, human body, soil, simple machines, states of matter), and basic "
    "English grammar.\n\n"
    "Rules:\n"
    "1. Answer in 320 characters or fewer (about two SMS messages). "
    "Be brief but complete.\n"
    "2. Plain text only. No Markdown, asterisks, emojis, or special "
    "characters that feature phones cannot render.\n"
    "3. If the student greets in Swahili (Habari, Mambo, Jambo), reply with "
    "a Swahili greeting then explain in English. Otherwise reply in English.\n"
    "4. Use Kenyan context where natural (Ksh, mangoes, matatu, Nairobi).\n"
    "5. If the question is outside CBC Grade 6 STEM, reply exactly: "
    '"Hii si swali la masomo. Uliza kuhusu hesabu au sayansi."\n'
    '6. If unsure, say "Sina hakika" and suggest a related CBC topic.'
)

# ── App setup ───────────────────────────────────────────────────────────────
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)s %(message)s",
)
log = logging.getLogger("elimu-cloud")

client = OpenAI(base_url=LLM_BASE_URL, api_key=LLM_API_KEY)
fl_state = FLState(FL_STATE_DIR, aggregator=FL_AGGREGATOR)
privacy_ledger = PrivacyLedger(os.path.join(FL_STATE_DIR, "privacy.json"))
start_scheduler(fl_state, AUTO_RETRAIN_INTERVAL_HOURS)
app = Flask(__name__)


@app.route("/v1/query", methods=["POST"])
def query():
    question = (request.form.get("q") or "").strip()
    if not question:
        return _plain("Tafadhali andika swali. (Please type a question.)", 400)

    lang = request.form.get("lang", "?")
    ctx  = (request.form.get("ctx") or "").strip()
    log.info("query lang=%s q=%r ctx=%r", lang, question[:120], ctx[:120])

    # The MIDlet sends a pipe-separated list of the student's recent prior
    # questions in `ctx`. Surface them to the LLM as a brief preamble so
    # follow-ups like "and what about chlorophyll?" make sense.
    if ctx:
        user_content = (
            "Earlier in this conversation the student asked: "
            + ctx + ".\n\nNow they ask: " + question
        )
    else:
        user_content = question

    try:
        completion = client.chat.completions.create(
            model=LLM_MODEL,
            max_tokens=LLM_MAX_TOK,
            temperature=LLM_TEMP,
            messages=[
                {"role": "system", "content": SYSTEM_PROMPT},
                {"role": "user",   "content": user_content},
            ],
        )
        answer = (completion.choices[0].message.content or "").strip()
    except Exception:
        log.exception("LLM call failed")
        return _plain(
            "Server iko busy. Jaribu tena. (Server is busy. Try again.)",
            503,
        )

    # Char-based truncation guarantees valid UTF-8 (no mid-codepoint splits).
    if len(answer) > MAX_RESPONSE_CHARS:
        answer = answer[:MAX_RESPONSE_CHARS]

    # Cloud-arbitrated supervision signal: classify the question with the
    # deterministic keyword classifier and ship the label as an HTTP header.
    # The MIDlet reads X-Elimu-Intent and applies one SGD step on its
    # on-device classifier with confidence-weighted learning rate.
    intent_label = classify_intent(question)
    response = Response(answer, status=200,
                        mimetype="text/plain; charset=utf-8")
    response.headers["X-Elimu-Intent"] = intent_label
    return response


@app.route("/health", methods=["GET"])
def health():
    return _plain("ok", 200)


# ── Federated learning endpoints ────────────────────────────────────────────
# Lazy aggregation: FedAvg fires when N>=5 deltas have arrived OR a week has
# elapsed, whichever comes first. The MIDlet's offline-first design means
# devices may upload via either HTTPS piggy-back or SD-card sneakernet (where
# a teacher's collector device replays the raw blobs to /v1/fl/upload).

@app.route("/v1/fl/upload", methods=["POST"])
def fl_upload():
    blob = request.get_data()
    if len(blob) != UPLOAD_SIZE:
        return jsonify(error=f"expected {UPLOAD_SIZE} bytes, got {len(blob)}"), 400
    try:
        result = fl_state.submit_delta(blob)
    except ValueError as e:
        return jsonify(error=str(e)), 400
    # Account for the privacy budget consumed by this submission.
    device_id_hex = blob[1:17].hex()
    result["privacy"] = privacy_ledger.record(device_id_hex)
    return jsonify(result), 200


@app.route("/v1/fl/privacy", methods=["GET"])
def fl_privacy():
    """
    Returns either a single device's privacy budget (?device=<hex>) or the
    cohort-wide snapshot. The snapshot is the H5 evaluation artefact:
    cite the max cumulative epsilon as the worst-case per-student leakage.
    """
    did = request.args.get("device")
    if did:
        return jsonify(privacy_ledger.query(did)), 200
    return jsonify(privacy_ledger.snapshot()), 200


@app.route("/v1/fl/global", methods=["GET"])
def fl_global():
    blob = fl_state.get_global_blob()
    return Response(blob, status=200,
                    mimetype="application/octet-stream",
                    headers={"Content-Length": str(GLOBAL_SIZE)})


@app.route("/v1/fl/status", methods=["GET"])
def fl_status():
    return jsonify(fl_state.status()), 200


@app.route("/v1/fl/aggregate", methods=["POST"])
def fl_force_aggregate():
    """Manual trigger for the SD-card sneakernet collector script."""
    return jsonify(fl_state.force_aggregate()), 200


# ── Admin: force a server-side retrain from the corpus ─────────────────────
# After retrain, every device's next opportunistic /v1/fl/global pull
# returns the new weights. Disable in production by leaving ADMIN_TOKEN
# empty (the endpoint then refuses all requests).

@app.route("/v1/admin/retrain", methods=["POST"])
def admin_retrain():
    if not ADMIN_TOKEN:
        return jsonify(error="admin endpoints disabled (no ADMIN_TOKEN set)"), 403
    if request.headers.get("X-Admin-Token") != ADMIN_TOKEN:
        return jsonify(error="forbidden"), 403
    try:
        result = retrain_from_corpus(fl_state=fl_state)
    except Exception as e:
        log.exception("admin retrain failed")
        return jsonify(error=str(e)), 500
    return jsonify(result), 200


# ── SMS-FL inbound webhook ───────────────────────────────────────────────────
# The MIDlet chunks a 235-byte FL delta into binary SMS messages and sends
# them to a short-code address. The SMS gateway (Africa's Talking, Twilio,
# self-hosted Kannel) POSTs each inbound message to /sms/inbound. We
# reassemble chunks per sender per msg_id, then submit the recovered delta
# to the same FL pipeline used by /v1/fl/upload.
#
# Wire chunk format (binary, 4-byte header + payload, fits in one SMS):
#     [0]  protocol version (0x01)
#     [1]  chunk_index (0-based, < chunk_total)
#     [2]  chunk_total
#     [3]  msg_id (random 0..255; disambiguates concurrent uploads)
#     [4..] payload bytes (up to 136 of the 235-byte FL delta)

_sms_buffers: dict[tuple[str, int], dict[int, bytes]] = {}
_sms_buffers_lock = threading.Lock() if False else None  # avoid double-import

import threading as _threading
_sms_buffers_lock = _threading.Lock()


@app.route("/sms/inbound", methods=["POST"])
def sms_inbound():
    """
    Accept an inbound SMS payload from the SMS gateway.

    Two encodings of the binary chunk are supported in the POST body:
      - form field `data` with a hex-encoded byte string (preferred, simple).
      - form field `data_b64` with a base64-encoded byte string.
    The sender's phone number is read from form field `from`; this becomes
    the reassembly key alongside the msg_id field embedded in the chunk.
    """
    sender = (request.form.get("from") or "").strip()
    if not sender:
        return jsonify(error="missing 'from'"), 400
    raw_hex = request.form.get("data")
    raw_b64 = request.form.get("data_b64")
    try:
        if raw_hex:
            chunk = bytes.fromhex(raw_hex)
        elif raw_b64:
            import base64
            chunk = base64.b64decode(raw_b64)
        else:
            return jsonify(error="missing 'data' or 'data_b64'"), 400
    except Exception as e:
        return jsonify(error=f"decode failed: {e}"), 400

    if len(chunk) < 4:
        return jsonify(error="chunk too short"), 400
    if chunk[0] != 0x01:
        return jsonify(error=f"unsupported chunk protocol 0x{chunk[0]:02x}"), 400
    idx, total, msg_id = chunk[1], chunk[2], chunk[3]
    if total == 0 or idx >= total:
        return jsonify(error=f"invalid chunk indices idx={idx} total={total}"), 400

    payload_part = chunk[4:]
    key = (sender, msg_id)
    with _sms_buffers_lock:
        buf = _sms_buffers.setdefault(key, {})
        buf[idx] = payload_part
        complete = len(buf) == total
        if complete:
            assembled = b"".join(buf[i] for i in range(total))
            del _sms_buffers[key]
        else:
            assembled = None

    if not complete:
        return jsonify(status="buffered", chunks=len(buf), expected=total), 202

    if len(assembled) != UPLOAD_SIZE:
        return jsonify(error=(
            f"reassembled {len(assembled)} bytes, expected {UPLOAD_SIZE}"
        )), 400

    try:
        result = fl_state.submit_delta(assembled)
    except ValueError as e:
        return jsonify(error=str(e)), 400
    device_id_hex = assembled[1:17].hex()
    result["privacy"] = privacy_ledger.record(device_id_hex)
    result["sender_msisdn_hash"] = _hash_msisdn(sender)
    return jsonify(result), 200


def _hash_msisdn(msisdn: str) -> str:
    """Return a short non-reversible tag for logging the SMS sender."""
    import hashlib
    return hashlib.sha256(msisdn.encode("utf-8")).hexdigest()[:8]


def _plain(text, status):
    return Response(text, status=status, mimetype="text/plain; charset=utf-8")


if __name__ == "__main__":
    log.info(
        "starting on :%d backend=%s model=%s",
        PORT, LLM_BASE_URL, LLM_MODEL,
    )
    app.run(host="0.0.0.0", port=PORT)
