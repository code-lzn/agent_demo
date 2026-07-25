package com.limou.agent_demo.service;

import com.limou.agent_demo.decision.*;
import com.limou.agent_demo.dto.ChatEvent;
import com.limou.agent_demo.dto.ChatRequest;
import com.limou.agent_demo.entity.Conversation;
import com.limou.agent_demo.entity.Message;
import com.limou.agent_demo.mapper.ConversationMapper;
import com.limou.agent_demo.mapper.MessageMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Agent 编排器 —— 四层架构的总控
 *
 * 流程：
 *   感知层(构建输入) → 循环 { 决策层(思考) → 执行层(工具) → 数据层(持久化) }
 *
 * 职责：
 * - 接收 ChatRequest，构建消息列表（感知层）
 * - 循环调用 DecisionLayer 做决策（决策层）
 * - 执行工具调用（执行层）
 * - 持久化消息（数据层）
 * - 通过 SSE 流式输出所有事件
 */
@Service
public class AgentOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(AgentOrchestrator.class);

    private static final String CHAT_MEMORY_CONVERSATION_ID = "chat_memory_conversation_id";

    private final DecisionLayer decisionLayer;
    private final TerminationEvaluator terminationEvaluator;
    private final AgentSecurityGuard securityGuard;
    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;
    private final ToolCallbackProvider toolCallbackProvider;

    public AgentOrchestrator(DecisionLayer decisionLayer,
                             TerminationEvaluator terminationEvaluator,
                             AgentSecurityGuard securityGuard,
                             ConversationMapper conversationMapper,
                             MessageMapper messageMapper,
                             ToolCallbackProvider toolCallbackProvider) {
        this.decisionLayer = decisionLayer;
        this.terminationEvaluator = terminationEvaluator;
        this.securityGuard = securityGuard;
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
        this.toolCallbackProvider = toolCallbackProvider;
    }

    /**
     * 执行业务流程：感知 → 循环{决策→执行} → 数据
     *
     * @param request 用户请求
     * @return SSE 事件流
     */
    public Flux<ChatEvent> execute(ChatRequest request) {
        return Flux.create(sink -> {
            try {
                // ====================================================================
                // 阶段 0：数据层 —— 获取/创建会话
                // ====================================================================
                String conversationId = getOrCreateConversationId(request);
                AgentSession session = new AgentSession(conversationId);

                // ====================================================================
                // 阶段 1：感知层 —— 构建消息列表 & 工具定义
                // ====================================================================
                session.setState(AgentState.PERCEIVING);

                // 构建工具定义描述文本（嵌入系统提示词）
                String toolDescriptions = buildToolDescriptions();

                // 构建系统提示词
                String systemPrompt = buildSystemPrompt(toolDescriptions);
                SystemMessage systemMsg = new SystemMessage(systemPrompt);

                // 加载历史消息（最多最近 20 条）
                List<Message> historyMessages = messageMapper.selectRecentByConversationId(conversationId, 20);
                // 反转历史消息为时间正序
                Collections.reverse(historyMessages);

                // 当前用户消息
                String userText = request.getMessage() != null ? request.getMessage() : "";

                // ====================================================================
                // 安全 ①：Prompt 注入检测
                // ====================================================================
                SecurityVerdict injectionCheck = securityGuard.checkPromptInjection(userText);
                if (injectionCheck.isBlocked()) {
                    log.warn("Prompt 注入拦截: {}", injectionCheck.getReason());
                    sink.next(ChatEvent.error("安全拦截: " + injectionCheck.getReason()));
                    sink.complete();
                    return;
                }

                // ====================================================================
                // 阶段 2：决策循环 —— 决策层 → 执行层 → 决策层 → ...
                // ====================================================================
                StringBuilder fullAnswer = new StringBuilder();
                StringBuilder toolResultsSummary = new StringBuilder();
                List<ToolCallRequest> previousCalls = new ArrayList<>();
                boolean answered = false;

                // 构建跨轮次共享的消息列表（系统提示词 + 历史消息 + 当前用户消息）
                // 后续每轮追加 assistant 回复和 tool 结果，不重新构建
                List<org.springframework.ai.chat.messages.Message> messages = new ArrayList<>();
                messages.add(systemMsg);

                for (Message msg : historyMessages) {
                    switch (msg.getRole()) {
                        case "user" -> messages.add(new UserMessage(msg.getContent()));
                        case "assistant" -> messages.add(new AssistantMessage(msg.getContent()));
                    }
                }
                messages.add(new UserMessage(userText));

                while (!answered && session.getCurrentRound() < TerminationEvaluator.MAX_ROUNDS) {
                    session.incrementRound();
                    session.setState(AgentState.DECIDING);

                    // --- 2a. 决策层：让 LLM 思考 ---
                    sink.next(ChatEvent.thinking());

                    Decision decision = decisionLayer.decide(messages);
                    log.info("第{}轮决策: {}", session.getCurrentRound(), decision);

                    // --- 2b. 终止判断 ---
                    TerminationDecision td = terminationEvaluator.shouldTerminate(session, decision, previousCalls);
                    if (td.isShouldStop()) {
                        if (decision.isAnswer()) {
                            String answer = decision.getAnswer();
                            fullAnswer.append(answer);
                            String filtered = securityGuard.filterOutput(answer);
                            sink.next(ChatEvent.message(filtered));
                            messages.add(new AssistantMessage(answer));
                            answered = true;
                            break;
                        }
                        String reason = td.getReason();
                        log.info("决策循环终止: {}", reason);
                        // 如果有工具执行结果但 LLM 没说话，使用工具结果作为回答
                        if (toolResultsSummary.length() > 0) {
                            fullAnswer.append(toolResultsSummary.toString());
                            sink.next(ChatEvent.message("已完成操作。"));
                        } else {
                            sink.next(ChatEvent.message("我已经完成了当前步骤。摘要：" + reason));
                        }
                        answered = true;
                        break;
                    }

                    // --- 2c. 处理决策 ---
                    if (decision.isToolCall()) {
                        session.setState(AgentState.EXECUTING);

                        // DecisionLayer.decide() 已追加了正确的 AssistantMessage
                        //（文本 TOOL_CALL 格式会补充 toolCalls；原生 tool_calls 保留原消息含 reasoning_content）
                        // 此处只需收集工具执行结果并追加 ToolResponseMessage

                        // 收集工具执行结果，如果最终 LLM 没有回答，就用工具结果作为回答
                        toolResultsSummary.setLength(0);

                        // 追加 ToolResponseMessage（工具执行结果）
                        List<org.springframework.ai.chat.messages.ToolResponseMessage.ToolResponse> toolResponses = new ArrayList<>();

                        for (ToolCallRequest toolCall : decision.getToolCalls()) {
                            // 先发送 tool_call 事件（让 UI 知道 LLM 想调什么工具）
                            sink.next(ChatEvent.toolCall(toolCall.getToolName(), toolCall.getArguments()));

                            String toolResult;
                            String blockReason = null;

                            // 安全 ②：工具参数校验
                            SecurityVerdict argsCheck = securityGuard.validateToolArgs(toolCall);
                            if (argsCheck.isBlocked()) {
                                blockReason = argsCheck.getReason();
                            }

                            // 安全 ③：敏感操作确认
                            if (blockReason == null) {
                                SecurityVerdict destructiveCheck = securityGuard.checkDestructiveOp(
                                        toolCall.getToolName(), request.isConfirm());
                                if (destructiveCheck.isBlocked()) {
                                    blockReason = destructiveCheck.getReason();
                                }
                            }

                            // 安全 ④：工具调用频率控制
                            if (blockReason == null) {
                                SecurityVerdict freqCheck = securityGuard.checkToolCallFrequency(
                                        session, toolCall.getToolName());
                                if (freqCheck.isBlocked()) {
                                    blockReason = freqCheck.getReason();
                                }
                            }

                            if (blockReason != null) {
                                sink.next(ChatEvent.error("安全拦截: " + blockReason));
                                log.warn("工具调用被拦截: {} - {}", toolCall.getToolName(), blockReason);
                                toolResult = "BLOCKED: " + blockReason;
                            } else {
                                // ===== 执行工具 =====
                                toolResult = executeTool(toolCall);

                                sink.next(ChatEvent.toolResult(toolResult));

                                // 记录工具调用
                                session.recordToolCall(toolCall.getToolName());
                                previousCalls.add(toolCall);

                                toolResultsSummary.append("[").append(toolCall.getToolName())
                                        .append("] ").append(toolResult).append("\n");

                                log.info("工具执行完成: {} → {}", toolCall.getToolName(),
                                        toolResult.length() > 100 ? toolResult.substring(0, 100) + "..." : toolResult);
                            }

                            // 每个 tool_call 都必须有对应的 ToolResponseMessage，否则 API 报错
                            toolResponses.add(new org.springframework.ai.chat.messages.ToolResponseMessage.ToolResponse(
                                    toolCall.getId(), toolCall.getToolName(), toolResult));
                        }

                        // 追加 ToolResponseMessage（工具执行结果）
                        for (var tr : toolResponses) {
                            messages.add(org.springframework.ai.chat.messages.ToolResponseMessage.builder()
                                    .responses(List.of(tr))
                                    .build());
                        }
                        // 下一轮继续让 LLM 决策（LLM 能看到工具执行结果）

                    } else if (decision.isAnswer()) {
                        // LLM 直接回答
                        String answer = decision.getAnswer();
                        fullAnswer.append(answer);
                        String filtered = securityGuard.filterOutput(answer);
                        sink.next(ChatEvent.message(filtered));
                        messages.add(new AssistantMessage(answer));
                        answered = true;

                    } else {
                        // NONE：异常情况
                        log.warn("第{}轮决策异常: LLM 无有效输出", session.getCurrentRound());
                        sink.next(ChatEvent.error("抱歉，处理过程中遇到异常。"));
                        session.setState(AgentState.FAILED);
                        answered = true;
                    }
                }

                // ====================================================================
                // 阶段 3：数据层 —— 持久化消息
                // ====================================================================
                String userMsgId = UUID.randomUUID().toString();
                persistMessages(userText, userMsgId, fullAnswer.toString(), conversationId);

                // 更新会话标题（如果是新会话）
                if (fullAnswer.length() > 0) {
                    Conversation conv = conversationMapper.selectById(conversationId);
                    if (conv != null && conv.getTitle() != null && conv.getTitle().equals("New Chat")) {
                        // 保留原始用户消息作为标题
                    }
                }

                session.setState(AgentState.COMPLETED);
                sink.next(ChatEvent.done(conversationId, userMsgId));
                sink.complete();

            } catch (Exception e) {
                log.error("编排器执行异常", e);
                sink.next(ChatEvent.error("系统错误: " + e.getMessage()));
                sink.complete();
            }
        });
    }

    // ========================================================================
    // 感知层 —— 构建系统提示词 & 消息列表
    // ========================================================================

    /**
     * 从 ToolCallbackProvider 获取所有工具定义，格式化为简明的文本列表
     * 含工具名、描述、必要参数
     */
    private String buildToolDescriptions() {
        ToolCallback[] callbacks = toolCallbackProvider.getToolCallbacks();
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < callbacks.length; i++) {
            ToolDefinition def = callbacks[i].getToolDefinition();
            sb.append("- ").append(def.name());
            if (def.description() != null && !def.description().isBlank()) {
                String desc = def.description().split("\\n")[0];
                sb.append(": ").append(desc);
            }
            // 从 inputSchema 提取参数名（仅提取名称以助 LLM 了解需要哪些参数）
            String schema = def.inputSchema();
            if (schema != null && !schema.isBlank()) {
                java.util.regex.Matcher pm = java.util.regex.Pattern.compile(
                        "\"([a-zA-Z]+)\"\\s*:\\s*\\{\\s*\"type\"\\s*:").matcher(schema);
                java.util.ArrayList<String> params = new java.util.ArrayList<>();
                while (pm.find()) {
                    params.add(pm.group(1));
                }
                if (!params.isEmpty()) {
                    sb.append(" 参数: ").append(String.join(", ", params));
                }
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    /**
     * 构建系统提示词
     */
    private String buildSystemPrompt(String toolDescriptions) {
        return """
                你是桌面助手，可以控制用户的 Windows 电脑。

                ## 核心规则
                1. 当用户要求执行操作时，你必须调用工具来实现，不要只口头说明。
                2. 调用工具时，只允许使用以下JSON格式，禁止使用XML或DSML格式：
                   TOOL_CALL: {"tool": "工具名", "arguments": {"参数名": "参数值"}}
                   示例：
                   用户：打开记事本
                   你：TOOL_CALL: {"tool": "openApp", "arguments": {"appPath": "notepad.exe"}}
                   用户：D盘根目录有哪些文件
                   你：TOOL_CALL: {"tool": "listDir", "arguments": {"dirPath": "D:\\"}}
                3. 当用户只是问问题（如"你是谁"），直接回答即可，不需要调用工具。
                4. 工具执行完成后，你必须根据执行结果向用户汇报。

                ## 可用工具列表
                %s

                ## 安全规则
                - 不要执行危险操作（格式化、删除系统文件等）
                - 保护用户隐私
                """.formatted(toolDescriptions);
    }

    /**
     * 构建本轮决策的消息列表
     *
     * 消息结构：系统提示词 + 历史消息 + 当前用户消息 + 之前的工具结果(如果有)
     */
    private List<org.springframework.ai.chat.messages.Message> buildDecisionMessages(
            SystemMessage systemMsg,
            List<Message> historyMessages,
            String userText,
            String previousAssistantText) {

        List<org.springframework.ai.chat.messages.Message> messages = new ArrayList<>();
        messages.add(systemMsg);

        // 添加历史消息
        for (Message msg : historyMessages) {
            switch (msg.getRole()) {
                case "user":
                    messages.add(new UserMessage(msg.getContent()));
                    break;
                case "assistant":
                    messages.add(new AssistantMessage(msg.getContent()));
                    break;
                // tool 类型消息由之前的工具执行结果提供，不在这里加载
            }
        }

        // 添加当前用户消息
        messages.add(new UserMessage(userText));

        return messages;
    }

    // ========================================================================
    // 执行层 —— 调用具体工具
    // ========================================================================

    /**
     * 根据工具调用请求执行对应的工具
     *
     * @param request 工具调用请求（工具名 + 参数 JSON）
     * @return 工具执行结果
     */
    private String executeTool(ToolCallRequest request) {
        ToolCallback[] callbacks = toolCallbackProvider.getToolCallbacks();

        String toolName = request.getToolName();
        String arguments = request.getArguments();

        // 查找匹配的工具
        for (ToolCallback callback : callbacks) {
            if (callback.getToolDefinition().name().equals(toolName)) {
                try {
                    // 预清理：LLM 可能生成未转义控制字符的 JSON，先转义换行符和制表符
                    String sanitized = arguments
                            .replace("\r\n", "\\n")
                            .replace("\n", "\\n")
                            .replace("\r", "\\n")
                            .replace("\t", "\\t");
                    // 执行工具
                    String result = callback.call(sanitized);
                    return result;
                } catch (Exception e) {
                    log.error("工具执行失败: {}", toolName, e);
                    return "工具执行失败: " + e.getMessage();
                }
            }
        }

        return "未找到工具: " + toolName;
    }

    // ========================================================================
    // 数据层 —— 持久化
    // ========================================================================

    /**
     * 获取或创建会话
     */
    private String getOrCreateConversationId(ChatRequest request) {
        if (request.getConversationId() != null && !request.getConversationId().isEmpty()) {
            Conversation existing = conversationMapper.selectById(request.getConversationId());
            if (existing != null) return request.getConversationId();
        }
        String newId = UUID.randomUUID().toString();
        Conversation conv = new Conversation();
        conv.setId(newId);
        conv.setTitle(truncate(request.getMessage(), 100));
        conv.setModel("deepseek-chat");
        conversationMapper.insert(conv);
        return newId;
    }

    /**
     * 持久化用户和助手的消息
     */
    private void persistMessages(String userText, String userMsgId,
                                  String assistantText, String conversationId) {
        Message userMsg = new Message();
        userMsg.setId(userMsgId);
        userMsg.setConversationId(conversationId);
        userMsg.setRole("user");
        userMsg.setContent(userText);
        messageMapper.insert(userMsg);

        if (assistantText != null && !assistantText.isEmpty()) {
            Message assistantMsg = new Message();
            assistantMsg.setId(UUID.randomUUID().toString());
            assistantMsg.setConversationId(conversationId);
            assistantMsg.setRole("assistant");
            assistantMsg.setContent(assistantText);
            messageMapper.insert(assistantMsg);
        }
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "New Chat";
        return s.length() <= maxLen ? s : s.substring(0, maxLen);
    }
}
