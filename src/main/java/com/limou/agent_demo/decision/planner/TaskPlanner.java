package com.limou.agent_demo.decision.planner;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.limou.agent_demo.decision.model.ExecutionPlan;
import com.limou.agent_demo.decision.model.PlanStep;
import com.limou.agent_demo.decision.prompt.DecisionPrompts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 任务规划器 —— 分析用户意图，将复杂任务分解为有序的执行步骤。
 * <p>
 * 使用独立的 LLM 调用（不带工具）进行"战略思考"，
 * 输出结构化的 {@link ExecutionPlan}，供 {@code ReActExecutor} 按步执行。
 * <p>
 * 设计要点：
 * <ul>
 *   <li>使用独立 ChatClient（无工具），避免规划时触发工具调用</li>
 *   <li>要求 LLM 输出 JSON，通过 Jackson 解析</li>
 *   <li>解析失败时降级为简单的单步计划（直接执行用户请求）</li>
 *   <li>对于简单闲聊，返回空步骤列表</li>
 * </ul>
 *
 * @author lubo
 * @since 2026-07-25
 */
@Component
public class TaskPlanner {

    private static final Logger log = LoggerFactory.getLogger(TaskPlanner.class);

    private final ChatClient plainChatClient;
    private final ToolCallbackProvider toolProvider;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TaskPlanner(ChatClient.Builder chatClientBuilder,
                       ToolCallbackProvider toolProvider) {
        // 构建一个不带任何工具的 ChatClient，专门用于规划
        this.plainChatClient = chatClientBuilder.build();
        this.toolProvider = toolProvider;
    }

    /**
     * 分析用户意图并生成执行计划。
     *
     * @param userMessage 用户输入
     * @return 执行计划（如果只是闲聊，步骤列表为空）
     */
    public ExecutionPlan plan(String userMessage) {
        log.info("[TaskPlanner] 开始分析用户意图: {}", truncate(userMessage, 100));

        try {
            String plannerPrompt = DecisionPrompts.buildPlannerPrompt(toolProvider);

            String response = plainChatClient.prompt()
                    .system(plannerPrompt)
                    .user("用户请求：" + userMessage)
                    .call()
                    .content();

            log.debug("[TaskPlanner] LLM 规划响应: {}", truncate(response, 300));
            return parsePlan(response, userMessage);

        } catch (Exception e) {
            log.warn("[TaskPlanner] 规划失败，降级为单步计划: {}", e.getMessage());
            return buildFallbackPlan(userMessage);
        }
    }

    /**
     * 根据执行反馈重新规划（调整未完成的步骤）。
     *
     * @param currentPlan 当前计划
     * @param feedback    执行反馈（如某步骤失败的原因）
     * @return 调整后的新计划
     */
    public ExecutionPlan replan(ExecutionPlan currentPlan, String feedback) {
        log.info("[TaskPlanner] 重新规划，反馈: {}", truncate(feedback, 100));

        try {
            String replanPrompt = """
                    你是一个任务规划专家。当前计划执行遇到了问题，需要调整。

                    ## 原始计划
                    目标: %s
                    步骤: %s

                    ## 执行反馈
                    %s

                    ## 要求
                    根据反馈调整计划。如果某步骤失败了，尝试换一种方法。
                    输出调整后的 JSON 计划（格式同前）。
                    """.formatted(
                    currentPlan.getGoal(),
                    summarizeSteps(currentPlan.getSteps()),
                    feedback
            );

            String response = plainChatClient.prompt()
                    .user(replanPrompt)
                    .call()
                    .content();

            ExecutionPlan newPlan = parsePlan(response, currentPlan.getGoal());
            // 保留原来的 maxRounds 设置
            newPlan.setMaxRounds(currentPlan.getMaxRounds());
            return newPlan;

        } catch (Exception e) {
            log.warn("[TaskPlanner] 重新规划失败: {}", e.getMessage());
            // 重新规划失败时，降低最大轮次继续尝试
            currentPlan.setMaxRounds(currentPlan.getMaxRounds() - 1);
            return currentPlan;
        }
    }

