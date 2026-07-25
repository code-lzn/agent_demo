# 决策层设计文档

## 一、概述

决策层是桌面 Agent 四层架构中的**大脑**，负责接收用户请求、调用 LLM 思考、解析决策结果。它不执行具体操作，只负责"思考"。

### 四层架构

```
用户 → [感知层] → [决策层] → [执行层] → [数据层]
                          ↕
                   安全防护 (5层)
                          ↕
                   终止判断 (5种条件)
```

| 层 | 职责 | 对应模块 |
|----|------|----------|
| **感知层** | 构建系统提示词、加载历史消息、组装消息列表 | `AgentOrchestrator` (内联) |
| **决策层** | 调用 LLM 思考，解析返回的决策类型 | `DecisionLayer` |
| **执行层** | 根据决策结果执行具体工具 | `ToolCallbackProvider` → `ProcessTool` / `FileTool` / `InputTool` 等 |
| **数据层** | 持久化会话和消息 | `ConversationMapper` / `MessageMapper` |

---

## 二、模块清单

| 包 | 文件 | 类型 | 职责 |
|----|------|------|------|
| `decision` | `Decision.java` | 值对象 | 决策结果：ANSWER / TOOL_CALL / NONE |
| `decision` | `ToolCallRequest.java` | 值对象 | 工具调用请求（id / 工具名 / 参数JSON） |
| `decision` | `AgentState.java` | 枚举 | 6 种状态：PERCEIVING→DECIDING→EXECUTING→COMPLETED/FAILED/BLOCKED |
| `decision` | `AgentSession.java` | 值对象 | 会话状态：轮次、工具调用频率控制 |
| `decision` | `TerminationDecision.java` | 值对象 | 终止判断结果：是否停止 + 原因 |
| `decision` | `TerminationEvaluator.java` | 组件 | 5 种终止条件判断 |
| `decision` | `SecurityVerdict.java` | 值对象 | 安全验证结果：拦截/通过 |
| `decision` | `AgentSecurityGuard.java` | 组件 | 五层安全防护 |
| `decision` | `DecisionLayer.java` | 组件 | **决策引擎**：LLM 调用 + 响应解析 |
| `service` | `AgentOrchestrator.java` | 组件 | **编排器**：四层总控，SSE 事件流 |

---

## 三、核心流程

### 3.1 完整请求生命周期

```
ChatRequest
    │
    ▼
┌─────────────────────────────────────────────────┐
│ AgentOrchestrator.execute()                      │
│                                                   │
│  阶段 0 (数据层)                                  │
│  ├─ 获取/创建 Conversation                        │
│  └─ 创建 AgentSession                             │
│                                                   │
│  阶段 1 (感知层)                                  │
│  ├─ buildToolDescriptions() → 43 个工具文本描述    │
│  ├─ buildSystemPrompt() → 系统提示词 + 工具列表    │
│  ├─ 加载历史消息 (最近20条)                        │
│  └─ 安全 ①: Prompt 注入检测                       │
│                                                   │
│  阶段 2 (决策循环, 最多 10 轮)                     │
│  ┌──────────────────────────────────────┐         │
│  │ ① LLM 思考 (DecisionLayer.decide()) │         │
│  │ ② 终止判断 (TerminationEvaluator)  │         │
│  │ ③ 如果 TOOL_CALL:                   │         │
│  │    ├─ 安全 ②③④: 参数/权限/频率       │         │
│  │    ├─ 执行工具 → ToolResponseMessage │         │
│  │    └─ 继续循环                      │         │
│  │ ④ 如果 ANSWER: 输出过滤 → 跳出循环  │         │
│  │ ⑤ 如果 NONE: 异常处理 → 跳出循环    │         │
│  └──────────────────────────────────────┘         │
│                                                   │
│  阶段 3 (数据层)                                  │
│  ├─ persistMessages()                             │
│  ├─ 更新会话标题                                  │
│  └─ sink.complete()                               │
└─────────────────────────────────────────────────┘
    │
    ▼
SSE Event Stream (Flux<ChatEvent>)
```

