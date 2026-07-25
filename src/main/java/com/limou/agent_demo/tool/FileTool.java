package com.limou.agent_demo.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
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

    // ==================== 读 ====================

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
            " Useful for reading large files in chunks. For example, offset=100, limit=50 reads lines 100-149.")
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

    @Tool(description = "Read the last N lines of a file (like Unix tail). Useful for reading log files.")
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

    @Tool(description = "Count how many lines are in a file")
    public String countLines(@ToolParam(description = "Full path to the file") String filePath) {
        if (!safety.isPathAllowed(filePath)) return "Access denied: " + filePath;
        try (Stream<String> lines = Files.lines(Path.of(filePath))) {
            long count = lines.count();
            return "File '" + filePath + "' has " + count + " lines";
        } catch (IOException e) {
            return "Error reading file: " + e.getMessage();
        }
    }

    // ==================== 写 ====================

    @Tool(description = "Write text content to a file (creates or overwrites)")
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

    @Tool(description = "Append text content to the end of a file. Creates the file if it does not exist.")
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

    // ==================== 目录操作 ====================

    @Tool(description = "List all files and directories in a given directory")
    public String listDir(@ToolParam(description = "Full path to the directory") String dirPath) {
        if (!safety.isPathAllowed(dirPath)) return "Access denied: " + dirPath;
        try (var stream = Files.list(Path.of(dirPath))) {
            return stream.map(p -> (Files.isDirectory(p) ? "[DIR]  " : "[FILE] ") + p.getFileName())
                    .collect(Collectors.joining("\n"));
        } catch (IOException e) {
            return "Error listing directory: " + e.getMessage();
        }
    }

    @Tool(description = "Create a directory, including any missing parent directories (like mkdir -p)." +
            " Returns success even if the directory already exists.")
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

    // ==================== 删除 ====================

    @Tool(description = "Delete a file. Fails if the path is a directory (use deleteDir for directories).")
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

    @Tool(description = "Delete a directory and all its contents recursively. Use with caution.")
    public String deleteDir(@ToolParam(description = "Full path to the directory to delete") String dirPath) {
        if (!safety.isPathAllowed(dirPath)) return "Access denied: " + dirPath;
        try {
            Path path = Path.of(dirPath);
            if (!Files.exists(path)) return "Directory does not exist: '" + dirPath + "'";
            if (!Files.isDirectory(path)) return "Path is a file, use deleteFile instead: '" + dirPath + "'";
            try (Stream<Path> walk = Files.walk(path)) {
                walk.sorted(Comparator.reverseOrder())
                        .forEach(p -> {
                            try {
                                Files.delete(p);
                            } catch (IOException ignored) {
                            }
                        });
            }
            return "Successfully deleted directory: '" + dirPath + "'";
        } catch (IOException e) {
            return "Error deleting directory: " + e.getMessage();
        }
    }

    // ==================== 复制 & 移动 ====================

    @Tool(description = "Copy a file to a target path. Target can be a directory or a new file path." +
            " Overwrites if the target already exists.")
    public String copyFile(
            @ToolParam(description = "Source file path") String sourcePath,
            @ToolParam(description = "Destination file or directory path") String targetPath) {
        if (!safety.isPathAllowed(sourcePath)) return "Access denied: " + sourcePath;
        if (!safety.isPathAllowed(targetPath)) return "Access denied: " + targetPath;
        try {
            Path src = Path.of(sourcePath);
            Path dst = Path.of(targetPath);
            if (!Files.exists(src)) return "Source file does not exist: '" + sourcePath + "'";
            if (Files.isDirectory(src)) return "Source is a directory, use copyDir or cp -r in a shell: '" + sourcePath + "'";
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

    @Tool(description = "Move or rename a file. Also works for directories.")
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

    // ==================== 信息 ====================

    @Tool(description = "Get detailed information about a file or directory: size, last modified time, type, readability.")
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
            sb.append("Readable: ").append(Files.isReadable(path)).append("\n");
            sb.append("Writable: ").append(Files.isWritable(path)).append("\n");
            sb.append("Executable: ").append(Files.isExecutable(path));
            return sb.toString();
        } catch (IOException e) {
            return "Error getting file info: " + e.getMessage();
        }
    }

    // ==================== 搜索 ====================

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

    @Tool(description = "Search for a keyword recursively in all files under a directory." +
            " Skips binary files and directories that cannot be read.")
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
                        } catch (IOException ignored) {
                        }
                    });
            return sb.isEmpty() ? "No matches found for '" + keyword + "'" : sb.toString().stripTrailing();
        } catch (IOException e) {
            return "Error searching directory: " + e.getMessage();
        }
    }

    @Tool(description = "Search for files by name in a directory. Supports wildcard patterns like *.java or *.txt")
    public String findFiles(
            @ToolParam(description = "Full path to the directory to search in") String dirPath,
            @ToolParam(description = "Glob pattern, e.g. *.java, *.txt, test*.xml") String pattern) {
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

    // ==================== 辅助方法 ====================

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
     * Check if a file is likely binary by reading the first 8KB and looking for null bytes.
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