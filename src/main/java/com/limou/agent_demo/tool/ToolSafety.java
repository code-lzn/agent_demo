package com.limou.agent_demo.tool;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

@Component
public class ToolSafety {

    private static final Set<String> BLOCKED_COMMANDS = Set.of(
            "format", "del /f", "rm -rf", "shutdown", "taskkill /f /im svchost"
    );

    // 命令白名单：SystemTool.runCommand 只允许这些命令
    private static final Set<String> DEFAULT_ALLOWED_COMMANDS = Set.of(
            "dir", "echo", "type", "find", "findstr", "whoami", "hostname",
            "ping", "ipconfig", "netstat", "tasklist",
            "curl", "date", "time", "cd", "set", "where", "ver",
            "systeminfo", "wmic", "nslookup", "tracert", "path",
            "help", "clip", "sort", "more"
    );

    private final List<String> allowedPaths;
    private final Set<String> allowedCommands;

    public ToolSafety(
            @Value("${agent.safety.allowed-paths:${user.home},${user.dir}}") String paths,
            @Value("${agent.safety.allowed-commands:}") String extraCommands) {
        this.allowedPaths = List.of(paths.split(","));
        // 合并默认白名单 + 配置文件中额外允许的命令
        this.allowedCommands = new java.util.HashSet<>(DEFAULT_ALLOWED_COMMANDS);
        if (extraCommands != null && !extraCommands.isEmpty()) {
            Arrays.stream(extraCommands.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .forEach(this.allowedCommands::add);
        }
    }

    // ==================== 路径安全 ====================

    public boolean isPathAllowed(String filePath) {
        Path p = Path.of(filePath).toAbsolutePath().normalize();
        return allowedPaths.stream().anyMatch(allowed -> p.startsWith(Path.of(allowed.trim())));
    }

    // ==================== 命令安全 ====================

    public boolean isCommandBlocked(String command) {
        String lower = command.toLowerCase();
        return BLOCKED_COMMANDS.stream().anyMatch(lower::contains);
    }

    /**
     * 检查命令是否在允许的白名单中。
     * 提取命令的第一个词（不区分大小写）进行匹配。
     */
    public boolean isCommandAllowed(String command) {
        if (command == null || command.isBlank()) return false;
        String cmd = command.trim().split("\\s+")[0].toLowerCase();
        // 去掉可能的前缀路径，如 C:\Windows\System32\ping.exe -> ping
        int lastSep = Math.max(cmd.lastIndexOf('/'), cmd.lastIndexOf('\\'));
        if (lastSep >= 0) {
            cmd = cmd.substring(lastSep + 1);
        }
        // 去掉 .exe/.bat/.cmd 后缀
        if (cmd.endsWith(".exe") || cmd.endsWith(".bat") || cmd.endsWith(".cmd")) {
            cmd = cmd.substring(0, cmd.length() - 4);
        }
        return allowedCommands.contains(cmd);
    }
}
