package com.limou.agent_demo.decision;

/**
 * Agent 状态机
 *
 * 状态流转：
 *   PERCEIVING → DECIDING → EXECUTING → DECIDING → EXECUTING → ... → COMPLETED
 *                                                                  → FAILED
 *                                                                  → BLOCKED
 */
public enum AgentState {
    PERCEIVING,  // 感知中（处理输入）
    DECIDING,    // 决策中（LLM 思考）
    EXECUTING,   // 执行中（调工具）
    COMPLETED,   // 已完成（正常结束）
    FAILED,      // 失败（异常）
    BLOCKED      // 被安全拦截
}
