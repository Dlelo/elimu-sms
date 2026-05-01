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
- **On-device intent classifier:** 26-feature MLP, 8 intents, ~91.79% test accuracy
- **Cloud fallback:** low-confidence queries are dispatched to a generative
  AI backend over HTTP; the device falls back to local templates if the
  cloud is unreachable
- **Federated learning (offline-first):** noisy weight deltas (DP ε=0.3)
  enqueued on app exit, flushed opportunistically over HTTPS or via
  SD-card sneakernet — raw learner queries never leave the device

## Repository layout

```
src/com/elimu/        Java ME MIDlet sources (CLDC 1.1 / MIDP 2.0)
elimu-model/          Python training pipeline (see elimu-model/README.txt)
cloud-server/         Flask cloud-tier server for low-confidence queries
lib/                  CLDC 1.1, MIDP 2.0, MicroEmulator jars
build.xml             Ant build (compiles JAR + generates JAD)
run.sh                Build + launch on Oracle Java ME SDK emulator
run-micro.sh          Build + launch on MicroEmulator
resources/            On-device runtime data
```

## Prerequisites

- **JDK 8 or newer** (the bootclasspath is pinned to CLDC 1.1, so any modern
  JDK that can target source/target 1.8 works — Zulu 8 is the reference
  configuration)
- **Apache Ant** (any 1.10.x)
- **One emulator**, either:
  - Oracle Java ME SDK 8.3 (closer to a real handset), or
  - MicroEmulator 2.0.4 (faster to set up, cross-platform)

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

## Run on an emulator

### Option A — MicroEmulator (recommended for quick iteration)

```bash
java -jar lib/microemulator-2.0.4.jar -Xdescriptor:build/dist/ElimuSMS.jad
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
Elimu-CloudURL: http://<your-host>:8080/v1/query
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
