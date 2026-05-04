# ElimuSMS

An SMS-based AI educational assistant that runs on Java ME (J2ME) feature
phones. Targets Kenya CBC Grade 6 STEM learners in low-resource settings.

## What it does (plain-English)

ElimuSMS is a little study app that runs on cheap basic phones — the
Nokia-3310 kind that most Kenyan families already own — and it teaches
Grade 6 students math and science (the topics they sit for at the end
of primary school under the CBC curriculum). It works **without
internet**, which is the whole point: most of the kids who need it
don't have smartphones or reliable data.

A student types a question — *"what is photosynthesis?"*, *"5*4"*,
*"habari"* — and the phone reads it. A tiny AI model living inside
the app (smaller than a single photo) figures out what topic the
student is really asking about and sends back a short,
curriculum-matched answer that fits in an SMS-sized message.

Three things make it more than just a Q&A app:

1. **One tap to dig deeper.** Every answer comes with two buttons:
   *OK* (done — back to the menu) and *More*. Tapping *More* once
   tries the bigger cloud AI for a richer answer if the phone has
   any signal; if it doesn't, the phone shows the broader
   curriculum context for the same topic. Tapping *More* again walks
   the student outward through related concepts in the same CBC
   topic web — photosynthesis → chlorophyll → leaves → respiration —
   so each tap surfaces a sibling idea instead of just repeating
   the first answer. The student is never asked to *grade* the AI;
   they're only ever asked to ask for *more help*.
2. **It uses a bigger AI as a backup, only when needed.** If the
   tiny on-phone AI isn't sure, and the phone happens to have signal,
   the question is forwarded to a smarter AI on a server and the
   answer comes back. If there's no signal, the phone falls back to
   a built-in answer. The student never has to think about whether
   they're online.
3. **All the phones quietly improve a shared AI together — without
   anyone's questions ever leaving their phone.** Each phone tracks
   small adjustments to its own copy of the AI as it's used (for
   example, when the cloud helped with a question, the phone learns
   from how the cloud labelled it). It scrambles those adjustments
   with mathematical privacy noise — so they reveal nothing about
   any specific student — and ships them to a server when convenient,
   either over data or physically by SD card carried by a teacher.
   The server averages everyone's scrambled adjustments into a
   slightly smarter shared model and sends it back to every phone.
   A child in school A helps a child in school B get a better tutor,
   but no one's actual questions ever leave their handset.

In one sentence: an offline AI tutor that fits on basic phones, so
kids in rural Kenya get personalised study help that adapts to them,
doesn't need internet, and protects their privacy by design.

## Technical highlights

- **Target JAR size:** ~45 KB (down from ~300 KB)
- **Compression:** 85% reduction via 4-bit quantization + 70% pruning
- **On-device intent classifier:** 26-feature MLP. Eight output slots —
  seven actively trained (math, science, quiz, general help, progress,
  greeting, farewell); the eighth (`english_help`) is retired in favour
  of a **STEM-only focus** and retained for wire-format backward
  compatibility. Quantised test accuracy ~72% on the STEM-only +
  synth-augmented corpus (was ~63.5% before).
- **Cloud fallback:** low-confidence queries are dispatched to a generative
  AI backend over HTTP; the device falls back to local templates if the
  cloud is unreachable
- **Federated learning (offline-first):** noisy weight deltas (DP ε=0.3)
  enqueued on app exit, flushed opportunistically over HTTPS or via
  SD-card sneakernet — raw learner queries never leave the device

## Repository layout

```
src/com/elimu/        Java ME MIDlet sources (CLDC 1.1 / MIDP 2.0)
elimu-web/            PWA / desktop client (Vite + TypeScript)
elimu-model/          Python training pipeline (see elimu-model/README.txt)
cloud-server/         Flask cloud-tier server for low-confidence queries
simulators/           Forgetting curve + reference NumPy classifier
lib/                  CLDC 1.1, MIDP 2.0, MicroEmulator jars
build.xml             Ant build (compiles JAR + generates JAD)
run.sh                Build + launch on Oracle Java ME SDK emulator
run-micro.sh          Build + launch on MicroEmulator
resources/            On-device runtime data
```

## Prerequisites

- **JDK 8 or newer** (the bootclasspath is pinned to CLDC 1.1, so any modern
  JDK that can target source/target 1.8 works — Zulu 8 is the reference
  configuration). *Required for the J2ME MIDlet only.*
