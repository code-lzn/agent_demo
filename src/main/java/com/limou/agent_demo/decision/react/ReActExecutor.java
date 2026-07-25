package com.limou.agent_demo.decision.react;

import com.limou.agent_demo.decision.model.ReActCycle;
import com.limou.agent_demo.decision.prompt.DecisionPrompts;
import com.limou.agent_demo.dto.ChatEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * ReAct 执行器 —— 单轮 ChatClient 调用的包装，注入 ReAct 推理提示词。
 * <p>
 * 每一轮执行 = 一次完整的 ChatClient 调用，Spring AI 在内部处理
 * Thought → Action(tool call) → Observation(tool result) 循环，
 * 直到 LLM 给出最终文本回答。
 * <p>
 * ReActExecutor 本身不控制多轮循环 —— 多轮管理由 {@code DecisionEngine} 负责。
 *
 * <pre>
 * ┌──────────────────────────────────────────┐
 * │  ReActExecutor (单轮)                      │
 * │  ┌────────────────────────────────────┐   │
 * │  │ ChatClient.prompt()                │   │
 * │  │  .system(ReAct系统提示词)            │   │
 * │  │  .user(用户消息)                     │   │
 * │  │  .stream().content()               │   │
 * │  │  ┌──────────────────────────────┐  │   │
 * │  │  │ Spring AI 内部循环:           │  │   │
 * │  │  │  LLM decides → tool call     │  │   │
 * │  │  │  → tool executes             │  │   │
 * │  │  │  → result → LLM decides...   │  │   │
 * │  │  │  → final text response       │  │   │
 * │  │  └──────────────────────────────┘  │   │
 * │  └────────────────────────────────────┘   │
 * └──────────────────────────────────────────┘
 * </pre>
 *
 * @author lubo
 * @since 2026-07-25
 */
@Component
public class ReActExecutor {

    private static final Logger log = LoggerFactory.getLogger(ReActExecutor.class);

    private static final String CHAT_MEMORY_CONVERSATION_ID = "chat_memory_conversation_id";

    private final ChatClient chatClient;
    private final ToolCallbackProvider toolProvider;

    public ReActExecutor(ChatClient chatClient, ToolCallbackProvider toolProvider) {
        this.chatClient = chatClient;
        this.toolProvider = toolProvider;
    }

    /**
     * 执行一轮 ReAct 推理，返回流式事件。
     * <p>
     * 此方法注入 ReAct 系统提示词，然后委托 ChatClient 进行处理。
     * ChatClient 利用 Spring AI 的内置工具调用能力
     * 自动完成 "行动→观察→再思考" 的内循环。
     *
     * @param userMessage     用户消息（仅第一轮使用原始消息，后续轮可为继续指令）
     * @param conversationId  会话 ID（用于 ChatMemory 上下文管理）
     * @param roundNumber     当前轮次（用于日志和事件）
     * @param isFirstRound    是否为第一轮（决定是否注入完整系统提示词）
     * @return 流式聊天事件
     */
    public Flux<ChatEvent> executeRound(String userMessage,
                                         String conversationId,
                                         int roundNumber,
                                         boolean isFirstRound) {
        log.info("[ReActExecutor] 第 {} 轮执行开始{}",
                roundNumber, isFirstRound ? "（首轮，注入系统提示词）" : "");

        String reactPrompt = DecisionPrompts.buildReActPrompt(toolProvider);

        return Flux.create(sink -> {
            try {
                sink.next(ChatEvent.thinking());

                StringBuilder fullResponse = new StringBuilder();

                // ChatMemory 不存储 system prompt，因此每轮都需要注入。
                // 首轮使用完整的 ReAct 提示词，后续轮使用简短的续接提示词，
                // 确保 LLM 始终知道自己是 Agent 且能使用工具。
                String systemPrompt = isFirstRound
                        ? reactPrompt
                        : buildContinuationReminder(roundNumber);

                var prompt = chatClient.prompt()
                        .system(systemPrompt)
                        .user(userMessage)
                        .advisors(a -> a.param(CHAT_MEMORY_CONVERSATION_ID, conversationId));

                prompt.stream()
                        .content()
                        .doOnNext(chunk -> {
                            fullResponse.append(chunk);
                            sink.next(ChatEvent.message(chunk));
                        })
                        .doOnComplete(() -> {
                            log.debug("[ReActExecutor] 第 {} 轮完成，响应长度: {}",
                                    roundNumber, fullResponse.length());
                            sink.complete();
                        })
                        .doOnError(error -> {
                            log.error("[ReActExecutor] 第 {} 轮出错: {}", roundNumber, error.getMessage());
                            sink.next(ChatEvent.error("执行错误: " + error.getMessage()));
                            sink.complete();
                        })
                        .subscribe();

            } catch (Exception e) {
                log.error("[ReActExecutor] 第 {} 轮异常: {}", roundNumber, e.getMessage(), e);
                sink.next(ChatEvent.error("系统错误: " + e.getMessage()));
                sink.complete();
            }
        });
    }

