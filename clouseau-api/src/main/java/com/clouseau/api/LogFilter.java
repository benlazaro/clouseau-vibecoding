package com.clouseau.api;

/**
 * Plugins implement this to add custom filter types to the filter bar.
 */
public interface LogFilter {

    String getName();

    /** Returns true if the entry should be included in the current view. */
    boolean accepts(LogEntry entry);
}
