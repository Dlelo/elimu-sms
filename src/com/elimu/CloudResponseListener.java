package com.elimu;

/**
 * Async callback for cloud AI responses. CLDC 1.1 has no Future/Promise API,
 * so SMSManager invokes one of these methods on the worker thread when the
 * HTTP request completes. Implementations must marshal back to the MIDP
 * event-dispatch thread (Display.callSerially) before touching UI.
 */
public interface CloudResponseListener {
    void onResponse(String answer);
    void onError(String reason);
}
