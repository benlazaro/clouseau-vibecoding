package com.tlaloc.clouseau.ui;

import com.tlaloc.clouseau.api.LogColorizer;
import com.tlaloc.clouseau.api.LogFormatter;
import com.tlaloc.clouseau.api.LogParser;
import com.tlaloc.clouseau.runtime.ClouseauPluginManager;
import lombok.extern.slf4j.Slf4j;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.plaf.basic.BasicFileChooserUI;
import java.awt.*;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ColorConvertOp;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

@Slf4j
public final class MainFrame extends JFrame {

    private final ClouseauPluginManager pluginManager;
    private List<LogParser> parsers;
    private List<LogFormatter> formatters;
    private List<LogColorizer> colorizers;
    private final JTabbedPane tabbedPane = new JTabbedPane();
    private final CardLayout cardLayout  = new CardLayout();
    private final JPanel contentArea     = new JPanel(cardLayout);
    private int lastParserIndex = 0;
    private JMenuItem closeTabItem;
    private JMenu recentMenu;

    private static final String CARD_WELCOME = "welcome";
    private static final String CARD_TABS    = "tabs";

    public MainFrame(ClouseauPluginManager pluginManager) {
        super(Messages.get("app.title"));
        this.pluginManager = pluginManager;
        this.parsers = new ArrayList<>(UserParsersLoader.load());
        this.formatters = new ArrayList<>();
        this.formatters.add(new JsonMessageFormatter());
        this.formatters.addAll(pluginManager.getExtensions(LogFormatter.class));
        log.info("Loaded {} formatter(s): {}", formatters.size(),
                formatters.stream().map(LogFormatter::getName).toList());

        this.colorizers = new ArrayList<>();
        this.colorizers.add(new JsonColorizer());
        this.colorizers.addAll(pluginManager.getExtensions(LogColorizer.class));
        log.info("Loaded {} colorizer(s): {}", colorizers.size(),
                colorizers.stream().map(LogColorizer::getName).toList());
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1280, 800);
        setMinimumSize(new Dimension(800, 500));
        setLocationRelativeTo(null);
        setIconImages(loadAppIcons());

        contentArea.add(buildWelcomePanel(), CARD_WELCOME);
        contentArea.add(tabbedPane,          CARD_TABS);

        setLayout(new MigLayout("fill, insets 0", "[grow]", "[grow]"));
        setJMenuBar(buildMenuBar());
        add(contentArea, "grow");

