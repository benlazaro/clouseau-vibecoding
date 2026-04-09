package com.droidenx.clouseau.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.function.LongConsumer;

/**
 * Searches and downloads plugin JARs from a Nexus Repository Manager 3 instance
 * via its REST API ({@code /service/rest/v1/search/assets}).
 */
final class NexusPluginRepository implements PluginRepository {

    private final AppPrefs.PluginRepo config;
    private final HttpClient          http = HttpClient.newHttpClient();

    NexusPluginRepository(AppPrefs.PluginRepo config) {
        this.config = config;
    }

    @Override
    public String getName() { return config.name(); }

    @Override
    public List<Asset> search(String query) throws IOException, InterruptedException {
        String raw  = config.url().stripTrailing();
        String base = raw.endsWith("/") ? raw.substring(0, raw.length() - 1) : raw;
        String repoParam = (config.repository() != null && !config.repository().isBlank())
                ? "&repository=" + URLEncoder.encode(config.repository(), StandardCharsets.UTF_8)
                : "";
        String url = base + "/service/rest/v1/search/assets?q="
                + URLEncoder.encode(query, StandardCharsets.UTF_8) + repoParam;

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "application/json")
                .header("Authorization", buildAuthHeader())
                .GET()
                .build();

        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200)
            throw new IOException("Nexus search returned HTTP " + resp.statusCode()
                    + " for " + config.name());

        return parseItems(resp.body());
    }

    private List<Asset> parseItems(String body) {
        JsonObject root  = JsonParser.parseString(body).getAsJsonObject();
        JsonArray  items = root.getAsJsonArray("items");
        List<Asset> result = new ArrayList<>();
        for (JsonElement el : items) {
            JsonObject item = el.getAsJsonObject();
            String path = item.has("path") ? item.get("path").getAsString() : "";
            String contentType = item.has("contentType") ? item.get("contentType").getAsString() : "";
            if (!contentType.contains("java-archive") && !path.endsWith(".jar")) continue;

            String downloadUrl = item.get("downloadUrl").getAsString();
            long   size        = item.has("fileSize") ? item.get("fileSize").getAsLong() : -1;

            JsonObject m2        = item.has("maven2") ? item.getAsJsonObject("maven2") : null;
            String     groupId    = m2 != null && m2.has("groupId")    ? m2.get("groupId").getAsString()    : "";
            String     artifactId = m2 != null && m2.has("artifactId") ? m2.get("artifactId").getAsString() : path;
            String     version    = m2 != null && m2.has("version")    ? m2.get("version").getAsString()    : "";

            result.add(new Asset(groupId, artifactId, version, downloadUrl, size));
        }
        return result;
    }

    @Override
    public Path download(Asset asset, Path destDir, LongConsumer onBytesRead)
            throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(asset.downloadUrl()))
                .header("Authorization", buildAuthHeader())
                .GET()
                .build();

        String rawName = asset.downloadUrl().substring(asset.downloadUrl().lastIndexOf('/') + 1);
        Path dest = destDir.resolve(rawName);

        HttpResponse<InputStream> resp = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
        if (resp.statusCode() != 200)
            throw new IOException("Download returned HTTP " + resp.statusCode());

        try (InputStream in = resp.body(); OutputStream out = Files.newOutputStream(dest)) {
            byte[] buf   = new byte[8192];
            long   total = 0;
            int    n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
                total += n;
                onBytesRead.accept(total);
            }
        }
        return dest;
    }

    private String buildAuthHeader() {
        return switch (config.authType() != null ? config.authType() : "none") {
            case "basic" -> "Basic " + Base64.getEncoder().encodeToString(
                    (config.username() + ":" + config.credential()).getBytes(StandardCharsets.UTF_8));
            case "token" -> "Bearer " + config.credential();
            default      -> "";
        };
    }
}
