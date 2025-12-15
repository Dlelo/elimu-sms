package com.elimu;

import java.io.*;

/**
 * CompressedTinyML - TinyML model for J2ME/CLDC 1.1.
 * Fully compatible with MIDP.
 * WITH TRAINED WEIGHTS FROM PYTHON
 */
public class CompressedTinyML {
    // TIER 1 OPTIMIZATION: 4-bit quantization + pruning

    private static final int NUM_WEIGHTS = 384; // Reduced from 1968 (80% reduction)
    private static final int NUM_BIASES = 20;

    // TRAINED WEIGHTS - Generated from Python training
    private static final byte[] COMPRESSED_WEIGHTS = {
            (byte)0xAD, (byte)0xCB, (byte)0x32, (byte)0x9E, (byte)0x6D, (byte)0x2D, (byte)0x73, (byte)0xBD,
            (byte)0x4C, (byte)0x3A, (byte)0x50, (byte)0x12, (byte)0x6D, (byte)0x2C, (byte)0xD7, (byte)0x41,
            (byte)0x6D, (byte)0xF5, (byte)0x99, (byte)0xCA, (byte)0x34, (byte)0xBE, (byte)0x5D, (byte)0x5B,
            (byte)0x67, (byte)0xCB, (byte)0x8C, (byte)0x7C, (byte)0x47, (byte)0x75, (byte)0x4A, (byte)0x4B,
            (byte)0xB7, (byte)0x56, (byte)0x4E, (byte)0xD4, (byte)0x58, (byte)0xA7, (byte)0x96, (byte)0x76,
            (byte)0x97, (byte)0x76, (byte)0x57, (byte)0x98, (byte)0xA7, (byte)0x79, (byte)0x97, (byte)0x95,
            (byte)0x77, (byte)0x77, (byte)0x77, (byte)0x77, (byte)0x77, (byte)0x77, (byte)0x77, (byte)0x77,
            (byte)0x77, (byte)0x77, (byte)0x77, (byte)0x77, (byte)0x57, (byte)0x45, (byte)0x88, (byte)0xEF,
            (byte)0x4E, (byte)0x59, (byte)0x58, (byte)0xB9, (byte)0x89, (byte)0x6B, (byte)0x8A, (byte)0x97,
            (byte)0x77, (byte)0x77, (byte)0x77, (byte)0x77, (byte)0x77, (byte)0x77, (byte)0x77, (byte)0x77,
            (byte)0x77, (byte)0x77, (byte)0x77, (byte)0x77, (byte)0x74, (byte)0xD5, (byte)0x4A, (byte)0xA6,
            (byte)0xB8, (byte)0xE9, (byte)0x77, (byte)0x77, (byte)0x77, (byte)0x77, (byte)0x77, (byte)0x77,
            (byte)0xA7, (byte)0x78, (byte)0x24, (byte)0x99, (byte)0xB3, (byte)0xC4, (byte)0x58, (byte)0x9A,
            (byte)0xBC, (byte)0x2A, (byte)0x26, (byte)0xA7, (byte)0xCE, (byte)0xD5, (byte)0x38, (byte)0x6A,
            (byte)0x44, (byte)0xB4, (byte)0xAC, (byte)0xB9, (byte)0x2F, (byte)0x7A, (byte)0xA8, (byte)0x67,
            (byte)0xC5, (byte)0x58, (byte)0xF7, (byte)0x63, (byte)0xCD, (byte)0x34, (byte)0xE8, (byte)0x67,
            (byte)0x77, (byte)0x8D, (byte)0x8C, (byte)0x89, (byte)0xE2, (byte)0xD5, (byte)0xE1, (byte)0x10,
            (byte)0x30, (byte)0xCD, (byte)0x18, (byte)0x34, (byte)0xD5, (byte)0x68, (byte)0x84, (byte)0x95
    };

