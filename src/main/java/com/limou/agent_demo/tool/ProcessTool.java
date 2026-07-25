package com.limou.agent_demo.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * Process management tool: launch, close, and list applications.
 */
@Component
public class ProcessTool {

    private final ToolSafety safety;

    public ProcessTool(ToolSafety safety) {
        this.safety = safety;
    }

    @Tool(description = "Launch an application or executable on this computer")
    public String openApp(@ToolParam(description = "Full path to the executable, e.g. notepad.exe or C:\\app\\my.exe") String appPath) {
        if (safety.isCommandBlocked(appPath)) {
            return "Blocked: '" + appPath + "' is not allowed for safety reasons";
        }
        try {
            new ProcessBuilder(appPath).start();
            return "Successfully launched: " + appPath;
        } catch (Exception e) {
            return "Failed to launch '" + appPath + "': " + e.getMessage();
        }
    }

    @Tool(description = "Launch an application with command-line arguments")
    public String openAppWithArgs(
            @ToolParam(description = "Full path to the executable") String appPath,
            @ToolParam(description = "Command-line arguments") String args) {
        if (safety.isCommandBlocked(appPath)) {
            return "Blocked: '" + appPath + "' is not allowed";
        }
        try {
            new ProcessBuilder(appPath, args).start();
            return "Successfully launched: " + appPath + " " + args;
        } catch (Exception e) {
            return "Failed to launch: " + e.getMessage();
        }
    }

    @Tool(description = "Close an application by its process name, e.g. notepad.exe")
    public String closeApp(@ToolParam(description = "Process name to kill, e.g. notepad.exe") String processName) {
        try {
            ProcessBuilder pb = new ProcessBuilder("taskkill", "/f", "/im", processName);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes());
            return "Close result: " + output;
        } catch (Exception e) {
            return "Failed to close '" + processName + "': " + e.getMessage();
        }
    }

    @Tool(description = "List currently running processes on this computer")
    public String listRunningApps() {
        try {
            ProcessBuilder pb = new ProcessBuilder("tasklist");
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes());
            if (output.length() > 4000) {
                output = output.substring(0, 4000) + "\n... (truncated)";
            }
            return output;
        } catch (Exception e) {
            return "Failed to list processes: " + e.getMessage();
        }
    }
}
