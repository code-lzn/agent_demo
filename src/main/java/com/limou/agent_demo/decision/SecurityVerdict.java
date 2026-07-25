package com.limou.agent_demo.decision;

/**
 * 安全验证结果
 */
public class SecurityVerdict {

    private final boolean blocked;
    private final String reason;

    private SecurityVerdict(boolean blocked, String reason) {
        this.blocked = blocked;
        this.reason = reason;
    }

    public static SecurityVerdict pass() {
        return new SecurityVerdict(false, null);
    }

    public static SecurityVerdict block(String reason) {
        return new SecurityVerdict(true, reason);
    }

    // --- getters ---
    public boolean isBlocked() { return blocked; }
    public String getReason() { return reason; }
}
