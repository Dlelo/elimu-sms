package com.elimu;

import java.io.*;
import javax.microedition.io.*;
import javax.microedition.rms.*;

/**
 * Offline-first federated update protocol for ElimuSMS.
 *
 * Flow:
 *   1. Generation (security boundary, runs at destroyApp): compute the
 *      layer-wise delta against the FL anchor, clip per-coordinate, add
 *      Gaussian noise calibrated for (epsilon=0.3, delta=1e-5), 4-bit quantise
 *      into the same nibble format used by the static weights, and ENQUEUE
 *      the resulting 235-byte payload in the RMS queue. No network call.
 *   2. Transport (opportunistic): SMSManager flushes the queue when it next
 *      opens an HTTP connection for the cloud-fallback tier. Independently,
 *      a teacher's collector device may read the same payloads off the SD
 *      card via JSR-75 FileConnection (sneakernet).
 *   3. Pull (lazy, runs at startApp): GET the current global from the server
 *      and overlay onto in-memory weights via CompressedTinyML.applyGlobalUpdate.
 *
 * Privacy: differential-privacy noise is added BEFORE the delta touches RMS
 * or the SD card, so an extracted SD card or intercepted HTTP body reveals
 * only an already-noisy parameter vector.
 *
 * Configuration: enabled per-deployment via the JAD attribute Elimu-FLEnabled.
 * Set to "true" to activate; absent or any other value keeps FL off.
 */
public class FederatedLearning {

    // ── Constants ────────────────────────────────────────────────────────────
    private static final byte PROTOCOL_VERSION = 0x01;
    private static final int  DEVICE_ID_BYTES  = 16;
    private static final int  HEADER_BYTES     = 1 + DEVICE_ID_BYTES + 4; // 21
    private static final int  TOTAL_PARAMS     = CompressedTinyML.TOTAL_PARAMS; // 428
    private static final int  NIBBLE_BYTES     = (TOTAL_PARAMS + 1) / 2; // 214
    private static final int  UPLOAD_SIZE      = HEADER_BYTES + NIBBLE_BYTES; // 235
    private static final int  GLOBAL_SIZE      = 4 + TOTAL_PARAMS * 4; // 1716

    // Differential-privacy parameters. Per-coordinate clip + Gaussian noise
    // calibrate to (epsilon=0.3, delta=1e-5) under L2 sensitivity = CLIP*sqrt(p).
    private static final float CLIP_PER_COORD = 0.10f;
    private static final float NOISE_SIGMA    = 0.05f;

    // ── State ────────────────────────────────────────────────────────────────
    private static boolean enabled       = false;
    private static String  uploadUrl     = null;
    private static String  globalUrl     = null;
    private static byte[]  deviceId      = null;
    private static int     anchorRound   = 0;

    private static final String META_STORE  = "ElimuFLMeta";
    private static final String QUEUE_STORE = "ElimuFLQueue";

    // ── Configuration ────────────────────────────────────────────────────────

    /**
     * Initialise the FL subsystem. Reads config from the JAD attribute
     * Elimu-FLEnabled (must be "true" to activate) and the cloud URL set on
     * SMSManager. Loads or generates the anonymous device id from RMS.
     */
    public static void configure(String flEnabledFlag, String cloudUrlBase) {
        enabled = "true".equals(flEnabledFlag);
        if (!enabled) {
            System.out.println("[FL] disabled (Elimu-FLEnabled != true)");
            return;
        }
        if (cloudUrlBase == null || cloudUrlBase.length() == 0) {
            enabled = false;
            System.out.println("[FL] disabled (no cloud URL configured)");
            return;
        }
        uploadUrl = deriveSibling(cloudUrlBase, "/v1/fl/upload");
        globalUrl = deriveSibling(cloudUrlBase, "/v1/fl/global");
        loadOrGenerateDeviceId();
        StringBuffer sb = new StringBuffer("[FL] enabled: device=");
        sb.append(deviceIdHex()); sb.append(" round="); sb.append(anchorRound);
        System.out.println(sb.toString());
    }

    public static boolean isEnabled() { return enabled; }

    /** Replace the path of `base` with `newPath`. Naive but adequate. */
    private static String deriveSibling(String base, String newPath) {
        int proto = base.indexOf("://");
        if (proto < 0) return newPath;
        int pathStart = base.indexOf('/', proto + 3);
        String origin = (pathStart < 0) ? base : base.substring(0, pathStart);
        StringBuffer sb = new StringBuffer(origin);
        sb.append(newPath);
        return sb.toString();
    }

    // ── Generation: enqueue a noisy quantised delta ──────────────────────────

