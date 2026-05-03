// TypeScript port of CompressedTinyML.java — same architecture, same nibble
// codec, same SGD + anchor regularisation. The on-device classifier runs
// entirely in the browser; this is what makes the PWA work offline.
//
// Architecture (must match the J2ME side):
//   F = 26 input features  ->  H = 12 hidden (ReLU)  ->  O = 8 intents (softmax)
//   408 trainable weights + 20 biases = 428 parameters
//   Quantised to 4-bit nibbles: 204 weight bytes + 10 bias bytes = 214 bytes

import { extractFeatures, FEATURE_SIZE } from "./features";
import {
  COMPRESSED_WEIGHTS_HEX,
  COMPRESSED_BIASES_HEX,
  hexToBytes,
} from "./weights";

export const HIDDEN_SIZE  = 12;
export const OUTPUT_SIZE  = 8;
export const W1_SIZE      = HIDDEN_SIZE * FEATURE_SIZE;   // 312
export const W2_SIZE      = OUTPUT_SIZE * HIDDEN_SIZE;    // 96
export const TOTAL_PARAMS = W1_SIZE + W2_SIZE + HIDDEN_SIZE + OUTPUT_SIZE; // 428

const CFP_LAMBDA = 0.01; // L2 anchor regularisation strength (matches Java)

export const INTENT_NAMES = [
  "math_help", "science_help", "english_help", "quiz",
  "general_help", "progress", "greeting", "farewell",
] as const;
export type IntentName = (typeof INTENT_NAMES)[number];

export interface Prediction {
  intent: number;
  intentName: IntentName;
  confidence: number;
  probabilities: Float32Array;
}

// ── Nibble codec — matches CompressedTinyML.getRawWeight ────────────────────

function decodeNibbles(packed: Uint8Array, count: number): Float32Array {
  const out = new Float32Array(count);
  for (let i = 0; i < count; i++) {
    const byteIdx = i >> 1;
    const shift   = (i & 1) * 4;
    const n4      = (packed[byteIdx]! >> shift) & 0x0F;
    out[i] = (n4 - 7.5) / 7.5;
  }
  return out;
}

// ── The classifier ─────────────────────────────────────────────────────────

export class CompressedTinyML {
  // Live weights (mutated by learn() and applyGlobalUpdate).
  w1 = new Float32Array(W1_SIZE);
  w2 = new Float32Array(W2_SIZE);
  b1 = new Float32Array(HIDDEN_SIZE);
  b2 = new Float32Array(OUTPUT_SIZE);

  // Catastrophic-forgetting anchor (last stable point).
  private aw1 = new Float32Array(W1_SIZE);
  private aw2 = new Float32Array(W2_SIZE);
  private ab1 = new Float32Array(HIDDEN_SIZE);
  private ab2 = new Float32Array(OUTPUT_SIZE);

  // Federated learning anchor (last synchronised global).
  private fw1 = new Float32Array(W1_SIZE);
  private fw2 = new Float32Array(W2_SIZE);
  private fb1 = new Float32Array(HIDDEN_SIZE);
  private fb2 = new Float32Array(OUTPUT_SIZE);

  // Forward-pass cache for backprop (matches Java fields).
  private lastFeatures = new Float32Array(FEATURE_SIZE);
  private lastZ1       = new Float32Array(HIDDEN_SIZE);
  private lastA1       = new Float32Array(HIDDEN_SIZE);
  private lastOutput   = new Float32Array(OUTPUT_SIZE);

  constructor() {
    this.loadFactoryDefaults();
  }

  loadFactoryDefaults(): void {
    const wBytes = hexToBytes(COMPRESSED_WEIGHTS_HEX);
    const bBytes = hexToBytes(COMPRESSED_BIASES_HEX);
    const wAll = decodeNibbles(wBytes, W1_SIZE + W2_SIZE);
    const bAll = decodeNibbles(bBytes, HIDDEN_SIZE + OUTPUT_SIZE);
    this.w1.set(wAll.subarray(0, W1_SIZE));
    this.w2.set(wAll.subarray(W1_SIZE));
    this.b1.set(bAll.subarray(0, HIDDEN_SIZE));
    this.b2.set(bAll.subarray(HIDDEN_SIZE));
    this.copyToCFPAnchor();
    this.copyToFLAnchor();
  }

  predict(text: string): Prediction {
    const f = extractFeatures(text);
    this.lastFeatures.set(f);

    // z1 = W1 · x + b1
    for (let i = 0; i < HIDDEN_SIZE; i++) {
      let s = this.b1[i]!;
      const row = i * FEATURE_SIZE;
      for (let j = 0; j < FEATURE_SIZE; j++) s += f[j]! * this.w1[row + j]!;
      this.lastZ1[i] = s;
      this.lastA1[i] = s > 0 ? s : 0;
    }
    // z2 = W2 · a1 + b2
    const z2 = new Float32Array(OUTPUT_SIZE);
    for (let i = 0; i < OUTPUT_SIZE; i++) {
      let s = this.b2[i]!;
      const row = i * HIDDEN_SIZE;
      for (let j = 0; j < HIDDEN_SIZE; j++) s += this.lastA1[j]! * this.w2[row + j]!;
      z2[i] = s;
    }
    // softmax (numerically stable)
    let max = z2[0]!;
    for (let i = 1; i < OUTPUT_SIZE; i++) if (z2[i]! > max) max = z2[i]!;
    let sum = 0;
    for (let i = 0; i < OUTPUT_SIZE; i++) {
      this.lastOutput[i] = Math.exp(z2[i]! - max);
      sum += this.lastOutput[i]!;
    }
    if (sum > 1e-9) for (let i = 0; i < OUTPUT_SIZE; i++) this.lastOutput[i]! /= sum;

    let argmax = 0, conf = this.lastOutput[0]!;
    for (let i = 1; i < OUTPUT_SIZE; i++) {
      if (this.lastOutput[i]! > conf) { conf = this.lastOutput[i]!; argmax = i; }
    }
    return {
      intent: argmax,
      intentName: INTENT_NAMES[argmax]!,
      confidence: conf,
      probabilities: new Float32Array(this.lastOutput),
    };
  }