    private static final short[] WEIGHT_POSITIONS = {
            0, 1, 2, 3, 4, 5, 6, 7, 8, 9,
            10, 11, 12, 13, 14, 15, 16, 17, 18, 19,
            20, 21, 22, 23, 24, 25, 26, 27, 28, 29,
            30, 31, 32, 33, 34, 35, 36, 37, 38, 39,
            40, 41, 42, 43, 44, 45, 46, 47, 48, 49,
            50, 51, 52, 53, 54, 55, 56, 57, 58, 59,
            60, 61, 62, 63, 64, 65, 66, 67, 68, 69,
            70, 71, 72, 73, 74, 75, 76, 77, 78, 79,
            80, 81, 82, 83, 84, 85, 86, 87, 88, 89,
            90, 91, 92, 93, 94, 95, 96, 97, 98, 99,
            100, 101, 102, 103, 104, 105, 106, 107, 108, 109,
            110, 111, 112, 113, 114, 115, 116, 117, 118, 119,
            120, 121, 122, 123, 124, 125, 126, 127, 128, 129,
            130, 131, 132, 133, 134, 135, 136, 137, 138, 139,
            140, 141, 142, 143, 144, 145, 146, 147, 148, 149,
            150, 151, 152, 153, 154, 155, 156, 157, 158, 159,
            160, 161, 162, 163, 164, 165, 166, 167, 168, 169,
            170, 171, 172, 173, 174, 175, 176, 177, 178, 179,
            180, 181, 182, 183, 184, 185, 186, 187, 188, 189,
            190, 191, 192, 193, 194, 195, 196, 197, 198, 199,
            200, 201, 202, 203, 204, 205, 206, 207, 208, 209,
            210, 211, 212, 213, 214, 215, 216, 217, 218, 219,
            220, 221, 222, 223, 224, 225, 226, 227, 228, 229,
            230, 231, 232, 233, 234, 235, 236, 237, 238, 239,
            240, 241, 242, 243, 244, 245, 246, 247, 248, 249,
            250, 251, 252, 253, 254, 255, 256, 257, 258, 259,
            260, 261, 262, 263, 264, 265, 266, 267, 268, 269,
            270, 271, 272, 273, 274, 275, 276, 277, 278, 279,
            280, 281, 282, 283, 284, 285, 286, 287
    };

    private static final byte[] COMPRESSED_BIASES = {
            (byte)0xFB, (byte)0x98, (byte)0x6C, (byte)0x14, (byte)0x10, (byte)0x69, (byte)0x0F, (byte)0xDC,
            (byte)0x8F, (byte)0xA6
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

    // Debug method for testing
    public void debugPrediction(String text) {
        byte[] features = extractFeatures(text);
        byte intent = predict(text);
        float confidence = getLastConfidence();

        // Use StringBuffer instead of string concatenation for J2ME
        StringBuffer debug = new StringBuffer();
        debug.append("DEBUG - Text: ");
        debug.append(text);
        debug.append(", Intent: ");
        debug.append(intent);
        debug.append(", Confidence: ");
        debug.append(confidence);
        System.out.println(debug.toString());
    }

    private byte[] extractFeatures(String text) {
        byte[] features = new byte[16];
        String lower = text.toLowerCase();

        // Feature 0-5: Subject keywords
        String[] subjects = {"math", "science", "english", "calculate", "experiment", "grammar",
                "plants", "animals", "weather", "sunlight", "growth"};
        for (int i = 0; i < subjects.length; i++) {
            if (contains(lower, subjects[i])) {
                features[i] = 1;
            }
        }

        // Feature 6-9: Question type
        if (contains(lower, "what") || contains(lower, "which")) features[6] = 1;
        if (contains(lower, "how")) features[7] = 1;
        if (contains(lower, "why")) features[8] = 1;
        if (contains(lower, "when") || contains(lower, "where")) features[9] = 1;

        // Feature 10-15: Educational context
        if (contains(lower, "help")) features[10] = 1;
        if (contains(lower, "learn")) features[11] = 1;
        if (contains(lower, "teach")) features[12] = 1;
        if (contains(lower, "explain")) features[13] = 1;
        if (contains(lower, "question")) features[14] = 1;
        if (contains(lower, "answer")) features[15] = 1;

        return features;
    }

    /**
     * J2ME compatible string contains method
     * CLDC 1.1 doesn't have String.contains()
     */
    private boolean contains(String str, String substring) {
        return str.indexOf(substring) != -1;
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