package com.droidenx.clouseau.core;

import com.droidenx.clouseau.api.LogEntry;
import com.droidenx.clouseau.api.LogFilter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Thread-safe in-memory store for log entries.
 *
 * v1: simple list with linear scan — good for ~500k entries.
 * v2: swap internals for a Lucene index when search performance demands it.
 */
public final class LogIndex {

    private final CopyOnWriteArrayList<LogEntry> entries = new CopyOnWriteArrayList<>();

    public void add(LogEntry entry) {
        entries.add(entry);
        ClouseauEventBus.postAsync(new LogEntryAddedEvent(entry));
    }

    public void addAll(List<LogEntry> batch) {
        entries.addAll(batch);
        ClouseauEventBus.postAsync(new LogBatchAddedEvent(batch));
    }

    /** Return all entries matching every supplied filter (AND logic). */
    public List<LogEntry> query(List<LogFilter> filters) {
        if (filters.isEmpty()) return Collections.unmodifiableList(new ArrayList<>(entries));

        return entries.stream()
            .filter(e -> filters.stream().allMatch(f -> f.accepts(e)))
            .toList();
    }

    /** Replaces all entries without firing any events. Use for bulk file loads. */
    public void load(List<LogEntry> newEntries) {
        entries.clear();
        entries.addAll(newEntries);
    }

    public int size()  { return entries.size(); }
    public void clear() { entries.clear(); }

    // --- Events ---

    public record LogEntryAddedEvent(LogEntry entry) {}
    public record LogBatchAddedEvent(List<LogEntry> entries) {}
}