- **Apache Ant** (any 1.10.x). *Required for the J2ME MIDlet only.*
- **One emulator**, either:
  - Oracle Java ME SDK 8.3 (closer to a real handset), or
  - MicroEmulator 2.0.4 (faster to set up, cross-platform).
- **Python 3.11+** for the cloud server, simulators, and training pipeline.
- **Node.js 18+ and npm 9+** for the PWA / desktop client (`elimu-web/`).
  Verified with Node 20.19 + npm 10.8.

### Install on macOS

```bash
brew install --cask zulu8     # JDK
brew install ant              # build tool
# MicroEmulator: download microemulator-2.0.4.zip from
#   https://github.com/barteo/microemu/releases
# Java ME SDK: download from
#   https://www.oracle.com/java/technologies/javame-sdk-downloads.html
```

### Install on Linux

```bash
sudo apt install openjdk-8-jdk ant
# Emulator: same downloads as above
```

## Build

```bash
ant clean build
```

Outputs:
- `build/dist/ElimuSMS.jar` — the MIDlet bundle
- `build/dist/ElimuSMS.jad` — descriptor referenced by emulators and phones

A successful build prints `Build completed successfully!` and the JAR should
be under ~50 KB.

## Run locally — quick start

After a successful build, the fastest way to see the system working
end-to-end on your laptop is:

```bash
# (1) Build the MIDlet
ant clean build

# (2) Start the cloud server in a separate terminal
make server          # or: cd cloud-server && python server.py

# (3) Launch the emulator
java -jar lib/microemulator-2.0.4.jar build/dist/ElimuSMS.jad
```

Open the emulator and tap *Ask Question*. High-confidence queries (e.g.
*"what is photosynthesis?"*) answer instantly from the on-device
classifier; low-confidence ones tap the cloud server you started in
step (2).

To exercise the full cloud-fallback path you need to point the MIDlet
at your local server. **One-time JAD edit:** open
`build/dist/ElimuSMS.jad` and change the URL line to:

```
Elimu-CloudURL: http://localhost:5051/v1/query
```

The default ships pointing at `api.elimu-ai.org`, which is not yet
hosted. Restart the emulator after editing.

If you only want to test the on-device classifier, OK/More UX,
quizzes, or *This Week* report, you can skip step (2) entirely — the
emulator works fine offline.

For just running checks without launching the UI:
```bash
make test            # J2ME compile + 30 property tests
make simulate        # catastrophic-forgetting curve → simulators/forgetting.csv
```

### Triggering server-side retraining (Loop 3)

The cloud server can retrain the on-device classifier from the
labelled corpus and push the new weights to all devices through the
existing federated-learning pull channel. No manual paste step, no
rebuild — devices receive the retrained weights on their next
opportunistic `GET /v1/fl/global`.

Start the server with an admin token (and optionally a scheduled
retrain cadence):

```bash
ADMIN_TOKEN="some-secret" \
AUTO_RETRAIN_INTERVAL_HOURS=24 \
make server
```

Then trigger a retrain manually:

```bash
curl -X POST -H "X-Admin-Token: some-secret" \
     http://localhost:5051/v1/admin/retrain
# {"n_samples": 1074, "train_accuracy": 0.74, "quantised_accuracy": 0.63,
#  "promoted": true, "new_fl_round": 4}
```

Confirm the device sees the new global on its next pull:

```bash
curl -s http://localhost:5051/v1/fl/global | head -c 4 | xxd
# 00000000: 0000 0004                                ....
# (round 4 — the retrain bumped the round counter)
```

Append new examples to `elimu-model/data/training_data_enhanced.csv`,
re-trigger, and the cohort's classifier improves without any
device-side change.

## Run as a PWA / desktop client

The `elimu-web/` directory contains a TypeScript port of the same
on-device classifier — same 26-feature extractor, same 4-bit nibble
weight bytes, same SGD step + anchor regularisation as
`CompressedTinyML.java`. It runs in any modern browser and installs
as a Progressive Web App (PWA) on Android, iOS Safari, macOS,
Windows, and Linux. The same federated endpoints serve it: phones,
tablets, and laptops all contribute to one shared global model.

### Quick start (verified end-to-end)

```bash
make web-install      # one-time: npm install (Node 18+ required)
make server &         # cloud server in the background (optional)
make web-dev          # Vite dev server at http://localhost:5173
                      # — proxies /v1 and /sms to the Flask cloud server
```

Open `http://localhost:5173/` in any browser. Type a question — the
on-device classifier answers instantly from cached weights. Tap
*More* to escalate to the cloud LLM if `make server` is running.