    /**
     * 同步执行一轮（用于需要等待结果的场景）。
     * <p>
     * 收集流中的所有文本内容，返回完整的响应字符串和 ReAct 周期记录。
     *
     * @param userMessage     用户消息
     * @param conversationId  会话 ID
     * @param roundNumber     轮次
     * @param timeoutSeconds  超时秒数
     * @return 本轮执行结果
     */
    public RoundResult executeRoundSync(String userMessage,
                                         String conversationId,
                                         int roundNumber,
                                         int timeoutSeconds) {
        log.info("[ReActExecutor] 同步执行第 {} 轮", roundNumber);

        try {
            String reactPrompt = DecisionPrompts.buildReActPrompt(toolProvider);
            StringBuilder fullResponse = new StringBuilder();

            chatClient.prompt()
                    .system(reactPrompt)
                    .user(userMessage)
                    .advisors(a -> a.param(CHAT_MEMORY_CONVERSATION_ID, conversationId))
                    .stream()
                    .content()
                    .doOnNext(fullResponse::append)
                    .blockLast(Duration.ofSeconds(timeoutSeconds));

            String response = fullResponse.toString();
            log.info("[ReActExecutor] 第 {} 轮同步完成，响应长度: {}", roundNumber, response.length());

            return RoundResult.builder()
                    .response(response)
                    .roundNumber(roundNumber)
                    .success(true)
                    .cycles(extractReActCycles(response, roundNumber))
                    .build();

        } catch (Exception e) {
            log.error("[ReActExecutor] 第 {} 轮同步执行失败: {}", roundNumber, e.getMessage());
            return RoundResult.builder()
                    .response("执行失败: " + e.getMessage())
                    .roundNumber(roundNumber)
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    // ==================== 提示词构建 ====================

    /**
     * 构建后续轮次的简短续接提示词。
     * <p>
     * ChatMemory 虽然保留了对话历史，但不包含 system prompt。
     * 因此每轮都需要注入一个简短的提醒，确保 LLM 保持 Agent 角色认知。
     */
    private String buildContinuationReminder(int roundNumber) {
        return """
                ## 续接执行（第 %d 轮）

                你是一个 AI Agent，正在继续完成用户的原始任务。
                你可以使用工具来读写文件、启动程序、模拟键盘输入。

                **请继续执行**：根据上面的对话历史，继续完成未完成的工作。
                如果任务已经完成，直接给出总结即可。

                ## 可用工具
                %s
                """.formatted(roundNumber, buildToolList());
    }

    private String buildToolList() {
        var callbacks = toolProvider.getToolCallbacks();
        if (callbacks == null || callbacks.length == 0) {
            return "（无可用工具）";
        }
        StringBuilder sb = new StringBuilder();
        for (var tc : callbacks) {
            sb.append("- **").append(tc.getToolDefinition().name())
                    .append("**: ").append(tc.getToolDefinition().description())
                    .append("\n");
        }
        return sb.toString();
    }

    // ==================== 辅助方法 ====================

    /**
     * 从 LLM 响应文本中提取 ReAct 周期信息（尽力解析）。
     * <p>
     * 这是一个启发式方法，根据 ReAct 提示词要求的格式尝试解析。
     * 如果 LLM 没有严格按格式输出，返回空列表。
     */
    private List<ReActCycle> extractReActCycles(String response, int roundNumber) {
        List<ReActCycle> cycles = new ArrayList<>();
        if (response == null || response.isBlank()) return cycles;

        // 简单的启发式检测：如果响应中包含明显的工具调用痕迹
        // 这里只创建一条汇总周期记录
        ReActCycle cycle = ReActCycle.builder()
                .cycleNumber(roundNumber)
                .thought("执行第 " + roundNumber + " 轮推理")
                .action(containsToolPattern(response) ? "调用工具" : "final_answer")
                .observation(response.length() > 500 ? response.substring(0, 500) + "..." : response)
                .isFinal(!containsToolPattern(response))
                .build();
        cycles.add(cycle);

        return cycles;
    }

    private boolean containsToolPattern(String text) {
        // 检查是否包含工具调用的典型模式
        return text.contains("Successfully") ||
               text.contains("Failed to") ||
               text.contains("Error reading") ||
               text.contains("Access denied") ||
               text.contains("Launched") ||
               text.contains("Pressed") ||
               text.contains("Typed");
    }

    // ==================== 内部类 ====================

    @lombok.Builder
    @lombok.Data
    public static class RoundResult {
        private String response;
        private int roundNumber;
        private boolean success;
        private String errorMessage;
        private List<ReActCycle> cycles;
    }
}
