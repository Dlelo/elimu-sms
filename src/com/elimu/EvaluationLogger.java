package com.elimu;

import java.io.*;
import javax.microedition.rms.*;

/**
 * Session-level analytics logger for longitudinal evaluation of ElimuSMS.
 *
 * Captures: query volume, per-intent prediction distribution, mean confidence,
 * cloud-fallback rate, and user correction (online-learning) events.
 * All counters persist across sessions via RMS so learning outcomes can be
 * measured over weeks of real use — a key requirement for the PhD evaluation.
 *
 * PhD relevance:
 *  - Provides empirical evidence of on-device vs. cloud query split.
 *  - Records user-correction events as a proxy for classification error rate.
 *  - Confidence distribution informs calibration analysis.
 */
public class EvaluationLogger {

    private static final String RMS_STORE   = "ElimuEvalLog";
    private static final int    NUM_INTENTS = 8;

    // Cumulative counters (persist to RMS)
    private static int   totalSessions    = 0;
    private static int   totalQueries     = 0;
    private static int   learnEvents      = 0;  // # times user pressed "Wrong?" and corrected
    private static int   cloudQueries     = 0;
    private static int   confidenceSum100 = 0;  // sum of (confidence * 100) cast to int
    private static int[] intentPredCount  = new int[NUM_INTENTS];

    // ── Session events ────────────────────────────────────────────────────────

    /** Call once at the start of each MIDlet session. */
    public static void newSession() {
        totalSessions++;
    }

    /**
     * Record a classification event.
     * @param intentId   predicted intent (0-7)
     * @param confidence softmax confidence of the top class
     */
    public static void recordPrediction(int intentId, float confidence) {
        totalQueries++;
        if (intentId >= 0 && intentId < NUM_INTENTS) {
            intentPredCount[intentId]++;
        }
        // Store as integer to avoid float accumulation across sessions
        confidenceSum100 += (int) (confidence * 100.0f);
    }

    /** Call when the user presses "Wrong?" and submits a correction topic. */
    public static void recordLearnEvent() {
        learnEvents++;
    }

    /** Call whenever a query is routed to the cloud fallback. */
    public static void recordCloudQuery() {
        cloudQueries++;
    }

    // ── Reporting ────────────────────────────────────────────────────────────

    /**
     * Returns a compact summary string suitable for the progress screen.
     * Example: "Sessions:12  Queries:87\nAvg conf:71%  Cloud:6%\nCorrections:3"
     */
    public static String getSummary() {
        StringBuffer sb = new StringBuffer();
        sb.append("Sessions: ");    sb.append(totalSessions);
        sb.append("  Queries: ");   sb.append(totalQueries);
        if (totalQueries > 0) {
            int avgConf  = confidenceSum100 / totalQueries;
            int pctCloud = (cloudQueries * 100) / totalQueries;
            sb.append("\nAvg conf: "); sb.append(avgConf);  sb.append("%");
            sb.append("  Cloud: ");   sb.append(pctCloud); sb.append("%");
        }
        if (learnEvents > 0) {
            sb.append("\nUser corrections: "); sb.append(learnEvents);
            if (totalQueries > 0) {
                int pctErr = (learnEvents * 100) / totalQueries;
                sb.append(" ("); sb.append(pctErr); sb.append("% error rate)");
            }
        }
        return sb.toString();
    }

    /**
     * Returns per-intent query counts as a compact string.
     * Example: "Math:23 Science:31 Quiz:12 ..."
     */
    public static String getIntentDistribution() {
        String[] names = {"Math","Sci","Eng","Quiz","Help","Prog","Hi","Bye"};
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < NUM_INTENTS; i++) {
            if (intentPredCount[i] > 0) {
                sb.append(names[i]); sb.append(":"); sb.append(intentPredCount[i]); sb.append(" ");
            }
        }
        return sb.length() > 0 ? sb.toString() : "No queries yet";
    }

    // ── RMS persistence ───────────────────────────────────────────────────────

    /** Persist all counters to RMS. Call from destroyApp(). */
    public static void save() {
        RecordStore rs = null;
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            dos.writeInt(totalSessions);
            dos.writeInt(totalQueries);
            dos.writeInt(learnEvents);
            dos.writeInt(cloudQueries);
            dos.writeInt(confidenceSum100);
            for (int i = 0; i < NUM_INTENTS; i++) dos.writeInt(intentPredCount[i]);
            dos.flush();
            byte[] data = baos.toByteArray();
            rs = RecordStore.openRecordStore(RMS_STORE, true);
            if (rs.getNumRecords() == 0) rs.addRecord(data, 0, data.length);
            else                         rs.setRecord(1,   data, 0, data.length);
        } catch (Exception e) {
            StringBuffer eb = new StringBuffer("[EvalLog] save: ");
            eb.append(e.getMessage());
            System.out.println(eb.toString());
        } finally {
            if (rs != null) { try { rs.closeRecordStore(); } catch (Exception ig) {} }
        }
    }

    /** Restore counters from RMS. Call from startApp() before newSession(). */
    public static void load() {
        RecordStore rs = null;
        try {
            rs = RecordStore.openRecordStore(RMS_STORE, false);
            if (rs.getNumRecords() > 0) {
                byte[] data = rs.getRecord(1);
                DataInputStream dis = new DataInputStream(new ByteArrayInputStream(data));
                totalSessions    = dis.readInt();
                totalQueries     = dis.readInt();
                learnEvents      = dis.readInt();
                cloudQueries     = dis.readInt();
                confidenceSum100 = dis.readInt();
                for (int i = 0; i < NUM_INTENTS; i++) intentPredCount[i] = dis.readInt();
            }
        } catch (RecordStoreNotFoundException e) {
            // First install — keep zero defaults
        } catch (Exception e) {
            StringBuffer eb = new StringBuffer("[EvalLog] load: ");
            eb.append(e.getMessage());
            System.out.println(eb.toString());
        } finally {
            if (rs != null) { try { rs.closeRecordStore(); } catch (Exception ig) {} }
        }
    }
}
