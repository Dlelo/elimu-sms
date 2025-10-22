package com.elimu;

public class MicroResponses {
    // Ultra-compact response storage
    private static final String[] RESPONSES = {
            // 0-7: Basic intents
            "Hello! Ask: Math Science English Quiz",
            "Math: Numbers Add Subtract Multiply Divide",
            "Science: Plants Animals Water Weather Earth",
            "English: Read Write Grammar Stories Spell",
            "Quiz: Math Quiz or Science Quiz",
            "Help: Text subject name for help",
            "Add: Combine numbers. 2+3=5",
            "Subtract: Take away. 5-2=3",

            // 8-15: Math concepts
            "Multiply: Repeated add. 3×4=12",
            "Divide: Share equally. 12÷3=4",
            "Numbers: 1 2 3 4 5 6 7 8 9 10",
            "Shapes: Circle Square Triangle Rectangle",
            "Fractions: Parts of whole. 1/2 = half",
            "Time: 60 sec=1 min, 60 min=1 hr",
            "Measure: Length Weight Volume",
            "Count: 1 to 100 and beyond",

            // 16-23: Science concepts
            "Plants: Need sun, water, air. Grow up.",
            "Animals: Live move eat. Groups: mammals birds fish",
            "Water: Liquid. Freezes to ice. Boils to steam.",
            "Weather: Sun rain wind clouds. Changes daily.",
            "Earth: Our planet. Land water air. Round shape.",
            "Body: Head arms legs. Heart lungs stomach.",
            "Food: Gives energy. Healthy foods: fruits veggies",
            "Energy: Makes things work. Sun food electricity",

            // 24-31: English concepts
            "Read: Look at words. Understand meaning.",
            "Write: Make words letters. Tell stories.",
            "Grammar: Nouns Verbs Adjectives. Good sentences.",
            "Stories: Beginning middle end. Characters plot.",
            "Spell: Correct letter order. C-A-T = cat",
            "Words: Many words in English. Learn daily.",
            "Sentences: Start capital. End period.",
            "Alphabet: A B C D E F G H I J K L M N O P Q R S T U V W X Y Z"
    };

    public static String getResponse(int intentId) {
        if (intentId >= 0 && intentId < RESPONSES.length) {
            UserPreferences.incrementLocalAnswers();
            return RESPONSES[intentId];
        }
        return "I'm learning. Try: Math Science English";
    }
}