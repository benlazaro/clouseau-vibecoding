package com.clouseau.ui;

import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.awt.*;

/**
 * Modal application settings / user preferences dialog.
 */
final class SettingsDialog extends JDialog {

    private final JTable logTable;

    // Table settings
    private final JSpinner rowHeightSpinner;

    // Detail panel settings
    private final JSpinner detailFontSizeSpinner;
    private final JCheckBox wrapLinesCheckBox;

    SettingsDialog(Frame owner, JTable logTable) {
        super(owner, Messages.get("settings.title"), true);
        this.logTable = logTable;

        // Capture current values as defaults
        rowHeightSpinner       = new JSpinner(new SpinnerNumberModel(logTable.getRowHeight(), 14, 64, 1));
        detailFontSizeSpinner  = new JSpinner(new SpinnerNumberModel(12, 8, 36, 1));
        wrapLinesCheckBox      = new JCheckBox(Messages.get("settings.detail.wrap"), true);

        JPanel content = new JPanel(new MigLayout("insets 16, wrap 2, gapy 6", "[grow,fill][120px!]"));

        // ── Table section ────────────────────────────────────────────────
        content.add(sectionLabel(Messages.get("settings.section.table")), "span 2, gaptop 0, gapbottom 4");
        content.add(new JLabel(Messages.get("settings.row.height")));
        content.add(rowHeightSpinner);

        // ── Detail panel section ─────────────────────────────────────────
        content.add(sectionLabel(Messages.get("settings.section.detail")), "span 2, gaptop 12, gapbottom 4");
        content.add(new JLabel(Messages.get("settings.detail.font.size")));
        content.add(detailFontSizeSpinner);
        content.add(wrapLinesCheckBox, "span 2");

        // ── Buttons ──────────────────────────────────────────────────────
        JButton ok     = new JButton(Messages.get("settings.button.ok"));
        JButton cancel = new JButton(Messages.get("settings.button.cancel"));
        ok.addActionListener(e -> { applySettings(); dispose(); });
        cancel.addActionListener(e -> dispose());

        JPanel buttons = new JPanel(new MigLayout("insets 8 0 0 0, align right", "[][]"));
        buttons.add(cancel);
        buttons.add(ok);
        content.add(buttons, "span 2, growx");

        setContentPane(content);
        getRootPane().setDefaultButton(ok);
        getRootPane().registerKeyboardAction(
                e -> dispose(),
                KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);

        pack();
        setResizable(false);
        setLocationRelativeTo(owner);
    }

    private void applySettings() {
        int rowHeight = (int) rowHeightSpinner.getValue();
        logTable.setRowHeight(rowHeight);
        // detailFontSizeSpinner and wrapLinesCheckBox values are stored here
        // for future wiring to the detail panel.
    }

    private static JLabel sectionLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        return label;
    }
}
