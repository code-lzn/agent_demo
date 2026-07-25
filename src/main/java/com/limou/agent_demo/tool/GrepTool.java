package com.limou.agent_demo.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

@Component
public class GrepTool {

    private final ToolSafety safety;

    public GrepTool(ToolSafety safety) {
        this.safety = safety;
    }

    @Tool(description = "Recursively search files for a regex pattern. Like grep -r. Returns file:line:content matches.")
    public String searchCode(
            @ToolParam(description = "Regex pattern to search for") String pattern,
            @ToolParam(description = "Root directory to search in. Defaults to project root if empty") String rootPath,
            @ToolParam(description = "File glob filter, e.g. *.java or *.{yml,xml}. Empty means all files") String fileGlob) {

        Path root;
        if (rootPath == null || rootPath.isBlank()) {
            root = Path.of(System.getProperty("user.dir"));
        } else {
            root = Path.of(rootPath).toAbsolutePath().normalize();
        }

        if (!safety.isPathAllowed(root.toString())) {
            return "Access denied: " + root;
        }

        if (!Files.isDirectory(root)) {
            return "Path is not a directory: " + root;
        }

        Pattern regex;
        try {
            regex = Pattern.compile(pattern);
        } catch (PatternSyntaxException e) {
            return "Invalid regex pattern: " + e.getMessage();
        }

        final PathMatcher matcher = (fileGlob != null && !fileGlob.isBlank())
                ? FileSystems.getDefault().getPathMatcher("glob:" + fileGlob.trim())
                : null;

        StringBuilder result = new StringBuilder();
        int matchCount = 0;
        final int MAX_RESULTS = 50;
        final int MAX_CHARS = 4000;

        try (var stream = Files.walk(root)) {
            var files = stream
                    .filter(Files::isRegularFile)
                    .filter(f -> matcher == null || matcher.matches(f.getFileName()))
                    .filter(f -> !isBinary(f))
                    .toList();

            outer:
            for (Path file : files) {
                var fileLines = Files.readAllLines(file);
                int lineNum = 0;
                for (String line : fileLines) {
                    lineNum++;
                    if (regex.matcher(line).find()) {
                        matchCount++;
                        String shortLine = line.length() > 200 ? line.substring(0, 200) + "..." : line;
                        result.append(root.relativize(file))
                                .append(":").append(lineNum)
                                .append(": ").append(shortLine).append("\n");
                        if (result.length() > MAX_CHARS || matchCount >= MAX_RESULTS) {
                            break outer;
                        }
                    }
                }
            }
        } catch (IOException e) {
            return "Search error: " + e.getMessage();
        }

        if (matchCount == 0) {
            return "No matches found for pattern: " + pattern;
        }

        String prefix = String.format("Found %d matches:\n\n", matchCount);
        String suffix = "";
        if (matchCount > MAX_RESULTS || result.length() > MAX_CHARS) {
            suffix = "\n\n... (results truncated)";
        }
        return prefix + result + suffix;
    }

    private boolean isBinary(Path file) {
        try {
            byte[] bytes = Files.readAllBytes(file);
            for (int i = 0; i < Math.min(bytes.length, 512); i++) {
                if (bytes[i] == 0) return true;
            }
            return false;
        } catch (IOException e) {
            return true;
        }
    }
}
