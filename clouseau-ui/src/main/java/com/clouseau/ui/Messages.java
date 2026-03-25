package com.clouseau.ui;

import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * Thin wrapper around {@link ResourceBundle} for UI string lookups.
 *
 * The active bundle is determined by the JVM default locale at startup.
 * To add a new locale, create messages_xx.properties alongside messages.properties.
 */
public final class Messages {

    private static final ResourceBundle BUNDLE =
        ResourceBundle.getBundle("com.clouseau.ui.messages");

    private Messages() {}

    /**
     * Returns the localized string for {@code key}.
     * Falls back to {@code "??key??"} rather than throwing, so a missing
     * translation never crashes the UI.
     */
    public static String get(String key) {
        try {
            return BUNDLE.getString(key);
        } catch (MissingResourceException e) {
            return "??" + key + "??";
        }
    }
}
