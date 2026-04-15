package com.elimu;

public class UserPreferences {
    private static int totalQuestions  = 0;
    private static int localAnswers    = 0;
    private static int cloudAnswers    = 0;
    private static int grade           = 6;

    // Quiz tracking
    private static int quizAttempts      = 0;
    private static int quizTotalCorrect  = 0;
    private static int quizTotalAnswered = 0;

    // Topic categories: Plants=0, Invertebrates=1, Vertebrates=2, Circulatory=3,
    //                   HumanBody=4, Reproduction=5, Matter=6, Ecology=7, Mathematics=8
    private static int[] topicCorrect   = new int[9];
    private static int[] topicAttempted = new int[9];

    public static int getTotalQuestions()   { return totalQuestions; }
    public static int getLocalAnswers()     { return localAnswers; }
    public static int getCloudAnswers()     { return cloudAnswers; }
    public static int getGrade()            { return grade; }

    public static int getQuizAttempts()     { return quizAttempts; }
    public static int getQuizTotalCorrect() { return quizTotalCorrect; }
    public static int getQuizTotalAnswered(){ return quizTotalAnswered; }

    public static void setGrade(int g) { grade = g; }

    public static void incrementLocalAnswers() {
        localAnswers++;
        totalQuestions++;
    }

    public static void incrementCloudAnswers() {
        cloudAnswers++;
        totalQuestions++;
    }

    public static void recordQuizResult(int correct, int total) {
        quizAttempts++;
        quizTotalCorrect  += correct;
        quizTotalAnswered += total;
    }

    public static void recordTopicResult(int topic, boolean correct) {
        if (topic >= 0 && topic < 9) {
            topicAttempted[topic]++;
            if (correct) topicCorrect[topic]++;
        }
    }

    public static int getTopicCorrect(int topic) {
        if (topic >= 0 && topic < 9) return topicCorrect[topic];
        return 0;
    }

    public static int getTopicAttempted(int topic) {
        if (topic >= 0 && topic < 9) return topicAttempted[topic];
        return 0;
    }

    public static int getWeakestTopic() {
        int weakest = 0;
        float weakestScore = 2.0f; // higher than any possible ratio
        for (int t = 0; t < 9; t++) {
            float score;
            if (topicAttempted[t] == 0) {
                score = 0.5f; // neutral for never attempted
            } else {
                score = (float) topicCorrect[t] / (float) topicAttempted[t];
            }
            if (score < weakestScore) {
                weakestScore = score;
                weakest = t;
            }
        }
        return weakest;
    }

    public static String getTopicName(int t) {
        switch (t) {
            case 0: return "Plants";
            case 1: return "Invertebrates";
            case 2: return "Vertebrates";
            case 3: return "Circulatory";
            case 4: return "Human Body";
            case 5: return "Reproduction";
            case 6: return "Matter/Soil";
            case 7: return "Ecology/Water";
            case 8: return "Mathematics";
            default: return "Unknown";
        }
    }
}
