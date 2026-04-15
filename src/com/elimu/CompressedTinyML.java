package com.elimu;

import java.io.*;
import javax.microedition.rms.*;

/**
 * CompressedTinyML - TinyML model for J2ME/CLDC 1.1.
 * Fully compatible with MIDP 2.0.
 * Trained on Kenya CBC Grade 6 Living Things science data.
 * Accuracy: 83.78% test / 80.73% train.
 *
 * Supports on-device online learning:
 *   call learn(correctIntent, lr) immediately after predict() to do one
 *   SGD backprop step, then saveWeights() to persist to RecordStore.
 */
public class CompressedTinyML {

    // ── Architecture ──────────────────────────────────────────────────────────
    private static final int FEATURE_SIZE = 25;
    private static final int HIDDEN_SIZE  = 12;
    private static final int OUTPUT_SIZE  = 8;

    // ── Factory-default compressed weights (4-bit nibble packed) ─────────────
    // W1 (12×25) + W2 (8×12) = 396 nibbles = 198 bytes
    private static final byte[] COMPRESSED_WEIGHTS = {
            (byte)0xB8, (byte)0x7B, (byte)0xA8, (byte)0xAA, (byte)0x9B, (byte)0xA9, (byte)0x98, (byte)0x78,
            (byte)0x88, (byte)0x77, (byte)0x99, (byte)0x8A, (byte)0x67, (byte)0xA6, (byte)0x87, (byte)0x7A,
            (byte)0x86, (byte)0x87, (byte)0x77, (byte)0x98, (byte)0x8A, (byte)0xB8, (byte)0xBA, (byte)0x8C,
            (byte)0x67, (byte)0x8B, (byte)0xBC, (byte)0xC7, (byte)0xAA, (byte)0x79, (byte)0x77, (byte)0x77,
            (byte)0x78, (byte)0x87, (byte)0x76, (byte)0x88, (byte)0x99, (byte)0x86, (byte)0x87, (byte)0x88,
            (byte)0x8A, (byte)0xA8, (byte)0x88, (byte)0x88, (byte)0x68, (byte)0x8A, (byte)0xA8, (byte)0xAA,
            (byte)0x7B, (byte)0x38, (byte)0x84, (byte)0x58, (byte)0xB8, (byte)0x88, (byte)0x96, (byte)0x98,
            (byte)0x88, (byte)0x77, (byte)0x88, (byte)0xC6, (byte)0x97, (byte)0x55, (byte)0x8A, (byte)0x67,
            (byte)0x76, (byte)0x76, (byte)0x67, (byte)0x67, (byte)0x66, (byte)0x57, (byte)0x89, (byte)0x78,
            (byte)0x7A, (byte)0x75, (byte)0xAA, (byte)0xA4, (byte)0x54, (byte)0x59, (byte)0x99, (byte)0xA9,
            (byte)0xBA, (byte)0xAA, (byte)0x78, (byte)0x88, (byte)0xA8, (byte)0x78, (byte)0x36, (byte)0xAA,
            (byte)0x01, (byte)0x8B, (byte)0x33, (byte)0x43, (byte)0x66, (byte)0x65, (byte)0x96, (byte)0x8B,
            (byte)0xD8, (byte)0x9C, (byte)0x99, (byte)0x79, (byte)0x7B, (byte)0xAC, (byte)0xA7, (byte)0x88,
            (byte)0x77, (byte)0x76, (byte)0x66, (byte)0x67, (byte)0x87, (byte)0x88, (byte)0x78, (byte)0x78,
            (byte)0xAA, (byte)0x7A, (byte)0x8D, (byte)0xA7, (byte)0xAA, (byte)0xAB, (byte)0xAB, (byte)0x9A,
            (byte)0x87, (byte)0x88, (byte)0x87, (byte)0x79, (byte)0x76, (byte)0xA4, (byte)0x78, (byte)0x67,
            (byte)0xBA, (byte)0x9A, (byte)0xA8, (byte)0x89, (byte)0x7B, (byte)0x87, (byte)0x79, (byte)0x89,
            (byte)0x7A, (byte)0x8A, (byte)0x9B, (byte)0x75, (byte)0xA7, (byte)0xCA, (byte)0x9A, (byte)0x99,
            (byte)0x89, (byte)0x7A, (byte)0x48, (byte)0x68, (byte)0x96, (byte)0x7C, (byte)0x74, (byte)0x99,
            (byte)0x65, (byte)0xA4, (byte)0xC9, (byte)0x25, (byte)0x6A, (byte)0x86, (byte)0x59, (byte)0x0B,
            (byte)0xC5, (byte)0x99, (byte)0xC8, (byte)0xB9, (byte)0x4A, (byte)0x14, (byte)0x58, (byte)0x36,
            (byte)0x4B, (byte)0x4B, (byte)0x83, (byte)0x34, (byte)0x58, (byte)0xCA, (byte)0xB7, (byte)0x93,
            (byte)0x65, (byte)0x98, (byte)0x54, (byte)0x3B, (byte)0x53, (byte)0x95, (byte)0xA9, (byte)0x88,
            (byte)0x35, (byte)0xC3, (byte)0x61, (byte)0x00, (byte)0xB9, (byte)0x79, (byte)0x2A, (byte)0x3A,
            (byte)0x56, (byte)0x66, (byte)0x9A, (byte)0x29, (byte)0x7B, (byte)0x29
    };

