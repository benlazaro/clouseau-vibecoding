package com.tlaloc.clouseau.ui;

import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.tlaloc.clouseau.api.LogSyntaxHighlighter;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Built-in syntax highlighter for JSON messages (plain or prefixed).
 * Produces VS Code-inspired token colors for keys, string values,
 * numbers, booleans, nulls, and punctuation.
 */
final class JsonSyntaxHighlighter implements LogSyntaxHighlighter {

    // Monokai-inspired palette
    private static final int KEY        = 0xA6E22E; // vivid green
    private static final int STRING_VAL = 0xE6DB74; // vivid yellow
    private static final int NUMBER     = 0xAE81FF; // vivid purple
    private static final int BOOL_NULL  = 0x66D9E8; // vivid cyan
    private static final int PUNCT      = 0x75715E; // warm gray

    @Override
    public String getName() { return "JSON"; }

    @Override
    public boolean canHighlight(String input) {
        if (input == null) return false;
        return findJsonStart(input) >= 0;
    }

    @Override
    public List<ColorSpan> highlight(String input) {
        int jsonStart = findJsonStart(input);
        if (jsonStart < 0) return List.of();
        return tokenize(input, jsonStart);
    }

    /**
     * Walks the string starting at {@code jsonStart}, emitting color spans.
     * Characters before {@code jsonStart} (the prefix) are left unspanned
     * so the caller renders them in the base style.
     */
    private static List<ColorSpan> tokenize(String text, int jsonStart) {
        List<ColorSpan> spans = new ArrayList<>();
        int i = jsonStart;
        int len = text.length();
        // true = inside object, false = inside array
        Deque<Boolean> stack = new ArrayDeque<>();
        boolean expectKey = false;

        while (i < len) {
            char c = text.charAt(i);
            switch (c) {
                case '{' -> {
                    spans.add(new ColorSpan(i, i + 1, PUNCT));
                    stack.push(true);
                    expectKey = true;
                    i++;
                }
                case '[' -> {
                    spans.add(new ColorSpan(i, i + 1, PUNCT));
                    stack.push(false);
                    expectKey = false;
                    i++;
                }
                case '}', ']' -> {
                    spans.add(new ColorSpan(i, i + 1, PUNCT));
                    if (!stack.isEmpty()) stack.pop();
                    i++;
                }
                case ',' -> {
                    spans.add(new ColorSpan(i, i + 1, PUNCT));
                    expectKey = !stack.isEmpty() && Boolean.TRUE.equals(stack.peek());
                    i++;
                }
                case ':' -> {
                    spans.add(new ColorSpan(i, i + 1, PUNCT));
                    expectKey = false;
                    i++;
                }
                case '"' -> {
                    int start = i++;
                    while (i < len) {
                        char sc = text.charAt(i);
                        if (sc == '\\') { i += 2; continue; }
                        if (sc == '"') { i++; break; }
                        i++;
                    }
                    spans.add(new ColorSpan(start, i, expectKey ? KEY : STRING_VAL));
                }
                case 't', 'f', 'n' -> {
                    int start = i;
                    while (i < len && Character.isLetter(text.charAt(i))) i++;
                    spans.add(new ColorSpan(start, i, BOOL_NULL));
                }
                default -> {
                    if (Character.isDigit(c) || c == '-') {
                        int start = i;
                        while (i < len) {
                            char nc = text.charAt(i);
                            if (Character.isDigit(nc) || nc == '.' || nc == 'e'
                                    || nc == 'E' || nc == '+' || nc == '-') i++;
                            else break;
                        }
                        spans.add(new ColorSpan(start, i, NUMBER));
                    } else {
                        i++; // whitespace, newlines
                    }
                }
            }
        }
        return spans;
    }

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
