package com.elimu;

import javax.microedition.midlet.*;
import javax.microedition.lcdui.*;

public class ElimuSMSMidlet extends MIDlet implements CommandListener {

    // ── Screens ──────────────────────────────────────────────────────────────
    private Display    display;
    private List       mainMenu;
    private Form       questionForm;
    private TextField  questionField;
    private Form       quizForm;       // rebuilt per question
    private ChoiceGroup quizChoices;   // radio-button options

    // ── AI ───────────────────────────────────────────────────────────────────
    private CompressedTinyML aiModel;
    private MicroResponses   responses;

    // ── Commands ─────────────────────────────────────────────────────────────
    private Command selectCmd = new Command("Select", Command.OK,   1);
    private Command sendCmd   = new Command("Send",   Command.OK,   1);
    private Command submitCmd = new Command("Submit", Command.OK,   1);
    private Command backCmd   = new Command("Back",   Command.BACK, 2);
    private Command exitCmd   = new Command("Exit",   Command.EXIT, 3);

    // ── Quiz state ───────────────────────────────────────────────────────────
    private int quizIndex = 0;
    private int quizScore = 0;

    // ── Quiz bank (CBC Grade 6 STEM, 10 questions) ───────────────────────────
    private static final String[] QUESTIONS = {
        "What process do plants use to make their own food?",
        "How many chambers does the human heart have?",
        "How many pairs of legs do insects have?",
        "Which blood group is the universal donor?",
        "What is chlorophyll?",
        "Which blood vessel carries blood AWAY from the heart?",
        "Which type of root grows one main root deep into the soil?",
        "How many body parts do spiders have?",
        "What is the liquid part of blood called?",
        "What do stomata do?"
    };

    private static final String[][] OPTIONS = {
        {"A) Transpiration", "B) Photosynthesis", "C) Respiration",   "D) Absorption"},
        {"A) 2",             "B) 3",              "C) 4",             "D) 6"},
        {"A) 2 pairs",       "B) 3 pairs",        "C) 4 pairs",       "D) 5 pairs"},
        {"A) A",             "B) B",              "C) AB",            "D) O"},
        {"A) Type of root",  "B) Blood cell",     "C) Green pigment in leaves", "D) Type of stem"},
        {"A) Vein",          "B) Capillary",      "C) Artery",        "D) Plasma"},
        {"A) Fibrous",       "B) Aerial",         "C) Prop",          "D) Taproot"},
        {"A) 2",             "B) 3",              "C) 4",             "D) 6"},
        {"A) Haemoglobin",   "B) Platelets",      "C) Plasma",        "D) Serum"},
        {"A) Store food",    "B) Exchange gases", "C) Absorb water",  "D) Produce seeds"}
    };

    // Correct answer index (0=A, 1=B, 2=C, 3=D)
    private static final int[] ANSWERS = {1, 2, 1, 3, 2, 2, 3, 0, 2, 1};

    private static final String[] EXPLANATIONS = {
        "Photosynthesis: plants use sunlight, water and CO2 to make food.",
        "The heart has 4 chambers: 2 auricles (upper) and 2 ventricles (lower).",
        "Insects have 3 pairs of legs (6 legs total).",
        "Blood group O is the universal donor - it can donate to all groups.",
        "Chlorophyll is the green colouring matter in leaves used in photosynthesis.",
        "Arteries carry blood away from the heart under high pressure.",
        "Taproot: one main root growing deep with lateral side roots.",
        "Spiders have 2 body parts (head + abdomen) and 4 pairs of legs.",
        "Plasma is the pale yellow liquid part of blood.",
        "Stomata exchange gases: let in CO2 and release O2 and water vapour."
    };

    // ── Lifecycle ────────────────────────────────────────────────────────────
    public ElimuSMSMidlet() {
        display = Display.getDisplay(this);
    }

    public void startApp() {
        initializeAI();
        showMainMenu();
    }

