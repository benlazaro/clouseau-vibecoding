package com.droidenx.clouseau.core;

import com.droidenx.clouseau.api.LogEntry;
import com.droidenx.clouseau.api.LogEntry.LogLevel;
import com.droidenx.clouseau.api.LogParser;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A user-defined log parser backed by a regex with named capture groups.
 *
 * <p>The following group names map to {@link LogEntry} fields:
 * {@code timestamp}, {@code level}, {@code logger}, {@code thread}, {@code message}.
 * Any other named group is stored in {@link LogEntry#fields()}.
 *
 * <p>All groups are optional — omit any you don't need.
 *
 * <p>Timestamp formats are tried in order; commas are normalised to dots before
 * parsing so that {@code yyyy-MM-dd HH:mm:ss.SSS} matches both {@code .123}
 * and {@code ,123} variants. Use {@code "ISO_OFFSET_DATE_TIME"} to match the
 * built-in {@link DateTimeFormatter#ISO_OFFSET_DATE_TIME} (handles variable
 * fractional seconds and timezone offsets).
 */
public final class RegexLogParser implements LogParser {

    private static final Pattern GROUP_NAME_RE = Pattern.compile("\\(\\?<(\\w+)>");

    private static final Set<String> STANDARD_FIELDS = Set.of(
            "timestamp", "level", "logger", "thread", "message");

    private final String            name;
    private final Pattern           pattern;
    private final DateTimeFormatter timestampFormatter;
    private final Set<String>       namedGroups;

    public RegexLogParser(String name, Pattern pattern, String timestampFormat) {
        this.name    = name;
        this.pattern = pattern;
        this.namedGroups = extractNamedGroups(pattern);
        this.timestampFormatter = buildFormatter(timestampFormat);
    }

    @Override
    public String getName() { return name; }

    @Override
    public List<String> customFields() {
        List<String> result = new ArrayList<>();
        for (String g : namedGroups) {
            if (!STANDARD_FIELDS.contains(g)) result.add(g);
        }
        return List.copyOf(result);
    }

    @Override
    public boolean canParse(String rawLine) {
        return rawLine != null && pattern.matcher(rawLine).matches();
    }

    @Override
    public LogEntry parse(String rawLine) {
        Matcher m = pattern.matcher(rawLine);
        if (!m.matches()) {
            return new LogEntry(null, LogLevel.UNKNOWN, "", "", rawLine, rawLine, Map.of());
        }

        Instant  timestamp = parseTimestamp(group(m, "timestamp"));
        LogLevel level     = parseLevel(group(m, "level"));
        String   logger    = trim(group(m, "logger"));
        String   thread    = trim(group(m, "thread"));
        String   message   = trim(group(m, "message"));

        Map<String, String> fields = new HashMap<>();
        for (String groupName : namedGroups) {
            if (!STANDARD_FIELDS.contains(groupName)) {
                String value = group(m, groupName);
                if (value != null) fields.put(groupName, value);
            }
        }

        return new LogEntry(timestamp, level,
                logger  != null ? logger  : "",
                thread  != null ? thread  : "",
                message != null ? message : rawLine,
                rawLine, Map.copyOf(fields));
    }

    private static String trim(String s) {
        return s != null ? s.strip() : null;
    }

    private String group(Matcher m, String name) {
        if (!namedGroups.contains(name)) return null;
        try {
            return m.group(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private Instant parseTimestamp(String raw) {
        if (raw == null || timestampFormatter == null) return null;
        try {
            return timestampFormatter.parse(raw.replace(',', '.'), Instant::from);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private static LogLevel parseLevel(String raw) {
        if (raw == null) return LogLevel.UNKNOWN;
        try {
            return LogLevel.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return LogLevel.UNKNOWN;
        }
    }

    private static DateTimeFormatter buildFormatter(String format) {
        if ("ISO_OFFSET_DATE_TIME".equals(format)) return DateTimeFormatter.ISO_OFFSET_DATE_TIME;
        // Normalize comma decimal separator to dot — Logback uses comma (HH:mm:ss,SSS)
        // but parseTimestamp normalizes the raw value the same way, so both must agree.
        return DateTimeFormatter.ofPattern(format.replace(',', '.')).withZone(ZoneId.systemDefault());
    }

    private static Set<String> extractNamedGroups(Pattern p) {
        Set<String> names = new LinkedHashSet<>();
        Matcher m = GROUP_NAME_RE.matcher(p.pattern());
        while (m.find()) names.add(m.group(1));
        return Set.copyOf(names);
    }
}
