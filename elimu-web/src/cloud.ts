// HTTPS client for the cloud-fallback tier and federated learning.
// Same endpoints the J2ME MIDlet hits — /v1/query for the LLM tier,
// /v1/fl/upload + /v1/fl/global for federated training.

import { TOTAL_PARAMS } from "./classifier";

const CLOUD_BASE = ""; // "" → same-origin (Vite dev proxy or PWA hosted alongside server)
const PROTOCOL_VERSION = 0x01;
const HEADER_BYTES     = 1 + 16 + 4; // version + device_id + anchor_round
const NIBBLE_BYTES     = (TOTAL_PARAMS + 1) >> 1; // 214
export const UPLOAD_SIZE = HEADER_BYTES + NIBBLE_BYTES; // 235

// ── Cloud fallback (LLM) ────────────────────────────────────────────────────

export interface CloudAnswer {
  answer: string;
  intentLabel: string | null;
}

export async function askCloud(
  question: string,
  context?: string,
): Promise<CloudAnswer> {
  const body = new URLSearchParams({ q: question, grade: "6", lang: "sw-ke" });
  if (context && context.length > 0) body.set("ctx", context);
  const r = await fetch(`${CLOUD_BASE}/v1/query`, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: body.toString(),
  });
  const text = await r.text();
  if (!r.ok) throw new Error(`cloud HTTP ${r.status}: ${text.slice(0, 100)}`);
  return {
    answer: text,
    intentLabel: r.headers.get("X-Elimu-Intent"),
  };
}

// ── Federated learning ──────────────────────────────────────────────────────

/** Per-coord clip + Gaussian noise, then 4-bit quantise into the Java-side
 *  nibble layout. Matches FederatedLearning.encodeUpload + DP noise. */
export function encodeUpload(
  delta: Float32Array,
  deviceId: Uint8Array,
  anchorRound: number,
  clip: number = 0.10,
  sigma: number = 0.05,
): Uint8Array {
  const out = new Uint8Array(UPLOAD_SIZE);
  out[0] = PROTOCOL_VERSION;
  out.set(deviceId, 1);
  out[17] = (anchorRound >>> 24) & 0xFF;
  out[18] = (anchorRound >>> 16) & 0xFF;
  out[19] = (anchorRound >>>  8) & 0xFF;
  out[20] = (anchorRound       ) & 0xFF;

  for (let i = 0; i < delta.length; i++) {
    let v = delta[i]!;
    if (v >  clip) v =  clip;
    if (v < -clip) v = -clip;
    v += sigma * gaussian();
    let n4 = Math.round(v * 7.5 + 7.5);
    if (n4 < 0)  n4 = 0;
    if (n4 > 15) n4 = 15;
    const byteIdx = HEADER_BYTES + (i >> 1);
    const shift = (i & 1) * 4;
    out[byteIdx]! |= (n4 & 0x0F) << shift;
  }
  return out;
}

function gaussian(): number {
  // Box-Muller via crypto.getRandomValues — strong source for DP noise.
  const buf = new Uint32Array(2);
  crypto.getRandomValues(buf);
  let u1 = (buf[0]! + 1) / 4294967296;
  const u2 = buf[1]! / 4294967296;
  if (u1 < 1e-9) u1 = 1e-9;
  return Math.sqrt(-2 * Math.log(u1)) * Math.cos(2 * Math.PI * u2);
}

export async function uploadFL(payload: Uint8Array): Promise<boolean> {
  if (payload.length !== UPLOAD_SIZE) return false;
  try {
    // Copy into a fresh ArrayBuffer-backed Uint8Array; TS 5.4+ is strict
    // about Uint8Array<ArrayBufferLike> vs Uint8Array<ArrayBuffer> and the
    // body slot of fetch wants the latter.
    const bytes = new Uint8Array(payload.length);
    bytes.set(payload);
    const r = await fetch(`${CLOUD_BASE}/v1/fl/upload`, {
      method: "POST",
      headers: { "Content-Type": "application/octet-stream" },
      body: bytes,
    });
    return r.ok;
  } catch {
    return false;
  }
}

/** Pull the current FL global. Returns the float32 weights + the round number. */
export async function pullGlobal(): Promise<{ round: number; weights: Float32Array } | null> {
  try {
    const r = await fetch(`${CLOUD_BASE}/v1/fl/global`);
    if (!r.ok) return null;
    const buf = new Uint8Array(await r.arrayBuffer());
    if (buf.byteLength < 4 + TOTAL_PARAMS * 4) return null;
    const view = new DataView(buf.buffer, buf.byteOffset, buf.byteLength);
    const round = view.getUint32(0, false); // big-endian
    const weights = new Float32Array(TOTAL_PARAMS);
    for (let i = 0; i < TOTAL_PARAMS; i++) {
      weights[i] = view.getFloat32(4 + i * 4, false);
    }
    return { round, weights };
  } catch {
    return null;
  }
}

// ── Connectivity status ─────────────────────────────────────────────────────

export async function pingHealth(): Promise<boolean> {
  try {
    const r = await fetch(`${CLOUD_BASE}/health`, { method: "GET" });
    return r.ok;
  } catch {
    return false;
  }
}
