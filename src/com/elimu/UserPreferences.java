package com.elimu;

public class UserPreferences {
    private static int totalQuestions = 0;
    private static int localAnswers = 0;
    private static int cloudAnswers = 0;
    private static int grade = 6;

    public static int getTotalQuestions() { return totalQuestions; }
    public static int getLocalAnswers() { return localAnswers; }
    public static int getCloudAnswers() { return cloudAnswers; }
    public static int getGrade() { return grade; }

    public static void setGrade(int g) { grade = g; }

    public static void incrementLocalAnswers() {
        localAnswers++;
        totalQuestions++;
    }

    public static void incrementCloudAnswers() {
        cloudAnswers++;
        totalQuestions++;
    }
}