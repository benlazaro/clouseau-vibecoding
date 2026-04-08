package com.droidenx.clouseau.ui;

import com.droidenx.clouseau.api.LogParser;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.io.File;
import java.util.List;
import java.util.Optional;

/**
 * Modal dialog for configuring an SSH log-streaming connection.
 * Call {@link #showDialog()} to display it; returns the result if the user confirmed.
 */
public class SshConnectionDialog extends JDialog {

    public record Result(SshConfig config, Optional<LogParser> parser) {}

    private static final String AUTH_PASSWORD = "password";
    private static final String AUTH_KEY      = "key";

    // Connection fields
    private final JTextField hostField       = new JTextField(20);
    private final JSpinner   portSpinner     = new JSpinner(new SpinnerNumberModel(22, 1, 65535, 1));
    private final JTextField userField       = new JTextField(20);
    private final JTextField remotePathField = new JTextField(20);

    // Auth panels
    private final JRadioButton passwordRadio = new JRadioButton(Messages.get("ssh.dialog.auth.password"), true);
    private final JRadioButton keyRadio      = new JRadioButton(Messages.get("ssh.dialog.auth.key"));
    private final JPanel       authCards     = new JPanel(new CardLayout());

    // Password auth
    private final JPasswordField passwordField = new JPasswordField(20);

    // Key auth
    private final JTextField     keyFileField      = new JTextField(16);
    private final JPasswordField keyPassphraseField = new JPasswordField(16);

    // Parser
    private final JComboBox<String> parserCombo;
    private final List<LogParser>   parsers;

    // Action
    private final JButton connectBtn = new JButton(Messages.get("ssh.dialog.connect"));
    private Result result;

