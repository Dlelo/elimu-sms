package com.elimu;

import javax.microedition.lcdui.*;

/**
 * NASA-TLX cognitive load instrument, simplified for Grade-6 students.
 *
 * The original NASA-TLX uses 6 sub-scales rated 0-100 with optional
 * pairwise weighting (Hart & Staveland, 1988). For 11-year-olds we
 * keep the 6 dimensions but rate each on a 1-5 Likert scale and skip
 * the pairwise weighting. Reported as raw per-dimension scores plus
 * an unweighted mean.
 *
 * Sub-scales:
 *   0 Mental demand     — How mentally demanding was the task?
 *   1 Physical demand   — How physically demanding was the task?
 *   2 Temporal demand   — How hurried or rushed was the pace?
 *   3 Performance       — How successful were you? (reverse-coded)
 *   4 Effort            — How hard did you have to work?
 *   5 Frustration       — How insecure, discouraged, irritated?
 *
 * Triggered from the "Quick Feedback" main-menu entry. The MIDlet
 * drives the state machine: call advance(score) after each dimension's
 * Select; when isComplete() returns true, persist via
 * EvaluationLogger.recordTLX().
 */
public class TLXSurvey {

    public static final int N_DIMS = 6;

    private static final String[] PROMPTS = {
        "How mentally demanding?",
        "How physically demanding?",
        "How rushed did you feel?",
        "How well did you do? (1=poorly, 5=well)",
        "How hard did you work?",
        "How frustrated were you?",
    };

    private static final String[] LIKERT_5 = {
        "1 - Very low",
        "2 - Low",
        "3 - Medium",
        "4 - High",
        "5 - Very high",
    };

    private final int[] scores = new int[N_DIMS];
    private int cursor = 0;

    /** Returns the List screen for the current dimension. */
    public List currentScreen() {
        if (cursor >= N_DIMS) return null;
        List l = new List(PROMPTS[cursor], Choice.IMPLICIT);
        for (int i = 0; i < LIKERT_5.length; i++) {
            l.append(LIKERT_5[i], null);
        }
        return l;
    }

    /** Record the (1-based) Likert score for the current dimension. */
    public void submit(int likert1to5) {
        if (cursor >= N_DIMS) return;
        if (likert1to5 < 1) likert1to5 = 1;
        if (likert1to5 > 5) likert1to5 = 5;
        scores[cursor++] = likert1to5;
    }

    public boolean isComplete() {
        return cursor >= N_DIMS;
    }

    public int currentDimensionIndex() {
        return cursor;
    }

    public int[] getScores() {
        int[] out = new int[N_DIMS];
        System.arraycopy(scores, 0, out, 0, N_DIMS);
        return out;
    }

    /** Unweighted mean of the 6 sub-scales. Returns 0 if not complete. */
    public float meanScore() {
        if (!isComplete()) return 0.0f;
        int sum = 0;
        for (int i = 0; i < N_DIMS; i++) sum += scores[i];
        return sum / (float) N_DIMS;
    }
}
