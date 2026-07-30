package com.artlu;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Asks a language model how long a task will take, reading the notes you wrote
 * for it. One request, one number back.
 *
 * Nothing else in the app depends on this working. With no key configured the
 * button stays disabled and the keyword rules do the guessing, exactly as
 * before — so the app is fully usable without an account, a network, or a cent.
 *
 * The URL is an OpenAI-shaped chat endpoint, which most providers speak, so
 * switching provider is a change to two settings rather than to this file.
 */
public class AiDuration {

    static final String DEFAULT_URL = "https://api.groq.com/openai/v1/chat/completions";
    static final String DEFAULT_MODEL = "llama-3.3-70b-versatile";

    /** True once a key has been pasted into Settings. */
    static boolean configured() {
        return !Settings.get("ai_api_key", "").isBlank();
    }

    /**
     * Minutes for this task, or 0 if the model didn't answer with a usable
     * number. Blocking — call it from a background thread, never from the UI
     * thread, or the window freezes until the network replies.
     */
    static int minutesFor(String taskName, String description) throws Exception {
        String key = Settings.get("ai_api_key", "");
        if (key.isBlank()) {
            return 0;
        }

        JsonObject system = new JsonObject();
        system.addProperty("role", "system");
        system.addProperty("content",
                "You estimate how long a piece of student work takes to do."
                        + " Reply with a whole number of minutes and nothing else —"
                        + " no words, no units, no punctuation."
                        + " Be realistic for an average undergraduate.");

        JsonObject user = new JsonObject();
        user.addProperty("role", "user");
        user.addProperty("content", "Task: " + taskName + "\nNotes: " + description);

        JsonArray messages = new JsonArray();
        messages.add(system);
        messages.add(user);

        JsonObject body = new JsonObject();
        body.addProperty("model", Settings.get("ai_model", DEFAULT_MODEL));
        body.add("messages", messages);
        body.addProperty("max_tokens", 8); // a number is all we want back
        body.addProperty("temperature", 0); // same notes should give the same answer

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(Settings.get("ai_url", DEFAULT_URL)))
                .header("Authorization", "Bearer " + key)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            // 401 means a bad key, 429 means you've hit the free daily limit
            throw new Exception("The model service replied " + response.statusCode()
                    + ": " + response.body());
        }

        String reply = JsonParser.parseString(response.body())
                .getAsJsonObject()
                .getAsJsonArray("choices").get(0).getAsJsonObject()
                .getAsJsonObject("message")
                .get("content").getAsString();

        return firstNumber(reply);
    }

    // The model was told to answer with digits only, but models tack on stray
    // words, so take the first number instead of trusting the whole string.
    static int firstNumber(String text) {
        Matcher found = Pattern.compile("\\d+").matcher(text);
        if (!found.find()) {
            return 0;
        }
        try {
            int minutes = Integer.parseInt(found.group());
            return Math.max(0, Math.min(1440, minutes)); // a day is the ceiling
        } catch (Exception ex) {
            return 0; // a number too big to parse — treat it as no answer
        }
    }
}
