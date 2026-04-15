#!/usr/bin/env python3
"""
train_living.py
Kenya CBC Grade 6 Living Things - TinyML training script
Produces Java-ready nibble-packed arrays for CompressedTinyML.

Fixed bugs:
  - Weight transposition: W1 = coefs_[0].T  (12,25), W2 = coefs_[1].T  (8,12)
  - Nibble packing: Java even-index → lower nibble, odd-index → upper nibble
    => byte = (w[i+1] << 4) | w[i]
"""

import os
import json
import numpy as np
from sklearn.neural_network import MLPClassifier
from sklearn.model_selection import train_test_split

# ── PDF extraction ──────────────────────────────────────────────────────────
def extract_pdf_text(pdf_path):
    from pdfminer.high_level import extract_text
    try:
        text = extract_text(pdf_path)
        print(f"[PDF] Extracted {len(text)} chars from {os.path.basename(pdf_path)}")
        return text
    except Exception as e:
        print(f"[PDF] Warning: {e}")
        return ""

# ── Constants ───────────────────────────────────────────────────────────────
INTENTS = {
    0: "math_help",
    1: "science_help",
    2: "english_help",
    3: "quiz",
    4: "general_help",
    5: "progress",
    6: "greeting",
    7: "farewell",
}

FEATURE_SIZE = 25
HIDDEN_SIZE  = 12
OUTPUT_SIZE  = 8

# Feature layout (matches the Java extractFeatures spec):
# 0-8  : subject keywords
SUBJECT_KW = ["math", "science", "english", "calculat", "experiment", "grammar",
               "plant", "animal", "living"]
# 9-13 : living-things keywords
LIVING_KW  = ["photosynthes", "habitat", "food", "water", "grow"]
# 14-17: question types
# 18-23: educational context
CONTEXT_KW = ["help", "learn", "teach", "explain", "question", "answer"]
# 24   : social (hello/hi/bye/good)

def extract_features(text):
    """Return float32[25] feature vector."""
    f = np.zeros(FEATURE_SIZE, dtype=np.float32)
    t = text.lower()

    for i, kw in enumerate(SUBJECT_KW):
        if kw in t:
            f[i] = 1.0

    for i, kw in enumerate(LIVING_KW):
        if kw in t:
            f[9 + i] = 1.0

    if "what" in t or "which" in t:  f[14] = 1.0
    if "how" in t:                    f[15] = 1.0
    if "why" in t:                    f[16] = 1.0
    if "when" in t or "where" in t:   f[17] = 1.0

    for i, kw in enumerate(CONTEXT_KW):
        if kw in t:
            f[18 + i] = 1.0

    if "hello" in t or "hi" in t or "bye" in t or "good" in t:
        f[24] = 1.0

    return f


