package com.elimu;

import javax.microedition.midlet.*;
import javax.microedition.lcdui.*;

public class ElimuSMSMidlet extends MIDlet implements CommandListener {
    private Display display;
    private List mainMenu;
    private Form questionForm;
    private TextField questionField;

    private CompressedTinyML aiModel;
    private MicroResponses responses;

    private Command selectCmd = new Command("Select", Command.OK, 1);
    private Command sendCmd = new Command("Send", Command.OK, 1);
    private Command backCmd = new Command("Back", Command.BACK, 2);
    private Command exitCmd = new Command("Exit", Command.EXIT, 3);

    public ElimuSMSMidlet() {
        display = Display.getDisplay(this);
    }

    public void startApp() {
        initializeAI();
        showMainMenu();
    }

    private void initializeAI() {
        try {
            aiModel = new CompressedTinyML();
            responses = new MicroResponses();
            aiModel.loadModel();

            // Test the enhanced model
            System.out.println("=== Testing Enhanced AI Model ===");
            aiModel.testModel(); // This will run all test cases

        } catch (Exception e) {
            StringBuffer sb = new StringBuffer();
            sb.append("AI init failed: ");
            sb.append(e.getMessage());
            showError(sb.toString());
        }
    }

    private void showMainMenu() {
        mainMenu = new List("ElimuSMS - Learn CBC", Choice.IMPLICIT);

        mainMenu.append("Ask Question", null);
        mainMenu.append("Math Help", null);
        mainMenu.append("Science Help", null);
        mainMenu.append("English Help", null);
        mainMenu.append("Take Quiz", null);
        mainMenu.append("My Progress", null);

        mainMenu.addCommand(selectCmd);
        mainMenu.addCommand(exitCmd);
        mainMenu.setCommandListener(this);

        display.setCurrent(mainMenu);
    }

    public void commandAction(Command c, Displayable d) {
        if (c == exitCmd) {
            destroyApp(false);
            notifyDestroyed();
        } else if (c == selectCmd && d == mainMenu) {
            handleMenuSelection();
        } else if (c == sendCmd && d == questionForm) {
            handleQuestionSubmission();
        } else if (c == backCmd) {
            showMainMenu();
        }
    }

    private void handleMenuSelection() {
        int index = mainMenu.getSelectedIndex();
        switch (index) {
            case 0: showQuestionScreen(); break;
            case 1: showQuestionScreen(); break; // Math help
            case 2: showQuestionScreen(); break; // Science help
            case 3: showQuestionScreen(); break; // English help
            case 4: processQuery("quiz"); break;
            case 5: showProgress(); break;
        }
    }