  /**
   * One SGD step using the cached forward pass + L2 pull toward the CFP
   * anchor. Caller must invoke predict() immediately before learn().
   * `lr` is typically scaled by (1 - confidence) to avoid hammering already
   * confident predictions — matches the J2ME applyCloudArbitratedLabel.
   */
  learn(correctIntent: number, lr: number): void {
    const x  = this.lastFeatures;
    const z1 = this.lastZ1;
    const a1 = this.lastA1;
    const p  = this.lastOutput;

    const d2 = new Float32Array(OUTPUT_SIZE);
    for (let i = 0; i < OUTPUT_SIZE; i++) {
      d2[i] = p[i]! - (i === correctIntent ? 1 : 0);
    }

    // Update W2 and b2
    for (let i = 0; i < OUTPUT_SIZE; i++) {
      this.b2[i]! -= lr * (d2[i]! + CFP_LAMBDA * (this.b2[i]! - this.ab2[i]!));
      const row = i * HIDDEN_SIZE;
      for (let j = 0; j < HIDDEN_SIZE; j++) {
        const idx = row + j;
        this.w2[idx]! -= lr * (d2[i]! * a1[j]! + CFP_LAMBDA * (this.w2[idx]! - this.aw2[idx]!));
      }
    }

    // Hidden-layer delta
    const d1 = new Float32Array(HIDDEN_SIZE);
    for (let j = 0; j < HIDDEN_SIZE; j++) {
      let s = 0;
      for (let i = 0; i < OUTPUT_SIZE; i++) s += this.w2[i * HIDDEN_SIZE + j]! * d2[i]!;
      d1[j] = z1[j]! > 0 ? s : 0;
    }

    // Update W1 and b1
    for (let i = 0; i < HIDDEN_SIZE; i++) {
      this.b1[i]! -= lr * (d1[i]! + CFP_LAMBDA * (this.b1[i]! - this.ab1[i]!));
      const row = i * FEATURE_SIZE;
      for (let j = 0; j < FEATURE_SIZE; j++) {
        const idx = row + j;
        this.w1[idx]! -= lr * (d1[i]! * x[j]! + CFP_LAMBDA * (this.w1[idx]! - this.aw1[idx]!));
      }
    }
  }

  /**
   * Replace current weights with a freshly-pulled FL global model and
   * advance both anchors so future learn() pulls toward the new global.
   */
  applyGlobalUpdate(global: Float32Array): void {
    if (global.length !== TOTAL_PARAMS) {
      throw new Error(`global must be ${TOTAL_PARAMS} params, got ${global.length}`);
    }
    let p = 0;
    this.w1.set(global.subarray(p, p + W1_SIZE)); p += W1_SIZE;
    this.w2.set(global.subarray(p, p + W2_SIZE)); p += W2_SIZE;
    this.b1.set(global.subarray(p, p + HIDDEN_SIZE)); p += HIDDEN_SIZE;
    this.b2.set(global.subarray(p, p + OUTPUT_SIZE));
    this.copyToCFPAnchor();
    this.copyToFLAnchor();
  }

  /** Flatten current weights minus FL anchor into a 428-float delta. */
  computeDeltaFromFLAnchor(): Float32Array {
    const out = new Float32Array(TOTAL_PARAMS);
    let p = 0;
    for (let i = 0; i < W1_SIZE; i++)     out[p++] = this.w1[i]! - this.fw1[i]!;
    for (let i = 0; i < W2_SIZE; i++)     out[p++] = this.w2[i]! - this.fw2[i]!;
    for (let i = 0; i < HIDDEN_SIZE; i++) out[p++] = this.b1[i]! - this.fb1[i]!;
    for (let i = 0; i < OUTPUT_SIZE; i++) out[p++] = this.b2[i]! - this.fb2[i]!;
    return out;
  }

  flattenWeights(): Float32Array {
    const out = new Float32Array(TOTAL_PARAMS);
    let p = 0;
    out.set(this.w1, p); p += W1_SIZE;
    out.set(this.w2, p); p += W2_SIZE;
    out.set(this.b1, p); p += HIDDEN_SIZE;
    out.set(this.b2, p);
    return out;
  }

  loadFromFlat(flat: Float32Array): void {
    if (flat.length !== TOTAL_PARAMS) {
      throw new Error(`flat must be ${TOTAL_PARAMS} params, got ${flat.length}`);
    }
    let p = 0;
    this.w1.set(flat.subarray(p, p + W1_SIZE)); p += W1_SIZE;
    this.w2.set(flat.subarray(p, p + W2_SIZE)); p += W2_SIZE;
    this.b1.set(flat.subarray(p, p + HIDDEN_SIZE)); p += HIDDEN_SIZE;
    this.b2.set(flat.subarray(p, p + OUTPUT_SIZE));
  }

  // ── Anchors (private) ─────────────────────────────────────────────────────

  private copyToCFPAnchor(): void {
    this.aw1.set(this.w1); this.aw2.set(this.w2);
    this.ab1.set(this.b1); this.ab2.set(this.b2);
  }

  private copyToFLAnchor(): void {
    this.fw1.set(this.w1); this.fw2.set(this.w2);
    this.fb1.set(this.b1); this.fb2.set(this.b2);
  }
}
