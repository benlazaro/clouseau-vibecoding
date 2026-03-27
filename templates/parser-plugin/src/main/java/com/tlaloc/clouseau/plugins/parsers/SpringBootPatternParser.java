package com.tlaloc.clouseau.plugins.parsers;

import com.tlaloc.clouseau.api.LogEntry;
import com.tlaloc.clouseau.api.LogEntry.LogLevel;
import com.tlaloc.clouseau.api.LogParser;
import org.pf4j.Extension;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses Spring Boot 3 default Logback console output.
 *
 * Handles both pre-3.4 and 3.4+ formats (3.4 added an [appName] bracket):
 *
 *   Pre-3.4:  2024-01-15T10:30:45.123+01:00  INFO 12345 --- [           main] com.example.App : Message
 *   3.4+:     2025-06-10T23:43:06.269-05:00  INFO 31280 --- [demo] [  restartedMain] com.example.App : Message
 */
@Extension
public final class SpringBootPatternParser implements LogParser {

    // Group 1: timestamp  2: level  3: pid  4: appName (optional)  5: thread  6: logger  7: message
    private static final Pattern PATTERN = Pattern.compile(
        "^(\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d+(?:[+-]\\d{2}:\\d{2}|Z))" +
        "\\s+(TRACE|DEBUG|INFO|WARN|ERROR)" +
        "\\s+(\\d+)" +
        "\\s+---\\s+" +
        "(?:\\[([^\\]]*)\\]\\s+)?" +   // optional [appName]
        "\\[([^\\]]+)\\]" +            // mandatory [thread]
        "\\s+(\\S+)" +
        "\\s*:\\s+" +
        "(.*)$"
    );

    @Override
    public String getName() {
        return "Spring Boot";
    }

    @Override
    public boolean canParse(String rawLine) {
        return rawLine != null && PATTERN.matcher(rawLine).matches();
    }

    @Override
    public LogEntry parse(String rawLine) {
        Matcher m = PATTERN.matcher(rawLine);
        if (!m.matches()) {
            return rawEntry(rawLine);
        }

        Instant timestamp = parseTimestamp(m.group(1));
        LogLevel level    = parseLevel(m.group(2));
        String pid        = m.group(3);
        // group(4) = appName — may be null if the pre-3.4 format is used
        String appName    = m.group(4);
        String thread     = m.group(5).trim();
        String logger     = m.group(6).trim();
        String message    = m.group(7);

        Map<String, String> fields = appName != null
                ? Map.of("pid", pid, "app", appName)
                : Map.of("pid", pid);

        return new LogEntry(timestamp, level, logger, thread, message, rawLine, fields);
    }

    private static Instant parseTimestamp(String raw) {
        try {
            return DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse(raw, Instant::from);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static LogLevel parseLevel(String raw) {
        try {
            return LogLevel.valueOf(raw.trim());
        } catch (IllegalArgumentException e) {
            return LogLevel.UNKNOWN;
        }
    }

    private static LogEntry rawEntry(String rawLine) {
        return new LogEntry(null, LogLevel.UNKNOWN, "", "", rawLine, rawLine, Map.of());
    }
}
