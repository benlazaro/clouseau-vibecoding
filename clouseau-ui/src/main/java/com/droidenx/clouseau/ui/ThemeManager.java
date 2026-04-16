package com.droidenx.clouseau.ui;

import com.formdev.flatlaf.*;
import com.formdev.flatlaf.intellijthemes.*;
import lombok.extern.slf4j.Slf4j;

import javax.swing.*;
import javax.swing.plaf.FontUIResource;
import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.*;
import java.util.List;

/**
 * Manages built-in and user-installed FlatLaf themes.
 *
 * <p>Built-in themes are baked into the application. User themes are
 * {@code .properties} files placed in {@code ~/.clouseau/themes/}.
 * A minimal user theme looks like:
 * <pre>
 *   # required — tells FlatLaf which base LAF to extend
 *   @baseTheme = FlatDarkLaf
 *   @background = #1e1e2e
 *   @foreground = #cdd6f4
 * </pre>
 */
@Slf4j
public final class ThemeManager {

    public static final String DEFAULT_THEME = "Clouseau Dark";

    /** Ordered map of builtin-name → setup runnable. */
    private static final LinkedHashMap<String, Runnable> BUILTINS = new LinkedHashMap<>();

    static {
        BUILTINS.put("Clouseau Dark", ThemeManager::setupClouseauDark);
        BUILTINS.put("FlatLaf Dark",  () -> setup(new FlatDarkLaf()));
        BUILTINS.put("FlatLaf Light", () -> setup(new FlatLightLaf()));
        BUILTINS.put("Darcula",       () -> setup(new FlatDarculaLaf()));
        BUILTINS.put("IntelliJ",      () -> setup(new FlatIntelliJLaf()));
        BUILTINS.put("One Dark",      () -> setup(new FlatOneDarkIJTheme()));
        BUILTINS.put("Dracula",       () -> setup(new FlatDraculaIJTheme()));
    }

    /** Represents one entry in the theme list (builtin or user-installed). */
    public record ThemeEntry(String name, boolean builtin, Path file) {}

    private ThemeManager() {}

    // ── Discovery ─────────────────────────────────────────────────────────────

    /** All themes: built-ins first, then user-installed sorted by name. */
    public static List<ThemeEntry> getAllThemes() {
        List<ThemeEntry> list = new ArrayList<>();
        BUILTINS.keySet().forEach(name -> list.add(new ThemeEntry(name, true, null)));
        Path dir = userThemesDir();
        if (Files.isDirectory(dir)) {
            try {
                Files.list(dir)
                        .filter(p -> p.getFileName().toString().endsWith(".properties"))
                        .sorted()
                        .forEach(p -> {
                            String name = p.getFileName().toString().replaceAll("\\.properties$", "");
                            list.add(new ThemeEntry(name, false, p));
                        });
            } catch (IOException e) {
                log.warn("Could not list user themes", e);
            }
        }
        return list;
    }

    /** Only user-installed (non-builtin) themes. */
    public static List<ThemeEntry> getUserThemes() {
        return getAllThemes().stream().filter(t -> !t.builtin()).toList();
    }

    // ── Apply ─────────────────────────────────────────────────────────────────

    /**
     * Applies the named theme. Looks up built-ins first, then user themes.
     * Falls back to {@link #DEFAULT_THEME} if the name is unknown.
     * Must be called on the EDT or before the UI is shown.
     */
    public static void applyTheme(String name) {
        Runnable builtin = BUILTINS.get(name);
        if (builtin != null) {
            builtin.run();
            return;
        }
        Path themeFile = userThemesDir().resolve(name + ".properties");
        if (Files.exists(themeFile)) {
            try {
                FlatLaf.setGlobalExtraDefaults(null);
                FlatLaf.setup(new FlatPropertiesLaf(name, themeFile.toFile()));
                applyNeutralOverrides();
                return;
            } catch (Exception e) {
                log.warn("Failed to apply user theme '{}', falling back to default", name, e);
            }
        }
        log.warn("Unknown theme '{}', applying default", name);
        setupClouseauDark();
    }

    /** Refreshes all open windows after a theme switch. Call on EDT only. */
    public static void updateUI() {
        FlatLaf.updateUI();
    }

    // ── Install / uninstall ───────────────────────────────────────────────────

    /**
     * Copies {@code source} into {@code ~/.clouseau/themes/} and returns the
     * resulting theme name (filename without {@code .properties}).
     */
    public static String installTheme(Path source) throws IOException {
        Path dir = userThemesDir();
        Files.createDirectories(dir);
        Path dest = dir.resolve(source.getFileName());
        Files.copy(source, dest, StandardCopyOption.REPLACE_EXISTING);
        return dest.getFileName().toString().replaceAll("\\.properties$", "");
    }

