package com.tlaloc.clouseau.ui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.tlaloc.clouseau.api.LogFormatter;

/**
 * Built-in formatter that detects JSON embedded in a log message and pretty-prints it.
 * Handles messages where JSON follows a text prefix (e.g. "Some text: [id] {...}").
 */
final class JsonMessageFormatter implements LogFormatter {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @Override
    public String getName() { return "JSON Pretty-Print"; }

    @Override
    public boolean canFormat(String input) {
        if (input == null) return false;
        return findJsonStart(input) >= 0;
    }

    @Override
    public String format(String input) {
        if (input == null) return input;
        int jsonStart = findJsonStart(input);
        if (jsonStart < 0) return input;
        String prefix = input.substring(0, jsonStart);
        String jsonPart = input.substring(jsonStart);
        JsonElement element = JsonParser.parseString(jsonPart);
        String pretty = GSON.toJson(element);
        return prefix.isEmpty() ? pretty : prefix + "\n" + pretty;
    }

    /**
     * Finds the first index where a valid JSON object or array begins.
     * Tries each '{' and '[' position in order and validates by parsing.
     */
    private static int findJsonStart(String input) {
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '{' || c == '[') {
                try {
                    JsonParser.parseString(input.substring(i));
                    return i;
                } catch (JsonParseException ignored) {}
            }
        }
        return -1;
    }
}
