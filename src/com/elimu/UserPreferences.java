package com.elimu;

import java.io.*;
import javax.microedition.rms.*;

/**
 * Session state, quiz metrics, and adaptive spaced-repetition scheduling.
 *
 * PhD-level addition: SM-2 Spaced Repetition System (SRS) per topic.
 * SM-2 is the algorithm behind Anki/SuperMemo; it schedules review intervals
 * based on recall quality, minimising review load while maximising retention.
 *
 * Reference: Wozniak P.A. (1990). "Optimization of Learning". Master's thesis.
 * Algorithm: if quality >= 3 (correct):
 *   interval = 1 (rep 0) | 6 (rep 1) | prev * EF (rep >= 2)
 *   EF = max(1.3, EF + 0.1 - (5-q)*(0.08+(5-q)*0.02))
 * else: interval = 1, repetitions = 0
 * nextReview = now + interval * 24h
 */
public class UserPreferences {

    // ── Session counters ──────────────────────────────────────────────────────
    private static int totalQuestions = 0;
    private static int localAnswers   = 0;
    private static int cloudAnswers   = 0;
    private static int grade          = 6;

    // ── Quiz tracking ─────────────────────────────────────────────────────────
    private static int quizAttempts      = 0;
    private static int quizTotalCorrect  = 0;
    private static int quizTotalAnswered = 0;

    // Topic categories: Plants=0, Invertebrates=1, Vertebrates=2, Circulatory=3,
    //                   HumanBody=4, Reproduction=5, Matter=6, Ecology=7, Mathematics=8
    private static int[] topicCorrect   = new int[9];
    private static int[] topicAttempted = new int[9];

    // ── SM-2 Spaced Repetition per topic ─────────────────────────────────────
    // EF stored as EF*100 (integer arithmetic only, no float division in inner loop)
    private static int[]  srsRep        = new int[9];   // consecutive correct answers
    private static int[]  srsInterval   = new int[9];   // review interval in days
    private static int[]  srsEF100      = new int[9];   // easiness factor * 100 [130, 250]
    private static long[] srsNextReview = new long[9];  // epoch ms for next due date

    static {
        for (int i = 0; i < 9; i++) srsEF100[i] = 250; // EF starts at 2.5
    }

    private static final long DAY_MS      = 86400000L;
    private static final String SRS_STORE = "ElimuSRS";

    // ── Basic getters / setters ───────────────────────────────────────────────
    public static int getTotalQuestions()    { return totalQuestions; }
    public static int getLocalAnswers()      { return localAnswers; }
    public static int getCloudAnswers()      { return cloudAnswers; }
    public static int getGrade()             { return grade; }
    public static int getQuizAttempts()      { return quizAttempts; }
    public static int getQuizTotalCorrect()  { return quizTotalCorrect; }
    public static int getQuizTotalAnswered() { return quizTotalAnswered; }

    public static void setGrade(int g) { grade = g; }

