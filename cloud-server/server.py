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

from fl import FLState, GLOBAL_SIZE, UPLOAD_SIZE

# ── Configuration (env vars) ────────────────────────────────────────────────
LLM_BASE_URL = os.environ.get("LLM_BASE_URL", "http://localhost:11434/v1")
LLM_API_KEY  = os.environ.get("LLM_API_KEY", "ollama")
LLM_MODEL    = os.environ.get("LLM_MODEL", "llama3.2:3b")
LLM_MAX_TOK  = int(os.environ.get("LLM_MAX_TOKENS", "200"))
LLM_TEMP     = float(os.environ.get("LLM_TEMPERATURE", "0.3"))
PORT         = int(os.environ.get("PORT", "8080"))
FL_STATE_DIR = os.environ.get("FL_STATE_DIR", "./fl-state")

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
fl_state = FLState(FL_STATE_DIR)
app = Flask(__name__)


@app.route("/v1/query", methods=["POST"])
def query():
    question = (request.form.get("q") or "").strip()
    if not question:
        return _plain("Tafadhali andika swali. (Please type a question.)", 400)

    lang = request.form.get("lang", "?")
    log.info("query lang=%s q=%r", lang, question[:120])

    try:
        completion = client.chat.completions.create(
            model=LLM_MODEL,
            max_tokens=LLM_MAX_TOK,
            temperature=LLM_TEMP,
            messages=[
                {"role": "system", "content": SYSTEM_PROMPT},
                {"role": "user",   "content": question},
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
    return _plain(answer, 200)


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
    return jsonify(result), 200


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


def _plain(text, status):
    return Response(text, status=status, mimetype="text/plain; charset=utf-8")


if __name__ == "__main__":
    log.info(
        "starting on :%d backend=%s model=%s",
        PORT, LLM_BASE_URL, LLM_MODEL,
    )
    app.run(host="0.0.0.0", port=PORT)
