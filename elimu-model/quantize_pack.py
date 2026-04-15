# quantize_pack.py
# pip install joblib numpy
import joblib
import numpy as np
import os
from textwrap import wrap

clf = joblib.load("model_out/clf.joblib")
vec = joblib.load("model_out/vectorizer.joblib")
label_map = joblib.load("model_out/label_map.joblib")

# We're going to turn the linear model coef shape (n_classes, n_features) to a tiny network:
# Strategy: Keep a single hidden layer mapping features->hidden(12)->output(num_classes<=8)
# But for simplicity, we'll quantize the linear model weights and map them to output layer directly if classes <=8.
coefs = clf.coef_  # shape (n_classes, n_features)
intercepts = clf.intercept_

n_classes, n_features = coefs.shape
print("Model shape:", n_classes, n_features)

# simple dimension checks
TARGET_OUTPUTS = max(8, n_classes)
# If n_classes > 8, re-train mapping or do top-K - but for KPSEA intents should be <=8.

# We'll compute a small hidden layer by PCA compression of coefs into hidden_dim=12
from sklearn.decomposition import PCA
HIDDEN_DIM = 12
pca = PCA(n_components=HIDDEN_DIM)
# flatten coefs to shape (n_classes, n_features) -> apply PCA across features
pca.fit(coefs.T)  # careful: we want components mapping feature->hidden
# We'll create weight matrices:
# features -> hidden : W1 (HIDDEN_DIM x n_features)
W1 = pca.components_.astype(np.float32)  # shape HIDDEN_DIM x n_features
# hidden -> output: project coefs to hidden space: W2 (n_classes x HIDDEN_DIM)
W2 = coefs.dot(pca.components_.T).astype(np.float32)  # n_classes x HIDDEN_DIM
b1 = np.zeros(HIDDEN_DIM, dtype=np.float32)
b2 = intercepts.astype(np.float32)

# Quantize to 4-bit: map float range to 0-15 then store as nibble
def quantize4(arr, scale=None):
    # arr: numpy float array
    # choose scale so that arr_scaled in 0..15
    if scale is None:
        a_min, a_max = arr.min(), arr.max()
        # add small margin
        if a_max == a_min:
            a_max = a_min + 1e-3
        scale = (a_max - a_min) / 15.0
        zero = a_min
    else:
        zero, scale = 0.0, scale
    scaled = np.round((arr - zero) / scale).astype(np.int32)
    scaled = np.clip(scaled, 0, 15)
    return scaled.astype(np.uint8), zero, scale

W1_q, z1, s1 = quantize4(W1)
W2_q, z2, s2 = quantize4(W2)
b1_q, zb1, sb1 = quantize4(b1)
b2_q, zb2, sb2 = quantize4(b2)

# pack two 4-bit into one byte
def pack_nibbles(arr):
    flat = arr.flatten().tolist()
    if len(flat) % 2 == 1:
        flat.append(0)
    out = []
    for i in range(0, len(flat), 2):
        hi = flat[i] & 0x0F
        lo = flat[i+1] & 0x0F
        out.append((hi << 4) | lo)
    return out

weights_bytes = pack_nibbles(W1_q.flatten().tolist())
weights2_bytes = pack_nibbles(W2_q.flatten().tolist())
biases_bytes = pack_nibbles(b2_q.flatten().tolist())

def to_java_byte_array(name, byte_list):
    lines = []
    for i in range(0, len(byte_list), 16):
        chunk = byte_list[i:i+16]
        lines.append(", ".join(" (byte)0x%02X" % (b & 0xFF) for b in chunk))
    body = ",\n        ".join(lines)
    return f"private static final byte[] {name} = {{\n        {body}\n    }};"

os.makedirs("model_out/java", exist_ok=True)
with open("model_out/java/weights_w1.java", "w") as f:
    f.write(to_java_byte_array("COMPRESSED_W1", weights_bytes))
with open("model_out/java/weights_w2.java", "w") as f:
    f.write(to_java_byte_array("COMPRESSED_W2", weights2_bytes))
with open("model_out/java/biases_out.java", "w") as f:
    f.write(to_java_byte_array("COMPRESSED_B2", biases_bytes))

meta = {
    "W1_zero": z1, "W1_scale": s1,
    "W2_zero": z2, "W2_scale": s2,
    "b2_zero": zb2, "b2_scale": sb2,
    "hidden_dim": HIDDEN_DIM,
    "n_features": n_features,
    "n_classes": n_classes
}
import json
with open("model_out/meta.json", "w") as f:
    json.dump(meta, f, indent=2)

print("Wrote model_out/java/*.java and meta.json")
