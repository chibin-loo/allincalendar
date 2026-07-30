package com.artlu;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Works out what the scheduler needs to know about a task: how long it takes,
 * how much runway it needs before the deadline, and how long a sitting should
 * be.
 *
 * It's an interface with a single method so the guessing can be swapped out
 * later — an AI-backed estimator would implement this same method, and nothing
 * else in the app would need to change. Note that it's called for every task on
 * every redraw, so an implementation that costs real time or money has to cache
 * its own answers.
 */
public interface Estimator {

    /** null when this estimator has no opinion about the task. */
    Profile profileFor(String taskName, List<Event> history);

    /** The estimator the app currently uses. */
    Estimator ACTIVE = new RuleEstimator();

    /**
     * A complete profile. Anything the estimator left blank is filled in from
     * Settings, and your settings bound the result either way: the session
     * length is a ceiling you set, and the lead time is a floor.
     */
    static Profile profile(String taskName, List<Event> history) {
        Profile p = ACTIVE.profileFor(taskName, history);
        if (p == null) {
            p = new Profile();
        }
        if (p.minutes <= 0) {
            p.minutes = Settings.getInt("default_task_minutes", 90);
        }

        int cap = Settings.getInt("max_block_minutes", 120);
        int floor = Settings.getInt("schedule_lead_days", 7);
        p.chunkMinutes = p.chunkMinutes <= 0 ? cap : Math.min(p.chunkMinutes, cap);
        p.leadDays = Math.max(p.leadDays, floor);
        return p;
    }

    /** Just the length — what the task form needs. */
    static int minutesFor(String taskName, List<Event> history) {
        return profile(taskName, history).minutes;
    }
}

/** What the scheduler needs to know about one task. */
class Profile {
    int minutes; // how much work it is
    int leadDays; // how long before the deadline to start
    int chunkMinutes; // how long one sitting should be

    Profile() {
    }

    Profile(int minutes, int leadDays, int chunkMinutes) {
        this.minutes = minutes;
        this.leadDays = leadDays;
        this.chunkMinutes = chunkMinutes;
    }

    Profile copy() {
        return new Profile(minutes, leadDays, chunkMinutes);
    }
}

/**
 * Guesses from two things: tasks you've already sized yourself, and a table of
 * words that usually mean a certain kind of work.
 */
class RuleEstimator implements Estimator {

    // Checked in order, so the specific phrases sit above the general ones —
    // "final project" has to be tested before plain "project" can match it.
    private static final LinkedHashMap<String, Profile> WORDS = new LinkedHashMap<>();

    private static void rule(String phrase, int minutes, int leadDays, int chunkMinutes) {
        WORDS.put(phrase, new Profile(minutes, leadDays, chunkMinutes));
    }

    static {
        // phrase minutes lead session
        rule("final project", 360, 21, 180);
        rule("term paper", 300, 21, 150);
        rule("project", 300, 14, 180);
        rule("essay", 180, 10, 120);
        rule("paper", 180, 10, 120);
        rule("presentation", 150, 10, 120);
        rule("report", 150, 10, 120);
        rule("slides", 120, 7, 120);
        rule("lab", 120, 5, 120);
        rule("study", 120, 7, 60); // spaced sessions beat one long cram
        rule("problem set", 90, 5, 90);
        rule("pset", 90, 5, 90);
        rule("homework", 90, 4, 90);
        rule("assignment", 90, 4, 90);
        rule("review", 90, 5, 45);
        rule("worksheet", 60, 3, 60);
        rule("quiz", 45, 3, 45);
        rule("reading", 45, 3, 45);
        rule("chapter", 45, 3, 45);
        rule("read", 45, 3, 45);
        rule("discussion", 30, 2, 30);
        rule("post", 30, 2, 30);
        rule("survey", 15, 2, 15);
    }

    public Profile profileFor(String taskName, List<Event> history) {
        if (taskName == null || taskName.isBlank()) {
            return null;
        }

        Profile fromTable = fromWords(taskName); // null if nothing matched
        int fromYou = fromHistory(taskName, history);

        if (fromYou > 0) {
            if (fromTable == null) {
                return sizedProfile(fromYou); // shape it from the size alone
            }
            fromTable.minutes = fromYou; // your own number beats the table's
        }
        return fromTable; // may still be null, meaning no opinion
    }

    // No keyword matched, so shape the profile from the size alone: roughly two
    // days of runway per hour of work, and a sitting no longer than the job.
    private static Profile sizedProfile(int minutes) {
        int hours = (int) Math.ceil(minutes / 60.0);
        int lead = Math.max(1, Math.min(21, hours * 2));
        int chunk = Math.max(30, Math.min(minutes, 120));
        return new Profile(minutes, lead, chunk);
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

    private Profile fromWords(String taskName) {
        String text = taskName.toLowerCase();
        for (Map.Entry<String, Profile> rule : WORDS.entrySet()) {
            if (text.contains(rule.getKey())) {
                return rule.getValue().copy(); // a copy — callers adjust theirs
            }
        }
        return null; // nothing matched
    }
}