    private void initializeAI() {
        try {
            aiModel   = new CompressedTinyML();
            responses = new MicroResponses();
            aiModel.loadModel();
            System.out.println("=== ElimuSMS STEM AI Ready ===");
            aiModel.testModel();
        } catch (Exception e) {
            StringBuffer sb = new StringBuffer("AI init failed: ");
            sb.append(e.getMessage());
            showError(sb.toString());
        }
    }

    // ── Main Menu ────────────────────────────────────────────────────────────
    private void showMainMenu() {
        mainMenu = new List("ElimuSMS - STEM Grade 6", Choice.IMPLICIT);
        mainMenu.append("Ask Question", null);
        mainMenu.append("Math Help",    null);
        mainMenu.append("Science Help", null);
        mainMenu.append("Take Quiz",    null);
        mainMenu.append("My Progress",  null);
        mainMenu.addCommand(selectCmd);
        mainMenu.addCommand(exitCmd);
        mainMenu.setCommandListener(this);
        display.setCurrent(mainMenu);
    }

    // ── Command routing ──────────────────────────────────────────────────────
    public void commandAction(Command c, Displayable d) {
        if (c == exitCmd) {
            destroyApp(false);
            notifyDestroyed();
        } else if (c == selectCmd && d == mainMenu) {
            handleMenuSelection();
        } else if (c == sendCmd && d == questionForm) {
            handleQuestionSubmission();
        } else if (c == submitCmd) {
            handleQuizSubmit();
        } else if (c == backCmd) {
            showMainMenu();
        }
    }

    private void handleMenuSelection() {
        switch (mainMenu.getSelectedIndex()) {
            case 0: showQuestionScreen(); break;
            case 1: showQuestionScreen(); break;  // Math
            case 2: showQuestionScreen(); break;  // Science
            case 3: startQuiz();          break;
            case 4: showProgress();       break;
        }
    }

    // ── Question Screen ──────────────────────────────────────────────────────
    private void showQuestionScreen() {
        questionForm = new Form("Ask a STEM Question");
        questionField = new TextField("Question:", "", 80, TextField.ANY);
        questionForm.append(questionField);
        questionForm.addCommand(sendCmd);
        questionForm.addCommand(backCmd);
        questionForm.setCommandListener(this);
        display.setCurrent(questionForm);
    }

    private void handleQuestionSubmission() {
        String question = questionField.getString().trim();
        if (question.length() == 0) return;
        processQuery(question);
    }

    // ── AI query dispatch ────────────────────────────────────────────────────
    private void processQuery(String question) {
        try {
            StringBuffer dbg = new StringBuffer("Question: ");
            dbg.append(question);
            System.out.println(dbg.toString());

            byte  intentId   = aiModel.predict(question);
            float confidence = aiModel.getLastConfidence();

            dbg = new StringBuffer("Intent: ");
            dbg.append(intentId);
            dbg.append("  Confidence: ");
            dbg.append(confidence);
            System.out.println(dbg.toString());

            if (confidence > 0.3f) {
                switch (intentId) {
                    case 0: handleMathQuestion(question);    break;
                    case 1: handleScienceQuestion(question); break;
                    case 2: handleScienceQuestion(question); break; // was English - route to science
                    case 3: startQuiz();                     break;
                    case 4: handleLowConfidence(question);   break;
                    case 5: showProgress();                  break;
                    case 6: showResponse("Hello! Ask me about math or science.", "Hi!"); break;
                    case 7: showResponse("Goodbye! Keep learning STEM!", "Bye!"); break;
                    default: handleLowConfidence(question);  break;
                }
            } else {
                handleLowConfidence(question);
            }
            UserPreferences.incrementLocalAnswers();
        } catch (Exception e) {
            showError("Oops! Something went wrong. Try a different question.");
        }
    }

