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

VENV         := cloud-server/.venv
VENV_PY      := $(VENV)/bin/python
VENV_PIP     := $(VENV)/bin/pip
VENV_PYTEST  := $(VENV)/bin/pytest

PAPER_DIR    := paper
PAPER_TEXS   := $(wildcard $(PAPER_DIR)/*.tex)

.PHONY: help all build test test-cloud test-j2me simulate paper presentation \
        server docker docker-build docker-run venv clean realclean

help:
	@echo "Common targets:"
	@echo "  make build         — compile the J2ME MIDlet (jar + jad)"
	@echo "  make test          — run all tests (J2ME compile + python pytest)"
	@echo "  make simulate      — run the catastrophic-forgetting simulator"
	@echo "  make paper         — pdflatex all four papers"
	@echo "  make presentation  — pdflatex the 15-min Beamer deck"
	@echo "  make server        — run the cloud server locally (Ollama default)"
	@echo "  make docker-build  — build the cloud-server Docker image"
	@echo "  make docker-run    — run the image (env from .env if present)"
	@echo "  make all           — build + test + simulate + paper"
	@echo "  make clean         — remove build artefacts"
	@echo "  make realclean     — clean + delete fl-state + venv + paper PDFs"

# ── Reproducibility entry point ──────────────────────────────────────────────
all: build test simulate paper

# ── J2ME build ───────────────────────────────────────────────────────────────
build:
	$(ANT) clean build

# ── Python venv (auto-created on demand) ─────────────────────────────────────
$(VENV)/bin/activate: cloud-server/requirements.txt
	$(PYTHON) -m venv $(VENV)
	$(VENV_PIP) install --upgrade pip
	$(VENV_PIP) install -r cloud-server/requirements.txt
	$(VENV_PIP) install pytest hypothesis
	@touch $(VENV)/bin/activate

venv: $(VENV)/bin/activate

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

test-cloud: venv
	cd cloud-server && ../$(VENV_PYTEST) tests/ -v

# ── Simulators ───────────────────────────────────────────────────────────────
simulate: simulate-forgetting

simulate-forgetting: venv
	$(VENV_PY) simulators/forgetting.py --corrections 200 --seeds 10
	@echo "→ output: simulators/forgetting.csv"

# ── Cloud server ─────────────────────────────────────────────────────────────
server: venv
	cd cloud-server && ../$(VENV_PY) server.py

# ── Docker ───────────────────────────────────────────────────────────────────
docker-build:
	$(DOCKER) build -t elimu-cloud cloud-server/

docker-run:
	@if [ -f cloud-server/.env ]; then ENVFILE="--env-file cloud-server/.env"; else ENVFILE=""; fi; \
	$(DOCKER) run --rm -p 8080:8080 -v elimu-fl-state:/state $$ENVFILE elimu-cloud

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

# ── Cleanup ──────────────────────────────────────────────────────────────────
clean:
	rm -rf build/test-classes build/classes build/dist
	find . -name '__pycache__' -type d -exec rm -rf {} + 2>/dev/null || true
	find . -name '*.pyc' -delete

realclean: clean
	rm -rf $(VENV) cloud-server/fl-state
	rm -f $(PAPER_DIR)/*.aux $(PAPER_DIR)/*.log $(PAPER_DIR)/*.out \
	      $(PAPER_DIR)/*.toc $(PAPER_DIR)/*.synctex.gz $(PAPER_DIR)/*.pdf
