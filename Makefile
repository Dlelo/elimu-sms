# ElimuSMS top-level Makefile — one command per major artefact.
#
# All targets are .PHONY: there are no real file outputs being tracked here,
# only the operations themselves. This file is the canonical entry point
# for reproducing every result in the thesis: a reviewer running `make all`
# from a fresh clone gets the same JAR, the same pytest report, the same
# forgetting curve, and the same compiled PDFs.

PYTHON       ?= python3
PIP          ?= $(PYTHON) -m pip
ANT          ?= ant
DOCKER       ?= docker
JAVAC        ?= javac
NPM          ?= npm

# cloud-server/.env (gitignored) holds LLM_API_KEY etc. We source it inside
# recipes via this shell snippet rather than `include`-ing it as a Makefile,
# because Make's include treats the file as Makefile syntax — fragile when
# values contain '=' or lines have stray whitespace from paste. Shell-level
# `set -a; source .env; set +a` loads any well-formed KEY=VALUE into the
# child process environment without parsing the values.
ENV_FILE     := cloud-server/.env
LOAD_ENV     := if [ -f $(ENV_FILE) ]; then set -a; . $(ENV_FILE); set +a; fi
SERVER_PORT  ?= 5051

# Portable port-kill macro. Respects PORT from .env if set, falls back to
# SERVER_PORT. macOS lsof doesn't support `xargs -r`, so we check $$PIDS
# explicitly. SIGTERM first; if the process ignores it (rare, but happens
# with wedged Flask), the second pass uses SIGKILL. No inline comments
# inside the shell recipe — Make's `define` mishandles `# ... \` lines.
define KILL_SERVER_PORT
$(LOAD_ENV); \
PORT=$${PORT:-$(SERVER_PORT)}; \
PIDS=$$(lsof -nP -iTCP:$$PORT -sTCP:LISTEN -t 2>/dev/null); \
if [ -n "$$PIDS" ]; then \
  echo "→ stopping stale server on port $$PORT (pid $$PIDS)"; \
  kill $$PIDS 2>/dev/null || true; \
  sleep 0.5; \
  PIDS2=$$(lsof -nP -iTCP:$$PORT -sTCP:LISTEN -t 2>/dev/null); \
  if [ -n "$$PIDS2" ]; then \
    echo "  forcing kill (pid $$PIDS2)"; \
    kill -9 $$PIDS2 2>/dev/null || true; \
    sleep 0.3; \
  fi; \
fi
endef

VENV         := cloud-server/.venv
VENV_PY      := $(VENV)/bin/python
VENV_PIP     := $(VENV)/bin/pip
VENV_PYTEST  := $(VENV)/bin/pytest

