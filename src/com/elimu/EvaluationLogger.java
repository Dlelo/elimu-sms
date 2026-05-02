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

    // FL counters (persist to RMS) — H_5 survival-analysis inputs
    private static int   flEnqueues = 0; // deltas computed + queued at destroyApp
    private static int   flFlushes  = 0; // queued blobs successfully POSTed
    private static int   flPulls    = 0; // global model fetches

    // NASA-TLX cognitive-load survey responses (H_4 mediator).
    // Each session yields up to one (6-int) record appended to tlxHistory.
    // Stored in-memory for the session and not yet persisted across runs;
    // the per-session aggregate is the analysis unit.
    private static int   tlxSessionsRecorded = 0;
    private static int[] tlxSumByDim = new int[6]; // running sum per dimension

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

    /** FL: a noisy delta has been queued at destroyApp(). */
    public static void recordFLEnqueue() { flEnqueues++; }

    /** FL: a queued delta has been successfully POSTed to the server. */
    public static void recordFLFlush() { flFlushes++; }

    /** FL: a fresh global model has been pulled from the server. */
    public static void recordFLPull() { flPulls++; }

    /**
     * Record a completed NASA-TLX response. `scores` is 6 integers,
     * each in [1, 5], in the canonical TLXSurvey dimension order.
     */
    public static void recordTLX(int[] scores) {
        if (scores == null || scores.length != 6) return;
        for (int i = 0; i < 6; i++) {
            int s = scores[i];
            if (s < 1) s = 1;
            if (s > 5) s = 5;
            tlxSumByDim[i] += s;
        }
        tlxSessionsRecorded++;
    }

    /** Mean per-dimension TLX score across all recorded sessions. */
    public static float[] getTLXMeans() {
        float[] means = new float[6];
        if (tlxSessionsRecorded == 0) return means;
        for (int i = 0; i < 6; i++) {
            means[i] = tlxSumByDim[i] / (float) tlxSessionsRecorded;
        }
        return means;
    }

    public static int getTLXResponseCount() { return tlxSessionsRecorded; }

    /**
     * Compact teacher-readable report. Designed to fit one phone screen
     * (~8 lines, <300 chars) and answer "how is this learner doing?" at
     * a glance during weekly SD-card pickup. Includes:
     *   - questions asked, cloud rate, mean confidence
     *   - top-3 intents the learner cared about
     *   - FL participation (rounds enqueued / pulled)
     *   - average cognitive load (NASA-TLX) if any responses recorded
     */
    public static String getThisWeekReport() {
        StringBuffer sb = new StringBuffer("=== This Week ===\n");
        sb.append("Questions: ");   sb.append(totalQueries);
        if (totalQueries > 0) {
            int pctCloud = (cloudQueries * 100) / totalQueries;
            int avgConf  = confidenceSum100 / totalQueries;
            sb.append(" (");        sb.append(pctCloud);
            sb.append("% cloud, conf "); sb.append(avgConf); sb.append("%)");
        }
        sb.append("\nTop topics: "); sb.append(topThreeIntentsString());
        sb.append("\nFL: enq=");     sb.append(flEnqueues);
        sb.append(" flush=");        sb.append(flFlushes);
        sb.append(" pulls=");        sb.append(flPulls);
        if (tlxSessionsRecorded > 0) {
            float[] tlx = getTLXMeans();
            float total = 0;
            for (int i = 0; i < tlx.length; i++) total += tlx[i];
            int avg10 = (int)((total / tlx.length) * 10);
            sb.append("\nLoad (NASA-TLX): ");
            sb.append(avg10 / 10); sb.append("."); sb.append(avg10 % 10);
            sb.append("/5  (n=");  sb.append(tlxSessionsRecorded);
            sb.append(")");
        }
        return sb.toString();
    }

    private static String topThreeIntentsString() {
        String[] names = {"math","sci","eng","quiz","help","prog","hi","bye"};
        // Insertion-sort indices 0..7 by descending intentPredCount
        int[] order = new int[NUM_INTENTS];
        for (int i = 0; i < NUM_INTENTS; i++) order[i] = i;
        for (int i = 1; i < NUM_INTENTS; i++) {
            int key = order[i];
            int j   = i - 1;
            while (j >= 0 && intentPredCount[order[j]] < intentPredCount[key]) {
                order[j + 1] = order[j];
                j--;
            }
            order[j + 1] = key;
        }
        StringBuffer sb = new StringBuffer();
        int shown = 0;
        for (int k = 0; k < NUM_INTENTS && shown < 3; k++) {
            int id = order[k];
            if (intentPredCount[id] == 0) break;
            if (shown > 0) sb.append(", ");
            sb.append(names[id]); sb.append("(");
            sb.append(intentPredCount[id]); sb.append(")");
            shown++;
        }
        if (shown == 0) sb.append("none yet");
        return sb.toString();
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
