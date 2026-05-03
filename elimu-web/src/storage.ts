// IndexedDB persistence for the PWA. Replaces the J2ME RecordStore.
//
// Two object stores:
//   weights     — single record id="current" holding the 428-float
//                 model parameters.
//   fl_queue    — auto-increment records each holding one Uint8Array
//                 (235-byte FL upload payload waiting to flush).

const DB_NAME = "elimu";
const DB_VERSION = 1;
const STORE_WEIGHTS = "weights";
const STORE_QUEUE   = "fl_queue";

let dbPromise: Promise<IDBDatabase> | null = null;

function open(): Promise<IDBDatabase> {
  if (dbPromise) return dbPromise;
  dbPromise = new Promise((resolve, reject) => {
    const req = indexedDB.open(DB_NAME, DB_VERSION);
    req.onupgradeneeded = () => {
      const db = req.result;
      if (!db.objectStoreNames.contains(STORE_WEIGHTS)) {
        db.createObjectStore(STORE_WEIGHTS);
      }
      if (!db.objectStoreNames.contains(STORE_QUEUE)) {
        db.createObjectStore(STORE_QUEUE, { autoIncrement: true });
      }
    };
    req.onsuccess = () => resolve(req.result);
    req.onerror   = () => reject(req.error);
  });
  return dbPromise;
}

function tx(db: IDBDatabase, store: string, mode: IDBTransactionMode) {
  return db.transaction(store, mode).objectStore(store);
}

// ── Weights ─────────────────────────────────────────────────────────────────

export async function saveWeights(flat: Float32Array): Promise<void> {
  const db = await open();
  return new Promise((resolve, reject) => {
    const buf = new Float32Array(flat).buffer; // copy so we own the bytes
    const req = tx(db, STORE_WEIGHTS, "readwrite").put(buf, "current");
    req.onsuccess = () => resolve();
    req.onerror   = () => reject(req.error);
  });
}

export async function loadWeights(): Promise<Float32Array | null> {
  const db = await open();
  return new Promise((resolve, reject) => {
    const req = tx(db, STORE_WEIGHTS, "readonly").get("current");
    req.onsuccess = () => {
      const buf = req.result as ArrayBuffer | undefined;
      resolve(buf ? new Float32Array(buf) : null);
    };
    req.onerror = () => reject(req.error);
  });
}

// ── FL queue ────────────────────────────────────────────────────────────────

export async function enqueueFL(payload: Uint8Array): Promise<void> {
  const db = await open();
  return new Promise((resolve, reject) => {
    const buf = new Uint8Array(payload).buffer;
    const req = tx(db, STORE_QUEUE, "readwrite").add(buf);
    req.onsuccess = () => resolve();
    req.onerror   = () => reject(req.error);
  });
}

/** Drain the queue, calling `send(payload)` per entry. Successful entries are
 *  deleted; failures keep the entry for the next flush attempt. */
export async function drainFL(
  send: (payload: Uint8Array) => Promise<boolean>,
): Promise<{ flushed: number; remaining: number }> {
  const db = await open();
  const entries: { key: IDBValidKey; payload: Uint8Array }[] = [];
  await new Promise<void>((resolve, reject) => {
    const req = tx(db, STORE_QUEUE, "readonly").openCursor();
    req.onsuccess = () => {
      const cur = req.result;
      if (cur) {
        entries.push({ key: cur.key, payload: new Uint8Array(cur.value as ArrayBuffer) });
        cur.continue();
      } else resolve();
    };
    req.onerror = () => reject(req.error);
  });

  let flushed = 0;
  for (const { key, payload } of entries) {
    const ok = await send(payload).catch(() => false);
    if (ok) {
      await new Promise<void>((resolve, reject) => {
        const req = tx(db, STORE_QUEUE, "readwrite").delete(key);
        req.onsuccess = () => resolve();
        req.onerror   = () => reject(req.error);
      });
      flushed++;
    }
  }
  return { flushed, remaining: entries.length - flushed };
}

// ── Anonymous device ID ─────────────────────────────────────────────────────

export async function loadOrCreateDeviceId(): Promise<Uint8Array> {
  const db = await open();
  const existing = await new Promise<ArrayBuffer | undefined>((resolve, reject) => {
    const req = tx(db, STORE_WEIGHTS, "readonly").get("device_id");
    req.onsuccess = () => resolve(req.result as ArrayBuffer | undefined);
    req.onerror   = () => reject(req.error);
  });
  if (existing) return new Uint8Array(existing);

  const fresh = new Uint8Array(16);
  crypto.getRandomValues(fresh);
  await new Promise<void>((resolve, reject) => {
    const req = tx(db, STORE_WEIGHTS, "readwrite").put(fresh.buffer, "device_id");
    req.onsuccess = () => resolve();
    req.onerror   = () => reject(req.error);
  });
  return fresh;
}
