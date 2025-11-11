package com.elimu;

public class SMSManager {
    public static void sendToCloudAI(String question) {
        // Use StringBuffer for J2ME compatibility
        StringBuffer sb = new StringBuffer();
        sb.append("WOULD SEND SMS: ");
        sb.append(question);
        System.out.println(sb.toString());

        UserPreferences.incrementCloudAnswers();
    }
}