### Production build

```bash
make web-build        # → elimu-web/dist/   (static site, ~17 KB)
make web-preview      # local preview at http://localhost:4173
```

The build pipeline runs `tsc --noEmit && vite build` so all
TypeScript types are checked. Verified bundle size (production):

| Artefact | Size | Gzipped |
|---|---|---|
| `index-*.js` | 14.7 KB | 5.9 KB |
| `index-*.css` | 2.0 KB | 0.8 KB |
| `index.html` + `manifest` | ~1.4 KB | — |
| Service-worker precache | 18.2 KB total | — |

### Deployment

`elimu-web/dist/` is a self-contained static site. Drop it on:

- **GitHub Pages** — push `dist/` contents to a `gh-pages` branch.
- **Netlify / Vercel / Cloudflare Pages** — point at the repo, set
  build command `cd elimu-web && npm install && npm run build`,
  publish directory `elimu-web/dist`.
- **Behind nginx / Apache** — copy the contents to the docroot and
  configure the same `/v1` and `/sms` reverse-proxy as the Vite
  dev server does to the Flask cloud server on `:5051`.
- **`python -m http.server`** from inside `elimu-web/dist/` for a
  zero-config local serve.

Once installed (the address bar will show an *Install* action in
Chrome/Edge/Safari), the service worker caches the UI shell + the
on-device classifier so the app works fully offline. The classifier
loads factory weights from a bundled hex blob byte-identical to
`CompressedTinyML.COMPRESSED_WEIGHTS`; subsequent
`/v1/fl/global` pulls overlay the latest cohort-trained weights via
`applyGlobalUpdate`, exactly as the J2ME MIDlet does. SMS-FL is not
available in browsers — desktop and PWA clients upload via HTTPS
only, falling back to local-only operation if the cloud is
unreachable.

### What the PWA shares with the J2ME MIDlet

| Component | J2ME side | Web side |
|---|---|---|
| Feature extractor (26 binary features) | `CompressedTinyML.extractFeatures` | `elimu-web/src/features.ts` |
| Forward pass + SGD step | `CompressedTinyML.predict / learn` | `elimu-web/src/classifier.ts` |
| 4-bit nibble weight bytes | `COMPRESSED_WEIGHTS` array | `weights.ts` (same hex string) |
| Cloud query | `SMSManager.sendToCloudAI` | `cloud.askCloud` |
| FL upload (235-byte payload) | `FederatedLearning.encodeUpload` | `cloud.encodeUpload` |
| FL global download (1.7 KB) | `FederatedLearning.pullNow` | `cloud.pullGlobal` |
| Persistent storage | RecordStore (RMS) | IndexedDB |
| OK / More UX | `ElimuSMSMidlet.showResponse` | `ui.appendBot` |

## Run on an emulator

### Option A — MicroEmulator (recommended for quick iteration)

```bash
java -jar lib/microemulator-2.0.4.jar build/dist/ElimuSMS.jad
```

Or use the helper script (downloads location is hard-coded — edit if needed):

```bash
chmod +x run-micro.sh
./run-micro.sh
```

### Option B — Oracle Java ME SDK

```bash
export WTK_HOME=/path/to/javame-sdk-8.3
"$WTK_HOME/bin/emulator" \
    -Xdevice:DefaultCldcPhone1 \
    -Xdescriptor:build/dist/ElimuSMS.jad
```

Or:

```bash
chmod +x run.sh
./run.sh
```

## Deploy to a real feature phone

ElimuSMS is a standard MIDP 2.0 MIDlet, so any phone with Java ME support
(most Nokia S40/S60, KaiOS, and Symbian devices) can install it. There are
three common transfer methods — pick whichever your phone supports.

### 0. Pre-flight

1. Run `ant clean build` and confirm `build/dist/ElimuSMS.jar` and
   `build/dist/ElimuSMS.jad` exist.
2. Open `build/dist/ElimuSMS.jad` in a text editor and verify the line
   `MIDlet-Jar-URL: ElimuSMS.jar` — for sideload installs, the JAR must
   sit next to the JAD on the phone's filesystem.
3. Confirm the phone supports Java ME (Settings → About / Apps → Java).

### Method 1 — USB or SD card sideload (simplest)

1. Connect the phone via USB in **Mass Storage** / **File Transfer** mode,
   or remove the SD card and mount it on your machine.
