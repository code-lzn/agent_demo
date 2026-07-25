package com.limou.agent_demo.decision;

/**
 * 工具调用请求 —— LLM 决定调用的具体工具和参数
 */
public class ToolCallRequest {

    private final String id;           // 调用的唯一 ID（LLM 生成，用于关联结果）
    private final String toolName;     // 工具名，如 "openApp"
    private final String arguments;    // 参数 JSON 字符串

    public ToolCallRequest(String id, String toolName, String arguments) {
        this.id = id;
        this.toolName = toolName;
        this.arguments = arguments;
    }

    // --- getters ---
    public String getId() { return id; }
    public String getToolName() { return toolName; }
    public String getArguments() { return arguments; }

    @Override
    public String toString() {
        return "ToolCallRequest{" +
                "id='" + id + '\'' +
                ", toolName='" + toolName + '\'' +
                ", arguments='" + arguments + '\'' +
                '}';
    }
}
