// Keyword-based intent backstop. Mirrors ElimuSMSMidlet.isMathQuery and
// isScienceQuery (Java) so the PWA routes high-keyword-confidence questions
// to the local response table even when the on-device classifier's
// quantised confidence sits below the cloud-fallback threshold.
//
// Why this exists: the deployed weights are quantised to 4-bit nibbles and
// score ~63.5% on test, so confidence is often borderline on questions that
// are unambiguously about math or science. Deferring to keywords for those
// two intents trades a small amount of classifier expressiveness for
// dramatically better user-visible accuracy on the high-priority topics.

const MATH_WORDS = [
  "fraction", "percent", "lcm", "hcf", "ratio", "decimal",
  "mean", "mode", "range", "area", "perimeter", "volume",
  "average", "calculat", "multiply", "divide",
] as const;

const SCIENCE_WORDS = [
  "plant", "animal", "photo", "transpir", "root", "leaf", "leaves",
  "seed", "flower", "pollin", "germinat",
  "blood", "heart", "pulse", "vein", "artery",
  "lung", "breath", "digest", "stomach", "bone", "muscle",
  "insect", "spider", "bacteria", "virus",
  "soil", "erosion", "weather", "cloud",
  "ecosystem", "food chain", "reproduct", "adolescen",
  "vertebrat", "mammal", "reptile", "amphibian",
  "lever", "pulley", "matter", "solid", "liquid", "gas",
  "living", "organism", "chlorophyl", "stomata",
] as const;

export function isMathQuery(q: string): boolean {
  // Arithmetic-expression detectors
  if (q.includes("+") || q.includes("*")) return true;
  for (let i = 1; i < q.length - 1; i++) {
    const c = q[i];
    if (c === "/" && isDigit(q[i - 1]!) && isDigit(q[i + 1]!)) return true;
  }
  for (let i = 1; i < q.length; i++) {
    if (q[i] === "-" && (isDigit(q[i - 1]!) || q[i - 1] === " ")) return true;
  }
  const lower = q.toLowerCase();
  return MATH_WORDS.some((w) => lower.includes(w));
}

export function isScienceQuery(q: string): boolean {
  const lower = q.toLowerCase();
  return SCIENCE_WORDS.some((w) => lower.includes(w));
}

function isDigit(c: string): boolean {
  return c >= "0" && c <= "9";
}
