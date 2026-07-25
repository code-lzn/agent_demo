package com.limou.agent_demo.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.limou.agent_demo.decision.DecisionEngine;
import com.limou.agent_demo.dto.ChatEvent;
import com.limou.agent_demo.dto.ChatRequest;
import com.limou.agent_demo.entity.Conversation;
import com.limou.agent_demo.entity.Message;
import com.limou.agent_demo.mapper.ConversationMapper;
import com.limou.agent_demo.mapper.MessageMapper;
import com.limou.agent_demo.tool.ToolCallCapture;
import com.limou.agent_demo.tool.ToolContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Agent 服务层 —— 对外统一入口。
 * <p>
 * 职责：
 * <ul>
 *   <li>RAG 知识库检索（{@link LocalRagService}）</li>
 *   <li>会话/消息持久化（MySQL）</li>
 *   <li>委托 {@link DecisionEngine} 进行 Plan → ReAct → Reflect 智能决策</li>
 * </ul>
 * <p>
 * 决策层是系统的"大脑"——所有 Agent 请求最终都由它处理。
 * AgentService 只负责"感知"（RAG）和"记录"（持久化），不直接调用 LLM。
 *
 * @author lubo
 * @since 2026-07-25
 */
@Service
public class AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final DecisionEngine decisionEngine;
    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;
    private final LocalRagService localRagService;
    private final ToolContext toolContext;
    private final ToolCallCapture toolCallCapture;
    private final ChatMemory chatMemory;

    public AgentService(DecisionEngine decisionEngine,
                        ConversationMapper conversationMapper,
                        MessageMapper messageMapper,
                        LocalRagService localRagService,
                        ToolContext toolContext,
                        ToolCallCapture toolCallCapture,
                        ChatMemory chatMemory) {
        this.decisionEngine = decisionEngine;
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
        this.localRagService = localRagService;
        this.toolContext = toolContext;
        this.toolCallCapture = toolCallCapture;
        this.chatMemory = chatMemory;
    }

    /**
     * 流式聊天 —— RAG 增强后交由决策引擎处理。
     */
    public Flux<ChatEvent> streamChat(ChatRequest request) {
        log.info("[AgentService] 收到请求: conversationId={}, message={}",
                request.getConversationId(), truncate(request.getMessage(), 60));

        // 1. 会话管理
        String conversationId = getOrCreateConversationId(request);
        primeChatMemory(conversationId);
        String userMsgId = UUID.randomUUID().toString();
        persistUserMessage(request.getMessage(), userMsgId, conversationId);

        // 2. RAG 知识库检索 → 增强用户消息
        String enrichedMessage = buildRagPrompt(request.getMessage());

        // 3. 委托决策引擎（Plan → ReAct → Reflect）
        // Flux.defer 确保 ThreadLocal 上下文在订阅线程上设置
        StringBuilder fullResponse = new StringBuilder();
        boolean confirm = request.isConfirm();

        return Flux.defer(() -> {
            toolContext.begin(conversationId, confirm);

            return decisionEngine.decide(enrichedMessage, conversationId, confirm)
                    .doOnNext(event -> {
                        if ("message".equals(event.getType()) && event.getData() != null) {
                            fullResponse.append(event.getData().toString());
                        }
                    })
                    .doOnComplete(() -> {
                        List<Map<String, Object>> toolCalls = toolCallCapture.drain();
                        persistAssistantMessage(fullResponse.toString(), conversationId, toolCalls);
                    })
                    .doOnError(error -> log.error("[AgentService] 决策引擎出错", error))
                    .doFinally(signal -> {
                        toolContext.clear();
                        toolCallCapture.clear();
                    });
        });
    }

    // ==================== RAG ====================

    /**
     * 用本地知识库增强用户问题。
     * 检索结果作为上下文拼入消息，决策引擎和 LLM 在后续推理中自然使用。
     */
    private String buildRagPrompt(String question) {
        List<LocalRagService.RagReference> references = localRagService.search(question);
        String context = localRagService.buildContext(references);

        if (context.isBlank()) {
            return question;
        }

        return """
                【本地知识库资料】
                %s

                【用户问题】
                %s
                """.formatted(context, question);
    }

    // ==================== 持久化 ====================

    /**
     * 将数据库中的历史消息载入 ChatMemory，确保 JVM 重启后对话上下文不丢失。
     * 仅在 ChatMemory 中尚无此会话记录时执行（避免重复加载）。
     */
    private void primeChatMemory(String conversationId) {
        List<org.springframework.ai.chat.messages.Message> existing = chatMemory.get(conversationId);
        if (existing != null && !existing.isEmpty()) return;

        List<Message> dbMessages = messageMapper.selectRecentByConversationId(conversationId, 20);
        if (dbMessages.isEmpty()) return;

        List<org.springframework.ai.chat.messages.Message> aiMessages = new ArrayList<>();
        // DB 返回 DESC (最新在前)，反转为按时间顺序添加
        for (int i = dbMessages.size() - 1; i >= 0; i--) {
            Message m = dbMessages.get(i);
            if ("user".equals(m.getRole())) {
                aiMessages.add(new UserMessage(m.getContent()));
            } else if ("assistant".equals(m.getRole())) {
                aiMessages.add(new AssistantMessage(m.getContent()));
            }
        }

        if (!aiMessages.isEmpty()) {
            chatMemory.add(conversationId, aiMessages);
            log.info("[AgentService] 已加载 {} 条历史消息到 ChatMemory, conversationId={}",
                    aiMessages.size(), conversationId);
        }
    }

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

    private void persistUserMessage(String content, String msgId, String conversationId) {
        Message msg = new Message();
        msg.setId(msgId);
        msg.setConversationId(conversationId);
        msg.setRole("user");
        msg.setContent(content);
        messageMapper.insert(msg);
    }

    private void persistAssistantMessage(String content, String conversationId,
                                          List<Map<String, Object>> toolCalls) {
        Message msg = new Message();
        msg.setId(UUID.randomUUID().toString());
        msg.setConversationId(conversationId);
        msg.setRole("assistant");
        msg.setContent(content);
        if (toolCalls != null && !toolCalls.isEmpty()) {
            try {
                msg.setToolCalls(objectMapper.writeValueAsString(toolCalls));
            } catch (Exception e) {
                log.warn("[AgentService] 序列化 toolCalls 失败", e);
            }
        }
        messageMapper.insert(msg);
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "New Chat";
        return s.length() <= maxLen ? s : s.substring(0, maxLen);
    }
}