    /** Deletes a user theme's file from disk. No-op for built-in themes. */
    public static void uninstallTheme(ThemeEntry theme) throws IOException {
        if (theme.builtin() || theme.file() == null) return;
        Files.deleteIfExists(theme.file());
    }

    static Path userThemesDir() {
        return AppPrefs.PREFS_DIR.resolve("themes");
    }

    // ── Private setup helpers ─────────────────────────────────────────────────

    private static void setup(LookAndFeel laf) {
        FlatLaf.setGlobalExtraDefaults(null);
        clearClouseauDefaults();
        FlatLaf.setup(laf);
        applyNeutralOverrides();
    }

    /**
     * Removes all {@code Clouseau.*} entries from UIManager's user defaults.
     * Called before applying any non-Clouseau theme so stale Clouseau Dark
     * values don't leak into FlatLaf Light or other themes.
     */
    private static void clearClouseauDefaults() {
        UIManager.getDefaults().entrySet().stream()
                .map(Map.Entry::getKey)
                .filter(k -> k instanceof String s && s.startsWith("Clouseau."))
                .toList()
                .forEach(k -> UIManager.put(k, null));
    }

    private static void setupClouseauDark() {
        Properties props = loadThemeProperties("ClouseauDark.properties");

        // ── Resolve base LAF from the properties file ─────────────────────────
        LookAndFeel baseLaf = resolveBaseLaf(props.getProperty("@baseTheme", "FlatDarkLaf").trim());

        // ── Pass FlatLaf variables and standard UI overrides ──────────────────
        // Keys starting with '@' are LAF variables (except @baseTheme which is
        // a loader directive); all other non-Clouseau keys are standard FlatLaf
        // / Swing UI defaults.
        Map<String, String> extraDefaults = new LinkedHashMap<>();
        props.forEach((k, v) -> {
            String key = k.toString().trim();
            String val = v.toString().trim();
            if (!key.equals("@baseTheme") && !key.startsWith("Clouseau.") && !key.startsWith("#")) {
                extraDefaults.put(key, val);
            }
        });
        FlatLaf.setGlobalExtraDefaults(extraDefaults.isEmpty() ? null : extraDefaults);
        FlatLaf.setup(baseLaf);
        applyNeutralOverrides();

        // ── Apply Clouseau-specific custom UI keys for ClouseauColors ─────────
        props.forEach((k, v) -> {
            String key = k.toString().trim();
            String val = v.toString().trim();
            if (key.startsWith("Clouseau.")) {
                Color color = parseHexColor(val);
                if (color != null) UIManager.put(key, color);
            }
        });
    }

    private static LookAndFeel resolveBaseLaf(String name) {
        return switch (name) {
            case "FlatLightLaf"    -> new FlatLightLaf();
            case "FlatDarculaLaf"  -> new FlatDarculaLaf();
            case "FlatIntelliJLaf" -> new FlatIntelliJLaf();
            default                -> new FlatDarkLaf();
        };
    }

    /**
     * Loads a properties file from the same classpath package as {@link ThemeManager}.
     * Returns an empty {@link Properties} on failure so callers can always iterate safely.
     */
    private static Properties loadThemeProperties(String resourceName) {
        Properties props = new Properties();
        try (InputStream in = ThemeManager.class.getResourceAsStream(resourceName)) {
            if (in == null) {
                log.warn("Theme resource not found: {}", resourceName);
                return props;
            }
            props.load(in);
        } catch (IOException e) {
            log.warn("Failed to load theme properties: {}", resourceName, e);
        }
        return props;
    }

    /** Parses a {@code #rrggbb} or {@code #aarrggbb} string; returns {@code null} on failure. */
    private static Color parseHexColor(String value) {
        if (value == null || !value.startsWith("#")) return null;
        try {
            String hex = value.substring(1);
            if (hex.length() == 6) {
                return new Color(Integer.parseUnsignedInt(hex, 16));
            } else if (hex.length() == 8) {
                long argb = Long.parseUnsignedLong(hex, 16);
                return new Color((int)(argb >> 16 & 0xFF), (int)(argb >> 8 & 0xFF),
                        (int)(argb & 0xFF), (int)(argb >> 24 & 0xFF));
            }
        } catch (NumberFormatException ignored) {}
        return null;
    }

    /** Structural/layout overrides that apply regardless of which theme is active. */
    private static void applyNeutralOverrides() {
        UIManager.put("defaultFont",                  new FontUIResource(new Font(Font.SANS_SERIF, Font.PLAIN, 13)));
        UIManager.put("TabbedPane.showTabSeparators", true);
        UIManager.put("TabbedPane.tabArc",             10);
        UIManager.put("TabbedPane.underlineHeight",    2);
        UIManager.put("ScrollBar.thumbArc",           999);
        UIManager.put("Component.focusWidth",         0.5);
        UIManager.put("MenuBar.itemMargins",          new Insets(0, 8, 0, 8));
        UIManager.put("MenuBar.selectionArc",         8);
    }
}