    // ── Math ─────────────────────────────────────────────────────────────────
    private void handleMathQuestion(String question) {
        if (question.indexOf('+') >= 0 || question.indexOf('-') >= 0
                || question.indexOf('*') >= 0 || question.indexOf('/') >= 0) {
            showResponse(evaluateMathExpression(question), "Math");
        } else {
            showResponse("Type a math expression like 2+3, 5*4, 10-3 or 8/2.", "Math Help");
        }
    }

    // ── Science / Living Things (CBC Grade 6) ────────────────────────────────
    private void handleScienceQuestion(String question) {
        String lower = question.toLowerCase();

        // --- Plants ---
        if (contains(lower, "photosynthes")) {
            showResponse("Photosynthesis: plants make food using sunlight, water and CO2.\nNeeds: chlorophyll, water, carbon dioxide, sunlight.", "Science");
        } else if (contains(lower, "chlorophyll")) {
            showResponse("Chlorophyll is the green colouring matter in leaves. It absorbs sunlight for photosynthesis.", "Science");
        } else if (contains(lower, "stomata") || contains(lower, "stoma")) {
            showResponse("Stomata are tiny holes on leaves. They let in CO2 and release O2 and water vapour (transpiration).", "Science");
        } else if (contains(lower, "transpir")) {
            showResponse("Transpiration: plants lose excess water through stomata.\nHigh when: hot, sunny, dry, windy.\nLow when: cold, wet, calm, rainy.", "Science");
        } else if (contains(lower, "pollinat")) {
            showResponse("Pollination transfers pollen to fertilise flowers. Wind and insects (bees) are the main agents. It enables plants to produce seeds.", "Science");
        } else if (contains(lower, "type") && (contains(lower, "plant") || contains(lower, "root"))) {
            if (contains(lower, "root")) {
                showResponse("2 types of roots:\n1. Taproot - one main root growing deep (legumes, acacia, fruit trees)\n2. Fibrous roots - many equal shallow roots (grass, maize, onions, sugarcane)\nOthers: aerial roots (breathing), prop roots (support)", "Science - Root Types");
            } else {
                showResponse("4 types of plants:\n1. Trees - big, single trunk (mango, coconut, avocado)\n2. Shrubs - many woody stems (hibiscus, rose, cotton)\n3. Herbs - small, soft green stems (mint, coriander)\n4. Grass - short, narrow leaves", "Science - Plant Types");
            }
        } else if (contains(lower, "part") && contains(lower, "plant")) {
            showResponse("External parts of a plant:\n- Roots: absorb water, anchor, store food\n- Stem: transports water, supports\n- Leaves: photosynthesis, breathing\n- Flowers: reproductive organs\n- Fruits: protect seeds, store food\n- Seeds: germinate into new plants", "Science - Plant Parts");
        } else if (contains(lower, "function") && contains(lower, "root")) {
            showResponse("Functions of roots:\n1. Absorb water and mineral salts from soil\n2. Anchor/support the plant in soil\n3. Store food (carrots, cassava, arrowroots)", "Science - Roots");
        } else if (contains(lower, "function") && contains(lower, "stem")) {
            showResponse("Functions of the stem:\n1. Transports water from roots to leaves\n2. Carries food from leaves to roots\n3. Supports upper parts of the plant\n4. Stem tubers store food: cactus, sugarcane, Irish potato", "Science - Stem");
        } else if (contains(lower, "function") && (contains(lower, "leaf") || contains(lower, "leave"))) {
            showResponse("Functions of leaves:\n1. Make food via photosynthesis\n2. Breathing - exchange gases through stomata\n3. Store food (kales, cabbages, spinach)\n4. Remove excess water through transpiration", "Science - Leaves");
        } else if (contains(lower, "taproot") || contains(lower, "fibrous")) {
            showResponse("Taproot: one main root growing deep with lateral side roots.\nExamples: legumes, acacia, fruit trees.\n\nFibrous roots: many equal shallow roots, grow in all directions.\nExamples: cereals, grass, sisal, onions, sugarcane, coconuts.", "Science - Root Types");
        } else if (contains(lower, "plant") || contains(lower, "grow")) {
            showResponse("Plants are living things. Types: Trees, Shrubs, Herbs, Grass.\nParts: roots, stem, leaves, flowers, fruits, seeds.\nMake food by photosynthesis (sunlight + water + CO2 + chlorophyll).\nAsk about: types, parts, root types, functions.", "Science - Plants");

        // --- Invertebrates ---
        } else if (contains(lower, "insect") && (contains(lower, "character") || contains(lower, "feature") || contains(lower, "part") || contains(lower, "body"))) {
            showResponse("Insect characteristics:\n- 3 body parts: head, thorax, abdomen\n- 3 pairs of legs (6 legs)\n- Usually 2 pairs of wings (beetles/ants have no wings)\n- 1 pair of antennae (feelers)\n- Hard exoskeleton", "Science - Insects");
        } else if (contains(lower, "spider") || contains(lower, "tick")) {
            showResponse("Spiders & ticks:\n- 2 body parts\n- 4 pairs of legs (8 legs)\n- No wings\n- No antennae", "Science - Spiders & Ticks");
        } else if (contains(lower, "snail") || contains(lower, "slug")) {
            showResponse("Snails & slugs:\n- Soft body, no wings\n- 2 pairs of feelers (receptacles)\n- Move by crawling on slimy mucus using muscular foot\n- Snails have shells; slugs do not", "Science - Snails & Slugs");
        } else if (contains(lower, "centipede") || contains(lower, "millipede")) {
            showResponse("Centipedes: 1 pair of legs per segment.\nMillipedes: 2 pairs of legs per segment, coil when disturbed.\nBoth: 2 body sections (head + trunk) divided into many segments.", "Science - Centipedes & Millipedes");
        } else if (contains(lower, "importance") && contains(lower, "invertebr")) {
            showResponse("Importance of invertebrates:\n1. Food - termites eaten by some; bees produce honey\n2. Pollination - insects pollinate flowering plants\n3. Clean environment - millipedes eat decaying matter, making compost", "Science - Invertebrates");
        } else if (contains(lower, "invertebr")) {
            showResponse("Invertebrates: animals without a backbone.\nExamples: bees, flies, grasshoppers, earthworms, lobsters, snails, millipedes.\nGroups: insects, spiders/ticks, snails/slugs, centipedes/millipedes.", "Science - Invertebrates");
        } else if (contains(lower, "vertebr")) {
            showResponse("Vertebrates have a backbone. Examples: fish, amphibians, reptiles, birds and mammals.", "Science");
        } else if (contains(lower, "food chain") || contains(lower, "food web")) {
            showResponse("A food chain shows who eats whom. Producer -> Herbivore -> Carnivore. Energy flows along the chain.", "Science");
        } else if (contains(lower, "habitat")) {
            showResponse("A habitat is the natural home of an organism. It provides food, water, shelter and space.", "Science");

        // --- Circulatory System ---
        } else if (contains(lower, "circulat")) {
            showResponse("Circulatory system: heart + blood + blood vessels.\nTransports: oxygen (lungs to body), digested food, CO2 (body to lungs), heat and waste products.", "Science - Circulatory System");
        } else if (contains(lower, "heart") && (contains(lower, "chamber") || contains(lower, "auricle") || contains(lower, "ventricle") || contains(lower, "pump"))) {
            showResponse("Heart has 4 chambers:\n- 2 auricles (upper, thin walls) - receive blood\n- 2 ventricles (lower, thick walls) - pump blood\nRight auricle: receives deoxygenated blood.\nLeft ventricle: pumps oxygenated blood to body via aorta.", "Science - Heart");
        } else if (contains(lower, "artery") || contains(lower, "arteries")) {
            showResponse("Arteries:\n- Thick elastic walls, narrow lumen\n- Carry blood AWAY from heart\n- Carry oxygenated blood (except pulmonary artery)\n- Blood flows under HIGH pressure", "Science - Arteries");
        } else if (contains(lower, "vein")) {
            showResponse("Veins:\n- Thin walls, wide lumen\n- Have VALVES to prevent backflow\n- Carry blood TOWARDS heart\n- Carry deoxygenated blood (except pulmonary vein)", "Science - Veins");
        } else if (contains(lower, "capillar")) {
            showResponse("Capillaries:\n- Very thin walls, no valves\n- Reach every part of the body\n- Exchange: oxygen and food into cells; CO2 and wastes into blood", "Science - Capillaries");
        } else if (contains(lower, "pulse")) {
            showResponse("Pulse: beat felt in arteries caused by the heart pumping.\nNormal resting pulse: 60-100 beats per minute.\nPulse INCREASES during activity - body needs more oxygen.", "Science - Pulse");
        } else if (contains(lower, "plasma")) {
            showResponse("Plasma:\n- Liquid part of blood (pale yellow)\n- Transports: digested food, oxygen, CO2, waste products, heat, hormones, blood cells.", "Science - Plasma");
        } else if (contains(lower, "red blood")) {
            showResponse("Red blood cells:\n- Biconcave shape\n- Contain haemoglobin (carries oxygen)\n- No nucleus when mature\n- Made in red bone marrow\n- Carry oxygen from lungs to body", "Science - Red Blood Cells");
        } else if (contains(lower, "white blood")) {
            showResponse("White blood cells:\n- Larger than red cells, have nucleus\n- No definite shape\n- Made in yellow bone marrow and lymph glands\n- Fight and kill germs by engulfing them\n- Ratio to red cells: 1:600", "Science - White Blood Cells");
        } else if (contains(lower, "platelet")) {
            showResponse("Platelets:\n- Tiny oval shaped cells in plasma\n- Help blood CLOT when injured\n- Stop bleeding from cuts and wounds", "Science - Platelets");
        } else if (contains(lower, "blood group") || contains(lower, "blood type") || contains(lower, "transfus")) {
            showResponse("ABO Blood Groups:\n- O: Universal DONOR (gives to all)\n- AB: Universal RECIPIENT (receives from all)\n- A: gives to A and AB\n- B: gives to B and AB\nBlood transfusion replaces lost blood.", "Science - Blood Groups");
        } else if (contains(lower, "blood vessel")) {
            showResponse("Blood vessels:\n1. Arteries - thick walls, carry blood away from heart\n2. Veins - thin walls, valves, carry blood towards heart\n3. Capillaries - thin walls, reach every cell, exchange substances", "Science - Blood Vessels");
        } else if (contains(lower, "blood") || contains(lower, "heart")) {
            showResponse("Blood has 4 components:\n1. Plasma - liquid, transports substances\n2. Red blood cells - carry oxygen\n3. White blood cells - fight germs\n4. Platelets - clotting\nThe heart pumps blood through arteries, veins and capillaries.", "Science - Blood");

        // --- Reproductive System ---
        } else if (contains(lower, "ovary") || contains(lower, "oviduct") || contains(lower, "fallopian")) {
            showResponse("Ovary: produces egg cells (ova) by ovulation, produces hormones.\nOviduct (Fallopian tube): connects ovary to uterus; site of fertilization.", "Science - Female Reproduction");
        } else if (contains(lower, "uterus") || contains(lower, "womb") || contains(lower, "cervix")) {
            showResponse("Uterus (womb): where fertilized egg develops into foetus.\nCervix: connects vagina to uterus; opens during childbirth.", "Science - Female Reproduction");
        } else if (contains(lower, "female") && contains(lower, "reproduct")) {
            showResponse("Female reproductive system:\n- Ovaries: produce eggs (ova)\n- Oviduct: fertilization occurs here\n- Uterus/Womb: foetus develops here\n- Cervix: connects vagina to uterus\n- Vagina: birth canal", "Science - Female Reproduction");
        } else if (contains(lower, "testis") || contains(lower, "testes") || contains(lower, "sperm")) {
            showResponse("Testis: produces sperm and hormones, enclosed in scrotum.\nSperm duct: carries sperm to urethra.\nGlands produce seminal fluid. Semen = seminal fluid + sperm.", "Science - Male Reproduction");
        } else if (contains(lower, "male") && contains(lower, "reproduct")) {
            showResponse("Male reproductive system:\n- Testis: produces sperm and hormones\n- Sperm duct: carries sperm to urethra\n- Urethra: passage for sperm and urine\n- Glands: produce seminal fluid\n- Penis: delivers sperm", "Science - Male Reproduction");
        } else if (contains(lower, "reproduct")) {
            showResponse("Reproductive systems allow living things to produce young.\nFemale: ovaries, oviduct, uterus, cervix, vagina.\nMale: testis, sperm duct, urethra, glands, penis.", "Science - Reproduction");

        // --- Adolescence ---
        } else if (contains(lower, "adolescen") || contains(lower, "puberty")) {
            showResponse("Adolescence (age 12-19):\nBoys: voice breaks, chest broadens, facial hair, sperm mature, wet dreams.\nGirls: breasts grow, hips widen, menstruation begins (every 28 days).\nBoth: body hair, pimples (acne), rapid height and weight increase.", "Science - Adolescence");
        } else if (contains(lower, "menstruat")) {
            showResponse("Menstruation: monthly shedding of uterus lining if egg is not fertilized.\nOccurs once a month, lasts 4-5 days.\nOvulation: release of an egg from the ovary every 28 days.", "Science - Menstruation");

        // --- Water Conservation ---
        } else if (contains(lower, "conserv") && contains(lower, "water")) {
            showResponse("Water conservation: proper care and use of water.\nWays to conserve:\n1. Harvest rainwater\n2. Reuse water\n3. Use water sparingly\n4. Mulching/shading\n5. Build dams\n6. Recycle water\n7. Reduce water use", "Science - Water Conservation");
        } else if ((contains(lower, "reus") || contains(lower, "reuse")) && contains(lower, "water")) {
            showResponse("Reusing water:\n- Clothes wash water: flush toilet, clean house\n- Fruit/vegetable wash water: water crops on farm\n- Clothes water: sprinkle on earthen floors to reduce dust\n- Handwashing water: mop floors", "Science - Water Reuse");

        // --- General ---
        } else if (contains(lower, "living")) {
            showResponse("Living things: move, feed, grow, breathe, reproduce, respond to stimuli and excrete waste.\nExamples: plants, animals, fungi.", "Science - Living Things");
        } else if (contains(lower, "animal")) {
            showResponse("Animals: classified as vertebrates (have backbone) or invertebrates (no backbone).\nInvertebrates include: insects, spiders, snails, centipedes, millipedes.", "Science");
        } else if (contains(lower, "experiment")) {
            showResponse("Try: germinate seeds in wet cotton, observe plants in light vs dark, check pulse before/after exercise, or track animal behaviour.", "Science");
        } else {
            showResponse("Grade 6 STEM Science topics:\n- Plants (types, parts, roots)\n- Invertebrates (insects, spiders, snails)\n- Circulatory system (heart, blood, vessels)\n- Reproductive system\n- Adolescence\n- Water conservation\nAsk about any topic!", "Science");
        }
    }

