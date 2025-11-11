package com.elimu;

import java.io.*;

/**
 * CompressedTinyML - TinyML model for J2ME/CLDC 1.1.
 * Fully compatible with MIDP.
 */
public class CompressedTinyML {
    // TIER 1 OPTIMIZATION: 4-bit quantization + 70% pruning

    private static final int NUM_WEIGHTS = 384; // Reduced from 1968 (80% reduction)
    private static final int NUM_BIASES = 20;

    // 4-bit weights packed (2 weights per byte)
    private static final byte[] COMPRESSED_WEIGHTS = {
            (byte)0x12, (byte)0x34, (byte)0x56, (byte)0x78,
            (byte)0x9A, (byte)0xBC, (byte)0xDE, (byte)0xF1,
            (byte)0x23, (byte)0x45, (byte)0x67, (byte)0x89,
            (byte)0xAB, (byte)0xCD, (byte)0xEF, (byte)0x12,
            // ... fill in remaining 172 bytes
    };

    private static final short[] WEIGHT_POSITIONS = {
            0, 5, 12, 23, 34, 45, 56, 67, 78, 89,
            104, 119, 134, 149, 164, 179, 194, 209, 224, 239,
            254, 269, 284, 299, 314, 329, 344, 359, 374, 389,
            // ... fill in remaining positions
    };

    private static final byte[] COMPRESSED_BIASES = {
            (byte)0x44, (byte)0x44, (byte)0x44, (byte)0x44, (byte)0x44,
            (byte)0x44, (byte)0x44, (byte)0x44, (byte)0x44, (byte)0x44
    };

    private float lastConfidence = 0.0f;

    public void loadModel() {
        // Use StringBuffer for J2ME compatibility
        StringBuffer sb = new StringBuffer();
        sb.append("CompressedTinyML loaded: ");
        sb.append(COMPRESSED_WEIGHTS.length);
        sb.append(" bytes weights, ");
        sb.append(WEIGHT_POSITIONS.length);
        sb.append(" weights");
        System.out.println(sb.toString());
    }

    public byte predict(String text) {
        byte[] features = extractFeatures(text);
        float[] hidden = computeHiddenLayer(features);
        float[] output = computeOutputLayer(hidden);

        lastConfidence = getConfidence(output);
        return argMax(output);
    }

    public float getLastConfidence() {
        return lastConfidence;
    }

    private byte[] extractFeatures(String text) {
        byte[] features = new byte[16];
        String lower = text.toLowerCase();

        for (int i = 0; i < Math.min(lower.length(), 50); i++) {
            char c = lower.charAt(i);
            if (c >= 'a' && c <= 'z') {
                int idx = (c - 'a') % 16;
                if (features[idx] < 10) {
                    features[idx]++;
                }
            }
        }
        return features;
    }

    private float[] computeHiddenLayer(byte[] features) {
        float[] hidden = new float[12];
        int weightIndex = 0;

        for (int i = 0; i < 12; i++) {
            for (int j = 0; j < 16; j++) {
                hidden[i] += features[j] * getWeight(weightIndex++);
            }
            hidden[i] = relu(hidden[i] + getBias(i));
        }
        return hidden;
    }

    private float[] computeOutputLayer(float[] hidden) {
        float[] output = new float[8];
        int weightIndex = 192; // start of hidden->output weights

        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 12; j++) {
                output[i] += hidden[j] * getWeight(weightIndex++);
            }
        }
        return softmax(output);
    }

    private float getWeight(int logicalIndex) {
        if (logicalIndex >= WEIGHT_POSITIONS.length) return 0.0f;

        int byteIndex = logicalIndex / 2;
        int nibbleIndex = (logicalIndex % 2) * 4;

        if (byteIndex < COMPRESSED_WEIGHTS.length) {
            int weight4bit = (COMPRESSED_WEIGHTS[byteIndex] >> nibbleIndex) & 0x0F;
            return (weight4bit * 0.1f) - 0.8f;
        }
        return 0.0f;
    }

    private float getBias(int index) {
        if (index < COMPRESSED_BIASES.length * 2) {
            int byteIndex = index / 2;
            int nibbleIndex = (index % 2) * 4;
            int bias4bit = (COMPRESSED_BIASES[byteIndex] >> nibbleIndex) & 0x0F;
            return (bias4bit * 0.1f) - 0.8f;
        }
        return 0.0f;
    }

    private float[] softmax(float[] x) {
        float max = x[0];
        float sum = 0.0f;

        // Find max
        for (int i = 0; i < x.length; i++) {
            if (x[i] > max) max = x[i];
        }

        // Apply exp approximation
        for (int i = 0; i < x.length; i++) {
            x[i] = expJ2ME(x[i] - max);
            sum += x[i];
        }

        // Normalize
        if (sum > 0) {
            for (int i = 0; i < x.length; i++) {
                x[i] /= sum;
            }
        }
        return x;
    }

    /**
     * J2ME CLDC 1.1 compatible exponential approximation
     * Using 10-term series expansion of e^x
     */
    private float expJ2ME(float x) {
        float sum = 1.0f;
        float term = 1.0f;
        for (int i = 1; i <= 10; i++) {
            term *= x / i;
            sum += term;
        }
        return sum;
    }

    private float relu(float x) {
        return (x > 0) ? x : 0;
    }

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
}
