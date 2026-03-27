package com.tlaloc.clouseau.ui;

import com.tlaloc.clouseau.api.LogParser;
import com.tlaloc.clouseau.core.Log4jPatternParser;
import com.tlaloc.clouseau.core.SpringBootPatternParser;
import com.tlaloc.clouseau.runtime.ClouseauPluginManager;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import lombok.extern.slf4j.Slf4j;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Path;
import java.util.List;

@Slf4j
public final class ClouseauApp {

    public static void main(String[] args) {
        log.info("Starting Clouseau Log Viewer");

        // Built-in parsers — always available regardless of how the app is launched
        List<LogParser> builtins = List.of(new SpringBootPatternParser(), new Log4jPatternParser());

        // Plugin parsers — loaded from the plugins directory; may be empty if dir not found
        String pluginsDirProp = System.getProperty("clouseau.plugins.dir");
        // Default: ~/.clouseau/plugins/parser — safe in dev (won't collide with the project tree)
        // and a sensible install location for end users.
        Path pluginsDir = pluginsDirProp != null
                ? Path.of(pluginsDirProp)
                : Path.of(System.getProperty("user.home"), ".clouseau", "plugins", "parser");
        ClouseauPluginManager pluginManager = new ClouseauPluginManager(pluginsDir);
        pluginManager.loadAll();
        log.info("Loaded {} built-in parser(s)", builtins.size());

        Runtime.getRuntime().addShutdownHook(new Thread(pluginManager::stopAll, "plugin-shutdown"));

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

        // FlatDarkLaf resolves all component colors from @-variables, so a single
        // setGlobalExtraDefaults call propagates to every component uniformly.
        // (IntelliJ-wrapper themes bypass this variable system entirely.)
        FlatLaf.setGlobalExtraDefaults(java.util.Map.of(
            "@background",          "#2c2c2c",   // panels, toolbar, tabs, menus
                "@componentBackground", "#282828",   // text fields, combos, lists, trees
                "@accentColor",         "#4d4d4d"    // focus rings, selection, active indicators
        ));
        FlatDarkLaf.setup();

        Color componentBg =new Color(0x282828);
        UIManager.put("defaultFont",                  new javax.swing.plaf.FontUIResource(new Font(Font.SANS_SERIF, Font.PLAIN, 13)));
        UIManager.put("TabbedPane.showTabSeparators", true);
        UIManager.put("ScrollBar.thumbArc",           999);
        UIManager.put("Component.focusWidth",         1);
        // Menu-bar item padding and hover-highlight style (FlatLaf-specific properties).
        UIManager.put("MenuBar.itemMargins",          new Insets(2, 12, 2, 12));
        UIManager.put("MenuBar.selectionInsets",      new Insets(0, 2, 0, 2));
        UIManager.put("MenuBar.selectionArc",         8);
        UIManager.put("MenuBar.selectionBackground",  componentBg);
//        // Explicit menu colors so drop-down menus inherit the new background
        UIManager.put("PopupMenu.background",         componentBg);
        UIManager.put("Menu.background",              componentBg);
        UIManager.put("MenuItem.background",          componentBg);
//        UIManager.put("MenuItem.selectionBackground", new Color(0x4A8CC8));
//
//        // Buttons — flat resting state, visible hover/active using accent tint
//        UIManager.put("Button.background",                new Color(0x222222));
//        UIManager.put("Button.hoverBackground",           new Color(0x2E2E2E));
//        UIManager.put("Button.pressedBackground",         new Color(0x3A3A3A));
//        UIManager.put("ToggleButton.background",          new Color(0x222222));
//        UIManager.put("ToggleButton.hoverBackground",     new Color(0x2E2E2E));
//        UIManager.put("ToggleButton.selectedBackground",  new Color(0x2B3A4E));  // accent tint
//        UIManager.put("Component.borderColor",            new Color(0x2E2E2E));
//        UIManager.put("Component.disabledBorderColor",    new Color(0x242424));
//        UIManager.put("Component.focusColor",             new Color(0x4A8CC8));
//        // Table header — match chrome background
//        UIManager.put("TableHeader.background",           new Color(0x1C1C1C));
//        UIManager.put("TableHeader.separatorColor",       new Color(0x2E2E2E));
//        UIManager.put("TableHeader.bottomSeparatorColor", new Color(0x2E2E2E));


        SwingUtilities.invokeLater(() -> {
            MainFrame frame = new MainFrame(builtins, pluginManager);
            frameRef[0] = frame;
            frame.setVisible(true);

            // Open any files passed as command-line arguments.
            for (String arg : args) frame.openFile(Path.of(arg));
        });
    }
}
