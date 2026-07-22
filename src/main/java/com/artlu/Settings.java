package com.artlu;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Settings {
    static final String FILE = "settings.txt";

    // Reads settings.txt into a name -> value map
    static Map<String, String> load() throws Exception {
        Map<String, String> values = new LinkedHashMap<>();
        if (!Files.exists(Paths.get(FILE))) {
            return values; // no file yet
        }
        for (String line : Files.readAllLines(Paths.get(FILE))) {
            if (line.isBlank() || line.startsWith("#")) {
                continue; // skip blanks and comments
            }
            int equals = line.indexOf("=");
            if (equals < 0) {
                continue; // skip malformed lines
            }
            String key = line.substring(0, equals).trim();
            String value = line.substring(equals + 1).trim();
            values.put(key, value);
        }
        return values;
    }

    // Writes the map back out to settings.txt
    static void save(Map<String, String> values) throws Exception {
        List<String> lines = new java.util.ArrayList<>();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            lines.add(entry.getKey() + "=" + entry.getValue());
        }
        Files.write(Paths.get(FILE), lines);
    }

    // Convenience: get one setting, or a fallback if it's missing
    static String get(String key, String fallback) {
        try {
            String value = load().get(key);
            return (value == null || value.isBlank()) ? fallback : value;
        } catch (Exception ex) {
            return fallback;
        }
    }
}