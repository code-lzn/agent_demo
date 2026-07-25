package com.limou.agent_demo.service;

import com.limou.agent_demo.decision.DecisionEngine;
import com.limou.agent_demo.dto.ChatEvent;
import com.limou.agent_demo.dto.ChatRequest;
import com.limou.agent_demo.entity.Conversation;
import com.limou.agent_demo.entity.Message;
import com.limou.agent_demo.mapper.ConversationMapper;
import com.limou.agent_demo.mapper.MessageMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
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

    private final DecisionEngine decisionEngine;
    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;
    private final LocalRagService localRagService;

    public AgentService(DecisionEngine decisionEngine,
                        ConversationMapper conversationMapper,
                        MessageMapper messageMapper,
                        LocalRagService localRagService) {
        this.decisionEngine = decisionEngine;
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
        this.localRagService = localRagService;
    }

    /**
     * 流式聊天 —— RAG 增强后交由决策引擎处理。
     */
    public Flux<ChatEvent> streamChat(ChatRequest request) {
        log.info("[AgentService] 收到请求: conversationId={}, message={}",
                request.getConversationId(), truncate(request.getMessage(), 60));

        // 1. 会话管理
        String conversationId = getOrCreateConversationId(request);
        String userMsgId = UUID.randomUUID().toString();
        persistUserMessage(request.getMessage(), userMsgId, conversationId);

        // 2. RAG 知识库检索（仅复杂问题时启用，闲聊跳过）
        String enrichedMessage = isSimpleChat(request.getMessage())
                ? request.getMessage()
                : buildRagPrompt(request.getMessage());

        // 3. 委托决策引擎（Plan → ReAct → Reflect）
        StringBuilder fullResponse = new StringBuilder();

        return decisionEngine.decide(enrichedMessage, conversationId)
                .doOnNext(event -> {
                    if ("message".equals(event.getType()) && event.getData() != null) {
                        fullResponse.append(event.getData().toString());
                    }
                })
                .doOnComplete(() -> persistAssistantMessage(fullResponse.toString(), conversationId))
                .doOnError(error -> log.error("[AgentService] 决策引擎出错", error));
    }

    // ==================== RAG ====================

    /**
     * 快速判断消息是否不需要 RAG 增强（闲聊/问候/确认等）。
     */
    private boolean isSimpleChat(String message) {
        if (message == null || message.isBlank()) return true;
        if (message.trim().length() <= 12) return true;

        String[] actionKeywords = {
                "打开", "启动", "运行", "关闭", "杀掉",
                "写", "写入", "创建", "新建", "生成",
                "查", "搜索", "找", "读取", "读", "列出",
                "复制", "移动", "删除", "重命名", "下载",
                "截图", "截屏", "录屏", "录音",
                "发送", "邮件", "通知",
                "执行", "编译", "安装",
                "注册表", "进程", "窗口", "鼠标", "键盘",
                "文件", "文件夹", "目录",
        };

        String lower = message.toLowerCase();
        for (String kw : actionKeywords) {
            if (lower.contains(kw)) return false;
        }
        return true;
    }

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

    private void persistAssistantMessage(String content, String conversationId) {
        Message msg = new Message();
        msg.setId(UUID.randomUUID().toString());
        msg.setConversationId(conversationId);
        msg.setRole("assistant");
        msg.setContent(content);
        messageMapper.insert(msg);
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "New Chat";
        return s.length() <= maxLen ? s : s.substring(0, maxLen);
    }
}
