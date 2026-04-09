package com.droidenx.clouseau.ui;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.function.LongConsumer;

/**
 * Abstraction over a remote plugin artifact repository.
 * Implementations handle authentication and protocol details internally.
 */
interface PluginRepository {

    record Asset(
            String groupId,
            String artifactId,
            String version,
            String downloadUrl,
            long   sizeBytes      // -1 if unknown
    ) {}

    /** Human-readable label shown in the repository selector. */
    String getName();

    /**
     * Search for plugin JARs matching {@code query}.
     * Must be called on a worker thread, never on the EDT.
     */
    List<Asset> search(String query) throws IOException, InterruptedException;

    /**
     * Download {@code asset} into {@code destDir}.
     * {@code onBytesRead} is called periodically with cumulative bytes received.
     * Must be called on a worker thread, never on the EDT.
     * Returns the path of the downloaded file.
     */
    Path download(Asset asset, Path destDir, LongConsumer onBytesRead)
            throws IOException, InterruptedException;
}
