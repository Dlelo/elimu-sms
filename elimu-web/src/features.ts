// 26-binary-feature extractor mirroring CompressedTinyML.extractFeatures
// (Java) and elimu-model/train_and_pack.py:extract_features (Python).
//
// Keeping all three implementations in sync is what makes federated learning
// possible across J2ME phones, Python simulators, and this PWA — they all
// extract the same features from the same text and therefore share weights.

export const FEATURE_SIZE = 26;

const SUBJECT_KW = ["math", "science", "english", "calculat", "experiment",
                    "grammar", "plant", "animal", "living"];
const BIO_ANIMAL = ["insect", "vertebr", "invertebr", "pollinat", "bee",
                    "worm", "bird", "fish", "mammal", "reptile", "amphibian"];
const PLANT_KW   = ["chlorophyll", "stomata", "transpir", "leaf", "leaves",
                    "root", "seed", "flower", "stem"];
const BODY_KW    = ["heart", "blood", "circul", "artery", "arteries", "vein",
                    "capillar", "pulse", "plasma", "haemoglob"];
const REPROD_KW  = ["reproduct", "adolescen", "puberty", "ovary", "uterus",
                    "testis", "sperm", "menstruat", "ovulat", "fallopian"];
const WATER_CONS = ["conserv", "harvest", "recycle", "mulch"];
const DIGEST_KW  = ["digest", "stomach", "intestin", "liver", "bile", "saliva",
                    "oesoph", "enzyme"];
const RESP_KW    = ["lung", "respirat", "breath", "trachea", "bronch", "diaphragm"];
const SKEL_KW    = ["skeleton", "bone", "joint", "cartilage", "muscle", "skull"];
const PHYS_KW    = ["lever", "pulley", "machine", "fulcrum", "effort", "inclined"];
const MATTER_KW  = ["solid", "liquid", "melting", "boiling", "matter", "condensat"];
const SOIL_KW    = ["soil", "erosion", "weather", "loam", "sandy", "clay"];
const MICRO_KW   = ["bacteria", "virus", "fungus", "microorganism", "disease", "germ"];
const VERT_KW    = ["amphibian", "reptile", "frog", "lizard", "mammal", "bird",
                    "gill", "feather", "scale"];
const PLANT_REP  = ["germinat", "dispersal", "vegetative"];
const CTX_KW     = ["help", "learn", "teach", "explain", "question", "answer"];
const GREET_KW   = ["hello", "hey", "good morning", "good afternoon",
                    "good evening", "good day"];
const FAREWELL_KW = ["bye", "goodbye", "good bye", "good night", "exit",
                     "quit", "farewell", "see you"];

function any(text: string, words: readonly string[]): boolean {
  for (const w of words) if (text.includes(w)) return true;
  return false;
}

export function extractFeatures(text: string): Float32Array {
  const f = new Float32Array(FEATURE_SIZE);
  const lower = text.toLowerCase();

  // 0–8: subject keywords
  for (let i = 0; i < SUBJECT_KW.length; i++) {
    if (lower.includes(SUBJECT_KW[i]!)) f[i] = 1;
  }

  // Aliases routed through f[6] / f[7] / f[1]
  if (any(lower, BIO_ANIMAL)) { f[7] = 1; f[1] = 1; }
  if (any(lower, PLANT_KW))   { f[6] = 1; f[1] = 1; }
  if (f[6] === 1 || f[7] === 1 || f[8] === 1) f[1] = 1;
  if (any(lower, BODY_KW))    f[1] = 1;
  if (any(lower, REPROD_KW))  f[1] = 1;
  if (any(lower, WATER_CONS)) f[1] = 1;
  if (any(lower, DIGEST_KW))  f[1] = 1;
  if (any(lower, RESP_KW))    f[1] = 1;
  if (any(lower, SKEL_KW))    f[1] = 1;
  if (any(lower, PHYS_KW))    f[1] = 1;
  if (any(lower, MATTER_KW))  f[1] = 1;
  if (any(lower, SOIL_KW))    f[1] = 1;
  if (any(lower, MICRO_KW))   f[1] = 1;
  if (any(lower, VERT_KW))    { f[7] = 1; f[1] = 1; }
  if (any(lower, PLANT_REP))  { f[6] = 1; f[1] = 1; }

  // 9–13: living-things specifics
  if (lower.includes("photosynthes")) f[9]  = 1;
  if (lower.includes("habitat"))      f[10] = 1;
  if (lower.includes("food"))         f[11] = 1;
  if (lower.includes("water"))        f[12] = 1;
  if (lower.includes("grow"))         f[13] = 1;

  // 14–17: question type
  if (lower.includes("what") || lower.includes("which")) f[14] = 1;
  if (lower.includes("how"))                              f[15] = 1;
  if (lower.includes("why"))                              f[16] = 1;
  if (lower.includes("when") || lower.includes("where"))  f[17] = 1;

  // 18–23: educational context
  for (let i = 0; i < CTX_KW.length; i++) {
    if (lower.includes(CTX_KW[i]!)) f[18 + i] = 1;
  }

  // 24: greeting-specific
  const isGreeting = any(lower, GREET_KW)
                  || lower.startsWith("hi ") || lower === "hi"
                  || lower.includes(" hi ");
  if (isGreeting) f[24] = 1;

  // 25: farewell-specific
  if (any(lower, FAREWELL_KW)) f[25] = 1;

  return f;
}
