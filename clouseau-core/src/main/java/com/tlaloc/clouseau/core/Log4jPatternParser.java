package com.tlaloc.clouseau.core;

import com.tlaloc.clouseau.api.LogEntry;
import com.tlaloc.clouseau.api.LogEntry.LogLevel;
import com.tlaloc.clouseau.api.LogParser;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the most common Log4j / Log4j2 console pattern:
 *
 *   2024-01-15 10:30:45.123 [main] INFO  com.example.App - Hello world
 *   2024-01-15T10:30:45,123 [main] ERROR com.example.App - Boom
 *
 * Supports date separators ' ' and 'T', millisecond separators '.' and ',',
 * and log levels TRACE / DEBUG / INFO / WARN / ERROR / FATAL.
 */
public final class Log4jPatternParser implements LogParser {

    // Group 1: timestamp   Group 2: thread   Group 3: level   Group 4: logger   Group 5: message
    private static final Pattern PATTERN = Pattern.compile(
        "^(\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}:\\d{2}[.,]\\d+)" +
        "\\s+\\[([^\\]]*)\\]" +
        "\\s+(TRACE|DEBUG|INFO|WARN|ERROR|FATAL)" +
        "\\s+(\\S+)" +
        "\\s+-\\s+(.*)$"
    );

    private static final DateTimeFormatter[] FORMATTERS = {
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneId.systemDefault()),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss,SSS").withZone(ZoneId.systemDefault()),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS").withZone(ZoneId.systemDefault()),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss,SSS").withZone(ZoneId.systemDefault()),
    };

    @Override
    public String getName() {
        return "Log4j Pattern";
    }

    @Override
    public boolean canParse(String rawLine) {
        return rawLine != null && PATTERN.matcher(rawLine).matches();
    }

    @Override
    public LogEntry parse(String rawLine) {
        Matcher m = PATTERN.matcher(rawLine);
        if (!m.matches()) {
            return fallback(rawLine);
        }

        Instant timestamp = parseTimestamp(m.group(1));
        String thread     = m.group(2);
        LogLevel level    = parseLevel(m.group(3));
        String logger     = m.group(4);
        String message    = m.group(5);

        return new LogEntry(timestamp, level, logger, thread, message, rawLine, Map.of());
    }

    private static Instant parseTimestamp(String raw) {
        // Normalise comma → dot so all formatters work uniformly
        String normalised = raw.replace(',', '.');
        for (DateTimeFormatter fmt : FORMATTERS) {
            try {
                return fmt.parse(normalised, Instant::from);
            } catch (DateTimeParseException ignored) {
                // try next formatter
            }
        }
        return null;
    }

    private static LogLevel parseLevel(String raw) {
        try {
            return LogLevel.valueOf(raw.trim());
        } catch (IllegalArgumentException e) {
            return LogLevel.UNKNOWN;
        }
    }

    private static LogEntry fallback(String rawLine) {
        return new LogEntry(null, LogLevel.UNKNOWN, "", "", rawLine, rawLine, Map.of());
    }
}
