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

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 决策引擎 —— 整个决策层的总协调器，是 AI Agent 的"大脑"。
 * <p>
 * 决策引擎将 Plan → Execute → Reflect 三个阶段串联成一个完整的智能循环：
 *
 * <pre>
 * ┌─────────────────────────────────────────────────────┐
 * │                 DecisionEngine                        │
 * │                                                       │
 * │  用户输入                                              │
 * │    │                                                  │
 * │    ▼                                                  │
 * │  ┌──────────┐                                         │
 * │  │ 1. PLAN  │  TaskPlanner 分析意图 → 生成执行计划      │
 * │  └────┬─────┘                                         │
 * │       │                                               │
 * │       ▼                                               │
 * │  ┌──────────┐    ┌──────────────────────────────┐     │
 * │  │ 2. EXEC  │───▶│ ReActExecutor 单轮执行         │     │
 * │  └────┬─────┘    │  · Thought → Action → Observe │     │
 * │       │          │  · Spring AI 自动工具调用      │     │
 * │       │          └──────────────────────────────┘     │
 * │       ▼                                               │
 * │  ┌──────────┐                                         │
 * │  │ 3. REFLECT│ ResultReflector 分析结果                │
 * │  └────┬─────┘                                         │
 * │       │                                               │
 * │       ├── 任务完成 → 返回最终结果                       │
 * │       ├── 需要重新规划 → 回到步骤 1                     │
 * │       └── 需要继续 → 回到步骤 2（下一轮）               │
 * └─────────────────────────────────────────────────────┘
 * </pre>
 *
 * <h3>安全控制</h3>
 * <ul>
 *   <li><b>最大轮次</b>：默认 5 轮，防止无限循环消耗 Token</li>
 *   <li><b>单轮超时</b>：每轮最长 180 秒，防止 LLM 挂起</li>
 *   <li><b>错误隔离</b>：单轮失败不影响整体流程，自动尝试修复</li>
 * </ul>
 *
 * @author lubo
 * @since 2026-07-25
 */
@Component
public class DecisionEngine {

    private static final Logger log = LoggerFactory.getLogger(DecisionEngine.class);

    /** 默认最大执行轮次 */
    private static final int DEFAULT_MAX_ROUNDS = 5;

    /** 单轮执行超时（秒） */
    private static final int ROUND_TIMEOUT_SECONDS = 180;

    private final TaskPlanner planner;
    private final ReActExecutor reActExecutor;
    private final ResultReflector reflector;

    public DecisionEngine(TaskPlanner planner,
                          ReActExecutor reActExecutor,
                          ResultReflector reflector) {
        this.planner = planner;
        this.reActExecutor = reActExecutor;
        this.reflector = reflector;
    }

    /**
     * 对用户消息进行完整的决策-执行流程，返回流式事件。
     * <p>
     * 这是决策层的主入口。调用方订阅返回的 Flux，
     * 即可实时接收每个阶段产生的事件。
     *
     * @param userMessage    用户输入
     * @param conversationId 会话 ID（用于上下文记忆）
     * @return 流式的 ChatEvent 序列
     */
    public Flux<ChatEvent> decide(String userMessage, String conversationId) {
        log.info("[DecisionEngine] 开始决策流程, conversationId={}, message={}",
                conversationId, truncate(userMessage, 80));

        return Flux.create(sink -> {
            try {
                // ============ Phase 1: Planning ============
                sink.next(ChatEvent.thinking());
                log.debug("[DecisionEngine] Phase 1: Planning");

                ExecutionPlan plan = planner.plan(userMessage);
                log.info("[DecisionEngine] 计划生成完成: goal={}, steps={}",
                        truncate(plan.getGoal(), 60), plan.getTotalSteps());

                // 将计划以格式化消息推送给客户端
                sink.next(ChatEvent.message(formatPlanMessage(plan)));

                // ============ Phase 2-3: Execute + Reflect Loop ============
                executeLoop(plan, userMessage, conversationId, sink);

            } catch (Exception e) {
                log.error("[DecisionEngine] 决策流程异常", e);
                sink.next(ChatEvent.error("决策引擎异常: " + e.getMessage()));
                sink.complete();
            }
        });
    }

    // ==================== 循环执行 ====================

