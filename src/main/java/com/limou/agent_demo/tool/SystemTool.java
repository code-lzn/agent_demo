package com.limou.agent_demo.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * 系统操作工具：命令执行、系统信息、环境变量、时间等。
 */
@Component
public class SystemTool {

    private final ToolSafety safety;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public SystemTool(ToolSafety safety) {
        this.safety = safety;
    }

    // ==================== 命令执行 ====================

    @Tool(description = "执行一条 Windows 命令行指令并返回输出结果。" +
            "出于安全考虑，仅允许执行白名单中的命令。" +
            "默认允许: dir, echo, type, find, findstr, whoami, hostname, ping, ipconfig, netstat, " +
            "tasklist, curl, date, time, cd, set, where, ver, systeminfo, wmic, nslookup, tracert, path")
    public String runCommand(@ToolParam(description = "要执行的命令（含参数），如 dir C:\\、ping baidu.com") String command) {
        if (!safety.isCommandAllowed(command)) {
            return "拒绝执行: 命令不在白名单中。如需使用 '" + command + "'，请联系管理员添加到 agent.safety.allowed-commands 配置";
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
            if (result.isEmpty()) return "命令执行完毕，无输出";
            // 截断过长输出
            if (result.length() > 8000) {
                result = result.substring(0, 8000) + "\n... (输出已截断，共 " + result.length() + " 字符)";
            }
            return result;
        } catch (Exception e) {
            return "命令执行失败: " + e.getMessage();
        }
    }

    // ==================== 时间 ====================

    @Tool(description = "获取当前日期和时间")
    public String getCurrentTime() {
        return "当前时间: " + LocalDateTime.now().format(FORMATTER);
    }

    // ==================== 环境变量 ====================

    @Tool(description = "读取系统环境变量的值，如 JAVA_HOME、PATH、USERNAME 等")
    public String getEnvVar(@ToolParam(description = "环境变量名称") String name) {
        String value = System.getenv(name);
        if (value == null) {
            return "环境变量 '" + name + "' 未设置";
        }
        // 对 PATH 类长变量截断显示
        if (value.length() > 2000) {
            return name + " = " + value.substring(0, 2000) + "\n... (共 " + value.length() + " 字符，已截断)";
        }
        return name + " = " + value;
    }

    @Tool(description = "列出所有系统环境变量（名称和值）")
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

    // ==================== 目录 ====================

    @Tool(description = "获取当前工作目录")
    public String getCurrentDir() {
        return "当前工作目录: " + System.getProperty("user.dir");
    }

    // ==================== 系统信息 ====================

    @Tool(description = "获取当前系统的基本信息：操作系统、Java 版本、CPU 核心数、可用内存等")
    public String getSystemInfo() {
        Runtime rt = Runtime.getRuntime();
        return "操作系统: " + System.getProperty("os.name") + " " + System.getProperty("os.version") + " (" + System.getProperty("os.arch") + ")\n"
                + "用户名: " + System.getProperty("user.name") + "\n"
                + "用户目录: " + System.getProperty("user.home") + "\n"
                + "Java 版本: " + System.getProperty("java.version") + " (" + System.getProperty("java.vendor") + ")\n"
                + "CPU 核心数: " + rt.availableProcessors() + "\n"
                + "JVM 最大内存: " + (rt.maxMemory() / 1024 / 1024) + " MB\n"
                + "JVM 当前已用内存: " + ((rt.totalMemory() - rt.freeMemory()) / 1024 / 1024) + " MB";
    }
}