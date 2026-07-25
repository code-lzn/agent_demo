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
│             决策层 (Decision) ← 你的模块   │
│     LLM + ReAct规划器 → 行动计划          │
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
├── DecisionEngine.java             主协调器
├── DecisionService.java            服务层（无DB依赖）
├── DecisionController.java         REST端点 /api/decision/stream
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

| 文件 | 行数 | 职责 |
|------|------|------|
| `DecisionEngine.java` | 313 | Plan→Execute→Reflect 主循环，最多5轮 |
| `DecisionService.java` | 63 | SSE流式服务，不依赖数据库 |
| `DecisionController.java` | 91 | REST端点，返回SSE事件流 |
| `PlanStep.java` | 91 | 步骤模型：描述/工具/状态(PENDING→COMPLETED/FAILED) |
| `ExecutionPlan.java` | 125 | 计划模型：目标/推理/步骤列表/进度摘要 |
| `ReActCycle.java` | 78 | 单次推理周期记录 |
| `ReflectionResult.java` | 72 | 反思结果：完成/重规划/追问/置信度 |
| `TaskPlanner.java` | 249 | LLM分析意图，输出JSON计划，失败降级为单步 |
| `ReActExecutor.java` | 265 | 注入ReAct提示词，驱动ChatClient流式执行 |
| `ResultReflector.java` | 274 | LLM语义反思 + 启发式规则反思双模式 |
| `DecisionPrompts.java` | 226 | ReAct/Planner/Reflector三套提示词模板 |

## 三、核心流程

### 3.1 完整请求生命周期

```
POST /api/chat/stream  (或 /api/decision/stream)
        │
        ▼
┌─ AgentService.streamChat() ───────────────────────────┐
│  1. RAG检索 → LocalRagService.search()                 │
│  2. 会话管理 → MySQL查/建conversation                    │
│  3. 委托决策 → decisionEngine.decide(enrichedMsg)       │
│  4. 持久化   → MySQL保存assistant回复                    │
└──────────────────────┬────────────────────────────────┘
                       │
                       ▼
┌─ DecisionEngine.decide() ─────────────────────────────┐
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
│  │      → ChatClient(含工具).stream().content()    │    │
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
│  │  判定逻辑:                                      │    │
│  │    complete=true        → 结束循环              │    │
│  │    needsReplan=true     → 回到Phase 1重新规划   │    │
│  │    needsClarification   → 追问用户后结束        │    │
│  │    其他                  → 回到Phase 2下一轮     │    │
│  └───────────────────────────────────────────────┘    │
│                                                        │
│  最大5轮，单轮超时180秒                                   │
└────────────────────────────────────────────────────────┘
```

### 3.2 Plan→Execute→Reflect 循环示意

```
用户: "帮我读取test.txt，计算文件内容的行数，把结果写入report.txt"

Phase 1 - PLAN:
  TaskPlanner → LLM输出:
  {
    "goal": "读取test.txt并计算行数后写入报告",
    "steps": [
      {"order":0, "description":"读取test.txt", "expectedTool":"readFile"},
      {"order":1, "description":"计算行数", "expectedTool":"countLines"},
      {"order":2, "description":"写入结果", "expectedTool":"writeFile"}
    ]
  }
  SSE → 📋 展示计划

Phase 2 - EXECUTE (Round 1):
  ReActExecutor → ChatClient:
    Thought: 需要先读取文件
    Action: readFile("test.txt")
    Observation: "文件内容: hello world\nfoo bar"
    
    Thought: 需要统计行数
    Action: countLines("test.txt")
    Observation: "2 行"
    
    Thought: 将结果写入报告
    Action: writeFile("report.txt", "test.txt 共 2 行")
    Observation: "写入成功"
    
    FinalAnswer: "📌步骤1完成...✅步骤2完成...✅步骤3完成..."
  SSE → 实时流式推送

Phase 3 - REFLECT:
  ResultReflector → LLM分析:
  {"complete":true, "confidence":0.95, "summary":"三个步骤全部完成"}
  SSE → 📋 ✅ 任务完成 (置信度: 95%)
```

## 四、核心组件详解

