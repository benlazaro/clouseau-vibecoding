package com.tlaloc.clouseau.ui;

import com.tlaloc.clouseau.api.LogEntry;
import com.tlaloc.clouseau.api.LogParser;
import com.tlaloc.clouseau.core.LogIndex;
import lombok.extern.slf4j.Slf4j;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.TableModelEvent;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.*;
import java.io.BufferedReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Self-contained panel representing one open log file tab.
 * Owns its own LogIndex, LogTableModel, table and detail pane.
 */
@Slf4j
public final class LogPanel extends JPanel {

    private final List<LogParser> parsers;
    private final LogIndex        logIndex      = new LogIndex();
    private final LogTableModel   logTableModel = new LogTableModel();
    private final FilterBar       filterBar;
    private JTable logTable;
    private JTextPane detailArea;
    private boolean tableColumnsManaged = false;
    private int fittedColumnsWidth = 0;
    private volatile SwingWorker<?, ?> currentWorker;
    private boolean follow = false;

    public LogPanel(List<LogParser> parsers) {
        super(new MigLayout("fill, insets 0", "[grow]", "[][grow]"));
        this.parsers   = parsers;
        FilterBar[] fbHolder = new FilterBar[1];
        fbHolder[0] = new FilterBar(() -> logTableModel.applyFilter(fbHolder[0].buildPredicate()));
        this.filterBar = fbHolder[0];

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, buildLogTable(), buildDetail());
        split.setResizeWeight(0.70);
        split.setOneTouchExpandable(true);
        split.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override public void componentResized(java.awt.event.ComponentEvent e) {
                split.removeComponentListener(this);
                split.setDividerLocation(0.70);
            }
        });
        add(filterBar, "growx, wrap");
        add(split, "grow");

        logTableModel.addTableModelListener(e -> {
            if (follow && e.getType() == TableModelEvent.INSERT) scrollToBottom();
        });

        logTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int row = logTable.getSelectedRow();
                showDetail(row >= 0 ? logTableModel.getEntry(row) : null);
            }
        });
    }

    public boolean isFollow() { return follow; }

    public void setFollow(boolean follow) {
        this.follow = follow;
        if (follow) scrollToBottom();
    }

    private void scrollToBottom() {
        int last = logTable.getRowCount() - 1;
        if (last >= 0) logTable.scrollRectToVisible(logTable.getCellRect(last, 0, true));
    }

    // ── File loading ─────────────────────────────────────────────────────────

    public void load(Path file, Optional<LogParser> parser) {
        SwingWorker<?, ?> old = currentWorker;
        if (old != null) old.cancel(true);
        logIndex.clear();
        logTableModel.clear();

        SwingWorker<ArrayList<LogEntry>, Void> worker = new SwingWorker<>() {
            @Override
            protected ArrayList<LogEntry> doInBackground() throws Exception {
                ArrayList<LogEntry> result = new ArrayList<>();
                try (BufferedReader reader = Files.newBufferedReader(file)) {
                    String line;
                    while ((line = reader.readLine()) != null && !isCancelled()) {
                        if (line.isBlank()) continue;
                        final String candidate = line;
                        parser.map(Stream::of)
                              .orElseGet(parsers::stream)
                              .filter(p -> p.canParse(candidate))
                              .findFirst()
                              .map(p -> p.parse(candidate))
                              .ifPresent(result::add);
                    }
                }
                return result;
            }

            @Override
            protected void done() {
                if (isCancelled()) return;
                try {
                    ArrayList<LogEntry> entries = get();
                    logIndex.load(entries);
                    filterBar.updateLoggers(entries);
                    logTableModel.load(entries);
                    autoResizeColumns();
                } catch (Exception ex) {
                    log.error("Failed to load {}", file, ex);
                    JOptionPane.showMessageDialog(LogPanel.this,
                            ex.getMessage(),
                            Messages.get("filechooser.error.title"),
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        currentWorker = worker;
        worker.execute();
    }

    public void cancelLoad() {
        SwingWorker<?, ?> w = currentWorker;
        if (w != null) w.cancel(true);
    }

    public void dispose() {
        cancelLoad();
        logTableModel.dispose();
    }

    public JTable getLogTable() { return logTable; }

    public void applyDetailFontSize(int size) {
        int row = logTable.getSelectedRow();
        showDetail(row >= 0 ? logTableModel.getEntry(row) : null);
    }

    // ── Log table ────────────────────────────────────────────────────────────

    private JScrollPane buildLogTable() {
        logTable = new JTable(logTableModel) {
            @Override public boolean isCellEditable(int r, int c) { return false; }

            @Override
            public void doLayout() {
                if (tableColumnsManaged && getTableHeader().getResizingColumn() == null) return;
                super.doLayout();
            }
        };
        logTable.setBackground(new Color(0x191919));
        logTable.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
        logTable.getColumnModel().getColumn(0).setPreferredWidth(50);
        logTable.getColumnModel().getColumn(1).setPreferredWidth(180);
        logTable.getColumnModel().getColumn(2).setPreferredWidth(60);
        logTable.getColumnModel().getColumn(3).setPreferredWidth(120);
        logTable.getColumnModel().getColumn(4).setPreferredWidth(200);

        Border cellPad = BorderFactory.createEmptyBorder(0, 8, 0, 8);
        DefaultTableCellRenderer levelRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(
                    JTable t, Object v, boolean sel, boolean foc, int r, int c) {
                super.getTableCellRendererComponent(t, v, sel, foc, r, c);
                setBorder(cellPad);
                if (!sel) {
                    LogEntry entry = logTableModel.getEntry(r);
                    LogEntry.LogLevel level = entry != null ? entry.level() : null;
                    Color levelColor = level != null
                            ? LEVEL_COLORS.getOrDefault(level, FG_DEFAULT)
                            : FG_DEFAULT;
                    setForeground(switch (c) {
                        case 2, 5 -> levelColor;   // Level, Message
                        case 3, 4 -> FG_DIM;       // Thread, Logger
                        default   -> FG_DEFAULT;   // #, Timestamp
                    });
                }
                return this;
            }
        };
        levelRenderer.setHorizontalAlignment(SwingConstants.LEFT);
        for (int i = 0; i < logTable.getColumnCount(); i++) {
            logTable.getColumnModel().getColumn(i).setCellRenderer(levelRenderer);
        }

        JTableHeader header = logTable.getTableHeader();
        TableCellRenderer origHeaderRenderer = header.getDefaultRenderer();
        header.setDefaultRenderer((table, value, isSelected, hasFocus, row, column) -> {
            Component c = origHeaderRenderer.getTableCellRendererComponent(
                    table, value, isSelected, hasFocus, row, column);
            if (c instanceof JLabel label) {
                Border existing = label.getBorder();
                label.setBorder(existing != null
                        ? BorderFactory.createCompoundBorder(existing, cellPad)
                        : cellPad);
            }
            return c;
        });

        Font headerFont = header.getFont();
        for (int i = 0; i < logTable.getColumnCount(); i++) {
            JLabel measure = new JLabel(logTable.getColumnName(i));
            measure.setFont(headerFont);
            int minW = measure.getPreferredSize().width + 40;
            logTable.getColumnModel().getColumn(i).setMinWidth(minW);
        }
        logTable.setShowHorizontalLines(true);
        logTable.setShowVerticalLines(false);
        logTable.setGridColor(new Color(0x272727));
        logTable.setRowHeight(22);
        logTable.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override public void componentResized(java.awt.event.ComponentEvent e) {
                if (tableColumnsManaged) stretchMessageColumn();
            }
        });

        JScrollPane scrollPane = new JScrollPane(logTable);
        scrollPane.getViewport().setBackground(new Color(0x181818));
        return scrollPane;
    }

    private void autoResizeColumns() {
        int lastCol = logTable.getColumnCount() - 1;
        int totalFitted = 0;

        for (int col = 0; col < lastCol; col++) {
            int maxWidth = 0;
            TableCellRenderer hr = logTable.getColumnModel().getColumn(col).getHeaderRenderer();
            if (hr == null) hr = logTable.getTableHeader().getDefaultRenderer();
            Component hc = hr.getTableCellRendererComponent(
                    logTable, logTable.getColumnName(col), false, false, -1, col);
            maxWidth = Math.max(maxWidth, hc.getPreferredSize().width);
            for (int row = 0; row < logTable.getRowCount(); row++) {
                Component rc = logTable.prepareRenderer(logTable.getCellRenderer(row, col), row, col);
                maxWidth = Math.max(maxWidth, rc.getPreferredSize().width);
            }
            maxWidth += 8;
            logTable.getColumnModel().getColumn(col).setPreferredWidth(maxWidth);
            logTable.getColumnModel().getColumn(col).setWidth(maxWidth);
            totalFitted += maxWidth;
        }

        fittedColumnsWidth = totalFitted;
        tableColumnsManaged = true;
        stretchMessageColumn();
        logTable.repaint();
        logTable.getTableHeader().repaint();
    }

    private void stretchMessageColumn() {
        int lastCol = logTable.getColumnCount() - 1;
        int margins = logTable.getColumnModel().getColumnMargin() * logTable.getColumnCount();
        int msgWidth = Math.max(logTable.getWidth() - fittedColumnsWidth - margins, 150);
        logTable.getColumnModel().getColumn(lastCol).setPreferredWidth(msgWidth);
        logTable.getColumnModel().getColumn(lastCol).setWidth(msgWidth);
    }

    // ── Level color palette ───────────────────────────────────────────────────

    private static final Map<LogEntry.LogLevel, Color> LEVEL_COLORS;
    static {
        Map<LogEntry.LogLevel, Color> m = new EnumMap<>(LogEntry.LogLevel.class);
        m.put(LogEntry.LogLevel.TRACE,   new Color(0x9E9E9E));
        m.put(LogEntry.LogLevel.DEBUG,   new Color(0x29B6F6));
        m.put(LogEntry.LogLevel.INFO,    new Color(0x66BB6A));
        m.put(LogEntry.LogLevel.WARN,    new Color(0xFFA726));
        m.put(LogEntry.LogLevel.ERROR,   new Color(0xEF5350));
        m.put(LogEntry.LogLevel.FATAL,   new Color(0xF44336));
        m.put(LogEntry.LogLevel.UNKNOWN, new Color(0x9E9E9E));
        LEVEL_COLORS = Collections.unmodifiableMap(m);
    }
    private static final Color FG_DEFAULT = new Color(0xB0B7C3);
    private static final Color FG_DIM     = new Color(0x6B7280);

    // ── Detail panel ─────────────────────────────────────────────────────────

    private JScrollPane buildDetail() {
        detailArea = new JTextPane();
        detailArea.setEditable(false);
        detailArea.setBackground(new Color(0x191919));
        detailArea.setForeground(FG_DEFAULT);
        detailArea.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        showDetail(null);
        JScrollPane scroll = new JScrollPane(detailArea);
//        scroll.getViewport().setBackground(new Color(0x191919));
        return scroll;
    }

    private static final DateTimeFormatter DETAIL_TS_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneId.systemDefault());

    private void showDetail(LogEntry entry) {
        StyledDocument doc = detailArea.getStyledDocument();
        try { doc.remove(0, doc.getLength()); } catch (BadLocationException ignored) {}

        int fontSize = AppPrefs.getDetailFontSize();

        SimpleAttributeSet key = new SimpleAttributeSet();
        StyleConstants.setFontFamily(key, Font.MONOSPACED);
        StyleConstants.setFontSize(key, fontSize);
        StyleConstants.setBold(key, true);
        StyleConstants.setForeground(key, FG_DIM);

        SimpleAttributeSet val = new SimpleAttributeSet();
        StyleConstants.setFontFamily(val, Font.MONOSPACED);
        StyleConstants.setFontSize(val, fontSize);
        StyleConstants.setForeground(val, FG_DEFAULT);

        if (entry == null) {
            insertText(doc, Messages.get("detail.placeholder"), val);
            detailArea.setCaretPosition(0);
            return;
        }

        Color levelColor = entry.level() != null
                ? LEVEL_COLORS.getOrDefault(entry.level(), FG_DEFAULT)
                : FG_DEFAULT;
        SimpleAttributeSet msgVal = new SimpleAttributeSet(val);
        StyleConstants.setForeground(msgVal, levelColor);

        String ts     = entry.timestamp() != null ? DETAIL_TS_FMT.format(entry.timestamp()) : "";
        String level  = entry.level()     != null ? entry.level().name()  : "";
        String thread = entry.thread()    != null ? entry.thread()        : "";
        String logger = entry.logger()    != null ? entry.logger()        : "";

        appendField(doc, key, val,    Messages.get("table.col.timestamp"), ts);
        appendField(doc, key, msgVal, Messages.get("table.col.level"),     level);
        appendField(doc, key, val,    Messages.get("table.col.thread"),    thread);
        appendField(doc, key, val,    Messages.get("table.col.logger"),    logger);

        if (entry.fields() != null) {
            entry.fields().forEach((k, v) -> appendField(doc, key, val, k, v));
        }

        appendField(doc, key, msgVal, Messages.get("table.col.message"),
                entry.message() != null ? entry.message() : "");

//        if (entry.rawLine() != null) {
//            insertText(doc, "\n" + "─".repeat(60) + "\n", val);
//            insertText(doc, Messages.get("detail.raw.label") + "\n", key);
//            insertText(doc, entry.rawLine(), msgVal);
//        }

        detailArea.setCaretPosition(0);
    }

    private static void appendField(StyledDocument doc, SimpleAttributeSet keyStyle,
                                    SimpleAttributeSet valStyle, String label, String value) {
        insertText(doc, String.format("%-12s ", label + ":"), keyStyle);
        insertText(doc, value + "\n", valStyle);
    }

    private static void insertText(StyledDocument doc, String text, SimpleAttributeSet style) {
        try { doc.insertString(doc.getLength(), text, style); } catch (BadLocationException ignored) {}
    }
}
