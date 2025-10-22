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
        } catch (Exception e) {
            showError("AI init failed: " + e.getMessage());
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
            case 1: processQuery("math help"); break;
            case 2: processQuery("science help"); break;
            case 3: processQuery("english help"); break;
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
            byte intentId = aiModel.predict(question);
            float confidence = aiModel.getLastConfidence();

            if (confidence > 0.6f) {
                // Local response
                String response = responses.getResponse(intentId);
                showResponse(response, "ElimuSMS");
            } else {
                // Would send to cloud in real version
                showResponse("Complex question - would use cloud AI", "AI Thinking");
            }
        } catch (Exception e) {
            showError("Error: " + e.getMessage());
        }
    }

    private void showResponse(String response, String title) {
        Alert alert = new Alert(title);
        alert.setString(response);
        alert.setTimeout(5000);
        display.setCurrent(alert, mainMenu);
    }

    private void showProgress() {
        int total = UserPreferences.getTotalQuestions();
        int local = UserPreferences.getLocalAnswers();
        int savings = local; // 1 KES per local answer

        String progress = "Questions: " + total +
                "\nLocal Answers: " + local +
                "\nSavings: " + savings + " KES";
        showResponse(progress, "My Progress");
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