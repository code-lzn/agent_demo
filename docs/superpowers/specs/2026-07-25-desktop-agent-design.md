# Desktop Agent — Design Spec

## Overview

基于 Spring AI 的桌面 Agent 后端 API，接入 DeepSeek 模型，Agent 可调用工具操控本机软件和文件，支持流式 SSE 对话，会话持久化到 MySQL。

- **日期**: 2026-07-25
- **项目**: Agent_Demo
- **模型**: DeepSeek (OpenAI 兼容)
- **端口**: 8123, context-path: /api

## Architecture

```
Knife4j UI (API doc + testing)
        │
        ▼
Controller Layer (ChatController, ConversationController)
        │
        ▼
Agent Service Layer (Spring AI ChatClient + ToolCallingManager)
   ├── DeepSeek API (OpenAI-compatible endpoint)
   ├── Function Calling loop (auto tool invocation)
   └── SSE streaming (Flux<String>)
        │
   ┌────┴────┐
   ▼         ▼
Tool Layer   Persistence Layer
ProcessTool  Conversation + Message
FileTool     (MySQL / MyBatis)
InputTool
   │
   ▼
Local OS (exec / filesystem / keyboard)
```

**Data flow**: User message → Controller → ChatClient (with tools) → DeepSeek → tool_calls detected → execute tool on local OS → result back to DeepSeek → final response SSE streamed.

**Tech stack**:

| Layer | Choice |
|-------|--------|
| LLM gateway | Spring AI 1.0+ + spring-ai-openai-spring-boot-starter |
| Tool registry | Spring AI `@Tool` annotation |
| Streaming | `Flux<String>` → SSE via `text/event-stream` |
| Chat memory | Spring AI JDBC ChatMemory (same MySQL) |
| ORM | MyBatis (existing) |
| DB | MySQL (existing) |
| API docs | Knife4j (already configured) |

## Tool Design

Three tool classes registered via `@Tool` annotation:

### ProcessTool
- `openApp(appPath)` — launch executable via ProcessBuilder
- `openApp(appPath, args)` — launch with arguments
- `closeApp(processName)` — kill by process name
- `listRunningApps()` — enumerate running processes

### FileTool
- `readFile(filePath)` — read entire file
- `readFileLines(filePath, n)` — first N lines
- `countLines(filePath)` — line count
- `listDir(dirPath)` — directory listing
- `writeFile(filePath, content)` — write to file
- `searchInFile(filePath, keyword)` — grep in file

### InputTool
- `typeText(text)` — type into focused window
- `pressKeys(keyCombo)` — simulate hotkey (e.g. "ctrl+s")
- `typeToApp(appName, text)` — switch to app then type

### Safety
- `allowedPaths`: whitelist directories (default: user home + project dir)
- `blockedCommands`: deny-list dangerous commands
- `confirm=true` required in API request for destructive operations

## API Design

### Chat

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/chat/stream` | SSE streaming chat with Agent tool calling |
| POST | `/api/chat` | Non-streaming fallback |

**Request** `POST /api/chat/stream`:
```json
{
  "conversationId": "uuid-optional",
  "message": "帮我打开记事本，输入 hello world",
  "confirm": true
}
```

**SSE events**:
```
event: thinking      → thinking...
event: tool_call     → {"tool":"openApp","args":{"appPath":"notepad.exe"}}
event: tool_result   → {"result":"started notepad.exe"}
event: message       → "已打开记事本"
event: done          → {"conversationId":"xxx","messageId":"yyy"}
```

### Conversations

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/conversations` | Paginated list |
| POST | `/api/conversations` | Create new |
| GET | `/api/conversations/{id}` | Detail + history |
| DELETE | `/api/conversations/{id}` | Delete |

### Tools

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/tools` | List all available tools |

## Data Model

```
conversation
├── id              VARCHAR(36)  PK
├── title           VARCHAR(200)
├── model           VARCHAR(50)   -- e.g. deepseek-chat
├── created_at      DATETIME
└── updated_at      DATETIME

message
├── id              VARCHAR(36)  PK
├── conversation_id VARCHAR(36)  FK → conversation
├── role            VARCHAR(20)   -- user/assistant/tool
├── content         TEXT
├── tool_calls      JSON          -- [{tool, args, result}]
├── created_at      DATETIME
└── INDEX idx_conv_id (conversation_id)
```

Spring AI ChatMemory uses the same JDBC `message` table to manage conversation context window.

## Project Structure

```
src/main/java/com/limou/agent_demo/
├── AgentDemoApplication.java
├── config/
│   └── AiConfig.java              -- DeepSeek ChatClient + ChatMemory beans
├── controller/
│   ├── ChatController.java        -- POST /api/chat/stream, POST /api/chat
│   ├── ConversationController.java -- CRUD /api/conversations
│   └── ToolController.java        -- GET /api/tools
├── service/
│   └── AgentService.java          -- ChatClient wrapper, streaming logic
├── tool/
│   ├── ProcessTool.java           -- @Tool beans for process management
│   ├── FileTool.java              -- @Tool beans for file operations
│   └── InputTool.java             -- @Tool beans for keyboard input
├── entity/
│   ├── Conversation.java
│   └── Message.java
├── mapper/
│   ├── ConversationMapper.java
│   └── MessageMapper.java
└── dto/
    ├── ChatRequest.java
    ├── ChatEvent.java
    └── ConversationVO.java
```

## Dependencies

```xml
<!-- Spring AI OpenAI starter (for DeepSeek) -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-openai-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

## Configuration

```yaml
spring:
  ai:
    openai:
      api-key: ${DEEPSEEK_API_KEY}
      base-url: https://api.deepseek.com/v1
      chat:
        options:
          model: deepseek-chat
          temperature: 0.7
```
