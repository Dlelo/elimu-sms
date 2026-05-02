# ElimuSMS cloud-tier server.
#
# Build: docker build -t elimu-cloud cloud-server/
# Run:   docker run --rm -p 8080:8080 \
#          -e LLM_BASE_URL="https://generativelanguage.googleapis.com/v1beta/openai/" \
#          -e LLM_API_KEY="<your-gemini-key>" \
#          -e LLM_MODEL="gemini-1.5-flash" \
#          -e FL_AGGREGATOR="trimmed_mean" \
#          -v elimu-fl-state:/state \
#          -e FL_STATE_DIR=/state \
#          elimu-cloud
#
# The fl-state volume persists privacy ledger + global model + pending deltas
# across container restarts so FL rounds and ε-budget accounting survive
# redeployments.

FROM python:3.11-slim

# Don't cache pip downloads in the image; --no-install-recommends keeps the
# image lean (~150 MB). curl is here for the HEALTHCHECK.
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Install Python deps first so the layer cache survives source edits.
COPY requirements.txt ./
RUN pip install --no-cache-dir -r requirements.txt

# Copy the rest of the server code.
COPY classify.py fl.py metrics.py privacy.py server.py ./

# Default port 8080; override with -e PORT=NNNN.
EXPOSE 8080

# Persist FL state outside the image.
ENV FL_STATE_DIR=/state
RUN mkdir -p /state

HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD curl -fsS "http://localhost:${PORT:-8080}/health" || exit 1

CMD ["python", "server.py"]
