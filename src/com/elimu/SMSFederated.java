package com.elimu;

import java.io.IOException;

import javax.microedition.io.Connector;
import javax.wireless.messaging.BinaryMessage;
import javax.wireless.messaging.MessageConnection;

/**
 * SMS-primary transport for federated learning.
 *
 * The MIDlet's enqueued FL deltas (235 bytes each, already noised and
 * 4-bit-quantised) are split into binary SMS chunks and sent to a
 * gateway short-code configured via the JAD attribute Elimu-FLShortcode.
 * The gateway POSTs each inbound SMS to the cloud server's
 * /sms/inbound webhook, which reassembles the chunks and feeds the
 * recovered delta into the same FedAvg pipeline used by the HTTPS path.
 *
 * Why SMS first: 2G cellular signal is the dominant network condition
 * in rural Kenya; SMS works on it without a data plan, an active data
 * session, or any application running in the foreground. HTTPS
 * piggy-back is the secondary transport (see SMSManager) when the
 * device happens to have data.
 *
 * Wire chunk format (binary SMS, max 140 payload bytes):
 *
 *     [0]  protocol version (0x01)
 *     [1]  chunk_index (0-based)
 *     [2]  chunk_total
 *     [3]  msg_id (random 0..255 to disambiguate concurrent sends)
 *     [4..] up to 136 bytes of the FL delta
 *
 * The 235-byte delta splits into exactly 2 chunks (136 + 99 bytes
 * payload). At Kenyan retail SMS rates of ~KES 1 per outbound message,
 * one round costs ~KES 2 per device.
 *
 * JSR-120 (Wireless Messaging API) is an optional package on CLDC 1.1
 * handsets; if it is unavailable at runtime, configure() leaves
 * SMS-FL disabled and the system falls back to HTTPS-only transport.
 */
public class SMSFederated {

    private static final byte PROTOCOL_VERSION = 0x01;
    private static final int  HEADER_BYTES     = 4;
    private static final int  MAX_PAYLOAD      = 136; // 140 - 4 header

    private static boolean enabled       = false;
    private static String  shortcodeUrl  = null; // e.g., "sms://+254700000000:0"
    private static int     msgIdCounter  = 0;

    /** Configure SMS-FL from the JAD attribute Elimu-FLShortcode. */
    public static void configure(String shortcode) {
        if (shortcode == null || shortcode.length() == 0) {
            enabled = false;
            System.out.println("[SMS-FL] disabled (no Elimu-FLShortcode)");
            return;
        }
        if (shortcode.startsWith("sms://")) {
            shortcodeUrl = shortcode;
        } else {
            StringBuffer sb0 = new StringBuffer("sms://");
            sb0.append(shortcode);
            shortcodeUrl = sb0.toString();
        }
        enabled = true;
        StringBuffer sb = new StringBuffer("[SMS-FL] enabled shortcode=");
        sb.append(shortcodeUrl);
        System.out.println(sb.toString());
    }

    public static boolean isEnabled() { return enabled; }

    /**
     * Send one 235-byte FL delta as 2 chunked binary SMS messages.
     * Returns true if both chunks were dispatched, false on error.
     * Caller (FederatedLearning.flushPendingOpportunistic) drops the
     * delta from the queue on success and retries later on failure.
     */
    public static boolean sendDelta(byte[] payload) {
        if (!enabled || shortcodeUrl == null) return false;
        if (payload == null || payload.length == 0) return false;
        int total = (payload.length + MAX_PAYLOAD - 1) / MAX_PAYLOAD;
        if (total == 0 || total > 255) return false;
        msgIdCounter = (msgIdCounter + 1) & 0xFF;
        int msgId = msgIdCounter;

        MessageConnection conn = null;
        try {
            conn = (MessageConnection) Connector.open(shortcodeUrl);
            for (int idx = 0; idx < total; idx++) {
                int start = idx * MAX_PAYLOAD;
                int end   = start + MAX_PAYLOAD;
                if (end > payload.length) end = payload.length;
                int partLen = end - start;

                byte[] chunk = new byte[HEADER_BYTES + partLen];
                chunk[0] = PROTOCOL_VERSION;
                chunk[1] = (byte) idx;
                chunk[2] = (byte) total;
                chunk[3] = (byte) msgId;
                System.arraycopy(payload, start, chunk, HEADER_BYTES, partLen);

                BinaryMessage bm = (BinaryMessage)
                        conn.newMessage(MessageConnection.BINARY_MESSAGE);
                bm.setPayloadData(chunk);
                conn.send(bm);
            }
            EvaluationLogger.recordFLFlush();
            StringBuffer sb = new StringBuffer("[SMS-FL] sent ");
            sb.append(total); sb.append(" chunks msg_id="); sb.append(msgId);
            System.out.println(sb.toString());
            return true;
        } catch (IOException e) {
            return false;
        } catch (SecurityException e) {
            // Untrusted MIDlet: SMS send blocked by security policy.
            // Disable for the rest of the session to avoid prompting.
            enabled = false;
            System.out.println("[SMS-FL] disabled by security policy");
            return false;
        } catch (Throwable t) {
            return false;
        } finally {
            if (conn != null) {
                try { conn.close(); } catch (Throwable ig) {}
            }
        }
    }
}
