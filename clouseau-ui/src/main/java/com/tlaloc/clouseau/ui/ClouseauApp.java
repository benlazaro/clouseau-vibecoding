package com.tlaloc.clouseau.ui;

import com.tlaloc.clouseau.runtime.ClouseauPluginManager;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;

import javax.swing.*;
import java.awt.*;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Slf4j
public final class ClouseauApp {

    public static void main(String[] args) {
        // Configure Log4j2 to use ~/.clouseau/log4j2.xml
        configureLogging();

        log.info("Starting Clouseau Log Viewer");

        SplashWindow splash = SplashWindow.createAndShow();

        // Plugin parsers — loaded from the plugins directory; may be empty if dir not found
        String pluginsDirProp = System.getProperty("clouseau.plugins.dir");
        // Default: ~/.clouseau/plugins/parser — safe in dev (won't collide with the project tree)
        // and a sensible install location for end users.
        Path pluginsDir = pluginsDirProp != null
                ? Path.of(pluginsDirProp)
                : Path.of(System.getProperty("user.home"), ".clouseau", "plugins");
        ClouseauPluginManager pluginManager = new ClouseauPluginManager(pluginsDir);
        pluginManager.loadAll();
        log.info("Plugin manager loaded");

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
        UIManager.put("TabbedPane.tabArc",             10);
        UIManager.put("TabbedPane.selectedBackground", new Color(0x191919));  // accent-tinted highlight
        UIManager.put("TabbedPane.hoverBackground",    new Color(0x4d4d4d));
        UIManager.put("TabbedPane.underlineHeight",    2);
        UIManager.put("TabbedPane.underlineColor",     new Color(0x4d4d4d));
        UIManager.put("ScrollBar.thumbArc",           999);
        UIManager.put("Component.focusWidth",         0.5);
        // Menu-bar item padding and hover-highlight style (FlatLaf-specific properties).
        UIManager.put("MenuBar.itemMargins",          new Insets(0, 8, 0, 8));
//        UIManager.put("MenuBar.selectionInsets",      new Insets(0, 2, 0, 2));
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
            MainFrame frame = new MainFrame(pluginManager);
            frameRef[0] = frame;

            Runnable show = () -> {
                frame.setVisible(true);
                for (String arg : args) frame.openFile(Path.of(arg));
            };

            if (splash != null) {
                splash.close(show);
            } else {
                show.run();
            }
        });
    }

    /** Configure Log4j2 to load config from ~/.clouseau/log4j2.xml, creating it if needed. */
    private static void configureLogging() {
        Path clouseauDir = Path.of(System.getProperty("user.home"), ".clouseau");
        Path configFile = clouseauDir.resolve("log4j2.xml");

        try {
            // Create directory if it doesn't exist
            Files.createDirectories(clouseauDir);

            // Copy default config to ~/.clouseau if not present
            if (!Files.exists(configFile)) {
                try (InputStream in = ClouseauApp.class.getResourceAsStream("/log4j2.xml")) {
                    if (in != null) {
                        Files.copy(in, configFile);
                    }
                }
            }

            // Configure Log4j2 to use the config file
            LoggerContext context = (LoggerContext) LogManager.getContext(false);
            context.setConfigLocation(configFile.toUri());
        } catch (Exception e) {
            System.err.println("Failed to configure logging: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
