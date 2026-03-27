package com.tlaloc.clouseau.ui;

import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * Ensures only one instance of Clouseau runs at a time by binding a
 * ServerSocket on localhost. Secondary instances send their file path(s)
 * to the primary and then exit.
 */
@Slf4j
public final class SingleInstanceManager {

    /** Arbitrary port unlikely to conflict with other services. */
    private static final int PORT = 54823;

    private SingleInstanceManager() {}

    /**
     * Attempts to become the primary instance.
     *
     * <p>If the port is free this instance binds it and starts a daemon
     * listener thread that calls {@code onFileReceived} (on the EDT) whenever
     * a secondary instance sends a file path.
     *
     * @return {@code true} if this is the primary instance;
     *         {@code false} if another instance is already running.
     */
    public static boolean tryBecomePrimary(Consumer<String> onFileReceived) {
        try {
            ServerSocket server = new ServerSocket(PORT, 1, InetAddress.getLoopbackAddress());
            Thread listener = new Thread(() -> {
                while (!server.isClosed()) {
                    try (Socket client = server.accept();
                         BufferedReader reader = new BufferedReader(
                                 new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8))) {
                        String path = reader.readLine();
                        if (path != null && !path.isBlank()) {
                            javax.swing.SwingUtilities.invokeLater(() -> onFileReceived.accept(path));
                        }
                    } catch (IOException ignored) {}
                }
            }, "clouseau-ipc");
            listener.setDaemon(true);
            listener.start();
            log.debug("Single-instance server bound on port {}", PORT);
            return true;
        } catch (IOException e) {
            log.info("Another instance is already running (port {} busy)", PORT);
            return false;
        }
    }

    /**
     * Sends {@code filePath} to the primary instance over the IPC socket.
     */
    public static void sendToPrimary(String filePath) {
        try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), PORT);
             PrintWriter writer = new PrintWriter(
                     new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)) {
            writer.println(filePath);
        } catch (IOException e) {
            log.warn("Failed to contact primary instance", e);
        }
    }
}
