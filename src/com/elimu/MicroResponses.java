package com.elimu;

/**
 * Compact response templates for the 8 TinyML intent categories.
 *
 * Each slot is a standalone educational micro-lesson: self-contained,
 * curriculum-aligned (Kenya CBC Grade 6), and under 160 characters to
 * remain readable on feature-phone displays with 15-character-wide screens.
 *
 * Bilingual design: Swahili greetings/farewells reflect the target user
 * demographic (Kenyan learners aged 11-12) and reduce affective barriers
 * to technology use in low-resource settings.
 *
 * PhD note: Response quality is evaluated via Flesch-Kincaid readability
 * (target grade level 6) and alignment with KICD CBC Grade 6 competencies.
 */
public class MicroResponses {

    // Intent 0: math_help   — core arithmetic & number concepts
    // Intent 1: science_help — plants / animals / systems / ecology
    // Intent 2: english_help — grammar / reading / writing (routed to science)
    // Intent 3: quiz         — start a quiz session
    // Intent 4: general_help — fall-through / guidance
    // Intent 5: progress     — show learner analytics
    // Intent 6: greeting     — social opening
    // Intent 7: farewell     — session close
    private static final String[] RESPONSES = {

        // 0 — math_help
        "Math topics: fractions, percentages, ratio, area, perimeter, "
            + "volume, LCM, HCF, decimals. Type an expression: 3/4+1/4",

        // 1 — science_help (Living Things focus)
        "Science: photosynthesis, food chains, vertebrates, circulatory "
            + "system, soil types, states of matter. Ask any topic!",

        // 2 — english_help (CBC Grade 6)
        "English: nouns, verbs, adjectives, tense, composition, "
            + "comprehension. Ask: 'what is a noun?' or 'types of tense'",

        // 3 — quiz
        "Quiz modes: Math Quiz (56 KPSEA questions) or Science Quiz "
            + "(43 CBC questions). Type 'quiz' or select from menu.",

        // 4 — general_help
        "ElimuSMS — CBC Grade 6 STEM. Ask about: photosynthesis, "
            + "fractions, blood groups, simple machines, soil types.",

        // 5 — progress
        "Check 'My Progress' from the menu to see your quiz scores, "
            + "topic breakdown, and adaptive review schedule.",

        // 6 — greeting  (Swahili-English bilingual)
        "Habari! / Hello! I'm ElimuSMS. Ask me: math, science, quiz, "
            + "or progress. Let's learn CBC Grade 6 together!",

        // 7 — farewell  (Swahili-English bilingual)
        "Kwaheri! / Goodbye! Keep revising. Come back tomorrow for "
            + "your next spaced-repetition review.",

        // ── Extended math micro-lessons (indices 8-15) ────────────────────
        // 8
        "Fractions: numerator/denominator. Simplify: 6/8 = 3/4 "
            + "(divide by HCF=2). Add: 1/3+1/6 = 2/6+1/6 = 3/6 = 1/2",
        // 9
        "Percentages: score/total x 100. "
            + "Ex: 18/20 x 100 = 90%. Profit%: profit/cost x 100.",
        // 10
        "Ratio: 18 girls to 12 boys = 18:12 = 3:2. Share Ksh 500 "
            + "in ratio 2:3 → 5 parts, Ksh 200 and Ksh 300.",
        // 11
        "LCM: smallest common multiple. LCM(12,18): "
            + "multiples of 12→12,24,36; of 18→18,36. LCM=36.",
        // 12
        "HCF: largest common factor. HCF(12,18): "
            + "factors of 12→1,2,3,4,6,12; of 18→1,2,3,6,9,18. HCF=6.",
        // 13
        "Area formulas: rectangle=l×w, square=s², "
            + "triangle=½bh, circle=πr². Perimeter: add all sides.",
        // 14
        "Volume: cuboid=l×w×h. Ex: 5×4×3=60 cm³. "
            + "Cylinder=πr²h. Capacity: 1 litre=1000 cm³.",
        // 15
        "Mean=sum÷count. Mode=most frequent. Range=max-min. "
            + "Ex: 60,72,58,80,65 → mean=335÷5=67.",

        // ── Extended science micro-lessons (indices 16-23) ────────────────
        // 16
        "Photosynthesis: sunlight+water+CO₂+chlorophyll → glucose+O₂. "
            + "Occurs in leaves. Chlorophyll gives green colour.",
        // 17
        "Food chain: Producer→Herbivore→Carnivore. "
            + "Energy flows; 90% lost at each level. Decomposers recycle.",
        // 18
        "Vertebrates: Fish (gills), Amphibians (gills→lungs), "
            + "Reptiles (scales), Birds (feathers), Mammals (warm-blooded).",
        // 19
        "Blood: plasma (liquid), red cells (O₂), white cells (immunity), "
            + "platelets (clotting). Heart has 4 chambers.",
        // 20
        "Soil types: Sandy (drains fast), Clay (holds water), "
            + "Loam (best for farming—balanced drainage and nutrients).",
        // 21
        "States of matter: Solid (fixed shape+volume), "
            + "Liquid (fixed volume), Gas (fills container). Particles speed.",
        // 22
        "Simple machines: Lever, Pulley, Inclined plane, Wedge, "
            + "Screw, Wheel-axle. All reduce effort or change direction.",
        // 23
        "Digestive system: Mouth→Oesophagus→Stomach→Small intestine"
            + "→Large intestine→Rectum. Nutrients absorbed in small intestine.",
    };