# ── Training data ────────────────────────────────────────────────────────────
def build_training_data(pdf_text=""):
    raw = []

    # ── Math (intent 0)  ─────────────────────────────────────────────────────
    math_base = [
        # fire f[0]=math, f[3]=calculat
        "help with math", "how to calculate", "addition problem", "subtraction help",
        "multiplication", "division", "math homework", "solve equation", "arithmetic",
        "math problem", "calculate area", "math question", "learn math", "mathematics",
        "fraction help", "decimals", "percentage calculation", "geometry shapes",
        "algebra help", "number patterns", "math formula", "calculate volume",
        "times table help", "square root math", "math calculation",
        "calculate perimeter", "number bonds math", "math exercise",
        "how to calculate fractions", "calculate decimals math",
        "mathematics help please", "learn how to calculate",
        "explain mathematics", "teach me math", "math lesson",
        "solve math problem", "what is mathematics", "math study",
        "help me learn math", "how does calculation work",
    ]
    for t in math_base:
        raw.append((t, 0))

    # ── Science / Living Things (intent 1)  ─────────────────────────────────
    # General science
    science_gen = [
        "science help", "science experiment", "weather patterns", "environment",
        "physics", "chemistry", "biology", "scientific method", "nature",
        "science project", "learn science", "science question", "climate change",
        "rainfall", "ecosystem", "science lesson", "science study",
        "how science works", "explain science", "teach me science",
        "what is science", "science topic", "science revision",
    ]
    for t in science_gen:
        raw.append((t, 1))

    # Plants
    plants = [
        "plants", "about plants", "plant growth", "types of plants",
        "watering plants", "plants need sunlight", "how do plants grow",
        "what are plants", "plant biology", "photosynthesis in plants",
        "plant cells", "plant reproduction", "plant science", "botany",
        "flowering plants", "medicinal plants", "plant experiments",
        "study plants", "leaf structure", "root system", "stem function",
        "plant nutrition", "learn about plants", "explain plant growth",
        "teach me about plants", "science of plants",
        "plant experiment science", "biology of plants",
        "how plants make food", "plant water absorption",
        "why do plants need water", "how do plants get food",
        "what do plants need to grow", "plant and sunlight",
    ]
    for t in plants:
        raw.append((t, 1))

    # Animals
    animals = [
        "animals", "about animals", "animal habitats", "types of animals",
        "animal reproduction", "vertebrates", "invertebrates", "mammals",
        "birds", "reptiles", "amphibians", "fish classification",
        "animal adaptation", "animal science", "study animals",
        "explain animals", "teach me about animals", "learn about animals",
        "how do animals live", "what are vertebrates",
    ]
    for t in animals:
        raw.append((t, 1))

    # Living things
    living = [
        "living things", "characteristics of living things", "living organisms",
        "what do living things need", "how do living things grow",
        "living and nonliving", "life processes", "nutrition in plants",
        "nutrition in animals", "respiration in plants", "respiration in animals",
        "excretion", "movement in plants", "movement in animals",
        "sensitivity in living things", "reproduction in living things",
        "growth in organisms", "water and living things", "food for living things",
        "living things science", "explain living things", "teach living things",
        "what are living things", "how do living things reproduce",
    ]
    for t in living:
        raw.append((t, 1))

    # Food chains / habitats
    food_habitat = [
        "food chain", "food web", "predator and prey", "herbivore", "carnivore",
        "omnivore", "producer and consumer", "decomposer", "habitat of animals",
        "forest habitat", "aquatic habitat", "desert habitat", "grassland habitat",
        "what is food chain", "explain food web", "habitat science",
        "teach me food chain", "how does food chain work",
        "food chain in ecosystem", "food water and living things",
    ]
    for t in food_habitat:
        raw.append((t, 1))

    # Photosynthesis / water
    photo_water = [
        "photosynthesis", "how photosynthesis works", "chlorophyll",
        "sunlight water carbon dioxide", "oxygen from plants",
        "water cycle in plants", "transpiration", "absorption of water by plants",
        "food production in plants", "starch in leaves",
        "explain photosynthesis", "teach photosynthesis",
        "what is photosynthesis", "photosynthesis experiment",
        "how plants use water", "why plants need water",
    ]
    for t in photo_water:
        raw.append((t, 1))

    # PDF-extracted topic phrases (Grade 6 CBC)
    pdf_topics = [
        "characteristics of living things grade 6",
        "nutrition in green plants",
        "factors affecting photosynthesis",
        "leaf as the organ of photosynthesis",
        "stomata in leaves",
        "chloroplast function",
        "importance of photosynthesis",
        "water transport in plants",
        "xylem and phloem",
        "mineral salts needed by plants",
        "nitrogen cycle plants",
        "soil nutrients for plant growth",
        "types of roots fibrous taproot",
        "parts of a flower",
        "pollination by wind insects",
        "seed structure cotyledon",
        "conditions for germination",
        "dispersal of seeds wind water animal",
        "asexual reproduction in plants",
        "vegetative reproduction cuttings",
        "food chain in grassland",
        "energy flow in ecosystem",
        "decomposers fungi bacteria",
        "habitat destruction conservation",
        "endangered species Kenya",
        "vertebrates and invertebrates classification",
        "adaptations of fish to water",
        "adaptations of birds to flight",
        "mammal characteristics warm blooded",
        "reptile characteristics scales",
        "amphibian life cycle tadpole",
        "insect body parts",
        "excretion in plants stomata",
        "respiration aerobic anaerobic",
        "movement in plants tropism",
        "sensitivity response to stimuli",
        "growth cell division",
        "water importance to living things",
        "air importance to living things",
        "food importance to living things",
        "shelter habitat importance",
        "how plants grow from seeds",
        "how animals adapt to their habitat",
        "what animals eat in food chain",
        "science of how living things grow",
    ]
    for t in pdf_topics:
        raw.append((t, 1))

    # ── English (intent 2)  ──────────────────────────────────────────────────
    english_base = [
        "english help", "grammar", "verbs", "nouns", "sentence structure",
        "reading", "writing", "spelling", "vocabulary", "learn english",
        "english lesson", "language", "adjectives", "adverbs", "punctuation",
        "composition writing", "comprehension", "essay", "tense",
        "parts of speech", "creative writing", "letter writing",
        "nouns and pronouns", "subject predicate", "english grammar help",
        "english question", "what is grammar", "explain grammar",
        "teach me english", "how to write essay", "learn english grammar",
        "english vocabulary help", "spelling help english",
        "help me with english", "english reading", "english writing",
        "english comprehension help", "grammar lesson", "vocabulary lesson",
        "english teach me", "explain sentence structure",
    ]
    for t in english_base:
        raw.append((t, 2))

    # ── Quiz (intent 3)  ─────────────────────────────────────────────────────
    # Key features that fire: f[22]=question, f[23]=answer
    # "quiz" alone fires nothing — pair it with question/answer keywords
    quiz_base = [
        # With "question" keyword (fires f[22])
        "take quiz question", "quiz question", "test question", "exam question",
        "practice question", "quiz questions please", "give me quiz question",
        "ask me question", "science question quiz", "math question quiz",
        "english question quiz", "living things question quiz",
        "grade 6 question quiz", "quiz question answer",
        "question and answer quiz", "answer quiz question",
        "questions for quiz", "question test", "exam questions please",
        "give me questions", "I have a question quiz", "practice questions",
        "revision questions", "test questions", "knowledge questions",
        "ask question please", "quiz question now", "exam question please",
        "what question quiz", "how question quiz",
        # With "answer" keyword (fires f[23])
        "answer questions", "give me answer quiz", "quiz and answer",
        "quiz answer please", "answer this quiz",
        # Both question and answer
        "question and answer", "questions and answers",
        "answer all questions", "question answer quiz",
        # Plain forms — model learns from frequency
        "take quiz", "test me", "exam", "quiz time",
        "give me a quiz", "practice test", "quiz on science", "quiz on math",
        "test my knowledge", "assessment", "quiz on living things",
        "ask me questions", "quiz plants", "quiz animals",
        "test yourself", "start quiz", "begin quiz",
        "quiz please", "I want questions",
        "quiz me now", "start test", "begin test", "practice quiz",
        "quiz grade 6", "science exam", "math exam", "english exam",
        "revision test", "knowledge test", "quiz me",
    ]
    for t in quiz_base:
        raw.append((t, 3))

    # ── General help (intent 4)  ─────────────────────────────────────────────
    general_base = [
        "help", "can you help", "assist me", "I need help", "please help",
        "help me", "what can you do", "how does this work", "support",
        "guidance needed", "need assistance", "show me how",
        "general question", "need information", "what do you know",
        "topics available", "help please", "I need assistance",
        "how can you help", "what help is available", "help me please",
        "can you assist", "need help now", "help me understand",
        "explain how to use this", "what can I learn here",
        "teach me something", "help me learn", "guide me",
    ]
    for t in general_base:
        raw.append((t, 4))

    # ── Progress (intent 5)  ─────────────────────────────────────────────────
    # Key features: f[23]=answer, f[22]=question (via "how many")
    progress_base = [
        "my progress", "how am I doing", "show progress", "statistics",
        "my results", "progress report", "scores", "performance",
        "how many correct", "marks", "achievements", "my score",
        "track progress", "learning progress", "quiz results",
        "how well am I doing", "show my progress", "progress check",
        "see my scores", "view progress", "progress today",
        "score report", "marks report", "results report",
        "show statistics", "overall score", "grade report",
        "my performance", "progress summary", "show results",
        "how well do I answer", "my answer scores", "my learning",
        "how many questions answered", "total answered", "correct answers",
        "how many I got right", "number correct", "my marks",
        "progress and scores", "my quiz scores", "quiz performance",
        "how many correct answers", "score today", "today results",
        "my total score", "learning results", "my grades",
    ]
    for t in progress_base:
        raw.append((t, 5))

    # ── Greeting (intent 6)  ─────────────────────────────────────────────────
    # Greetings mostly fire f[24] via "hello", "hi", "good".
    # Strategy: use MANY hello/hi patterns; use "good morning/afternoon/evening"
    # (not "good night" which is farewell).
    greetings_base = [
        # hello-based (fire f[24] via "hello")
        "hello", "hello there", "hello everyone", "hello teacher",
        "hello friend", "hello hi", "hello to you", "hello I am here",
        "hello start", "hello begin", "hello welcome", "hello ready",
        "hello how are you", "hello I want to learn", "hello help me",
        "hello good morning", "hello good afternoon", "hello good evening",
        "hello hi there", "say hello", "hello from me",
        # hi-based (fire f[24] via "hi")
        "hi", "hi there", "hi everyone", "hi teacher",
        "hi friend", "hi hello", "hi to you", "hi I am here",
        "hi start", "hi begin", "hi welcome", "hi ready",
        "hi how are you", "hi I want to learn", "hi help me",
        "hi good morning", "hi good afternoon",
        # good morning/afternoon/evening (fire f[24] via "good")
        "good morning", "good afternoon", "good evening",
        "good morning teacher", "good morning everyone", "good morning class",
        "good morning to you", "good morning hi", "good morning hello",
        "good afternoon to you", "good afternoon hello",
        "good evening to you", "good evening hello",
        "good day", "good day to you",
        # hey
        "hey", "hey there", "hey hello", "hey hi",
    ]
    for t in greetings_base:
        raw.append((t, 6))

    # ── Farewell (intent 7)  ─────────────────────────────────────────────────
    # Farewell: primarily "bye"-based phrases (f[24] via "bye").
    # "good morning/afternoon/evening/day" belong to greetings only.
    # "good night" and "goodbye"/"good bye" are farewell only.
    farewells_base = [
        # bye-based (fire f[24] via "bye") — primary farewell signal
        "bye", "bye bye", "bye everyone", "bye teacher", "bye friend",
        "bye for now", "bye see you", "bye goodbye", "bye take care",
        "bye done", "bye quit", "bye exit", "bye close",
        "bye I am leaving", "bye going now", "bye done for today",
        "bye I am done", "bye later", "bye farewell",
        "bye see you later", "bye see you tomorrow",
        "goodbye", "goodbye everyone", "goodbye teacher",
        "goodbye take care", "goodbye bye", "goodbye farewell",
        "goodbye see you", "goodbye later",
        # good night (fires f[24] via "good") — farewell only
        "good night", "good night teacher", "good night everyone",
        "good night farewell", "good night see you",
        "good night take care", "good night class", "good night bye",
        # good bye (fires f[24] via both "good" and "bye")
        "good bye", "good bye teacher", "good bye everyone",
        "good bye bye", "good bye farewell",
        # exit/quit/close — no f[24] but strong farewell signal
        "exit", "quit", "close", "stop", "end session",
        "exit now", "quit now", "close now", "stop now",
        "exit program", "quit program", "close program",
        "see you", "see you later", "see you tomorrow",
        "farewell", "farewell bye", "farewell see you",
        "take care", "later", "talk later",
        "done for today", "leaving now", "going now",
        "I am done", "done now", "closing now",
    ]
    for t in farewells_base:
        raw.append((t, 7))

    # ── Augmentation (balanced) ───────────────────────────────────────────────
    augmented = []

    # Prefixes that add useful feature signal where possible
    aug_patterns = {
        0: [  # math — add math/calculat prefixes
            ("calculate ", 0), ("math help with ", 0),
            ("I need help with math ", 0), ("how to do math ", 0),
        ],
        1: [  # science — keep science signal
            ("science ", 1), ("explain science ", 1),
            ("teach me science ", 1), ("learn about science ", 1),
        ],
        2: [  # english — keep english/grammar signal
            ("english help with ", 2), ("grammar help ", 2),
            ("learn english ", 2), ("teach me english ", 2),
        ],
        3: [  # quiz — add question/answer signal
            ("quiz question ", 3), ("answer quiz ", 3),
            ("question and answer ", 3), ("give me questions ", 3),
        ],
        4: [  # general — help signal
            ("help me with ", 4), ("can you help with ", 4),
            ("I need help ", 4),
        ],
        5: [  # progress — answer/score signal
            ("show my ", 5), ("how many correct ", 5),
            ("my score for ", 5),
        ],
        6: [  # greeting — social signal
            ("good morning ", 6), ("hello ", 6), ("hi ", 6),
        ],
        7: [  # farewell — bye/good signal
            ("bye ", 7), ("good bye ", 7), ("good night ", 7),
        ],
    }

    counts = {0: 3, 1: 2, 2: 3, 3: 4, 4: 3, 5: 4, 6: 3, 7: 3}

    for text, intent in raw:
        patterns = aug_patterns.get(intent, [("", intent)])
        n = counts.get(intent, 2)
        added = 0
        for prefix, _ in patterns:
            if added >= n:
                break
            aug_text = (prefix + text).strip()
            if aug_text != text and len(aug_text) < 80:
                augmented.append((aug_text, intent))
                added += 1

    all_data = raw + augmented

    # Count per intent
    from collections import Counter
    cnts = Counter(i for _, i in all_data)
    print(f"[DATA] Total: {len(all_data)}")
    for k in sorted(cnts):
        print(f"  Intent {k} ({INTENTS[k]}): {cnts[k]}")

    return all_data


