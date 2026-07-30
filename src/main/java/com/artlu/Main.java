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

    // A fixed palette — muted enough to read white text on
    static final java.awt.Color[] PALETTE = {
            new java.awt.Color(120, 130, 200), // blue-violet
            new java.awt.Color(200, 120, 130), // rose
            new java.awt.Color(120, 170, 130), // green
            new java.awt.Color(200, 160, 100), // amber
            new java.awt.Color(140, 130, 175), // lavender
            new java.awt.Color(100, 165, 180), // teal
            new java.awt.Color(185, 135, 175), // orchid
            new java.awt.Color(150, 150, 110), // olive
    };

    static java.awt.Color colorFor(Event e) {
        String key;
        if (e.uid.startsWith("gradescope|")) {
            key = e.url; // same course = same colour
        } else if (!e.uid.isBlank()) {
            key = e.uid; // same recurring series = same colour
        } else {
            key = e.name;
        }
        return PALETTE[Math.abs(key.hashCode()) % PALETTE.length];
    }

    // Removes duplicate imported occurrences — the same event on the same day.
    // Happens when a feed sends a modified instance of a recurring event
    // alongside the original. Keeps the last one seen. Only safe to call while
    // the list holds imported calendar events (before tasks are added).
    static void dedupImported(List<Event> events) {
        java.util.Map<String, Event> seen = new java.util.LinkedHashMap<>();
        for (Event e : events) {
            String key = e.uid.isBlank()
                    ? "noid|" + e.date + "|" + e.time + "|" + e.name // no ID: fall back to details
                    : e.uid + "|" + e.date; // normal case
            seen.put(key, e); // same key again = overwrite, so the later copy wins
        }
        events.clear();
        events.addAll(seen.values());
    }

    // builds full list of events for front end
    static List<Event> buildEventList() throws Exception {
        List<Event> events = new ArrayList<>();

        for (int i = 1; i <= 10; i++) {
            String link = Settings.get("calendar" + i, "");
            if (!link.isBlank()) {
                addEvents(link, events);
            }
        }
        dedupImported(events);
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
        applyWorkBlocks(events);
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
                e.kind = "event";
                events.add(e);
            }
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
                + e.endDate + "|" + e.endTime + "|" + safeDesc + "|" + e.durationMin
                + "|" + e.kind;
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
            if (parts.length >= 8) {
                try {
                    e.durationMin = Integer.parseInt(parts[7]);
                } catch (Exception ignored) {
                }
            }
            e.userAdded = true;
            // Lines saved before events existed have no kind field — they're tasks.
            e.kind = parts.length >= 9 && !parts[8].isBlank() ? parts[8] : "task";
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

    // A stretch of free time on one day
    static class FreeBlock {
        LocalDate date;
        int startMin; // minutes since midnight
        int endMin;

        int lengthMin() {
            return endMin - startMin;
        }

        String startText() {
            return String.format("%02d:%02d", startMin / 60, startMin % 60);
        }

        String endText() {
            return String.format("%02d:%02d", endMin / 60, endMin % 60);
        }
    }

    // Turns "10:30" into 630
    static int minutesOf(String time) {
        try {
            LocalTime t = LocalTime.parse(time);
            return t.getHour() * 60 + t.getMinute();
        } catch (Exception ex) {
            return -1;
        }
    }

    // Finds the gaps between commitments on one day
    static List<FreeBlock> findFreeBlocks(List<Event> events, LocalDate day) {
        String daysOff = Settings.get("no_work_days", "");
        if (!daysOff.isBlank() && daysOff.contains(day.getDayOfWeek().toString())) {
            return new ArrayList<>();
        }
        int dayStart = minutesOf(Settings.get("day_start", "08:00"));
        int dayEnd = minutesOf(Settings.get("day_end", "22:00"));
        int minGap = Integer.parseInt(Settings.get("min_gap_minutes", "30"));

        // Collect the busy stretches for this day
        List<int[]> busy = new ArrayList<>();
        String iso = day.toString();
        for (Event e : events) {
            if (!e.date.equals(iso))
                continue;
            if (e.time.isBlank() || e.endTime.isBlank())
                continue; // deadlines don't block time

            int s = minutesOf(e.time);
            int en = minutesOf(e.endTime);
            if (s < 0 || en <= s)
                continue;
            busy.add(new int[] { s, en });
        }

        // Sort by start time
        busy.sort((a, b) -> Integer.compare(a[0], b[0]));

        // Merge overlapping or touching stretches
        List<int[]> merged = new ArrayList<>();
        for (int[] slot : busy) {
            if (!merged.isEmpty() && slot[0] <= merged.get(merged.size() - 1)[1]) {
                int[] last = merged.get(merged.size() - 1);
                last[1] = Math.max(last[1], slot[1]); // extend the existing stretch
            } else {
                merged.add(new int[] { slot[0], slot[1] });
            }
        }

        // Walk through the day, collecting the gaps
        List<FreeBlock> free = new ArrayList<>();
        int cursor = dayStart;
        for (int[] slot : merged) {
            if (slot[0] > cursor) {
                addIfLongEnough(free, day, cursor, Math.min(slot[0], dayEnd), minGap);
            }
            cursor = Math.max(cursor, slot[1]);
            if (cursor >= dayEnd)
                break;
        }
        if (cursor < dayEnd) {
            addIfLongEnough(free, day, cursor, dayEnd, minGap);
        }

        return free;
    }

    static void addIfLongEnough(List<FreeBlock> free, LocalDate day, int start, int end, int minGap) {
        if (end - start >= minGap) {
            FreeBlock b = new FreeBlock();
            b.date = day;
            b.startMin = start;
            b.endMin = end;
            free.add(b);
        }
    }

    // A scheduled chunk of work for a task
    static class WorkBlock {
        LocalDate date;
        int startMin;
        int endMin;
        Event task; // what this work is for

        String startText() {
            return String.format("%02d:%02d", startMin / 60, startMin % 60);
        }

        String endText() {
            return String.format("%02d:%02d", endMin / 60, endMin % 60);
        }
    }

    // Places work blocks for upcoming tasks into free time
    static List<WorkBlock> scheduleWork(List<Event> events) {
        int daysAhead = Settings.getInt("schedule_days_ahead", 60);
        int maxPerDay = Settings.getInt("max_work_minutes_per_day", 240);
        int breakMin = Settings.getInt("break_minutes", 15);
        // lead time and session length are per-task now — see Estimator.profile

        // Which items need work scheduled: not done, has a date, in the future
        List<Event> todo = new ArrayList<>();
        for (Event e : events) {
            if (e.date.isBlank() || e.date.equals("no date")) {
                continue;
            }
            if (!e.kind.equals("task")) {
                continue;
            }
            if (isPast(e.date, e.time)) {
                continue;
            }
            todo.add(e);
        }

        // Earliest deadline first
        todo.sort((a, b) -> whenKey(a).compareTo(whenKey(b)));

        // Gather free blocks for the next N days, in order
        List<FreeBlock> free = new ArrayList<>();
        for (int i = 1; i <= daysAhead; i++) {
            free.addAll(findFreeBlocks(events, LocalDate.now().plusDays(i)));
        }

        java.util.Map<LocalDate, Integer> usedPerDay = new java.util.HashMap<>();

        List<WorkBlock> scheduled = new ArrayList<>();

        for (Event task : todo) {
            Profile profile = Estimator.profile(task.name, events);
            int base = task.durationMin > 0 ? task.durationMin : profile.minutes;
            int pinnedMin = 0;
            for (Event e : events) {
                if (e.pinned && e.sourceTask != null && e.sourceTask.name.equals(task.name)) {
                    pinnedMin += minutesOf(e.endTime) - minutesOf(e.time);
                }
            }
            int remaining = base - pinnedMin;
            if (remaining <= 0) {
                continue; // you've manually placed the whole task already
            }
            LocalDate deadline = LocalDate.parse(task.date);
            LocalDate earliest = deadline.minusDays(profile.leadDays);

            // Two passes. The first gives this task at most one session a day, so
            // a long job spreads across its lead window instead of cramming into
            // the first days of it. If that can't get the work done before the
            // deadline, the second pass drops the restriction and packs it in.
            for (int pass = 0; pass < 2 && remaining > 0; pass++) {
                boolean oneSessionPerDay = (pass == 0);
                java.util.Set<LocalDate> daysTouched = new java.util.HashSet<>();

                for (FreeBlock block : free) {
                    if (remaining <= 0) {
                        break;
                    }
                    if (block.date.isBefore(earliest)) {
                        continue;
                    }
                    if (block.date.isAfter(deadline)) {
                        break; // too late to help
                    }
                    if (block.lengthMin() <= 0) {
                        continue; // already used up
                    }
                    if (oneSessionPerDay && daysTouched.contains(block.date)) {
                        continue; // this task already had its session today
                    }

                    int usedToday = usedPerDay.getOrDefault(block.date, 0);
                    int dayCapacity = maxPerDay - usedToday;
                    if (dayCapacity <= 0)
                        continue;
                    int use = Math.min(remaining, block.lengthMin());
                    use = Math.min(use, dayCapacity); // respect the daily cap
                    use = Math.min(use, profile.chunkMinutes); // no marathon blocks
                    if (use <= 0)
                        continue;

                    WorkBlock w = new WorkBlock();
                    w.date = block.date;
                    w.startMin = block.startMin;
                    w.endMin = block.startMin + use;
                    w.task = task;
                    scheduled.add(w);

                    block.startMin += use + breakMin; // consume it plus a breather
                    remaining -= use;
                    usedPerDay.put(block.date, usedToday + use);
                    daysTouched.add(block.date);
                }
            }

            // Whatever is still left never found a slot. Remember it so the UI
            // can say so, instead of quietly dropping the work.
            task.unscheduledMin = Math.max(0, remaining);
        }

        return scheduled;
    }

    // Groups events by their date string, so views can look up one day directly
    static java.util.Map<String, List<Event>> byDate(List<Event> events) {
        java.util.Map<String, List<Event>> map = new java.util.HashMap<>();
        for (Event e : events) {
            map.computeIfAbsent(e.date, k -> new ArrayList<>()).add(e);
        }
        return map;
    }

    static void applyWorkBlocks(List<Event> events) {
        events.removeIf(e -> e.sourceTask != null);
        for (Event e : events) {
            e.unscheduledMin = 0; // recomputed by scheduleWork below
        }

        // 1. Place manually pinned blocks first, so the auto-scheduler below
        // sees them as busy time and works around them.
        try {
            for (Pin p : loadPins()) {
                Event task = findTaskByName(events, p.taskName);
                if (task == null) {
                    continue; // the task was deleted — ignore its stale pin
                }
                Event e = new Event();
                e.name = "Work: " + p.taskName;
                e.date = p.date.toString();
                e.endDate = p.date.toString();
                e.time = fmtMinutes(p.startMin);
                e.endTime = fmtMinutes(p.endMin);
                e.kind = "work";
                e.uid = "work|" + p.taskName;
                e.userAdded = false;
                e.sourceTask = task;
                e.pinned = true;
                events.add(e);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        // 2. Auto-schedule whatever work is left over.
        if (Settings.get("auto_schedule", "true").equals("true")) {
            for (WorkBlock w : scheduleWork(events)) {
                Event e = new Event();
                e.name = "Work: " + w.task.name;
                e.date = w.date.toString();
                e.time = w.startText();
                e.endTime = w.endText();
                e.endDate = w.date.toString();
                e.kind = "work";
                e.uid = "work|" + w.task.name;
                e.userAdded = false;
                e.sourceTask = w.task;
                events.add(e);
            }
        }
        events.sort((a, b) -> whenKey(a).compareTo(whenKey(b)));
    }

    // A manually-placed work block, remembered across redraws in work-pins.txt.
    // Tab-separated because task names can contain almost anything else.
    static class Pin {
        String taskName;
        LocalDate date;
        int startMin, endMin;
    }

    static List<Pin> loadPins() throws Exception {
        List<Pin> pins = new ArrayList<>();
        if (!Files.exists(Paths.get("work-pins.txt"))) {
            return pins;
        }
        for (String line : Files.readAllLines(Paths.get("work-pins.txt"))) {
            if (line.isBlank()) {
                continue;
            }
            String[] p = line.split("\t");
            if (p.length < 4) {
                continue;
            }
            try {
                Pin pin = new Pin();
                pin.taskName = p[0];
                pin.date = LocalDate.parse(p[1]);
                pin.startMin = Integer.parseInt(p[2]);
                pin.endMin = Integer.parseInt(p[3]);
                pins.add(pin);
            } catch (Exception ignored) {
            }
        }
        return pins;
    }

    static void savePins(List<Pin> pins) throws Exception {
        List<String> lines = new ArrayList<>();
        for (Pin p : pins) {
            lines.add(p.taskName.replace("\t", " ") + "\t" + p.date
                    + "\t" + p.startMin + "\t" + p.endMin);
        }
        Files.write(Paths.get("work-pins.txt"), lines);
    }

    static void addPin(String name, LocalDate date, int startMin, int endMin) throws Exception {
        List<Pin> pins = loadPins();
        Pin p = new Pin();
        p.taskName = name;
        p.date = date;
        p.startMin = startMin;
        p.endMin = endMin;
        pins.add(p);
        savePins(pins);
    }

    // Removes a pin by task + day + start. No-op if there wasn't one there —
    // which is the case when you drag an auto block for the first time.
    static void removePin(String name, LocalDate date, int startMin) throws Exception {
        List<Pin> pins = loadPins();
        pins.removeIf(p -> p.taskName.equals(name) && p.date.equals(date) && p.startMin == startMin);
        savePins(pins);
    }

    // Pins are filed under the task's name, and applyWorkBlocks silently drops
    // any it can't match to a task — so a rename has to bring them along or
    // every block you dragged into place would vanish.
    static void renamePins(String oldName, String newName) throws Exception {
        List<Pin> pins = loadPins();
        boolean changed = false;
        for (Pin p : pins) {
            if (p.taskName.equals(oldName)) {
                p.taskName = newName;
                changed = true;
            }
        }
        if (changed) {
            savePins(pins);
        }
    }

    // A duration in words: 45 -> "45m", 120 -> "2h", 510 -> "8h 30m"
    static String fmtHours(int min) {
        int hours = min / 60;
        int minutes = min % 60;
        if (hours == 0) {
            return minutes + "m";
        }
        if (minutes == 0) {
            return hours + "h";
        }
        return hours + "h " + minutes + "m";
    }

    static String fmtMinutes(int min) {
        min = Math.max(0, Math.min(1439, min));
        return String.format("%02d:%02d", min / 60, min % 60);
    }

    static Event findTaskByName(List<Event> events, String name) {
        for (Event e : events) {
            if (e.kind.equals("task") && e.name.equals(name)) {
                return e;
            }
        }
        return null;
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
    String kind = "event";
    Event sourceTask = null;
    int durationMin = 0;
    boolean pinned = false;
    int unscheduledMin = 0; // work the scheduler couldn't find room for

    boolean isDone() {
        return sourceTask != null ? sourceTask.done : done;
    }
}