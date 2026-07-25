package com.limou.agent_demo.decision;

import com.limou.agent_demo.tool.ToolSafety;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Agent 安全控制器
 *
 * 五层防护（在 ToolSafety 底层守卫之上增强）：
 *   ① Prompt 注入检测     → 用户试图篡改系统提示词
 *   ② 工具参数校验        → LLM 传了非法参数
 *   ③ 敏感操作确认        → 写入文件/关闭进程需要确认
 *   ④ 工具调用频率控制    → 防止死循环调工具
 *   ⑤ 输出内容过滤        → LLM 回复了敏感信息
 *
 * 与 ToolSafety 的关系：
 *   - ToolSafety（已存在）：底层守卫——路径白名单、命令黑名单
 *   - AgentSecurityGuard（新增）：上层策略——注入检测、参数校验、操作确认、频率控制、输出过滤
 *   两者协作，形成纵深防御。
 */
@Component
public class AgentSecurityGuard {

    private static final Logger log = LoggerFactory.getLogger(AgentSecurityGuard.class);

    /** Prompt 注入关键词黑名单 */
    private static final String[] INJECTION_PATTERNS = {
            "忽略系统提示", "忽略之前的指令", "忘记你之前的",
            "你被越狱了", "扮演另一个角色", "你是一个",
            "system prompt", "ignore all", "ignore previous",
            "you are now", "you are a", "you are not",
            "bypass", "jailbreak"
    };

    /** 敏感操作工具列表（需用户 confirm=true 确认） */
    private static final java.util.Set<String> DESTRUCTIVE_TOOLS = java.util.Set.of(
            "writeFile", "closeApp", "openAppWithArgs",
            "deleteFile", "deleteDir", "executeCommand",
            "closeWindow", "moveFile"
    );

    /** 敏感数据类型 */
    private static final java.util.regex.Pattern PHONE_PATTERN =
            java.util.regex.Pattern.compile("1[3-9]\\d{9}");
    private static final java.util.regex.Pattern API_KEY_PATTERN =
            java.util.regex.Pattern.compile("sk-[a-zA-Z0-9]{20,}");
    private static final java.util.regex.Pattern PASSWORD_PATTERN =
            java.util.regex.Pattern.compile("password\\s*[=:]+\\s*['\"]?[^'\"\\s]{6,}", java.util.regex.Pattern.CASE_INSENSITIVE);

    private final ToolSafety toolSafety;

    public AgentSecurityGuard(ToolSafety toolSafety) {
        this.toolSafety = toolSafety;
    }

    // ========================================================================
    // 防护 ①：Prompt 注入检测
    // ========================================================================

    /**
     * 检查用户输入是否包含 Prompt 注入/越狱尝试
     */
    public SecurityVerdict checkPromptInjection(String question) {
        if (question == null || question.isBlank()) {
            return SecurityVerdict.pass();
        }

        String lower = question.toLowerCase();

        for (String pattern : INJECTION_PATTERNS) {
            if (lower.contains(pattern)) {
                log.warn("检测到 Prompt 注入尝试: pattern='{}', question='{}'",
                        pattern, truncate(question, 80));
                return SecurityVerdict.block("检测到非法输入模式");
            }
        }

        return SecurityVerdict.pass();
    }

    // ========================================================================
    // 防护 ②：工具参数校验
    // ========================================================================

    /**
     * 校验工具调用参数是否合法
     */
    public SecurityVerdict validateToolArgs(ToolCallRequest request) {
        if (request == null) {
            return SecurityVerdict.block("工具调用请求为空");
        }

        String toolName = request.getToolName();
        String args = request.getArguments();

        // 基本校验
        if (args == null || args.trim().isEmpty()) {
            return SecurityVerdict.block("工具参数不能为空");
        }

        if (args.length() > 2000) {
            return SecurityVerdict.block("工具参数超长（上限 2000 字符）");
        }

        // 按工具特定规则校验
        switch (toolName) {
            case "readFile":
            case "readFileLines":
            case "countLines":
            case "writeFile":
            case "searchInFile":
                if (!toolSafety.isPathAllowed(parseStringArg(args, "filePath"))) {
                    return SecurityVerdict.block("文件路径不在允许的白名单内");
                }
                break;

            case "listDir":
                if (!toolSafety.isPathAllowed(parseStringArg(args, "dirPath"))) {
                    return SecurityVerdict.block("目录路径不在允许的白名单内");
                }
                break;

            case "openApp":
            case "openAppWithArgs":
                String appPath = parseStringArg(args, "appPath");
                if (appPath != null && toolSafety.isCommandBlocked(appPath)) {
                    return SecurityVerdict.block("禁止启动该程序");
                }
                break;

            case "closeApp":
                String processName = parseStringArg(args, "processName");
                if (processName != null && toolSafety.isCommandBlocked("taskkill /f /im " + processName)) {
                    return SecurityVerdict.block("禁止关闭该进程");
                }
                break;

            case "executeCommand":
                String command = parseStringArg(args, "command");
                if (command != null && toolSafety.isCommandBlocked(command)) {
                    return SecurityVerdict.block("禁止执行该命令");
                }
                break;
        }

        return SecurityVerdict.pass();
    }

    // ========================================================================
    // 防护 ③：敏感操作确认
    // ========================================================================

    /**
     * 检查工具调用是否需要用户二次确认
     *
     * @param toolName 工具名
     * @param confirm  用户请求中是否携带 confirm=true
     * @return 如果操作需要确认但未确认，返回 block
     */
    public SecurityVerdict checkDestructiveOp(String toolName, boolean confirm) {
        if (DESTRUCTIVE_TOOLS.contains(toolName)) {
            if (!confirm) {
                log.warn("敏感操作未确认: tool={}", toolName);
                return SecurityVerdict.block("该操作需要用户确认（请设置 confirm=true）");
            }
        }
        return SecurityVerdict.pass();
    }

    // ========================================================================
    // 防护 ④：工具调用频率控制
    // ========================================================================

    /**
     * 检查工具调用频率是否超限（委托给 AgentSession）
     */
    public SecurityVerdict checkToolCallFrequency(AgentSession session, String toolName) {
        if (!session.canCallTool(toolName)) {
            log.warn("工具调用超限: tool={}, calledTimes={}",
                    toolName,
                    session.getCalledTools().stream().filter(t -> t.equals(toolName)).count());
            return SecurityVerdict.block("工具调用次数超限");
        }
        return SecurityVerdict.pass();
    }

    // ========================================================================
    // 防护 ⑤：输出内容过滤
    // ========================================================================

    /**
     * 过滤 LLM 回答中的敏感信息
     */
    public String filterOutput(String answer) {
        if (answer == null) return null;

        // 过滤手机号
        answer = PHONE_PATTERN.matcher(answer).replaceAll("****");

        // 过滤 API Key
        answer = API_KEY_PATTERN.matcher(answer).replaceAll("sk-****");

        // 过滤明文的密码配置
        answer = PASSWORD_PATTERN.matcher(answer).replaceAll("password=****");

        return answer;
    }

    // ========================================================================
    // 辅助方法
    // ========================================================================

    /**
     * 从参数 JSON 中提取指定字段的字符串值（简易 JSON 解析）
     */
    private String parseStringArg(String args, String fieldName) {
        if (args == null || fieldName == null) return null;
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "\"" + fieldName + "\"\\s*:\\s*\"([^\"]+)\"");
        java.util.regex.Matcher matcher = pattern.matcher(args);
        return matcher.find() ? matcher.group(1) : null;
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return null;
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }
}
