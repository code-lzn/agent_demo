package com.limou.agent_demo.decision.model;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 一次 ReAct 推理-行动周期记录。
 * <p>
 * ReAct = Reasoning + Acting，是 LLM Agent 的核心决策范式：
 * <ol>
 *   <li><b>Thought（思考）</b>：分析当前情况，决定下一步做什么</li>
 *   <li><b>Action（行动）</b>：调用工具或给出最终回答</li>
 *   <li><b>Observation（观察）</b>：工具返回的结果</li>
 * </ol>
 * 多个 Cycle 组成一个完整的任务执行过程。
 *
 * @author lubo
 * @since 2026-07-25
 */
@Data
@Builder
public class ReActCycle {

    /** 周期序号（从 1 开始） */
    private int cycleNumber;

    /** 思考 — LLM 的推理文本 */
    private String thought;

    /** 行动 — 工具名称，或 {@code "final_answer"} 表示最终回答 */
    private String action;

    /** 行动输入 — 传给工具的参数（JSON 字符串或纯文本） */
    private String actionInput;

    /** 观察 — 工具返回的结果 */
    private String observation;

    /** 是否为终止周期（给出最终回答，不再继续） */
    @Builder.Default
    private boolean isFinal = false;

    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    // ---- 便捷方法 ----

    public boolean isToolCall() {
        return action != null && !"final_answer".equals(action);
    }

    /** 格式化为日志友好的摘要 */
    public String toSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Cycle #").append(cycleNumber).append(" ===\n");
        if (thought != null && !thought.isEmpty()) {
            sb.append("  Thought: ").append(truncate(thought, 200)).append("\n");
        }
        if (action != null) {
            sb.append("  Action: ").append(action);
            if (actionInput != null) {
                sb.append("(").append(truncate(actionInput, 100)).append(")");
            }
            sb.append("\n");
        }
        if (observation != null && !observation.isEmpty()) {
            sb.append("  Observation: ").append(truncate(observation, 200)).append("\n");
        }
        return sb.toString();
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }
}
