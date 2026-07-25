## SpringAI的框架的搭建

### 1.框架的搭建

```
@PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
@Operation(summary = "Stream chat with Agent (SSE)", description = "Agent can call tools automatically. Returns SSE events: thinking, message, tool_call, tool_result, done, error.")
public Flux<ChatEvent> streamChat(@RequestBody ChatRequest request) {
    return agentService.streamChat(request);
}
```

将工具加入到Agent里面



```
@Bean
public ChatClient chatClient(ChatClient.Builder builder, ChatMemory chatMemory, ToolCallbackProvider toolCallbackProvider) {
    return builder
            .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
            .defaultTools(toolCallbackProvider)
            .build();
}
```

### 2.嵌入到命令行

![image-20260725171501605](C:\Users\henan\AppData\Roaming\Typora\typora-user-images\image-20260725171501605.png)

基本已经可以

---

## 2026-07-25 下午：工具类模块扩展

### 一、新增工具（6个）

#### 1. WebTool — HTTP 请求
| 方法 | 说明 |
|------|------|
| `httpGet(url, headers)` | GET 请求，headers 传 JSON 如 `{"Authorization":"Bearer xxx"}` |
| `httpPost(url, body, contentType, headers)` | POST 请求，指定 Content-Type |
| `httpPut(url, body, headers)` | PUT 请求 |
| `httpDelete(url, headers)` | DELETE 请求 |

- 零外部依赖，JDK `java.net.http.HttpClient`
- URL 安全由 `ToolSafety.isUrlAllowed()` 保障，拦截 `file://`、localhost、内网 IP
- 响应体 1MB 截断，15s 超时，自动跟随重定向
- 手写 JSON 解析 headers（不引入 Jackson）

#### 2. NotificationTool — 系统通知
| 方法 | 说明 |
|------|------|
| `notify(title, message)` | 弹出 Windows 托盘通知气泡 |

- 基于 JDK `SystemTray` + `TrayIcon`
- 构造时注册透明占位图标到系统托盘
- 不可用时返回错误信息而非崩溃

#### 3. GrepTool — 递归代码搜索
| 方法 | 说明 |
|------|------|
| `searchCode(pattern, rootPath?, fileGlob?)` | 正则递归搜索，返回 `文件:行号:内容` |

- `rootPath` 默认项目根目录，受 ToolSafety 路径约束
- `fileGlob` 如 `*.java` 或 `*.{yml,xml}` 过滤文件类型
- 自动跳过二进制文件（检测 null 字节）
- 输出截断：50 条匹配 / 4000 字符

#### 4. EditTool — 精准文件编辑
| 方法 | 说明 |
|------|------|
| `editFile(filePath, oldString, newString, replaceAll?)` | 精确字符串替换 |

- `replaceAll=false` 时，多处匹配会报错并提示更精确指定
- 每次修改自动生成 `.bak` 备份文件
- oldString 必须完全匹配（包括缩进、换行）

#### 5. GitTool — 版本控制（只读）
| 方法 | 说明 |
|------|------|
| `gitStatus()` | `git status --porcelain` |
| `gitDiff(staged)` | 工作区 / 暂存区差异 |
| `gitLog(n)` | 最近 N 条提交记录 |
| `gitShowChangedFiles(n)` | 最近 N 次提交变动的文件 |
| `gitBranch()` | 当前分支名 |

- 所有操作只读，不执行 commit/push/pull
- 输出截断 4000 字符，30s 超时

#### 6. EmailTool（补注册）
- 文件已存在但未在 AiConfig 注册，本次补上

### 二、增强已有模块

#### ToolSafety
| 新增方法 | 说明 |
|----------|------|
| `isUrlAllowed(url)` | 拦截 `file://`、localhost、`127.0.0.1`、`192.168.x.x`、`10.x.x.x`、`172.16-31.x.x` |
| `isCommandAllowed(cmd)` | 命令白名单校验，提取命令名匹配，兼容 `.exe`/路径前缀 |
| `getMaxResponseBytes()` | 返回响应体限制 1MB |

#### AiConfig
- 注册全部 22 个工具类到 `MethodToolCallbackProvider`
- 通过 `.defaultTools(provider)` 让 Spring AI 自动向模型发送工具 schema

#### AgentDemoApplication
- 添加 `System.setProperty("java.awt.headless", "false")` 使 SystemTray 可用

### 三、项目工具全景（22个类）

| 域 | 工具 |
|------|------|
| 🖥️ 桌面自动化 | InputTool, WindowTool, ClipboardTool, MouseTool, ProcessTool, PowerTool |
| 📁 文件系统 | FileTool, ArchiveTool, DownloadTool, **GrepTool**, **EditTool** |
| 🌐 网络 | **WebTool** |
| 🔧 版本控制 | **GitTool** |
| ⚙️ 系统 | SystemTool, RegistryTool, **NotificationTool** |
| 🎵 媒体 | AudioTool, ImageTool |
| 📊 数据 | DatabaseTool |
| ⏰ 定时 | CronTool |
| 📋 监控 | LogMonitorTool |
| 📧 通信 | EmailTool |
| 🔒 安全 | ToolSafety |

### 四、关键发现与踩坑

1. **DeepSeek 不支持 Function Calling**：通过 `.chatResponse()` 抓包确认 `toolCalls=[]`，模型只在文本中"描述"调用了工具，实际并未触发 Spring AI 的工具执行链路
2. **Spring Boot 默认 headless**：`java.awt.headless=true` 导致 `SystemTray.isSupported()` 返回 false，需显式关闭
3. **Spring AI 2.0 工具注册**：正确方式是 `.defaultTools(provider)`，Spring AI 自动将 `@Tool` 注解的方法转为 OpenAI Function Calling 的 JSON Schema 发给模型
4. **Effectively Final**：lambda 中引用的局部变量必须是 final 或 effectively final，`if-else` 赋值会导致编译错误，需改用三元表达式

### 五、待完成

- 换用支持 Function Calling 的模型（千问 qwen-plus / GPT-4o-mini）验证端到端工具调用链路
- 确认 NotificationTool 在模型真正调用后能否正常弹窗
- 