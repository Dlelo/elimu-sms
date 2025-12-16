package com.elimu;

import java.io.*;

/**
 * CompressedTinyML - TinyML model for J2ME/CLDC 1.1.
 * Fully compatible with MIDP.
 * WITH ENHANCED PLANTS TRAINING - FIXED VERSION
 */
public class CompressedTinyML {
    // Model architecture
    private static final int FEATURE_SIZE = 17;
    private static final int HIDDEN_SIZE = 12;
    private static final int OUTPUT_SIZE = 8;

    // TRAINED WEIGHTS - Generated from Python training with plants enhancement
    private static final byte[] COMPRESSED_WEIGHTS = {
            (byte)0x39, (byte)0xCC, (byte)0x42, (byte)0x3C, (byte)0xA5, (byte)0x9E, (byte)0xC7, (byte)0x79,
            (byte)0xCA, (byte)0xB5, (byte)0x3C, (byte)0x47, (byte)0x37, (byte)0x14, (byte)0x41, (byte)0x41,
            (byte)0x0C, (byte)0xF6, (byte)0x37, (byte)0xCB, (byte)0x66, (byte)0x4D, (byte)0x96, (byte)0x9C,
            (byte)0x97, (byte)0xBA, (byte)0xCB, (byte)0xA9, (byte)0x5B, (byte)0x36, (byte)0x37, (byte)0x65,
            (byte)0x65, (byte)0x45, (byte)0x4C, (byte)0xE6, (byte)0x98, (byte)0xAA, (byte)0xCB, (byte)0x96,
            (byte)0x6B, (byte)0x46, (byte)0x87, (byte)0x77, (byte)0x88, (byte)0x69, (byte)0x87, (byte)0x87,
            (byte)0x97, (byte)0x67, (byte)0x69, (byte)0x78, (byte)0x77, (byte)0x87, (byte)0x77, (byte)0x77,
            (byte)0x77, (byte)0x77, (byte)0x77, (byte)0x77, (byte)0x77, (byte)0x77, (byte)0x77, (byte)0x77,
            (byte)0x77, (byte)0x77, (byte)0x47, (byte)0xD5, (byte)0x7C, (byte)0x4D, (byte)0xD8, (byte)0x57,
            (byte)0x87, (byte)0xA9, (byte)0xAB, (byte)0xB7, (byte)0x7B, (byte)0xA8, (byte)0xA8, (byte)0x8A,
            (byte)0x9B, (byte)0xA8, (byte)0x5B, (byte)0x47, (byte)0x77, (byte)0x86, (byte)0x78, (byte)0x76,
            (byte)0x87, (byte)0x97, (byte)0xA7, (byte)0xE5, (byte)0x66, (byte)0x95, (byte)0x57, (byte)0x4D,
            (byte)0x77, (byte)0x77, (byte)0x77, (byte)0x77, (byte)0x77, (byte)0x77, (byte)0x4C, (byte)0x6C,
            (byte)0x5B, (byte)0x99, (byte)0x88, (byte)0xA9, (byte)0x99, (byte)0x99, (byte)0xC7, (byte)0x4C,
            (byte)0xB1, (byte)0x63, (byte)0xDC, (byte)0x97, (byte)0x3A, (byte)0x9B, (byte)0x7F, (byte)0x43,
            (byte)0x65, (byte)0x57, (byte)0x7B, (byte)0x43, (byte)0xEB, (byte)0x34, (byte)0x7C, (byte)0x8C,
            (byte)0x47, (byte)0xBA, (byte)0xD7, (byte)0x77, (byte)0xDA, (byte)0xAA, (byte)0xC7, (byte)0x66,
            (byte)0xC7, (byte)0x88, (byte)0x2D, (byte)0xD0, (byte)0x61, (byte)0x13, (byte)0x94, (byte)0xE6,
            (byte)0x3A, (byte)0x99, (byte)0xE7, (byte)0x5C, (byte)0x53, (byte)0x54
    };

    private static final byte[] COMPRESSED_BIASES = {
            (byte)0xD0, (byte)0x7C, (byte)0x76, (byte)0xEC, (byte)0xA7, (byte)0xF7, (byte)0x0F, (byte)0xEC,
            (byte)0x42, (byte)0x43
    };

    private float lastConfidence = 0.0f;

