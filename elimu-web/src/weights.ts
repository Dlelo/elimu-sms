// Quantised weights bundled with the app, byte-identical to the J2ME
// CompressedTinyML.COMPRESSED_WEIGHTS / COMPRESSED_BIASES arrays.
// Trained by elimu-model/train_and_pack.py; quantised test acc ≈ 63.5%.
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
  "7EC5678877888888788988767558887777787777778776778B657778877777878778788887887778787787878878777777788787778777777777777787787779777676778888788777777B7777D6E6666889888888887774888794B66B9888888888677787787658577CB7777877778988888775957674688877788797877B7878599996788888888867778B788767798777777777778778768787BE9F776865678A677767BE7569858966B65D67C588797885667877B864C75A7887576989655587678595D77778D75A49A6";

export const COMPRESSED_BIASES_HEX = "BA7899BBBA9958A9945A";

export function hexToBytes(hex: string): Uint8Array {
  const out = new Uint8Array(hex.length / 2);
  for (let i = 0; i < out.length; i++) {
    out[i] = parseInt(hex.substr(i * 2, 2), 16);
  }
  return out;
}
