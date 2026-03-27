package com.tlaloc.clouseau.ui;

import com.tlaloc.clouseau.runtime.ClouseauPluginManager;
import com.tlaloc.clouseau.runtime.ClouseauPluginManager.PluginInfo;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class PluginManagerDialog extends JDialog {

    private final ClouseauPluginManager pluginManager;
    private final Runnable onRefresh;
    private final PluginTableModel tableModel;

    public PluginManagerDialog(JFrame owner, ClouseauPluginManager pluginManager, Runnable onRefresh) {
        super(owner, Messages.get("plugins.dialog.title"), true);
        this.pluginManager = pluginManager;
        this.onRefresh     = onRefresh;
        this.tableModel    = new PluginTableModel(pluginManager, onRefresh);
        buildUI();
        pack();
        setMinimumSize(new Dimension(580, 320));
        setLocationRelativeTo(owner);
    }

    private void buildUI() {
        setLayout(new MigLayout("fill, insets 16", "[grow]", "[][grow][]"));

        // ── Plugins directory ─────────────────────────────────────────────────
        Path root = pluginManager.getPluginsRoot();

        JTextField dirField = new JTextField(root.toString());
        dirField.setEditable(false);

        JButton openFolder = new JButton(Messages.get("plugins.dialog.open.folder"));
        openFolder.addActionListener(e -> openFolder(root));

        JPanel dirPanel = new JPanel(new MigLayout("insets 0, gap 6", "[][grow][]"));
        dirPanel.add(new JLabel(Messages.get("plugins.dialog.dir.label")));
        dirPanel.add(dirField, "growx");
        dirPanel.add(openFolder);
        add(dirPanel, "growx, wrap");

        // ── Plugin table ──────────────────────────────────────────────────────
        JTable table = new JTable(tableModel);
        table.setRowHeight(28);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));

        // Column widths
        table.getColumnModel().getColumn(0).setMinWidth(60);
        table.getColumnModel().getColumn(0).setMaxWidth(60);
        table.getColumnModel().getColumn(2).setMinWidth(90);
        table.getColumnModel().getColumn(2).setMaxWidth(90);
        table.getColumnModel().getColumn(3).setMinWidth(80);
        table.getColumnModel().getColumn(3).setMaxWidth(80);

        add(new JScrollPane(table), "grow, wrap");

        // ── Buttons ───────────────────────────────────────────────────────────
        JButton refreshBtn = new JButton(Messages.get("plugins.dialog.refresh"));
        refreshBtn.addActionListener(e -> {
            pluginManager.refresh();
            onRefresh.run();
            tableModel.reload();
        });

        JButton closeBtn = new JButton(Messages.get("plugins.dialog.close"));
        closeBtn.addActionListener(e -> dispose());

        JPanel buttons = new JPanel(new MigLayout("insets 0", "push[][]", "[]"));
        buttons.add(refreshBtn);
        buttons.add(closeBtn);
        add(buttons, "growx");
    }

    private void openFolder(Path path) {
        try {
            Files.createDirectories(path);
            Desktop.getDesktop().open(path.toFile());
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this,
                    Messages.get("plugins.dialog.open.folder.error").formatted(path),
                    Messages.get("plugins.dialog.open.folder.error.title"),
                    JOptionPane.WARNING_MESSAGE);
        }
    }

    // ── Table model ───────────────────────────────────────────────────────────

    private static final class PluginTableModel extends AbstractTableModel {

        private static final String[] COLUMNS = {
            Messages.get("plugins.table.col.enabled"),
            Messages.get("plugins.table.col.id"),
            Messages.get("plugins.table.col.version"),
            Messages.get("plugins.table.col.status"),
        };

        private final ClouseauPluginManager pluginManager;
        private final Runnable onRefresh;
        private List<PluginInfo> rows;

        PluginTableModel(ClouseauPluginManager pluginManager, Runnable onRefresh) {
            this.pluginManager = pluginManager;
            this.onRefresh     = onRefresh;
            this.rows          = new ArrayList<>(pluginManager.getPluginInfos());
        }

        void reload() {
            rows = new ArrayList<>(pluginManager.getPluginInfos());
            fireTableDataChanged();
        }

        @Override public int getRowCount()    { return rows.size(); }
        @Override public int getColumnCount() { return COLUMNS.length; }
        @Override public String getColumnName(int col) { return COLUMNS[col]; }

        @Override
        public Class<?> getColumnClass(int col) {
            return col == 0 ? Boolean.class : String.class;
        }

        @Override
        public boolean isCellEditable(int row, int col) {
            // Only the enabled toggle is editable, and not for failed plugins
            return col == 0 && !"Failed".equals(rows.get(row).state());
        }

        @Override
        public Object getValueAt(int row, int col) {
            PluginInfo p = rows.get(row);
            return switch (col) {
                case 0 -> p.enabled();
                case 1 -> p.id();
                case 2 -> p.version();
                case 3 -> p.state();
                default -> null;
            };
        }

        @Override
        public void setValueAt(Object value, int row, int col) {
            if (col != 0) return;
            String id = rows.get(row).id();
            if ((boolean) value) {
                pluginManager.enablePlugin(id);
            } else {
                pluginManager.disablePlugin(id);
            }
            onRefresh.run();
            reload();
        }
    }
}
