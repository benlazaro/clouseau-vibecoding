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
import java.awt.*;
import java.nio.file.Path;
import java.util.List;

@Slf4j
public final class MainFrame extends JFrame {

    private final LogIndex logIndex;
    private final List<LogParser> parsers;
    private final LogTableModel logTableModel = new LogTableModel();
    private JTable logTable;
    private volatile LogSource currentSource;

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
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        logTable.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
        logTable.getColumnModel().getColumn(0).setPreferredWidth(50);
        logTable.getColumnModel().getColumn(1).setPreferredWidth(180);
        logTable.getColumnModel().getColumn(2).setPreferredWidth(60);
        logTable.getColumnModel().getColumn(3).setPreferredWidth(120);
        logTable.getColumnModel().getColumn(4).setPreferredWidth(200);
        return new JScrollPane(logTable);
    }

    private void openFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(Messages.get("filechooser.title"));
        chooser.setFileFilter(new FileNameExtensionFilter(
                Messages.get("filechooser.filter.desc"), "log", "txt", "out", "gz"));
        chooser.setAcceptAllFileFilterUsed(true);

        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;

        Path file = chooser.getSelectedFile().toPath();

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
            source.open(rawLine -> parsers.stream()
                    .filter(p -> p.canParse(rawLine))
                    .findFirst()
                    .map(p -> p.parse(rawLine))
                    .ifPresent(logIndex::add));
            setTitle(Messages.get("app.title") + " \u2014 " + file.getFileName());
        } catch (Exception ex) {
            log.error("Failed to open {}", file, ex);
            JOptionPane.showMessageDialog(this,
                    ex.getMessage(),
                    Messages.get("filechooser.error.title"),
                    JOptionPane.ERROR_MESSAGE);
        }
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
