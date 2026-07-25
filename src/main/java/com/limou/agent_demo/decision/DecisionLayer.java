package com.limou.agent_demo.decision;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 决策层 —— Agent 的大脑
 *
 * 职责只有一件：把当前消息列表发给 LLM，解析它的决策。
 *
 * 决策层不执行任何操作，不访问数据库，不调任何业务服务。
 * 它只负责"思考"，执行交给 Orchestrator / Execution Layer。
 *
 * LLM 返回决策的方式：
 *   1. 结构化 tool_calls（原生 function calling，由工具定义在 ChatClient 层配置）
 *   2. 文本 TOOL_CALL: 格式（通过系统提示词描述工具）
 *   3. 直接回答文本
 *
 * 注意：工具定义通过 ChatClient（在 AiConfig 中配置）自动传给 LLM，
 * 而不是通过 ChatModel 的 Prompt options 传递（OpenAI adapter 不自持
 * ToolCallback 直接传递，会抛异常）。
 */
@Component
public class DecisionLayer {

    private static final Logger log = LoggerFactory.getLogger(DecisionLayer.class);

    /** 工具调用响应的正则：TOOL_CALL: {"tool": "...", "arguments": {...}} */
    private static final Pattern TOOL_CALL_PATTERN =
            Pattern.compile("TOOL_CALL:\\s*\\{\"tool\":\\s*\"([^\"]+)\",\\s*\"arguments\":\\s*(\\{.+\\})\\s*\\}",
                    Pattern.DOTALL);

    private final ChatModel chatModel;

    public DecisionLayer(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    /**
     * 做一次决策
     *
     * @param messages 当前完整的消息列表（可变，会追加 LLM 的原始响应消息）
     * @return 决策结果：回答用户 / 调用工具 / 无决策
     */
    public Decision decide(List<org.springframework.ai.chat.messages.Message> messages) {
        long start = System.currentTimeMillis();

        // 调 ChatModel，不传 tool options（工具定义已在系统提示词中描述）
        ChatResponse response;
        try {
            Prompt prompt = new Prompt(messages);
            response = chatModel.call(prompt);
        } catch (Exception e) {
            log.error("LLM 调用失败", e);
            return Decision.none();
        }

        long elapsed = System.currentTimeMillis() - start;
        log.debug("决策耗时: {}ms", elapsed);

        // 解析响应 → 得到决策
        Decision decision = parseResponse(response);

        // 将 LLM 的原始 AssistantMessage 追加到消息列表
        // 保留 reasoning_content、tool_calls 等原始字段（DeepSeek 要求必须原样回传）
        if (response != null && response.getResult() != null && response.getResult().getOutput() != null) {
            AssistantMessage original = response.getResult().getOutput();

            if (decision.isToolCall() && !original.hasToolCalls()) {
                // 文本 TOOL_CALL 格式（如 "TOOL_CALL: {\"tool\":...}"）
                // 原始 message 有文本但无结构化 toolCalls，需要补充 toolCalls
                // 以便后面紧跟的 ToolResponseMessage 能被 API 接受
                List<AssistantMessage.ToolCall> tcs = decision.getToolCalls().stream()
                        .map(tcr -> new AssistantMessage.ToolCall(
                                tcr.getId(), "function", tcr.getToolName(), tcr.getArguments()))
                        .collect(Collectors.toList());
                // 保留原始消息的 text 和 metadata（DeepSeek 的 reasoning_content 可能在 metadata 中）
                var builder = AssistantMessage.builder()
                        .toolCalls(tcs);
                // 拷贝原始消息的 metadata/properties（含 reasoning_content 等信息）
                java.util.Map<String, Object> metadata = original.getMetadata();
                if (metadata != null && !metadata.isEmpty()) {
                    builder.properties(metadata);
                }
                messages.add(builder.build());
            } else {
                // 结构化 tool_calls 或直接回答：原样追加（保留 reasoning_content 等）
                messages.add(original);
            }
        }

        return decision;
    }

    /**
     * 解析 LLM 的响应，判断它想做什么
     *
     * LLM 的返回有三种可能性（按检查优先级）：
     *   A. 结构化 tool_calls（通过 ChatClient 层原生 function calling）
     *   B. 文本中 TOOL_CALL: 格式（系统提示词描述工具）
     *   C. 直接回答文本
     *   D. 空内容 → 异常情况
     */
    private Decision parseResponse(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            log.warn("LLM 返回为空");
            return Decision.none();
        }

        AssistantMessage output = response.getResult().getOutput();
        String content = output.getText();
        List<AssistantMessage.ToolCall> toolCalls = output.getToolCalls();

        // 情况 A：结构化 tool_calls（通过原生 function calling）
        if (toolCalls != null && !toolCalls.isEmpty()) {
            log.debug("决策结果：检测到结构化 tool_calls ({}个)", toolCalls.size());
            List<ToolCallRequest> requests = toolCalls.stream()
                    .map(tc -> new ToolCallRequest(tc.id(), tc.name(), tc.arguments()))
                    .collect(Collectors.toList());
            return Decision.toolCall(requests);
        }

        // 内容为空 → NONE
        if (content == null || content.isBlank()) {
            log.warn("LLM 返回内容为空");
            return Decision.none();
        }

        content = content.trim();

        // 情况 B：文本 TOOL_CALL: 格式
        ToolCallRequest toolCall = tryParseToolCall(content);
        if (toolCall != null) {
            log.debug("决策结果：文本格式调用工具 {}", toolCall.getToolName());
            return Decision.toolCall(List.of(toolCall));
        }

        // 情况 C：LLM 直接回答
        log.debug("决策结果：LLM 直接回答 (长度={})", content.length());
        return Decision.answer(content);
    }