### 3.2 决策循环详述

每一轮循环中：

1. **LLM 思考** — `DecisionLayer.decide(messages)` 将当前消息列表发给 `ChatModel.call()`，返回原始响应
2. **追加原始消息** — 将 LLM 返回的 `AssistantMessage` 追加到消息列表（保留 `reasoning_content`、`toolCalls` 等字段）
3. **解析决策** — `parseResponse()` 按优先级检查：
   - 结构化 `tool_calls`（原生 function calling）
   - 文本 `TOOL_CALL:` 格式
   - DeepSeek DSML XML 格式
   - 直接回答文本
4. **终止判断** — `TerminationEvaluator` 检查 5 种条件
5. **处理决策** — 执行工具或输出回答

### 3.3 SSE 事件流

```
event: thinking      → "Agent is thinking..."
event: decision      → {"round": 1, "toolCallCount": 0}
event: tool_call     → {"tool": "openApp", "args": {...}}
event: tool_result   → {"result": "Successfully launched..."}
event: blocked       → {"reason": "文件路径不在白名单内"}
event: message       → "已完成操作。"
event: done          → {"conversationId": "xxx", "messageId": "yyy"}
event: error         → {"message": "系统错误"}
```

---

## 四、决策引擎 (DecisionLayer)

### 4.1 职责

- 接收消息列表
- 调用 `ChatModel.call(Prompt)`（不传 tool options，工具定义在系统提示词中描述）
- 将原始 `AssistantMessage` 追加到消息列表（保留 `reasoning_content`）
- 解析响应为 `Decision`

### 4.2 三种决策格式

| 格式 | 触发条件 | 解析方式 |
|------|----------|----------|
| **结构化 tool_calls** | LLM 通过原生 function calling 返回 | `AssistantMessage.getToolCalls()` |
| **文本 TOOL_CALL** | 系统提示词引导 LLM 输出 | 正则 `TOOL_CALL: {"tool": "...", "arguments": {...}}` |
| **DSML XML** | DeepSeek 模型原生输出 | 正则 `<invoke name="toolName">` + `<parameter>` |
| **直接回答** | 无需调用工具 | 直接返回文本 |

### 4.3 消息列表维护

`DecisionLayer.decide()` 在返回决策前会将原始 `AssistantMessage` 追加到消息列表：

- **结构化 tool_calls** → 原样追加（保留 `reasoning_content`）
- **文本 TOOL_CALL** → 新建 `AssistantMessage` + 注入 `toolCalls` 字段（供后续 `ToolResponseMessage` 关联）
- **直接回答** → 原样追加

---

## 五、终止判断 (TerminationEvaluator)

5 种终止条件，满足任一即停止循环：

| # | 条件 | 阈值 | 说明 |
|---|------|------|------|
| 1 | 最大轮数 | MAX_ROUNDS = 10 | 防止无限循环 |
| 2 | LLM 直接回答 | 决策类型为 ANSWER | 正常结束 |
| 3 | 死循环检测 | 连续 2 轮相同工具+相同参数 | 防止 LLM 反复调同一工具 |
| 4 | 无有效输出 | 决策类型为 NONE | LLM 异常情况 |
| 5 | 超时 | 60 秒 | 长时间无响应 |

---

## 六、五层安全防护 (AgentSecurityGuard)

| 层 | 防护 | 实现 | 触发 |
|----|------|------|------|
| ① | **Prompt 注入检测** | 关键词黑名单（中英文） | 用户输入含越狱/注入词 |
| ② | **工具参数校验** | 空参数/超长/白名单路径/黑名单命令 | LLM 生成非法参数 |
| ③ | **敏感操作确认** | `confirm=true` 开关 | 写文件/删文件/关进程等 |
| ④ | **工具调用频率控制** | 同工具≤3次，总计≤10次 | LLM 频繁调同一工具 |
| ⑤ | **输出内容过滤** | 正则匹配手机号/API Key/密码 | LLM 回复含敏感信息 |

