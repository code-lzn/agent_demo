package com.limou.agent_demo.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

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

    // ==================== 读 ====================

    @Tool(description = "读取文件的全部文本内容")
    public String readFile(@ToolParam(description = "文件完整路径") String filePath) {
        if (!safety.isPathAllowed(filePath)) return "Access denied: " + filePath;
        try {
            return Files.readString(Path.of(filePath));
        } catch (IOException e) {
            return "Error reading file: " + e.getMessage();
        }
    }

    @Tool(description = "读取文件开头的指定行数")
    public String readFileLines(
            @ToolParam(description = "文件完整路径") String filePath,
            @ToolParam(description = "要读取的行数") int n) {
        if (!safety.isPathAllowed(filePath)) return "Access denied: " + filePath;
        try (Stream<String> lines = Files.lines(Path.of(filePath))) {
            return lines.limit(n).collect(Collectors.joining("\n"));
        } catch (IOException e) {
            return "Error reading file: " + e.getMessage();
        }
    }

    @Tool(description = "从指定偏移位置开始读取 N 行，适合分块读取大文件。例如 offset=100, limit=50 读取第 100-149 行")
    public String readFileRange(
            @ToolParam(description = "文件完整路径") String filePath,
            @ToolParam(description = "起始行号（从 0 开始，第一行为 0）") int offset,
            @ToolParam(description = "最多读取行数") int limit) {
        if (!safety.isPathAllowed(filePath)) return "Access denied: " + filePath;
        try (Stream<String> lines = Files.lines(Path.of(filePath))) {
            return lines.skip(offset).limit(limit).collect(Collectors.joining("\n"));
        } catch (IOException e) {
            return "Error reading file: " + e.getMessage();
        }
    }

    @Tool(description = "读取文件最后 N 行，类似于 Unix tail 命令，适合查看日志文件")
    public String readLastLines(
            @ToolParam(description = "文件完整路径") String filePath,
            @ToolParam(description = "从末尾读取的行数") int n) {
        if (!safety.isPathAllowed(filePath)) return "Access denied: " + filePath;
        try {
            List<String> allLines = Files.readAllLines(Path.of(filePath));
            int from = Math.max(0, allLines.size() - n);
            return String.join("\n", allLines.subList(from, allLines.size()));
        } catch (IOException e) {
            return "Error reading file: " + e.getMessage();
        }
    }

    @Tool(description = "统计文件总共有多少行")
    public String countLines(@ToolParam(description = "文件完整路径") String filePath) {
        if (!safety.isPathAllowed(filePath)) return "Access denied: " + filePath;
        try (Stream<String> lines = Files.lines(Path.of(filePath))) {
            long count = lines.count();
            return "文件 '" + filePath + "' 共 " + count + " 行";
        } catch (IOException e) {
            return "Error reading file: " + e.getMessage();
        }
    }

    // ==================== 写 ====================

    @Tool(description = "将文本内容写入文件（覆盖已有内容，文件不存在则创建）")
    public String writeFile(
            @ToolParam(description = "文件完整路径") String filePath,
            @ToolParam(description = "要写入的内容") String content) {
        if (!safety.isPathAllowed(filePath)) return "Access denied: " + filePath;
        try {
            ensureParentDir(filePath);
            Files.writeString(Path.of(filePath), content,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            return "成功写入文件: '" + filePath + "'";
        } catch (IOException e) {
            return "Error writing file: " + e.getMessage();
        }
    }

    @Tool(description = "将文本内容追加到文件末尾（文件不存在则创建）")
    public String appendFile(
            @ToolParam(description = "文件完整路径") String filePath,
            @ToolParam(description = "要追加的内容") String content) {
        if (!safety.isPathAllowed(filePath)) return "Access denied: " + filePath;
        try {
            ensureParentDir(filePath);
            Files.writeString(Path.of(filePath), content,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            return "成功追加到文件: '" + filePath + "'";
        } catch (IOException e) {
            return "Error appending to file: " + e.getMessage();
        }
    }

    // ==================== 目录操作 ====================

    @Tool(description = "列出指定目录下的所有文件和子目录")
    public String listDir(@ToolParam(description = "目录完整路径") String dirPath) {
        if (!safety.isPathAllowed(dirPath)) return "Access denied: " + dirPath;
        try (var stream = Files.list(Path.of(dirPath))) {
            return stream.map(p -> (Files.isDirectory(p) ? "[目录] " : "[文件] ") + p.getFileName())
                    .collect(Collectors.joining("\n"));
        } catch (IOException e) {
            return "Error listing directory: " + e.getMessage();
        }
    }

    @Tool(description = "递归创建目录，包括所有不存在的父目录（类似 mkdir -p）。目录已存在也不报错")
    public String createDir(@ToolParam(description = "要创建的目录完整路径") String dirPath) {
        if (!safety.isPathAllowed(dirPath)) return "Access denied: " + dirPath;
        try {
            Path path = Path.of(dirPath);
            if (Files.exists(path)) {
                return Files.isDirectory(path)
                        ? "目录已存在: '" + dirPath + "'"
                        : "路径已存在但不是目录: '" + dirPath + "'";
            }
            Files.createDirectories(path);
            return "成功创建目录: '" + dirPath + "'";
        } catch (IOException e) {
            return "Error creating directory: " + e.getMessage();
        }
    }

    // ==================== 删除 ====================

    @Tool(description = "删除指定文件。如果路径是目录则会失败，请使用 deleteDir 删除目录")
    public String deleteFile(@ToolParam(description = "要删除的文件完整路径") String filePath) {
        if (!safety.isPathAllowed(filePath)) return "Access denied: " + filePath;
        try {
            Path path = Path.of(filePath);
            if (!Files.exists(path)) return "文件不存在: '" + filePath + "'";
            if (Files.isDirectory(path)) return "该路径是目录，请使用 deleteDir 删除: '" + filePath + "'";
            Files.delete(path);
            return "成功删除文件: '" + filePath + "'";
        } catch (IOException e) {
            return "Error deleting file: " + e.getMessage();
        }
    }

    @Tool(description = "递归删除目录及其所有内容，请谨慎使用")
    public String deleteDir(@ToolParam(description = "要删除的目录完整路径") String dirPath) {
        if (!safety.isPathAllowed(dirPath)) return "Access denied: " + dirPath;
        try {
            Path path = Path.of(dirPath);
            if (!Files.exists(path)) return "目录不存在: '" + dirPath + "'";
            if (!Files.isDirectory(path)) return "该路径是文件而非目录，请使用 deleteFile 删除: '" + dirPath + "'";
            try (Stream<Path> walk = Files.walk(path)) {
                walk.sorted(Comparator.reverseOrder())
                        .forEach(p -> {
                            try {
                                Files.delete(p);
                            } catch (IOException ignored) {
                            }
                        });
            }
            return "成功删除目录: '" + dirPath + "'";
        } catch (IOException e) {
            return "Error deleting directory: " + e.getMessage();
        }
    }

    // ==================== 复制 & 移动 ====================

    @Tool(description = "将文件复制到目标路径。目标可以是目录或新文件路径，目标已存在则覆盖")
    public String copyFile(
            @ToolParam(description = "源文件路径") String sourcePath,
            @ToolParam(description = "目标路径（目录或新文件名）") String targetPath) {
        if (!safety.isPathAllowed(sourcePath)) return "Access denied: " + sourcePath;
        if (!safety.isPathAllowed(targetPath)) return "Access denied: " + targetPath;
        try {
            Path src = Path.of(sourcePath);
            Path dst = Path.of(targetPath);
            if (!Files.exists(src)) return "源文件不存在: '" + sourcePath + "'";
            if (Files.isDirectory(src)) return "源路径是目录，不支持复制目录: '" + sourcePath + "'";
            if (Files.isDirectory(dst)) {
                dst = dst.resolve(src.getFileName());
            }
            ensureParentDir(dst.toString());
            Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
            return "成功复制 '" + sourcePath + "' 到 '" + dst + "'";
        } catch (IOException e) {
            return "Error copying file: " + e.getMessage();
        }
    }

    @Tool(description = "移动或重命名文件或目录")
    public String moveFile(
            @ToolParam(description = "源文件或目录路径") String sourcePath,
            @ToolParam(description = "目标路径（新名称或新位置）") String targetPath) {
        if (!safety.isPathAllowed(sourcePath)) return "Access denied: " + sourcePath;
        if (!safety.isPathAllowed(targetPath)) return "Access denied: " + targetPath;
        try {
            Path src = Path.of(sourcePath);
            Path dst = Path.of(targetPath);
            if (!Files.exists(src)) return "源路径不存在: '" + sourcePath + "'";
            if (Files.isDirectory(dst) && !Files.isDirectory(src)) {
                dst = dst.resolve(src.getFileName());
            }
            ensureParentDir(dst.toString());
            Files.move(src, dst, StandardCopyOption.REPLACE_EXISTING);
            return "成功移动 '" + sourcePath + "' 到 '" + dst + "'";
        } catch (IOException e) {
            return "Error moving file: " + e.getMessage();
        }
    }

    // ==================== 信息 ====================

    @Tool(description = "获取文件或目录的详细信息：大小、修改时间、创建时间、读写权限等")
    public String getFileInfo(@ToolParam(description = "文件或目录的完整路径") String filePath) {
        if (!safety.isPathAllowed(filePath)) return "Access denied: " + filePath;
        try {
            Path path = Path.of(filePath);
            if (!Files.exists(path)) return "路径不存在: '" + filePath + "'";

            BasicFileAttributes attr = Files.readAttributes(path, BasicFileAttributes.class);
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            String modifiedTime = LocalDateTime.ofInstant(
                    attr.lastModifiedTime().toInstant(), ZoneId.systemDefault()).format(fmt);
            String createdTime = LocalDateTime.ofInstant(
                    attr.creationTime().toInstant(), ZoneId.systemDefault()).format(fmt);

            StringBuilder sb = new StringBuilder();
            sb.append("路径: ").append(path.toRealPath()).append("\n");
            sb.append("类型: ").append(attr.isDirectory() ? "目录" : "文件").append("\n");
            sb.append("大小: ").append(formatSize(attr.size())).append("\n");
            sb.append("创建时间: ").append(createdTime).append("\n");
            sb.append("修改时间: ").append(modifiedTime).append("\n");
            sb.append("可读: ").append(Files.isReadable(path) ? "是" : "否").append("\n");
            sb.append("可写: ").append(Files.isWritable(path) ? "是" : "否").append("\n");
            sb.append("可执行: ").append(Files.isExecutable(path) ? "是" : "否");
            return sb.toString();
        } catch (IOException e) {
            return "Error getting file info: " + e.getMessage();
        }
    }

    // ==================== 搜索 ====================

    @Tool(description = "在文件中搜索关键字，返回包含该关键字的行及行号")
    public String searchInFile(
            @ToolParam(description = "文件完整路径") String filePath,
            @ToolParam(description = "要搜索的关键字") String keyword) {
        if (!safety.isPathAllowed(filePath)) return "Access denied: " + filePath;
        try {
            List<String> lines = Files.readAllLines(Path.of(filePath));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).contains(keyword)) {
                    sb.append(i + 1).append(": ").append(lines.get(i)).append("\n");
                }
            }
            return sb.isEmpty() ? "未找到匹配内容" : sb.toString().stripTrailing();
        } catch (IOException e) {
            return "Error reading file: " + e.getMessage();
        }
    }

    @Tool(description = "递归搜索目录下所有文本文件中的关键字，自动跳过二进制文件")
    public String searchInDir(
            @ToolParam(description = "目录完整路径") String dirPath,
            @ToolParam(description = "要搜索的关键字") String keyword) {
        if (!safety.isPathAllowed(dirPath)) return "Access denied: " + dirPath;
        Path root = Path.of(dirPath);
        if (!Files.isDirectory(root)) return "不是目录: '" + dirPath + "'";
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
            return sb.isEmpty() ? "未找到匹配 '" + keyword + "' 的内容" : sb.toString().stripTrailing();
        } catch (IOException e) {
            return "Error searching directory: " + e.getMessage();
        }
    }

    @Tool(description = "按通配符模式在目录中递归查找文件，如 *.java、test*.xml")
    public String findFiles(
            @ToolParam(description = "目录完整路径") String dirPath,
            @ToolParam(description = "通配符模式，如 *.java、*.txt") String pattern) {
        if (!safety.isPathAllowed(dirPath)) return "Access denied: " + dirPath;
        Path root = Path.of(dirPath);
        if (!Files.isDirectory(root)) return "不是目录: '" + dirPath + "'";
        try (Stream<Path> walk = Files.walk(root)) {
            PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
            String results = walk.filter(Files::isRegularFile)
                    .filter(matcher::matches)
                    .map(p -> root.relativize(p).toString())
                    .collect(Collectors.joining("\n"));
            return results.isEmpty()
                    ? "在 '" + dirPath + "' 中未找到匹配 '" + pattern + "' 的文件"
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
     * 通过检查前 8KB 是否包含 null 字节来判断文件是否可能是二进制文件
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