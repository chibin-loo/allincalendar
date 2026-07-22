package com.artlu;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

public class Gradescope {

    // Logs in and returns the session cookies needed for later requests
    static Map<String, String> login() throws Exception {
        List<String> lines = Files.readAllLines(Paths.get("gradescope.txt"));
        if (lines.size() < 2) {
            throw new Exception("gradescope.txt needs two lines: email on the first, password on the second.");
        }
        String email = lines.get(0).trim();
        String password = lines.get(1).trim();

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

    public static void main(String[] args) throws Exception {
        Map<String, String> cookies = login();
        Map<String, String> courses = getCourses(cookies);

        System.out.println("Found " + courses.size() + " courses:");
        for (Map.Entry<String, String> entry : courses.entrySet()) {
            System.out.println("  " + entry.getKey() + "  ->  " + entry.getValue());
        }
    }
}