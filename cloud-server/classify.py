"""
Keyword-based intent classifier for cloud-arbitrated supervision.

When the on-device TinyML classifier falls back to the cloud, we don't
just want a generative answer — we want a *label* the device can use
to update its own classifier (Section: Two-Button Interaction and
Implicit Feedback in the proposal).

This module mirrors the keyword chains the J2ME on-device classifier
already uses (CompressedTinyML.extractFeatures) but expressed
declaratively in Python. It is intentionally simple and deterministic:
the supervision signal must be reliable, not clever.

The mapping returned matches CompressedTinyML.intentIdFromLabel():
    0 math_help, 1 science_help, 2 english_help, 3 quiz,
    4 general_help, 5 progress, 6 greeting, 7 farewell
"""

# Each entry: (label, set of substrings — match if any appears in the
# lower-cased question). Order matters: first matching rule wins.
_RULES = [
    ("greeting", {
        "habari", "mambo", "jambo", "hello", "hey", "good morning",
        "good afternoon", "good evening", "good day",
    }),
    ("farewell", {
        "kwaheri", "goodbye", "good bye", "good night", "bye",
        "farewell", "see you", "exit", "quit",
    }),
    ("progress", {
        "progress", "score", "scores", "result", "results", "my marks",
    }),
    ("quiz", {
        "quiz", "test", "exam",
    }),
    ("math_help", {
        "math", "calculat", "fraction", "percent", "ratio", "decimal",
        "lcm", "hcf", "least common", "highest common", "gcd",
        "area", "perimeter", "volume", "mean", "mode", "range",
        "average",
    }),
    ("science_help", {
        # plants
        "photosynthes", "chlorophyll", "stomata", "stoma", "transpir",
        "leaf", "leaves", "root", "seed", "flower", "stem", "germinat",
        # animals
        "vertebrate", "invertebrate", "insect", "pollinat", "bee",
        "worm", "bird", "fish", "mammal", "reptile", "amphibian",
        "frog", "lizard", "gill", "feather", "scale",
        # human body
        "heart", "blood", "circul", "artery", "arteries", "vein",
        "capillar", "pulse", "plasma", "haemoglob",
        "lung", "respirat", "breath", "trachea", "bronch", "diaphragm",
        "digest", "stomach", "intestin", "liver", "bile", "saliva",
        "oesoph", "enzyme",
        "skeleton", "bone", "joint", "cartilage", "muscle", "skull",
        "reproduct", "adolescen", "puberty", "ovary", "uterus",
        "testis", "sperm", "menstruat",
        # earth & physics
        "soil", "loam", "sandy", "clay", "erosion", "weather",
        "lever", "pulley", "fulcrum", "machine", "inclined",
        "solid", "liquid", "melting", "boiling", "matter", "condensat",
        "bacteria", "virus", "fungus", "microorganism", "germ",
        "food chain", "habitat", "ecology", "ecosystem",
        # generic plant/animal
        "plant", "animal", "living",
    }),
    # english_help (intent 2) is retired in favour of STEM-only focus.
    # The keyword rule is removed so classify_intent never returns
    # "english_help"; English-grammar questions fall through to
    # general_help. The label is retained in INTENT_LABELS below for
    # index stability with the federated wire format.
]

INTENT_LABELS = [
    "math_help", "science_help", "english_help", "quiz",
    "general_help", "progress", "greeting", "farewell",
]


def classify_intent(question: str) -> str:
    """Return one of INTENT_LABELS. Defaults to general_help on no match."""
    if not question:
        return "general_help"
    q = question.lower()
    # Fast arithmetic-expression check: any of + - * / between digits → math.
    if _looks_like_arithmetic(q):
        return "math_help"
    for label, kws in _RULES:
        for kw in kws:
            if kw in q:
                return label
    return "general_help"


def _looks_like_arithmetic(q: str) -> bool:
    # Strip whitespace first so "5 * 4" reads as "5*4" for adjacency check.
    s = "".join(q.split())
    if not any(op in s for op in ("+", "*", "/")):
        return False
    for i, ch in enumerate(s):
        if ch in "+*/" and 0 < i < len(s) - 1:
            if s[i - 1].isdigit() and s[i + 1].isdigit():
                return True
    return False
