package com.clouseau.api;

import java.time.Instant;
import java.util.Map;

/**
 * Immutable representation of a single parsed log line.
 */
public record LogEntry(
    Instant timestamp,
    LogLevel level,
    String logger,
    String thread,
    String message,
    String rawLine,
    Map<String, String> fields
) {
    public enum LogLevel { TRACE, DEBUG, INFO, WARN, ERROR, FATAL, UNKNOWN }
}
