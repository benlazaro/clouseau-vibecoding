package com.tlaloc.clouseau.api;

import org.pf4j.ExtensionPoint;

/**
 * Implement this to support a new log format.
 * Annotate your implementation with @Extension and drop the JAR in the plugins folder.
 */
public interface LogParser extends ExtensionPoint {

    /** Human-readable name shown in the UI (e.g. "Log4j Pattern"). */
    String getName();

    /** Returns true if this parser can handle the given raw line. */
    boolean canParse(String rawLine);

    /** Parse a raw line into a structured LogEntry. */
    LogEntry parse(String rawLine);
}
