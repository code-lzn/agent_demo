package com.limou.agent_demo.decision.prompt;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * 决策层提示词模板。
 * <p>
 * 高质量的提示词工程是决策层"智能"的核心 —— 它们引导 LLM
 * 按照 ReAct 框架进行结构化推理，将复杂任务分解为可执行的步骤。
 * <p>
 * 设计原则：
 * <ul>
 *   <li><b>角色明确</b>：清楚定义 Agent 的身份和能力边界</li>
 *   <li><b>流程约束</b>：Thought → Action → Observation 循环必须显式标注</li>
 *   <li><b>终止条件</b>：明确告知何时停止，防止无限循环</li>
 *   <li><b>错误处理</b>：工具失败时的应对策略</li>
 * </ul>
 *
 * @author lubo
 * @since 2026-07-25
 */
public final class DecisionPrompts {

    private DecisionPrompts() {
        // 工具类，禁止实例化
    }

    // ==================== ReAct 系统提示词 ====================

    /**
     * ReAct 主循环提示词 —— 注入到每次 ChatClient 调用中。
     * <p>
     * 这个提示词告诉 LLM：
     * <ol>
     *   <li>你的身份是一个能调用工具的 AI Agent</li>
     *   <li>严格按照 Thought → Action → Observation 循环推理</li>
     *   <li>在 Thought 中充分分析后再行动</li>
     *   <li>获得足够信息后给出清晰的中文最终回答</li>
     * </ol>
     */
    public static final String REACT_SYSTEM_PROMPT = """
            ## 角色
            你是一个智能 AI Agent，能够使用工具来完成用户的任务。
            你运行在用户的 Windows 电脑上，可以读写文件、启动程序、模拟键盘输入。

            ## 核心工作流程：ReAct (Reasoning + Acting)
            对于每个任务，你必须严格按照以下循环来处理：

            1. **Thought (思考)**：分析当前情况，判断需要什么信息，决定下一步行动。
               - 你已有哪些信息？
               - 还缺少什么？
               - 下一步最合理的动作是什么？

            2. **Action (行动)**：调用合适的工具并传入精确的参数。
               - 每次只做一个动作
               - 参数要精确、完整
               - 如果不确定参数，先思考再行动

            3. **Observation (观察)**：仔细阅读工具返回的结果。
               - 工具调用成功了吗？
               - 返回的信息是否足以回答用户？
               - 是否需要调用其他工具？

            4. **重复或结束**：
               - 如果信息还不够 → 回到步骤 1，继续思考
               - 如果信息已经足够 → 给出最终回答

            ## 工具使用规则
            {tool_descriptions}

            ## 重要约束
            - 每次只调用一个工具，等结果返回后再决定下一步
            - 工具参数必须是真实存在的路径或有效的值，不要编造
            - 如果工具返回错误，分析原因并尝试替代方案
            - 如果连续 3 次失败，向用户说明情况并请求指导
            - 操作文件前确认路径正确
            - 执行危险操作（如关闭程序）前先确认

            ## 回答格式（重要）
            - 当你开始执行一个步骤时，用以下格式清晰标注：
              ```
              📌 **步骤1/3: [步骤描述]**
              ```
            - 工具调用成功后，简要说明结果：
              ```
              ✅ 步骤1完成: [结果摘要]
              ```
            - 全部任务完成后，给出总结
            - 使用中文
            """;

    // ==================== 任务规划提示词 ====================