# ── Quantisation ─────────────────────────────────────────────────────────────
def quantize_matrix(W):
    """Normalise by max(|W|), then map to uint8[0,15] via round(clip*7.5+7.5)."""
    scale = float(np.max(np.abs(W)))
    if scale < 0.1:
        scale = 0.1
    W_norm = W / scale
    q = np.round(np.clip(W_norm, -1.0, 1.0) * 7.5 + 7.5).astype(np.uint8)
    return q

def quantize_biases(b_all):
    scale = float(np.max(np.abs(b_all)))
    if scale < 0.1:
        scale = 0.1
    b_norm = b_all / scale
    q = np.round(np.clip(b_norm, -1.0, 1.0) * 7.5 + 7.5).astype(np.uint8)
    return q


# ── Nibble packing (corrected for Java getWeight indexing) ───────────────────
def pack_nibbles(values):
    """
    Java getWeight(i):
        byte b = arr[i >> 1];
        return (i & 1) == 0 ? (b & 0x0F) : ((b >> 4) & 0x0F);
    Even index i  → lower nibble (b & 0x0F)
    Odd  index i+1 → upper nibble ((b >> 4) & 0x0F)
    Byte for pair (v_i, v_{i+1}): byte = (v_{i+1} << 4) | v_i
    """
    flat = [int(x) for x in values]
    if len(flat) % 2 == 1:
        flat.append(0)
    out = []
    for i in range(0, len(flat), 2):
        lo = flat[i]   & 0x0F   # even → lower nibble
        hi = flat[i+1] & 0x0F   # odd  → upper nibble
        out.append((hi << 4) | lo)
    return out


