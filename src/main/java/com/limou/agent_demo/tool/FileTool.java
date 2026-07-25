package com.limou.agent_demo.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.util.stream.Collectors;

@Component
public class FileTool {

    private final ToolSafety safety;

    public FileTool(ToolSafety safety) {
        this.safety = safety;
    }

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
        try {
            return Files.lines(Path.of(filePath)).limit(n)
                    .collect(Collectors.joining("\n"));
        } catch (IOException e) {
            return "Error reading file: " + e.getMessage();
        }
    }

    @Tool(description = "Count how many lines are in a file")
    public String countLines(@ToolParam(description = "Full path to the file") String filePath) {
        if (!safety.isPathAllowed(filePath)) return "Access denied: " + filePath;
        try {
            long count = Files.lines(Path.of(filePath)).count();
            return "File '" + filePath + "' has " + count + " lines";
        } catch (IOException e) {
            return "Error reading file: " + e.getMessage();
        }
    }

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

    @Tool(description = "Write text content to a file (creates or overwrites)")
    public String writeFile(
            @ToolParam(description = "Full path to the file") String filePath,
            @ToolParam(description = "Content to write") String content) {
        if (!safety.isPathAllowed(filePath)) return "Access denied: " + filePath;
        try {
            Files.writeString(Path.of(filePath), content,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            return "Successfully wrote to '" + filePath + "'";
        } catch (IOException e) {
            return "Error writing file: " + e.getMessage();
        }
    }

    @Tool(description = "Search for a keyword in a file and return matching lines")
    public String searchInFile(
            @ToolParam(description = "Full path to the file") String filePath,
            @ToolParam(description = "Keyword to search for") String keyword) {
        if (!safety.isPathAllowed(filePath)) return "Access denied: " + filePath;
        try {
            String results = Files.lines(Path.of(filePath))
                    .filter(line -> line.contains(keyword))
                    .collect(Collectors.joining("\n"));
            return results.isEmpty() ? "No matches found" : results;
        } catch (IOException e) {
            return "Error reading file: " + e.getMessage();
        }
    }
}