    public static void incrementLocalAnswers() { localAnswers++; totalQuestions++; }
    public static void incrementCloudAnswers() { cloudAnswers++; totalQuestions++; }

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
        return (topic >= 0 && topic < 9) ? topicCorrect[topic] : 0;
    }

    public static int getTopicAttempted(int topic) {
        return (topic >= 0 && topic < 9) ? topicAttempted[topic] : 0;
    }

    /** Returns the topic index with the lowest correct/attempted ratio. */
    public static int getWeakestTopic() {
        int weakest = 0;
        float weakestScore = 2.0f;
        for (int t = 0; t < 9; t++) {
            float score = (topicAttempted[t] == 0)
                ? 0.5f
                : (float) topicCorrect[t] / (float) topicAttempted[t];
            if (score < weakestScore) { weakestScore = score; weakest = t; }
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

    // ── SM-2 Spaced Repetition ────────────────────────────────────────────────

    /**
     * Update SRS state after a quiz answer.
     * @param topic   topic index 0-8
     * @param quality recall quality 0-5 (5=perfect, 3=correct, 0=blackout)
     *                Use quality=5 for correct, quality=0 for wrong in binary quiz.
     */
    public static void srsUpdate(int topic, int quality) {
        if (topic < 0 || topic >= 9) return;

        if (quality >= 3) {
            int rep  = srsRep[topic];
            int prev = srsInterval[topic];
            int ef   = srsEF100[topic];
            int next;
            if      (rep == 0) next = 1;
            else if (rep == 1) next = 6;
            else               next = (prev * ef + 50) / 100; // round(prev * EF)
            srsInterval[topic] = next;
            srsRep[topic]++;
        } else {
            srsRep[topic]      = 0;
            srsInterval[topic] = 1;
        }

        // EF update: EF' = max(1.30, EF + 0.10 - (5-q)*(0.08 + (5-q)*0.02))
        // Scaled: EF'*100 = max(130, EF*100 + 10 - (5-q)*(8 + (5-q)*2))
        int q     = quality;
        int delta = 10 - (5 - q) * (8 + (5 - q) * 2);
        srsEF100[topic] = Math.max(130, srsEF100[topic] + delta);

        srsNextReview[topic] = System.currentTimeMillis()
                + (long) srsInterval[topic] * DAY_MS;
    }

    /**
     * Returns the topic most overdue for review (i.e., nextReview <= now + 24h),
     * or -1 if nothing is currently due.
     */
    public static int getDueTopicForReview() {
        long now      = System.currentTimeMillis();
        long earliest = Long.MAX_VALUE;
        int  dueTopic = -1;
        for (int i = 0; i < 9; i++) {
            if (srsNextReview[i] > 0 && srsNextReview[i] <= now + DAY_MS) {
                if (srsNextReview[i] < earliest) {
                    earliest = srsNextReview[i];
                    dueTopic = i;
                }
            }
        }
        return dueTopic;
    }

    /** Human-readable review status for a topic. */
    public static String getSRSStatus(int topic) {
        if (topic < 0 || topic >= 9 || srsNextReview[topic] == 0) return "New";
        long diff = srsNextReview[topic] - System.currentTimeMillis();
        if (diff <= 0)        return "Due now!";
        long days = diff / DAY_MS;
        if (days == 0)        return "Due today";
        if (days == 1)        return "Tomorrow";
        StringBuffer sb = new StringBuffer("In ");
        sb.append(days); sb.append("d");
        return sb.toString();
    }

    // ── SRS RMS persistence ───────────────────────────────────────────────────

    /** Save SRS state across sessions. Call from destroyApp(). */
    public static void saveSRS() {
        RecordStore rs = null;
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            for (int i = 0; i < 9; i++) dos.writeInt(srsRep[i]);
            for (int i = 0; i < 9; i++) dos.writeInt(srsInterval[i]);
            for (int i = 0; i < 9; i++) dos.writeInt(srsEF100[i]);
            for (int i = 0; i < 9; i++) dos.writeLong(srsNextReview[i]);
            dos.flush();
            byte[] data = baos.toByteArray();
            rs = RecordStore.openRecordStore(SRS_STORE, true);
            if (rs.getNumRecords() == 0) rs.addRecord(data, 0, data.length);
            else                         rs.setRecord(1,   data, 0, data.length);
        } catch (Exception e) {
            StringBuffer eb = new StringBuffer("[SRS] save: ");
            eb.append(e.getMessage());
            System.out.println(eb.toString());
        } finally {
            if (rs != null) { try { rs.closeRecordStore(); } catch (Exception ig) {} }
        }
    }

    /** Restore SRS state from RMS. Call from startApp(). */
    public static void loadSRS() {
        RecordStore rs = null;
        try {
            rs = RecordStore.openRecordStore(SRS_STORE, false);
            if (rs.getNumRecords() > 0) {
                byte[] data = rs.getRecord(1);
                DataInputStream dis = new DataInputStream(new ByteArrayInputStream(data));
                for (int i = 0; i < 9; i++) srsRep[i]        = dis.readInt();
                for (int i = 0; i < 9; i++) srsInterval[i]   = dis.readInt();
                for (int i = 0; i < 9; i++) srsEF100[i]      = dis.readInt();
                for (int i = 0; i < 9; i++) srsNextReview[i] = dis.readLong();
            }
        } catch (RecordStoreNotFoundException e) {
            // First install — defaults already set in static initialiser
        } catch (Exception e) {
            StringBuffer eb = new StringBuffer("[SRS] load: ");
            eb.append(e.getMessage());
            System.out.println(eb.toString());
        } finally {
            if (rs != null) { try { rs.closeRecordStore(); } catch (Exception ig) {} }
        }
    }
}