2. Copy **both** `ElimuSMS.jar` and `ElimuSMS.jad` from `build/dist/` into a
   folder on the phone, e.g. `/Phone/Java/` or `/SD/Apps/`.
3. Safely eject; on the phone, open the file manager, navigate to the
   folder, and select `ElimuSMS.jad` (not the .jar). The phone will
   prompt to install the MIDlet.
4. Accept the prompts. The app appears under **Applications** /
   **My Apps** / **Java**.

### Method 2 — Bluetooth

1. Pair the phone with your computer.
2. Send `ElimuSMS.jad` first, then `ElimuSMS.jar` to the phone via
   Bluetooth file transfer.
3. On the phone, open the received `ElimuSMS.jad` to trigger install.

### Method 3 — Over-the-air (OTA) install

Useful if the phone has data but no USB/Bluetooth pairing. You host the
JAR + JAD on any web server and point the phone's browser at the JAD URL.

1. Edit `build/dist/ElimuSMS.jad` and change the URL line to the absolute
   HTTP URL where the JAR will live, e.g.:

   ```
   MIDlet-Jar-URL: http://example.com/elimu/ElimuSMS.jar
   ```

2. Upload **both files** to that location. The web server **must** serve
   them with the correct MIME types or many phones will refuse the install:

   ```
   .jad  →  text/vnd.sun.j2me.app-descriptor
   .jar  →  application/java-archive
   ```

   For Apache, add to `.htaccess`:

   ```
   AddType text/vnd.sun.j2me.app-descriptor .jad
   AddType application/java-archive .jar
   ```

3. On the phone, open the browser and visit the JAD URL directly
   (e.g. `http://example.com/elimu/ElimuSMS.jad`). The phone fetches the
   descriptor, then prompts to download and install the JAR.

### Phone-side first-run notes

- **Network permission prompts:** unsigned MIDlets are placed in the
  *untrusted* security domain, so the phone will prompt the user each time
  the cloud fallback opens an HTTP connection. To suppress prompts you must
  sign the JAR with a code-signing certificate (Verisign / Thawte for
  Symbian, manufacturer-specific for Nokia S40/KaiOS) — generally not
  practical for a research deployment, so plan for the per-connection
  prompt in your UX evaluation.
- **Cloud endpoint:** `SMSManager.CLOUD_API` is hard-coded to
  `http://api.elimu-ai.org/v1/query`. The server is **not yet deployed**,
  so the cloud-fallback path currently always falls back to the offline
  template. Either stand up that endpoint or change `CLOUD_API` to your
  own server before recording cloud-tier evaluation runs.
- **Persistent storage:** the on-device online-learning weights are saved
  to RMS under store name `ElimuWeights`. To reset to factory weights,
  delete app data from the phone's app manager.

## Cloud-tier server (low-confidence fallback)

The MIDlet posts low-confidence queries to a small HTTP server that
forwards them to a generative model. Quick start with a free-tier backend:

```bash
cd cloud-server
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt

# Pick one backend (all OpenAI-compatible — same client, different env vars):

# (a) Gemini 1.5 Flash — free tier, no card needed
LLM_BASE_URL="https://generativelanguage.googleapis.com/v1beta/openai/" \
LLM_API_KEY="<your-gemini-key>" \
LLM_MODEL="gemini-1.5-flash" \
python server.py

# (b) Self-hosted Ollama — $0 marginal cost, fully reproducible
ollama pull llama3.2:3b
LLM_BASE_URL="http://localhost:11434/v1" \
LLM_API_KEY="ollama" \
LLM_MODEL="llama3.2:3b" \
python server.py
```

Then point the MIDlet at your server. The cloud URL is read from the JAD
attribute `Elimu-CloudURL` at startup, so you can swap backends per
deployment **without rebuilding** — just edit `build/dist/ElimuSMS.jad`
before sideload / OTA hosting:

```
Elimu-CloudURL: http://<your-host>:5051/v1/query
```

Defaults to `http://api.elimu-ai.org/v1/query` (set in `build.xml`) if the
attribute is missing.

This makes the cloud backend a deployment-time variable — useful for
A/B comparing Gemini Flash, Groq Llama, and self-hosted Ollama across
study cohorts using the same JAR.

See `cloud-server/server.py` for the full backend table (Groq, OpenRouter,
Azure, etc.) and the SMS-friendly system prompt.

## Retraining the on-device classifier