# ── Java output ───────────────────────────────────────────────────────────────
def java_byte_array(name, byte_list, per_line=8):
    lines = []
    for i in range(0, len(byte_list), per_line):
        chunk = byte_list[i:i + per_line]
        lines.append(", ".join(f"(byte)0x{b & 0xFF:02X}" for b in chunk))
    body = ",\n        ".join(lines)
    return f"private static final byte[] {name} = {{\n        {body}\n}};"


# ── Main ──────────────────────────────────────────────────────────────────────
def main():
    BASE   = "/Users/donattahakinyi/Documents/PHD/elimu-sms/elimu-model"
    PDF    = os.path.join(BASE, "LIVING.pdf")
    OUTDIR = os.path.join(BASE, "model_out")
    os.makedirs(OUTDIR, exist_ok=True)

    # 1. Extract PDF
    print("\n=== 1. Extracting PDF ===")
    pdf_text = extract_pdf_text(PDF)

    topic_keywords = [
        "photosynthesis", "habitat", "food chain", "food web", "reproduction",
        "adaptation", "vertebrate", "invertebrate", "mammal", "nutrition",
        "respiration", "excretion", "growth", "sensitivity", "movement",
        "chlorophyll", "stomata", "xylem", "pollination", "germination",
        "transpiration", "ecosystem", "decomposer", "predator",
    ]
    found_topics = [kw for kw in topic_keywords if kw.lower() in pdf_text.lower()]
    print(f"[PDF] Topics detected: {found_topics}")

    # 2. Build dataset
    print("\n=== 2. Building dataset ===")
    all_data = build_training_data(pdf_text)

    X = np.array([extract_features(t) for t, _ in all_data])
    y = np.array([i for _, i in all_data])
    print(f"[DATA] X: {X.shape}  y: {y.shape}")

    # 3. Split
    print("\n=== 3. Splitting ===")
    X_train, X_test, y_train, y_test = train_test_split(
        X, y, test_size=0.2, random_state=42, stratify=y
    )
    print(f"  Train: {len(X_train)}, Test: {len(X_test)}")

    # 4. Train MLP
    print("\n=== 4. Training MLPClassifier ===")
    model = MLPClassifier(
        hidden_layer_sizes=(HIDDEN_SIZE,),
        activation="relu",
        solver="adam",
        max_iter=10000,
        random_state=42,
        learning_rate_init=0.001,
        alpha=0.001,
        early_stopping=False,
        n_iter_no_change=50,
        tol=1e-5,
    )
    model.fit(X_train, y_train)

    train_acc = model.score(X_train, y_train)
    test_acc  = model.score(X_test,  y_test)
    print(f"  Training accuracy : {train_acc*100:.2f}%")
    print(f"  Test     accuracy : {test_acc*100:.2f}%")

    # 5. Extract & transpose weights
    print("\n=== 5. Transposing weights ===")
    W1 = model.coefs_[0].T   # (HIDDEN, FEATURE) = (12, 25)
    W2 = model.coefs_[1].T   # (OUTPUT, HIDDEN)  = (8, 12)
    b1 = model.intercepts_[0]
    b2 = model.intercepts_[1]
    b_all = np.concatenate([b1, b2])

    print(f"  W1: {W1.shape}  W2: {W2.shape}  biases: {b_all.shape}")

    # 6. Quantise
    print("\n=== 6. Quantising ===")
    W1_q = quantize_matrix(W1)
    W2_q = quantize_matrix(W2)
    B_q  = quantize_biases(b_all)

    all_weights_q = np.concatenate([W1_q.flatten(), W2_q.flatten()])
    expected_w = HIDDEN_SIZE * FEATURE_SIZE + OUTPUT_SIZE * HIDDEN_SIZE
    print(f"  Weight values: {len(all_weights_q)}  (expected {expected_w})")
    print(f"  Bias values  : {len(B_q)}  (expected 20)")

    # 7. Pack nibbles
    print("\n=== 7. Packing nibbles ===")
    packed_weights = pack_nibbles(all_weights_q)
    packed_biases  = pack_nibbles(B_q)
    print(f"  COMPRESSED_WEIGHTS: {len(packed_weights)} bytes  (expected 198)")
    print(f"  COMPRESSED_BIASES : {len(packed_biases)} bytes  (expected 10)")

    assert len(packed_weights) == 198, f"Weight byte count wrong: {len(packed_weights)}"
    assert len(packed_biases)  == 10,  f"Bias byte count wrong: {len(packed_biases)}"

    # 8. Generate Java
    print("\n=== 8. Generating Java output ===")
    java_out = (
        f"// ================================================================\n"
        f"// GENERATED BY train_living.py\n"
        f"// Kenya CBC Grade 6 Living Things  –  CompressedTinyML\n"
        f"// FEATURE_SIZE={FEATURE_SIZE}  HIDDEN_SIZE={HIDDEN_SIZE}  OUTPUT_SIZE={OUTPUT_SIZE}\n"
        f"// Training accuracy : {train_acc*100:.2f}%\n"
        f"// Test     accuracy : {test_acc*100:.2f}%\n"
        f"// ================================================================\n\n"
        + java_byte_array("COMPRESSED_WEIGHTS", packed_weights, per_line=8)
        + "\n\n"
        + java_byte_array("COMPRESSED_BIASES",  packed_biases,  per_line=8)
        + "\n"
    )

    out_java = os.path.join(OUTDIR, "living_java_output.txt")
    with open(out_java, "w") as fh:
        fh.write(java_out)
    print(f"  Written: {out_java}")

    # 9. Save meta JSON
    meta = {
        "FEATURE_SIZE":       FEATURE_SIZE,
        "HIDDEN_SIZE":        HIDDEN_SIZE,
        "OUTPUT_SIZE":        OUTPUT_SIZE,
        "training_accuracy":  round(float(train_acc), 6),
        "test_accuracy":      round(float(test_acc),  6),
        "total_samples":      int(len(all_data)),
        "pdf_topics_found":   found_topics,
        "intents":            {str(k): v for k, v in INTENTS.items()},
    }
    out_json = os.path.join(OUTDIR, "living_meta.json")
    with open(out_json, "w") as fh:
        json.dump(meta, fh, indent=2)
    print(f"  Written: {out_json}")

    # 10. Sanity tests
    print("\n=== 9. Sanity tests ===")
    sanity = [
        # science / living things
        ("plants",              1),
        ("photosynthesis",      1),
        ("food chain",          1),
        ("habitat of animals",  1),
        ("how do plants grow",  1),
        ("living things",       1),
        # other subjects
        ("math addition",       0),
        ("calculate area",      0),
        ("english grammar",     2),
        ("learn english",       2),
        # quiz
        ("take quiz",           3),
        ("quiz question",       3),
        ("give me questions",   3),
        # general
        ("help",                4),
        # progress
        ("my progress",         5),
        ("show my scores",      5),
        # greeting
        ("hello",               6),
        ("good morning",        6),
        # farewell
        ("bye",                 7),
        ("good night",          7),
    ]
    correct = 0
    for text, expected in sanity:
        feat = extract_features(text)
        pred = int(model.predict([feat])[0])
        conf = float(np.max(model.predict_proba([feat])))
        ok   = "OK" if pred == expected else "FAIL"
        if pred == expected:
            correct += 1
        print(f"  [{ok:4s}] '{text}': expected={INTENTS[expected]:12s} "
              f"got={INTENTS[pred]:12s}  conf={conf:.3f}")
    print(f"  Sanity: {correct}/{len(sanity)}")

    print(f"\n=== DONE ===")
    print(f"  living_java_output.txt → {out_java}")
    print(f"  living_meta.json       → {out_json}")
    print(f"  Train: {train_acc*100:.2f}%   Test: {test_acc*100:.2f}%")


if __name__ == "__main__":
    main()
