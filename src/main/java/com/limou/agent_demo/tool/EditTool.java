package com.limou.agent_demo.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class EditTool {

    private final ToolSafety safety;

    public EditTool(ToolSafety safety) {
        this.safety = safety;
    }

    @Tool(description = "Replace a string in a file with exact match. If oldString matches multiple places, specify replaceAll=true or make oldString more specific.")
    public String editFile(
            @ToolParam(description = "Full path to the file") String filePath,
            @ToolParam(description = "The exact string to find and replace") String oldString,
            @ToolParam(description = "The string to replace with") String newString,
            @ToolParam(description = "Whether to replace all occurrences. Default false") boolean replaceAll) {

        if (!safety.isPathAllowed(filePath)) {
            return "Access denied: " + filePath;
        }

        if (oldString == null || oldString.isEmpty()) {
            return "Error: oldString cannot be empty";
        }

        Path path = Path.of(filePath);
        if (!Files.isRegularFile(path)) {
            return "File not found: " + filePath;
        }

        try {
            String content = Files.readString(path);

            int firstIdx = content.indexOf(oldString);
            if (firstIdx == -1) {
                return String.format("Error: oldString not found in file.\n"
                        + "Tip: Make sure the string matches exactly, including whitespace and indentation.\n"
                        + "oldString length: %d chars", oldString.length());
            }

            int count = 0;
            int idx = 0;
            while ((idx = content.indexOf(oldString, idx)) != -1) {
                count++;
                idx += oldString.length();
            }

            if (count > 1 && !replaceAll) {
                return String.format(
                        "Found %d occurrences of oldString. Use replaceAll=true to replace all, "
                                + "or make oldString more specific (include more surrounding context) to match only one.",
                        count);
            }

            String newContent;
            if (replaceAll) {
                newContent = content.replace(oldString, newString);
            } else {
                newContent = content.substring(0, firstIdx) + newString
                        + content.substring(firstIdx + oldString.length());
            }

            // 备份
            Path backupPath = Path.of(filePath + ".bak");
            Files.writeString(backupPath, content);

            Files.writeString(path, newContent);

            return String.format("Successfully replaced %d occurrence(s) in '%s'. Backup saved to '%s'.",
                    replaceAll ? count : 1, filePath, backupPath.toString());

        } catch (IOException e) {
            return "Error editing file: " + e.getMessage();
        }
    }
}