### 4.1 DecisionEngine — 主协调器

```java
@Component
public class DecisionEngine {
    // 依赖: TaskPlanner + ReActExecutor + ResultReflector

    public Flux<ChatEvent> decide(String userMessage, String conversationId) {
        // Phase 1: Plan
        ExecutionPlan plan = planner.plan(userMessage);
        sink.next(formatPlanMessage(plan));

        // Phase 2-3: Execute + Reflect Loop
        while (!done && round < plan.getMaxRounds()) {
            round++;
            // 执行一轮（流式输出 + 收集完整响应）
            reActExecutor.executeRound(prompt, conversationId, round, isFirstRound);
            
            // 等待本轮完成
            latch.await(ROUND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            
            // 反思
            ReflectionResult reflection = reflector.reflect(userMessage, response);
            
            if (reflection.shouldStop()) break;
            if (reflection.needsReplan()) plan = planner.replan(plan, failReason);
            // else: continue loop
        }
        sink.complete();
    }
}
```

安全控制:
- **最大轮次**: 默认5轮，防止无限循环
- **单轮超时**: 180秒，超时取消本轮订阅
- **错误隔离**: 单轮失败触发replan，不中断整体流程

### 4.2 TaskPlanner — 任务规划器

使用独立的 `ChatClient`（无工具绑定），让 LLM 做"战略思考"：

```
输入: "帮我读取test.txt并计算行数"
  ↓
ChatClient(无工具).prompt()
  .system(PLANNER_SYSTEM_PROMPT)  ← 要求输出JSON格式计划
  .user("用户请求：" + message)
  .call()
  ↓
输出: {"goal":"...", "reasoning":"...", "steps":[{...}]}
  ↓
Jackson解析 → ExecutionPlan
```

失败降级：LLM返回非JSON时，自动构建单步Fallback计划。

### 4.3 ReActExecutor — ReAct执行器

注入 ReAct 系统提示词，引导 LLM 按 Thought→Action→Observation 循环推理：

```java
public Flux<ChatEvent> executeRound(userMessage, conversationId, roundNumber, isFirstRound) {
    String systemPrompt = isFirstRound
        ? REACT_SYSTEM_PROMPT       // 完整ReAct提示词 + 工具列表
        : buildContinuationReminder // 简短续接提醒

    return chatClient.prompt()
        .system(systemPrompt)
        .user(userMessage)          // 首轮含计划步骤
        .advisors(chatMemory)       // 上下文记忆
        .stream().content();        // Spring AI自动处理工具调用
}
```

关键设计：
- **每轮都注入system prompt**：ChatMemory不存储system prompt，需每轮重注
- **首轮嵌入执行计划**：`buildFirstRoundPrompt()` 将计划步骤拼入user消息
- **步骤标注格式**：提示词要求LLM用 `📌步骤1/3` `✅步骤1完成` 标注进度

### 4.4 ResultReflector — 结果反思器

双模式反思：

| 模式 | 实现 | 优点 | 缺点 |
|------|------|------|------|
| LLM反思 | ChatClient分析→JSON判定 | 语义准确 | 增加延迟+Token消耗 |
| 规则反思 | 启发式关键词检测 | 零延迟 | 精度较低 |

判定维度：
- `complete` → 任务是否完成
- `needsReplan` → 是否需要重新规划
- `needsUserClarification` → 是否需要追问用户
- `confidence` → 置信度 0.0~1.0

规则模式关键词：
- **未完成标记**: "我需要更多信息"、"首先"、"第一步"...
- **失败标记**: "失败"、"错误"、"Access denied"、"Failed to"...

## 五、数据模型

### 5.1 ExecutionPlan

```java
ExecutionPlan {
    String id;                    // UUID
    String goal;                  // 用户核心目标
    String reasoning;             // 推理过程
    List<PlanStep> steps;         // 有序步骤列表
    PlanStatus status;            // CREATED→IN_PROGRESS→COMPLETED/FAILED
    int maxRounds;                // 最大执行轮次(默认5)
}
```

### 5.2 PlanStep

