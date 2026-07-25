package com.limou.agent_demo.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * System operations tool: command execution, system info, environment variables, time.
 */
@Component
public class SystemTool {

    private final ToolSafety safety;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public SystemTool(ToolSafety safety) {
        this.safety = safety;
    }

    // ==================== Command Execution ====================

    @Tool(description = "Execute a Windows command and return its output." +
            " For security, only whitelisted commands are allowed." +
            " Default allowed commands: dir, echo, type, find, findstr, whoami, hostname," +
            " ping, ipconfig, netstat, tasklist, curl, date, time, cd, set, where, ver," +
            " systeminfo, wmic, nslookup, tracert, path, help, clip, sort, more")
    public String runCommand(@ToolParam(description = "Command with arguments, e.g. dir C:\\, ping baidu.com") String command) {
        if (!safety.isCommandAllowed(command)) {
            return "Command rejected: not in whitelist. To use '" + command + "', add it to agent.safety.allowed-commands config";
        }
        try {
            ProcessBuilder pb = new ProcessBuilder("cmd.exe", "/c", command);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            p.waitFor();
            String result = output.toString().stripTrailing();
            if (result.isEmpty()) return "Command executed, no output";
            if (result.length() > 8000) {
                result = result.substring(0, 8000) + "\n... (output truncated, total " + result.length() + " chars)";
            }
            return result;
        } catch (Exception e) {
            return "Command execution failed: " + e.getMessage();
        }
    }

    // ==================== Time ====================

    @Tool(description = "Get the current date and time")
    public String getCurrentTime() {
        return "Current time: " + LocalDateTime.now().format(FORMATTER);
    }

    // ==================== Environment Variables ====================

    @Tool(description = "Read a system environment variable, e.g. JAVA_HOME, PATH, USERNAME")
    public String getEnvVar(@ToolParam(description = "Environment variable name") String name) {
        String value = System.getenv(name);
        if (value == null) {
            return "Environment variable '" + name + "' is not set";
        }
        if (value.length() > 2000) {
            return name + " = " + value.substring(0, 2000) + "\n... (" + value.length() + " chars total, truncated)";
        }
        return name + " = " + value;
    }

    @Tool(description = "List all system environment variables with their values")
    public String listEnvVars() {
        StringBuilder sb = new StringBuilder();
        System.getenv().forEach((k, v) -> {
            if (v.length() > 200) {
                v = v.substring(0, 200) + "...";
            }
            sb.append(k).append(" = ").append(v).append("\n");
        });
        return sb.toString().stripTrailing();
    }

    // ==================== Directory ====================

    @Tool(description = "Get the current working directory")
    public String getCurrentDir() {
        return "Current working directory: " + System.getProperty("user.dir");
    }

    // ==================== System Info ====================

    @Tool(description = "Get basic system information: OS, Java version, CPU cores, available memory")
    public String getSystemInfo() {
        Runtime rt = Runtime.getRuntime();
        return "OS: " + System.getProperty("os.name") + " " + System.getProperty("os.version") + " (" + System.getProperty("os.arch") + ")\n"
                + "User: " + System.getProperty("user.name") + "\n"
                + "Home dir: " + System.getProperty("user.home") + "\n"
                + "Java: " + System.getProperty("java.version") + " (" + System.getProperty("java.vendor") + ")\n"
                + "CPU cores: " + rt.availableProcessors() + "\n"
                + "JVM max memory: " + (rt.maxMemory() / 1024 / 1024) + " MB\n"
                + "JVM used memory: " + ((rt.totalMemory() - rt.freeMemory()) / 1024 / 1024) + " MB";
    }
}
