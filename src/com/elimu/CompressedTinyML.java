package com.elimu;

import java.io.*;

/**
 * CompressedTinyML - TinyML model for J2ME/CLDC 1.1.
 * Fully compatible with MIDP.
 * Trained on Kenya CBC Grade 6 Living Things science data (LIVING.pdf)
 * Accuracy: 83.78% test / 80.73% train
 */
public class CompressedTinyML {
    // Model architecture
    private static final int FEATURE_SIZE = 25;
    private static final int HIDDEN_SIZE = 12;
    private static final int OUTPUT_SIZE = 8;

    // TRAINED WEIGHTS - Generated from LIVING.pdf grade 6 science data
    // W1 (12x25) + W2 (8x12) = 300+96 = 396 values = 198 bytes
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

    // Biases: 12 hidden + 8 output = 20 values = 10 bytes
    private static final byte[] COMPRESSED_BIASES = {
            (byte)0xBA, (byte)0xEE, (byte)0xEF, (byte)0xBE, (byte)0xAC, (byte)0xCA, (byte)0x49, (byte)0xB8,
            (byte)0x8B, (byte)0xA7
    };

    private float lastConfidence = 0.0f;

    public void loadModel() {
        StringBuffer sb = new StringBuffer();
        sb.append("CompressedTinyML loaded: ");
        sb.append(COMPRESSED_WEIGHTS.length);
        sb.append(" bytes weights, ");
        sb.append(FEATURE_SIZE);
        sb.append(" features (Living Things model)");
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
        String[] names = {
            "math","science","english","calculat","experiment","grammar",
            "plant","animal","living","photosynthes","habitat","food",
            "water","grow","what/which","how","why","when/where",
            "help","learn","teach","explain","question","answer","social"
        };
        if (index >= 0 && index < names.length) return names[index];
        StringBuffer sb = new StringBuffer("f");
        sb.append(index);
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

    // FEATURE EXTRACTION (25 features) - trained on Kenya CBC Grade 6 Living Things
    private byte[] extractFeatures(String text) {
        byte[] features = new byte[25];
        String lower = text.toLowerCase();

        // Feature 0-8: Subject keywords
        String[] subjectKeywords = {
            "math", "science", "english", "calculat", "experiment",
            "grammar", "plant", "animal", "living"
        };
        for (int i = 0; i < subjectKeywords.length; i++) {
            if (contains(lower, subjectKeywords[i])) features[i] = 1;
        }

        // Extra aliases: fire feature[7]=animal or feature[1]=science for biology terms
        // not covered by the subject keyword list
        if (contains(lower, "insect")     || contains(lower, "vertebr")  ||
            contains(lower, "invertebr")  || contains(lower, "pollinat") ||
            contains(lower, "bee")        || contains(lower, "worm")     ||
            contains(lower, "bird")       || contains(lower, "fish")     ||
            contains(lower, "mammal")     || contains(lower, "reptile")  ||
            contains(lower, "amphibian")) {
            features[7] = 1; // animal
            features[1] = 1; // science
        }
        if (contains(lower, "chlorophyll") || contains(lower, "stomata") ||
            contains(lower, "transpir")    || contains(lower, "leaf")    ||
            contains(lower, "leaves")      || contains(lower, "root")    ||
            contains(lower, "seed")        || contains(lower, "flower")  ||
            contains(lower, "stem")) {
            features[6] = 1; // plant
            features[1] = 1; // science
        }
        // Subject keywords plant/animal/living are all science — fire feature[1] too
        if (features[6] == 1 || features[7] == 1 || features[8] == 1) {
            features[1] = 1;
        }
        // Human body / health science terms
        if (contains(lower, "heart")      || contains(lower, "blood")     ||
            contains(lower, "circul")     || contains(lower, "artery")    ||
            contains(lower, "arteries")   || contains(lower, "vein")      ||
            contains(lower, "capillar")   || contains(lower, "pulse")     ||
            contains(lower, "plasma")     || contains(lower, "haemoglob")) {
            features[1] = 1; // science
        }
        // Reproductive system / adolescence terms
        if (contains(lower, "reproduct")  || contains(lower, "adolescen") ||
            contains(lower, "puberty")    || contains(lower, "ovary")     ||
            contains(lower, "uterus")     || contains(lower, "testis")    ||
            contains(lower, "sperm")      || contains(lower, "menstruat") ||
            contains(lower, "ovulat")     || contains(lower, "fallopian")) {
            features[1] = 1; // science
        }
        // Environment / water conservation terms
        if (contains(lower, "conserv")    || contains(lower, "harvest")   ||
            contains(lower, "recycle")    || contains(lower, "mulch")) {
            features[1] = 1; // science
        }

        // Feature 9-13: Living things specific keywords
        if (contains(lower, "photosynthes")) features[9] = 1;
        if (contains(lower, "habitat"))      features[10] = 1;
        if (contains(lower, "food"))         features[11] = 1;
        if (contains(lower, "water"))        features[12] = 1;
        if (contains(lower, "grow"))         features[13] = 1;

        // Feature 14-17: Question types
        if (contains(lower, "what") || contains(lower, "which")) features[14] = 1;
        if (contains(lower, "how"))                              features[15] = 1;
        if (contains(lower, "why"))                              features[16] = 1;
        if (contains(lower, "when") || contains(lower, "where")) features[17] = 1;

        // Feature 18-23: Educational context
        String[] contextKeywords = {"help", "learn", "teach", "explain", "question", "answer"};
        for (int i = 0; i < contextKeywords.length; i++) {
            if (contains(lower, contextKeywords[i])) features[18 + i] = 1;
        }

        // Feature 24: Social/greeting/farewell cue
        if (contains(lower, "hello") || contains(lower, "hi") ||
            contains(lower, "bye")   || contains(lower, "good")) {
            features[24] = 1;
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
                "what is photosynthesis",
                "how do plants grow",
                "animal habitats",
                "vertebrate animals",
                "food chain living things",
                "math addition problem",
                "english grammar nouns",
                "science experiment",
                "take a quiz",
                "show my progress",
                "good morning",
                "goodbye"
        };

        for (int i = 0; i < testCases.length; i++) {
            debugPrediction(testCases[i]);
        }
    }
}