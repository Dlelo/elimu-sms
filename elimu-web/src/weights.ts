// Quantised weights bundled with the app, byte-identical to the J2ME
// CompressedTinyML.COMPRESSED_WEIGHTS / COMPRESSED_BIASES arrays.
// Trained by elimu-model/train_and_pack.py on the STEM-only corpus
// (english_help intent retired). Quantised test acc ≈ 71.8%.
//
// The on-device classifier overlays the federated global model on top of
// these factory defaults at startup, so a freshly-installed PWA already
// works offline; subsequent /v1/fl/global pulls keep it in sync with the
// cohort.
//
// Layout (low nibble = even index, high nibble = odd index):
//   W1: 12 × 26 = 312 nibbles (indices 0..311)
//   W2: 8  × 12 = 96  nibbles (indices 312..407)
// Total: 408 nibbles → 204 bytes.
//
// Biases (10 bytes = 20 nibbles):
//   b1: 12 nibbles (indices 0..11)
//   b2: 8  nibbles (indices 12..19)

export const COMPRESSED_WEIGHTS_HEX =
  "7CB7777777877788788887776668887777787777778777877A857777878777878877787788888778887777778777777776677779678877777777777787887889666677777778777788778A8995C6C7697888988888987876877795C696788998888868788A888678777777777877778888898886A6666877777777778887897997487A97888888888895779B699977778687777777776778777777AD9D878895777A767767CB6778777777776777A688595886787758A895B96A7997775998535697578596C68678B6694AA7";

export const COMPRESSED_BIASES_HEX = "A997999AB99888B0A67B";

export function hexToBytes(hex: string): Uint8Array {
  const out = new Uint8Array(hex.length / 2);
  for (let i = 0; i < out.length; i++) {
    out[i] = parseInt(hex.substr(i * 2, 2), 16);
  }
  return out;
}
