package com.artlu;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

public class Gradescope {

    // Logs in and returns the session cookies needed for later requests
    static Map<String, String> login() throws Exception {

        String email = Settings.get("gradescope_email", "");
        String password = Settings.get("gradescope_password", "");
        if (email.isBlank() || password.isBlank()) {
            throw new Exception("Gradescope email/password not set in settings.");
        }

        // Step 1: load the login page to get its hidden token and first cookies
        Connection.Response loginPage = Jsoup.connect("https://www.gradescope.com/login")
                .method(Connection.Method.GET)
                .execute();

        Document doc = loginPage.parse();
        String token = doc.select("input[name=authenticity_token]").attr("value");

        // Step 2: send the credentials back along with that token
        Connection.Response result = Jsoup.connect("https://www.gradescope.com/login")
                .data("authenticity_token", token)
                .data("session[email]", email)
                .data("session[password]", password)
                .data("session[remember_me]", "0")
                .data("commit", "Log In")
                .cookies(loginPage.cookies())
                .method(Connection.Method.POST)
                .followRedirects(true)
                .execute();

        return result.cookies();
    }

    // Finds your courses: returns a map of course name -> course URL
    static Map<String, String> getCourses(Map<String, String> cookies) throws Exception {
        Document account = Jsoup.connect("https://www.gradescope.com/account")
                .cookies(cookies)
                .get();

        Map<String, String> courses = new java.util.LinkedHashMap<>();

        // Course tiles are links pointing at /courses/12345
        for (org.jsoup.nodes.Element link : account.select("a[href^=/courses/]")) {
            String url = "https://www.gradescope.com" + link.attr("href");
            String name = link.text().trim();
            if (!name.isBlank()) {
                courses.put(name, url);
            }
        }
        return courses;
    }

    // Scrapes one course page for its assignments
    static void addAssignments(String courseUrl, Map<String, String> cookies, List<Event> events) throws Exception {
        Document page = Jsoup.connect(courseUrl).cookies(cookies).get();

        // Assignments live as rows in a table
        for (org.jsoup.nodes.Element row : page.select("table#assignments-student-table tbody tr")) {
            // The name is in the first cell, sometimes wrapped in a link
            org.jsoup.nodes.Element nameCell = row.selectFirst("th");
            if (nameCell == null)
                continue;
            String name = nameCell.text().trim();

            // The due date is in a element with a datetime attribute
            org.jsoup.nodes.Element dueCell = row.selectFirst(".submissionTimeChart--dueDate");
            String due = (dueCell == null) ? "" : dueCell.attr("datetime");

            if (due.isBlank()) {
                continue;
            }

            String date = due.substring(0, 10);
            if (!Main.inWindow(date)) {
                continue;
            }

            Event e = new Event();
            e.name = name;
            e.url = courseUrl;
            e.date = due.substring(0, 10); // "2026-01-30"
            e.time = due.substring(11, 16); // "23:00"
            e.uid = "gradescope|" + name + "|" + e.date;
            e.userAdded = false;
            events.add(e);
        }
    }

    // Logs in and adds every dated assignment to the list
    static void addGradescopeEvents(List<Event> events) throws Exception {
        if (Settings.get("gradescope_email", "").isBlank()) {
            return; // not configured, skip
        }
        Map<String, String> cookies = login();
        Map<String, String> courses = getCourses(cookies);
        for (String url : courses.values()) {
            addAssignments(url, cookies, events);
        }
    }

    public static void main(String[] args) throws Exception {
        Map<String, String> cookies = login();
        Map<String, String> courses = getCourses(cookies);

        List<Event> events = new ArrayList<>();
        for (String url : courses.values()) {
            addAssignments(url, cookies, events);
        }

        System.out.println("Collected " + events.size() + " assignments:");
        for (Event e : events) {
            System.out.println("  " + e.date + " " + e.time + "  " + e.name);
        }
    }
}