package com.limou.agent_demo.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Log monitoring tool: watch files for new lines and keywords.
 */
@Component
public class LogMonitorTool {

    private final Map<String, Thread> monitors = new ConcurrentHashMap<>();
    private final ToolSafety safety;

    public LogMonitorTool(ToolSafety safety) {
        this.safety = safety;
    }

    @Tool(description = "Monitor a file for new lines containing a keyword." +
            " When a match is found, writes the line to a result file." +
            " Runs in the background; returns a monitor ID that you can use with stopMonitor." +
            " For example, monitor a log file for 'ERROR' and write matches to D:\\errors.txt")
    public String startMonitor(
            @ToolParam(description = "Full path to the file to watch") String watchFile,
            @ToolParam(description = "Keyword to search for in new lines") String keyword,
            @ToolParam(description = "Full path to the output file where matching lines will be written") String outputFile) {
        if (!safety.isPathAllowed(watchFile)) return "Access denied: " + watchFile;
        if (!safety.isPathAllowed(outputFile)) return "Access denied: " + outputFile;

        Path watchPath = Path.of(watchFile);
        Path outputPath = Path.of(outputFile);
        if (!Files.exists(watchPath)) return "File to watch does not exist: " + watchFile;

        String monitorId = "monitor_" + System.currentTimeMillis();

        Thread thread = new Thread(() -> {
            try {
                // Start reading from current position
                long position = Files.size(watchPath);

                while (!Thread.currentThread().isInterrupted()) {
                    Thread.sleep(1000);
                    long newSize = Files.size(watchPath);
                    if (newSize > position) {
                        try (var reader = Files.newBufferedReader(watchPath)) {
                            reader.skip(position);
                            String line;
                            while ((line = reader.readLine()) != null) {
                                if (line.contains(keyword)) {
                                    String entry = java.time.LocalDateTime.now() + " | " + line + "\n";
                                    Files.writeString(outputPath, entry,
                                            StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                                }
                            }
                        }
                        position = newSize;
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception ignored) {
            }
        }, monitorId);
        thread.setDaemon(true);
        thread.start();

        monitors.put(monitorId, thread);
        return "Monitor started (id=" + monitorId + "). Watching '" + watchFile + "' for '" + keyword + "', output to '" + outputFile + "'";
    }

    @Tool(description = "List all active log monitors")
    public String listMonitors() {
        if (monitors.isEmpty()) return "No active monitors";
        StringBuilder sb = new StringBuilder();
        monitors.forEach((id, thread) ->
                sb.append(id).append(" | alive=").append(thread.isAlive()).append("\n"));
        return sb.toString().stripTrailing();
    }

    @Tool(description = "Stop a running log monitor by its ID")
    public String stopMonitor(@ToolParam(description = "Monitor ID to stop") String monitorId) {
        Thread thread = monitors.remove(monitorId);
        if (thread == null) return "Monitor not found: " + monitorId;
        thread.interrupt();
        return "Stopped monitor: " + monitorId;
    }
}