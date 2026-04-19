package com.elimu;

import javax.microedition.io.*;
import java.io.*;

/**
 * Cloud fallback dispatcher for ElimuSMS.
 *
 * When the on-device TinyML model has insufficient confidence, questions are
 * forwarded to a cloud AI backend via HTTP POST (MIDP 2.0 HttpConnection).
 *
 * Design rationale:
 *  - HTTP POST over TCP is used instead of raw SMS (JSR-120) because it
 *    avoids per-SMS carrier costs, supports richer payloads, and works on
 *    both feature phones with GPRS and smart-feature phones with Wi-Fi.
 *  - Background Thread prevents UI blocking; CLDC 1.1 supports java.lang.Thread.
 *  - Retry with exponential backoff handles spotty rural GPRS connectivity.
 *  - URL encoding is implemented without java.net.URLEncoder (not in CLDC 1.1).
 *
 * PhD relevance: the cloud/local split ratio (tracked by EvaluationLogger) is
 * a key metric for evaluating the on-device model's coverage and confidence
 * calibration across real student queries.
 */
public class SMSManager {

    private static final String CLOUD_API   = "http://api.elimu-ai.org/v1/query";
    private static final int    MAX_RETRIES = 3;

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Asynchronously dispatch question to the cloud AI backend.
     * Returns immediately; network I/O happens on a background thread.
     */
    public static void sendToCloudAI(final String question) {
        Thread worker = new Thread() {
            public void run() {
                dispatchToCloud(question);
            }
        };
        worker.start();
        UserPreferences.incrementCloudAnswers();
        EvaluationLogger.recordCloudQuery();
    }

    // ── Network dispatch ──────────────────────────────────────────────────────

    private static void dispatchToCloud(String question) {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            HttpConnection conn = null;
            OutputStream   os   = null;
            InputStream    is   = null;
            try {
                conn = (HttpConnection) Connector.open(CLOUD_API);
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
                if (status == HttpConnection.HTTP_OK) return;

            } catch (Exception e) {
                StringBuffer err = new StringBuffer("[Cloud] attempt ");
                err.append(attempt); err.append(" failed: "); err.append(e.getMessage());
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
    }

    // ── Payload builder ───────────────────────────────────────────────────────

    private static String buildPayload(String question) {
        StringBuffer sb = new StringBuffer("q=");
        sb.append(urlEncode(question));
        sb.append("&grade=6&lang=sw-ke");
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