    /**
     * Look up a canned response by intent ID.
     * Increments local-answer counter for the analytics logger.
     */
    public static String getResponse(int intentId) {
        if (intentId >= 0 && intentId < RESPONSES.length) {
            UserPreferences.incrementLocalAnswers();
            return RESPONSES[intentId];
        }
        return "Ask me: Math, Science, Quiz, or My Progress.";
    }

    /** Extended response for a specific curriculum sub-topic (index 8-23). */
    public static String getExtendedResponse(int index) {
        if (index >= 0 && index < RESPONSES.length) {
            UserPreferences.incrementLocalAnswers();
            return RESPONSES[index];
        }
        return getResponse(4); // general help
    }

    // ── "More" tier walk: related keywords per intent ────────────────────────
    // When the student taps "More" while offline, the MIDlet re-routes through
    // the intent handler with the next keyword in this list as the question
    // seed. Each tap surfaces a sibling concept in the same CBC topic web,
    // walking the student outward into broader context.
    //
    // Keywords are chosen to hit distinct branches of handleMathQuestion()
    // and handleScienceQuestion(), so each tap returns a different micro-lesson.
    private static final String[][] RELATED_KEYWORDS = {
        // 0 math_help
        { "fractions", "lcm", "hcf", "percent", "ratio", "decimal",
          "area triangle", "perimeter", "volume", "mean" },
        // 1 science_help
        { "photosynthesis", "chlorophyll", "vertebrate", "circulatory",
          "soil", "states of matter", "digestive", "respiration",
          "simple machines", "food chain" },
        // 2 english_help (routed to science handler in the MIDlet)
        { "photosynthesis", "vertebrate", "soil", "states of matter",
          "digestive", "simple machines" },
        // 3 quiz — handled separately by the quiz UI, but a few prompts help
        { "math quiz", "science quiz" },
        // 4 general_help — broaden into the two main subject menus
        { "fractions", "photosynthesis", "lcm", "vertebrate", "soil",
          "percent", "states of matter" },
        // 5 progress — nothing useful offline; "More" naturally exhausts
        { },
        // 6 greeting — nothing useful offline
        { },
        // 7 farewell — nothing useful offline
        { },
    };

    /** Return the related-keyword list for an intent, or null if none. */
    public static String[] relatedFor(int intentId) {
        if (intentId < 0 || intentId >= RELATED_KEYWORDS.length) return null;
        String[] list = RELATED_KEYWORDS[intentId];
        return (list != null && list.length > 0) ? list : null;
    }
}
