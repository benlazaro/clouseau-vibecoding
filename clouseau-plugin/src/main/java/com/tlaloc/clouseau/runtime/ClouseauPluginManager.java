package com.tlaloc.clouseau.runtime;

import lombok.extern.slf4j.Slf4j;
import org.pf4j.DefaultPluginManager;
import org.pf4j.PluginManager;
import org.pf4j.PluginState;
import org.pf4j.PluginWrapper;

import java.nio.file.Path;
import java.util.List;

/**
 * Bootstraps PF4J and exposes typed extension lookups.
 *
 * Drop a plugin JAR into the plugins/ folder and call refresh()
 * to hot-reload without restarting the app.
 */
@Slf4j
public final class ClouseauPluginManager {

    /** Snapshot of a loaded plugin's identity and runtime state. */
    public record PluginInfo(String id, String version, String state, boolean enabled) {}

    private final PluginManager pf4j;

    public ClouseauPluginManager(Path pluginsDir) {
        this.pf4j = new DefaultPluginManager(pluginsDir);
    }

    public Path getPluginsRoot() {
        return pf4j.getPluginsRoot();
    }

    public List<PluginInfo> getPluginInfos() {
        return pf4j.getPlugins().stream()
                .map(pw -> new PluginInfo(
                        pw.getPluginId(),
                        pw.getDescriptor().getVersion(),
                        friendlyState(pw),
                        pw.getPluginState() == PluginState.STARTED))
                .toList();
    }

    public void enablePlugin(String id) {
        pf4j.enablePlugin(id);
        pf4j.startPlugin(id);
        log.info("Enabled plugin '{}'", id);
    }

    public void disablePlugin(String id) {
        pf4j.stopPlugin(id);
        pf4j.disablePlugin(id);
        log.info("Disabled plugin '{}'", id);
    }

    private static String friendlyState(PluginWrapper pw) {
        return switch (pw.getPluginState()) {
            case STARTED  -> "Running";
            case STOPPED  -> "Stopped";
            case DISABLED -> "Disabled";
            case FAILED   -> "Failed";
            default       -> pw.getPluginState().toString();
        };
    }

    public void loadAll() {
        log.info("Loading plugins from {}", pf4j.getPluginsRoot());
        pf4j.loadPlugins();
        pf4j.startPlugins();
        log.info("Loaded {} plugin(s)", pf4j.getPlugins().size());
    }

    /** Unload stale JARs and load any new ones — supports hot-reload. */
    public void refresh() {
        log.info("Refreshing plugins");
        pf4j.stopPlugins();
        pf4j.unloadPlugins();
        pf4j.loadPlugins();
        pf4j.startPlugins();
        log.info("Refresh complete — {} plugin(s) active", pf4j.getPlugins().size());
    }

    /** Return all registered extensions of a given type across all active plugins. */
    public <T> List<T> getExtensions(Class<T> type) {
        return pf4j.getExtensions(type);
    }

    public void stopAll() {
        log.info("Stopping all plugins");
        pf4j.stopPlugins();
        pf4j.unloadPlugins();
    }
}
