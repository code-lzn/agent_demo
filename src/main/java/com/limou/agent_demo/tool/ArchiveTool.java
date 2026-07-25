package com.limou.agent_demo.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.file.*;
import java.util.zip.*;

/**
 * Archive tool: zip/unzip files and directories.
 */
@Component
public class ArchiveTool {

    private final ToolSafety safety;

    public ArchiveTool(ToolSafety safety) {
        this.safety = safety;
    }

    @Tool(description = "Create a ZIP archive from a file or directory." +
            " For a directory, all files and subdirectories are included recursively")
    public String zip(
            @ToolParam(description = "Source file or directory to zip") String sourcePath,
            @ToolParam(description = "Destination .zip file path") String zipPath) {
        if (!safety.isPathAllowed(sourcePath)) return "Access denied: " + sourcePath;
        if (!safety.isPathAllowed(zipPath)) return "Access denied: " + zipPath;
        try {
            Path src = Path.of(sourcePath);
            if (!Files.exists(src)) return "Source does not exist: " + sourcePath;
            Path parent = Path.of(zipPath).getParent();
            if (parent != null) Files.createDirectories(parent);

            try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipPath))) {
                if (Files.isDirectory(src)) {
                    try (var walk = Files.walk(src)) {
                        walk.filter(Files::isRegularFile).forEach(f -> {
                            String entryName = src.relativize(f).toString().replace("\\", "/");
                            try {
                                zos.putNextEntry(new ZipEntry(entryName));
                                Files.copy(f, zos);
                                zos.closeEntry();
                            } catch (IOException ignored) {}
                        });
                    }
                } else {
                    zos.putNextEntry(new ZipEntry(src.getFileName().toString()));
                    Files.copy(src, zos);
                    zos.closeEntry();
                }
            }
            long size = Files.size(Path.of(zipPath));
            return "Created zip: '" + zipPath + "' (" + formatSize(size) + ")";
        } catch (IOException e) {
            return "Error creating zip: " + e.getMessage();
        }
    }

    @Tool(description = "Extract a ZIP archive to a directory")
    public String unzip(
            @ToolParam(description = "Full path to the .zip file") String zipPath,
            @ToolParam(description = "Destination directory to extract into") String destDir) {
        if (!safety.isPathAllowed(zipPath)) return "Access denied: " + zipPath;
        if (!safety.isPathAllowed(destDir)) return "Access denied: " + destDir;
        try {
            Path dest = Path.of(destDir);
            Files.createDirectories(dest);

            int count = 0;
            try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipPath))) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    Path entryPath = dest.resolve(entry.getName());
                    // Security: prevent zip slip
                    if (!entryPath.normalize().startsWith(dest.normalize())) {
                        continue;
                    }
                    if (entry.isDirectory()) {
                        Files.createDirectories(entryPath);
                    } else {
                        Files.createDirectories(entryPath.getParent());
                        Files.copy(zis, entryPath, StandardCopyOption.REPLACE_EXISTING);
                    }
                    zis.closeEntry();
                    count++;
                }
            }
            return "Extracted " + count + " file(s) to '" + destDir + "'";
        } catch (IOException e) {
            return "Error extracting zip: " + e.getMessage();
        }
    }

    @Tool(description = "List the contents of a ZIP archive without extracting")
    public String listZip(@ToolParam(description = "Full path to the .zip file") String zipPath) {
        if (!safety.isPathAllowed(zipPath)) return "Access denied: " + zipPath;
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipPath))) {
            StringBuilder sb = new StringBuilder();
            ZipEntry entry;
            int count = 0;
            while ((entry = zis.getNextEntry()) != null) {
                String type = entry.isDirectory() ? "[DIR] " : "[FILE]";
                sb.append(type).append(" ").append(entry.getName())
                        .append(" (").append(formatSize(entry.getSize())).append(")\n");
                count++;
            }
            return sb.isEmpty() ? "Zip is empty" : count + " entries:\n" + sb.toString().stripTrailing();
        } catch (IOException e) {
            return "Error reading zip: " + e.getMessage();
        }
    }

    private String formatSize(long bytes) {
        if (bytes < 0) return "?";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024));
    }
}