    // ── Low confidence fallback ───────────────────────────────────────────────
    private void handleLowConfidence(String question) {
        if (question.indexOf('+') >= 0 || question.indexOf('-') >= 0
                || question.indexOf('*') >= 0 || question.indexOf('/') >= 0) {
            showResponse(evaluateMathExpression(question), "Math");
            return;
        }
        showResponse("I can help with Math and Science questions.\nTry: 'photosynthesis', 'heart chambers', 'types of roots', '5*4'", "STEM Assistant");
    }

    // ── Quiz ─────────────────────────────────────────────────────────────────
    private void startQuiz() {
        quizIndex = 0;
        quizScore = 0;
        showQuizQuestion(0);
    }

    private void showQuizQuestion(int index) {
        StringBuffer title = new StringBuffer("Quiz ");
        title.append(index + 1);
        title.append("/");
        title.append(QUESTIONS.length);

        quizForm = new Form(title.toString());

        StringItem qItem = new StringItem("", QUESTIONS[index]);
        quizForm.append(qItem);

        quizChoices = new ChoiceGroup("", Choice.EXCLUSIVE);
        for (int i = 0; i < OPTIONS[index].length; i++) {
            quizChoices.append(OPTIONS[index][i], null);
        }
        quizForm.append(quizChoices);

        quizForm.addCommand(submitCmd);
        quizForm.addCommand(backCmd);
        quizForm.setCommandListener(this);
        display.setCurrent(quizForm);
    }

