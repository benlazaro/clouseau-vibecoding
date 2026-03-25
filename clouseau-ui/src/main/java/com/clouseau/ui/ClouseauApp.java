package com.clouseau.ui;

import com.formdev.flatlaf.intellijthemes.FlatOneDarkIJTheme;
import lombok.extern.slf4j.Slf4j;

import javax.swing.*;
import java.awt.*;

@Slf4j
public final class ClouseauApp {

    public static void main(String[] args) {
        log.info("Starting Clouseau Log Viewer");
        // Apply FlatLaf before any Swing component is created
        FlatOneDarkIJTheme.setup();

        // Global UI tweaks
        UIManager.put("defaultFont",          new javax.swing.plaf.FontUIResource(new Font(Font.SANS_SERIF, Font.PLAIN, 13)));
        UIManager.put("TabbedPane.showTabSeparators", true);
        UIManager.put("ScrollBar.thumbArc",   999);
        UIManager.put("Component.focusWidth", 1);

        SwingUtilities.invokeLater(() -> {
            MainFrame frame = new MainFrame();
            frame.setVisible(true);
        });
    }
}
