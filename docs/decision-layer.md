# 决策层设计文档

## 一、架构定位

决策层是 AI Agent 四层架构中的**大脑**，负责接收感知层传来的消息、调用 LLM 进行 ReAct 推理、产出行动计划并驱动执行。

```
┌─────────────────────────────────────────┐
│             感知层 (Perception)          │
│     AgentService: RAG检索 + 消息增强      │
└──────────────────┬──────────────────────┘
                   ↓
┌─────────────────────────────────────────┐
│             决策层 (Decision)             │
│     LLM + ReAct规划器 → 行动计划          │
│     Plan → Execute → Reflect 循环        │
└──────────────────┬──────────────────────┘
                   ↓
┌─────────────────────────────────────────┐
│             执行层 (Execution)            │
│     Spring AI ChatClient + 工具调用       │
└──────────────────┬──────────────────────┘
                   ↓
┌─────────────────────────────────────────┐
│             数据层 (Data)                 │
│     ChatMemory(上下文) + MySQL(持久化)    │
└─────────────────────────────────────────┘
```

## 二、模块清单

```
com.limou.agent_demo.decision/
├── DecisionEngine.java             主协调器 (Plan→Execute→Reflect)
├── DecisionService.java            服务层（SSE流式接口）
├── DecisionController.java         REST端点 /api/decision/stream
├── AgentSecurityGuard.java         五层安全防护
├── AgentSession.java               会话状态追踪
├── AgentState.java                 状态机枚举
├── SecurityVerdict.java            安全验证结果
├── TerminationDecision.java        终止判断结果
├── TerminationEvaluator.java       终止条件判断
├── Decision.java                   决策数据模型
├── ToolCallRequest.java            工具调用请求
├── DecisionLayer.java              旧版决策引擎（兼容保留）
├── model/
│   ├── PlanStep.java               单步骤模型 + 状态流转
│   ├── ExecutionPlan.java          执行计划 + 进度跟踪
│   ├── ReActCycle.java             单次 Thought→Action→Observation
│   └── ReflectionResult.java       反思判定结果
├── planner/
│   └── TaskPlanner.java            LLM意图分析 → JSON执行计划
├── react/
│   └── ReActExecutor.java          ReAct提示词注入 + 流式执行
├── reflector/
│   └── ResultReflector.java        双模式反思（LLM语义 + 规则降级）
└── prompt/
    └── DecisionPrompts.java        三套核心提示词模板
```

| 文件 | 职责 |
|------|------|
| `DecisionEngine.java` | Plan→Execute→Reflect 主循环，最多5轮，单轮超时180s |
| `DecisionService.java` | SSE流式服务，不依赖数据库 |
| `DecisionController.java` | REST端点，返回SSE事件流 |
| `AgentSecurityGuard.java` | 五层安全防护（注入检测/参数校验/敏感确认/频率控制/输出过滤） |
| `AgentSession.java` | 会话状态、轮次、频率控制（同工具≤3次，总计≤10次） |
| `PlanStep.java` | 步骤模型：描述/工具/状态(PENDING→COMPLETED/FAILED) |
| `ExecutionPlan.java` | 计划模型：目标/推理/步骤列表/进度摘要 |
| `ReActCycle.java` | 单次推理周期记录 |
| `ReflectionResult.java` | 反思结果：完成/重规划/追问/置信度 |
| `TaskPlanner.java` | LLM分析意图，输出JSON计划，失败降级为单步 |
| `ReActExecutor.java` | 注入ReAct提示词，驱动ChatClient流式执行 |
| `ResultReflector.java` | LLM语义反思 + 启发式规则反思双模式 |
| `DecisionPrompts.java` | ReAct/Planner/Reflector三套提示词模板 |

## 三、核心流程

### 3.1 完整请求生命周期

