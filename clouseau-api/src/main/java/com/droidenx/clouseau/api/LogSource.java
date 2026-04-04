package com.droidenx.clouseau.api;

import java.util.function.Consumer;

/**
 * Implement this to add a new log source (file tail, socket, Kubernetes pod, etc.).
 */
public interface LogSource {

    String getName();

    /**
     * Start streaming raw lines to the consumer.
     * Called on a background thread — do not block the caller.
     */
    void open(Consumer<String> lineConsumer) throws Exception;

    void close() throws Exception;
}
