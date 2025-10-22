package com.elimu;

public class SMSManager {
    public static void sendToCloudAI(String question) {
        // Placeholder - in real version would send SMS
        System.out.println("WOULD SEND SMS: " + question);
        UserPreferences.incrementCloudAnswers();
    }
}