    public void loadModel() {
        StringBuffer sb = new StringBuffer();
        sb.append("CompressedTinyML loaded: ");
        sb.append(COMPRESSED_WEIGHTS.length);
        sb.append(" bytes weights, ");
        sb.append(FEATURE_SIZE);
        sb.append(" features");
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

    // Enhanced debug method
    public void debugPrediction(String text) {
        byte[] features = extractFeatures(text);

        // Print features
        StringBuffer featureStr = new StringBuffer("Features: ");
        for (int i = 0; i < features.length; i++) {
            if (features[i] == 1) {
                featureStr.append(getFeatureName(i));
                featureStr.append(" ");
            }
        }
        System.out.println(featureStr.toString());

        byte intent = predict(text);
        float confidence = getLastConfidence();

        StringBuffer debug = new StringBuffer();
        debug.append("DEBUG - Text: '");
        debug.append(text);
        debug.append("' -> Intent: ");
        debug.append(intent);
        debug.append(" (");
        debug.append(getIntentName(intent));
        debug.append("), Confidence: ");
        debug.append(confidence);
        System.out.println(debug.toString());
    }

    private String getFeatureName(int index) {
        StringBuffer sb = new StringBuffer();

        if (index <= 6) {
            sb.append("subj");
            sb.append(index);
            return sb.toString();
        }

        if (index <= 10) {
            sb.append("qtype");
            sb.append(index - 7);
            return sb.toString();
        }

        sb.append("ctx");
        sb.append(index - 11);
        return sb.toString();
    }


    private String getIntentName(byte intentId) {
        switch(intentId) {
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

    // FEATURE EXTRACTION FOR NEW MODEL (17 features)
    private byte[] extractFeatures(String text) {
        byte[] features = new byte[17];
        String lower = text.toLowerCase();

        // Feature 0-6: Subject keywords (plants at position 6)
        String[] subjectKeywords = {"math", "science", "english", "calculate", "experiment", "grammar", "plants"};
        for (int i = 0; i < subjectKeywords.length; i++) {
            if (contains(lower, subjectKeywords[i])) {
                features[i] = 1;
            }
        }

        // Feature 7-10: Question types
        if (contains(lower, "what") || contains(lower, "which")) {
            features[7] = 1;
        }
        if (contains(lower, "how")) {
            features[8] = 1;
        }
        if (contains(lower, "why")) {
            features[9] = 1;
        }
        if (contains(lower, "when") || contains(lower, "where")) {
            features[10] = 1;
        }

        // Feature 11-16: Educational context
        String[] contextKeywords = {"help", "learn", "teach", "explain", "question", "answer"};
        for (int i = 0; i < contextKeywords.length; i++) {
            if (contains(lower, contextKeywords[i])) {
                features[11 + i] = 1;
            }
        }

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
        float[] hidden = new float[HIDDEN_SIZE];
        int weightIndex = 0;

        for (int i = 0; i < HIDDEN_SIZE; i++) {
            float sum = 0.0f;
            for (int j = 0; j < FEATURE_SIZE; j++) {
                sum += features[j] * getWeight(weightIndex++);
            }
            hidden[i] = relu(sum + getBias(i));
        }
        return hidden;
    }

    private float[] computeOutputLayer(float[] hidden) {
        float[] output = new float[OUTPUT_SIZE];
        int weightIndex = FEATURE_SIZE * HIDDEN_SIZE; // start of hidden->output weights

        for (int i = 0; i < OUTPUT_SIZE; i++) {
            float sum = 0.0f;
            for (int j = 0; j < HIDDEN_SIZE; j++) {
                sum += hidden[j] * getWeight(weightIndex++);
            }
            output[i] = sum + getBias(HIDDEN_SIZE + i); // Add output bias
        }

        // Apply softmax
        return softmax(output);
    }

    private float getWeight(int logicalIndex) {
        int byteIndex = logicalIndex / 2;
        int nibbleIndex = (logicalIndex % 2) * 4;

        if (byteIndex < COMPRESSED_WEIGHTS.length) {
            int weight4bit = (COMPRESSED_WEIGHTS[byteIndex] >> nibbleIndex) & 0x0F;
            // IMPORTANT: Convert 4-bit [0,15] to float [-1.0, 1.0]
            // Formula: (weight4bit - 7.5f) / 7.5f
            return (weight4bit - 7.5f) / 7.5f;
        }
        return 0.0f;
    }

    private float getBias(int index) {
        if (index < 20) { // 12 hidden + 8 output biases
            int byteIndex = index / 2;
            int nibbleIndex = (index % 2) * 4;
            int bias4bit = (COMPRESSED_BIASES[byteIndex] >> nibbleIndex) & 0x0F;
            // Same scaling as weights
            return (bias4bit - 7.5f) / 7.5f;
        }
        return 0.0f;
    }

    private float[] softmax(float[] x) {
        float max = -Float.MAX_VALUE;
        float sum = 0.0f;

        // Find max for numerical stability
        for (int i = 0; i < x.length; i++) {
            if (x[i] > max) max = x[i];
        }

        // Apply exp approximation
        for (int i = 0; i < x.length; i++) {
            x[i] = expApprox(x[i] - max);
            sum += x[i];
        }

        // Normalize
        if (sum > 0.00001f) {
            for (int i = 0; i < x.length; i++) {
                x[i] /= sum;
            }
        }
        return x;
    }

    /**
     * Fast exponential approximation for J2ME
     */
    private float expApprox(float x) {
        // Fast exp approximation using limit (1 + x/n)^n
        if (x > 5.0f) return 148.413f;  // e^5
        if (x < -5.0f) return 0.0067f;  // e^-5

        // Use (1 + x/8)^8 approximation for speed
        float temp = 1.0f + x/8.0f;
        float result = temp * temp;  // ^2
        result = result * result;    // ^4
        result = result * result;    // ^8

        return result;
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

    // Test method to verify model works
    public void testModel() {
        System.out.println("=== Model Testing ===");
        String[] testCases = {
                "plants",
                "science plants",
                "plant growth",
                "math addition",
                "english grammar",
                "science experiment",
                "help with plants",
                "what are plants",
                "hello",
                "bye"
        };

        for (String test : testCases) {
            debugPrediction(test);
        }
    }
}