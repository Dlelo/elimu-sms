package com.elimu;

/**
 * Async callback for cloud AI responses. CLDC 1.1 has no Future/Promise API,
 * so SMSManager invokes one of these methods on the worker thread when the
 * HTTP request completes. Implementations must marshal back to the MIDP
 * event-dispatch thread (Display.callSerially) before touching UI.
 *
 * The cloud server returns a structured intent label alongside the answer
 * via the X-Elimu-Intent HTTP response header — this is the supervision
 * signal that drives one SGD step on the on-device classifier. The label
 * may be null if the header was absent (older server, or non-JSON path).
 */
public interface CloudResponseListener {
    void onResponse(String answer, String intentLabel);
    void onError(String reason);
}
