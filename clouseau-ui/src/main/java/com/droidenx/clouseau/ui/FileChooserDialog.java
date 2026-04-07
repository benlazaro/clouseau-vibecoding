package com.droidenx.clouseau.ui;

import com.droidenx.clouseau.api.LogParser;
import lombok.extern.slf4j.Slf4j;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.filechooser.FileSystemView;
import javax.swing.plaf.basic.BasicFileChooserUI;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Custom file-chooser dialog with a favorites sidebar, file preview, and parser picker.
 * Use {@link #showDialog()} to display it; returns the chosen file and parser on approval.
 */
@Slf4j
final class FileChooserDialog extends JDialog {

    record Result(Path file, Optional<LogParser> parser, int parserIndex) {}

    private final List<LogParser> parsers;
    private final JFileChooser chooser = new JFileChooser();
    private final DefaultListModel<Path> favoritesModel = new DefaultListModel<>();
    private int selectedParserIndex;
    private JButton openBtn;
    private Result result;

    FileChooserDialog(Frame owner, List<LogParser> parsers, int initialParserIndex) {
        super(owner, Messages.get("filechooser.title"), true);
        this.parsers = parsers;
        this.selectedParserIndex = initialParserIndex;

        AppPrefs.getFavorites().forEach(favoritesModel::addElement);

        chooser.setFileFilter(new FileNameExtensionFilter(
                Messages.get("filechooser.filter.desc"), "log", "txt", "out", "gz", "zip"));
        chooser.setAcceptAllFileFilterUsed(true);
        chooser.setControlButtonsAreShown(false);
        File lastDir = AppPrefs.getLastOpenDir();
        if (lastDir != null) chooser.setCurrentDirectory(lastDir);

        // Switch to details view
        Action detailsView = chooser.getActionMap().get("viewTypeDetails");
        if (detailsView != null)
            detailsView.actionPerformed(new ActionEvent(chooser, ActionEvent.ACTION_PERFORMED, null));

        // Approve/cancel fired by double-click, Enter, or our own buttons
        chooser.addActionListener(e -> {
            if (JFileChooser.APPROVE_SELECTION.equals(e.getActionCommand())) doApprove();
            else if (JFileChooser.CANCEL_SELECTION.equals(e.getActionCommand())) dispose();
        });

        JSplitPane leftSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                buildFavoritesPanel(), chooser);
        leftSplit.setDividerLocation(180);
        leftSplit.setBorder(null);

        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                leftSplit, buildParserAccessory());
        mainSplit.setResizeWeight(1.0); // chooser absorbs resize, accessory stays fixed
        mainSplit.setBorder(null);
        // Set right divider after first layout when widths are known
        mainSplit.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override public void componentResized(java.awt.event.ComponentEvent e) {
                mainSplit.removeComponentListener(this);
                mainSplit.setDividerLocation(mainSplit.getWidth() - 280 - mainSplit.getDividerSize());
            }
        });

        JPanel content = new JPanel(new BorderLayout(0, 0));
        content.add(mainSplit,        BorderLayout.CENTER);
        content.add(buildButtonBar(), BorderLayout.SOUTH);
        setContentPane(content);

        setPreferredSize(new Dimension(1100, 650));
        pack();
        setLocationRelativeTo(owner);
    }

    /** Shows the dialog modally and returns the result, or empty if cancelled. */
    Optional<Result> showDialog() {
        setVisible(true); // blocks until disposed
        return Optional.ofNullable(result);
    }

    private void doApprove() {
        File f = chooser.getSelectedFile();
        if (f == null || !f.isFile()) return;
        Optional<LogParser> chosen = selectedParserIndex == 0
                ? Optional.empty()
                : Optional.of(parsers.get(selectedParserIndex - 1));
        result = new Result(f.toPath(), chosen, selectedParserIndex);
        AppPrefs.setLastOpenDir(chooser.getCurrentDirectory());
        dispose();
    }

    // ── Favorites sidebar ─────────────────────────────────────────────────────

    private JPanel buildFavoritesPanel() {
        JList<Path> list = new JList<>(favoritesModel);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setFixedCellHeight(28);
        FileSystemView fsv = FileSystemView.getFileSystemView();
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(
                    JList<?> l, Object v, int i, boolean sel, boolean foc) {
                super.getListCellRendererComponent(l, v, i, sel, foc);
                Path p = (Path) v;
                setText(p.getFileName() != null ? p.getFileName().toString() : p.toString());
                setToolTipText(p.toString());
                setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
                File f = p.toFile();
                setIcon(f.exists() ? fsv.getSystemIcon(f) : null);
                return this;
            }
        });
        list.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() != 1) return;
                Path fav = list.getSelectedValue();
                if (fav == null) return;
                if (Files.isDirectory(fav)) {
                    chooser.setCurrentDirectory(fav.toFile());
                } else if (fav.getParent() != null && Files.isDirectory(fav.getParent())) {
                    chooser.setCurrentDirectory(fav.getParent().toFile());
                    chooser.setSelectedFile(fav.toFile());
                }
            }
        });

        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(BorderFactory.createEmptyBorder());

        JButton addBtn = new JButton("+");
        addBtn.setToolTipText(Messages.get("filechooser.favorites.add.tooltip"));
        addBtn.setMargin(new Insets(2, 8, 2, 8));
        addBtn.addActionListener(e -> {
            File dir = chooser.getCurrentDirectory();
            if (dir == null) return;
            Path p = dir.toPath().toAbsolutePath();
            if (!favoritesModel.contains(p)) {
                favoritesModel.addElement(p);
                saveFavorites();
            }
        });

        JButton removeBtn = new JButton("\u2212");
        removeBtn.setToolTipText(Messages.get("filechooser.favorites.remove.tooltip"));
        removeBtn.setMargin(new Insets(2, 8, 2, 8));
        removeBtn.setEnabled(false);
        list.addListSelectionListener(e -> removeBtn.setEnabled(list.getSelectedValue() != null));
        removeBtn.addActionListener(e -> {
            int idx = list.getSelectedIndex();
            if (idx >= 0) { favoritesModel.remove(idx); saveFavorites(); }
        });

        JLabel header = new JLabel(Messages.get("filechooser.favorites.label"));
        header.setIcon(new javax.swing.Icon() {
            private static final int S = 12;
            public int getIconWidth()  { return S; }
            public int getIconHeight() { return S; }
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                double cx = x + S / 2.0, cy = y + S / 2.0;
                double outer = S / 2.0, inner = outer * 0.42;
                int[] px = new int[10], py = new int[10];
                for (int i = 0; i < 10; i++) {
                    double angle = Math.PI / 2 + i * Math.PI / 5;
                    double r = (i % 2 == 0) ? outer : inner;
                    px[i] = (int) Math.round(cx - r * Math.cos(angle));
                    py[i] = (int) Math.round(cy - r * Math.sin(angle));
                }
                g2.setColor(new Color(0xFFD700));
                g2.fillPolygon(px, py, 10);
                g2.dispose();
            }
        });
        header.setIconTextGap(5);
        header.setBorder(BorderFactory.createEmptyBorder(8, 8, 6, 8));

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
        btnRow.add(addBtn);
        btnRow.add(removeBtn);

        JPanel panel = new JPanel(new BorderLayout());
        panel.setMinimumSize(new Dimension(80, 0));
        panel.setPreferredSize(new Dimension(180, 0));
        panel.add(header, BorderLayout.NORTH);
        panel.add(scroll,  BorderLayout.CENTER);
        panel.add(btnRow,  BorderLayout.SOUTH);
        return panel;
    }

    private void saveFavorites() {
        List<Path> list = new ArrayList<>(favoritesModel.size());
        for (int i = 0; i < favoritesModel.size(); i++) list.add(favoritesModel.get(i));
        AppPrefs.saveFavorites(list);
    }

    // ── Parser accessory + preview ────────────────────────────────────────────

    private JPanel buildParserAccessory() {
        String[] items = Stream.concat(
                Stream.of(Messages.get("filechooser.parser.autodetect")),
                parsers.stream().map(LogParser::getName)
        ).toArray(String[]::new);

        JComboBox<String> combo = new JComboBox<>(items);
        combo.setSelectedIndex(selectedParserIndex);

        JLabel statusLabel = new JLabel(" ");
        statusLabel.setFont(statusLabel.getFont().deriveFont(11f));

        JTextArea previewArea = new JTextArea();
        previewArea.setEditable(false);
        previewArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        previewArea.setBackground(new Color(0x191919));
        previewArea.setForeground(new Color(0x9E9E9E));
        previewArea.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        previewArea.setLineWrap(false);
        previewArea.setRows(6);
        previewArea.setText(Messages.get("filechooser.preview.empty"));

        JScrollPane previewScroll = new JScrollPane(previewArea);
        previewScroll.setBorder(BorderFactory.createLineBorder(new Color(0x3C3C3C)));

        JLabel previewLabel = new JLabel(Messages.get("filechooser.preview.label"));
        previewLabel.setFont(previewLabel.getFont().deriveFont(11f));

        Runnable updatePreview = () -> {
            File selected = chooser.getSelectedFile();
            if (selected == null || !selected.isFile()) {
                previewArea.setText(Messages.get("filechooser.preview.empty"));
                return;
            }
            try (BufferedReader reader = LogPanel.openReader(selected.toPath())) {
                StringBuilder sb = new StringBuilder();
                String line;
                int count = 0;
                while ((line = reader.readLine()) != null && count < 50) {
                    sb.append(line).append('\n');
                    count++;
                }
                String text = sb.toString();
                long nonPrintable = text.chars()
                        .filter(c -> c < 32 && c != '\n' && c != '\r' && c != '\t')
                        .count();
                if (nonPrintable > text.length() * 0.05) {
                    previewArea.setText(Messages.get("filechooser.preview.binary"));
                } else if (text.isBlank()) {
                    previewArea.setText(Messages.get("filechooser.preview.empty.file"));
                } else {
                    previewArea.setText(text);
                    previewArea.setCaretPosition(0);
                }
            } catch (IOException e) {
                previewArea.setText(Messages.get("filechooser.preview.error"));
            }
        };

        Runnable validate = () -> {
            File selected = chooser.getSelectedFile();
            if (selected == null || !selected.isFile()) {
                statusLabel.setText(" ");
                if (openBtn != null) openBtn.setEnabled(false);
                updatePreview.run();
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
            if (openBtn != null) openBtn.setEnabled(matched.isPresent());
            updatePreview.run();
        };

        combo.addActionListener(e -> {
            selectedParserIndex = combo.getSelectedIndex();
            validate.run();
        });
        chooser.addPropertyChangeListener(JFileChooser.SELECTED_FILE_CHANGED_PROPERTY,
                e -> validate.run());
        chooser.addPropertyChangeListener(JFileChooser.DIRECTORY_CHANGED_PROPERTY, e -> {
            chooser.setSelectedFile(null);
            if (chooser.getUI() instanceof BasicFileChooserUI basicUI) basicUI.setFileName("");
            SwingUtilities.invokeLater(validate);
        });

        JPanel panel = new JPanel(new MigLayout("insets 8, fill, wrap 1", "[280px,grow]", "[][]4[]8[][grow]"));
        panel.setMinimumSize(new Dimension(150, 0));
        panel.add(new JLabel(Messages.get("filechooser.parser.label")));
        panel.add(combo, "growx");
        panel.add(statusLabel, "growx");
        panel.add(previewLabel);
        panel.add(previewScroll, "grow");
        return panel;
    }

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

    // ── Button bar ────────────────────────────────────────────────────────────

    private JPanel buildButtonBar() {
        JButton cancelBtn = new JButton(Messages.get("filechooser.button.cancel"));
        cancelBtn.addActionListener(e -> dispose());

        openBtn = new JButton(Messages.get("filechooser.button.open"));
        openBtn.setEnabled(false);
        openBtn.addActionListener(e -> chooser.approveSelection());

        JPanel bar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        bar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(0x3C3C3C)));
        bar.add(cancelBtn);
        bar.add(openBtn);
        getRootPane().setDefaultButton(openBtn);
        return bar;
    }
}
