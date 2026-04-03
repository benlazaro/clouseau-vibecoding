package com.tlaloc.clouseau.api;

import org.pf4j.ExtensionPoint;

import java.util.List;

/**
 * Implement to add syntax-aware highlighting for formatted log messages.
 * Works in tandem with {@link LogFormatter}: the formatter produces the text,
 * the highlighter supplies per-token color spans for the detail panel.
 * Annotate your implementation with {@code @Extension} and drop the JAR in the plugins folder.
 */
public interface LogSyntaxHighlighter extends ExtensionPoint {

    /** Human-readable name (e.g. "JSON Syntax Highlight"). */
    String getName();

    /** Returns true if this highlighter can handle the given (already formatted) input. */
    boolean canHighlight(String input);

    /**
     * Returns a list of color spans to apply to the input.
     * Spans must be non-overlapping and sorted by start position.
     * Gaps between spans are rendered in the caller's base style.
     */
    List<ColorSpan> highlight(String input);

    /** A half-open [start, end) character range with a packed RGB color. */
    record ColorSpan(int start, int end, int rgb) {}
}
