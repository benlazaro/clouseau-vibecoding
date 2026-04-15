package com.droidenx.clouseau.ui;

import com.droidenx.clouseau.runtime.ClouseauPluginManager;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;

import javax.swing.*;
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

        if (splash != null) splash.setStatus("Loading plugins...");
        ClouseauPluginManager pluginManager = new ClouseauPluginManager(pluginsDir);
        pluginManager.loadAll();
        log.info("Plugin manager loaded");

        Runtime.getRuntime().addShutdownHook(new Thread(pluginManager::stopAll, "plugin-shutdown"));

        // Use an array so the IPC lambda can capture the reference set later on the EDT.
        MainFrame[] frameRef = new MainFrame[1];

        if (splash != null) splash.setStatus("Starting...");
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

        ThemeManager.applyTheme(AppPrefs.getTheme());


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
