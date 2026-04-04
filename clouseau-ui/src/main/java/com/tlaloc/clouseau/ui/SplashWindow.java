package com.tlaloc.clouseau.ui;

import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

@Slf4j
public final class SplashWindow extends JWindow {

    private static final int    SVG_W          = 680;
    private static final int    SVG_H          = 300;
    private static final int    PAD            = 24;
    private static final int    STATUS_H       = 32;
    private static final Color  BG             = new Color(0x2c2c2c);
    private static final long   MIN_DISPLAY_MS = 1_500;

    private final long   shownAt = System.currentTimeMillis();
    private final JLabel statusLabel;

    private SplashWindow(BufferedImage img) {
        JPanel panel = new JPanel(null) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setColor(BG);
                g2.fillRect(0, 0, getWidth(), getHeight());
                if (img != null) {
                    g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                    g2.setRenderingHint(RenderingHints.KEY_RENDERING,     RenderingHints.VALUE_RENDER_QUALITY);
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,  RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.drawImage(img, PAD, PAD, SVG_W, SVG_H, null);
                }
                g2.dispose();
            }
        };

        int winW = SVG_W + PAD * 2;
        int winH = SVG_H + PAD * 2 + STATUS_H;
        panel.setPreferredSize(new Dimension(winW, winH));

        int statusY = SVG_H + PAD * 2;
        Font statusFont = UIManager.getFont("defaultFont") != null
                ? UIManager.getFont("defaultFont").deriveFont(Font.PLAIN, 11f)
                : new JLabel().getFont().deriveFont(Font.PLAIN, 11f);
        Color statusColor = new Color(0x888888);

        statusLabel = new JLabel("Initializing application...", SwingConstants.LEFT);
        statusLabel.setForeground(statusColor);
        statusLabel.setFont(statusFont);
        statusLabel.setOpaque(false);
        statusLabel.setBounds(PAD, statusY, winW / 2, STATUS_H);
        panel.add(statusLabel);

        JLabel versionLabel = new JLabel("v" + AppPrefs.getAppVersion(), SwingConstants.RIGHT);
        versionLabel.setForeground(statusColor);
        versionLabel.setFont(statusFont);
        versionLabel.setOpaque(false);
        versionLabel.setBounds(winW / 2, statusY, winW / 2 - PAD, STATUS_H);
        panel.add(versionLabel);

        setContentPane(panel);
        pack();
        setLocationRelativeTo(null);
    }

    /** Updates the status label. Safe to call from any thread. */
    public void setStatus(String text) {
        SwingUtilities.invokeLater(() -> statusLabel.setText(text));
    }

    /**
     * Renders the splash SVG on the calling thread (safe to call from the main thread),
     * then creates and shows the window on the EDT.  Returns {@code null} if the splash
     * cannot be shown (resource missing, Batik unavailable, etc.).
     */
    public static SplashWindow createAndShow() {
        BufferedImage img = renderSplashImage();
        SplashWindow[] ref = {null};
        try {
            SwingUtilities.invokeAndWait(() -> {
                ref[0] = new SplashWindow(img);
                ref[0].setVisible(true);
            });
        } catch (Exception e) {
            log.warn("Could not show splash screen", e);
        }
        return ref[0];
    }

    /**
     * Hides and disposes the window, but waits until at least {@code MIN_DISPLAY_MS}
     * have elapsed since the splash was shown, then runs {@code onClosed}.
     * Must be called on the EDT.
     */
    public void close(Runnable onClosed) {
        long remaining = MIN_DISPLAY_MS - (System.currentTimeMillis() - shownAt);
        if (remaining <= 0) {
            setVisible(false);
            dispose();
            if (onClosed != null) onClosed.run();
        } else {
            Timer t = new Timer((int) remaining, e -> {
                setVisible(false);
                dispose();
                if (onClosed != null) onClosed.run();
            });
            t.setRepeats(false);
            t.start();
        }
    }

    // ── SVG rasterization ─────────────────────────────────────────────────────

    private static BufferedImage renderSplashImage() {
        var url = SplashWindow.class.getResource(
                "/com/tlaloc/clouseau/ui/images/clouseau_splash.svg");
        if (url == null) {
            log.warn("Splash SVG resource not found");
            return null;
        }
        try {
            // Render at 4× so small text (font-size 11) has enough pixels for clean
            // antialiasing; paintComponent downscales to display size via bicubic.
            int renderW = SVG_W * 4;
            int renderH = SVG_H * 4;
            var transcoder = new org.apache.batik.transcoder.image.PNGTranscoder();
            transcoder.addTranscodingHint(
                    org.apache.batik.transcoder.SVGAbstractTranscoder.KEY_WIDTH,  (float) renderW);
            transcoder.addTranscodingHint(
                    org.apache.batik.transcoder.SVGAbstractTranscoder.KEY_HEIGHT, (float) renderH);
            var baos = new ByteArrayOutputStream();
            transcoder.transcode(
                    new org.apache.batik.transcoder.TranscoderInput(url.toString()),
                    new org.apache.batik.transcoder.TranscoderOutput(baos));

            return ImageIO.read(new ByteArrayInputStream(baos.toByteArray()));
        } catch (Exception e) {
            log.warn("Could not rasterize splash SVG", e);
            return null;
        }
    }
}