    // 12 hidden + 8 output = 20 biases = 10 bytes
    private static final byte[] COMPRESSED_BIASES = {
            (byte)0xBA, (byte)0xEE, (byte)0xEF, (byte)0xBE, (byte)0xAC, (byte)0xCA, (byte)0x49, (byte)0xB8,
            (byte)0x8B, (byte)0xA7
    };

    // ── Mutable weight arrays (live during inference; updated by learning) ────
    private float[] w1 = new float[HIDDEN_SIZE * FEATURE_SIZE]; // 12×25 row-major
    private float[] w2 = new float[OUTPUT_SIZE  * HIDDEN_SIZE]; //  8×12 row-major
    private float[] b1 = new float[HIDDEN_SIZE];
    private float[] b2 = new float[OUTPUT_SIZE];

    // ── Anchor weights for catastrophic-forgetting prevention ────────────────
    // Each correction is penalised if it drifts too far from the last stable point.
    private float[] anchorW1 = new float[HIDDEN_SIZE * FEATURE_SIZE];
    private float[] anchorW2 = new float[OUTPUT_SIZE  * HIDDEN_SIZE];
    private float[] anchorB1 = new float[HIDDEN_SIZE];
    private float[] anchorB2 = new float[OUTPUT_SIZE];
    private static final float LAMBDA = 0.01f; // regularisation strength

    // ── Forward-pass cache (needed for backprop) ──────────────────────────────
    private byte[]  lastFeatures = new byte[FEATURE_SIZE];
    private float[] lastZ1       = new float[HIDDEN_SIZE]; // pre-ReLU hidden
    private float[] lastA1       = new float[HIDDEN_SIZE]; // post-ReLU hidden
    private float[] lastOutput   = new float[OUTPUT_SIZE]; // softmax probs

    private float lastConfidence = 0.0f;

    private static final String RMS_STORE = "ElimuWeights";

    // ── Lifecycle ─────────────────────────────────────────────────────────────
    public void loadModel() {
        decompressWeights();   // populate float arrays from static byte defaults
        loadSavedWeights();    // overlay with persisted weights if any exist

        StringBuffer sb = new StringBuffer("CompressedTinyML loaded: ");
        sb.append(COMPRESSED_WEIGHTS.length);
        sb.append(" bytes weights, ");
        sb.append(FEATURE_SIZE);
        sb.append(" features (Living Things model + online learning)");
        System.out.println(sb.toString());
    }

