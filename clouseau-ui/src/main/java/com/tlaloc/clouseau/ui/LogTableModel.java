package com.tlaloc.clouseau.ui;

import com.tlaloc.clouseau.api.LogEntry;
import com.tlaloc.clouseau.core.ClouseauEventBus;
import com.tlaloc.clouseau.core.LogIndex;
import com.google.common.eventbus.Subscribe;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Table model backed by LogIndex events.
 * Subscribes to the async event bus; all mutations are dispatched to the EDT.
 */
public final class LogTableModel extends AbstractTableModel {

    private static final int COLUMN_COUNT = 6;
    private static final String[] COLUMN_KEYS = {
        "table.col.index", "table.col.timestamp", "table.col.level",
        "table.col.thread", "table.col.logger",   "table.col.message"
    };
    private static final DateTimeFormatter TS_FMT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneId.systemDefault());

    private final List<LogEntry> allEntries = new ArrayList<>();
    private final List<LogEntry> rows       = new ArrayList<>();
    private Predicate<LogEntry>  activeFilter = e -> true;

    public LogTableModel() {
        ClouseauEventBus.registerAsync(this);
    }

    public void dispose() {
        ClouseauEventBus.unregisterAsync(this);
    }

    @Subscribe
    public void onEntryAdded(LogIndex.LogEntryAddedEvent event) {
        LogEntry entry = event.entry();
        SwingUtilities.invokeLater(() -> {
            allEntries.add(entry);
            if (activeFilter.test(entry)) {
                int idx = rows.size();
                rows.add(entry);
                fireTableRowsInserted(idx, idx);
            }
        });
    }

    @Subscribe
    public void onBatchAdded(LogIndex.LogBatchAddedEvent event) {
        List<LogEntry> batch = List.copyOf(event.entries());
        SwingUtilities.invokeLater(() -> {
            allEntries.addAll(batch);
            int first = rows.size();
            List<LogEntry> matching = batch.stream().filter(activeFilter).toList();
            if (!matching.isEmpty()) {
                rows.addAll(matching);
                fireTableRowsInserted(first, rows.size() - 1);
            }
        });
    }

    public void clear() {
        Runnable doClear = () -> {
            allEntries.clear();
            int last = rows.size() - 1;
            if (last < 0) return;
            rows.clear();
            fireTableRowsDeleted(0, last);
        };
        if (SwingUtilities.isEventDispatchThread()) doClear.run();
        else SwingUtilities.invokeLater(doClear);
    }

    /** Replaces all entries and reapplies the active filter. Must be called on the EDT. */
    public void load(List<LogEntry> newEntries) {
        allEntries.clear();
        allEntries.addAll(newEntries);
        reapplyFilter();
    }

    /** Installs a new filter and recomputes the visible rows. Must be called on the EDT. */
    public void applyFilter(Predicate<LogEntry> filter) {
        activeFilter = filter;
        reapplyFilter();
    }

    private void reapplyFilter() {
        rows.clear();
        allEntries.stream().filter(activeFilter).forEach(rows::add);
        fireTableDataChanged();
    }

    /** Returns the LogEntry at the given view row, or null if out of range. */
    public LogEntry getEntry(int row) {
        if (row < 0 || row >= rows.size()) return null;
        return rows.get(row);
    }

    @Override public int getRowCount()    { return rows.size(); }
    @Override public int getColumnCount() { return COLUMN_COUNT; }
    @Override public String getColumnName(int col) { return Messages.get(COLUMN_KEYS[col]); }

    @Override
    public Class<?> getColumnClass(int col) {
        return col == 0 ? Integer.class : String.class;
    }

    @Override
    public Object getValueAt(int row, int col) {
        LogEntry e = rows.get(row);
        return switch (col) {
            case 0 -> row + 1;
            case 1 -> e.timestamp() != null ? TS_FMT.format(e.timestamp()) : "";
            case 2 -> e.level()     != null ? e.level().name()             : "";
            case 3 -> e.thread()    != null ? e.thread()                   : "";
            case 4 -> e.logger()    != null ? e.logger()                   : "";
            case 5 -> e.message()   != null ? e.message()                  : "";
            default -> "";
        };
    }
}
