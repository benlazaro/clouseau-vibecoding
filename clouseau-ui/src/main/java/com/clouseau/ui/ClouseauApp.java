package com.clouseau.ui;

import com.clouseau.api.LogParser;
import com.clouseau.core.Log4jPatternParser;
import com.clouseau.core.SpringBootPatternParser;
import com.formdev.flatlaf.intellijthemes.FlatOneDarkIJTheme;
import lombok.extern.slf4j.Slf4j;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Path;
import java.util.List;

@Slf4j
public final class ClouseauApp {

    public static void main(String[] args) {
        log.info("Starting Clouseau Log Viewer");

        List<LogParser> parsers = List.of(new SpringBootPatternParser(), new Log4jPatternParser());

        // Use an array so the IPC lambda can capture the reference set later on the EDT.
        MainFrame[] frameRef = new MainFrame[1];

        boolean isPrimary = SingleInstanceManager.tryBecomePrimary(filePath ->
                SwingUtilities.invokeLater(() -> {
                    if (frameRef[0] != null) frameRef[0].openFile(Path.of(filePath));
                }));

        if (!isPrimary) {
            // Forward any CLI file args to the running instance, then exit.
            if (args.length > 0) {
                for (String arg : args) SingleInstanceManager.sendToPrimary(arg);
            }
            log.info("Delegated to existing instance — exiting.");
            System.exit(0);
        }

        FlatOneDarkIJTheme.setup();

        UIManager.put("defaultFont",                  new javax.swing.plaf.FontUIResource(new Font(Font.SANS_SERIF, Font.PLAIN, 13)));
        UIManager.put("TabbedPane.showTabSeparators", true);
        UIManager.put("ScrollBar.thumbArc",           999);
        UIManager.put("Component.focusWidth",         1);
        // Menu-bar item padding and hover-highlight style (FlatLaf-specific properties).
        UIManager.put("MenuBar.itemMargins",          new Insets(2, 12, 2, 12));
        UIManager.put("MenuBar.selectionInsets",      new Insets(0, 2, 0, 2));
        UIManager.put("MenuBar.selectionArc",         8);
        UIManager.put("MenuBar.selectionBackground",  new Color(255, 255, 255, 20));


        SwingUtilities.invokeLater(() -> {
            MainFrame frame = new MainFrame(parsers);
            frameRef[0] = frame;
            frame.setVisible(true);

            // Open any files passed as command-line arguments.
            for (String arg : args) frame.openFile(Path.of(arg));
        });
    }
}
