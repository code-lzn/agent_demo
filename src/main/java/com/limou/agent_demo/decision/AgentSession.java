package com.limou.agent_demo.decision;

import java.util.ArrayList;
import java.util.List;

/**
 * Agent 会话状态 —— 跟踪一次对话的完整状态
 *
 * 职责：
 * - 记录当前轮次、工具调用次数
 * - 提供工具调用频率控制（防止死循环）
 * - 记录已调用的工具列表
 */
public class AgentSession {

    /** 同一工具最大调用次数 */
    private static final int MAX_CALLS_PER_TOOL = 3;
    /** 本轮总计最大工具调用次数 */
    private static final int MAX_TOTAL_TOOL_CALLS = 10;

    private final String conversationId;
    private AgentState state;
    private int currentRound;
    private int toolCallCount;
    private final List<String> calledTools;
    private String lastError;
    private final long startTime;

    public AgentSession(String conversationId) {
        this.conversationId = conversationId;
        this.state = AgentState.PERCEIVING;
        this.currentRound = 0;
        this.toolCallCount = 0;
        this.calledTools = new ArrayList<>();
        this.startTime = System.currentTimeMillis();
    }

    // --- 工具调用频率控制 ---

    /**
     * 检查是否允许调用某个工具
     */
    public boolean canCallTool(String toolName) {
        long count = calledTools.stream().filter(t -> t.equals(toolName)).count();
        if (count >= MAX_CALLS_PER_TOOL) {
            return false;
        }
        return toolCallCount < MAX_TOTAL_TOOL_CALLS;
    }

    public void recordToolCall(String toolName) {
        calledTools.add(toolName);
        toolCallCount++;
    }

    // --- getters & setters ---

    public String getConversationId() { return conversationId; }

    public AgentState getState() { return state; }
    public void setState(AgentState state) { this.state = state; }

    public int getCurrentRound() { return currentRound; }
    public void setCurrentRound(int currentRound) { this.currentRound = currentRound; }
    public void incrementRound() { this.currentRound++; }

    public int getToolCallCount() { return toolCallCount; }

    public List<String> getCalledTools() { return calledTools; }

    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }

    public long getStartTime() { return startTime; }

    /** 获取已消耗的时间（毫秒） */
    public long getElapsed() {
        return System.currentTimeMillis() - startTime;
    }
}
