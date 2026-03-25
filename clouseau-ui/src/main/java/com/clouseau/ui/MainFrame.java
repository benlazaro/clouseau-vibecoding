package com.clouseau.ui;

import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.awt.*;

public final class MainFrame extends JFrame {

    private final LogTableModel logTableModel = new LogTableModel();
    private JTable logTable;

    public MainFrame() {
        super(Messages.get("app.title"));
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
