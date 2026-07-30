package com.artlu;

/**
 * Guesses whether something you typed is an event (it happens at a time) or a
 * task (you have to get it done by a time).
 *
 * Task words are checked first and win, because they're mostly verbs about
 * doing the work — "midterm exam" is an event, but "study for midterm exam" is
 * the work you do beforehand.
 */
public class Classify {

    private static final String[] TASK_WORDS = {
            "study", "write", "read", "finish", "submit", "prepare", "prep for",
            "review", "revise", "draft", "practice problems", "homework",
            "assignment", "problem set", "pset", "essay", "paper", "project",
            "worksheet", "report", "post", "quiz", "turn in", "due",
    };

    private static final String[] EVENT_WORDS = {
            "lecture", "class", "meeting", "appointment", "office hours", "exam",
            "midterm", "interview", "seminar", "workshop", "recitation",
            "practice", "rehearsal", "concert", "game", "party", "flight",
            "doctor", "dentist", "lunch", "dinner", "breakfast", "standup",
            "shift", "orientation", "ceremony", "wedding", "birthday",
    };

    /** True when the name reads like something that happens at a set time. */
    static boolean looksLikeEvent(String name) {
        if (name == null || name.isBlank()) {
            return false; // nothing typed yet — leave it on task
        }
        String text = name.toLowerCase();

        for (String word : TASK_WORDS) {
            if (text.contains(word)) {
                return false; // work to do, whatever else the name says
            }
        }
        for (String word : EVENT_WORDS) {
            if (text.contains(word)) {
                return true;
            }
        }
        return false; // no idea — a task is the safer default
    }
}
