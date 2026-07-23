package com.artlu;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

public class Main {

    // builds full list of events for front end
    static List<Event> buildEventList() throws Exception {
        List<Event> events = new ArrayList<>();

        for (int i = 1; i <= 10; i++) {
            String link = Settings.get("calendar" + i, "");
            if (!link.isBlank()) {
                addEvents(link, events);
            }
        }

        loadTasks(events);

        try {
            Gradescope.addGradescopeEvents(events);
        } catch (Exception ex) {
            ex.printStackTrace(); // don't let a Gradescope failure kill everything else
        }

        java.util.Set<String> doneKeys = loadDoneOverrides();
        for (Event e : events) {
            if (!e.userAdded && doneKeys.contains(doneKey(e))) {
                e.done = true;
            }
        }
        events.sort((a, b) -> whenKey(a).compareTo(whenKey(b)));
        return events;
    }

    // Appends one new task to tasks.txt
    static void saveNewTask(Event e) throws Exception {
        List<String> line = new ArrayList<>();
        line.add(lineFor(e));
        Files.write(Paths.get("tasks.txt"), line,
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.APPEND);
    }

    // Makes a sortable text key like "2026-01-20 18:00" from an event
    static String whenKey(Event e) {
        String t = e.time.isBlank() ? "00:00" : e.time;
        return e.date + " " + t;
    }

    // Pulls "HH:mm" out of something like 2026-01-20T10:30:00, or "" if there's no
    // time
    static String extractTime(String iso) {
        int t = iso.indexOf("T");
        if (t >= 0 && iso.length() >= t + 6) {
            return iso.substring(t + 1, t + 6); // the "10:30" part
        }
        return "";
    }

    // Reads an iCalendar link and adds all events to the list
    static void addEvents(String link, List<Event> events) throws Exception {

        // read the iCalendar file from the link
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(link)).build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        net.fortuna.ical4j.data.CalendarBuilder builder = new net.fortuna.ical4j.data.CalendarBuilder();
        net.fortuna.ical4j.model.Calendar calendar = builder.build(new java.io.StringReader(response.body()));

        int monthsBack = Integer.parseInt(Settings.get("months_back", "1"));
        int monthsAhead = Integer.parseInt(Settings.get("months_ahead", "4"));

        java.time.LocalDateTime startTime = java.time.LocalDate.now().minusMonths(monthsBack).atStartOfDay();
        java.time.LocalDateTime endTime = java.time.LocalDate.now().plusMonths(monthsAhead).atStartOfDay();

        net.fortuna.ical4j.model.Period<java.time.LocalDateTime> period = new net.fortuna.ical4j.model.Period<>(
                startTime, endTime);

