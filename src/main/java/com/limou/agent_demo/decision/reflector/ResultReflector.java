package com.limou.agent_demo.decision.reflector;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.limou.agent_demo.decision.model.ReflectionResult;
import com.limou.agent_demo.decision.prompt.DecisionPrompts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 结果反思器 —— 在一轮 ReAct 执行结束后，分析输出是否完成了用户目标。
 * <p>
 * 反思是决策层的关键能力：它让 Agent 具备"自我审视"的能力，
 * 判断是否需要继续执行、调整策略、或向用户追问。
 * <p>
 * 实现采用双模式：
 * <ul>
 *   <li><b>LLM 反思</b>（主要）：调用 LLM 做语义级别分析，准确但增加延迟</li>
 *   <li><b>规则反思</b>（降级）：基于启发式规则快速判断，不额外消耗 Token</li>
 * </ul>
 *
 * @author lubo
 * @since 2026-07-25
 */
@Component
public class ResultReflector {

    private static final Logger log = LoggerFactory.getLogger(ResultReflector.class);

    private final ChatClient plainChatClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 标记响应"未完成"的关键词 */
    private static final String[] INCOMPLETE_INDICATORS = {
            "我需要更多信息",
            "我不确定",
            "让我再",
            "接下来",
            "然后",
            "首先",
            "第一步",
    };

    /** 标记响应"失败"的关键词 */
    private static final String[] FAILURE_INDICATORS = {
            "失败",
            "错误",
            "无法",
            "不能",
            "访问被拒绝",
            "Access denied",
            "Failed to",
            "Error",
    };

    /** 最小响应长度（太短可能意味着没有真正执行） */
    private static final int MIN_MEANINGFUL_RESPONSE_LENGTH = 20;

    public ResultReflector(ChatClient.Builder chatClientBuilder) {
        // 无工具的纯文本 ChatClient，专门用于反思分析
        this.plainChatClient = chatClientBuilder.build();
    }

    /**
     * 反思执行结果 —— 使用 LLM 进行语义分析。
     *
     * @param userGoal  用户原始目标
     * @param response  Agent 的最终响应
     * @return 反思结果
     */
    public ReflectionResult reflect(String userGoal, String response) {
        log.info("[ResultReflector] 开始反思，目标: {}", truncate(userGoal, 80));

        try {
            return llmReflect(userGoal, response);
        } catch (Exception e) {
            log.warn("[ResultReflector] LLM 反思失败，降级为规则反思: {}", e.getMessage());
            return ruleBasedReflect(userGoal, response);
        }
    }

    /**
     * 快速反思 —— 仅使用规则，不做 LLM 调用。
     * <p>
     * 适用于对延迟敏感的场景或作为 LLM 反思的降级方案。
     */
    public ReflectionResult reflectFast(String userGoal, String response) {
        return ruleBasedReflect(userGoal, response);
    }

    // ==================== LLM 反思 ====================

    private ReflectionResult llmReflect(String userGoal, String response) {
        String reflectorPrompt = DecisionPrompts.buildReflectorPrompt();

        String context = String.format("""
                用户目标: %s

                Agent 响应:
                %s
                """, userGoal, response.length() > 2000 ? response.substring(0, 2000) + "..." : response);

        String llmResponse = plainChatClient.prompt()
                .system(reflectorPrompt)
                .user(context)
                .call()
                .content();

        return parseReflectionResult(llmResponse);
    }

    @SuppressWarnings("unchecked")
    private ReflectionResult parseReflectionResult(String llmResponse) {
        try {
            String json = extractJson(llmResponse);
            Map<String, Object> map = objectMapper.readValue(json, Map.class);

            return ReflectionResult.builder()
                    .complete(toBool(map.get("complete"), false))
                    .needsReplan(toBool(map.get("needsReplan"), false))
                    .summary((String) map.getOrDefault("summary", ""))
                    .nextAction((String) map.getOrDefault("nextAction", null))
                    .confidence(toDouble(map.get("confidence"), 0.5))
                    .failureReason((String) map.getOrDefault("failureReason", null))
                    .needsUserClarification(toBool(map.get("needsUserClarification"), false))
                    .clarificationQuestion((String) map.getOrDefault("clarificationQuestion", null))
                    .build();

        } catch (JsonProcessingException | ClassCastException e) {
            log.warn("[ResultReflector] JSON 解析失败，降级为规则反思: {}", e.getMessage());
            return ruleBasedReflect("", llmResponse);
        }
    }

