package com.elimu;

import javax.microedition.io.*;
import java.io.*;

/**
 * Cloud fallback dispatcher for ElimuSMS.
 *
 * When the on-device TinyML model has insufficient confidence, questions are
 * forwarded to a cloud AI backend via HTTP POST (MIDP 2.0 HttpConnection).
 * The response body is read and delivered to a CloudResponseListener so the
 * MIDlet can surface the cloud-generated answer back to the learner.
 *
 * Design rationale:
 *  - HTTP POST over TCP is used instead of raw SMS (JSR-120) because it
 *    avoids per-SMS carrier costs, supports richer payloads, and works on
 *    both feature phones with GPRS and smart-feature phones with Wi-Fi.
 *  - Background Thread prevents UI blocking; CLDC 1.1 supports java.lang.Thread.
 *  - Retry with exponential backoff handles spotty rural GPRS connectivity.
 *  - URL encoding is implemented without java.net.URLEncoder (not in CLDC 1.1).
 *  - Listener callbacks fire on the worker thread; UI code must marshal back
 *    via Display.callSerially.
 *
 * PhD relevance: the cloud/local split ratio (tracked by EvaluationLogger) is
 * a key metric for evaluating the on-device model's coverage and confidence
 * calibration across real student queries.
 */
public class SMSManager {

    private static final String DEFAULT_CLOUD_API = "http://api.elimu-ai.org/v1/query";
    private static final int    MAX_RETRIES = 3;
    private static final int    READ_BUF    = 256;
    // Cap response payload to avoid OOM on constrained heaps.
    private static final int    MAX_RESPONSE_BYTES = 4096;

    // Mutable so deployments can point at a different backend (Gemini / Groq /
    // Ollama / OpenRouter) by setting the JAD attribute Elimu-CloudURL — see
    // ElimuSMSMidlet.startApp(). Falls back to DEFAULT_CLOUD_API if unset.
    private static String cloudApi = DEFAULT_CLOUD_API;

    /** Override the cloud endpoint at startup from a JAD attribute. */
    public static void setCloudUrl(String url) {
        if (url != null && url.length() > 0) {
            cloudApi = url;
        }
    }

    // ── Conversation memory (rolling 3-turn context) ─────────────────────────
    // The cloud LLM benefits from knowing what the student asked just before;
    // a follow-up like "and what about chlorophyll?" is meaningless without
    // the prior turn. We maintain a tiny in-memory ring of the last
    // CONTEXT_TURNS questions and ship them alongside the current query.
    // No assistant replies are buffered (we don't store cloud answers
    // verbatim) — only the student's own prior questions, which they
    // already typed. So this adds no new privacy concern.
    private static final int CONTEXT_TURNS = 3;
    private static final int CONTEXT_MAX_CHARS = 480; // hard cap on payload bloat
    private static final String[] convBuffer = new String[CONTEXT_TURNS];
    private static int convCount = 0;

    /** Append a question to the rolling context buffer. */
    public static void pushContext(String question) {
        if (question == null || question.length() == 0) return;
        // Shift older turns toward index 0; freshest at the end.
        for (int i = 0; i < CONTEXT_TURNS - 1; i++) {
            convBuffer[i] = convBuffer[i + 1];
        }
        convBuffer[CONTEXT_TURNS - 1] = question;
        if (convCount < CONTEXT_TURNS) convCount++;
    }

    /** Reset the buffer (e.g., after greeting/farewell intent). */
    public static void clearContext() {
        for (int i = 0; i < CONTEXT_TURNS; i++) convBuffer[i] = null;
        convCount = 0;
    }

