// Entry point. Wires the on-device classifier to the cloud-fallback tier
// and the federated learning loop, with the chat UI on top.
//
// The architecture mirrors the J2ME ElimuSMSMidlet:
//   1. predict() locally; if confidence >= threshold, answer from a
//      curriculum response table (placeholder here).
//   2. otherwise, askCloud(); on response, learn(intent, lr*(1-conf)).
//   3. enqueue an FL delta on each session end; flush opportunistically
//      whenever the cloud-fallback request succeeds.
//   4. on startup, opportunistically pull the latest global.

import { CompressedTinyML, INTENT_NAMES } from "./classifier";
import {
  askCloud, encodeUpload, pingHealth, pullGlobal, uploadFL, UPLOAD_SIZE,
} from "./cloud";
import { evaluateMathExpression, looksLikeArithmetic } from "./calc";
import { isMathQuery, isScienceQuery } from "./keywords";
import { mathResponse, scienceResponse } from "./responses";
import {
  drainFL, enqueueFL, loadOrCreateDeviceId, loadWeights, saveWeights,
} from "./storage";
import {
  appendBot, appendSystem, appendUser, setFooter, setStatus,
} from "./ui";

const CONFIDENCE_THRESHOLD = 0.30;
const BASE_LR              = 0.05;
const MAX_CONTEXT_TURNS    = 3;

const model    = new CompressedTinyML();
// Assigned in boot() before any FL upload; ! tells TS we don't read it before then.
let deviceId!: Uint8Array;
let anchorRound = 0;
let online     = false;
const recentQs: string[] = [];

// ── Boot ───────────────────────────────────────────────────────────────────

async function boot(): Promise<void> {
  setStatus("loading…", false);

  // Restore weights if we have a saved session.
  const saved = await loadWeights().catch(() => null);
  if (saved) model.loadFromFlat(saved);

  // Stable anonymous device ID for FL uploads.
  deviceId = await loadOrCreateDeviceId();

  // Probe the cloud server. Even if it's down, the app works offline.
  online = await pingHealth();
  setStatus(online ? "online" : "offline", online);

  if (online) {
    const g = await pullGlobal();
    if (g) {
      model.applyGlobalUpdate(g.weights);
      anchorRound = g.round;
      await saveWeights(model.flattenWeights());
      appendSystem(`Pulled global model (round ${g.round}).`);
    }
    // Drain any deltas left from prior offline sessions.
    drainFL(uploadFL).then(({ flushed, remaining }) => {
      if (flushed > 0) {
        appendSystem(`Flushed ${flushed} pending FL upload(s); ${remaining} queued.`);
      }
    });
  }

  appendBot(
    "Habari! / Hello! I'm ElimuSMS. Ask me a math or science question — I run on your device, no internet needed for routine answers.",
    { tier: "device" },
  );

  document.getElementById("composer")!.addEventListener("submit", onSubmit);
  document.addEventListener("visibilitychange", () => {
    if (document.visibilityState === "hidden") onSessionEnd();
  });
  window.addEventListener("beforeunload", () => onSessionEnd());
}

// ── Submit handler ─────────────────────────────────────────────────────────

async function onSubmit(e: Event): Promise<void> {
  e.preventDefault();
  const input = document.getElementById("q") as HTMLInputElement;
  const q = input.value.trim();
  if (!q) return;
  input.value = "";

  appendUser(q);
  pushContext(q);

  // Arithmetic expressions (e.g. "5 * 4", "3/4 + 1/2") are computed
  // locally by the same evaluator the J2ME side uses. Skips the
  // classifier entirely — there is nothing to learn here.
  if (looksLikeArithmetic(q)) {
    const r = evaluateMathExpression(q);
    if (r.evaluated) {
      appendBot(`${q} = ${r.text}`, {
        tier: "device",
        intentName: "math_help",
        confidence: 1.0,
      });
      return;
    }
  }

  const pred = model.predict(q);

  // Keyword override (matches ElimuSMSMidlet.processQuery): when the question
  // contains unambiguous math or science keywords, route it locally even if
  // the quantised classifier's confidence is borderline. Without this, ~40%
  // of CBC-Grade-6 questions fall through to the cloud unnecessarily.
  let intent = pred.intent;
  let confidence = pred.confidence;
  const kwMath = isMathQuery(q);
  const kwSci  = isScienceQuery(q);
  if (kwMath && !kwSci) {
    intent = 0; confidence = 1.0;
  } else if (kwSci && !kwMath) {
    intent = 1; confidence = 1.0;
  }

  // Intent 2 (english_help) is retired — the model now focuses on STEM only.
  // Any leftover prediction in that slot (rare; no training data points to
  // intent 2) routes to general_help so the user still gets a useful reply.
  if (intent === 2) intent = 4;
  const intentLabel = INTENT_NAMES[intent]!;

  // High confidence (model or keyword override) → answer locally.
  if (confidence >= CONFIDENCE_THRESHOLD) {
    // Topic-specific lookup first (mirrors handleMathQuestion /
    // handleScienceQuestion in the J2ME side); fall back to the
    // generic intent menu if no specific entry matches.
    let answer = localResponse(intentLabel, q);
    if (intent === 0) {
      const hit = mathResponse(q);
      if (hit) answer = hit.text;
    } else if (intent === 1) {
      const hit = scienceResponse(q);
      if (hit) answer = hit.text;
    }
    appendBot(answer, {
      tier: "device",
      intentName: intentLabel,
      confidence,
      onMore: () => askMore(q),
    });
    return;
  }

  // Low confidence → cloud fallback.
  await askMore(q);
}

