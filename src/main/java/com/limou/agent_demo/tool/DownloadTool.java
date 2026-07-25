package com.limou.agent_demo.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/**
 * File download tool with progress support.
 */
@Component
public class DownloadTool {

    private final ToolSafety safety;
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public DownloadTool(ToolSafety safety) {
        this.safety = safety;
    }

    @Tool(description = "Download a file from a URL and save it to the specified local path." +
            " Returns the file size and save location on success." +
            " The download is synchronous — for very large files this may take a while")
    public String downloadFile(
            @ToolParam(description = "URL of the file to download") String url,
            @ToolParam(description = "Full local path to save the file to") String savePath) {
        if (!safety.isPathAllowed(savePath)) return "Access denied: " + savePath;
        try {
            Path path = Path.of(savePath);
            Path parent = path.getParent();
            if (parent != null) Files.createDirectories(parent);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMinutes(10))
                    .GET()
                    .build();

            HttpResponse<byte[]> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() >= 400) {
                return "Download failed: HTTP " + response.statusCode();
            }

            Files.write(path, response.body());
            long size = Files.size(path);
            return "Downloaded " + formatSize(size) + " from " + url + " to '" + savePath + "'";
        } catch (Exception e) {
            return "Download failed: " + e.getMessage();
        }
    }

    @Tool(description = "Check the size of a remote file by sending a HEAD request." +
            " Returns the Content-Length header value")
    public String getFileSize(@ToolParam(description = "URL of the file to check") String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<Void> response = CLIENT.send(request, HttpResponse.BodyHandlers.discarding());
            long contentLength = response.headers().firstValueAsLong("Content-Length").orElse(-1);
            return contentLength >= 0
                    ? "File size: " + formatSize(contentLength) + " (" + contentLength + " bytes)"
                    : "Could not determine file size (no Content-Length header)";
        } catch (Exception e) {
            return "Failed to check file size: " + e.getMessage();
        }
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }
}