    /** Build the context preamble; "" if no prior turns. */
    private static String getContextString() {
        if (convCount <= 1) return "";
        StringBuffer sb = new StringBuffer();
        // Include all prior turns except the last (which is the current question).
        int start = CONTEXT_TURNS - convCount;
        int stop  = CONTEXT_TURNS - 1; // exclude the most recent (== current)
        for (int i = start; i < stop; i++) {
            if (convBuffer[i] == null) continue;
            if (sb.length() > 0) sb.append(" | ");
            sb.append(convBuffer[i]);
            if (sb.length() >= CONTEXT_MAX_CHARS) break;
        }
        if (sb.length() > CONTEXT_MAX_CHARS) {
            sb.setLength(CONTEXT_MAX_CHARS);
        }
        return sb.toString();
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Asynchronously dispatch a question to the cloud AI backend and deliver
     * the generated answer (or an error reason) to the listener. Returns
     * immediately; network I/O happens on a background thread.
     */
    public static void sendToCloudAI(final String question,
                                     final CloudResponseListener listener) {
        Thread worker = new Thread() {
            public void run() {
                dispatchToCloud(question, listener);
            }
        };
        worker.start();
        UserPreferences.incrementCloudAnswers();
        EvaluationLogger.recordCloudQuery();
    }

    // ── Network dispatch ──────────────────────────────────────────────────────

    private static void dispatchToCloud(String question,
                                        CloudResponseListener listener) {
        String lastError = "no attempts made";
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            HttpConnection conn = null;
            OutputStream   os   = null;
            InputStream    is   = null;
            try {
                conn = (HttpConnection) Connector.open(cloudApi);
                conn.setRequestMethod(HttpConnection.POST);
                conn.setRequestProperty("Content-Type",
                        "application/x-www-form-urlencoded");
                conn.setRequestProperty("User-Agent",
                        "ElimuSMS/1.0 CLDC-1.1 MIDP-2.0");

                byte[] body = buildPayload(question).getBytes("UTF-8");
                conn.setRequestProperty("Content-Length",
                        Integer.toString(body.length));

                os = conn.openOutputStream();
                os.write(body);
                os.flush();

                int status = conn.getResponseCode();
                StringBuffer log = new StringBuffer("[Cloud] HTTP ");
                log.append(status);
                log.append(" (attempt "); log.append(attempt); log.append(")");
                System.out.println(log.toString());

                if (status == HttpConnection.HTTP_OK) {
                    is = conn.openInputStream();
                    String answer = readResponse(is);
                    String intent = conn.getHeaderField("X-Elimu-Intent");
                    if (listener != null) listener.onResponse(answer, intent);
                    // Opportunistic FL flush — the network is already warm,
                    // so any pending DP-noisy deltas piggy-back on this burst.
                    FederatedLearning.flushPendingOpportunistic();
                    return;
                }
                StringBuffer es = new StringBuffer("HTTP ");
                es.append(status);
                lastError = es.toString();

            } catch (Exception e) {
                lastError = e.getMessage();
                StringBuffer err = new StringBuffer("[Cloud] attempt ");
                err.append(attempt); err.append(" failed: ");
                err.append(lastError == null ? "unknown" : lastError);
                System.out.println(err.toString());
            } finally {
                if (os   != null) { try { os.close();   } catch (Exception ig) {} }
                if (is   != null) { try { is.close();   } catch (Exception ig) {} }
                if (conn != null) { try { conn.close(); } catch (Exception ig) {} }
            }

            // Exponential backoff between retries (200ms, 400ms)
            if (attempt < MAX_RETRIES) {
                try { Thread.sleep(200 * attempt); }
                catch (InterruptedException ie) { break; }
            }
        }
        System.out.println("[Cloud] failed after " + MAX_RETRIES + " attempts.");
        if (listener != null) listener.onError(lastError);
    }

    /** Read response body up to MAX_RESPONSE_BYTES, decoded as UTF-8. */
    private static String readResponse(InputStream is) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] chunk = new byte[READ_BUF];
        int total = 0;
        int n;
        while ((n = is.read(chunk)) != -1) {
            int allowed = MAX_RESPONSE_BYTES - total;
            if (allowed <= 0) break;
            int toWrite = (n < allowed) ? n : allowed;
            buf.write(chunk, 0, toWrite);
            total += toWrite;
            if (total >= MAX_RESPONSE_BYTES) break;
        }
        return new String(buf.toByteArray(), "UTF-8");
    }

    // ── Payload builder ───────────────────────────────────────────────────────

    private static String buildPayload(String question) {
        StringBuffer sb = new StringBuffer("q=");
        sb.append(urlEncode(question));
        sb.append("&grade=6&lang=sw-ke");
        String ctx = getContextString();
        if (ctx.length() > 0) {
            sb.append("&ctx=");
            sb.append(urlEncode(ctx));
        }
        return sb.toString();
    }

    /**
     * RFC 3986-compliant URL encoding without java.net (not in CLDC 1.1).
     * Encodes all characters except unreserved set: A-Z a-z 0-9 - _ . ~
     */
    private static String urlEncode(String s) {
        StringBuffer out = new StringBuffer();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9')
                    || c == '-' || c == '_' || c == '.' || c == '~') {
                out.append(c);
            } else if (c == ' ') {
                out.append('+');
            } else {
                out.append('%');
                int hi = (c >> 4) & 0x0F;
                int lo =  c       & 0x0F;
                out.append((char)(hi < 10 ? '0' + hi : 'A' + hi - 10));
                out.append((char)(lo < 10 ? '0' + lo : 'A' + lo - 10));
            }
        }
        return out.toString();
    }
}