```java
PlanStep {
    int order;                    // 步骤序号
    String description;           // 步骤描述
    String expectedTool;          // 预期工具名
    String expectedOutcome;       // 预期产出
    StepStatus status;            // PENDING→IN_PROGRESS→COMPLETED/FAILED/SKIPPED
    String observation;           // 实际执行结果
}
```

### 5.3 ReActCycle

```java
ReActCycle {
    int cycleNumber;              // 周期序号
    String thought;               // LLM推理文本
    String action;                // 工具名 或 "final_answer"
    String actionInput;           // 工具参数
    String observation;           // 工具返回结果
    boolean isFinal;              // 是否为终止周期
}
```

### 5.4 ReflectionResult

```java
ReflectionResult {
    boolean complete;             // 任务是否完成
    Boolean needsReplan;          // 是否需要重新规划
    Boolean needsUserClarification; // 是否需要追问用户
    String summary;               // 执行摘要
    String nextAction;            // 建议下一步
    double confidence;            // 置信度 0.0~1.0
    String failureReason;         // 失败原因
    String clarificationQuestion; // 追问问题
}
```

## 六、提示词工程

三套核心提示词：

### 6.1 REACT_SYSTEM_PROMPT

```
角色: AI Agent，运行在Windows电脑上
流程: Thought → Action → Observation → 重复/结束
格式: 📌步骤1/3: [描述] / ✅步骤完成: [结果]
工具列表: (动态注入ToolCallbackProvider中的所有工具)
约束: 每次一个工具、精确参数、错误重试、中文回答
```

### 6.2 PLANNER_SYSTEM_PROMPT

```
角色: 任务规划专家
要求: 分析意图 → 分解为1~5步 → 标注工具 → 输出JSON
输出: {"goal":"...", "reasoning":"...", "steps":[{order,description,expectedTool,expectedOutcome}]}
约束: 只有纯闲聊才允许空steps，涉及文件/程序/输入必须列步骤
```

### 6.3 REFLECTOR_SYSTEM_PROMPT

```
角色: 执行审核专家
判断: 已完成/需继续/需重新规划/需用户澄清
输出: {"complete":bool, "needsReplan":bool, "confidence":0.0~1.0, ...}
```

## 七、与其它层的交互

### 7.1 从感知层接收

```
AgentService.streamChat(request)
  → RAG检索 → 消息增强
  → decisionEngine.decide(enrichedMessage, conversationId)
```

决策层不直接处理原始用户消息，而是接收感知层增强后的消息（含RAG上下文）。

### 7.2 驱动执行层

决策层不直接调用工具。它通过 `ChatClient`（含 `ToolCallbackProvider`）将工具列表注入 LLM，由 Spring AI 框架自动处理工具调用：

```
ReActExecutor → ChatClient.prompt()
  → LLM决定调用 readFile("test.txt")
  → Spring AI拦截tool_call → 执行FileTool.readFile()
  → 结果回传LLM → LLM继续推理
```

### 7.3 SSE事件流

```
event: thinking → "Agent is thinking..."
event: message  → "📋 执行计划: ..."      (计划展示)
event: message  → "📌步骤1/3: 读取文件"    (实时执行)
event: message  → "✅步骤1完成: 2行"      (步骤完成)
event: message  → "📋 反思: ✅ 任务完成"   (反思结果)
event: done     → {"conversationId":"..."} (流结束)
event: error    → {"message":"..."}        (异常)
```

## 八、调用入口

两个端点，都走决策引擎：

| 端点 | 特点 |
|------|------|
| `POST /api/chat/stream` | 经AgentService → RAG增强 + MySQL持久化 + 决策引擎 |
| `POST /api/decision/stream` | 经DecisionService → 纯决策引擎，无DB依赖 |

## 九、设计原则

1. **不修改已有代码**: 决策层作为独立package，复用已有DTO/Entity
2. **不依赖数据库**: ChatMemory管理上下文，后续可切换向量库
3. **Plan→Execute→Reflect**: 结构化决策循环，非一次性LLM调用
4. **双模式反思**: LLM语义分析为主，规则降级兜底
5. **安全边界**: 最大轮次、单轮超时、错误隔离三重保护