        // VK-based binding drives the actual Ctrl+, shortcut.
        // The menu item uses a char-based KeyStroke so it renders "," not "Comma".
        KeyStroke settingsKs = KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_COMMA,
                java.awt.Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx());
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(settingsKs, "openSettings");
        getRootPane().getActionMap().put("openSettings", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { openSettings(); }
        });
    }

    // ── Parser list management ────────────────────────────────────────────────

    /** Called after parsers.json is saved to reload user-defined parsers. */
    void reloadParsers() {
        this.parsers = new ArrayList<>(UserParsersLoader.load());
        if (lastParserIndex > parsers.size()) lastParserIndex = 0;
    }

    // ── Menu bar ─────────────────────────────────────────────────────────────

    private JMenuBar buildMenuBar() {
        JMenuBar menuBar = new JMenuBar();
        menuBar.setBorder(BorderFactory.createEmptyBorder(5, 1, 2, 0));

        JMenu fileMenu = new JMenu(Messages.get("menu.file"));

        JMenuItem openItem = menuItem(Messages.get("menu.file.open"));
        openItem.setAccelerator(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_O,
                java.awt.Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
        openItem.addActionListener(e -> openFile());
        fileMenu.add(openItem);

        recentMenu = new JMenu(Messages.get("menu.file.recent"));
        Insets menuDefaults = UIManager.getInsets("Menu.margin");
        int mh = menuDefaults != null ? menuDefaults.left : 0;
        recentMenu.setMargin(new Insets(7, mh, 7, mh));
        fileMenu.add(recentMenu);
        refreshRecentMenu();

        closeTabItem = menuItem(Messages.get("menu.file.close.tab"));
        closeTabItem.setEnabled(false);
        closeTabItem.setAccelerator(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_W,
                java.awt.Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
        closeTabItem.addActionListener(e -> closeTab(tabbedPane.getSelectedIndex()));
        fileMenu.add(closeTabItem);

        fileMenu.addSeparator();

        // KeyStroke.getKeyStroke(char, int) casts char to int → ',' (44) == VK_COMMA,
        // so getKeyText renders "Comma". The string form with "typed" creates a true
        // char-based stroke (keyCode=0, keyChar=',') that renders the literal character.
        // The actual Ctrl+, binding lives in the root-pane InputMap in the constructor.
        int shortcutMask = java.awt.Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        String modName = (shortcutMask & java.awt.event.InputEvent.META_DOWN_MASK) != 0 ? "meta" : "ctrl";
        JMenuItem settingsItem = menuItem(Messages.get("menu.file.settings"));
        settingsItem.setAccelerator(KeyStroke.getKeyStroke(modName + " typed ,"));
        settingsItem.addActionListener(e -> openSettings());
        fileMenu.add(settingsItem);

        fileMenu.addSeparator();

        JMenuItem exitItem = menuItem(Messages.get("menu.file.exit"));
        exitItem.addActionListener(e -> dispose());
        fileMenu.add(exitItem);

        menuBar.add(fileMenu);

        JMenu toolsMenu = new JMenu(Messages.get("menu.tools"));
        JMenuItem parsersItem = menuItem(Messages.get("menu.tools.parsers"));
        parsersItem.addActionListener(e ->
                new ParserEditorDialog(this, this::reloadParsers).setVisible(true));
        toolsMenu.add(parsersItem);
        JMenuItem pluginsItem = menuItem(Messages.get("menu.tools.plugins"));
        pluginsItem.setToolTipText(Messages.get("toolbar.plugins.tooltip"));
        pluginsItem.addActionListener(e ->
                new PluginManagerDialog(this, pluginManager, () -> {}).setVisible(true));
        toolsMenu.add(pluginsItem);
        menuBar.add(toolsMenu);

        JMenu helpMenu = new JMenu(Messages.get("menu.help"));

        JMenuItem docsItem = menuItem(Messages.get("menu.help.docs"));
        docsItem.setEnabled(false);
        helpMenu.add(docsItem);

        helpMenu.addSeparator();

        JMenuItem aboutItem = menuItem(Messages.get("menu.help.about"));
        aboutItem.addActionListener(e -> openAbout());
        helpMenu.add(aboutItem);

        menuBar.add(helpMenu);
        return menuBar;
    }

    private void openAbout() {
        BufferedImage icon64 = rasterizeSvg(
                MainFrame.class.getResource("/com/tlaloc/clouseau/ui/icons/clouseau.svg").toString(), 64, 1.15f);
        JOptionPane.showMessageDialog(
                this,
                "<html><b>" + Messages.get("about.version") + "</b><br><br>"
                        + Messages.get("about.description") + "</html>",
                Messages.get("about.title"),
                JOptionPane.INFORMATION_MESSAGE,
                icon64 != null ? new ImageIcon(icon64) : null);
    }

    /** Creates a {@link JMenuItem} with VSCode-style vertical padding pre-applied. */
    private static JMenuItem menuItem(String text) {
        JMenuItem item = new JMenuItem(text);
        item.setMargin(new Insets(7, 0, 7, 0));
        return item;
    }

    private void openSettings() {
        new SettingsDialog(this, activeLogPanel(), this::refreshRecentMenu).setVisible(true);
    }

    private void refreshRecentMenu() {
        recentMenu.removeAll();
        java.util.List<java.nio.file.Path> recents = AppPrefs.getRecentFiles();
        recentMenu.setEnabled(!recents.isEmpty());
        for (java.nio.file.Path p : recents) {
            JMenuItem item = menuItem(p.getFileName().toString());
            item.setToolTipText(p.toAbsolutePath().toString());
            item.addActionListener(e -> openFile(p));
            recentMenu.add(item);
        }
        if (!recents.isEmpty()) {
            recentMenu.addSeparator();
            JMenuItem clear = menuItem(Messages.get("menu.file.recent.clear"));
            clear.addActionListener(e -> { AppPrefs.clearRecentFiles(); refreshRecentMenu(); });
            recentMenu.add(clear);
        }
    }

    // ── File opening ──────────────────────────────────────────────────────────

    private void openFile() {
        JFileChooser chooser = new JFileChooser() {
            @Override
            public void approveSelection() {
                File f = getSelectedFile();
                if (f != null && f.isFile()) {
                    Optional<LogParser> chosen = lastParserIndex == 0
                            ? Optional.empty()
                            : Optional.of(parsers.get(lastParserIndex - 1));
                    if (detectParser(f.toPath(), chosen).isEmpty()) return;
                }
                super.approveSelection();
            }
        };
        chooser.setDialogTitle(Messages.get("filechooser.title"));
        chooser.setFileFilter(new FileNameExtensionFilter(
                Messages.get("filechooser.filter.desc"), "log", "txt", "out", "gz", "zip"));
        chooser.setAcceptAllFileFilterUsed(true);
        chooser.setAccessory(buildParserAccessory(chooser));
        File lastDir = AppPrefs.getLastOpenDir();
        if (lastDir != null) chooser.setCurrentDirectory(lastDir);

        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;

        AppPrefs.setLastOpenDir(chooser.getCurrentDirectory());
        Path file = chooser.getSelectedFile().toPath();
        Optional<LogParser> chosen = lastParserIndex == 0
                ? Optional.empty()
                : Optional.of(parsers.get(lastParserIndex - 1));
        log.info("Opening {} with parser: {}", file.getFileName(),
                chosen.map(LogParser::getName).orElse("Auto-detect"));
        openFile(file, chosen);
    }

    /** Opens {@code file} in a new tab with auto-detect parser. Called via IPC or CLI args. */
    public void openFile(Path file) {
        if (parsers.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    Messages.get("error.no.parsers.message"),
                    Messages.get("error.no.parsers.title"),
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        openFile(file, Optional.empty());
    }

    private void openFile(Path file, Optional<LogParser> parser) {
        Path abs = file.toAbsolutePath();
        for (int i = 0; i < tabbedPane.getTabCount(); i++) {
            if (tabbedPane.getComponentAt(i) instanceof LogPanel lp && abs.equals(lp.getFile())) {
                tabbedPane.setSelectedIndex(i);
                return;
            }
        }
        AppPrefs.addRecentFile(file);
        refreshRecentMenu();
        LogPanel panel = new LogPanel(parsers, formatters, colorizers);
        addLogTab(file.getFileName().toString(), panel);
        panel.load(file, parser, () -> {
            AppPrefs.removeRecentFile(file);
            refreshRecentMenu();
            int idx = tabbedPane.indexOfComponent(panel);
            if (idx >= 0) {
                panel.dispose();
                tabbedPane.removeTabAt(idx);
                updateCard();
            }
        });
    }

    // ── Tab management ────────────────────────────────────────────────────────

    private void addLogTab(String title, LogPanel panel) {
        tabbedPane.addTab(null, panel);
        int idx = tabbedPane.getTabCount() - 1;
        tabbedPane.setTabComponentAt(idx, buildTabHeader(title, panel));
        tabbedPane.setSelectedIndex(idx);
        updateCard();
    }

    private JPanel buildTabHeader(String title, LogPanel panel) {
        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        header.setOpaque(false);

        JLabel label = new JLabel(title);

        JButton close = new JButton("\u00d7"); // ×
        close.setPreferredSize(new Dimension(18, 18));
        close.setFocusable(false);
        close.setBorderPainted(false);
        close.setContentAreaFilled(false);
        close.setFont(close.getFont().deriveFont(Font.BOLD));
        close.addActionListener(e -> closeTab(tabbedPane.indexOfTabComponent(header)));

        header.add(label);
        header.add(close);
        return header;
    }

    private void closeTab(int idx) {
        if (idx < 0 || idx >= tabbedPane.getTabCount()) return;
        if (AppPrefs.isTabCloseConfirm()) {
            JCheckBox dontAsk = new JCheckBox(Messages.get("tab.close.dontask"));
            int result = JOptionPane.showConfirmDialog(
                    this,
                    new Object[]{Messages.get("tab.close.confirm.message"), dontAsk},
                    Messages.get("tab.close.confirm.title"),
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.QUESTION_MESSAGE);
            if (dontAsk.isSelected()) AppPrefs.setTabCloseConfirm(false);
            if (result != JOptionPane.YES_OPTION) return;
        }
        Component comp = tabbedPane.getComponentAt(idx);
        if (comp instanceof LogPanel lp) lp.dispose();
        tabbedPane.removeTabAt(idx);
        updateCard();
    }

    private void updateCard() {
        boolean hasTabs = tabbedPane.getTabCount() > 0;
        cardLayout.show(contentArea, hasTabs ? CARD_TABS : CARD_WELCOME);
        if (closeTabItem != null) closeTabItem.setEnabled(hasTabs);
    }

    private LogPanel activeLogPanel() {
        Component c = tabbedPane.getSelectedComponent();
        return c instanceof LogPanel lp ? lp : null;
    }

    // ── Welcome panel ─────────────────────────────────────────────────────────

    private JPanel buildWelcomePanel() {
        JPanel outer = new JPanel(new GridBagLayout());

        JPanel content = new JPanel(new MigLayout("insets 0, wrap 1, gapy 0", "[center]"));
        content.setOpaque(false);

        BufferedImage watermark = renderSvgWatermark(256);
        if (watermark != null) {
            content.add(new JLabel(new ImageIcon(watermark)), "gapbottom 28");
        }

        JPanel grid = new JPanel(new MigLayout("insets 0, wrap 2, gapy 10, gapx 24", "[right][left]"));
        grid.setOpaque(false);
        addShortcutRow(grid, Messages.get("welcome.shortcut.open"),      "Ctrl+O");
        addShortcutRow(grid, Messages.get("welcome.shortcut.close.tab"), "Ctrl+W");
        addShortcutRow(grid, Messages.get("welcome.shortcut.settings"),  "Ctrl+,");
        content.add(grid);

        outer.add(content);
        return outer;
    }

    /**
     * Adds one shortcut row to {@code grid}.
     * The shortcut string is split on {@code "+"} so each token gets its own
     * rounded key badge; the {@code "+"} separators are plain (unboxed) labels.
     */
    private static void addShortcutRow(JPanel grid, String description, String shortcut) {
        grid.add(new JLabel(description));

        JPanel badgeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        badgeRow.setOpaque(false);

        String[] parts = shortcut.split("\\+", -1);
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                JLabel plus = new JLabel("+");
                plus.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 5));
                badgeRow.add(plus);
            }
            badgeRow.add(keyBadge(parts[i]));
        }
        grid.add(badgeRow);
    }

    /** A rounded key-chip label, styled like a keyboard key. */
    private static JComponent keyBadge(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

        JPanel badge = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(0x2D2F38));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                g2.setColor(new Color(0x555566));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
                g2.dispose();
            }
        };
        badge.setOpaque(false);
        badge.setBorder(BorderFactory.createEmptyBorder(2, 7, 2, 7));
        badge.add(label);
        return badge;
    }

    // ── Parser detection + file-chooser accessory ────────────────────────────

    private Optional<LogParser> detectParser(Path file, Optional<LogParser> chosen) {
        try (BufferedReader reader = LogPanel.openReader(file)) {
            String line;
            int checked = 0;
            while ((line = reader.readLine()) != null && checked < 50) {
                if (line.isBlank()) continue;
                checked++;
                final String candidate = line;
                Optional<LogParser> match = chosen
                        .map(Stream::of)
                        .orElseGet(parsers::stream)
                        .filter(p -> p.canParse(candidate))
                        .findFirst();
                if (match.isPresent()) return match;
            }
        } catch (IOException e) {
            log.warn("Could not validate file {} — proceeding anyway", file, e);
            return chosen.or(() -> parsers.stream().findFirst());
        }
        return Optional.empty();
    }

    private JPanel buildParserAccessory(JFileChooser chooser) {
        String[] items = Stream.concat(
                Stream.of(Messages.get("filechooser.parser.autodetect")),
                parsers.stream().map(LogParser::getName)
        ).toArray(String[]::new);

        JComboBox<String> combo = new JComboBox<>(items);
        combo.setSelectedIndex(lastParserIndex);

        JLabel statusLabel = new JLabel(" ");
        statusLabel.setFont(statusLabel.getFont().deriveFont(11f));

        Runnable validate = () -> {
            File selected = chooser.getSelectedFile();
            if (selected == null || !selected.isFile()) {
                statusLabel.setText(" ");
                findApproveButton(chooser).ifPresent(b -> b.setEnabled(true));
                return;
            }
            int idx = combo.getSelectedIndex();
            Optional<LogParser> chosen = idx == 0
                    ? Optional.empty()
                    : Optional.of(parsers.get(idx - 1));
            Optional<LogParser> matched = detectParser(selected.toPath(), chosen);
            if (matched.isPresent()) {
                statusLabel.setForeground(new Color(0x4CAF50));
                String statusText = chosen.isPresent()
                        ? Messages.get("filechooser.status.compatible")
                        : Messages.get("filechooser.status.compatible.detected").formatted(matched.get().getName());
                statusLabel.setText(statusText);
            } else {
                statusLabel.setForeground(new Color(0xE57373));
                String name = chosen.map(LogParser::getName)
                        .orElse(Messages.get("filechooser.parser.autodetect"));
                statusLabel.setText(Messages.get("filechooser.status.incompatible").formatted(name));
            }
            findApproveButton(chooser).ifPresent(b -> b.setEnabled(matched.isPresent()));
        };

        combo.addActionListener(e -> {
            lastParserIndex = combo.getSelectedIndex();
            validate.run();
        });
        chooser.addPropertyChangeListener(JFileChooser.SELECTED_FILE_CHANGED_PROPERTY,
                e -> validate.run());
        chooser.addPropertyChangeListener(JFileChooser.DIRECTORY_CHANGED_PROPERTY, e -> {
            chooser.setSelectedFile(null);
            if (chooser.getUI() instanceof BasicFileChooserUI basicUI) {
                basicUI.setFileName("");
            }
            SwingUtilities.invokeLater(validate);
        });

        JPanel panel = new JPanel(new MigLayout("insets 8, wrap 1", "[160px!]"));
        panel.add(new JLabel(Messages.get("filechooser.parser.label")));
        panel.add(combo, "growx");
        panel.add(statusLabel, "growx, gaptop 6");
        return panel;
    }

    private static Optional<JButton> findApproveButton(JFileChooser chooser) {
        String text = UIManager.getString("FileChooser.openButtonText");
        return findButtonByText(chooser, text);
    }

    private static Optional<JButton> findButtonByText(Container container, String text) {
        for (Component c : container.getComponents()) {
            if (c instanceof JButton b && text != null && text.equals(b.getText())) {
                return Optional.of(b);
            }
            if (c instanceof Container sub) {
                Optional<JButton> found = findButtonByText(sub, text);
                if (found.isPresent()) return found;
            }
        }
        return Optional.empty();
    }

    private static BufferedImage renderSvgWatermark(int size) {
        var svgUrl = MainFrame.class.getResource("/com/tlaloc/clouseau/ui/icons/clouseau.svg");
        if (svgUrl == null) return null;
        try {
            // rasterize at 2x for crisp rendering on HiDPI displays
            int renderSize = size * 2;
            var transcoder = new org.apache.batik.transcoder.image.PNGTranscoder();
            transcoder.addTranscodingHint(org.apache.batik.transcoder.SVGAbstractTranscoder.KEY_WIDTH, (float) renderSize);
            transcoder.addTranscodingHint(org.apache.batik.transcoder.SVGAbstractTranscoder.KEY_HEIGHT, (float) renderSize);
            var output = new java.io.ByteArrayOutputStream();
            transcoder.transcode(new org.apache.batik.transcoder.TranscoderInput(svgUrl.toString()),
                                 new org.apache.batik.transcoder.TranscoderOutput(output));
            BufferedImage hi = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(output.toByteArray()));

            // scale down smoothly to target size
            BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = img.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.drawImage(hi, 0, 0, size, size, null);
            g2.dispose();

            new ColorConvertOp(ColorSpace.getInstance(ColorSpace.CS_GRAY), null).filter(img, img);
            for (int y = 0; y < img.getHeight(); y++) {
                for (int x = 0; x < img.getWidth(); x++) {
                    int argb = img.getRGB(x, y);
                    int alpha = (argb >>> 24) & 0xFF;
                    img.setRGB(x, y, (int)(alpha * 0.40) << 24 | (argb & 0x00FFFFFF));
                }
            }
            return img;
        } catch (Exception e) {
            log.warn("Could not render SVG watermark", e);
            return null;
        }
    }

    private static BufferedImage rasterizeSvg(String svgUri, int size) {
        return rasterizeSvg(svgUri, size, 1.0f);
    }

    private static BufferedImage rasterizeSvg(String svgUri, int size, float zoom) {
        try {
            int renderSize = Math.round(size * zoom);
            var transcoder = new org.apache.batik.transcoder.image.PNGTranscoder();
            transcoder.addTranscodingHint(org.apache.batik.transcoder.SVGAbstractTranscoder.KEY_WIDTH, (float) renderSize);
            transcoder.addTranscodingHint(org.apache.batik.transcoder.SVGAbstractTranscoder.KEY_HEIGHT, (float) renderSize);
            var output = new java.io.ByteArrayOutputStream();
            transcoder.transcode(new org.apache.batik.transcoder.TranscoderInput(svgUri),
                                 new org.apache.batik.transcoder.TranscoderOutput(output));
            BufferedImage rendered = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(output.toByteArray()));
            if (zoom == 1.0f) return rendered;
            // center-crop back to target size
            int offset = (renderSize - size) / 2;
            BufferedImage cropped = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = cropped.createGraphics();
            g2.drawImage(rendered, 0, 0, size, size, offset, offset, offset + size, offset + size, null);
            g2.dispose();
            return cropped;
        } catch (Exception e) {
            log.warn("Could not rasterize SVG at size {}", size, e);
            return null;
        }
    }

    private static List<Image> loadAppIcons() {
        var svgUrl = MainFrame.class.getResource("/com/tlaloc/clouseau/ui/icons/clouseau.svg");
        if (svgUrl == null) return List.of();
        return Stream.of(256, 64, 16)
                .map(size -> rasterizeSvg(svgUrl.toString(), size, 1.19f))
                .filter(Objects::nonNull)
                .map(img -> (Image) img)
                .toList();
    }
}