    private void handleQuizSubmit() {
        int selected = quizChoices.getSelectedIndex();
        if (selected < 0) {
            showError("Please select an answer first!");
            return;
        }

        boolean correct = (selected == ANSWERS[quizIndex]);
        if (correct) quizScore++;

        StringBuffer msg = new StringBuffer();
        if (correct) {
            msg.append("CORRECT!\n\n");
        } else {
            msg.append("WRONG!\n\nCorrect answer: ");
            msg.append(OPTIONS[quizIndex][ANSWERS[quizIndex]]);
            msg.append("\n\n");
        }
        msg.append(EXPLANATIONS[quizIndex]);

        int nextIndex = quizIndex + 1;
        quizIndex = nextIndex;

        Displayable nextScreen;
        if (nextIndex < QUESTIONS.length) {
            // Pre-build the next question form so the Alert can transition to it
            quizIndex = nextIndex;
            StringBuffer nextTitle = new StringBuffer("Quiz ");
            nextTitle.append(nextIndex + 1);
            nextTitle.append("/");
            nextTitle.append(QUESTIONS.length);
            Form nextQuizForm = new Form(nextTitle.toString());
            StringItem nqItem = new StringItem("", QUESTIONS[nextIndex]);
            nextQuizForm.append(nqItem);
            ChoiceGroup nextChoices = new ChoiceGroup("", Choice.EXCLUSIVE);
            for (int i = 0; i < OPTIONS[nextIndex].length; i++) {
                nextChoices.append(OPTIONS[nextIndex][i], null);
            }
            nextQuizForm.append(nextChoices);
            nextQuizForm.addCommand(submitCmd);
            nextQuizForm.addCommand(backCmd);
            nextQuizForm.setCommandListener(this);
            quizForm    = nextQuizForm;
            quizChoices = nextChoices;
            nextScreen  = nextQuizForm;
        } else {
            // All questions done — build results screen
            UserPreferences.recordQuizResult(quizScore, QUESTIONS.length);
            nextScreen = buildResultsForm();
        }

        Alert feedback = new Alert(correct ? "Correct!" : "Wrong");
        feedback.setString(msg.toString());
        feedback.setTimeout(4000);
        display.setCurrent(feedback, nextScreen);
    }

