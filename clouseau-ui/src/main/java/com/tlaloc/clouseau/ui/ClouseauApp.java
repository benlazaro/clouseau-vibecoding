package com.tlaloc.clouseau.ui;

import com.tlaloc.clouseau.api.LogParser;
import com.tlaloc.clouseau.core.Log4jPatternParser;
import com.tlaloc.clouseau.core.SpringBootPatternParser;
import com.tlaloc.clouseau.runtime.ClouseauPluginManager;
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
        // Merge: built-ins first, then any plugin parsers not already covered by name
        java.util.Set<String> builtinNames = new java.util.HashSet<>();
        builtins.forEach(p -> builtinNames.add(p.getName()));
        List<LogParser> parsers = java.util.stream.Stream.concat(
                builtins.stream(),
                pluginManager.getExtensions(LogParser.class).stream()
                             .filter(p -> !builtinNames.contains(p.getName()))
        ).toList();
        log.info("Loaded {} parser(s): {}", parsers.size(),
                parsers.stream().map(LogParser::getName).toList());

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
