package com.tlaloc.clouseau.ui;

import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.awt.*;

/**
 * Modal application settings / user preferences dialog.
 */
final class SettingsDialog extends JDialog {

    private final LogPanel logPanel;

    // Table settings
    private final JSpinner rowHeightSpinner;

    // Detail panel settings
    private final JSpinner detailFontSizeSpinner;
    private final JCheckBox wrapLinesCheckBox;

    // Tab settings
    private final JCheckBox confirmCloseCheckBox;

    SettingsDialog(Frame owner, LogPanel logPanel) {
        super(owner, Messages.get("settings.title"), true);
        this.logPanel = logPanel;

        JTable logTable = logPanel != null ? logPanel.getLogTable() : null;
        int currentRowHeight = logTable != null ? logTable.getRowHeight() : 22;
        rowHeightSpinner       = new JSpinner(new SpinnerNumberModel(currentRowHeight, 14, 64, 1));
        detailFontSizeSpinner  = new JSpinner(new SpinnerNumberModel(AppPrefs.getDetailFontSize(), 8, 36, 1));
        wrapLinesCheckBox      = new JCheckBox(Messages.get("settings.detail.wrap"), true);
        confirmCloseCheckBox   = new JCheckBox(Messages.get("settings.tab.confirm.close"), AppPrefs.isTabCloseConfirm());

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

        // ── Tabs section ──────────────────────────────────────────────────
        content.add(sectionLabel(Messages.get("settings.section.tabs")), "span 2, gaptop 12, gapbottom 4");
        content.add(confirmCloseCheckBox, "span 2");

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
        JTable logTable = logPanel != null ? logPanel.getLogTable() : null;
        if (logTable != null) logTable.setRowHeight((int) rowHeightSpinner.getValue());
        int fontSize = (int) detailFontSizeSpinner.getValue();
        AppPrefs.setDetailFontSize(fontSize);
        if (logPanel != null) logPanel.applyDetailFontSize(fontSize);
        AppPrefs.setTabCloseConfirm(confirmCloseCheckBox.isSelected());
    }

    private static JLabel sectionLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        return label;
    }
}
