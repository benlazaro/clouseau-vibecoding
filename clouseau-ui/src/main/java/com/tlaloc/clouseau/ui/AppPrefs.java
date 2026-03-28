package com.tlaloc.clouseau.ui;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * Thin wrapper around {@link Preferences} for typed, centrally-keyed access
 * to user preferences that persist across sessions.
 */
public final class AppPrefs {

    private static final Preferences PREFS = Preferences.userRoot().node("com/tlaloc");

    private static final String KEY_TAB_CLOSE_CONFIRM = "tab.close.confirm";
    private static final String KEY_FOLLOW_BY_DEFAULT  = "follow.by.default";
    private static final String KEY_DETAIL_FONT_SIZE  = "detail.font.size";
    private static final String KEY_LAST_OPEN_DIR     = "last.open.dir";
    private static final String KEY_RECENT_FILES      = "recent.files";
    private static final String KEY_RECENT_MAX        = "recent.max";
    private static final int    MAX_RECENT            = 10;
    private static final String RECENT_SEP            = "\n";

    private AppPrefs() {}

    public static boolean isFollowByDefault() {
        return PREFS.getBoolean(KEY_FOLLOW_BY_DEFAULT, true);
    }

    public static void setFollowByDefault(boolean value) {
        PREFS.putBoolean(KEY_FOLLOW_BY_DEFAULT, value);
    }

    public static boolean isTabCloseConfirm() {
        return PREFS.getBoolean(KEY_TAB_CLOSE_CONFIRM, true);
    }

    public static void setTabCloseConfirm(boolean value) {
        PREFS.putBoolean(KEY_TAB_CLOSE_CONFIRM, value);
    }

    public static int getDetailFontSize() {
        return PREFS.getInt(KEY_DETAIL_FONT_SIZE, 12);
    }

    public static void setDetailFontSize(int size) {
        PREFS.putInt(KEY_DETAIL_FONT_SIZE, size);
    }

    public static java.io.File getLastOpenDir() {
        String path = PREFS.get(KEY_LAST_OPEN_DIR, null);
        if (path == null) return null;
        java.io.File dir = new java.io.File(path);
        return dir.isDirectory() ? dir : null;
    }

    public static void setLastOpenDir(java.io.File dir) {
        if (dir != null) PREFS.put(KEY_LAST_OPEN_DIR, dir.getAbsolutePath());
    }

    public static int getRecentFilesMax() {
        return PREFS.getInt(KEY_RECENT_MAX, 5);
    }

    public static void setRecentFilesMax(int max) {
        PREFS.putInt(KEY_RECENT_MAX, Math.max(1, Math.min(MAX_RECENT, max)));
    }

    /** Returns up to {@link #getRecentFilesMax()} recently opened files that still exist on disk. */
    public static List<Path> getRecentFiles() {
        String stored = PREFS.get(KEY_RECENT_FILES, "");
        if (stored.isBlank()) return List.of();
        return Arrays.stream(stored.split(RECENT_SEP))
                .filter(s -> !s.isBlank())
                .map(Path::of)
                .filter(Files::exists)
                .limit(getRecentFilesMax())
                .toList();
    }

    /** Inserts {@code path} at the front of the recent-files list, deduplicating and capping at {@value MAX_RECENT}. */
    public static void addRecentFile(Path path) {
        List<String> list = new ArrayList<>(
                Arrays.asList(PREFS.get(KEY_RECENT_FILES, "").split(RECENT_SEP)));
        String abs = path.toAbsolutePath().toString();
        list.removeIf(s -> s.isBlank() || s.equals(abs));
        list.add(0, abs);
        if (list.size() > MAX_RECENT) list = list.subList(0, MAX_RECENT);
        PREFS.put(KEY_RECENT_FILES, String.join(RECENT_SEP, list));
    }

    public static void clearRecentFiles() {
        PREFS.remove(KEY_RECENT_FILES);
    }
}
