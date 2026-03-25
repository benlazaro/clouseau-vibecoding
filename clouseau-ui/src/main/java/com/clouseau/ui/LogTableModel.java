package com.clouseau.ui;

import com.clouseau.api.LogEntry;
import com.clouseau.core.ClouseauEventBus;
import com.clouseau.core.LogIndex;
import com.google.common.eventbus.Subscribe;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

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

    private final List<LogEntry> rows = new ArrayList<>();

    public LogTableModel() {
        ClouseauEventBus.registerAsync(this);
    }

    @Subscribe
    public void onEntryAdded(LogIndex.LogEntryAddedEvent event) {
        LogEntry entry = event.entry();
        SwingUtilities.invokeLater(() -> {
            int idx = rows.size();
            rows.add(entry);
            fireTableRowsInserted(idx, idx);
        });
    }

    @Subscribe
    public void onBatchAdded(LogIndex.LogBatchAddedEvent event) {
        List<LogEntry> batch = List.copyOf(event.entries());
        SwingUtilities.invokeLater(() -> {
            int first = rows.size();
            rows.addAll(batch);
            fireTableRowsInserted(first, rows.size() - 1);
        });
    }

    public void clear() {
        Runnable doClear = () -> {
            int last = rows.size() - 1;
            if (last < 0) return;
            rows.clear();
            fireTableRowsDeleted(0, last);
        };
        if (SwingUtilities.isEventDispatchThread()) doClear.run();
        else SwingUtilities.invokeLater(doClear);
    }

    /** Replaces all rows with {@code newRows} in one shot. Must be called on the EDT. */
    public void load(List<LogEntry> newRows) {
        rows.clear();
        rows.addAll(newRows);
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