async function askMore(question: string): Promise<void> {
  if (!online) {
    online = await pingHealth();
    setStatus(online ? "online" : "offline", online);
  }
  if (!online) {
    appendBot(
      "I'm offline right now. Try again when you have signal — I'll fetch a deeper answer then.",
      { tier: "fallback" },
    );
    return;
  }

  const msg = appendBot("Asking the cloud…", { tier: "cloud" });
  try {
    const reply = await askCloud(question, recentQs.slice(0, -1).join(" | "));
    msg.firstChild!.textContent = reply.answer || "(no answer)";

    // Cloud-arbitrated label → one SGD step on the on-device classifier.
    if (reply.intentLabel) {
      const idx = INTENT_NAMES.indexOf(reply.intentLabel as typeof INTENT_NAMES[number]);
      if (idx >= 0) {
        const conf = model.predict(question).confidence;
        model.learn(idx, BASE_LR * (1 - conf));
        await saveWeights(model.flattenWeights());
      }
    }
    // Successful cloud round-trip — flush any queued FL deltas.
    drainFL(uploadFL).catch(() => {});
  } catch (err) {
    // Cloud unreachable (no LLM configured, server down, etc). Fall back to
    // the topic-specific local response table so the user still sees a
    // useful answer rather than the generic intent menu.
    const fallbackPred = model.predict(question);
    const kwMath = isMathQuery(question);
    const kwSci  = isScienceQuery(question);
    let intent = fallbackPred.intent;
    if (kwMath && !kwSci) intent = 0;
    else if (kwSci && !kwMath) intent = 1;
    const intentLabel = INTENT_NAMES[intent]!;
    let text = localResponse(intentLabel, question);
    if (intent === 0) {
      const hit = mathResponse(question);
      if (hit) text = hit.text;
    } else if (intent === 1) {
      const hit = scienceResponse(question);
      if (hit) text = hit.text;
    }
    msg.firstChild!.textContent = text + "\n\n(Offline tier — cloud unavailable.)";
  }
}

// ── Session-end FL enqueue ─────────────────────────────────────────────────

let alreadyEnqueued = false;
async function onSessionEnd(): Promise<void> {
  if (alreadyEnqueued) return;
  alreadyEnqueued = true;
  const delta = model.computeDeltaFromFLAnchor();
  // Skip if nothing changed (norm < tiny epsilon).
  let normSq = 0;
  for (let i = 0; i < delta.length; i++) normSq += delta[i]! * delta[i]!;
  if (normSq < 1e-10) return;
  const payload = encodeUpload(delta, deviceId, anchorRound);
  if (payload.length === UPLOAD_SIZE) {
    await enqueueFL(payload).catch(() => {});
  }
}

// ── Conversation memory ────────────────────────────────────────────────────

function pushContext(q: string): void {
  recentQs.push(q);
  if (recentQs.length > MAX_CONTEXT_TURNS) recentQs.shift();
}

// ── Tiny built-in response table ───────────────────────────────────────────
// Real curriculum content lives in the cloud server's LLM and the J2ME
// MicroResponses class; this is just the friendly fallback so on-device
// answers are useful in the simplest demo flow.

function localResponse(intent: string, _q: string): string {
  switch (intent) {
    case "math_help":
      return "Math topics I can help with: fractions, LCM, HCF, percentages, ratio, decimals, area, perimeter, volume, mean, mode, range. Tap More for a deeper answer from the cloud.";
    case "science_help":
      return "Science: photosynthesis, food chains, vertebrates, circulatory system, soil types, states of matter, simple machines. Tap More for depth.";
    case "english_help":
      // Retired — STEM-only system. If the model ever lands here we route
      // to general_help (see processQuery), so this branch is unreachable
      // in practice. Kept for label-table compatibility.
      return "I help with math and science. Try a STEM question.";
    case "quiz":
      return "Quizzes are coming to the web client — for now, ask a topic question.";
    case "progress":
      return "Progress tracking is on the roadmap for the web client.";
    case "greeting":
      return "Habari! Ask me about math or science — I run on your device.";
    case "farewell":
      return "Kwaheri! Keep revising.";
    default:
      return "I can help with Math and Science. Try: 'photosynthesis', 'fractions', 'LCM', '5 * 4'. Tap More for a richer answer.";
  }
}

setFooter(`device · ${navigator.onLine ? "online" : "offline"} · 26-feature MLP`);
boot();
