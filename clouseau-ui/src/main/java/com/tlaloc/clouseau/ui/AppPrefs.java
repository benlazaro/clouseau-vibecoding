package com.tlaloc.clouseau.ui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Persistent user preferences stored as {@code ~/.clouseau/settings.json}.
 */
@Slf4j
public final class AppPrefs {

    static final Path PREFS_DIR  = Path.of(System.getProperty("user.home"), ".clouseau");
    private static final Path PREFS_FILE = PREFS_DIR.resolve("settings.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final String KEY_TAB_CLOSE_CONFIRM            = "tab.close.confirm";
    private static final String KEY_HIGHLIGHT_CLEAR_ALL_CONFIRM  = "highlight.clear.all.confirm";
    private static final String KEY_FOLLOW_BY_DEFAULT  = "follow.by.default";
    private static final String KEY_DETAIL_FONT_SIZE   = "detail.font.size";
    private static final String KEY_DETAIL_WRAP_LINES  = "detail.wrap.lines";
    private static final String KEY_ROW_HEIGHT         = "row.height";
    private static final String KEY_LAST_OPEN_DIR      = "last.open.dir";
    private static final String KEY_RECENT_FILES       = "recent.files";
    private static final String KEY_RECENT_MAX         = "recent.max";
    private static final String KEY_FAVORITES          = "favorites";
    private static final int    MAX_RECENT             = 10;

    private static final JsonObject ROOT = load();

    private AppPrefs() {}

    public static String getAppVersion() {
        try (java.io.InputStream is = AppPrefs.class.getResourceAsStream("/version.properties")) {
            if (is == null) return "dev";
            java.util.Properties props = new java.util.Properties();
            props.load(is);
            return props.getProperty("version", "dev");
        } catch (java.io.IOException e) {
            return "dev";
        }
    }

    // ── I/O ──────────────────────────────────────────────────────────────────

    private static JsonObject load() {
        if (Files.exists(PREFS_FILE)) {
            try (Reader r = Files.newBufferedReader(PREFS_FILE)) {
                JsonElement el = JsonParser.parseReader(r);
                if (el.isJsonObject()) return el.getAsJsonObject();
            } catch (IOException e) {
                log.warn("Could not read settings file {}", PREFS_FILE, e);
            }
        }
        return new JsonObject();
    }

    private static void save() {
        try {
            Files.createDirectories(PREFS_DIR);
            try (Writer w = Files.newBufferedWriter(PREFS_FILE)) {
                GSON.toJson(ROOT, w);
            }
        } catch (IOException e) {
            log.warn("Could not write settings file {}", PREFS_FILE, e);
        }
    }

    // ── Typed accessors ───────────────────────────────────────────────────────

    private static boolean getBool(String key, boolean def) {
        return ROOT.has(key) ? ROOT.get(key).getAsBoolean() : def;
    }

    private static void putBool(String key, boolean value) {
        ROOT.addProperty(key, value);
        save();
    }

    private static int getInt(String key, int def) {
        return ROOT.has(key) ? ROOT.get(key).getAsInt() : def;
    }

    private static void putInt(String key, int value) {
        ROOT.addProperty(key, value);
        save();
    }

    private static String getString(String key, String def) {
        return ROOT.has(key) ? ROOT.get(key).getAsString() : def;
    }

    private static void putString(String key, String value) {
        ROOT.addProperty(key, value);
        save();
    }

    private static List<String> getStringList(String key) {
        if (!ROOT.has(key)) return new ArrayList<>();
        JsonArray arr = ROOT.get(key).getAsJsonArray();
        List<String> list = new ArrayList<>(arr.size());
        arr.forEach(e -> list.add(e.getAsString()));
        return list;
    }

    private static void putStringList(String key, List<String> values) {
        JsonArray arr = new JsonArray();
        values.forEach(arr::add);
        ROOT.add(key, arr);
        save();
    }

    private static void remove(String key) {
        ROOT.remove(key);
        save();
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public static boolean isFollowByDefault() {
        return getBool(KEY_FOLLOW_BY_DEFAULT, true);
    }

    public static void setFollowByDefault(boolean value) {
        putBool(KEY_FOLLOW_BY_DEFAULT, value);
    }

    public static boolean isTabCloseConfirm() {
        return getBool(KEY_TAB_CLOSE_CONFIRM, true);
    }

    public static void setTabCloseConfirm(boolean value) {
        putBool(KEY_TAB_CLOSE_CONFIRM, value);
    }

    public static boolean isHighlightClearAllConfirm() {
        return getBool(KEY_HIGHLIGHT_CLEAR_ALL_CONFIRM, true);
    }

    public static void setHighlightClearAllConfirm(boolean value) {
        putBool(KEY_HIGHLIGHT_CLEAR_ALL_CONFIRM, value);
    }

    public static int getDetailFontSize() {
        return getInt(KEY_DETAIL_FONT_SIZE, 12);
    }

    public static void setDetailFontSize(int size) {
        putInt(KEY_DETAIL_FONT_SIZE, size);
    }

    public static boolean isDetailWrapLines() {
        return getBool(KEY_DETAIL_WRAP_LINES, true);
    }

    public static void setDetailWrapLines(boolean wrap) {
        putBool(KEY_DETAIL_WRAP_LINES, wrap);
    }

    public static int getRowHeight() {
        return getInt(KEY_ROW_HEIGHT, 22);
    }

    public static void setRowHeight(int height) {
        putInt(KEY_ROW_HEIGHT, height);
    }

    public static java.io.File getLastOpenDir() {
        String path = getString(KEY_LAST_OPEN_DIR, null);
        if (path == null) return null;
        java.io.File dir = new java.io.File(path);
        return dir.isDirectory() ? dir : null;
    }

    public static void setLastOpenDir(java.io.File dir) {
        if (dir != null) putString(KEY_LAST_OPEN_DIR, dir.getAbsolutePath());
    }

    public static int getRecentFilesMax() {
        return getInt(KEY_RECENT_MAX, 5);
    }

    public static void setRecentFilesMax(int max) {
        putInt(KEY_RECENT_MAX, Math.max(1, Math.min(MAX_RECENT, max)));
    }

    /** Returns up to {@link #getRecentFilesMax()} recently opened files that still exist on disk. */
    public static List<Path> getRecentFiles() {
        return getStringList(KEY_RECENT_FILES).stream()
                .filter(s -> !s.isBlank())
                .map(Path::of)
                .filter(Files::exists)
                .limit(getRecentFilesMax())
                .toList();
    }

    /** Inserts {@code path} at the front of the recent-files list, deduplicating and capping at {@value MAX_RECENT}. */
    public static void addRecentFile(Path path) {
        String abs = path.toAbsolutePath().toString();
        List<String> list = getStringList(KEY_RECENT_FILES);
        list.removeIf(s -> s.isBlank() || s.equals(abs));
        list.add(0, abs);
        if (list.size() > MAX_RECENT) list = list.subList(0, MAX_RECENT);
        putStringList(KEY_RECENT_FILES, list);
    }

    public static void removeRecentFile(Path path) {
        String abs = path.toAbsolutePath().toString();
        List<String> list = getStringList(KEY_RECENT_FILES);
        list.removeIf(s -> s.isBlank() || s.equals(abs));
        putStringList(KEY_RECENT_FILES, list);
    }

    public static void clearRecentFiles() {
        remove(KEY_RECENT_FILES);
    }

    /** Returns the saved favorite locations (directories or files) that still exist on disk. */
    public static List<Path> getFavorites() {
        return getStringList(KEY_FAVORITES).stream()
                .filter(s -> !s.isBlank())
                .map(Path::of)
                .filter(Files::exists)
                .toList();
    }

    public static void saveFavorites(List<Path> favorites) {
        putStringList(KEY_FAVORITES, favorites.stream().map(Path::toString).toList());
    }
}