```
POST /api/chat/stream
        │
        ▼
┌─ AgentService.streamChat() ───────────────────────────┐
│  1. 会话管理 → MySQL查/建conversation                    │
│  2. RAG检索 → LocalRagService.search()                 │
│  3. 委托决策 → decisionEngine.decide()                  │
│  4. 持久化   → MySQL保存assistant回复 + toolCalls       │
└──────────────────────┬────────────────────────────────┘
                       │
                       ▼
┌─ DecisionEngine.decide() ─────────────────────────────┐
│                                                        │
│  安全①: Prompt注入检测                                  │
│                                                        │
│  ┌─ Phase 1: PLAN ──────────────────────────────┐    │
│  │  TaskPlanner.plan(userMessage)                 │    │
│  │    → ChatClient(无工具) 调用LLM                 │    │
│  │    → LLM输出JSON: {goal, reasoning, steps[]}   │    │
│  │    → 解析为ExecutionPlan                       │    │
│  │    → SSE: "📋 执行计划: 目标+步骤"             │    │
│  └───────────────────────────────────────────────┘    │
│                       │                                │
│  ┌─ Phase 2: EXECUTE ───────────────────────────┐    │
│  │  for round in 1..maxRounds:                    │    │
│  │    ReActExecutor.executeRound()                │    │
│  │      → 注入ReAct系统提示词(首轮)                 │    │
│  │      → 嵌入执行计划到用户消息                    │    │
│  │      → ChatClient(含工具).stream().content()   │    │
│  │      → Spring AI内部处理工具调用循环             │    │
│  │      → SSE: 实时流式推送文本                    │    │
│  └───────────────────────────────────────────────┘    │
│                       │                                │
│  ┌─ Phase 3: REFLECT ──────────────────────────┐    │
│  │  ResultReflector.reflect(goal, response)       │    │
│  │    → LLM模式: ChatClient分析→JSON              │    │
│  │    → 规则模式: 启发式关键词检测(降级方案)       │    │
│  │    → 输出ReflectionResult                      │    │
│  │                                                │    │
│  │  安全⑤: 过滤反思结果中的敏感信息                │    │
│  │                                                │    │
│  │  判定逻辑:                                      │    │
│  │    complete=true        → 结束循环              │    │
│  │    needsReplan=true     → 回到Phase 1重新规划   │    │
│  │    needsClarification   → 追问用户后结束        │    │
│  │    其他                  → 回到Phase 2下一轮     │    │
│  └───────────────────────────────────────────────┘    │
│                                                        │
│  最大5轮，单轮超时180秒                                  │
└────────────────────────────────────────────────────────┘
```

### 3.2 SSE 事件流

```
event: message  → "## 📋 执行计划\n**目标**: ..."    (计划)
event: message  → "正在搜索文件..."                    (执行过程)
event: message  → "📋 反思: 任务完成"                 (反思结果)
event: error    → "安全拦截: ..."                     (安全阻断)
event: done     → {"conversationId":"...","messageId":"..."}
```

## 四、安全防护

| 层 | 防护 | 集成位置 | 触发条件 |
|----|------|----------|---------|
| ① | Prompt注入检测 | `DecisionEngine.decide()` 入口 | 用户输入含"忽略系统提示"/"jailbreak"等关键词 |
| ② | 工具参数校验 | ToolSafety（底层守卫） | 路径不在白名单、命令在黑名单 |
| ③ | 敏感操作确认 | confirm参数传入DecisionEngine | writeFile/deleteFile/closeApp等需confirm=true |
| ④ | 调用频率控制 | AgentSession | 同工具>3次 或 总计>10次 |
| ⑤ | 输出内容过滤 | executeLoop()反思+最终输出 | 手机号/API Key/密码正则匹配 |

## 五、Plan→Execute→Reflect 循环

### Phase 1: Planning (TaskPlanner)

使用独立的 `ChatClient`（无工具绑定），让 LLM 做"战略思考"：

```
输入: "帮我读取test.txt并计算行数"
  ↓
ChatClient(无工具) → 输出JSON计划
  ↓
Jackson解析 → ExecutionPlan
```

失败降级：LLM返回非JSON时，自动构建单步Fallback计划。

### Phase 2: Execute (ReActExecutor)

注入 ReAct 系统提示词，引导 LLM 按 Thought→Action→Observation 循环推理。
Spring AI 的 ChatClient 在内部自动处理工具调用循环。

### Phase 3: Reflect (ResultReflector)

双模式反思：
- **LLM 反思**（主要）：调 LLM 做语义分析，输出 JSON 判定
- **规则反思**（降级）：关键词检测，不额外消耗 Token

## 六、数据模型

```java
Decision { ANSWER, TOOL_CALL, NONE }
ToolCallRequest(String id, String toolName, String arguments)
AgentSession(String conversationId)  // 轮次/频率控制
PlanStep(int order, String description, String expectedTool, StepStatus status)
ExecutionPlan(String goal, String reasoning, List<PlanStep> steps, int maxRounds)
ReflectionResult(Boolean complete, Boolean needsReplan, String summary, ...)
```

## 七、已知问题

| 问题 | 说明 |
|------|------|
| ReAct 速度慢 | 每轮 Plan+Execute+Reflect 串行调用 LLM 3次，总计 2N+1 次 API 调用 |
| 空计划幻觉 | 用户输入模糊时，Plan 返回空步骤但 Execute 仍带工具，LLM 可能编造操作 |
| 工具级安全待增强 | ②③ 的上层策略需借助 Spring AI advisor 机制拦截 ChatClient 自动工具调用 |