    /**
     * 尝试从 LLM 响应文本中解析工具调用
     *
     * 支持三种格式：
     *   1. TOOL_CALL: {"tool": "xxx", "arguments": {...}}
     *   2. ```tool_call\n{"tool": "xxx", "arguments": {...}}\n```
     *   3. DeepSeek DSML 格式：<｜DSML｜tool_calls><｜DSML｜invoke name="xxx">...
     */
    private ToolCallRequest tryParseToolCall(String content) {
        // 格式 1：TOOL_CALL: JSON
        Matcher matcher = TOOL_CALL_PATTERN.matcher(content);
        if (matcher.find()) {
            String toolName = matcher.group(1);
            String arguments = matcher.group(2);
            String id = "call_" + System.nanoTime();
            return new ToolCallRequest(id, toolName, arguments);
        }

        // 格式 2：```tool_call JSON ```
        Pattern codeBlockPattern = Pattern.compile(
                "```tool_call\\s*\\n?\\{(.+?)\\}\\s*```", Pattern.DOTALL);
        Matcher codeMatcher = codeBlockPattern.matcher(content);
        if (codeMatcher.find()) {
            try {
                String jsonContent = "{" + codeMatcher.group(1) + "}";
                Pattern namePattern = Pattern.compile("\"tool\"\\s*:\\s*\"([^\"]+)\"");
                Matcher nameMatcher = namePattern.matcher(jsonContent);
                if (nameMatcher.find()) {
                    String toolName = nameMatcher.group(1);
                    Pattern argsPattern = Pattern.compile("\"arguments\"\\s*:\\s*(\\{.+?\\})");
                    Matcher argsMatcher = argsPattern.matcher(jsonContent);
                    if (argsMatcher.find()) {
                        String id = "call_" + System.nanoTime();
                        return new ToolCallRequest(id, toolName, argsMatcher.group(1));
                    }
                }
            } catch (Exception e) {
                log.warn("解析 tool_call 代码块失败", e);
            }
        }

        // 格式 3：DeepSeek DSML 格式（分隔符为 Unicode 全角竖线 U+FF5C）
        // 格式：<invoke name="toolName">...
        //       <parameter name="x" string="true">v</...>
        String dsml = "｜｜DSML｜｜";
        Pattern dsmlPattern = Pattern.compile(
                "<" + dsml + "invoke name=\"([^\"]+)\"(.*?)</" + dsml + "invoke>",
                Pattern.DOTALL);
        Matcher dsmlMatcher = dsmlPattern.matcher(content);
        if (dsmlMatcher.find()) {
            try {
                String toolName = dsmlMatcher.group(1);
                String paramsBlock = dsmlMatcher.group(2);

                Pattern paramPattern = Pattern.compile(
                        "<" + dsml + "parameter name=\"([^\"]+)\"\\s+string=\"true\">(.*?)</" + dsml + "parameter>",
                        Pattern.DOTALL);
                Matcher paramMatcher = paramPattern.matcher(paramsBlock);

                StringBuilder argsJson = new StringBuilder("{");
                boolean first = true;
                while (paramMatcher.find()) {
                    if (!first) argsJson.append(", ");
                    argsJson.append("\"").append(paramMatcher.group(1)).append("\": ")
                            .append("\"").append(paramMatcher.group(2).replace("\\", "\\\\").replace("\"", "\\\"")).append("\"");
                    first = false;
                }
                argsJson.append("}");

                String id = "call_" + System.nanoTime();
                log.debug("解析到 DSML 工具调用: {} args={}", toolName, argsJson);
                return new ToolCallRequest(id, toolName, argsJson.toString());
            } catch (Exception e) {
                log.warn("解析 DSML 格式失败", e);
            }
        }

        return null;
    }
}
