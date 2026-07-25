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


# Agent 工具模块文档

## 概述

本项目共 **20 个工具模块**，注册为 Spring AI Tool，Agent 可在对话中自动调用。所有工具类位于 `com.limou.agent_demo.tool` 包下。

---

## 目录

1. [FileTool — 文件管理](#1-filetool--文件管理)
2. [InputTool — 键盘输入模拟](#2-inputtool--键盘输入模拟)
3. [ProcessTool — 进程管理](#3-processtool--进程管理)
4. [WindowTool — 窗口管理](#4-windowtool--窗口管理)
5. [ClipboardTool — 剪贴板操作](#5-clipboardtool--剪贴板操作)
6. [SystemTool — 系统操作](#6-systemtool--系统操作)
7. [WebTool — HTTP 请求](#7-webtool--http-请求)
8. [NotificationTool — 系统通知](#8-notificationtool--系统通知)
9. [MouseTool — 鼠标控制](#9-mousetool--鼠标控制)
10. [AudioTool — 音频与语音](#10-audiotool--音频与语音)
11. [OfficeTool — Office 文档](#11-officetool--office-文档)
12. [EmailTool — 邮件收发](#12-emailtool--邮件收发)
13. [DatabaseTool — 数据库查询](#13-databasetool--数据库查询)
14. [CronTool — 任务调度](#14-crontool--任务调度)
15. [LogMonitorTool — 日志监控](#15-logmonitortool--日志监控)
16. [ImageTool — 图片处理](#16-imagetool--图片处理)
17. [ArchiveTool — 压缩解压](#17-archivetool--压缩解压)
18. [DownloadTool — 文件下载](#18-downloadtool--文件下载)
19. [RegistryTool — 注册表操作](#19-registrytool--注册表操作)
20. [PowerTool — 电源管理](#20-powertool--电源管理)
21. [ToolSafety — 安全策略](#21-toolsafety--安全策略)

---

## FileTool — 文件管理

**依赖**：JDK `java.nio.file`

| 方法            | 参数                      | 说明                                       |
| --------------- | ------------------------- | ------------------------------------------ |
| `readFile`      | `filePath`                | 读取文件的全部文本内容                     |
| `readFileLines` | `filePath, n`             | 读取文件开头 N 行                          |
| `readFileRange` | `filePath, offset, limit` | 从指定行号起读取 N 行（分块读大文件）      |
| `readLastLines` | `filePath, n`             | 读取文件末尾 N 行（类似 tail）             |
| `countLines`    | `filePath`                | 统计文件总行数                             |
| `writeFile`     | `filePath, content`       | 写入内容到文件（覆盖）                     |
| `appendFile`    | `filePath, content`       | 追加内容到文件末尾                         |
| `listDir`       | `dirPath`                 | 列出目录下所有文件和子目录                 |
| `createDir`     | `dirPath`                 | 递归创建目录（类似 mkdir -p）              |
| `deleteFile`    | `filePath`                | 删除指定文件                               |
| `deleteDir`     | `dirPath`                 | 递归删除目录及所有内容                     |
| `copyFile`      | `sourcePath, targetPath`  | 复制文件到目标路径                         |
| `moveFile`      | `sourcePath, targetPath`  | 移动或重命名文件/目录                      |
| `getFileInfo`   | `filePath`                | 获取文件/目录详情：大小、时间、权限        |
| `openFile`      | `filePath`                | 用系统默认程序打开文件（不包含可执行文件） |
| `openDir`       | `dirPath`                 | 在资源管理器中打开目录                     |
| `searchInFile`  | `filePath, keyword`       | 在文件中搜索关键字，返回行号和内容         |
| `searchInDir`   | `dirPath, keyword`        | 递归搜索目录下所有文本文件中的关键字       |
| `findFiles`     | `dirPath, pattern`        | 按通配符查找文件（如 `*.java`）            |

---

##  InputTool — 键盘输入模拟

**依赖**：JDK `java.awt.Robot`

| 方法        | 参数            | 说明                                      |
| ----------- | --------------- | ----------------------------------------- |
| `typeText`  | `text`          | 在当前焦点窗口逐字符打字                  |
| `pressKeys` | `keyCombo`      | 按组合键，如 `ctrl+s`、`alt+tab`、`win+r` |
| `typeToApp` | `appName, text` | 按 Alt+Tab 切换后键入文字                 |

---

## ProcessTool — 进程管理

**依赖**：JDK `ProcessBuilder`

| 方法              | 参数            | 说明                                 |
| ----------------- | --------------- | ------------------------------------ |
| `openApp`         | `appPath`       | 启动应用程序或可执行文件             |
| `openAppWithArgs` | `appPath, args` | 带命令行参数启动应用                 |
| `closeApp`        | `processName`   | 按进程名强制关闭（如 `notepad.exe`） |
| `listRunningApps` | —               | 列出当前运行的进程（前 4000 字符）   |

---

##  WindowTool — 窗口管理

**依赖**：JNA + Win32 API（`user32.dll`）

| 方法              | 参数                | 说明                                 |
| ----------------- | ------------------- | ------------------------------------ |
| `listWindows`     | —                   | 枚举所有可见窗口，返回标题和句柄     |
| `getActiveWindow` | —                   | 获取当前活动窗口标题                 |
| `focusWindow`     | `title`             | 按标题查找并激活窗口（支持部分匹配） |
| `closeWindow`     | `title`             | 发送 WM_CLOSE 消息关闭窗口           |
| `minimizeWindow`  | `title`             | 最小化窗口                           |
| `maximizeWindow`  | `title`             | 最大化窗口                           |
| `resizeWindow`    | `title, x, y, w, h` | 调整窗口位置和大小                   |

---

##  ClipboardTool — 剪贴板操作

**依赖**：JDK `java.awt.Toolkit`

| 方法               | 参数   | 说明                                   |
| ------------------ | ------ | -------------------------------------- |
| `getClipboard`     | —      | 读取系统剪贴板文本                     |
| `setClipboard`     | `text` | 写入文本到剪贴板                       |
| `paste`            | —      | 模拟 Ctrl+V 粘贴                       |
| `typeViaClipboard` | `text` | 一键：写剪贴板并粘贴（处理长文本最快） |

---

##  SystemTool — 系统操作

**依赖**：JDK

| 方法             | 参数      | 说明                                   |
| ---------------- | --------- | -------------------------------------- |
| `runCommand`     | `command` | 执行 Windows 命令（白名单模式）        |
| `getCurrentTime` | —         | 获取当前日期时间                       |
| `getEnvVar`      | `name`    | 读取指定环境变量                       |
| `listEnvVars`    | —         | 列出所有环境变量                       |
| `getCurrentDir`  | —         | 获取当前工作目录                       |
| `getSystemInfo`  | —         | 获取系统信息：OS、Java 版本、CPU、内存 |

---

##  WebTool — HTTP 请求

**依赖**：JDK `java.net.http.HttpClient`

| 方法         | 参数                              | 说明        |
| ------------ | --------------------------------- | ----------- |
| `httpGet`    | `url, headers`                    | GET 请求    |
| `httpPost`   | `url, body, contentType, headers` | POST 请求   |
| `httpPut`    | `url, body, headers`              | PUT 请求    |
| `httpDelete` | `url, headers`                    | DELETE 请求 |

**headers 格式**：JSON 键值对，如 `{"Authorization":"Bearer xxx"}`，不需要时传空字符串。

---

##  NotificationTool — 系统通知

**依赖**：JDK `java.awt.SystemTray`

| 方法            | 参数             | 说明                 |
| --------------- | ---------------- | -------------------- |
| `notify`        | `title, message` | 弹出系统托盘信息通知 |
| `notifyWarning` | `title, message` | 弹出警告通知         |

---


## OfficeTool — Office 文档

**依赖**：Apache POI

| 方法              | 参数                        | 说明                               |
| ----------------- | --------------------------- | ---------------------------------- |
| `readExcel`       | `filePath, sheetIndex`      | 读取 .xlsx 文件内容（最多 500 行） |
| `listExcelSheets` | `filePath`                  | 列出 Excel 所有 Sheet 名           |
| `writeExcel`      | `filePath, data, sheetName` | 用制表符分隔数据创建 .xlsx         |
| `readWord`        | `filePath`                  | 读取 .docx 文本内容                |
| `writeWord`       | `filePath, text`            | 创建 .docx 文件（空行分隔段落）    |
| `readCsv`         | `filePath`                  | 读取 CSV 文件                      |
| `writeCsv`        | `filePath, content`         | 写入 CSV 文件                      |

---

## EmailTool — 邮件收发

**依赖**：Jakarta Mail

| 方法         | 参数                                                         | 说明                         |
| ------------ | ------------------------------------------------------------ | ---------------------------- |
| `sendEmail`  | `smtpHost, smtpPort, useSSL, username, password, from, to, subject, body` | 通过 SMTP 发送邮件           |
| `readEmails` | `imapHost, imapPort, useSSL, username, password, maxResults` | 通过 IMAP 读取收件箱最近邮件 |

---

## DatabaseTool — 数据库查询

**依赖**：Spring DataSource（复用项目已有数据源）

| 方法         | 参数  | 说明                                          |
| ------------ | ----- | --------------------------------------------- |
| `query`      | `sql` | 执行 SELECT 查询（最多 200 行，禁止修改操作） |
| `listTables` | —     | 列出数据库中所有表                            |

---

## CronTool — 任务调度

**依赖**：JDK `ScheduledExecutorService`

| 方法                 | 参数                       | 说明               |
| -------------------- | -------------------------- | ------------------ |
| `scheduleTask`       | `delaySeconds, command`    | 延迟后执行一次命令 |
| `scheduleRecurring`  | `intervalSeconds, command` | 定期重复执行命令   |
| `listScheduledTasks` | —                          | 列出所有活动任务   |
| `cancelTask`         | `taskId`                   | 取消指定任务       |

---

## LogMonitorTool — 日志监控

**依赖**：JDK `java.nio.file`

| 方法           | 参数                             | 说明                                   |
| -------------- | -------------------------------- | -------------------------------------- |
| `startMonitor` | `watchFile, keyword, outputFile` | 监控文件新增行中的关键字，写入输出文件 |
| `listMonitors` | —                                | 列出所有活动监控                       |
| `stopMonitor`  | `monitorId`                      | 停止指定监控                           |

---

## ImageTool — 图片处理

**依赖**：JDK `javax.imageio`

| 方法           | 参数                                  | 说明                                 |
| -------------- | ------------------------------------- | ------------------------------------ |
| `getImageInfo` | `filePath`                            | 获取图片格式、尺寸、文件大小         |
| `resizeImage`  | `sourcePath, destPath, width, height` | 缩放图片到指定尺寸                   |
| `convertImage` | `sourcePath, destPath`                | 转换图片格式（jpg/png/gif/bmp 互转） |
| `cropImage`    | `sourcePath, destPath, x, y, w, h`    | 裁剪图片指定区域                     |

---

##  ArchiveTool — 压缩解压

**依赖**：JDK `java.util.zip`

| 方法      | 参数                  | 说明                    |
| --------- | --------------------- | ----------------------- |
| `zip`     | `sourcePath, zipPath` | 将文件或目录打包为 .zip |
| `unzip`   | `zipPath, destDir`    | 解压 .zip 到指定目录    |
| `listZip` | `zipPath`             | 查看 .zip 内容列表      |

---

##  DownloadTool — 文件下载

**依赖**：JDK `java.net.http.HttpClient`

| 方法           | 参数            | 说明                           |
| -------------- | --------------- | ------------------------------ |
| `downloadFile` | `url, savePath` | 下载 URL 指向的文件到本地      |
| `getFileSize`  | `url`           | 通过 HEAD 请求获取远程文件大小 |

---

## RegistryTool — 注册表操作

**依赖**：JNA `Advapi32`（需管理员权限写 HKLM）

| 方法                    | 参数                          | 说明               |
| ----------------------- | ----------------------------- | ------------------ |
| `readRegistry`          | `hive, key, valueName`        | 读取注册表字符串值 |
| `writeRegistry`         | `hive, key, valueName, value` | 写入注册表字符串值 |
| `listInstalledPrograms` | —                             | 列出所有已安装程序 |

---

## PowerTool — 电源管理

**依赖**：Windows `shutdown` / `rundll32` 命令

| 方法             | 参数           | 说明                  |
| ---------------- | -------------- | --------------------- |
| `shutdown`       | `delaySeconds` | 延迟关机（0=立即）    |
| `restart`        | `delaySeconds` | 延迟重启              |
| `sleep`          | —              | 进入睡眠模式          |
| `hibernate`      | —              | 进入休眠模式          |
| `lockScreen`     | —              | 锁屏                  |
| `logoff`         | —              | 注销当前用户          |
| `cancelShutdown` | —              | 取消已计划的关机/重启 |

---

## ToolSafety — 安全策略

**依赖**：JDK

ToolSafety 是工具安全基座，不被 Agent 直接调用，但被其他工具有效使用。

| 安全机制     | 说明                                                     | 配置项                          |
| ------------ | -------------------------------------------------------- | ------------------------------- |
| 路径白名单   | FileTool、ArchiveTool、DownloadTool 等只能访问允许的目录 | `agent.safety.allowed-paths`    |
| 命令黑名单   | ProcessTool 禁止执行危险命令                             | 内置固定列表                    |
| 命令白名单   | SystemTool.runCommand 只允许执行安全命令                 | `agent.safety.allowed-commands` |
| URL 内网防护 | WebTool 禁止访问内网地址，防止 SSRF                      | `agent.safety.blocked-domains`  |

默认配置示例（`application-dev.yml`）：

```yaml
agent:
  safety:
    allowed-paths: ${user.home},${user.dir},C:/,D:/,E:/,F:/,G:/,H:/
    allowed-commands: git, npm, python
```

---

## 附录

### 方法数统计

| 模块             | 方法数  | 新增依赖                            |
| ---------------- | ------- | ----------------------------------- |
| FileTool         | 18      | 无                                  |
| MouseTool        | 8       | 无                                  |
| OfficeTool       | 7       | Apache POI                          |
| PowerTool        | 7       | 无                                  |
| WindowTool       | 7       | JNA                                 |
| SystemTool       | 6       | 无                                  |
| InputTool        | 3       | 无                                  |
| ProcessTool      | 4       | 无                                  |
| ClipboardTool    | 4       | 无                                  |
| WebTool          | 4       | 无                                  |
| ImageTool        | 4       | 无                                  |
| CronTool         | 4       | 无                                  |
| ArchiveTool      | 3       | 无                                  |
| LogMonitorTool   | 3       | 无                                  |
| RegistryTool     | 3       | JNA（已有）                         |
| AudioTool        | 2       | 无                                  |
| DownloadTool     | 2       | 无                                  |
| EmailTool        | 2       | Jakarta Mail（Spring Boot Starter） |
| DatabaseTool     | 2       | JDBC（已有）                        |
| NotificationTool | 1       | 无                                  |
| **总计**         | **~91** |                                     |

### Maven 依赖新增

```xml
<!-- JNA for WindowTool / RegistryTool -->
<dependency>
    <groupId>net.java.dev.jna</groupId>
    <artifactId>jna</artifactId>
    <version>5.14.0</version>
</dependency>
<dependency>
    <groupId>net.java.dev.jna</groupId>
    <artifactId>jna-platform</artifactId>
    <version>5.14.0</version>
</dependency>

<!-- Apache POI for OfficeTool -->
<dependency>
    <groupId>org.apache.poi</groupId>
    <artifactId>poi-ooxml</artifactId>
    <version>5.4.0</version>
</dependency>

<!-- Spring Boot Mail for EmailTool -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-mail</artifactId>
</dependency>
```

其余 17 个模块全部使用 JDK 自带 API，零额外依赖。