    private Form buildResultsForm() {
        Form results = new Form("Quiz Results");

        int total = QUESTIONS.length;
        int pct   = (quizScore * 100) / total;
        String grade;
        if      (pct >= 80) grade = "A - Excellent!";
        else if (pct >= 60) grade = "B - Good";
        else if (pct >= 40) grade = "C - Keep studying";
        else                grade = "D - Review the topics";

        StringBuffer sb = new StringBuffer();
        sb.append("Score: ");  sb.append(quizScore);
        sb.append(" / ");      sb.append(total);
        sb.append("\nPercentage: "); sb.append(pct); sb.append("%");
        sb.append("\nGrade: ");      sb.append(grade);
        sb.append("\n\nTotal quizzes taken: ");
        sb.append(UserPreferences.getQuizAttempts());
        sb.append("\nOverall correct: ");
        sb.append(UserPreferences.getQuizTotalCorrect());
        sb.append(" / ");
        sb.append(UserPreferences.getQuizTotalAnswered());

        results.append(new StringItem("", sb.toString()));
        results.addCommand(backCmd);
        results.setCommandListener(this);
        return results;
    }

    // ── Progress ─────────────────────────────────────────────────────────────
    private void showProgress() {
        int questions = UserPreferences.getTotalQuestions();
        int local     = UserPreferences.getLocalAnswers();
        int attempts  = UserPreferences.getQuizAttempts();
        int correct   = UserPreferences.getQuizTotalCorrect();
        int answered  = UserPreferences.getQuizTotalAnswered();

        StringBuffer sb = new StringBuffer();
        sb.append("=== My STEM Progress ===");
        sb.append("\nQuestions asked: "); sb.append(questions);
        sb.append("\nAnswered on-device: "); sb.append(local);

        sb.append("\n\n=== Quiz History ===");
        sb.append("\nQuizzes taken: "); sb.append(attempts);
        if (answered > 0) {
            sb.append("\nTotal correct: "); sb.append(correct);
            sb.append(" / "); sb.append(answered);
            int overallPct = (correct * 100) / answered;
            sb.append("\nOverall score: "); sb.append(overallPct); sb.append("%");
            String overallGrade;
            if      (overallPct >= 80) overallGrade = "A - Excellent!";
            else if (overallPct >= 60) overallGrade = "B - Good";
            else if (overallPct >= 40) overallGrade = "C - Keep studying";
            else                       overallGrade = "D - Review topics";
            sb.append("\nGrade: "); sb.append(overallGrade);
        } else {
            sb.append("\nNo quizzes taken yet.\nTake a quiz to track your score!");
        }

        showResponse(sb.toString(), "My Progress");
    }

