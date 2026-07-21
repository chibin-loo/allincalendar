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
import java.util.Scanner;

public class Main {
    public static void main(String[] args) throws Exception {
        List<Event> events = new ArrayList<>();
        List<String> calendarLinks = Files.readAllLines(Paths.get("links.txt"));
        // read events from each link
        for (String link : calendarLinks) {
            if (link.isBlank()) {
                continue;
            }
            addEvents(link, events);
        }

        // load previously user saved tasks
        loadTasks(events);

        // loop to add tasks
        Scanner scanner = new Scanner(System.in);
        while (true) {

            System.out.print("\nAdd a task? Type its name, or type 'done' to finish: ");
            String name = scanner.nextLine().trim();

            // user is done adding tasks
            if (name.equalsIgnoreCase("done")) {
                break;
            }

            System.out.print("What date? (like 2026-01-18, or just press Enter to skip): ");
            String date = scanner.nextLine().trim();

            System.out.print("What time? (like 08:00, or Enter to skip): ");
            String time = scanner.nextLine().trim();

            // add new event
            Event e = new Event();
            e.name = name;
            e.date = date.isBlank() ? "no date" : date;
            e.time = time.isBlank() ? "no time" : time;
            e.userAdded = true;
            events.add(e);
        }

        // separate events into upcoming and past
        List<Event> upcoming = new ArrayList<>();
        List<Event> past = new ArrayList<>();
        for (Event e : events) {
            if (!isPast(e.date, e.time)) {
                upcoming.add(e);
            } else {
                past.add(e);
            }
        }

        // loop to remove tasks, mark them done, or view past events
        while (true) {

            // show upcoming events
            showList(upcoming);

            System.out.print(
                    "\nType a number to remove it, 'd' + number to mark done (like 'd3'), 'today', 'week', 'thisweek', 'month', 'past' to view today's, comming week's, current week's, current month's, or past events, or 'quit': ");
            String command = scanner.nextLine().trim();

            // quit the program
            if (command.equalsIgnoreCase("quit")) {
                saveTasks(upcoming, past);
                break;
            }

            // show todays events
            if (command.equalsIgnoreCase("today")) {
                showFiltered(upcoming, "today");
                continue;
            }

            // show this weeks events
            if (command.equalsIgnoreCase("week")) {
                showFiltered(upcoming, "week");
                continue;
            }

            // show events in calendar week (Monday-Sunday)
            if (command.equalsIgnoreCase("thisweek")) {
                showFiltered(upcoming, "thisweek");
                continue;
            }

            // show events in current month
            if (command.equalsIgnoreCase("month")) {
                showFiltered(upcoming, "month");
                continue;
            }

            // show past events
            if (command.equalsIgnoreCase("past")) {
                showList(past);
                continue;
            }

            // mark an event done
            if (command.toLowerCase().startsWith("d")) {
                int index = readNumber(command.substring(1));
                if (isValid(index, upcoming)) {
                    upcoming.get(index - 1).done = true;
                }
            } else {
                // remove event
                int index = readNumber(command);
                if (isValid(index, upcoming)) {
                    upcoming.remove(index - 1);
                }
            }
        }
        scanner.close();
    }

    // builds full list of events for front end
    static List<Event> buildEventList() throws Exception {
        List<Event> events = new ArrayList<>();

        List<String> calendarLinks = Files.readAllLines(Paths.get("links.txt"));
        for (String link : calendarLinks) {
            if (link.isBlank())
                continue;
            addEvents(link, events);
        }

        loadTasks(events);
        // Sort by date+time so earliest comes first
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

    // Prints events in list ex. "1) 2026-01-18 08:00 Study [done]"
    static void showList(List<Event> list) {
        System.out.println("\nYour list (" + list.size() + "):");
        for (int i = 0; i < list.size(); i++) {
            Event e = list.get(i);
            String when = e.time.isBlank() ? e.date : (e.date + " " + e.time);
            String mark = e.done ? " [done]" : "";
            System.out.println((i + 1) + ") " + when + "   " + e.name + mark);
        }
    }

    // Turns typed text into a number; returns -1 if it wasn't a number
    static int readNumber(String text) {
        try {
            return Integer.parseInt(text.trim());
        } catch (Exception ex) {
            return -1;
        }
    }

    // Checks the number actually points at an item in the list
    static boolean isValid(int index, List<Event> list) {
        if (index < 1 || index > list.size()) {
            System.out.println("That number isn't on the list.");
            return false;
        }
        return true;
    }

    // Reads an iCalendar link and adds all events to the list
    static void addEvents(String link, List<Event> events) throws Exception {

        // read the iCalendar file from the link
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(link)).build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        String[] lines = response.body().split("\n");

        String name = "";
        String date = "";

        // parse the iCalendar file line by line
        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.startsWith("SUMMARY:")) {
                name = line.substring(8);
            }
            if (line.startsWith("DTSTART")) {
                int colon = line.indexOf(":");
                date = line.substring(colon + 1);
            }
            // add each event to list
            if (line.startsWith("END:VEVENT")) {
                Event e = new Event();
                e.name = name;
                e.date = prettyDate(date);
                e.time = prettyTime(date);
                events.add(e);
                name = "";
                date = "";
            }
        }
    }

    // process dates
    static String prettyDate(String raw) {
        if (raw.length() >= 8) {
            return raw.substring(0, 4) + "-" + raw.substring(4, 6) + "-" + raw.substring(6, 8);
        }
        return raw;
    }

    // process times
    static String prettyTime(String raw) {
        int t = raw.indexOf("T");
        if (t >= 0 && raw.length() >= t + 5) {
            String hh = raw.substring(t + 1, t + 3);
            String mm = raw.substring(t + 3, t + 5);
            return hh + ":" + mm;
        }
        return "";
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

    // shows filtered events based on mode (today or week)
    static void showFiltered(List<Event> upcoming, String mode) {
        List<Event> slice = new ArrayList<>();
        for (Event e : upcoming) {
            if (mode.equals("today") && isToday(e.date))
                slice.add(e);
            if (mode.equals("week") && isThisWeek(e.date))
                slice.add(e);
            if (mode.equals("thisweek") && isCalendarWeek(e.date))
                slice.add(e);
            if (mode.equals("month") && isThisMonth(e.date))
                slice.add(e);
        }
        showList(slice);
    }

    // Writes user tasks to tasks.txt
    static void saveTasks(List<Event> upcoming, List<Event> past) throws Exception {
        List<String> lines = new ArrayList<>();

        // Check both lists, since a task could be upcoming or past
        for (Event e : upcoming) {
            if (e.userAdded)
                lines.add(lineFor(e));
        }
        for (Event e : past) {
            if (e.userAdded)
                lines.add(lineFor(e));
        }

        Files.write(Paths.get("tasks.txt"), lines);
        System.out.println("Saved " + lines.size() + " of your tasks.");
    }

    // Format events for storage ex. "Study|2026-01-20|18:00|false"
    static String lineFor(Event e) {
        return e.name + "|" + e.date + "|" + e.time + "|" + e.done;
    }

    // Reads your saved tasks back from tasks.txt
    static void loadTasks(List<Event> events) throws Exception {

        // no file do nothing
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

            // create event from line and add to list
            Event e = new Event();
            e.name = parts[0];
            e.date = parts[1];
            e.time = parts[2];
            e.done = parts[3].equals("true");
            e.userAdded = true;
            events.add(e);
        }
    }
}

// Represents a single event or task
class Event {
    String name;
    String date;
    String time = "";
    boolean done = false;
    boolean userAdded = false;
}