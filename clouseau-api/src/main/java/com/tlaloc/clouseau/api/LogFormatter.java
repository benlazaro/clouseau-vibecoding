package com.tlaloc.clouseau.api;

import org.pf4j.ExtensionPoint;

/**
 * Implement this to add a new log formatter (e.g. hex→ASCII, collapsed JSON pretty-print).
 * Annotate your implementation with @Extension and drop the JAR in the plugins folder.
 */
public interface LogFormatter extends ExtensionPoint {

    /** Human-readable name shown in the UI (e.g. "JSON Pretty-Print"). */
    String getName();

    /** Returns true if this formatter can process the given input. */
    boolean canFormat(String input);

    /** Transform the input and return the formatted result. */
    String format(String input);
}