与 `ToolSafety` 的关系：
- `ToolSafety`（底层守卫）：路径白名单、命令黑名单
- `AgentSecurityGuard`（上层策略）：注入检测、参数校验、操作确认、频率控制、输出过滤

---

## 七、状态管理 (AgentSession + AgentState)

### 7.1 状态流转

```
PERCEIVING ──→ DECIDING ──→ EXECUTING ──→ DECIDING ──→ ... ──→ COMPLETED
                                                              ├── FAILED
                                                              └── BLOCKED
```

### 7.2 频率控制

| 限制 | 值 | 说明 |
|------|-----|------|
| 同工具最大调用次数 | 3 | 防止死循环 |
| 总计最大调用次数 | 10 | 防止无限调工具 |
| 最大决策轮数 | 10 | 循环上限 |

---

## 八、编排器 (AgentOrchestrator)

### 8.1 依赖注入

```java
AgentOrchestrator(
    DecisionLayer decisionLayer,          // 决策引擎
    TerminationEvaluator terminationEvaluator,  // 终止判断
    AgentSecurityGuard securityGuard,     // 安全防护
    ConversationMapper conversationMapper, // 会话持久化
    MessageMapper messageMapper,          // 消息持久化
    ToolCallbackProvider toolCallbackProvider  // 工具注册中心
)
```

### 8.2 系统提示词构建

两步构建：
1. `buildToolDescriptions()` — 遍历 `ToolCallbackProvider` 中注册的所有工具，提取名称、描述、参数名
2. `buildSystemPrompt()` — 嵌入到系统提示词中，包含核心规则和工具调用格式说明

### 8.3 工具调用容错

- JSON 参数预处理：转义未转义的换行符和制表符
- 安全拦截时仍生成 `ToolResponseMessage`（避免 API 报错 "insufficient tool messages"）
- 工具执行异常时返回友好错误信息

---

## 九、数据模型

### Decision 类

```java
Decision.Type { ANSWER, TOOL_CALL, NONE }

Decision.answer("text")       → Type=ANSWER,  answer="text"
Decision.toolCall([requests]) → Type=TOOL_CALL, toolCalls=[...]
Decision.none()               → Type=NONE
```

### ToolCallRequest 类

```java
ToolCallRequest(
    String id,           // 调用唯一 ID（LLM 生成）
    String toolName,     // 工具名，如 "openApp"
    String arguments     // 参数 JSON 字符串
)
```

### ChatEvent (SSE 事件)

```java
ChatEvent.thinking()          → type="thinking"
ChatEvent.toolCall(tool, args) → type="tool_call"
ChatEvent.toolResult(result)   → type="tool_result"
ChatEvent.message(text)        → type="message"
ChatEvent.done(convId, msgId)  → type="done"
ChatEvent.error(msg)           → type="error"
ChatEvent.blocked(reason)      → type="blocked"
ChatEvent.decision(type, data) → type="decision"
```

---

## 十、已知问题与边界处理

| 问题 | 处理方式 |
|------|----------|
| DeepSeek `reasoning_content` 未回传 | 保留原始 `AssistantMessage`（含 metadata/properties） |
| DeepSeek DSML XML 格式 | 正则解析 `<invoke name="">参数</invoke>` |
| LLM 生成未转义换行符的 JSON | `executeTool()` 中预替换 `\n` → `\\n` |
| 工具执行完成后 LLM 不回答 | 降级使用工具结果摘要 "已完成操作。" |
| 安全拦截后 tool_call 无对应响应 | 仍生成 `ToolResponseMessage`（含 BLOCKED 信息） |
| Prompt 注入 | 中英文关键词黑名单 |
| 死循环 | 同工具 + 同参数连续 2 轮触发终止 |
