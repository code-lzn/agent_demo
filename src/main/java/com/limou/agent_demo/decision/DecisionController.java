package com.limou.agent_demo.decision;

import com.limou.agent_demo.dto.ChatEvent;
import com.limou.agent_demo.dto.ChatRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

/**
 * 决策层 REST 端点 —— 提供基于 ReAct 决策引擎的智能对话接口。
 * <p>
 * 与 {@code /api/chat/stream} 的区别：
 * <ul>
 *   <li>使用完整的 Plan → Execute → Reflect 决策流程</li>
 *   <li>自动进行任务分解和步骤规划</li>
 *   <li>支持多轮推理直到任务完成</li>
 *   <li>失败时自动调整策略重试</li>
 * </ul>
 * <p>
 * 流式事件类型（SSE）：
 * <ul>
 *   <li>{@code thinking} — Agent 正在思考/规划</li>
 *   <li>{@code message}  — 文本消息（含计划、执行过程、反思结果、最终回答）</li>
 *   <li>{@code error}    — 错误信息</li>
 *   <li>{@code done}     — 流结束标记</li>
 * </ul>
 *
 * @author lubo
 * @since 2026-07-25
 */
@RestController
@RequestMapping("/decision")
@Tag(name = "Decision", description = "基于 ReAct 决策引擎的智能 Agent 对话")
public class DecisionController {

    private final DecisionService decisionService;

    public DecisionController(DecisionService decisionService) {
        this.decisionService = decisionService;
    }

    /**
     * 流式决策对话（SSE）。
     * <p>
     * 这是决策层的主入口。前端通过 EventSource 连接此端点，
     * 即可实时看到 Agent 的 Plan → Think → Act → Observe → Reflect 全过程。
     *
     * <pre>
     * 请求示例:
     *   POST /api/decision/stream
     *   Content-Type: application/json
     *
     *   {
     *     "message": "帮我在项目中找到所有 TODO 注释，汇总成一个文件",
     *     "conversationId": "abc-123"  // 可选，不传则创建新会话
     *   }
     *
     * 响应（SSE 流）:
     *   event:thinking  data:"Agent is thinking..."
     *   event:message   data:"## 执行计划\n..."
     *   event:message   data:"正在搜索 TODO..."
     *   event:message   data:"📋 反思: 任务完成"
     *   event:done      data:{"conversationId":"...","messageId":"..."}
     * </pre>
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(
            summary = "ReAct 决策流式对话",
            description = """
                    使用 Plan → ReAct Execute → Reflect 完整决策流程处理用户请求。
                    返回 SSE 事件流: thinking / message / error / done。
                    支持多轮推理、自动工具调用、失败重试、任务分解。"""
    )
    public Flux<ChatEvent> streamDecision(@RequestBody ChatRequest request) {
        return decisionService.streamDecision(request);
    }

    /**
     * 非流式决策对话（简化版，收集完整结果后一次性返回）。
     */
    @PostMapping
    @Operation(
            summary = "ReAct 决策对话（非流式）",
            description = "完整执行决策流程后一次性返回结果"
    )
    public Flux<ChatEvent> decide(@RequestBody ChatRequest request) {
        return decisionService.streamDecision(request);
    }
}
