package com.limou.agent_demo.decision;

import com.limou.agent_demo.dto.ChatEvent;
import com.limou.agent_demo.dto.ChatRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.UUID;

/**
 * 决策层服务 —— 提供与 {@code AgentService} 相同的 SSE 流式接口，
 * 但内部使用完整的决策引擎（Plan → ReAct Execute → Reflect）。
 * <p>
 * 本模块不依赖数据库，会话上下文由 Spring AI 的 {@code ChatMemory}（内存）
 * 管理，后续可切换为向量库方案而不影响业务逻辑。
 *
 * <pre>
 * 对比：
 *   AgentService:  用户消息 → ChatClient（简单 tool calling） → 响应  + MySQL 持久化
 *   DecisionService: 用户消息 → Planner → ReActExecutor(多轮) → Reflector → 响应  （无 DB）
 * </pre>
 */
@Service
public class DecisionService {

    private static final Logger log = LoggerFactory.getLogger(DecisionService.class);

    private final DecisionEngine decisionEngine;
    private final AgentSecurityGuard securityGuard;

    public DecisionService(DecisionEngine decisionEngine, AgentSecurityGuard securityGuard) {
        this.decisionEngine = decisionEngine;
        this.securityGuard = securityGuard;
    }

    /**
     * 使用决策引擎处理用户消息，返回流式 SSE 事件。
     *
     * @param request 聊天请求（conversationId 为空时自动生成新 ID）
     * @return 流式 ChatEvent（thinking / message / error / done）
     */
    public Flux<ChatEvent> streamDecision(ChatRequest request) {
        log.info("[DecisionService] 收到决策请求: conversationId={}, message={}",
                request.getConversationId(), truncate(request.getMessage(), 60));

        // 会话 ID：用已有 ID 或生成新的（ChatMemory 基于此 ID 管理上下文）
        String conversationId = (request.getConversationId() != null
                && !request.getConversationId().isEmpty())
                ? request.getConversationId()
                : UUID.randomUUID().toString();

        // 将 ChatRequest.confirm 传递给决策引擎（用于安全③敏感操作确认）
        return decisionEngine.decide(request.getMessage(), conversationId, request.isConfirm())
                .doOnError(error -> log.error("[DecisionService] 决策引擎出错", error));
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen);
    }
}
