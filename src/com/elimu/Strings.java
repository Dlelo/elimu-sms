package com.elimu;

/**
 * Bilingual UI string table for ElimuSMS.
 *
 * The deployment language is selected at startup via the JAD attribute
 * Elimu-Lang ∈ {"en", "sw"}. Falls back to English if the attribute is
 * missing or unknown. Switching locales is a JAD-edit operation, not a
 * recompile, so the same MIDlet binary ships to English-medium and
 * Swahili-medium cohorts.
 *
 * Design choice: parallel String[] arrays indexed by named integer
 * constants. CLDC 1.1 Hashtable would use ~3x the heap for the same
 * payload, and for a fixed-size table the array indexing is cheaper.
 *
 * Adding a new key:
 *   1. Add a new public static final int K_<NAME> at the next index.
 *   2. Append the English text to EN at that index.
 *   3. Append the Swahili translation to SW at the same index.
 *   4. Update N_KEYS.
 */
public class Strings {

    // ── Key indices ──────────────────────────────────────────────────────────
    public static final int K_APP_TITLE    = 0;
    public static final int K_OK           = 1;
    public static final int K_MORE         = 2;
    public static final int K_EXIT         = 3;
    public static final int K_BACK         = 4;
    public static final int K_SELECT       = 5;
    public static final int K_SUBMIT       = 6;
    public static final int K_SEND         = 7;
    public static final int K_ASK_QUESTION = 8;
    public static final int K_MATH_HELP    = 9;
    public static final int K_SCIENCE_HELP = 10;
    public static final int K_MATH_QUIZ    = 11;
    public static final int K_SCIENCE_QUIZ = 12;
    public static final int K_MY_PROGRESS  = 13;
    public static final int K_CHOOSE_QUIZ  = 14;
    public static final int K_CLOUD        = 15;
    public static final int K_CLOUD_ASKING = 16;
    public static final int K_CLOUD_OFFLINE = 17;
    public static final int K_CLOUD_ANSWER = 18;
    public static final int K_OFFLINE_TIER_EXHAUSTED = 19;
    public static final int K_HELLO_REPLY  = 20;
    public static final int K_GOODBYE_REPLY = 21;
    public static final int N_KEYS         = 22;

    // ── Locale tables ────────────────────────────────────────────────────────
    private static final String[] EN = {
        "ElimuSMS - STEM Grade 6",
        "OK",
        "More",
        "Exit",
        "Back",
        "Select",
        "Submit",
        "Send",
        "Ask Question",
        "Math Help",
        "Science Help",
        "Math Quiz",
        "Science Quiz",
        "My Progress",
        "Choose Quiz Type",
        "Cloud",
        "Asking the cloud AI...",
        "Cloud unreachable. I can help with Math and Science.\n"
                + "Try: 'photosynthesis', 'fraction', 'LCM', '5*4'.",
        "Cloud Answer",
        "I have shown you everything I can offline.\n"
                + "Ask this when you are online for a deeper answer.",
        "Hello! Ask me about math or science.",
        "Goodbye! Keep learning STEM!",
    };

    private static final String[] SW = {
        "ElimuSMS - STEM Darasa la 6",
        "Sawa",
        "Zaidi",
        "Toka",
        "Rudi",
        "Chagua",
        "Wasilisha",
        "Tuma",
        "Uliza Swali",
        "Msaada wa Hesabu",
        "Msaada wa Sayansi",
        "Jaribio la Hesabu",
        "Jaribio la Sayansi",
        "Maendeleo Yangu",
        "Chagua Aina ya Jaribio",
        "Wingu",
        "Nauliza AI ya wingu...",
        "Wingu halifikiki. Ninaweza kusaidia na Hesabu na Sayansi.\n"
                + "Jaribu: 'photosynthesis', 'fraction', 'LCM', '5*4'.",
        "Jibu la Wingu",
        "Nimekuonyesha kila kitu nilicho nacho bila mtandao.\n"
                + "Uliza hili ukiwa mtandaoni kwa jibu la kina.",
        "Habari! Niulize kuhusu hesabu au sayansi.",
        "Kwaheri! Endelea kujifunza STEM!",
    };

    // ── Active locale ────────────────────────────────────────────────────────
    private static String[] active = EN;

    /** Set the active locale. Accepts "en" or "sw"; anything else keeps the default. */
    public static void setLocale(String lang) {
        if ("sw".equals(lang)) {
            active = SW;
            System.out.println("[Strings] locale=sw (Kiswahili)");
        } else {
            active = EN;
            System.out.println("[Strings] locale=en (English)");
        }
    }

    /** Look up a string by key index. Out-of-range returns "?". */
    public static String get(int key) {
        if (key < 0 || key >= N_KEYS) return "?";
        return active[key];
    }
}