    // ==================== 规则反思 ====================

    /**
     * 基于启发式规则的反思 —— 快速、无需 LLM 调用。
     * <p>
     * 检查维度：
     * <ol>
     *   <li>响应是否为空/过短</li>
     *   <li>是否包含"未完成"标记词</li>
     *   <li>是否包含"失败"标记词</li>
     *   <li>是否包含工具执行成功的迹象</li>
     * </ol>
     */
    private ReflectionResult ruleBasedReflect(String userGoal, String response) {
        if (response == null || response.isBlank()) {
            return ReflectionResult.builder()
                    .complete(false)
                    .needsReplan(false)
                    .summary("Agent 未产生任何输出")
                    .nextAction("重试执行")
                    .confidence(0.0)
                    .build();
        }

        // 1. 检查失败指标
        boolean hasFailure = false;
        String failureReason = null;
        for (String indicator : FAILURE_INDICATORS) {
            if (response.contains(indicator)) {
                hasFailure = true;
                failureReason = "响应中包含失败标记: " + indicator;
                break;
            }
        }

        // 2. 检查未完成指标
        boolean looksIncomplete = false;
        for (String indicator : INCOMPLETE_INDICATORS) {
            if (response.contains(indicator)) {
                looksIncomplete = true;
                break;
            }
        }

        // 3. 检查响应长度
        boolean tooShort = response.length() < MIN_MEANINGFUL_RESPONSE_LENGTH;

        // 4. 判定
        if (hasFailure && tooShort) {
            return ReflectionResult.builder()
                    .complete(false)
                    .needsReplan(true)
                    .summary("执行似乎失败了")
                    .failureReason(failureReason)
                    .nextAction("尝试替代方案")
                    .confidence(0.7)
                    .build();
        }

        if (hasFailure) {
            // 有失败但也有内容 —— 可能部分成功
            return ReflectionResult.builder()
                    .complete(false)
                    .needsReplan(false)
                    .summary("部分执行遇到问题")
                    .failureReason(failureReason)
                    .nextAction("检查失败原因后继续")
                    .confidence(0.4)
                    .build();
        }

        if (looksIncomplete || tooShort) {
            return ReflectionResult.builder()
                    .complete(false)
                    .needsReplan(false)
                    .summary("执行可能未完成")
                    .nextAction("继续执行剩余步骤")
                    .confidence(0.3)
                    .build();
        }

        // 默认：看起来完成了
        return ReflectionResult.builder()
                .complete(true)
                .summary("任务似乎已完成")
                .confidence(0.7)
                .build();
    }

    // ==================== 辅助方法 ====================

    private String extractJson(String response) {
        if (response == null || response.isBlank()) return "{}";
        String trimmed = response.trim();

        int jsonStart = trimmed.indexOf("```json");
        if (jsonStart >= 0) {
            int contentStart = trimmed.indexOf('\n', jsonStart);
            int end = trimmed.indexOf("```", contentStart > 0 ? contentStart : jsonStart + 7);
            if (contentStart > 0 && end > contentStart) {
                return trimmed.substring(contentStart, end).trim();
            }
        }

        int braceStart = trimmed.indexOf('{');
        int braceEnd = trimmed.lastIndexOf('}');
        if (braceStart >= 0 && braceEnd > braceStart) {
            return trimmed.substring(braceStart, braceEnd + 1);
        }

        return trimmed;
    }

    private boolean toBool(Object value, boolean defaultValue) {
        if (value instanceof Boolean b) return b;
        if (value instanceof String s) return Boolean.parseBoolean(s);
        return defaultValue;
    }

    private double toDouble(Object value, double defaultValue) {
        if (value instanceof Number n) return n.doubleValue();
        if (value instanceof String s) {
            try {
                return Double.parseDouble(s);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }
}