The committed `CompressedTinyML.java` already contains a working set of
quantized weights, so you do **not** need to retrain to build and run the
app. To regenerate weights from updated data, see
[`elimu-model/README.txt`](elimu-model/README.txt) for the full Python
pipeline (build dataset → train → quantize → paste byte arrays back into
`CompressedTinyML.java`).

## Synthetic corpus expansion (LLM)

The original 1,074-row training corpus is heavily class-imbalanced
(8:1 between the largest and smallest intent). A teacher-review loop
to label new examples doesn't scale, so the project ships
`elimu-model/synth_corpus.py`: it generates new labelled rows with a
free-tier LLM and validates each row against the deterministic
keyword classifier already used by the cloud server. Two independent
labellers must agree before a row enters the training set; this is
the no-human gate.

**One-time setup (LLM credentials):**

```bash
cp cloud-server/.env.example cloud-server/.env
# Open cloud-server/.env and paste your free Gemini key on the
# LLM_API_KEY= line. Get one at https://aistudio.google.com/app/apikey
# (no credit card needed). The .env file is gitignored.
```

**Run the expansion:**

```bash
make synth-corpus      # 50 candidates per intent, ~3 min, $0
```

You'll see a per-intent acceptance table:

```
intent              generated   accepted     rate
math_help                  50         41      82%
science_help               50         37      74%
english_help                -          -       -    (retired; STEM-only focus)
quiz                       50         38      76%
general_help               50         47      94%
progress                   50         28      56%
greeting                   50         44      88%
farewell                   50         45      90%
TOTAL                     350        280      80%
```

Acceptance rates vary by intent — `science_help` and the social
intents (greeting/farewell) have rich keyword vocabularies so the
gate accepts most candidates; `progress` has a sparser keyword set
so its rate is lower. The `english_help` slot is retired (the model
focuses on STEM mathematics and science) and the synth script skips
it by default. Top-up an under-represented intent with:

```bash
cd elimu-model && \
  ../cloud-server/.venv/bin/python synth_corpus.py \
  --intents 5,6,7 --append --n-per-intent 100
```

`--append` keeps existing rows; `--intents` targets specific labels.

**Combine and retrain:**

```bash
cat elimu-model/data/training_data_enhanced.csv \
    elimu-model/data/training_data_synth.csv \
    > elimu-model/data/training_data_combined.csv

cd elimu-model && \
  ../cloud-server/.venv/bin/python train_and_pack.py \
  --csv data/training_data_combined.csv
```

The retrain output prints new `COMPRESSED_WEIGHTS` and
`COMPRESSED_BIASES` byte arrays for `CompressedTinyML.java` and the
matching hex strings for `elimu-web/src/weights.ts`. Paste those in
and the on-device model now reflects the expanded corpus.

**Cost confirmation:** with Gemini 1.5 Flash on the free tier the
whole pipeline (corpus expansion + retrain) costs $0. Expect
1.5-flash to handle 50 candidates × 7 active intents in one minute under
the 15 RPM free-tier limit; 2.5-flash has a much tighter quota and
is more likely to hit a 429. The script retries with exponential
backoff (30→45→67→100 s), so transient rate limits self-recover.

**Honest limit (worth noting in the thesis):** the keyword classifier
is the validation gate, and the on-device classifier is trained
against keyword-classified supervision, so the gate is partially
self-confirming — the synthesised corpus boosts *quantity* and
*intra-keyword-vocabulary diversity* but cannot teach the model
keywords it doesn't already know. Genuine vocabulary expansion
requires either an LLM-as-judge with a different prompt, or
periodic human review.

## Troubleshooting

- **`ant clean build` fails with `cannot access StringBuilder`:** you used
  `String + String` somewhere; CLDC 1.1 has only `StringBuffer`. Replace
  with `new StringBuffer(...).append(...).toString()`.
- **Build succeeds but JAR is much larger than 45 KB:** check that
  `quantize_pack.py` actually 4-bit-packed the new weights; a full-precision
  fallback adds ~30 KB.
- **Emulator launches but app crashes on startup:** check stdout for the
  `loadModel`/`loadSavedWeights` log line; a corrupt RMS record from a
  previous run will be reported there. Delete the emulator's RMS data
  (`~/.microemulator` or the Java ME SDK appdb directory).
- **Phone refuses to install JAD over OTA:** verify both MIME types on the
  web server (see Method 3). 90% of OTA install failures are MIME-related.
- **Phone installs but cloud queries always fail:** `api.elimu-ai.org` is
  not yet hosted. See "Cloud endpoint" note above.
