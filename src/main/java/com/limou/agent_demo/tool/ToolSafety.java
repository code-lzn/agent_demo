package com.limou.agent_demo.tool;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

@Component
public class ToolSafety {

    private static final Set<String> BLOCKED_COMMANDS = Set.of(
            "format", "del /f", "rm -rf", "shutdown", "taskkill /f /im svchost"
    );

    private final List<String> allowedPaths;

    public ToolSafety(@Value("${agent.safety.allowed-paths:${user.home},${user.dir}}") String paths) {
        this.allowedPaths = List.of(paths.split(","));
    }

    public boolean isPathAllowed(String filePath) {
        Path p = Path.of(filePath).toAbsolutePath().normalize();
        return allowedPaths.stream().anyMatch(allowed -> p.startsWith(Path.of(allowed.trim())));
    }

    public boolean isCommandBlocked(String command) {
        String lower = command.toLowerCase();
        return BLOCKED_COMMANDS.stream().anyMatch(lower::contains);
    }
}
