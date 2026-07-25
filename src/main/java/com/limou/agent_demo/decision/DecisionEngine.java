package com.limou.agent_demo.decision;

import com.limou.agent_demo.decision.model.ExecutionPlan;
import com.limou.agent_demo.decision.model.PlanStep;
import com.limou.agent_demo.decision.model.ReflectionResult;
import com.limou.agent_demo.decision.planner.TaskPlanner;
import com.limou.agent_demo.decision.react.ReActExecutor;
import com.limou.agent_demo.decision.reflector.ResultReflector;
import com.limou.agent_demo.dto.ChatEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.UUID;

/**
 * 决策引擎 —— 整个决策层的总协调器，是 AI Agent 的"大脑"。
 * <p>
 * 纯响应式实现：Plan → Execute(流式) → Reflect，全程无阻塞。
 * 使用 Flux.concat 和递归组合实现 Plan→Execute→Reflect 循环，
 * 保证 Execute 阶段的内容逐字流式推送。
 */
@Component
public class DecisionEngine {

    private static final Logger log = LoggerFactory.getLogger(DecisionEngine.class);

    private static final int DEFAULT_MAX_ROUNDS = 5;
    private static final Duration ROUND_TIMEOUT = Duration.ofSeconds(180);

    private final TaskPlanner planner;
    private final ReActExecutor reActExecutor;
    private final ResultReflector reflector;
    private final AgentSecurityGuard securityGuard;

    public DecisionEngine(TaskPlanner planner,
                          ReActExecutor reActExecutor,
                          ResultReflector reflector,
                          AgentSecurityGuard securityGuard) {
        this.planner = planner;
        this.reActExecutor = reActExecutor;
        this.reflector = reflector;
        this.securityGuard = securityGuard;
    }

    /**
     * 对用户消息进行完整的决策-执行流程，返回流式事件。
     * 全程响应式，无阻塞等待。
     */
    public Flux<ChatEvent> decide(String userMessage, String conversationId, boolean confirm) {
        log.info("[DecisionEngine] 开始决策流程, conversationId={}", conversationId);

        // 安全①：Prompt 注入检测
        SecurityVerdict injectionCheck = securityGuard.checkPromptInjection(userMessage);
        if (injectionCheck.isBlocked()) {
            log.warn("[DecisionEngine] Prompt 注入拦截: {}", injectionCheck.getReason());
            return Flux.just(ChatEvent.error("安全拦截: " + injectionCheck.getReason()));
        }

        AgentSession session = new AgentSession(conversationId);

        // 1. Plan（同步，但立即 emit）
        ExecutionPlan plan = planner.plan(userMessage);
        log.info("[DecisionEngine] 计划生成: goal={}, steps={}",
                truncate(plan.getGoal(), 60), plan.getTotalSteps());

        // 2. Execute + Reflect 循环
        return Flux.concat(
                Flux.just(ChatEvent.thinking(), ChatEvent.message(formatPlanMessage(plan))),
                executeLoop(plan, userMessage, conversationId, confirm, session, 0)
        );
    }

