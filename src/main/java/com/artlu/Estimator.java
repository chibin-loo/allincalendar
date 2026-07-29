package com.artlu;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Guesses how long a task will take, so the scheduler has something better to
 * work with than a flat 90 minutes for everything.
 *
 * It's an interface with a single method so the guessing can be swapped out
 * later — an AI-backed estimator would implement this same method, and nothing
 * else in the app would need to change.
 */
public interface Estimator {

    /** Minutes this task should take, or 0 meaning "no opinion". */
    int estimateMinutes(String taskName, List<Event> history);

    /** The estimator the app currently uses. */
    Estimator ACTIVE = new RuleEstimator();

    /**
     * What the rest of the app calls. Falls back to the configured default when
     * the estimator has no opinion, so this never hands back 0.
     */
    static int minutesFor(String taskName, List<Event> history) {
        int guess = ACTIVE.estimateMinutes(taskName, history);
        return guess > 0 ? guess : Settings.getInt("default_task_minutes", 90);
    }
}

/**
 * Guesses from two things: tasks you've already sized yourself, and a table of
 * words that usually mean a certain amount of work.
 */
class RuleEstimator implements Estimator {

    // Checked in order, so the specific phrases sit above the general ones —
    // "final project" has to be tested before plain "project" can match it.
    private static final LinkedHashMap<String, Integer> WORDS = new LinkedHashMap<>();
    static {
        WORDS.put("final project", 360);
        WORDS.put("term paper", 300);
        WORDS.put("project", 300);
        WORDS.put("essay", 180);
        WORDS.put("paper", 180);
        WORDS.put("presentation", 150);
        WORDS.put("report", 150);
        WORDS.put("slides", 120);
        WORDS.put("lab", 120);
        WORDS.put("study", 120);
        WORDS.put("problem set", 90);
        WORDS.put("pset", 90);
        WORDS.put("homework", 90);
        WORDS.put("assignment", 90);
        WORDS.put("review", 90);
        WORDS.put("worksheet", 60);
        WORDS.put("quiz", 45);
        WORDS.put("reading", 45);
        WORDS.put("chapter", 45);
        WORDS.put("read", 45);
        WORDS.put("discussion", 30);
        WORDS.put("post", 30);
        WORDS.put("survey", 15);
    }

    public int estimateMinutes(String taskName, List<Event> history) {
        if (taskName == null || taskName.isBlank()) {
            return 0;
        }
        int fromYou = fromHistory(taskName, history);
        if (fromYou > 0) {
            return fromYou; // what you did last time beats any word list
        }
        return fromWords(taskName);
    }

    // "CS 180 Project 3" and "CS 180 Project 2" are the same job with a different
    // number, so strip the digits and punctuation and compare what's left.
    static String shape(String name) {
        return name.toLowerCase()
                .replaceAll("[^a-z ]", " ")
                .replaceAll(" +", " ")
                .trim();
    }

    // Averages the lengths you gave past tasks of the same shape. Kept exact —
    // one past match hands back precisely the number you set.
    private int fromHistory(String taskName, List<Event> history) {
        if (history == null) {
            return 0;
        }
        String shape = shape(taskName);
        if (shape.isEmpty()) {
            return 0;
        }

        int total = 0;
        int count = 0;
        for (Event e : history) {
            if (!e.userAdded || !e.kind.equals("task") || e.durationMin <= 0) {
                continue; // only tasks you made and sized yourself
            }
            if (e.name.equalsIgnoreCase(taskName)) {
                continue; // don't let a task teach itself its own length
            }
            if (shape(e.name).equals(shape)) {
                total += e.durationMin;
                count++;
            }
        }
        if (count == 0) {
            return 0;
        }
        return Math.round((float) total / count);
    }

    private int fromWords(String taskName) {
        String text = taskName.toLowerCase();
        for (Map.Entry<String, Integer> rule : WORDS.entrySet()) {
            if (text.contains(rule.getKey())) {
                return rule.getValue();
            }
        }
        return 0; // nothing matched — let the caller use the default
    }
}