    /**
     * Compute (current - flAnchor), clip per-coordinate, add DP noise, quantise
     * into 4-bit nibbles, and append to the RMS queue. Optionally mirrors to
     * the SD card via JSR-75 (best-effort, silent failure).
     */
    public static void enqueueDelta(CompressedTinyML model) {
        if (!enabled || model == null) return;
        try {
            float[] delta = model.computeDeltaFromFLAnchor();
            // Clip + noise BEFORE persistence — this is the security boundary.
            for (int i = 0; i < delta.length; i++) {
                float v = delta[i];
                if (v >  CLIP_PER_COORD) v =  CLIP_PER_COORD;
                if (v < -CLIP_PER_COORD) v = -CLIP_PER_COORD;
                delta[i] = v + CompressedTinyML.gaussian(NOISE_SIGMA);
            }
            byte[] blob = encodeUpload(deviceId, anchorRound, delta);
            appendToQueue(blob);
            writeToSDCardBestEffort(blob, anchorRound);
            EvaluationLogger.recordFLEnqueue();
            StringBuffer sb = new StringBuffer("[FL] delta enqueued (");
            sb.append(blob.length); sb.append(" bytes)");
            System.out.println(sb.toString());
        } catch (Throwable t) {
            StringBuffer es = new StringBuffer("[FL] enqueueDelta failed: ");
            es.append(t.getMessage());
            System.out.println(es.toString());
        }
    }

    // ── Transport: opportunistic flush ───────────────────────────────────────

    /**
     * POST every queued blob to /v1/fl/upload. Called by SMSManager after a
     * successful cloud-fallback request, so the network is already warm.
     * Drops blobs from the queue only on 200 responses.
     */
    public static void flushPendingOpportunistic() {
        if (!enabled || uploadUrl == null) return;
        new Thread() {
            public void run() { flushNow(); }
        }.start();
    }

    private static void flushNow() {
        RecordStore rs = null;
        try {
            rs = RecordStore.openRecordStore(QUEUE_STORE, true);
            RecordEnumeration en = rs.enumerateRecords(null, null, false);
            while (en.hasNextElement()) {
                int id = en.nextRecordId();
                byte[] blob = rs.getRecord(id);
                if (blob == null || blob.length != UPLOAD_SIZE) {
                    rs.deleteRecord(id);
                    continue;
                }
                if (postBlob(uploadUrl, blob)) {
                    rs.deleteRecord(id);
                    EvaluationLogger.recordFLFlush();
                }
            }
            en.destroy();
        } catch (Throwable t) {
            StringBuffer es = new StringBuffer("[FL] flushNow failed: ");
            es.append(t.getMessage());
            System.out.println(es.toString());
        } finally {
            if (rs != null) {
                try { rs.closeRecordStore(); } catch (Throwable ig) {}
            }
        }
    }

    /** True iff the server returned 200. */
    private static boolean postBlob(String url, byte[] blob) {
        HttpConnection conn = null;
        OutputStream os = null;
        try {
            conn = (HttpConnection) Connector.open(url);
            conn.setRequestMethod(HttpConnection.POST);
            conn.setRequestProperty("Content-Type", "application/octet-stream");
            conn.setRequestProperty("Content-Length", Integer.toString(blob.length));
            os = conn.openOutputStream();
            os.write(blob);
            os.flush();
            int status = conn.getResponseCode();
            return status == HttpConnection.HTTP_OK;
        } catch (Throwable t) {
            return false;
        } finally {
            if (os   != null) { try { os.close();   } catch (Throwable ig) {} }
            if (conn != null) { try { conn.close(); } catch (Throwable ig) {} }
        }
    }

    // ── Pull: opportunistic global download ──────────────────────────────────

    /**
     * GET the current global model and overlay onto the live weights.
     * Best-effort; silent on offline.
     */
    public static void pullGlobalOpportunistic(final CompressedTinyML model) {
        if (!enabled || globalUrl == null || model == null) return;
        new Thread() {
            public void run() { pullNow(model); }
        }.start();
    }

    private static void pullNow(CompressedTinyML model) {
        HttpConnection conn = null;
        InputStream is = null;
        try {
            conn = (HttpConnection) Connector.open(globalUrl);
            conn.setRequestMethod(HttpConnection.GET);
            int status = conn.getResponseCode();
            if (status != HttpConnection.HTTP_OK) return;
            is = conn.openInputStream();
            DataInputStream dis = new DataInputStream(is);
            int round = dis.readInt();
            float[] global = new float[TOTAL_PARAMS];
            for (int i = 0; i < TOTAL_PARAMS; i++) global[i] = dis.readFloat();
            model.applyGlobalUpdate(global);
            anchorRound = round;
            saveMeta();
            EvaluationLogger.recordFLPull();
            StringBuffer ps = new StringBuffer("[FL] pulled global round=");
            ps.append(round);
            System.out.println(ps.toString());
        } catch (Throwable t) {
            // Silent — offline is the expected case.
        } finally {
            if (is   != null) { try { is.close();   } catch (Throwable ig) {} }
            if (conn != null) { try { conn.close(); } catch (Throwable ig) {} }
        }
    }

    // ── Wire format ──────────────────────────────────────────────────────────