    public SshConnectionDialog(Frame owner, List<LogParser> parsers) {
        super(owner, Messages.get("ssh.dialog.title"), true);
        this.parsers = parsers;

        String[] parserNames = new String[parsers.size() + 1];
        parserNames[0] = Messages.get("filechooser.parser.autodetect");
        for (int i = 0; i < parsers.size(); i++) parserNames[i + 1] = parsers.get(i).getName();
        parserCombo = new JComboBox<>(parserNames);

        setContentPane(buildContent());
        getRootPane().setDefaultButton(connectBtn);
        getRootPane().registerKeyboardAction(
                e -> dispose(),
                KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        pack();
        setResizable(false);
        setLocationRelativeTo(owner);
        validateForm();
    }

    public Optional<Result> showDialog() {
        setVisible(true);
        return Optional.ofNullable(result);
    }

    // ── Layout ───────────────────────────────────────────────────────────────

    private JPanel buildContent() {
        JPanel panel = new JPanel(new MigLayout(
                "insets 20, wrap 2, gapy 8, gapx 10",
                "[right][grow, fill]"));

        // Host + port on the same row
        panel.add(new JLabel(Messages.get("ssh.dialog.host")));
        JPanel hostRow = new JPanel(new MigLayout("insets 0, gapy 0, gapx 6", "[grow, fill][][]"));
        hostRow.add(hostField, "growx");
        hostRow.add(new JLabel(Messages.get("ssh.dialog.port")));
        JSpinner.NumberEditor portEditor = new JSpinner.NumberEditor(portSpinner, "#");
        portSpinner.setEditor(portEditor);
        portEditor.getTextField().setColumns(5);
        hostRow.add(portSpinner);
        panel.add(hostRow);

        panel.add(new JLabel(Messages.get("ssh.dialog.user")));
        panel.add(userField);

        // Auth type
        ButtonGroup authGroup = new ButtonGroup();
        authGroup.add(passwordRadio);
        authGroup.add(keyRadio);
        JPanel authTypeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        authTypeRow.add(passwordRadio);
        authTypeRow.add(Box.createHorizontalStrut(16));
        authTypeRow.add(keyRadio);
        panel.add(new JLabel());
        panel.add(authTypeRow);

        // Password card
        JPanel passwordCard = new JPanel(new MigLayout("insets 0", "[right][grow, fill]"));
        passwordCard.add(new JLabel(Messages.get("ssh.dialog.password")));
        passwordCard.add(passwordField, "growx");

        // Key card
        JPanel keyCard = new JPanel(new MigLayout("insets 0, wrap 2, gapy 6", "[right][grow, fill]"));
        keyCard.add(new JLabel(Messages.get("ssh.dialog.key.file")));
        JPanel keyFileRow = new JPanel(new MigLayout("insets 0, gapy 0, gapx 4", "[grow, fill][]"));
        keyFileRow.add(keyFileField, "growx");
        JButton browseBtn = new JButton(Messages.get("ssh.dialog.key.browse"));
        browseBtn.addActionListener(e -> browseKeyFile());
        keyFileRow.add(browseBtn);
        keyCard.add(keyFileRow);
        keyCard.add(new JLabel(Messages.get("ssh.dialog.key.passphrase")));
        keyCard.add(keyPassphraseField, "growx");

        authCards.add(passwordCard, AUTH_PASSWORD);
        authCards.add(keyCard,      AUTH_KEY);

        passwordRadio.addActionListener(e -> ((CardLayout) authCards.getLayout()).show(authCards, AUTH_PASSWORD));
        keyRadio.addActionListener(e ->      ((CardLayout) authCards.getLayout()).show(authCards, AUTH_KEY));

        panel.add(new JLabel(), "");
        panel.add(authCards);

        panel.add(new JLabel(Messages.get("ssh.dialog.remote.path")));
        panel.add(remotePathField);

        panel.add(new JLabel(Messages.get("ssh.dialog.parser")));
        panel.add(parserCombo);

        // Separator + buttons
        panel.add(new JSeparator(), "span 2, growx, gaptop 8, gapbottom 4");

        connectBtn.addActionListener(e -> onConnect());
        JButton cancelBtn = new JButton(Messages.get("ssh.dialog.cancel"));
        cancelBtn.addActionListener(e -> dispose());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.add(connectBtn);
        buttons.add(cancelBtn);
        panel.add(buttons, "span 2, growx");

        // Live validation
        DocumentListener validator = new DocumentListener() {
            public void insertUpdate(DocumentEvent e)  { validateForm(); }
            public void removeUpdate(DocumentEvent e)  { validateForm(); }
            public void changedUpdate(DocumentEvent e) { validateForm(); }
        };
        hostField.getDocument().addDocumentListener(validator);
        userField.getDocument().addDocumentListener(validator);
        remotePathField.getDocument().addDocumentListener(validator);

        return panel;
    }

    private void validateForm() {
        boolean ok = !hostField.getText().isBlank()
                  && !userField.getText().isBlank()
                  && !remotePathField.getText().isBlank();
        connectBtn.setEnabled(ok);
    }

    private void browseKeyFile() {
        JFileChooser fc = new JFileChooser(System.getProperty("user.home") + "/.ssh");
        fc.setDialogTitle(Messages.get("ssh.dialog.key.file"));
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File f = fc.getSelectedFile();
            keyFileField.setText(f.getAbsolutePath());
        }
    }

    private void onConnect() {
        String host       = hostField.getText().trim();
        int    port       = (int) portSpinner.getValue();
        String user       = userField.getText().trim();
        String remotePath = remotePathField.getText().trim();

        SshConfig config;
        if (keyRadio.isSelected()) {
            config = new SshConfig(host, port, user,
                    null,
                    keyFileField.getText().trim(),
                    new String(keyPassphraseField.getPassword()),
                    remotePath);
        } else {
            config = new SshConfig(host, port, user,
                    new String(passwordField.getPassword()),
                    null, null,
                    remotePath);
        }

        int parserIdx = parserCombo.getSelectedIndex();
        Optional<LogParser> parser = parserIdx > 0
                ? Optional.of(parsers.get(parserIdx - 1))
                : Optional.empty();

        result = new Result(config, parser);
        dispose();
    }
}
