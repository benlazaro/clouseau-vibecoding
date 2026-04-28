package com.droidenx.clouseau.ui.theme;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;

import javax.swing.*;

/**
 * Custom FlatLaf Look and Feel for Clouseau Dark.
 * FlatLaf uses this class name to locate {@code ClouseauDarkLaf.properties}
 * in any package registered via {@code FlatLaf.registerCustomDefaultsSource()}.
 */
public final class ClouseauDarkLaf extends FlatDarkLaf {

    public static final String NAME = "Clouseau Dark";

    /**
     * Sets the application look and feel to this LaF
     * using {@link UIManager#setLookAndFeel(javax.swing.LookAndFeel)}.
     *
     * @since 1.2
     */
    public static boolean setup() {
        return setup( new ClouseauDarkLaf() );
    }

    /**
     * @deprecated use {@link #setup()} instead; this method will be removed in a future version
     */
    @Deprecated
    public static boolean install() {
        return setup();
    }

    /**
     * Adds this look and feel to the set of available look and feels.
     * <p>
     * Useful if your application uses {@link UIManager#getInstalledLookAndFeels()}
     * to query available LaFs and display them to the user in a combobox.
     */
    public static void installLafInfo() {
        installLafInfo( NAME, ClouseauDarkLaf.class );
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "Clouseau Dark Look and Feel";
    }

    @Override
    public boolean isDark() {
        return true;
    }

}
