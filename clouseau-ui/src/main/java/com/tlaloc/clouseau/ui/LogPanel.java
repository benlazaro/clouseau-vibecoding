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
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipInputStream;
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
    private volatile SwingWorker<Void, LogEntry> tailWorker;
    private Path currentFile;
    private Optional<LogParser> currentParser = Optional.empty();
    private volatile long tailPosition = 0;
    private boolean follow = AppPrefs.isFollowByDefault();
    private JPanel centerCards;
    private static final String CARD_CONTENT = "content";
    private static final String CARD_LOADING = "loading";
    private JPanel statusBar;
    private JLabel statusBarLabel;
    private JPanel highlightNavBar;
    private JPanel highlightNavSwatches;
    private JLabel highlightNavPosition;
    private Color  highlightNavColor = null;

    public LogPanel(List<LogParser> parsers) {
        super(new MigLayout("fill, insets 0, gapy 0", "[grow]", ""));
        this.parsers   = parsers;
        FilterBar[] fbHolder = new FilterBar[1];
        fbHolder[0] = new FilterBar(() -> logTableModel.applyFilter(fbHolder[0].buildPredicate()));
        this.filterBar = fbHolder[0];
        filterBar.initFollow(follow, this::setFollow);
        logTableModel.applyFilter(filterBar.buildPredicate());

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, buildLogTable(), buildDetail());
        split.setResizeWeight(0.70);
        split.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override public void componentResized(java.awt.event.ComponentEvent e) {
                split.removeComponentListener(this);
                split.setDividerLocation(0.70);
            }
        });

        centerCards = new JPanel(new CardLayout());
        centerCards.add(split, CARD_CONTENT);
        centerCards.add(buildLoadingPanel(), CARD_LOADING);

        statusBar = buildStatusBar();

        add(filterBar, "growx, wrap");
        add(buildHighlightNavBar(), "growx, wrap, hidemode 3");
        add(centerCards, "grow, pushy, wrap");
        add(statusBar, "growx");

        logTableModel.addTableModelListener(e -> {
            if (follow && e.getType() == TableModelEvent.INSERT)
                SwingUtilities.invokeLater(this::scrollToBottom);
            updateStatusBar();
        });

        logTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int viewRow = logTable.getSelectedRow();
                int modelRow = viewRow >= 0 ? logTable.convertRowIndexToModel(viewRow) : -1;
                showDetail(modelRow >= 0 ? logTableModel.getEntry(modelRow) : null, modelRow);
                updateHighlightNavPosition();
            }
        });
    }

    public boolean isFollow() { return follow; }

    public void setFollow(boolean follow) {
        this.follow = follow;
        filterBar.setFollowSelected(follow);
        if (follow) {
            scrollToBottom();
            startTailing();
        } else {
            stopTailing();
        }
    }

    private void scrollToBottom() {
        int last = logTable.getRowCount() - 1;
        if (last >= 0) logTable.scrollRectToVisible(logTable.getCellRect(last, 0, true));
    }

    // ── File loading ─────────────────────────────────────────────────────────

    public Path getFile() { return currentFile; }

    public void load(Path file, Optional<LogParser> parser) {
        load(file, parser, () -> {});
    }

    public void load(Path file, Optional<LogParser> parser, Runnable onError) {
        currentFile = file.toAbsolutePath();
        currentParser = parser;
        stopTailing();
        SwingWorker<?, ?> old = currentWorker;
        if (old != null) old.cancel(true);
        logIndex.clear();
        logTableModel.clear();
        logTableModel.setCustomFields(parser.map(com.tlaloc.clouseau.api.LogParser::customFields).orElse(List.of()));

        ((CardLayout) centerCards.getLayout()).show(centerCards, CARD_LOADING);

        SwingWorker<ArrayList<LogEntry>, Void> worker = new SwingWorker<>() {
            @Override
            protected ArrayList<LogEntry> doInBackground() throws Exception {
                ArrayList<LogEntry> result = new ArrayList<>();
                try (BufferedReader reader = openReader(file)) {
                    String line;
                    while ((line = reader.readLine()) != null && !isCancelled()) {
                        if (line.isBlank()) continue;
                        final String candidate = line;
                        LogEntry entry = parser.map(Stream::of)
                              .orElseGet(parsers::stream)
                              .filter(p -> p.canParse(candidate))
                              .findFirst()
                              .map(p -> p.parse(candidate))
                              .orElseGet(() -> new LogEntry(
                                      null, LogEntry.LogLevel.UNKNOWN,
                                      null, null,
                                      candidate, candidate, Map.of()));
                        result.add(entry);
                    }
                }
                return result;
            }

            @Override
            protected void done() {
                ((CardLayout) centerCards.getLayout()).show(centerCards, CARD_CONTENT);
                if (isCancelled()) return;
                try {
                    ArrayList<LogEntry> entries = get();
                    logIndex.load(entries);
                    filterBar.updateLoggers(entries);
                    logTableModel.load(entries);
                    autoResizeColumns();
                    if (follow) SwingUtilities.invokeLater(LogPanel.this::scrollToBottom);
                    try { tailPosition = Files.size(currentFile); } catch (IOException ignored) {}
                    if (follow) startTailing();
                } catch (Exception ex) {
                    log.error("Failed to load {}", file, ex);
                    onError.run();
                    JOptionPane.showMessageDialog(
                            SwingUtilities.getWindowAncestor(LogPanel.this),
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
        stopTailing();
        SwingWorker<?, ?> w = currentWorker;
        if (w != null) w.cancel(true);
    }

    private static boolean isCompressed(Path file) {
        String name = file.getFileName().toString().toLowerCase();
        return name.endsWith(".gz") || name.endsWith(".zip");
    }

    static BufferedReader openReader(Path file) throws IOException {
        String name = file.getFileName().toString().toLowerCase();
        if (name.endsWith(".gz")) {
            return new BufferedReader(new InputStreamReader(
                    new GZIPInputStream(Files.newInputStream(file)), StandardCharsets.UTF_8));
        }
        if (name.endsWith(".zip")) {
            ZipInputStream zis = new ZipInputStream(Files.newInputStream(file));
            zis.getNextEntry();
            return new BufferedReader(new InputStreamReader(zis, StandardCharsets.UTF_8));
        }
        return Files.newBufferedReader(file, StandardCharsets.UTF_8);
    }

    private void startTailing() {
        stopTailing();
        if (currentFile == null || isCompressed(currentFile)) return;
        final Optional<LogParser> parser = currentParser;
        tailWorker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                while (!isCancelled()) {
                    try {
                        if (Files.size(currentFile) > tailPosition) {
                            try (RandomAccessFile raf = new RandomAccessFile(currentFile.toFile(), "r")) {
                                raf.seek(tailPosition);
                                String line;
                                while ((line = raf.readLine()) != null && !isCancelled()) {
                                    if (!line.isBlank()) {
                                        final String candidate = line;
                                        LogEntry entry = parser.map(Stream::of)
                                                .orElseGet(parsers::stream)
                                                .filter(p -> p.canParse(candidate))
                                                .findFirst()
                                                .map(p -> p.parse(candidate))
                                                .orElseGet(() -> new LogEntry(
                                                        null, LogEntry.LogLevel.UNKNOWN,
                                                        null, null,
                                                        candidate, candidate, Map.of()));
                                        publish(entry);
                                    }
                                }
                                tailPosition = raf.getFilePointer();
                            }
                        }
                    } catch (IOException ignored) {}
                    Thread.sleep(500);
                }
                return null;
            }

            @Override
            protected void process(List<LogEntry> entries) {
                for (LogEntry entry : entries) logTableModel.append(entry);
            }
        };
        tailWorker.execute();
    }

    private void stopTailing() {
        SwingWorker<?, ?> w = tailWorker;
        if (w != null) { w.cancel(true); tailWorker = null; }
    }

    public void dispose() {
        cancelLoad();
        logTableModel.dispose();
    }

    public JTable getLogTable() { return logTable; }

    public void applyDetailFontSize(int size) {
        int viewRow = logTable.getSelectedRow();
        int modelRow = viewRow >= 0 ? logTable.convertRowIndexToModel(viewRow) : -1;
        showDetail(modelRow >= 0 ? logTableModel.getEntry(modelRow) : null, modelRow);
    }

    // ── Status bar ────────────────────────────────────────────────────────────

    private JPanel buildStatusBar() {
        statusBarLabel = new JLabel(" ");
        statusBarLabel.setForeground(UIManager.getColor("Button.foreground"));
        statusBarLabel.setFont(statusBarLabel.getFont().deriveFont(12f));
        statusBarLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        statusBarLabel.setBorder(BorderFactory.createEmptyBorder(6, 8, 8, 8));

        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(0x272727)));
        panel.add(statusBarLabel, BorderLayout.CENTER);
        return panel;
    }

    private void updateStatusBar() {
        int visible = logTableModel.getRowCount();
        int total   = logTableModel.getTotalCount();
        String text = (visible == total)
                ? java.text.MessageFormat.format(Messages.get("status.lines"), total)
                : java.text.MessageFormat.format(Messages.get("status.lines.filtered"), visible, total);
        statusBarLabel.setText(text);
    }

    // ── Loading overlay ───────────────────────────────────────────────────────

    private JPanel buildLoadingPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(new Color(0x181818));

        JProgressBar bar = new JProgressBar();
        bar.setIndeterminate(true);
        bar.setBorderPainted(false);
        bar.setForeground(new Color(0x29B6F6));
        bar.setBackground(new Color(0x272727));
        bar.setPreferredSize(new Dimension(280, 4));

        JLabel label = new JLabel(Messages.get("loading.message"), SwingConstants.CENTER);
        label.setForeground(new Color(0x6B7280));
        label.setFont(label.getFont().deriveFont(13f));

        JPanel inner = new JPanel(new MigLayout("insets 0", "[280!]", "[]10[]"));
        inner.setOpaque(false);
        inner.add(bar,   "wrap, h 4!");
        inner.add(label, "growx");

        panel.add(inner, BorderLayout.CENTER);
        return panel;
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
        logTable.setRowSorter(new javax.swing.table.TableRowSorter<>(logTableModel));
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
                LogEntry entry = logTableModel.getEntry(logTable.convertRowIndexToModel(r));
                Color hl = logTableModel.getHighlight(entry);
                boolean hlSel = hl != null && sel;

                // Background
                if (hl != null) {
                    setBackground(sel ? blend(hl, t.getSelectionBackground(), 0.55f) : hl);
                } else if (sel) {
                    setBackground(SEL_BG);
                } else {
                    setBackground(t.getBackground());
                }

                // Ring border on any selected row
                if (sel) {
                    Color ring = hl != null ? hl.brighter().brighter() : SEL_BG.brighter();
                    int lastCol = t.getColumnCount() - 1;
                    setBorder(BorderFactory.createCompoundBorder(
                            BorderFactory.createMatteBorder(2, c == 0 ? 2 : 0, 2, c == lastCol ? 2 : 0, ring),
                            cellPad));
                } else {
                    setBorder(cellPad);
                }

                // Foreground + font
                if (sel && hl == null) {
                    setForeground(Color.BLACK);
                } else if (!sel) {
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
                setFont(getFont().deriveFont(sel ? Font.BOLD : Font.PLAIN));
                return this;
            }
        };
        levelRenderer.setHorizontalAlignment(SwingConstants.LEFT);
        logTable.setDefaultRenderer(String.class,  levelRenderer);
        logTable.setDefaultRenderer(Integer.class, levelRenderer);

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
        logTable.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e)  { maybeShowHighlightMenu(e); }
            @Override public void mouseReleased(MouseEvent e) { maybeShowHighlightMenu(e); }
            private void maybeShowHighlightMenu(MouseEvent e) {
                if (!e.isPopupTrigger()) return;
                int viewRow = logTable.rowAtPoint(e.getPoint());
                if (viewRow < 0) return;
                if (!logTable.isRowSelected(viewRow)) logTable.setRowSelectionInterval(viewRow, viewRow);
                List<LogEntry> entries = new ArrayList<>();
                for (int vr : logTable.getSelectedRows()) {
                    LogEntry entry = logTableModel.getEntry(logTable.convertRowIndexToModel(vr));
                    if (entry != null) entries.add(entry);
                }
                showHighlightMenu(entries, e.getX(), e.getY());
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

    // ── Highlight menu ────────────────────────────────────────────────────────

    private void showHighlightMenu(List<LogEntry> entries, int x, int y) {
        if (entries.isEmpty()) return;
        boolean anyHighlighted = entries.stream().anyMatch(e -> logTableModel.getHighlight(e) != null);

        JPopupMenu menu = new JPopupMenu();

        JPanel swatches = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
        swatches.setOpaque(false);
        for (Color color : HIGHLIGHT_COLORS) {
            boolean allMatch = entries.stream().allMatch(e -> color.equals(logTableModel.getHighlight(e)));
            JButton swatch = new JButton() {
                @Override protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(color);
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
                    if (allMatch) {
                        g2.setColor(Color.WHITE);
                        g2.setStroke(new BasicStroke(2f));
                        g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 5, 5);
                    }
                    g2.dispose();
                }
            };
            swatch.setPreferredSize(new Dimension(22, 22));
            swatch.setBorderPainted(false);
            swatch.setContentAreaFilled(false);
            swatch.setFocusPainted(false);
            swatch.addActionListener(e -> {
                entries.forEach(entry -> logTableModel.setHighlight(entry, color));
                menu.setVisible(false);
                refreshDetailAfterHighlight();
            });
            swatches.add(swatch);
        }
        menu.add(swatches);
        menu.addSeparator();

        final boolean hasHL = logTableModel.hasHighlights();

        JMenuItem clearItem = new JMenuItem(Messages.get("highlight.clear"));
        if (anyHighlighted) {
            clearItem.addActionListener(e -> {
                entries.forEach(entry -> logTableModel.setHighlight(entry, null));
                refreshDetailAfterHighlight();
            });
        } else {
            clearItem.putClientProperty("FlatLaf.style", "disabledForeground: #3C3C3C");
            clearItem.setEnabled(false);
        }
        menu.add(clearItem);

        JMenuItem clearAllItem = new JMenuItem(Messages.get("highlight.clear.all"));
        if (hasHL) {
            clearAllItem.addActionListener(e -> {
                logTableModel.clearAllHighlights();
                refreshDetailAfterHighlight();
            });
        } else {
            clearAllItem.putClientProperty("FlatLaf.style", "disabledForeground: #3C3C3C");
            clearAllItem.setEnabled(false);
        }
        menu.add(clearAllItem);

        menu.show(logTable, x, y);
    }

    private void refreshDetailAfterHighlight() {
        int viewRow = logTable.getSelectedRow();
        int modelRow = viewRow >= 0 ? logTable.convertRowIndexToModel(viewRow) : -1;
        showDetail(modelRow >= 0 ? logTableModel.getEntry(modelRow) : null, modelRow);
        refreshHighlightNav();
    }

    // ── Highlight navigation ──────────────────────────────────────────────────

    private JPanel buildHighlightNavBar() {
        highlightNavBar = new JPanel(new MigLayout("insets 3 8 3 8, gapy 0", "[][grow][]", "[]"));
        highlightNavBar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(0x272727)));
        highlightNavBar.setVisible(false);

        JLabel label = new JLabel(Messages.get("highlight.nav.label"));
        label.setForeground(FG_DIM);
        label.setFont(label.getFont().deriveFont(12f));

        highlightNavSwatches = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 0));
        highlightNavSwatches.setOpaque(false);

        highlightNavPosition = new JLabel("0 / 0");
        highlightNavPosition.setForeground(FG_DIM);
        highlightNavPosition.setFont(highlightNavPosition.getFont().deriveFont(12f));

        JButton prevBtn = makeNavArrowButton("↑");
        JButton nextBtn = makeNavArrowButton("↓");
        prevBtn.setToolTipText(Messages.get("highlight.nav.prev.tooltip"));
        nextBtn.setToolTipText(Messages.get("highlight.nav.next.tooltip"));
        prevBtn.addActionListener(e -> navigateHighlight(-1));
        nextBtn.addActionListener(e -> navigateHighlight(+1));

        JPanel rightPanel = new JPanel(new MigLayout("insets 0", "[]6[]4[]", "[]"));
        rightPanel.setOpaque(false);
        rightPanel.add(highlightNavPosition);
        rightPanel.add(prevBtn);
        rightPanel.add(nextBtn);

        highlightNavBar.add(label);
        highlightNavBar.add(highlightNavSwatches, "grow");
        highlightNavBar.add(rightPanel);
        return highlightNavBar;
    }

    private JButton makeNavArrowButton(String text) {
        JButton btn = new JButton(text);
        btn.setFont(btn.getFont().deriveFont(11f));
        btn.setPreferredSize(new Dimension(22, 18));
        btn.setMargin(new Insets(0, 2, 0, 2));
        return btn;
    }

    private void refreshHighlightNav() {
        java.util.Set<Color> activeColors = logTableModel.getActiveHighlightColors();
        if (activeColors.isEmpty()) {
            highlightNavBar.setVisible(false);
            highlightNavColor = null;
            return;
        }
        // If selected color was removed, fall back to "All"
        if (highlightNavColor != null && !activeColors.contains(highlightNavColor)) {
            highlightNavColor = null;
        }
        highlightNavBar.setVisible(true);
        highlightNavSwatches.removeAll();

        // "All" button
        final Color navColorSnap = highlightNavColor;
        JButton allBtn = new JButton(Messages.get("highlight.nav.all")) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(navColorSnap == null ? new Color(0x4A4A4A) : new Color(0x2A2A2A));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 5, 5);
                g2.setColor(navColorSnap == null ? Color.WHITE : new Color(0x6B7280));
                FontMetrics fm = g2.getFontMetrics();
                int x = (getWidth() - fm.stringWidth(getText())) / 2;
                int y = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
                g2.drawString(getText(), x, y);
                g2.dispose();
            }
        };
        allBtn.setPreferredSize(new Dimension(30, 16));
        allBtn.setBorderPainted(false);
        allBtn.setContentAreaFilled(false);
        allBtn.setFocusPainted(false);
        allBtn.setFont(allBtn.getFont().deriveFont(11f));
        allBtn.addActionListener(e -> { highlightNavColor = null; refreshHighlightNav(); });
        highlightNavSwatches.add(allBtn);

        // Color swatches in HIGHLIGHT_COLORS palette order
        for (Color palette : HIGHLIGHT_COLORS) {
            if (!activeColors.contains(palette)) continue;
            final Color c = palette;
            final boolean selected = c.equals(highlightNavColor);
            JButton swatch = new JButton() {
                @Override protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(c);
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 5, 5);
                    if (selected) {
                        g2.setColor(Color.WHITE);
                        g2.setStroke(new BasicStroke(1.5f));
                        g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 4, 4);
                    }
                    g2.dispose();
                }
            };
            swatch.setPreferredSize(new Dimension(16, 16));
            swatch.setBorderPainted(false);
            swatch.setContentAreaFilled(false);
            swatch.setFocusPainted(false);
            swatch.addActionListener(e -> { highlightNavColor = c; refreshHighlightNav(); });
            highlightNavSwatches.add(swatch);
        }

        highlightNavSwatches.revalidate();
        highlightNavSwatches.repaint();
        updateHighlightNavPosition();
    }

    private void updateHighlightNavPosition() {
        if (highlightNavBar == null || !highlightNavBar.isVisible()) return;
        List<Integer> highlighted = logTableModel.getHighlightedModelRows(highlightNavColor);
        if (highlighted.isEmpty()) { highlightNavPosition.setText("0 / 0"); return; }
        int currentView  = logTable.getSelectedRow();
        int currentModel = currentView >= 0 ? logTable.convertRowIndexToModel(currentView) : -1;
        int pos = 0;
        for (int i = 0; i < highlighted.size(); i++) {
            if (highlighted.get(i) <= currentModel) pos = i + 1;
        }
        highlightNavPosition.setText(pos + " / " + highlighted.size());
    }

    private void navigateHighlight(int direction) {
        List<Integer> highlighted = logTableModel.getHighlightedModelRows(highlightNavColor);
        if (highlighted.isEmpty()) return;
        int currentView  = logTable.getSelectedRow();
        int currentModel = currentView >= 0 ? logTable.convertRowIndexToModel(currentView) : -1;
        int targetModel;
        if (direction > 0) {
            targetModel = highlighted.stream()
                    .filter(r -> r > currentModel)
                    .findFirst()
                    .orElse(highlighted.get(0));
        } else {
            targetModel = highlighted.stream()
                    .filter(r -> r < currentModel)
                    .reduce((a, b) -> b)
                    .orElse(highlighted.get(highlighted.size() - 1));
        }
        int viewRow = logTable.convertRowIndexToView(targetModel);
        if (viewRow >= 0) {
            logTable.setRowSelectionInterval(viewRow, viewRow);
            logTable.scrollRectToVisible(logTable.getCellRect(viewRow, 0, true));
            logTable.requestFocusInWindow();
            updateHighlightNavPosition();
        }
    }

    // ── Level color palette ───────────────────────────────────────────────────

    private static final Map<LogEntry.LogLevel, Color> LEVEL_COLORS;
    static {
        Map<LogEntry.LogLevel, Color> m = new EnumMap<>(LogEntry.LogLevel.class);
        m.put(LogEntry.LogLevel.TRACE,   new Color(0x9E9E9E));
        m.put(LogEntry.LogLevel.DEBUG,   new Color(0x66BB6A));
        m.put(LogEntry.LogLevel.INFO,    new Color(0x29B6F6));
        m.put(LogEntry.LogLevel.WARN,    new Color(0xFFA726));
        m.put(LogEntry.LogLevel.ERROR,   new Color(0xEF5350));
        m.put(LogEntry.LogLevel.FATAL,   new Color(0xFF4081));
        m.put(LogEntry.LogLevel.UNKNOWN, new Color(0x9E9E9E));
        LEVEL_COLORS = Collections.unmodifiableMap(m);
    }
    private static final Color FG_DEFAULT = new Color(0xB0B7C3);
    private static final Color FG_DIM     = new Color(0x6B7280);
    private static final Color SEL_BG     = new Color(0xC8D8E8);

    private static Color blend(Color a, Color b, float t) {
        return new Color(
                (int)(a.getRed()   * t + b.getRed()   * (1 - t)),
                (int)(a.getGreen() * t + b.getGreen() * (1 - t)),
                (int)(a.getBlue()  * t + b.getBlue()  * (1 - t))
        );
    }

    // ── Highlight palette ─────────────────────────────────────────────────────
    static final Color[] HIGHLIGHT_COLORS = {
        new Color(0x6B2020), // Red
        new Color(0x6B4A20), // Orange
        new Color(0x5C5C1A), // Yellow
        new Color(0x1A5C1A), // Green
        new Color(0x1A5C5C), // Teal
        new Color(0x1A2E6B), // Blue
        new Color(0x3D1A6B), // Purple
        new Color(0x6B1A4A), // Pink
    };

    // ── Detail panel ─────────────────────────────────────────────────────────

    private JScrollPane buildDetail() {
        detailArea = new JTextPane();
        detailArea.setEditable(false);
        detailArea.setBackground(new Color(0x191919));
        detailArea.setForeground(FG_DEFAULT);
        detailArea.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        showDetail(null, -1);
        JScrollPane scroll = new JScrollPane(detailArea);
//        scroll.getViewport().setBackground(new Color(0x191919));
        return scroll;
    }

    private static final DateTimeFormatter DETAIL_TS_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneId.systemDefault());

    private void showDetail(LogEntry entry, int modelRow) {
        StyledDocument doc = detailArea.getStyledDocument();
        try { doc.remove(0, doc.getLength()); } catch (BadLocationException ignored) {}

        int fontSize = AppPrefs.getDetailFontSize();

        SimpleAttributeSet key = new SimpleAttributeSet();
        StyleConstants.setFontFamily(key, Font.MONOSPACED);
        StyleConstants.setFontSize(key, fontSize);
        StyleConstants.setBold(key, true);
        StyleConstants.setForeground(key, new Color(0x6B9FD4));

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

        Color hl = logTableModel.getHighlight(entry);
        if (hl != null) {
            SimpleAttributeSet swatchStyle = new SimpleAttributeSet();
            StyleConstants.setFontFamily(swatchStyle, Font.MONOSPACED);
            StyleConstants.setFontSize(swatchStyle, fontSize);
            StyleConstants.setForeground(swatchStyle, hl);
            appendField(doc, key, swatchStyle, Messages.get("detail.highlight.label"), "\u2588\u2588\u2588\u2588\u2588\u2588");
        }

        appendField(doc, key, val,    Messages.get("table.col.timestamp"), ts);
        appendField(doc, key, msgVal, Messages.get("table.col.level"),     level);
        appendField(doc, key, val,    Messages.get("table.col.thread"),    thread);
        appendField(doc, key, val,    Messages.get("table.col.logger"),    logger);

        if (entry.fields() != null) {
            entry.fields().forEach((k, v) -> appendField(doc, key, val, k, v));
        }

        appendField(doc, key, msgVal, Messages.get("table.col.message"),
                entry.message() != null ? entry.message() : "");

        // Collect consecutive continuation lines (null timestamp = stack trace / wrapped lines)
        if (modelRow >= 0) {
            List<LogEntry> continuations = logTableModel.getContinuationLines(modelRow);
            StringBuilder stackTrace = new StringBuilder();
            for (LogEntry c : continuations) stackTrace.append(c.rawLine()).append("\n");
            if (!stackTrace.isEmpty()) {
                insertText(doc, "\n", val);
                insertText(doc, Messages.get("detail.stacktrace.label") + "\n", key);
                insertText(doc, stackTrace.toString().stripTrailing(), val);
            }
        }

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