    // ── Inference ─────────────────────────────────────────────────────────────
    public byte predict(String text) {
        byte[] features = extractFeatures(text);
        System.arraycopy(features, 0, lastFeatures, 0, FEATURE_SIZE);
        computeHiddenLayer(features);          // fills lastZ1, lastA1
        float[] output = computeOutputLayer(); // reads lastA1, fills lastOutput
        lastConfidence = getConfidence(output);
        return argMax(output);
    }

    public float getLastConfidence() {
        return lastConfidence;
    }

    // ── On-device online learning ─────────────────────────────────────────────
    /**
     * One stochastic gradient-descent step using cached forward-pass state.
     * Must be called immediately after predict() — uses lastFeatures, lastZ1,
     * lastA1, lastOutput which were set during that predict() call.
     *
     * Gradient derivation (cross-entropy loss + softmax):
     *   dL/dz2_k  = output_k - 1{k == correctIntent}   (clean closed form)
     *   dL/dW2_ij = dL/dz2_i * a1_j
     *   dL/db2_i  = dL/dz2_i
     *   dL/da1_j  = sum_i( W2_ij * dL/dz2_i )
     *   dL/dz1_i  = dL/da1_i * relu'(z1_i)             relu' = 1 if z1>0 else 0
     *   dL/dW1_ij = dL/dz1_i * x_j
     *   dL/db1_i  = dL/dz1_i
     *
     * @param correctIntent  true intent label 0-7
     * @param lr             learning rate (0.05f recommended)
     */
    public void learn(int correctIntent, float lr) {
        // ── Output-layer delta ────────────────────────────────────────────────
        float[] delta2 = new float[OUTPUT_SIZE];
        for (int i = 0; i < OUTPUT_SIZE; i++) {
            delta2[i] = lastOutput[i] - (i == correctIntent ? 1.0f : 0.0f);
        }

        // ── Update W2 and b2 (gradient + L2 pull toward anchor) ─────────────
        for (int i = 0; i < OUTPUT_SIZE; i++) {
            b2[i] -= lr * (delta2[i] + LAMBDA * (b2[i] - anchorB2[i]));
            for (int j = 0; j < HIDDEN_SIZE; j++) {
                int idx = i * HIDDEN_SIZE + j;
                w2[idx] -= lr * (delta2[i] * lastA1[j] + LAMBDA * (w2[idx] - anchorW2[idx]));
            }
        }

        // ── Hidden-layer delta (backprop through W2 then ReLU) ────────────────
        float[] delta1 = new float[HIDDEN_SIZE];
        for (int j = 0; j < HIDDEN_SIZE; j++) {
            float sum = 0.0f;
            for (int i = 0; i < OUTPUT_SIZE; i++) {
                sum += w2[i * HIDDEN_SIZE + j] * delta2[i];
            }
            // ReLU derivative: pass gradient only where pre-activation was positive
            delta1[j] = (lastZ1[j] > 0) ? sum : 0.0f;
        }

        // ── Update W1 and b1 (gradient + L2 pull toward anchor) ─────────────
        for (int i = 0; i < HIDDEN_SIZE; i++) {
            b1[i] -= lr * (delta1[i] + LAMBDA * (b1[i] - anchorB1[i]));
            for (int j = 0; j < FEATURE_SIZE; j++) {
                int idx = i * FEATURE_SIZE + j;
                w1[idx] -= lr * (delta1[i] * lastFeatures[j] + LAMBDA * (w1[idx] - anchorW1[idx]));
            }
        }
    }

