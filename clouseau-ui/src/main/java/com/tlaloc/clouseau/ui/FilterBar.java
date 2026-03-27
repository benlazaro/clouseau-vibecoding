package com.tlaloc.clouseau.ui;

import com.tlaloc.clouseau.api.LogEntry;
import com.tlaloc.clouseau.api.LogEntry.LogLevel;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreePath;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Per-tab filter bar: level toggles, logger picker, timestamp range, and text/regex search.
 * Call {@link #buildPredicate()} to get the current combined filter.
 * Call {@link #updateLoggers(List)} after a file is loaded to populate the logger picker.
 */
final class FilterBar extends JPanel {

    private static final LogLevel[] LEVELS = {
        LogLevel.TRACE, LogLevel.DEBUG, LogLevel.INFO,
        LogLevel.WARN,  LogLevel.ERROR, LogLevel.FATAL, LogLevel.UNKNOWN
    };

    private static final Map<LogLevel, Color> LEVEL_COLORS;
    static {
        Map<LogLevel, Color> m = new EnumMap<>(LogLevel.class);
        m.put(LogLevel.TRACE,   new Color(0x888888));
        m.put(LogLevel.DEBUG,   new Color(0x56A8F5));
        m.put(LogLevel.INFO,    new Color(0x73C991));
        m.put(LogLevel.WARN,    new Color(0xF0A030));
        m.put(LogLevel.ERROR,   new Color(0xFF6B68));
        m.put(LogLevel.FATAL,   new Color(0xFF3333));
        m.put(LogLevel.UNKNOWN, new Color(0x888888));
        LEVEL_COLORS = Collections.unmodifiableMap(m);
    }

    private static final List<DateTimeFormatter> TS_FORMATS = List.of(
        DateTimeFormatter.ofPattern("HH:mm:ss.SSS"),
        DateTimeFormatter.ofPattern("HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    );

    private final Map<LogLevel, JToggleButton> levelButtons = new EnumMap<>(LogLevel.class);
    private final JButton     loggerButton;
    private final JTextField  fromField    = new JTextField(9);
    private final JTextField  toField      = new JTextField(9);
    private final JTextField  searchField  = new JTextField(16);
    private final Runnable    onChanged;

    // Logger picker state
    private List<String>     allLoggers      = List.of();
    private final Set<String> excludedLoggers = new LinkedHashSet<>();

    // Used when only HH:mm:ss is typed — anchors time to the date of the first log entry
    private LocalDate referenceDate = LocalDate.now();

    FilterBar(Runnable onChanged) {
        super(new MigLayout("insets 2 8 2 8, gap 4", "", "[center]"));
        this.onChanged = onChanged;

        // ── Level toggle buttons ───────────────────────────────────────────────
        for (LogLevel level : LEVELS) {
            JToggleButton btn = new JToggleButton(level.name());
            btn.setSelected(true);
            btn.setFocusPainted(false);
            btn.setMargin(new Insets(2, 5, 2, 5));
            btn.setFont(btn.getFont().deriveFont(11f));
            Color onColor  = LEVEL_COLORS.get(level);
            Color offColor = UIManager.getColor("Label.disabledForeground");
            btn.setForeground(onColor);
            btn.addItemListener(e -> {
                btn.setForeground(btn.isSelected() ? onColor : offColor);
                onChanged.run();
            });
            levelButtons.put(level, btn);
            add(btn);
        }

        add(new JSeparator(JSeparator.VERTICAL), "growy, gapx 4");

        // ── Logger picker button ──────────────────────────────────────────────
        loggerButton = new JButton(Messages.get("filter.loggers.all"));
        loggerButton.addActionListener(e -> showLoggerPopup(loggerButton));
        add(loggerButton);

        add(new JSeparator(JSeparator.VERTICAL), "growy, gapx 4");

        // ── Timestamp range ───────────────────────────────────────────────────
        fromField.putClientProperty("JTextField.placeholderText", Messages.get("filter.from.placeholder"));
        toField.putClientProperty("JTextField.placeholderText",   Messages.get("filter.to.placeholder"));

        DocumentListener tsDoc = docListener(() -> {
            validateTimestamp(fromField);
            validateTimestamp(toField);
            onChanged.run();
        });
        fromField.getDocument().addDocumentListener(tsDoc);
        toField.getDocument().addDocumentListener(tsDoc);

        add(new JLabel(Messages.get("filter.from.label")));
        add(fromField);
        add(new JLabel(Messages.get("filter.to.label")));
        add(toField);

        add(new JSeparator(JSeparator.VERTICAL), "growy, gapx 4");

        // ── Text / regex search ───────────────────────────────────────────────
        searchField.putClientProperty("JTextField.placeholderText", Messages.get("filter.search.placeholder"));
        searchField.getDocument().addDocumentListener(docListener(onChanged::run));
        add(searchField, "growx");

        // ── Clear button ──────────────────────────────────────────────────────
        JButton clearBtn = new JButton(Messages.get("filter.clear"));
        clearBtn.addActionListener(e -> clear());
        add(clearBtn);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Call after a file is loaded to populate the logger picker list and update the
     * reference date used for time-only timestamp parsing.
     */
    void updateLoggers(List<LogEntry> entries) {
        allLoggers = entries.stream()
            .map(LogEntry::logger)
            .filter(Objects::nonNull)
            .distinct()
            .sorted()
            .toList();
        excludedLoggers.retainAll(new HashSet<>(allLoggers));

        entries.stream()
            .filter(e -> e.timestamp() != null)
            .findFirst()
            .ifPresent(e -> referenceDate = e.timestamp().atZone(ZoneId.systemDefault()).toLocalDate());

        updateLoggerButtonLabel();
    }

    /** Returns a predicate that is the AND of all active filters. */
    Predicate<LogEntry> buildPredicate() {
        return buildLevelPredicate()
            .and(buildLoggerPredicate())
            .and(buildTimestampPredicate())
            .and(buildTextPredicate());
    }

    /** Resets all filters to their default (pass-through) state. */
    void clear() {
        levelButtons.values().forEach(b -> b.setSelected(true));
        excludedLoggers.clear();
        updateLoggerButtonLabel();
        fromField.setText("");
        toField.setText("");
        searchField.setText("");
        onChanged.run();
    }

    // ── Predicates ───────────────────────────────────────────────────────────

    private Predicate<LogEntry> buildLevelPredicate() {
        EnumSet<LogLevel> allowed = EnumSet.noneOf(LogLevel.class);
        levelButtons.forEach((level, btn) -> { if (btn.isSelected()) allowed.add(level); });
        if (allowed.size() == LEVELS.length) return e -> true;
        return e -> e.level() == null || allowed.contains(e.level());
    }

    private Predicate<LogEntry> buildLoggerPredicate() {
        if (excludedLoggers.isEmpty()) return e -> true;
        Set<String> snapshot = Set.copyOf(excludedLoggers);
        return e -> e.logger() == null || !snapshot.contains(e.logger());
    }

    private Predicate<LogEntry> buildTimestampPredicate() {
        Instant from = parseTimestamp(fromField.getText().trim());
        Instant to   = parseTimestamp(toField.getText().trim());
        if (from == null && to == null) return e -> true;
        return e -> {
            if (e.timestamp() == null) return true;
            if (from != null && e.timestamp().isBefore(from)) return false;
            if (to   != null && e.timestamp().isAfter(to))   return false;
            return true;
        };
    }

    private Predicate<LogEntry> buildTextPredicate() {
        String text = searchField.getText().trim();
        if (text.isEmpty()) return e -> true;
        try {
            Pattern p = Pattern.compile(text, Pattern.CASE_INSENSITIVE);
            return e -> p.matcher(e.message() != null ? e.message() : "").find();
        } catch (PatternSyntaxException ex) {
            String lower = text.toLowerCase();
            return e -> (e.message() != null ? e.message().toLowerCase() : "").contains(lower);
        }
    }

    // ── Logger picker popup ───────────────────────────────────────────────────

    private void showLoggerPopup(JButton invoker) {
        JPopupMenu popup = new JPopupMenu();
        JPanel content = new JPanel(new MigLayout("fill, insets 6", "[grow]", "[grow][]"));
        content.setPreferredSize(new Dimension(300, 350));

        DefaultMutableTreeNode root = buildLoggerTree();
        JTree tree = new JTree(root);
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.setToggleClickCount(0); // expand/collapse only via handle; click = checkbox toggle
        for (int i = 0; i < tree.getRowCount(); i++) tree.expandRow(i);

        JCheckBox rendererCb = new JCheckBox();
        rendererCb.setOpaque(false);
        tree.setCellRenderer((TreeCellRenderer) (t, value, selected, expanded, leaf, row, hasFocus) -> {
            if (!(((DefaultMutableTreeNode) value).getUserObject() instanceof LoggerNode ln))
                return new JLabel();
            CheckState state = getCheckState((DefaultMutableTreeNode) value);
            rendererCb.setText(ln.segment());
            rendererCb.setSelected(state != CheckState.UNCHECKED);
            rendererCb.putClientProperty("JCheckBox.indeterminate", state == CheckState.INDETERMINATE);
            return rendererCb;
        });

        tree.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                TreePath path = tree.getPathForLocation(e.getX(), e.getY());
                if (path == null) return;
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
                if (!(node.getUserObject() instanceof LoggerNode)) return;
                boolean nowChecked = getCheckState(node) != CheckState.CHECKED;
                setNodeChecked(node, nowChecked);
                tree.repaint();
                updateLoggerButtonLabel();
                onChanged.run();
            }
        });

        content.add(new JScrollPane(tree), "grow, wrap");

        JButton allBtn  = new JButton(Messages.get("filter.loggers.select.all"));
        JButton noneBtn = new JButton(Messages.get("filter.loggers.select.none"));
        allBtn.addActionListener(e -> {
            excludedLoggers.clear();
            tree.repaint();
            updateLoggerButtonLabel();
            onChanged.run();
        });
        noneBtn.addActionListener(e -> {
            excludedLoggers.addAll(allLoggers);
            tree.repaint();
            updateLoggerButtonLabel();
            onChanged.run();
        });
        JPanel btnRow = new JPanel(new MigLayout("insets 0", "[]push[]"));
        btnRow.add(allBtn);
        btnRow.add(noneBtn);
        content.add(btnRow, "growx");

        popup.add(content);
        popup.show(invoker, 0, invoker.getHeight());
    }

    private DefaultMutableTreeNode buildLoggerTree() {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("ROOT");
        for (String logger : allLoggers) {
            String[] parts = logger.split("\\.", -1);
            DefaultMutableTreeNode current = root;
            int offset = 0;
            for (String part : parts) {
                String fullPath = logger.substring(0, offset + part.length());
                DefaultMutableTreeNode child = findTreeChild(current, part);
                if (child == null) {
                    child = new DefaultMutableTreeNode(new LoggerNode(part, fullPath));
                    current.add(child);
                }
                current = child;
                offset += part.length() + 1; // +1 for the dot separator
            }
        }
        return root;
    }

    private static DefaultMutableTreeNode findTreeChild(DefaultMutableTreeNode parent, String segment) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) parent.getChildAt(i);
            if (child.getUserObject() instanceof LoggerNode ln && ln.segment().equals(segment))
                return child;
        }
        return null;
    }

    private CheckState getCheckState(DefaultMutableTreeNode node) {
        if (node.isLeaf()) {
            String path = node.getUserObject() instanceof LoggerNode ln ? ln.fullPath() : "";
            return excludedLoggers.contains(path) ? CheckState.UNCHECKED : CheckState.CHECKED;
        }
        boolean anyChecked = false, anyUnchecked = false;
        for (int i = 0; i < node.getChildCount(); i++) {
            CheckState cs = getCheckState((DefaultMutableTreeNode) node.getChildAt(i));
            if (cs != CheckState.UNCHECKED) anyChecked = true;
            if (cs != CheckState.CHECKED)   anyUnchecked = true;
            if (anyChecked && anyUnchecked) return CheckState.INDETERMINATE;
        }
        return anyChecked ? CheckState.CHECKED : CheckState.UNCHECKED;
    }

    private void setNodeChecked(DefaultMutableTreeNode node, boolean checked) {
        if (node.isLeaf()) {
            if (node.getUserObject() instanceof LoggerNode ln) {
                if (checked) excludedLoggers.remove(ln.fullPath());
                else         excludedLoggers.add(ln.fullPath());
            }
            return;
        }
        for (int i = 0; i < node.getChildCount(); i++)
            setNodeChecked((DefaultMutableTreeNode) node.getChildAt(i), checked);
    }

    private record LoggerNode(String segment, String fullPath) {}
    private enum CheckState { CHECKED, UNCHECKED, INDETERMINATE }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Instant parseTimestamp(String text) {
        if (text.isEmpty()) return null;
        for (DateTimeFormatter fmt : TS_FORMATS) {
            try { return LocalDateTime.parse(text, fmt).atZone(ZoneId.systemDefault()).toInstant(); }
            catch (DateTimeParseException ignored) {}
            try { return LocalTime.parse(text, fmt).atDate(referenceDate).atZone(ZoneId.systemDefault()).toInstant(); }
            catch (DateTimeParseException ignored) {}
        }
        return null;
    }

    private void validateTimestamp(JTextField field) {
        String text = field.getText().trim();
        field.putClientProperty("JTextField.outline",
            !text.isEmpty() && parseTimestamp(text) == null ? "error" : null);
    }

    private void updateLoggerButtonLabel() {
        int total   = allLoggers.size();
        long active = allLoggers.stream().filter(l -> !excludedLoggers.contains(l)).count();
        loggerButton.setText(excludedLoggers.isEmpty() || total == 0
            ? Messages.get("filter.loggers.all")
            : Messages.get("filter.loggers.partial").formatted(active, total));
    }

    private static DocumentListener docListener(Runnable action) {
        return new DocumentListener() {
            public void insertUpdate(DocumentEvent e)  { action.run(); }
            public void removeUpdate(DocumentEvent e)  { action.run(); }
            public void changedUpdate(DocumentEvent e) {}
        };
    }
}