    // ── Utilities ─────────────────────────────────────────────────────────────
    private boolean contains(String str, String sub) {
        return str.indexOf(sub) != -1;
    }

    private String removeSpaces(String str) {
        char[] chars = new char[str.length()];
        int j = 0;
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c != ' ') chars[j++] = c;
        }
        return new String(chars, 0, j);
    }

    private String evaluateMathExpression(String expr) {
        expr = removeSpaces(expr);
        int plus  = expr.indexOf('+');
        int minus = expr.indexOf('-');
        int mult  = expr.indexOf('*');
        int div   = expr.indexOf('/');
        try {
            if (plus > 0) {
                return String.valueOf(Integer.parseInt(expr.substring(0, plus))
                                   + Integer.parseInt(expr.substring(plus + 1)));
            } else if (minus > 0) {
                return String.valueOf(Integer.parseInt(expr.substring(0, minus))
                                   - Integer.parseInt(expr.substring(minus + 1)));
            } else if (mult > 0) {
                return String.valueOf(Integer.parseInt(expr.substring(0, mult))
                                   * Integer.parseInt(expr.substring(mult + 1)));
            } else if (div > 0) {
                int b = Integer.parseInt(expr.substring(div + 1));
                if (b == 0) return "Cannot divide by zero";
                return String.valueOf(Integer.parseInt(expr.substring(0, div)) / b);
            } else {
                return "Type an expression like 2+3 or 5*4";
            }
        } catch (Exception e) {
            return "Error parsing numbers";
        }
    }

    private void showResponse(String response, String title) {
        Alert alert = new Alert(title);
        alert.setString(response);
        alert.setTimeout(6000);
        display.setCurrent(alert, mainMenu);
    }

    private void showError(String message) {
        Alert error = new Alert("Error");
        error.setString(message);
        error.setTimeout(3000);
        display.setCurrent(error, mainMenu);
    }

    public void pauseApp() {}
    public void destroyApp(boolean unconditional) {}
}
