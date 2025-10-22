package com.elimu;

public class CacheSystem {
    private static final String[] recentQuestions = new String[5];
    private static final String[] recentAnswers = new String[5];
    private static int cacheIndex = 0;

    public static void cacheResponse(String question, String answer) {
        recentQuestions[cacheIndex] = question.toLowerCase();
        recentAnswers[cacheIndex] = answer;
        cacheIndex = (cacheIndex + 1) % 5;
    }

    public static String getCached(String question) {
        String lowerQ = question.toLowerCase();
        for (int i = 0; i < recentQuestions.length; i++) {
            if (lowerQ.equals(recentQuestions[i])) {
                return recentAnswers[i];
            }
        }
        return null;
    }
}