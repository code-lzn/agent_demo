package com.limou.agent_demo.tool;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

@Component
public class ToolSafety {

    private static final Set<String> BLOCKED_COMMANDS = Set.of(
            "format", "del /f", "rm -rf", "shutdown", "taskkill /f /im svchost"
    );

    private static final Set<String> BLOCKED_NETWORKS = Set.of(
            "localhost", "127.0.0.1", "0.0.0.0", "[::1]", "169.254", "10.", "172.16.",
            "172.17.", "172.18.", "172.19.", "172.20.", "172.21.", "172.22.", "172.23.",
            "172.24.", "172.25.", "172.26.", "172.27.", "172.28.", "172.29.", "172.30.",
            "172.31.", "192.168."
    );

    private static final long MAX_RESPONSE_BYTES = 1024 * 1024; // 1MB

    private final List<String> allowedPaths;
    private final List<String> allowedCommands;

    public ToolSafety(
            @Value("${agent.safety.allowed-paths:${user.home},${user.dir}}") String paths,
            @Value("${agent.safety.allowed-commands:dir,echo,type,curl,ipconfig,ping,whoami,netstat,find,findstr,tasklist,where,ver,systeminfo,nslookup,tracert,pathping,getmac,arp,route}") String commands) {
        this.allowedPaths = List.of(paths.split(","));
        this.allowedCommands = List.of(commands.split(","));
    }

    // ========== 路径安全 ==========

    public boolean isPathAllowed(String filePath) {
        Path p = Path.of(filePath).toAbsolutePath().normalize();
        return allowedPaths.stream().anyMatch(allowed -> p.startsWith(Path.of(allowed.trim())));
    }

    // ========== 命令安全 ==========

    public boolean isCommandBlocked(String command) {
        String lower = command.toLowerCase();
        return BLOCKED_COMMANDS.stream().anyMatch(lower::contains);
    }

    public boolean isCommandAllowed(String command) {
        if (isCommandBlocked(command)) return false;
        String baseCmd = command.trim().split("\\s+")[0].toLowerCase();
        return allowedCommands.stream().anyMatch(c -> c.trim().equalsIgnoreCase(baseCmd));
    }

    // ========== URL 安全（防 SSRF） ==========

    public boolean isUrlAllowed(String url) {
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
                return false;
            }
            String host = uri.getHost();
            if (host == null) return false;
            String lowerHost = host.toLowerCase();
            for (String blocked : BLOCKED_NETWORKS) {
                if (lowerHost.contains(blocked)) return false;
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public long getMaxResponseBytes() {
        return MAX_RESPONSE_BYTES;
    }
}