        for (net.fortuna.ical4j.model.component.CalendarComponent component : calendar.getComponents("VEVENT")) {
            net.fortuna.ical4j.model.component.VEvent vevent = (net.fortuna.ical4j.model.component.VEvent) component;

            String name = vevent.getSummary().map(s -> s.getValue()).orElse("(no title)");
            String uid = vevent.getUid().map(u -> u.getValue()).orElse("");

            // Purdue's feed has no URL field, so hunt for a link in the description or
            // location
            String description = vevent.getDescription().map(d -> d.getValue()).orElse("");
            String location = vevent.getLocation().map(l -> l.getValue()).orElse("");
            String url = findLink(description);
            if (url.isBlank()) {
                url = findLink(location);
            }

            var occurrences = vevent.calculateRecurrenceSet(period);

            for (var occurrence : occurrences) {
                java.time.temporal.Temporal start = occurrence.getStart();
                java.time.temporal.Temporal end = occurrence.getEnd();

                String isoStart = start.toString();
                String isoEnd = end.toString();

                Event e = new Event();
                e.name = name;
                e.date = isoStart.length() >= 10 ? isoStart.substring(0, 10) : isoStart;
                e.time = extractTime(isoStart);
                e.endDate = isoEnd.length() >= 10 ? isoEnd.substring(0, 10) : isoEnd;
                e.endTime = extractTime(isoEnd);
                e.description = description;
                e.url = url;
                e.uid = uid;
                events.add(e);
            }
        }
    }

    // check if event is happening today
    static boolean isToday(String date) {
        try {
            return LocalDate.parse(date).isEqual(LocalDate.now());
        } catch (Exception ex) {
            return false;
        }
    }

    // check date within 7 days of today
    static boolean isThisWeek(String date) {
        try {
            LocalDate d = LocalDate.parse(date);
            LocalDate today = LocalDate.now();
            return !d.isBefore(today) && !d.isAfter(today.plusDays(7));
        } catch (Exception ex) {
            return false;
        }
    }

    // Check if date/time is in the past
    static boolean isPast(String date, String time) {
        try {
            LocalDate day = LocalDate.parse(date);

            // If there's no time, treat it as end-of-day so it stays up all day
            LocalTime clock = time.isBlank() ? LocalTime.of(23, 59) : LocalTime.parse(time);

            LocalDateTime when = LocalDateTime.of(day, clock);
            return when.isBefore(LocalDateTime.now());
        } catch (Exception ex) {
            return false;
        }
    }

    // check if date is in the current calendar week (Monday-Sunday)
    static boolean isCalendarWeek(String date) {
        try {
            LocalDate d = LocalDate.parse(date);
            LocalDate today = LocalDate.now();
            // start from this monday
            LocalDate monday = today.minusDays(today.getDayOfWeek().getValue() - 1);
            LocalDate sunday = monday.plusDays(6);
            return !d.isBefore(monday) && !d.isAfter(sunday);
        } catch (Exception ex) {
            return false;
        }
    }

    // check if date is in the current month
    static boolean isThisMonth(String date) {
        try {
            LocalDate d = LocalDate.parse(date);
            LocalDate today = LocalDate.now();
            return d.getYear() == today.getYear() && d.getMonth() == today.getMonth();
        } catch (Exception ex) {
            return false;
        }
    }

    // Writes user tasks to tasks.txt
    static void saveTasks(List<Event> events) throws Exception {
        List<String> lines = new ArrayList<>();
        for (Event e : events) {
            if (e.userAdded) {
                lines.add(lineFor(e));
            }
        }
        Files.write(Paths.get("tasks.txt"), lines);
    }

    static String lineFor(Event e) {
        String safeDesc = e.description.replace("|", "/").replace("\n", " ");
        return e.name + "|" + e.date + "|" + e.time + "|" + e.done + "|"
                + e.endDate + "|" + e.endTime + "|" + safeDesc;
    }

    // Reads your saved tasks back from tasks.txt
    static void loadTasks(List<Event> events) throws Exception {

        if (!Files.exists(Paths.get("tasks.txt"))) {
            return;
        }

        List<String> lines = Files.readAllLines(Paths.get("tasks.txt"));
        for (String line : lines) {
            if (line.isBlank()) {
                continue;
            }

            String[] parts = line.split("\\|");
            // skip if malformed
            if (parts.length < 4) {
                continue;
            }

            Event e = new Event();
            e.name = parts[0];
            e.date = parts[1];
            e.time = parts[2];
            e.done = parts[3].equals("true");
            if (parts.length >= 6) { // older lines won't have these
                e.endDate = parts[4];
                e.endTime = parts[5];
            }
            if (parts.length >= 7) {
                e.description = parts[6];
            }
            e.userAdded = true;
            events.add(e);
        }
    }

    // Finds the first web link inside a chunk of text, or "" if there isn't one
    static String findLink(String text) {
        int start = text.indexOf("http");
        if (start < 0)
            return "";

        int end = start;
        while (end < text.length() && !Character.isWhitespace(text.charAt(end))) {
            end++;
        }
        return text.substring(start, end);
    }

    // Builds the key that identifies one occurrence of an imported event
    static String doneKey(Event e) {
        return e.uid + "|" + e.date;
    }

    // Reads the set of imported events the user has marked done
    static java.util.Set<String> loadDoneOverrides() throws Exception {
        java.util.Set<String> keys = new java.util.HashSet<>();
        if (!Files.exists(Paths.get("done-overrides.txt"))) {
            return keys; // no file yet, nothing marked
        }
        for (String line : Files.readAllLines(Paths.get("done-overrides.txt"))) {
            if (!line.isBlank()) {
                keys.add(line.trim());
            }
        }
        return keys;
    }

    // Remembers that the user marked this imported event done
    static void addDoneOverride(Event e) throws Exception {
        List<String> line = new ArrayList<>();
        line.add(doneKey(e));
        Files.write(Paths.get("done-overrides.txt"), line,
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.APPEND);
    }

    // Forgets that the user marked this imported event done
    static void removeDoneOverride(Event e) throws Exception {
        if (!Files.exists(Paths.get("done-overrides.txt"))) {
            return;
        }
        String key = doneKey(e);
        List<String> kept = new ArrayList<>();
        for (String line : Files.readAllLines(Paths.get("done-overrides.txt"))) {
            if (!line.trim().equals(key)) {
                kept.add(line); // keep everything except this one
            }
        }
        Files.write(Paths.get("done-overrides.txt"), kept);
    }

    // True if this date falls inside the window the user configured
    static boolean inWindow(String date) {
        try {
            LocalDate d = LocalDate.parse(date);
            int monthsBack = Integer.parseInt(Settings.get("months_back", "1"));
            int monthsAhead = Integer.parseInt(Settings.get("months_ahead", "4"));
            LocalDate start = LocalDate.now().minusMonths(monthsBack);
            LocalDate end = LocalDate.now().plusMonths(monthsAhead);
            return !d.isBefore(start) && !d.isAfter(end);
        } catch (Exception ex) {
            return true; // can't tell, so keep it
        }
    }

}

// Represents a single event or task
class Event {
    String name;
    String date;
    String time = "";
    String endDate = "";
    String endTime = "";
    boolean done = false;
    boolean userAdded = false;
    String url = "";
    String uid = "";
    String description = "";
}