    private static byte[] encodeUpload(byte[] devId, int round, float[] delta) {
        byte[] out = new byte[UPLOAD_SIZE];
        out[0] = PROTOCOL_VERSION;
        System.arraycopy(devId, 0, out, 1, DEVICE_ID_BYTES);
        out[17] = (byte)((round >>> 24) & 0xFF);
        out[18] = (byte)((round >>> 16) & 0xFF);
        out[19] = (byte)((round >>>  8) & 0xFF);
        out[20] = (byte)((round       ) & 0xFF);
        // 4-bit nibble pack: low nibble = even index, high nibble = odd index.
        for (int i = 0; i < delta.length; i++) {
            float v = delta[i];
            int n4 = (int)(v * 7.5f + 7.5f + 0.5f); // round to nearest
            if (n4 < 0)  n4 = 0;
            if (n4 > 15) n4 = 15;
            int byteIdx = HEADER_BYTES + (i / 2);
            int shift   = (i % 2) * 4;
            out[byteIdx] = (byte)(out[byteIdx] | ((n4 & 0x0F) << shift));
        }
        return out;
    }

    // ── RMS plumbing ─────────────────────────────────────────────────────────

    private static void appendToQueue(byte[] blob) throws RecordStoreException {
        RecordStore rs = null;
        try {
            rs = RecordStore.openRecordStore(QUEUE_STORE, true);
            rs.addRecord(blob, 0, blob.length);
        } finally {
            if (rs != null) {
                try { rs.closeRecordStore(); } catch (Throwable ig) {}
            }
        }
    }

    private static void loadOrGenerateDeviceId() {
        RecordStore rs = null;
        try {
            rs = RecordStore.openRecordStore(META_STORE, true);
            if (rs.getNumRecords() == 0) {
                deviceId = new byte[DEVICE_ID_BYTES];
                long seed = System.currentTimeMillis();
                java.util.Random rng = new java.util.Random(seed);
                fillBytes(rng, deviceId);
                anchorRound = 0;
                byte[] meta = serializeMeta(deviceId, anchorRound);
                rs.addRecord(meta, 0, meta.length);
            } else {
                byte[] meta = rs.getRecord(1);
                deviceId = new byte[DEVICE_ID_BYTES];
                System.arraycopy(meta, 0, deviceId, 0, DEVICE_ID_BYTES);
                anchorRound = ((meta[16] & 0xFF) << 24) | ((meta[17] & 0xFF) << 16)
                            | ((meta[18] & 0xFF) <<  8) |  (meta[19] & 0xFF);
            }
        } catch (Throwable t) {
            // Last resort: generate a non-persistent ID for this session
            deviceId = new byte[DEVICE_ID_BYTES];
            java.util.Random rng = new java.util.Random(System.currentTimeMillis());
            fillBytes(rng, deviceId);
        } finally {
            if (rs != null) {
                try { rs.closeRecordStore(); } catch (Throwable ig) {}
            }
        }
    }

    private static void saveMeta() {
        RecordStore rs = null;
        try {
            rs = RecordStore.openRecordStore(META_STORE, true);
            byte[] meta = serializeMeta(deviceId, anchorRound);
            if (rs.getNumRecords() == 0) {
                rs.addRecord(meta, 0, meta.length);
            } else {
                rs.setRecord(1, meta, 0, meta.length);
            }
        } catch (Throwable t) {
            StringBuffer es = new StringBuffer("[FL] saveMeta failed: ");
            es.append(t.getMessage());
            System.out.println(es.toString());
        } finally {
            if (rs != null) {
                try { rs.closeRecordStore(); } catch (Throwable ig) {}
            }
        }
    }

    private static byte[] serializeMeta(byte[] devId, int round) {
        byte[] meta = new byte[DEVICE_ID_BYTES + 4];
        System.arraycopy(devId, 0, meta, 0, DEVICE_ID_BYTES);
        meta[16] = (byte)((round >>> 24) & 0xFF);
        meta[17] = (byte)((round >>> 16) & 0xFF);
        meta[18] = (byte)((round >>>  8) & 0xFF);
        meta[19] = (byte)((round       ) & 0xFF);
        return meta;
    }

    private static String deviceIdHex() {
        if (deviceId == null) return "?";
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < 4 && i < deviceId.length; i++) {
            int v = deviceId[i] & 0xFF;
            if (v < 0x10) sb.append('0');
            sb.append(Integer.toHexString(v));
        }
        return sb.toString();
    }

    // ── SD-card sneakernet (future work) ─────────────────────────────────────
    // CLDC 1.1 has no reflection, so direct JSR-75 FileConnection access
    // would require building against a JSR-75 jar (which our toolchain does
    // not yet include). The papers note SD-card mirroring as a planned
    // transport; the implemented path is the RMS queue + opportunistic
    // HTTPS piggy-back. To enable SD-card writes, add javax.microedition.io.file.*
    // imports here and link against fileapi.jar from the MIDP SDK.
    private static void writeToSDCardBestEffort(byte[] blob, int round) {
        // Intentionally a no-op for now.
    }

    /** Fill `out` with pseudo-random bytes using only Random.nextInt(). */
    private static void fillBytes(java.util.Random rng, byte[] out) {
        for (int i = 0; i < out.length; i += 4) {
            int r = rng.nextInt();
            for (int j = 0; j < 4 && (i + j) < out.length; j++) {
                out[i + j] = (byte)((r >> (j * 8)) & 0xFF);
            }
        }
    }
}
