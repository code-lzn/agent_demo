package com.limou.agent_demo.decision;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 终止判断 —— 判断是否应该停止 Agent 循环
 *
 * 终止条件（满足任一即停）：
 *   1. 已达最大轮数（MAX_ROUNDS = 10）
 *   2. 本轮 LLM 直接回答了
 *   3. 连续 2 轮调同一个工具且参数相同（死循环检测）
 *   4. 本轮无任何有效输出（LLM 抽风）
 *   5. 已超时（> 60 秒）
 */
@Component
public class TerminationEvaluator {

    private static final Logger log = LoggerFactory.getLogger(TerminationEvaluator.class);

    /** 最大决策轮数 */
    public static final int MAX_ROUNDS = 10;

    /** 超时时间（毫秒） */
    private static final long TIMEOUT_MS = 60_000;

    /**
     * 判断是否应该终止 Agent 循环
     *
     * @param session       当前会话状态
     * @param decision      本轮决策结果
     * @param previousCalls 历史工具调用记录（用于死循环检测）
     * @return 终止判断结果
     */
    public TerminationDecision shouldTerminate(
            AgentSession session,
            Decision decision,
            List<ToolCallRequest> previousCalls) {

        // 条件 1：超过最大轮数
        if (session.getCurrentRound() >= MAX_ROUNDS) {
            log.warn("终止: 已达最大轮数 {}", MAX_ROUNDS);
            return TerminationDecision.stop("已达最大决策轮数 " + MAX_ROUNDS);
        }

        // 条件 2：LLM 直接回答了
        if (decision.isAnswer()) {
            log.debug("终止: LLM 已生成回答");
            return TerminationDecision.stop("LLM 已生成回答");
        }

        // 条件 3：死循环检测 —— 连续 2 轮调同一个工具且参数相同
        if (detectLoop(decision, previousCalls)) {
            log.warn("终止: 检测到工具调用死循环");
            return TerminationDecision.stop("检测到工具调用循环，已自动终止");
        }

        // 条件 4：LLM 无有效输出
        if (decision.isNone()) {
            log.warn("终止: LLM 无有效输出");
            return TerminationDecision.stop("LLM 无有效输出");
        }

        // 条件 5：超时
        if (session.getElapsed() > TIMEOUT_MS) {
            log.warn("终止: 超时 ({}ms)", session.getElapsed());
            return TerminationDecision.stop("处理超时");
        }

        return TerminationDecision.continue_();
    }

    /**
     * 死循环检测 —— 连续 2 轮调同一个工具且参数相同
     */
    private boolean detectLoop(Decision decision, List<ToolCallRequest> previousCalls) {
        if (!decision.isToolCall()
                || decision.getToolCalls() == null
                || decision.getToolCalls().isEmpty()
                || previousCalls.size() < 2) {
            return false;
        }

        ToolCallRequest current = decision.getToolCalls().get(0);
        ToolCallRequest prev1 = previousCalls.get(previousCalls.size() - 1);
        ToolCallRequest prev2 = previousCalls.get(previousCalls.size() - 2);

        return current.getToolName().equals(prev1.getToolName())
                && current.getArguments().equals(prev1.getArguments())
                && current.getToolName().equals(prev2.getToolName());
    }
}
