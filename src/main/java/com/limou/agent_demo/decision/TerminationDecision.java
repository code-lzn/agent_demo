package com.limou.agent_demo.decision;

/**
 * 终止判断结果 —— 是否应该停止 Agent 循环
 */
public class TerminationDecision {

    private final boolean shouldStop;
    private final String reason;

    private TerminationDecision(boolean shouldStop, String reason) {
        this.shouldStop = shouldStop;
        this.reason = reason;
    }

    public static TerminationDecision stop(String reason) {
        return new TerminationDecision(true, reason);
    }

    public static TerminationDecision continue_() {
        return new TerminationDecision(false, null);
    }

    // --- getters ---
    public boolean isShouldStop() { return shouldStop; }
    public String getReason() { return reason; }
}