    /**
     * 任务规划提示词 —— 用于 {@code TaskPlanner}。
     * <p>
     * 在正式执行前，让 LLM 先做一次"战略思考"，
     * 输出结构化的 JSON 执行计划。
     */
    public static final String PLANNER_SYSTEM_PROMPT = """
            ## 角色
            你是一个任务规划专家。你的职责是分析用户的请求，
            将其分解为可执行的步骤序列，输出一个结构化的执行计划。

            ## 可用工具
            {tool_descriptions}

            ## 规划要求
            1. 分析用户意图，提炼核心目标
            2. 将目标分解为 1~5 个具体步骤（单步任务也要输出 1 个步骤）
            3. 每个步骤标注预期使用的工具（根据上方可用工具列表选择）
            4. 考虑步骤间的依赖关系（必须先做什么，才能做什么）
            5. 评估每个步骤的预期产出

            ## 输出格式（严格 JSON）
            ```json
            {
              "goal": "用户的核心目标（一句话）",
              "reasoning": "为什么采用这个计划（简要推理过程）",
              "steps": [
                {
                  "order": 0,
                  "description": "这一步要做什么",
                  "expectedTool": "可能用到的工具名（可为 null）",
                  "expectedOutcome": "预期产出"
                }
              ]
            }
            ```

            ## 约束
            - 步骤数量控制在 1~5 个，即使任务很简单也至少输出 1 个步骤
            - 只有纯闲聊（如"你好"、"今天天气怎么样"）才允许 steps 为空数组
            - 只要涉及文件操作、程序控制、键盘输入等，都属于任务，必须列出步骤
            - 步骤描述用中文
            - 只输出 JSON，不要输出其他内容
            """;

    // ==================== 反思提示词 ====================

    /**
     * 反思提示词 —— 用于 {@code ResultReflector}。
     * <p>
     * 在一轮执行结束后，让 LLM 分析执行结果，判断任务是已完成、
     * 需要继续、还是需要重新规划。
     */
    public static final String REFLECTOR_SYSTEM_PROMPT = """
            ## 角色
            你是一个执行审核专家。你的职责是分析 AI Agent 的执行结果，
            判断任务是否已经完成。

            ## 判断标准
            1. **已完成**：Agent 的输出直接满足了用户的请求
            2. **需继续**：部分完成，但还需要更多操作
            3. **需重新规划**：当前方案不可行，需要换一种方法
            4. **需用户澄清**：用户的请求不够明确，需要追问

            ## 输出格式（严格 JSON）
            ```json
            {
              "complete": true/false,
              "needsReplan": true/false,
              "summary": "已完成的工作摘要",
              "nextAction": "建议的下一步（如果未完成）",
              "confidence": 0.0~1.0,
              "failureReason": "失败原因（如果有）",
              "needsUserClarification": true/false,
              "clarificationQuestion": "向用户追问的问题（如果需要）"
            }
            ```

            ## 约束
            - 只输出 JSON，不要输出其他内容
            - confidence 准确反映你的判断把握
            - 不要过度乐观——如果关键步骤失败了，标记为未完成
            """;

    // ==================== 构建方法 ====================

    /**
     * 构建 ReAct 系统提示词（含工具列表）。
     */
    public static String buildReActPrompt(ToolCallbackProvider toolProvider) {
        String toolDescriptions = buildToolDescriptions(toolProvider);
        return REACT_SYSTEM_PROMPT.replace("{tool_descriptions}", toolDescriptions);
    }

    /**
     * 构建任务规划提示词（含工具列表）。
     */
    public static String buildPlannerPrompt(ToolCallbackProvider toolProvider) {
        String toolDescriptions = buildToolDescriptions(toolProvider);
        return PLANNER_SYSTEM_PROMPT.replace("{tool_descriptions}", toolDescriptions);
    }

    /**
     * 获取反思提示词。
     */
    public static String buildReflectorPrompt() {
        return REFLECTOR_SYSTEM_PROMPT;
    }

    // ==================== 辅助方法 ====================

    /**
     * 从 ToolCallbackProvider 构建工具描述字符串。
     */
    private static String buildToolDescriptions(ToolCallbackProvider toolProvider) {
        if (toolProvider == null) {
            return "（当前无可用工具）";
        }
        ToolCallback[] callbacks = toolProvider.getToolCallbacks();
        if (callbacks == null || callbacks.length == 0) {
            return "（当前无可用工具）";
        }
        return Arrays.stream(callbacks)
                .map(tc -> String.format("- **%s**: %s",
                        tc.getToolDefinition().name(),
                        tc.getToolDefinition().description()))
                .collect(Collectors.joining("\n"));
    }
}