PAPER_DIR    := paper
PAPER_TEXS   := $(wildcard $(PAPER_DIR)/*.tex)

.PHONY: help all build test test-cloud test-j2me simulate paper presentation \
        server server-stop server-restart docker docker-build docker-run \
        venv clean realclean web-install web-dev web-build web-preview \
        synth-corpus

help:
	@echo "Common targets:"
	@echo "  make build         — compile the J2ME MIDlet (jar + jad)"
	@echo "  make test          — run all tests (J2ME compile + python pytest)"
	@echo "  make simulate      — run the catastrophic-forgetting simulator"
	@echo "  make synth-corpus  — LLM-generate new CBC training rows (needs LLM_API_KEY)"
	@echo "  make paper         — pdflatex all four papers"
	@echo "  make presentation  — pdflatex the 15-min Beamer deck"
	@echo "  make server        — run the cloud server locally (auto-frees port first)"
	@echo "  make server-stop   — kill any cloud server listening on port 5051"
	@echo "  make server-restart— stop then start (useful after editing .env)"
	@echo "  make docker-build  — build the cloud-server Docker image"
	@echo "  make docker-run    — run the image (env from .env if present)"
	@echo "  make web-install   — install elimu-web/ npm dependencies"
	@echo "  make web-dev       — Vite dev server (PWA hot reload at :5173)"
	@echo "  make web-build     — production build → elimu-web/dist/"
	@echo "  make web-preview   — preview the built bundle locally"
	@echo "  make all           — build + test + simulate + paper"
	@echo "  make clean         — remove build artefacts"
	@echo "  make realclean     — clean + delete fl-state + venv + paper PDFs"

# ── Reproducibility entry point ──────────────────────────────────────────────
all: build test simulate paper

# ── J2ME build ───────────────────────────────────────────────────────────────
build:
	$(ANT) clean build

# ── Python venv (auto-created on demand, re-syncs on requirements change) ───
# Sentinel file is updated whenever requirements.txt changes, so adding new
# deps to requirements.txt re-runs pip install on the next `make` invocation.
$(VENV)/.deps-installed: cloud-server/requirements.txt
	test -d $(VENV) || $(PYTHON) -m venv $(VENV)
	$(VENV_PIP) install --upgrade pip
	$(VENV_PIP) install -r cloud-server/requirements.txt
	$(VENV_PIP) install pytest hypothesis
	@touch $(VENV)/.deps-installed

venv: $(VENV)/.deps-installed

# ── Tests ────────────────────────────────────────────────────────────────────
test: test-j2me test-cloud

test-j2me:
	@echo "→ J2ME compile check (CLDC 1.1 / MIDP 2.0 + JSR-120 stubs)"
	@mkdir -p build/test-classes
	$(JAVAC) -source 1.8 -target 1.8 \
	  -bootclasspath lib/cldcapi11-2.0.2.jar \
	  -classpath    lib/midpapi20.jar:lib/wma20-stubs.jar \
	  -d build/test-classes \
	  src/com/elimu/*.java

test-cloud: $(VENV)/.deps-installed
	cd cloud-server && ../$(VENV_PYTEST) tests/ -v

# ── Simulators ───────────────────────────────────────────────────────────────
simulate: simulate-forgetting

simulate-forgetting: $(VENV)/.deps-installed
	$(VENV_PY) simulators/forgetting.py --corrections 200 --seeds 10
	@echo "→ output: simulators/forgetting.csv"

# ── Synthetic corpus expansion (LLM + self-validation, no teacher) ──────────
# Requires LLM_API_KEY in cloud-server/.env (free Gemini key:
# https://aistudio.google.com/app/apikey).
synth-corpus: $(VENV)/.deps-installed
	@$(LOAD_ENV); \
	if [ -z "$$LLM_API_KEY" ]; then \
	  echo "Set LLM_API_KEY in cloud-server/.env first."; \
	  echo "Get a free Gemini key at https://aistudio.google.com/app/apikey"; \
	  exit 2; \
	fi; \
	cd elimu-model && ../$(VENV_PY) synth_corpus.py --n-per-intent 50
	@echo "→ output: elimu-model/data/training_data_synth.csv"

# ── Cloud server ─────────────────────────────────────────────────────────────
# `make server` auto-frees the configured port first so a stale instance
# from a previous run can't shadow the current .env config (a footgun we
# hit during testing). Use `make server-stop` to just kill the server.
server: $(VENV)/.deps-installed
	@$(KILL_SERVER_PORT)
	@$(LOAD_ENV); cd cloud-server && ../$(VENV_PY) server.py

server-stop:
	@$(KILL_SERVER_PORT)
	@echo "✓ port free"

server-restart: server-stop server

# ── Docker ───────────────────────────────────────────────────────────────────
docker-build:
	$(DOCKER) build -t elimu-cloud cloud-server/

docker-run:
	@if [ -f cloud-server/.env ]; then ENVFILE="--env-file cloud-server/.env"; else ENVFILE=""; fi; \
	$(DOCKER) run --rm -p 5051:5051 -v elimu-fl-state:/state $$ENVFILE elimu-cloud

docker: docker-build

# ── Papers ───────────────────────────────────────────────────────────────────
paper: $(PAPER_TEXS)
	@for f in $(PAPER_TEXS); do \
	  echo "→ pdflatex $$f"; \
	  (cd $(PAPER_DIR) && pdflatex -interaction=nonstopmode -halt-on-error $$(basename $$f) > /dev/null) \
	    || echo "  ! $$f did not compile cleanly (this is OK if class is unavailable)"; \
	done

presentation:
	cd $(PAPER_DIR) && pdflatex -interaction=nonstopmode -halt-on-error presentation_stemedge3310.tex

# ── PWA / desktop client (elimu-web) ─────────────────────────────────────────

web-install:
	cd elimu-web && $(NPM) install

elimu-web/node_modules: elimu-web/package.json
	$(MAKE) web-install

web-dev: elimu-web/node_modules
	cd elimu-web && $(NPM) run dev

web-build: elimu-web/node_modules
	cd elimu-web && $(NPM) run build

web-preview: web-build
	cd elimu-web && $(NPM) run preview

# ── Cleanup ──────────────────────────────────────────────────────────────────
clean:
	rm -rf build/test-classes build/classes build/dist
	find . -name '__pycache__' -type d -exec rm -rf {} + 2>/dev/null || true
	find . -name '*.pyc' -delete

realclean: clean
	rm -rf $(VENV) cloud-server/fl-state
	rm -rf elimu-web/node_modules elimu-web/dist
	rm -f $(PAPER_DIR)/*.aux $(PAPER_DIR)/*.log $(PAPER_DIR)/*.out \
	      $(PAPER_DIR)/*.toc $(PAPER_DIR)/*.synctex.gz $(PAPER_DIR)/*.pdf
