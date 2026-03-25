package com.clouseau.ui;

import com.clouseau.api.LogEntry;
import com.clouseau.api.LogParser;
import com.clouseau.api.LogSource;
import com.clouseau.core.FileLogSource;
import com.clouseau.core.LogIndex;
import lombok.extern.slf4j.Slf4j;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.plaf.basic.BasicFileChooserUI;
import java.awt.*;
import java.awt.Component;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

@Slf4j
public final class MainFrame extends JFrame {

    private final LogIndex logIndex;
    private final List<LogParser> parsers;
    private final LogTableModel logTableModel = new LogTableModel();
    private JTable logTable;
    private volatile LogSource currentSource;
    private int lastParserIndex = 0;  // 0 = Auto-detect; 1..n = parsers.get(n-1)

    public MainFrame(LogIndex logIndex, List<LogParser> parsers) {
        super(Messages.get("app.title"));
        this.logIndex = logIndex;
        this.parsers  = parsers;
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1280, 800);
        setMinimumSize(new Dimension(800, 500));
        setLocationRelativeTo(null);

        setLayout(new MigLayout("fill, insets 0", "[grow]", "[36!][grow][180!]"));

        add(buildToolbar(),  "growx, wrap");
        add(buildLogTable(), "grow, wrap");
        add(buildDetail(),   "growx");
    }

    private JPanel buildToolbar() {
        JPanel bar = new JPanel(new MigLayout("insets 4 8 4 8, gap 6", "[][][][grow][]"));

        JButton open   = new JButton(Messages.get("toolbar.open"));
        open.addActionListener(e -> openFile());
        JButton follow = new JButton(Messages.get("toolbar.follow"));
        follow.setToolTipText(Messages.get("toolbar.follow.tooltip"));

        JTextField filter = new JTextField();
        filter.putClientProperty("JTextField.placeholderText", Messages.get("toolbar.filter.placeholder"));

        JButton plugins = new JButton(Messages.get("toolbar.plugins"));
        plugins.setToolTipText(Messages.get("toolbar.plugins.tooltip"));

        bar.add(open);
        bar.add(follow);
        bar.add(new JSeparator(JSeparator.VERTICAL), "growy");
        bar.add(filter,  "growx");
        bar.add(plugins);
        return bar;
    }

    private JScrollPane buildLogTable() {
        logTable = new JTable(logTableModel) {
            private static final Color ROW_DARK  = new Color(0x25272E);
            private static final Color ROW_LIGHT = new Color(0x2D2F38);

            @Override public boolean isCellEditable(int r, int c) { return false; }

            @Override
            public Component prepareRenderer(javax.swing.table.TableCellRenderer renderer, int row, int col) {
                Component c = super.prepareRenderer(renderer, row, col);
                if (!isRowSelected(row)) {
                    c.setBackground(row % 2 == 0 ? ROW_DARK : ROW_LIGHT);
                }
                return c;
            }
        };
        logTable.setBackground(new Color(0x1E1F22));
        logTable.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
        logTable.getColumnModel().getColumn(0).setPreferredWidth(50);
        logTable.getColumnModel().getColumn(1).setPreferredWidth(180);
        logTable.getColumnModel().getColumn(2).setPreferredWidth(60);
        logTable.getColumnModel().getColumn(3).setPreferredWidth(120);
        logTable.getColumnModel().getColumn(4).setPreferredWidth(200);
        logTable.setShowHorizontalLines(true);
        logTable.setShowVerticalLines(false);
        logTable.setGridColor(new Color(0x2C2F3A));
        logTable.setRowHeight(22);
        JScrollPane scrollPane = new JScrollPane(logTable);
        scrollPane.getViewport().setBackground(new Color(0x1E1F22));
        return scrollPane;
    }

    private void openFile() {
        JFileChooser chooser = new JFileChooser() {
            @Override
            public void approveSelection() {
                File f = getSelectedFile();
                if (f != null && f.isFile()) {
                    Optional<LogParser> chosen = lastParserIndex == 0
                            ? Optional.empty()
                            : Optional.of(parsers.get(lastParserIndex - 1));
                    if (detectParser(f.toPath(), chosen).isEmpty()) return;
                }
                super.approveSelection();
            }
        };
        chooser.setDialogTitle(Messages.get("filechooser.title"));
        chooser.setFileFilter(new FileNameExtensionFilter(
                Messages.get("filechooser.filter.desc"), "log", "txt", "out", "gz"));
        chooser.setAcceptAllFileFilterUsed(true);
        chooser.setAccessory(buildParserAccessory(chooser));

        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;

        Path file = chooser.getSelectedFile().toPath();

        // Resolve which parser(s) to use based on the combo selection
        Optional<LogParser> chosen = lastParserIndex == 0
                ? Optional.empty()
                : Optional.of(parsers.get(lastParserIndex - 1));
        log.info("Opening {} with parser: {}", file.getFileName(),
                chosen.map(LogParser::getName).orElse("Auto-detect"));

        // Stop and discard any in-progress source
        LogSource old = currentSource;
        if (old != null) {
            try { old.close(); } catch (Exception ex) { log.warn("Failed to close previous source", ex); }
        }

        // Clear stale data synchronously on the EDT before starting the new source
        logIndex.clear();
        logTableModel.clear();

        // Open the new source — reads in a background thread
        FileLogSource source = new FileLogSource(file);
        currentSource = source;
        try {
            source.open(rawLine -> {
                Stream<LogParser> candidates = chosen
                        .map(Stream::of)
                        .orElseGet(parsers::stream);
                candidates.filter(p -> p.canParse(rawLine))
                        .findFirst()
                        .map(p -> p.parse(rawLine))
                        .ifPresent(logIndex::add);
            });
            setTitle(Messages.get("app.title") + " \u2014 " + file.getFileName());
        } catch (Exception ex) {
            log.error("Failed to open {}", file, ex);
            JOptionPane.showMessageDialog(this,
                    ex.getMessage(),
                    Messages.get("filechooser.error.title"),
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Reads up to 50 non-blank lines and returns the first parser that matches,
     * or empty if none do. On I/O error returns the chosen parser (or the first
     * available parser) so a network hiccup doesn't block the open.
     */
    private Optional<LogParser> detectParser(Path file, Optional<LogParser> chosen) {
        try (BufferedReader reader = Files.newBufferedReader(file)) {
            String line;
            int checked = 0;
            while ((line = reader.readLine()) != null && checked < 50) {
                if (line.isBlank()) continue;
                checked++;
                final String candidate = line;
                Optional<LogParser> match = chosen
                        .map(Stream::of)
                        .orElseGet(parsers::stream)
                        .filter(p -> p.canParse(candidate))
                        .findFirst();
                if (match.isPresent()) return match;
            }
        } catch (IOException e) {
            log.warn("Could not validate file {} — proceeding anyway", file, e);
            return chosen.or(() -> parsers.stream().findFirst());
        }
        return Optional.empty();
    }

    private JPanel buildParserAccessory(JFileChooser chooser) {
        String[] items = Stream.concat(
                Stream.of(Messages.get("filechooser.parser.autodetect")),
                parsers.stream().map(LogParser::getName)
        ).toArray(String[]::new);

        JComboBox<String> combo = new JComboBox<>(items);
        combo.setSelectedIndex(lastParserIndex);

        JLabel statusLabel = new JLabel(" ");
        statusLabel.setFont(statusLabel.getFont().deriveFont(11f));

        Runnable validate = () -> {
            File selected = chooser.getSelectedFile();
            if (selected == null || !selected.isFile()) {
                statusLabel.setText(" ");
                findApproveButton(chooser).ifPresent(b -> b.setEnabled(true));
                return;
            }
            int idx = combo.getSelectedIndex();
            Optional<LogParser> chosen = idx == 0
                    ? Optional.empty()
                    : Optional.of(parsers.get(idx - 1));
            Optional<LogParser> matched = detectParser(selected.toPath(), chosen);
            if (matched.isPresent()) {
                statusLabel.setForeground(new Color(0x4CAF50));
                // Show matched parser name only when auto-detect resolved it
                String statusText = chosen.isPresent()
                        ? Messages.get("filechooser.status.compatible")
                        : Messages.get("filechooser.status.compatible.detected").formatted(matched.get().getName());
                statusLabel.setText(statusText);
            } else {
                statusLabel.setForeground(new Color(0xE57373));
                String name = chosen.map(LogParser::getName)
                        .orElse(Messages.get("filechooser.parser.autodetect"));
                statusLabel.setText(Messages.get("filechooser.status.incompatible").formatted(name));
            }
            findApproveButton(chooser).ifPresent(b -> b.setEnabled(matched.isPresent()));
        };

        combo.addActionListener(e -> {
            lastParserIndex = combo.getSelectedIndex();
            validate.run();
        });
        chooser.addPropertyChangeListener(JFileChooser.SELECTED_FILE_CHANGED_PROPERTY,
                e -> validate.run());
        chooser.addPropertyChangeListener(JFileChooser.DIRECTORY_CHANGED_PROPERTY, e -> {
            chooser.setSelectedFile(null);
            // BasicFileChooserUI skips setFileName() when the new file is null,
            // so the text field keeps the old name — clear it explicitly.
            if (chooser.getUI() instanceof BasicFileChooserUI basicUI) {
                basicUI.setFileName("");
            }
            // The L&F's own DIRECTORY_CHANGED handler runs after ours and may
            // leave the approve button in an inconsistent state — re-validate
            // once the event queue has settled.
            SwingUtilities.invokeLater(validate);
        });

        JPanel panel = new JPanel(new MigLayout("insets 8, wrap 1", "[160px!]"));
        panel.add(new JLabel(Messages.get("filechooser.parser.label")));
        panel.add(combo, "growx");
        panel.add(statusLabel, "growx, gaptop 6");
        return panel;
    }

    private static Optional<JButton> findApproveButton(JFileChooser chooser) {
        String text = UIManager.getString("FileChooser.openButtonText");
        return findButtonByText(chooser, text);
    }

    private static Optional<JButton> findButtonByText(Container container, String text) {
        for (Component c : container.getComponents()) {
            if (c instanceof JButton b && text != null && text.equals(b.getText())) {
                return Optional.of(b);
            }
            if (c instanceof Container sub) {
                Optional<JButton> found = findButtonByText(sub, text);
                if (found.isPresent()) return found;
            }
        }
        return Optional.empty();
    }

    public LogTableModel getLogTableModel() { return logTableModel; }
    public JTable getLogTable()             { return logTable; }

    private JScrollPane buildDetail() {
        JTextArea area = new JTextArea(Messages.get("detail.placeholder"));
        area.setEditable(false);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        area.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        return new JScrollPane(area);
    }
}
