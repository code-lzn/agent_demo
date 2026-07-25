package com.limou.agent_demo.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class FileTool {

    private final ToolSafety safety;

    public FileTool(ToolSafety safety) {
        this.safety = safety;
    }

    // ==================== Read ====================

    @Tool(description = "Read the entire content of a file")
    public String readFile(@ToolParam(description = "Full path to the file") String filePath) {
        if (!safety.isPathAllowed(filePath)) return "Access denied: " + filePath;
        try {
            return Files.readString(Path.of(filePath));
        } catch (IOException e) {
            return "Error reading file: " + e.getMessage();
        }
    }

    @Tool(description = "Read the first N lines of a file")
    public String readFileLines(
            @ToolParam(description = "Full path to the file") String filePath,
            @ToolParam(description = "Number of lines to read") int n) {
        if (!safety.isPathAllowed(filePath)) return "Access denied: " + filePath;
        try (Stream<String> lines = Files.lines(Path.of(filePath))) {
            return lines.limit(n).collect(Collectors.joining("\n"));
        } catch (IOException e) {
            return "Error reading file: " + e.getMessage();
        }
    }

    @Tool(description = "Read lines from a file starting at offset (0-based), returning up to limit lines." +
            " Useful for reading large files in chunks. For example, offset=100, limit=50 reads lines 100-149")
    public String readFileRange(
            @ToolParam(description = "Full path to the file") String filePath,
            @ToolParam(description = "Line number to start from (0-based, first line is 0)") int offset,
            @ToolParam(description = "Maximum number of lines to read") int limit) {
        if (!safety.isPathAllowed(filePath)) return "Access denied: " + filePath;
        try (Stream<String> lines = Files.lines(Path.of(filePath))) {
            return lines.skip(offset).limit(limit).collect(Collectors.joining("\n"));
        } catch (IOException e) {
            return "Error reading file: " + e.getMessage();
        }
    }

    @Tool(description = "Read the last N lines of a file (like Unix tail). Useful for reading log files")
    public String readLastLines(
            @ToolParam(description = "Full path to the file") String filePath,
            @ToolParam(description = "Number of lines to read from the end") int n) {
        if (!safety.isPathAllowed(filePath)) return "Access denied: " + filePath;
        try {
            List<String> allLines = Files.readAllLines(Path.of(filePath));
            int from = Math.max(0, allLines.size() - n);
            return String.join("\n", allLines.subList(from, allLines.size()));
        } catch (IOException e) {
            return "Error reading file: " + e.getMessage();
        }
    }

    @Tool(description = "Count the number of lines in a file")
    public String countLines(@ToolParam(description = "Full path to the file") String filePath) {
        if (!safety.isPathAllowed(filePath)) return "Access denied: " + filePath;
        try (Stream<String> lines = Files.lines(Path.of(filePath))) {
            long count = lines.count();
            return "File '" + filePath + "' has " + count + " lines";
        } catch (IOException e) {
            return "Error reading file: " + e.getMessage();
        }
    }

    // ==================== Write ====================

    @Tool(description = "Write text content to a file (creates the file if it doesn't exist, overwrites if it does)")
    public String writeFile(
            @ToolParam(description = "Full path to the file") String filePath,
            @ToolParam(description = "Content to write") String content) {
        if (!safety.isPathAllowed(filePath)) return "Access denied: " + filePath;
        try {
            ensureParentDir(filePath);
            Files.writeString(Path.of(filePath), content,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            return "Successfully wrote to '" + filePath + "'";
        } catch (IOException e) {
            return "Error writing file: " + e.getMessage();
        }
    }

    @Tool(description = "Append text content to the end of a file. Creates the file if it doesn't exist")
    public String appendFile(
            @ToolParam(description = "Full path to the file") String filePath,
            @ToolParam(description = "Content to append") String content) {
        if (!safety.isPathAllowed(filePath)) return "Access denied: " + filePath;
        try {
            ensureParentDir(filePath);
            Files.writeString(Path.of(filePath), content,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            return "Successfully appended to '" + filePath + "'";
        } catch (IOException e) {
            return "Error appending to file: " + e.getMessage();
        }
    }

    // ==================== Directory ====================

    @Tool(description = "List all files and subdirectories in a given directory")
    public String listDir(@ToolParam(description = "Full path to the directory") String dirPath) {
        if (!safety.isPathAllowed(dirPath)) return "Access denied: " + dirPath;
        try (var stream = Files.list(Path.of(dirPath))) {
            return stream.map(p -> (Files.isDirectory(p) ? "[DIR] " : "[FILE] ") + p.getFileName())
                    .collect(Collectors.joining("\n"));
        } catch (IOException e) {
            return "Error listing directory: " + e.getMessage();
        }
    }

    @Tool(description = "Create a directory, including any missing parent directories (like mkdir -p)." +
            " Returns success even if the directory already exists")
    public String createDir(@ToolParam(description = "Full path to the directory to create") String dirPath) {
        if (!safety.isPathAllowed(dirPath)) return "Access denied: " + dirPath;
        try {
            Path path = Path.of(dirPath);
            if (Files.exists(path)) {
                return Files.isDirectory(path)
                        ? "Directory already exists: '" + dirPath + "'"
                        : "Path exists but is not a directory: '" + dirPath + "'";
            }
            Files.createDirectories(path);
            return "Successfully created directory: '" + dirPath + "'";
        } catch (IOException e) {
            return "Error creating directory: " + e.getMessage();
        }
    }

    // ==================== Delete ====================

    @Tool(description = "Delete a file. Fails if the path is a directory (use deleteDir for directories)")
    public String deleteFile(@ToolParam(description = "Full path to the file to delete") String filePath) {
        if (!safety.isPathAllowed(filePath)) return "Access denied: " + filePath;
        try {
            Path path = Path.of(filePath);
            if (!Files.exists(path)) return "File does not exist: '" + filePath + "'";
            if (Files.isDirectory(path)) return "Path is a directory, use deleteDir instead: '" + filePath + "'";
            Files.delete(path);
            return "Successfully deleted file: '" + filePath + "'";
        } catch (IOException e) {
            return "Error deleting file: " + e.getMessage();
        }
    }

    @Tool(description = "Delete a directory and all its contents recursively. Use with caution")
    public String deleteDir(@ToolParam(description = "Full path to the directory to delete") String dirPath) {
        if (!safety.isPathAllowed(dirPath)) return "Access denied: " + dirPath;
        try {
            Path path = Path.of(dirPath);
            if (!Files.exists(path)) return "Directory does not exist: '" + dirPath + "'";
            if (!Files.isDirectory(path)) return "Path is a file, use deleteFile instead: '" + dirPath + "'";
            try (Stream<Path> walk = Files.walk(path)) {
                walk.sorted(Comparator.reverseOrder())
                        .forEach(p -> {
                            try { Files.delete(p); } catch (IOException ignored) {}
                        });
            }
            return "Successfully deleted directory: '" + dirPath + "'";
        } catch (IOException e) {
            return "Error deleting directory: " + e.getMessage();
        }
    }

    // ==================== Copy & Move ====================

    @Tool(description = "Copy a file to a target path. Target can be a directory or a new file path." +
            " Overwrites if the target already exists")
    public String copyFile(
            @ToolParam(description = "Source file path") String sourcePath,
            @ToolParam(description = "Destination file or directory path") String targetPath) {
        if (!safety.isPathAllowed(sourcePath)) return "Access denied: " + sourcePath;
        if (!safety.isPathAllowed(targetPath)) return "Access denied: " + targetPath;
        try {
            Path src = Path.of(sourcePath);
            Path dst = Path.of(targetPath);
            if (!Files.exists(src)) return "Source file does not exist: '" + sourcePath + "'";
            if (Files.isDirectory(src)) return "Source is a directory, copy not supported: '" + sourcePath + "'";
            if (Files.isDirectory(dst)) {
                dst = dst.resolve(src.getFileName());
            }
            ensureParentDir(dst.toString());
            Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
            return "Successfully copied '" + sourcePath + "' to '" + dst + "'";
        } catch (IOException e) {
            return "Error copying file: " + e.getMessage();
        }
    }

    @Tool(description = "Move or rename a file or directory")
    public String moveFile(
            @ToolParam(description = "Source file or directory path") String sourcePath,
            @ToolParam(description = "Destination path (new name or new location)") String targetPath) {
        if (!safety.isPathAllowed(sourcePath)) return "Access denied: " + sourcePath;
        if (!safety.isPathAllowed(targetPath)) return "Access denied: " + targetPath;
        try {
            Path src = Path.of(sourcePath);
            Path dst = Path.of(targetPath);
            if (!Files.exists(src)) return "Source does not exist: '" + sourcePath + "'";
            if (Files.isDirectory(dst) && !Files.isDirectory(src)) {
                dst = dst.resolve(src.getFileName());
            }
            ensureParentDir(dst.toString());
            Files.move(src, dst, StandardCopyOption.REPLACE_EXISTING);
            return "Successfully moved '" + sourcePath + "' to '" + dst + "'";
        } catch (IOException e) {
            return "Error moving file: " + e.getMessage();
        }
    }

    // ==================== Info ====================

    @Tool(description = "Get detailed information about a file or directory: size, last modified time, type, permissions")
    public String getFileInfo(@ToolParam(description = "Full path to the file or directory") String filePath) {
        if (!safety.isPathAllowed(filePath)) return "Access denied: " + filePath;
        try {
            Path path = Path.of(filePath);
            if (!Files.exists(path)) return "Path does not exist: '" + filePath + "'";

            BasicFileAttributes attr = Files.readAttributes(path, BasicFileAttributes.class);
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            String modifiedTime = LocalDateTime.ofInstant(
                    attr.lastModifiedTime().toInstant(), ZoneId.systemDefault()).format(fmt);
            String createdTime = LocalDateTime.ofInstant(
                    attr.creationTime().toInstant(), ZoneId.systemDefault()).format(fmt);

            StringBuilder sb = new StringBuilder();
            sb.append("Path: ").append(path.toRealPath()).append("\n");
            sb.append("Type: ").append(attr.isDirectory() ? "Directory" : "File").append("\n");
            sb.append("Size: ").append(formatSize(attr.size())).append("\n");
            sb.append("Created: ").append(createdTime).append("\n");
            sb.append("Modified: ").append(modifiedTime).append("\n");
            sb.append("Readable: ").append(Files.isReadable(path) ? "Yes" : "No").append("\n");
            sb.append("Writable: ").append(Files.isWritable(path) ? "Yes" : "No").append("\n");
            sb.append("Executable: ").append(Files.isExecutable(path) ? "Yes" : "No");
            return sb.toString();
        } catch (IOException e) {
            return "Error getting file info: " + e.getMessage();
        }
    }

    // ==================== Open ====================

    @Tool(description = "Open a file with the system default application." +
            " For example: .txt opens in Notepad, .pdf opens in PDF reader, .jpg opens in image viewer." +
            " Executable files (.exe, .bat, etc.) are blocked for safety — use ProcessTool.openApp instead")
    public String openFile(@ToolParam(description = "Full path to the file") String filePath) {
        if (!safety.isPathAllowed(filePath)) return "Access denied: " + filePath;
        try {
            Path path = Path.of(filePath);
            if (!Files.exists(path)) return "File does not exist: '" + filePath + "'";
            if (Files.isDirectory(path)) return "Path is a directory, use openDir: '" + filePath + "'";

            String name = path.getFileName().toString().toLowerCase();
            if (name.endsWith(".exe") || name.endsWith(".bat") || name.endsWith(".cmd")
                    || name.endsWith(".com") || name.endsWith(".msi")) {
                return "Blocked for safety: executable files not supported. Use ProcessTool.openApp instead: '" + filePath + "'";
            }

            Desktop.getDesktop().open(path.toFile());
            return "Opened file with default application: '" + filePath + "'";
        } catch (IOException e) {
            return "Failed to open file: " + e.getMessage();
        }
    }

    @Tool(description = "Open a directory in Windows Explorer. If a file path is given," +
            " opens the parent directory and selects the file")
    public String openDir(@ToolParam(description = "Directory path (or file path to reveal in Explorer)") String dirPath) {
        if (!safety.isPathAllowed(dirPath)) return "Access denied: " + dirPath;
        try {
            Path path = Path.of(dirPath);
            if (!Files.exists(path)) return "Path does not exist: '" + dirPath + "'";

            if (Files.isDirectory(path)) {
                Desktop.getDesktop().open(path.toFile());
            } else {
                Runtime.getRuntime().exec(new String[]{"explorer", "/select,", path.toString()});
            }
            return "Opened in Explorer: '" + dirPath + "'";
        } catch (IOException e) {
            return "Failed to open directory: " + e.getMessage();
        }
    }

    // ==================== Search ====================

    @Tool(description = "Search for a keyword in a file and return matching lines with line numbers")
    public String searchInFile(
            @ToolParam(description = "Full path to the file") String filePath,
            @ToolParam(description = "Keyword to search for") String keyword) {
        if (!safety.isPathAllowed(filePath)) return "Access denied: " + filePath;
        try {
            List<String> lines = Files.readAllLines(Path.of(filePath));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).contains(keyword)) {
                    sb.append(i + 1).append(": ").append(lines.get(i)).append("\n");
                }
            }
            return sb.isEmpty() ? "No matches found" : sb.toString().stripTrailing();
        } catch (IOException e) {
            return "Error reading file: " + e.getMessage();
        }
    }

    @Tool(description = "Search for a keyword recursively in all text files under a directory." +
            " Automatically skips binary files")
    public String searchInDir(
            @ToolParam(description = "Full path to the directory to search in") String dirPath,
            @ToolParam(description = "Keyword to search for") String keyword) {
        if (!safety.isPathAllowed(dirPath)) return "Access denied: " + dirPath;
        Path root = Path.of(dirPath);
        if (!Files.isDirectory(root)) return "Not a directory: '" + dirPath + "'";
        try (Stream<Path> walk = Files.walk(root)) {
            StringBuilder sb = new StringBuilder();
            walk.filter(Files::isRegularFile)
                    .filter(f -> !isLikelyBinary(f))
                    .forEach(f -> {
                        try {
                            List<String> lines = Files.readAllLines(f);
                            for (int i = 0; i < lines.size(); i++) {
                                if (lines.get(i).contains(keyword)) {
                                    sb.append(root.relativize(f)).append(":")
                                            .append(i + 1).append(": ")
                                            .append(lines.get(i).stripTrailing())
                                            .append("\n");
                                }
                            }
                        } catch (IOException ignored) {}
                    });
            return sb.isEmpty() ? "No matches found for '" + keyword + "'" : sb.toString().stripTrailing();
        } catch (IOException e) {
            return "Error searching directory: " + e.getMessage();
        }
    }

    @Tool(description = "Find files by name pattern in a directory. Supports wildcards like *.java, *.txt, test*.xml")
    public String findFiles(
            @ToolParam(description = "Full path to the directory to search in") String dirPath,
            @ToolParam(description = "Glob pattern, e.g. *.java, *.txt") String pattern) {
        if (!safety.isPathAllowed(dirPath)) return "Access denied: " + dirPath;
        Path root = Path.of(dirPath);
        if (!Files.isDirectory(root)) return "Not a directory: '" + dirPath + "'";
        try (Stream<Path> walk = Files.walk(root)) {
            PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
            String results = walk.filter(Files::isRegularFile)
                    .filter(matcher::matches)
                    .map(p -> root.relativize(p).toString())
                    .collect(Collectors.joining("\n"));
            return results.isEmpty()
                    ? "No files matching '" + pattern + "' found in '" + dirPath + "'"
                    : results;
        } catch (IOException e) {
            return "Error searching files: " + e.getMessage();
        }
    }

    // ==================== Helpers ====================

    private void ensureParentDir(String filePath) throws IOException {
        Path parent = Path.of(filePath).getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    /**
     * Check if a file is likely binary by looking for null bytes in the first 8KB.
     */
    private boolean isLikelyBinary(Path file) {
        try {
            byte[] bytes = Files.readAllBytes(file);
            if (bytes.length == 0) return false;
            int checkLen = Math.min(bytes.length, 8192);
            for (int i = 0; i < checkLen; i++) {
                if (bytes[i] == 0) return true;
            }
            return false;
        } catch (IOException e) {
            return true;
        }
    }
}
