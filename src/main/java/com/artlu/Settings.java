package com.artlu;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Settings {
    static final String FILE = "settings.txt";
    private static Map<String, String> cache = null;

    static Map<String, String> load() throws Exception {
        if (cache != null) {
            return cache;
        }
        Map<String, String> values = new LinkedHashMap<>();
        if (Files.exists(Paths.get(FILE))) {
            for (String line : Files.readAllLines(Paths.get(FILE))) {
                if (line.isBlank() || line.startsWith("#"))
                    continue;
                int equals = line.indexOf("=");
                if (equals < 0)
                    continue;
                values.put(line.substring(0, equals).trim(),
                        line.substring(equals + 1).trim());
            }
        }
        cache = values;
        return cache;
    }

    static void save(Map<String, String> values) throws Exception {
        List<String> lines = new ArrayList<>();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            lines.add(entry.getKey() + "=" + entry.getValue());
        }
        Files.write(Paths.get(FILE), lines);
        cache = null; // force a fresh read next time
    }

    static String get(String key, String fallback) {
        try {
            String value = load().get(key);
            return (value == null || value.isBlank()) ? fallback : value;
        } catch (Exception ex) {
            return fallback;
        }
    }

    // Numeric settings, with a safe fallback if the value isn't a number
    static int getInt(String key, int fallback) {
        try {
            return Integer.parseInt(get(key, String.valueOf(fallback)).trim());
        } catch (Exception ex) {
            return fallback;
        }
    }
}