    // ==================== 私有方法 ====================

    /**
     * 解析 LLM 返回的 JSON 计划。
     */
    @SuppressWarnings("unchecked")
    private ExecutionPlan parsePlan(String response, String userMessage) {
        try {
            // 提取 JSON 块（LLM 可能用 ```json ... ``` 包裹）
            String json = extractJson(response);
            Map<String, Object> map = objectMapper.readValue(json, Map.class);

            String goal = (String) map.getOrDefault("goal", userMessage);
            String reasoning = (String) map.getOrDefault("reasoning", "");

            List<PlanStep> steps = new ArrayList<>();
            List<Map<String, Object>> stepList = (List<Map<String, Object>>) map.get("steps");
            if (stepList != null) {
                for (Map<String, Object> stepMap : stepList) {
                    PlanStep step = PlanStep.builder()
                            .order(toInt(stepMap.get("order"), steps.size()))
                            .description((String) stepMap.getOrDefault("description", ""))
                            .expectedTool((String) stepMap.getOrDefault("expectedTool", null))
                            .expectedOutcome((String) stepMap.getOrDefault("expectedOutcome", ""))
                            .build();
                    steps.add(step);
                }
            }

            return ExecutionPlan.builder()
                    .goal(goal)
                    .reasoning(reasoning)
                    .steps(steps)
                    .build();

        } catch (JsonProcessingException | ClassCastException e) {
            log.warn("[TaskPlanner] JSON 解析失败: {}", e.getMessage());
            return buildFallbackPlan(userMessage);
        }
    }

    /**
     * 构建降级计划 —— 返回空步骤，由 DecisionEngine 快速路径直接处理。
     * <p>
     * 空步骤意味着无需分步执行（闲聊/简单问答），Engine 会跳过 Plan→Reflect 循环，
     * 直接调用 ReActExecutor 一轮返回，避免"反思→重试"的死循环。
     */
    private ExecutionPlan buildFallbackPlan(String userMessage) {
        return ExecutionPlan.builder()
                .goal(userMessage)
                .reasoning("简单对话，无需分步，直接回复")
                .steps(List.of())
                .build();
    }

    /**
     * 从 LLM 响应中提取 JSON 内容。
     */
    private String extractJson(String response) {
        if (response == null || response.isBlank()) return "{}";

        String trimmed = response.trim();

        // 处理 ```json ... ``` 包裹
        int jsonStart = trimmed.indexOf("```json");
        if (jsonStart >= 0) {
            int contentStart = trimmed.indexOf('\n', jsonStart);
            int end = trimmed.indexOf("```", contentStart > 0 ? contentStart : jsonStart + 7);
            if (contentStart > 0 && end > contentStart) {
                return trimmed.substring(contentStart, end).trim();
            }
        }

        // 处理 ``` ... ``` 包裹
        int codeStart = trimmed.indexOf("```");
        if (codeStart >= 0) {
            int contentStart = trimmed.indexOf('\n', codeStart);
            int end = trimmed.indexOf("```", contentStart > 0 ? contentStart : codeStart + 3);
            if (contentStart > 0 && end > contentStart) {
                return trimmed.substring(contentStart, end).trim();
            }
        }

        // 尝试找到 { 和 } 的配对
        int braceStart = trimmed.indexOf('{');
        int braceEnd = trimmed.lastIndexOf('}');
        if (braceStart >= 0 && braceEnd > braceStart) {
            return trimmed.substring(braceStart, braceEnd + 1);
        }

        return trimmed;
    }

    private int toInt(Object value, int defaultValue) {
        if (value instanceof Number n) return n.intValue();
        if (value instanceof String s) {
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private String summarizeSteps(List<PlanStep> steps) {
        if (steps == null || steps.isEmpty()) return "（无步骤）";
        StringBuilder sb = new StringBuilder();
        for (PlanStep s : steps) {
            sb.append(String.format("  %d. %s [状态:%s]\n",
                    s.getOrder(), s.getDescription(), s.getStatus()));
        }
        return sb.toString();
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }
}