    /**
     * 执行-反思主循环。
     * <p>
     * 使用 CountDownLatch 在异步流中实现"等待单轮完成→反思→决定是否继续"的同步语义，
     * 同时保持每轮内部的流式输出（实时推送给客户端）。
     */
    private void executeLoop(ExecutionPlan plan,
                             String userMessage,
                             String conversationId,
                             reactor.core.publisher.FluxSink<ChatEvent> sink) {
        int round = 0;
        boolean done = false;

        while (!done && round < plan.getMaxRounds() && !sink.isCancelled()) {
            round++;
            log.info("[DecisionEngine] 开始第 {}/{} 轮执行", round, plan.getMaxRounds());

            final int currentRound = round;

            CountDownLatch latch = new CountDownLatch(1);
            StringBuilder roundResponse = new StringBuilder();
            AtomicBoolean roundSuccess = new AtomicBoolean(true);

            // 首轮：用户消息 + 计划步骤（让 LLM 知道要按计划执行）
            // 后续轮：简短续接指令
            String prompt = (currentRound == 1)
                    ? buildFirstRoundPrompt(userMessage, plan)
                    : buildContinuePrompt(plan, currentRound);

            // 执行一轮（流式输出给客户端，同时收集完整响应）
            var subscription = reActExecutor.executeRound(prompt, conversationId, currentRound, currentRound == 1)
                    .doOnNext(event -> {
                        if ("message".equals(event.getType()) && event.getData() != null) {
                            roundResponse.append(event.getData().toString());
                        }
                        sink.next(event);
                    })
                    .doOnError(error -> {
                        log.error("[DecisionEngine] 第 {} 轮出错: {}", currentRound, error.getMessage());
                        roundSuccess.set(false);
                        sink.next(ChatEvent.error("第 " + currentRound + " 轮出错: " + error.getMessage()));
                        latch.countDown();
                    })
                    .doOnComplete(() -> latch.countDown())
                    .subscribe();

            // 等待本轮完成（阻塞当前线程但不阻塞事件流）
            try {
                boolean completed = latch.await(ROUND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                if (!completed) {
                    log.warn("[DecisionEngine] 第 {} 轮超时", currentRound);
                    subscription.dispose(); // 取消本轮订阅
                    sink.next(ChatEvent.error("第 " + currentRound + " 轮执行超时（" + ROUND_TIMEOUT_SECONDS + "秒）"));
                    break;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                subscription.dispose();
                log.warn("[DecisionEngine] 第 {} 轮被中断", currentRound);
                break;
            }

            if (!roundSuccess.get()) {
                // 单轮失败，尝试修复
                sink.next(ChatEvent.thinking());
                plan = planner.replan(plan, "第 " + currentRound + " 轮执行失败: " + roundResponse);
                sink.next(ChatEvent.message("\n🔄 执行遇到问题，已调整计划重试...\n"));
                continue;
            }

            // ============ Phase 3: Reflection ============
            log.debug("[DecisionEngine] Phase 3: Reflection (round {})", currentRound);

            ReflectionResult reflection = reflector.reflect(
                    userMessage, roundResponse.toString());

            log.info("[DecisionEngine] 反思结果: {}", reflection.toDecisionSummary());

            // 通知客户端反思结果
            sink.next(ChatEvent.message("\n\n📋 **反思**: " + reflection.toDecisionSummary() + "\n"));

            if (reflection.shouldStop()) {
                // 任务完成或需要用户输入
                done = true;
                if (Boolean.TRUE.equals(reflection.getNeedsUserClarification())
                        && reflection.getClarificationQuestion() != null) {
                    sink.next(ChatEvent.message(
                            "\n❓ " + reflection.getClarificationQuestion() + "\n"));
                }
            } else if (Boolean.TRUE.equals(reflection.getNeedsReplan())) {
                // 当前方案不可行，重新规划
                sink.next(ChatEvent.thinking());
                String failReason = reflection.getFailureReason() != null
                        ? reflection.getFailureReason()
                        : "反思判定需要重新规划";
                plan = planner.replan(plan, failReason);
                sink.next(ChatEvent.message(
                        "\n🔄 **调整计划**\n" + formatPlanMessage(plan)));
            } else {
                // 需要继续执行
                sink.next(ChatEvent.thinking());
            }
        }

        // ============ 循环结束 ============
        if (!done && !sink.isCancelled()) {
            sink.next(ChatEvent.message(
                    "\n\n---\n⚠️ 已达到最大执行轮次（" + plan.getMaxRounds()
                            + "），任务可能未完全完成。你可以继续指示我完成剩余工作。\n"));
        }

        log.info("[DecisionEngine] 决策流程结束, 总轮次={}, 完成={}", round, done);
        sink.next(ChatEvent.done(conversationId, UUID.randomUUID().toString()));
        sink.complete();
    }

    // ==================== 辅助方法 ====================

    /**
     * 格式化执行计划为可读消息。
     */
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

    /**
     * 首轮消息：将执行计划嵌入用户消息，让 LLM 按步骤执行并汇报进度。
     */
    private String buildFirstRoundPrompt(String userMessage, ExecutionPlan plan) {
        if (plan.getSteps().isEmpty()) {
            return userMessage;
        }
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

    /**
     * 构建后续轮次的简短续接提示词。
     * ChatMemory 已保留完整对话历史，此处只需轻量提醒。
     */
    private String buildContinuePrompt(ExecutionPlan plan, int currentRound) {
        return "请继续完成用户的任务。如果已全部完成，直接给出总结。";
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }
}
