package com.limou.agent_demo.decision;

import java.util.List;

/**
 * 决策结果 —— LLM 经过思考后给出的决定
 *
 * 三种类型：
 *   1. ANSWER    → 直接回答用户，内容是文本
 *   2. TOOL_CALL → 需要调用工具，内容是工具调用请求列表
 *   3. NONE      → 异常情况，LLM 既没回答也没调工具
 */
public class Decision {

    public enum Type {
        ANSWER,
        TOOL_CALL,
        NONE
    }

    private final Type type;
    private final String answer;
    private final List<ToolCallRequest> toolCalls;

    private Decision(Type type, String answer, List<ToolCallRequest> toolCalls) {
        this.type = type;
        this.answer = answer;
        this.toolCalls = toolCalls;
    }

    /** 创建"直接回答"决策 */
    public static Decision answer(String content) {
        return new Decision(Type.ANSWER, content, null);
    }

    /** 创建"调用工具"决策 */
    public static Decision toolCall(List<ToolCallRequest> toolCalls) {
        return new Decision(Type.TOOL_CALL, null, toolCalls);
    }

    /** 创建"无决策"（LLM 既没回答也没调工具，异常情况） */
    public static Decision none() {
        return new Decision(Type.NONE, null, null);
    }

    // --- getters ---
    public Type getType() { return type; }
    public String getAnswer() { return answer; }
    public List<ToolCallRequest> getToolCalls() { return toolCalls; }

    // --- convenience checks ---
    public boolean isAnswer() { return type == Type.ANSWER; }
    public boolean isToolCall() { return type == Type.TOOL_CALL; }
    public boolean isNone() { return type == Type.NONE; }

    @Override
    public String toString() {
        if (isAnswer()) return "ANSWER: " + (answer != null ? answer.substring(0, Math.min(50, answer.length())) : "");
        if (isToolCall()) return "TOOL_CALL: " + (toolCalls != null ? toolCalls.toString() : "");
        return "NONE";
    }
}