    private void showQuestionScreen() {
        questionForm = new Form("Ask Question");
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

    private void processQuery(String question) {
        try {
            // Debug output
            StringBuffer debug = new StringBuffer();
            debug.append("Question: ");
            debug.append(question);
            System.out.println(debug.toString());

            // TEMPORARY FIX: Check for plants first
            String lower = question.toLowerCase();
            if (contains(lower, "plant") || contains(lower, "plants")) {
                handleScienceQuestion(question);
                return;
            }

            // Then try the AI model
            byte intentId = aiModel.predict(question);
            float confidence = aiModel.getLastConfidence();

            debug = new StringBuffer();
            debug.append("Predicted intent: ");
            debug.append(intentId);
            debug.append(" Confidence: ");
            debug.append(confidence);
            System.out.println(debug.toString());

            if (confidence > 0.5f) { // Increased threshold
                switch (intentId) {
                    case 0: handleMathQuestion(question); break;
                    case 1: handleScienceQuestion(question); break;
                    case 2: handleEnglishQuestion(question); break;
                    case 3: showResponse("I can give you practice questions on math, science or English!", "Quiz"); break;
                    case 6: showResponse("Hello! How can I help you learn today?", "Greeting"); break;
                    case 7: showResponse("Goodbye! Come back anytime for more learning!", "Farewell"); break;
                    default: handleLowConfidence(question); break;
                }
            } else {
                handleLowConfidence(question);
            }

        } catch (Exception e) {
            showError("Oops! Something went wrong. Try a different question.");
        }
    }
    // ---------------- Math ----------------
    private void handleMathQuestion(String question) {
        if (question.indexOf('+') >= 0 || question.indexOf('-') >= 0
                || question.indexOf('*') >= 0 || question.indexOf('/') >= 0) {
            String result = evaluateMathExpression(question);
            showResponse(result, "Math Help");
        } else {
            showResponse("Try typing a math expression like 2+3 or 5*4.", "Math Help");
        }
    }

    // ---------------- Science ----------------
    private void handleScienceQuestion(String question) {
        String lower = question.toLowerCase();

        if (contains(lower, "plants")) {
            showResponse("Plants need sunlight, water, and nutrients to grow.", "Science Help");
        } else if (contains(lower, "animal") || contains(lower, "animals")) {
            showResponse("Animals need food, water, and shelter to survive.", "Science Help");
        } else if (contains(lower, "weather")) {
            showResponse("Weather includes rain, sun, clouds, and wind patterns.", "Science Help");
        } else if (contains(lower, "experiment")) {
            showResponse("Try simple experiments like planting seeds or observing insects.", "Science Help");
        } else {
            showResponse("For science: Ask about plants, animals, weather, or simple experiments.", "Science Help");
        }
    }

    // ---------------- English ----------------
    private void handleEnglishQuestion(String question) {
        String lower = question.toLowerCase();

        if (contains(lower, "noun")) {
            showResponse("A noun is a person, place, thing, or idea.", "English Help");
        } else if (contains(lower, "verb")) {
            showResponse("A verb is an action word, e.g., run, eat, jump.", "English Help");
        } else if (contains(lower, "sentence")) {
            showResponse("A sentence must have a subject and a predicate.", "English Help");
        } else {
            showResponse("For English: Ask about nouns, verbs, sentence structure, or reading.", "English Help");
        }
    }

    // ---------------- Low Confidence ----------------
    private void handleLowConfidence(String question) {
        if (question.indexOf('+') >= 0 || question.indexOf('-') >= 0
                || question.indexOf('*') >= 0 || question.indexOf('/') >= 0) {
            String result = evaluateMathExpression(question);
            showResponse(result, "Math Help");
            return;
        }

        showResponse("I can help with math, science, and English questions. Try asking specifically about one of these subjects!", "Assistant");
    }


    private String getFallbackResponse(String question) {
        String lower = question.toLowerCase();

        if (question.indexOf('+') >= 0 || question.indexOf('-') >= 0
                || question.indexOf('*') >= 0 || question.indexOf('/') >= 0) {
            return evaluateMathExpression(question);
        } else if (contains(lower, "math") || contains(lower, "calculate") || contains(lower, "number")) {
            return "For math help: Try asking about addition, subtraction, multiplication or division!";
        } else if (contains(lower, "science") || contains(lower, "experiment") || contains(lower, "nature")) {
            return "For science: Ask about plants, animals, weather or simple experiments!";
        } else if (contains(lower, "english") || contains(lower, "grammar") || contains(lower, "language")) {
            return "For English: Ask about verbs, nouns, sentence structure or reading!";
        } else if (contains(lower, "quiz") || contains(lower, "test") || contains(lower, "practice")) {
            return "I can give you practice questions on math, science or English!";
        } else if (contains(lower, "progress") || contains(lower, "stat") || contains(lower, "result")) {
            return "Check your learning progress and statistics here!";
        } else if (contains(lower, "hello") || contains(lower, "hi") || contains(lower, "hey")) {
            return "Hello! I can help with math, science and English questions. What would you like to learn?";
        } else if (contains(lower, "bye") || contains(lower, "exit") || contains(lower, "quit")) {
            return "Goodbye! Come back anytime for more learning!";
        } else {
            return "I can help with math, science, and English questions. Try asking specifically about one of these subjects!";
        }
    }

    private String removeSpaces(String str) {
        char[] chars = new char[str.length()];
        int j = 0;
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c != ' ') {
                chars[j++] = c;
            }
        }
        return new String(chars, 0, j);
    }

    // Simple math evaluator (handles +, -, *, /)
    private String evaluateMathExpression(String expr) {
        // Remove spaces
        expr = removeSpaces(expr);

        int plus = expr.indexOf('+');
        int minus = expr.indexOf('-');
        int mult = expr.indexOf('*');
        int div = expr.indexOf('/');

        try {
            if (plus > 0) {
                int a = Integer.parseInt(expr.substring(0, plus));
                int b = Integer.parseInt(expr.substring(plus + 1));
                return String.valueOf(a + b);
            } else if (minus > 0) {
                int a = Integer.parseInt(expr.substring(0, minus));
                int b = Integer.parseInt(expr.substring(minus + 1));
                return String.valueOf(a - b);
            } else if (mult > 0) {
                int a = Integer.parseInt(expr.substring(0, mult));
                int b = Integer.parseInt(expr.substring(mult + 1));
                return String.valueOf(a * b);
            } else if (div > 0) {
                int a = Integer.parseInt(expr.substring(0, div));
                int b = Integer.parseInt(expr.substring(div + 1));
                if (b == 0) return "Cannot divide by zero";
                return String.valueOf(a / b);
            } else {
                return "I couldn't understand the math expression.";
            }
        } catch (Exception e) {
            return "Error parsing numbers.";
        }
    }



    // Add this helper method to the MIDlet class
    private boolean contains(String str, String substring) {
        return str.indexOf(substring) != -1;
    }

    private void showResponse(String response, String title) {
        Alert alert = new Alert(title);
        alert.setString(response);
        alert.setTimeout(6000); // Longer timeout for reading
        display.setCurrent(alert, mainMenu);
    }

    private void showProgress() {
        int total = UserPreferences.getTotalQuestions();
        int local = UserPreferences.getLocalAnswers();
        int savings = local; // 1 KES per local answer

        // Use StringBuffer for progress message
        StringBuffer sb = new StringBuffer();
        sb.append("Questions: ");
        sb.append(total);
        sb.append("\nLocal Answers: ");
        sb.append(local);
        sb.append("\nSavings: ");
        sb.append(savings);
        sb.append(" KES");

        if (total > 0) {
            int percentage = (local * 100) / total;
            sb.append("\nLocal Success: ");
            sb.append(percentage);
            sb.append("%");
        }

        showResponse(sb.toString(), "My Progress");
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