    /**
     * 递归响应式执行循环：Execute(流式) → Reflect → 继续/结束。
     * 每轮内部使用 Flux.concat(executeEvents, reflectThenNext)，
     * 保证 execute 阶段是真正流式的。
     */
    private Flux<ChatEvent> executeLoop(ExecutionPlan plan,
                                         String userMessage,
                                         String conversationId,
                                         boolean confirm,
                                         AgentSession session,
                                         int round) {
        if (round >= plan.getMaxRounds()) {
            return Flux.just(ChatEvent.message(
                    "\n\n---\n⚠️ 已达到最大执行轮次（" + plan.getMaxRounds() + "）\n"));
        }

        session.incrementRound();
        int currentRound = round + 1;
        log.info("[DecisionEngine] 第 {}/{} 轮", currentRound, plan.getMaxRounds());

        String prompt = (currentRound == 1)
                ? buildFirstRoundPrompt(userMessage, plan)
                : buildContinuePrompt(plan, currentRound);

        StringBuilder roundResponse = new StringBuilder();

        // ── Execute（真正流式）──
        Flux<ChatEvent> executeEvents = reActExecutor.executeRound(
                        prompt, conversationId, currentRound, currentRound == 1)
                .timeout(ROUND_TIMEOUT)
                .doOnNext(event -> {
                    if ("message".equals(event.getType()) && event.getData() != null) {
                        roundResponse.append(event.getData().toString());
                    }
                })
                .onErrorResume(error -> {
                    log.warn("[DecisionEngine] 第 {} 轮异常: {}", currentRound, error.getMessage());
                    return Flux.just(ChatEvent.error("第 " + currentRound + " 轮出错: " + error.getMessage()));
                });

        // ── 执行完成后 → 反思 + 继续/结束 ──
        Flux<ChatEvent> reflectThenNext = Flux.defer(() -> {
            String responseText = roundResponse.toString();

            if (responseText.isBlank()) {
                // 空响应 → 重试
                log.warn("[DecisionEngine] 第 {} 轮无输出", currentRound);
                return Flux.just(ChatEvent.thinking(),
                        ChatEvent.message("\n🔄 执行未产生输出，调整计划重试...\n"));
            }

            // 反思
            ReflectionResult reflection = reflector.reflect(userMessage, responseText);
            String filteredSummary = securityGuard.filterOutput(reflection.toDecisionSummary());
            log.info("[DecisionEngine] 反思: {}", reflection.toDecisionSummary());

            // 判定后续动作
            Flux<ChatEvent> result = Flux.just(
                    ChatEvent.message("\n\n📋 **反思**: " + filteredSummary + "\n"));

            if (reflection.shouldStop()) {
                // 完成或需追问
                if (Boolean.TRUE.equals(reflection.getNeedsUserClarification())
                        && reflection.getClarificationQuestion() != null) {
                    String q = securityGuard.filterOutput(reflection.getClarificationQuestion());
                    result = Flux.concat(result, Flux.just(ChatEvent.message("\n❓ " + q + "\n")));
                }
                return Flux.concat(result,
                        Flux.just(ChatEvent.done(conversationId, UUID.randomUUID().toString())));

            } else if (Boolean.TRUE.equals(reflection.getNeedsReplan())) {
                // 重新规划
                String failReason = reflection.getFailureReason() != null
                        ? reflection.getFailureReason() : "反思判定需要重新规划";
                ExecutionPlan newPlan = planner.replan(plan, failReason);
                return Flux.concat(result,
                        Flux.just(ChatEvent.message("\n🔄 **调整计划**\n" + formatPlanMessage(newPlan))),
                        executeLoop(newPlan, userMessage, conversationId, confirm, session, currentRound));

            } else {
                // 继续下一轮
                return Flux.concat(result,
                        Flux.just(ChatEvent.thinking()),
                        executeLoop(plan, userMessage, conversationId, confirm, session, currentRound));
            }
        });

        return Flux.concat(executeEvents, reflectThenNext);
    }

    // ==================== 辅助方法 ====================

    private String formatPlanMessage(ExecutionPlan plan) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n## 📋 执行计划\n\n");
        sb.append("**目标**: ").append(plan.getGoal()).append("\n\n");
        if (plan.getReasoning() != null && !plan.getReasoning().isBlank()) {
            sb.append("**推理**: ").append(plan.getReasoning()).append("\n\n");
        }
        if (plan.getSteps().isEmpty()) {
            sb.append("（这是一个简单对话，无需分步执行）\n");
        } else {
            sb.append("**步骤**:\n");
            for (PlanStep step : plan.getSteps()) {
                String icon = switch (step.getStatus()) {
                    case COMPLETED -> "✅";
                    case FAILED -> "❌";
                    case SKIPPED -> "⏭️";
                    case IN_PROGRESS -> "🔄";
                    default -> "⬜";
                };
                sb.append(String.format("  %s %d. %s", icon, step.getOrder() + 1, step.getDescription()));
                if (step.getExpectedTool() != null) {
                    sb.append("  *(工具: ").append(step.getExpectedTool()).append(")*");
                }
                sb.append("\n");
            }
        }
        sb.append("\n---\n\n⚡ **开始执行**...\n");
        return sb.toString();
    }

    private String buildFirstRoundPrompt(String userMessage, ExecutionPlan plan) {
        if (plan.getSteps().isEmpty()) return userMessage;
        StringBuilder sb = new StringBuilder();
        sb.append("【执行计划 - 请逐步执行并在每步开始时用 📌 标注】\n");
        for (PlanStep step : plan.getSteps()) {
            sb.append(step.getOrder() + 1).append(". ").append(step.getDescription());
            if (step.getExpectedTool() != null) {
                sb.append("（使用工具: ").append(step.getExpectedTool()).append("）");
            }
            sb.append("\n");
        }
        sb.append("\n【用户请求】\n").append(userMessage);
        return sb.toString();
    }

    private String buildContinuePrompt(ExecutionPlan plan, int currentRound) {
        return "请继续完成用户的任务。如果已全部完成，直接给出总结。";
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }
}
