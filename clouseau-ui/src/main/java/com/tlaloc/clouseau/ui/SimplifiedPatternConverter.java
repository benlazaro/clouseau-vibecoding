package com.tlaloc.clouseau.ui;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts a simplified, human-readable pattern into a Java regex with named
 * capture groups suitable for {@link com.tlaloc.clouseau.core.RegexLogParser}.
 *
 * <p>Supported field keywords (case-sensitive, must be uppercase):
 * <pre>
 *   TIMESTAMP{format}  — timestamp, with an optional DateTimeFormatter pattern in braces
 *   LEVEL              — log level (TRACE / DEBUG / INFO / WARN / ERROR / FATAL)
 *   LOGGER             — logger name (no whitespace)
 *   THREAD             — thread name (no whitespace)
 *   MESSAGE            — log message (rest of line)
 *   PID                — process ID (digits only)
 * </pre>
 *
 * <p>Everything between keywords is treated as a literal and escaped for use in a regex.
 *
 * <p>Example:
 * <pre>
 *   TIMESTAMP{yyyy-MM-dd HH:mm:ss} [THREAD] LEVEL LOGGER - MESSAGE
 *   →  ^(?&lt;timestamp&gt;\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}) \[(?&lt;thread&gt;\S+)\] ...
 * </pre>
 */
public final class SimplifiedPatternConverter {

    private static final Pattern KEYWORD = Pattern.compile(
            "(?<![A-Za-z])(TIMESTAMP(?:\\{([^}]+)\\})?|LEVEL|LOGGER|THREAD|MESSAGE|PID)(?![A-Za-z])" +
            "|\\{([a-zA-Z][a-zA-Z0-9_]*)}");
    // group 1 = built-in keyword, group 2 = TIMESTAMP format, group 3 = custom {field_name}

    public record Result(String regex, String timestampFormat) {}

    public static Result convert(String pattern) {
        if (pattern == null || pattern.isBlank())
            throw new IllegalArgumentException("Pattern must not be empty.");

        StringBuilder sb = new StringBuilder("^");
        String timestampFormat = null;
        Matcher m = KEYWORD.matcher(pattern);
        int last = 0;

        while (m.find()) {
            if (m.start() > last) appendLiteral(sb, pattern.substring(last, m.start()));
            String keyword     = m.group(1);
            String fmt         = m.group(2); // only populated for TIMESTAMP{fmt}
            String customField = m.group(3); // only populated for {field_name}

            if (customField != null) {
                sb.append("(?<").append(customField).append(">\\S+)");
            } else if (keyword.startsWith("TIMESTAMP")) {
                if (fmt != null) {
                    timestampFormat = fmt;
                    sb.append("(?<timestamp>").append(LogbackPatternConverter.dateFormatToRegex(fmt)).append(")");
                } else {
                    sb.append("(?<timestamp>.+?)");
                }
            } else switch (keyword) { // keyword != null here
                case "LEVEL"   -> sb.append("(?<level>TRACE|DEBUG|INFO|WARN|ERROR|FATAL)");
                case "LOGGER"  -> sb.append("(?<logger>\\S+)");
                case "THREAD"  -> sb.append("(?<thread>\\S+)");
                case "MESSAGE" -> sb.append("(?<message>.*)");
                case "PID"     -> sb.append("(?<pid>\\d+)");
            }
            last = m.end();
        }

        if (last < pattern.length()) appendLiteral(sb, pattern.substring(last));
        sb.append("$");

        try {
            Pattern.compile(sb.toString());
        } catch (Exception e) {
            throw new IllegalArgumentException("Generated regex is invalid: " + e.getMessage(), e);
        }

        return new Result(sb.toString(), timestampFormat);
    }

    private static void appendLiteral(StringBuilder sb, String text) {
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == ' ' || c == '\t') {
                // Collapse any run of whitespace into \s+ to tolerate level-name padding
                sb.append("\\s+");
                while (i < text.length() && (text.charAt(i) == ' ' || text.charAt(i) == '\t')) i++;
            } else {
                if (".[]{}()*+?^$|\\".indexOf(c) >= 0) sb.append('\\');
                sb.append(c);
                i++;
            }
        }
    }
}