    // ── Weight persistence (RecordStore) ──────────────────────────────────────
    /**
     * Serialise current weights to RMS so they survive app restarts.
     * Total size: (300+96+12+8) floats × 4 bytes = 1664 bytes (< 8 KB minimum).
     */
    public void saveWeights() {
        RecordStore rs = null;
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            for (int i = 0; i < w1.length; i++) dos.writeFloat(w1[i]);
            for (int i = 0; i < w2.length; i++) dos.writeFloat(w2[i]);
            for (int i = 0; i < b1.length; i++) dos.writeFloat(b1[i]);
            for (int i = 0; i < b2.length; i++) dos.writeFloat(b2[i]);
            dos.flush();
            byte[] data = baos.toByteArray();

            rs = RecordStore.openRecordStore(RMS_STORE, true);
            if (rs.getNumRecords() == 0) {
                rs.addRecord(data, 0, data.length);
            } else {
                rs.setRecord(1, data, 0, data.length);
            }
            copyToAnchor(); // advance anchor so the next correction protects this one
            System.out.println("Weights saved to RMS.");
        } catch (Exception e) {
            StringBuffer se = new StringBuffer("saveWeights failed: ");
            se.append(e.getMessage());
            System.out.println(se.toString());
        } finally {
            if (rs != null) {
                try { rs.closeRecordStore(); } catch (Exception ignore) {}
            }
        }
    }

    /** Overwrite current weights with the original factory defaults and clear RMS. */
    public void resetWeights() {
        decompressWeights();
        RecordStore rs = null;
        try {
            rs = RecordStore.openRecordStore(RMS_STORE, false);
            rs.deleteRecord(1);
        } catch (Exception ignore) {
        } finally {
            if (rs != null) {
                try { rs.closeRecordStore(); } catch (Exception ignore) {}
            }
        }
        System.out.println("Weights reset to factory defaults.");
    }

    // ── Internal: decompress static bytes → float arrays ─────────────────────
    private void decompressWeights() {
        // W1: first 300 logical indices
        for (int i = 0; i < HIDDEN_SIZE * FEATURE_SIZE; i++) {
            w1[i] = getRawWeight(i);
        }
        // W2: next 96 logical indices
        for (int i = 0; i < OUTPUT_SIZE * HIDDEN_SIZE; i++) {
            w2[i] = getRawWeight(HIDDEN_SIZE * FEATURE_SIZE + i);
        }
        for (int i = 0; i < HIDDEN_SIZE; i++) b1[i] = getRawBias(i);
        for (int i = 0; i < OUTPUT_SIZE; i++) b2[i] = getRawBias(HIDDEN_SIZE + i);
        copyToAnchor(); // anchor starts at factory defaults
    }

    /** Snapshot current weights as the new stable baseline for L2 regularisation. */
    private void copyToAnchor() {
        System.arraycopy(w1, 0, anchorW1, 0, w1.length);
        System.arraycopy(w2, 0, anchorW2, 0, w2.length);
        System.arraycopy(b1, 0, anchorB1, 0, b1.length);
        System.arraycopy(b2, 0, anchorB2, 0, b2.length);
    }

    /** Load persisted weights from RMS; silently keeps current weights if not found. */
    private void loadSavedWeights() {
        RecordStore rs = null;
        try {
            rs = RecordStore.openRecordStore(RMS_STORE, false);
            if (rs.getNumRecords() > 0) {
                byte[] data = rs.getRecord(1);
                DataInputStream dis = new DataInputStream(new ByteArrayInputStream(data));
                for (int i = 0; i < w1.length; i++) w1[i] = dis.readFloat();
                for (int i = 0; i < w2.length; i++) w2[i] = dis.readFloat();
                for (int i = 0; i < b1.length; i++) b1[i] = dis.readFloat();
                for (int i = 0; i < b2.length; i++) b2[i] = dis.readFloat();
                copyToAnchor(); // anchor = what was persisted, so future corrections don't forget it
                System.out.println("Loaded saved weights from RMS.");
            }
        } catch (RecordStoreNotFoundException e) {
            System.out.println("No saved weights — using factory defaults.");
        } catch (Exception e) {
            StringBuffer le = new StringBuffer("loadSavedWeights error: ");
            le.append(e.getMessage());
            System.out.println(le.toString());
        } finally {
            if (rs != null) {
                try { rs.closeRecordStore(); } catch (Exception ignore) {}
            }
        }
    }

    // ── Nibble decompression (maps 4-bit [0,15] → float [-1.0, 1.0]) ─────────
    private float getRawWeight(int logicalIndex) {
        int byteIdx   = logicalIndex / 2;
        int nibbleShift = (logicalIndex % 2) * 4;
        if (byteIdx < COMPRESSED_WEIGHTS.length) {
            int w4 = (COMPRESSED_WEIGHTS[byteIdx] >> nibbleShift) & 0x0F;
            return (w4 - 7.5f) / 7.5f;
        }
        return 0.0f;
    }

    private float getRawBias(int index) {
        if (index < 20) {
            int byteIdx     = index / 2;
            int nibbleShift = (index % 2) * 4;
            int b4 = (COMPRESSED_BIASES[byteIdx] >> nibbleShift) & 0x0F;
            return (b4 - 7.5f) / 7.5f;
        }
        return 0.0f;
    }

    // ── Forward pass ──────────────────────────────────────────────────────────
    /** Computes hidden layer; stores pre-ReLU in lastZ1, post-ReLU in lastA1. */
    private void computeHiddenLayer(byte[] features) {
        for (int i = 0; i < HIDDEN_SIZE; i++) {
            float sum = b1[i];
            for (int j = 0; j < FEATURE_SIZE; j++) {
                sum += features[j] * w1[i * FEATURE_SIZE + j];
            }
            lastZ1[i] = sum;
            lastA1[i] = relu(sum);
        }
    }

    /** Computes output layer from lastA1; stores softmax probs in lastOutput. */
    private float[] computeOutputLayer() {
        float[] z2 = new float[OUTPUT_SIZE];
        for (int i = 0; i < OUTPUT_SIZE; i++) {
            float sum = b2[i];
            for (int j = 0; j < HIDDEN_SIZE; j++) {
                sum += lastA1[j] * w2[i * HIDDEN_SIZE + j];
            }
            z2[i] = sum;
        }
        float[] out = softmax(z2);
        for (int i = 0; i < OUTPUT_SIZE; i++) lastOutput[i] = out[i];
        return out;
    }

    // ── Math ──────────────────────────────────────────────────────────────────
    private float[] softmax(float[] x) {
        float max = x[0];
        for (int i = 1; i < x.length; i++) {
            if (x[i] > max) max = x[i];
        }
        float[] out = new float[x.length];
        float sum = 0.0f;
        for (int i = 0; i < x.length; i++) {
            out[i] = expApprox(x[i] - max);
            sum += out[i];
        }
        if (sum > 0.00001f) {
            for (int i = 0; i < out.length; i++) out[i] /= sum;
        }
        return out;
    }

    /** Fast exp approximation via (1 + x/8)^8 — accurate enough for softmax routing. */
    private float expApprox(float x) {
        if (x >  5.0f) return 148.413f;
        if (x < -5.0f) return 0.0067f;
        float t = 1.0f + x / 8.0f;
        t = t * t; t = t * t; t = t * t; // ^8
        return t;
    }

    private float relu(float x) { return (x > 0) ? x : 0; }

    private byte argMax(float[] arr) {
        byte maxIdx = 0;
        for (byte i = 1; i < arr.length; i++) {
            if (arr[i] > arr[maxIdx]) maxIdx = i;
        }
        return maxIdx;
    }

    private float getConfidence(float[] outputs) {
        float max = outputs[0];
        for (int i = 1; i < outputs.length; i++) {
            if (outputs[i] > max) max = outputs[i];
        }
        return max;
    }

    // ── Feature extraction (25 binary features) ───────────────────────────────
    private byte[] extractFeatures(String text) {
        byte[] features = new byte[25];
        String lower = text.toLowerCase();

        // Features 0-8: Subject keywords
        String[] subjectKeywords = {
            "math", "science", "english", "calculat", "experiment",
            "grammar", "plant", "animal", "living"
        };
        for (int i = 0; i < subjectKeywords.length; i++) {
            if (contains(lower, subjectKeywords[i])) features[i] = 1;
        }

        // Aliases: biology terms not in the primary keyword list
        if (contains(lower, "insect")    || contains(lower, "vertebr")  ||
            contains(lower, "invertebr") || contains(lower, "pollinat") ||
            contains(lower, "bee")       || contains(lower, "worm")     ||
            contains(lower, "bird")      || contains(lower, "fish")     ||
            contains(lower, "mammal")    || contains(lower, "reptile")  ||
            contains(lower, "amphibian")) {
            features[7] = 1; features[1] = 1;
        }
        if (contains(lower, "chlorophyll") || contains(lower, "stomata") ||
            contains(lower, "transpir")    || contains(lower, "leaf")    ||
            contains(lower, "leaves")      || contains(lower, "root")    ||
            contains(lower, "seed")        || contains(lower, "flower")  ||
            contains(lower, "stem")) {
            features[6] = 1; features[1] = 1;
        }
        if (features[6] == 1 || features[7] == 1 || features[8] == 1) features[1] = 1;

        // Human body / health
        if (contains(lower, "heart")    || contains(lower, "blood")    ||
            contains(lower, "circul")   || contains(lower, "artery")   ||
            contains(lower, "arteries") || contains(lower, "vein")     ||
            contains(lower, "capillar") || contains(lower, "pulse")    ||
            contains(lower, "plasma")   || contains(lower, "haemoglob")) {
            features[1] = 1;
        }
        // Reproductive system / adolescence
        if (contains(lower, "reproduct") || contains(lower, "adolescen") ||
            contains(lower, "puberty")   || contains(lower, "ovary")    ||
            contains(lower, "uterus")    || contains(lower, "testis")   ||
            contains(lower, "sperm")     || contains(lower, "menstruat")||
            contains(lower, "ovulat")    || contains(lower, "fallopian")) {
            features[1] = 1;
        }
        // Water conservation
        if (contains(lower, "conserv") || contains(lower, "harvest") ||
            contains(lower, "recycle") || contains(lower, "mulch")) {
            features[1] = 1;
        }

        // Digestive, respiratory, skeletal systems
        if (contains(lower,"digest") || contains(lower,"stomach") || contains(lower,"intestin") ||
            contains(lower,"liver")  || contains(lower,"bile")    || contains(lower,"saliva")   ||
            contains(lower,"oesoph") || contains(lower,"enzyme")) features[1] = 1;

        if (contains(lower,"lung")   || contains(lower,"respirat") || contains(lower,"breath")  ||
            contains(lower,"trachea")|| contains(lower,"bronch")   || contains(lower,"diaphragm")) features[1] = 1;

        if (contains(lower,"skeleton") || contains(lower,"bone") || contains(lower,"joint")   ||
            contains(lower,"cartilage") || contains(lower,"muscle") || contains(lower,"skull")) features[1] = 1;

        // Physics and environment
        if (contains(lower,"lever")  || contains(lower,"pulley") || contains(lower,"machine")  ||
            contains(lower,"fulcrum") || contains(lower,"effort") || contains(lower,"inclined")) features[1] = 1;

        if (contains(lower,"solid")  || contains(lower,"liquid") || contains(lower,"melting")  ||
            contains(lower,"boiling")|| contains(lower,"matter")  || contains(lower,"condensat")) features[1] = 1;

        if (contains(lower,"soil")   || contains(lower,"erosion")|| contains(lower,"weather")  ||
            contains(lower,"loam")   || contains(lower,"sandy")  || contains(lower,"clay")) features[1] = 1;

        if (contains(lower,"bacteria")|| contains(lower,"virus") || contains(lower,"fungus")   ||
            contains(lower,"microorganism") || contains(lower,"disease") || contains(lower,"germ")) features[1] = 1;

        // Vertebrate groups
        if (contains(lower,"amphibian") || contains(lower,"reptile") || contains(lower,"frog") ||
            contains(lower,"lizard")    || contains(lower,"mammal")  || contains(lower,"bird")  ||
            contains(lower,"gill")      || contains(lower,"feather") || contains(lower,"scale")) {
            features[7] = 1; features[1] = 1;
        }

        // Plant reproduction
        if (contains(lower,"germinat") || contains(lower,"dispersal") || contains(lower,"vegetative")) {
            features[6] = 1; features[1] = 1;
        }

        // Features 9-13: Living-things specifics
        if (contains(lower, "photosynthes")) features[9]  = 1;
        if (contains(lower, "habitat"))      features[10] = 1;
        if (contains(lower, "food"))         features[11] = 1;
        if (contains(lower, "water"))        features[12] = 1;
        if (contains(lower, "grow"))         features[13] = 1;

        // Features 14-17: Question type
        if (contains(lower, "what") || contains(lower, "which")) features[14] = 1;
        if (contains(lower, "how"))                               features[15] = 1;
        if (contains(lower, "why"))                               features[16] = 1;
        if (contains(lower, "when") || contains(lower, "where"))  features[17] = 1;

        // Features 18-23: Educational context
        String[] ctx = {"help", "learn", "teach", "explain", "question", "answer"};
        for (int i = 0; i < ctx.length; i++) {
            if (contains(lower, ctx[i])) features[18 + i] = 1;
        }

        // Feature 24: Social cue
        if (contains(lower, "hello") || contains(lower, "hi") ||
            contains(lower, "bye")   || contains(lower, "good")) {
            features[24] = 1;
        }

        return features;
    }

    private boolean contains(String str, String sub) {
        return str.indexOf(sub) != -1;
    }

    // ── Debug helpers ─────────────────────────────────────────────────────────
    public void debugPrediction(String text) {
        byte[] features = extractFeatures(text);
        StringBuffer fb = new StringBuffer("Features: ");
        for (int i = 0; i < features.length; i++) {
            if (features[i] == 1) { fb.append(getFeatureName(i)); fb.append(' '); }
        }
        System.out.println(fb.toString());

        byte intent = predict(text);
        StringBuffer db = new StringBuffer("DEBUG '");
        db.append(text); db.append("' -> Intent ");
        db.append(intent); db.append(" ("); db.append(getIntentName(intent));
        db.append(") conf="); db.append(getLastConfidence());
        System.out.println(db.toString());
    }

    private String getFeatureName(int i) {
        String[] names = {
            "math","science","english","calculat","experiment","grammar",
            "plant","animal","living","photosynthes","habitat","food",
            "water","grow","what/which","how","why","when/where",
            "help","learn","teach","explain","question","answer","social"
        };
        if (i >= 0 && i < names.length) return names[i];
        StringBuffer sb = new StringBuffer("f"); sb.append(i); return sb.toString();
    }

    private String getIntentName(byte id) {
        switch (id) {
            case 0: return "math_help";
            case 1: return "science_help";
            case 2: return "english_help";
            case 3: return "quiz";
            case 4: return "general_help";
            case 5: return "progress";
            case 6: return "greeting";
            case 7: return "farewell";
            default: return "unknown";
        }
    }

    public void testModel() {
        System.out.println("=== Model Testing ===");
        String[] cases = {
            "what is photosynthesis", "how do plants grow", "animal habitats",
            "vertebrate animals", "food chain living things", "math addition problem",
            "english grammar nouns", "science experiment", "take a quiz",
            "show my progress", "good morning", "goodbye"
        };
        for (int i = 0; i < cases.length; i++) debugPrediction(cases[i]);
    }
}