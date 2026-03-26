package com.clouseau.ui;

import java.util.prefs.Preferences;

/**
 * Thin wrapper around {@link Preferences} for typed, centrally-keyed access
 * to user preferences that persist across sessions.
 */
public final class AppPrefs {

    private static final Preferences PREFS = Preferences.userRoot().node("com/clouseau");

    private static final String KEY_TAB_CLOSE_CONFIRM = "tab.close.confirm";
    private static final String KEY_DETAIL_FONT_SIZE  = "detail.font.size";

    private AppPrefs() {}

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
}
