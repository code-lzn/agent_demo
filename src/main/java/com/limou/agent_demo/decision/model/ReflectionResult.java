package com.limou.agent_demo.decision.model;

import lombok.Builder;
import lombok.Data;

/**
 * 反思结果 — {@code ResultReflector} 在一次或多次执行后分析任务完成情况。
 * <p>
 * 反思结果驱动决策引擎的下一步行为：
 * <ul>
 *   <li>{@code complete=true} → 任务完成，返回最终结果</li>
 *   <li>{@code needsReplan=true} → 当前计划不可行，需要重新规划</li>
 *   <li>两者都为 false → 继续执行下一步</li>
 * </ul>
 *
 * @author lubo
 * @since 2026-07-25
 */
@Data
@Builder
public class ReflectionResult {

    /** 任务是否已完成 */
    private boolean complete;

    /** 是否需要重新规划（当前方案不可行） */
    @Builder.Default
    private Boolean needsReplan = false;

    /** 执行摘要 — 已完成的工作总结 */
    private String summary;

    /** 建议下一步行动（如果未完成） */
    private String nextAction;

    /** 完成置信度 0.0 ~ 1.0 */
    @Builder.Default
    private double confidence = 0.0;

    /** 如果有失败，记录原因 */
    private String failureReason;

    /** 是否建议请求用户澄清（任务描述不够明确） */
    @Builder.Default
    private Boolean needsUserClarification = false;

    /** 请求用户澄清的具体问题 */
    private String clarificationQuestion;

    // ---- 便捷方法 ----

    public boolean shouldContinue() {
        return !complete && !needsReplan && !needsUserClarification;
    }

    public boolean shouldStop() {
        return complete || needsUserClarification;
    }

    public String toDecisionSummary() {
        if (complete) {
            return "✅ 任务完成 (置信度: " + String.format("%.0f%%", confidence * 100) + ")";
        }
        if (needsReplan) {
            return "🔄 需要重新规划: " + (failureReason != null ? failureReason : "未知原因");
        }
        if (needsUserClarification) {
            return "❓ 需要用户澄清: " + (clarificationQuestion != null ? clarificationQuestion : "");
        }
        return "➡️ 继续执行: " + (nextAction != null ? nextAction : "下一步");
    }
}
