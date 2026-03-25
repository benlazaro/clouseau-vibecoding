package com.clouseau.api;

/**
 * Implement this to support a new log format.
 * Annotate with @Extension and register via PF4J.
 */
public interface LogParser {

    /** Human-readable name shown in the UI (e.g. "Log4j Pattern"). */
    String getName();

    /** Returns true if this parser can handle the given raw line. */
    boolean canParse(String rawLine);

    /** Parse a raw line into a structured LogEntry. */
    LogEntry parse(String rawLine);
}
