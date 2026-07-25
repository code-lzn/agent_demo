# Desktop Agent Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a Spring AI-powered Desktop Agent API that can control local software, files, and keyboard input via DeepSeek model, with SSE streaming and MySQL persistence.

**Architecture:** Spring Boot backend with Spring AI ChatClient + @Tool-annotated beans for function calling. Conversation context loaded from MySQL into InMemoryChatMemory per request. Knife4j for API documentation/testing.

**Tech Stack:** Spring Boot + Spring AI 1.0.0 + DeepSeek (OpenAI-compatible) + MyBatis + MySQL + Knife4j

## Global Constraints

- Java 21, Maven
- Model: deepseek-chat via https://api.deepseek.com/v1
- API key from env var DEEPSEEK_API_KEY
- Server port 8123, context-path /api
- All tools in `com.limou.agent_demo.tool` package
- Safety: path whitelist, command blacklist, confirm flag for destructive ops

---

### Task 1: Dependencies & Configuration

**Files:**
- Modify: `D:\idea-study\Agent_Demo\pom.xml`
- Modify: `D:\idea-study\Agent_Demo\src\main\resources\application.yml`

**Interfaces:**
- Produces: Spring AI OpenAI starter on classpath, DeepSeek configuration in application.yml

- [ ] **Step 1: Add Spring AI BOM and dependency to pom.xml**

Replace the dependencies section with these additions. Remove the duplicate Lombok dependency (lines 53-57). Add the Spring AI BOM in `<dependencyManagement>` and the OpenAI starter in `<dependencies>`.

First, add the BOM inside a `<dependencyManagement>` section after `<properties>`:

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-bom</artifactId>
            <version>1.0.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

Then add the Spring AI OpenAI starter dependency after the knife4j dependency:

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-openai-spring-boot-starter</artifactId>
</dependency>
```

Also remove the duplicate Lombok dependency (the second `<dependency>` block for `org.projectlombok` at lines 53-57).

- [ ] **Step 2: Add DeepSeek configuration to application.yml**

Append to `src/main/resources/application.yml`:

```yaml
spring:
  ai:
    openai:
      api-key: ${DEEPSEEK_API_KEY}
      base-url: https://api.deepseek.com
      chat:
        options:
          model: deepseek-chat
          temperature: 0.7
```

- [ ] **Step 3: Set env var and verify build**

Run: `set DEEPSEEK_API_KEY=sk-test-placeholder && mvn compile -f "D:\idea-study\Agent_Demo\pom.xml"`

Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add pom.xml src/main/resources/application.yml
git commit -m "feat: add Spring AI OpenAI starter and DeepSeek configuration"
```

---

### Task 2: Database Schema, Entities & Mappers

**Files:**
- Create: `D:\idea-study\Agent_Demo\src\main\resources\schema.sql`
- Create: `D:\idea-study\Agent_Demo\src\main\java\com\limou\agent_demo\entity\Conversation.java`
- Create: `D:\idea-study\Agent_Demo\src\main\java\com\limou\agent_demo\entity\Message.java`
- Create: `D:\idea-study\Agent_Demo\src\main\java\com\limou\agent_demo\mapper\ConversationMapper.java`
- Create: `D:\idea-study\Agent_Demo\src\main\java\com\limou\agent_demo\mapper\MessageMapper.java`
- Modify: `D:\idea-study\Agent_Demo\src\main\resources\application.yml`

**Interfaces:**
- Produces:
  - `Conversation` entity: id (String/UUID), title (String), model (String), createdAt, updatedAt (LocalDateTime)
  - `Message` entity: id (String/UUID), conversationId (String), role (String), content (String), toolCalls (String/JSON), createdAt (LocalDateTime)
  - `ConversationMapper`: insert, selectById, selectAll (with pagination offset/limit), deleteById, updateTitle
  - `MessageMapper`: insert, selectByConversationId (ordered by created_at)

- [ ] **Step 1: Create schema.sql**

Create `src/main/resources/schema.sql`:

```sql
CREATE TABLE IF NOT EXISTS conversation (
    id VARCHAR(36) PRIMARY KEY,
    title VARCHAR(200),
    model VARCHAR(50),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS message (
    id VARCHAR(36) PRIMARY KEY,
    conversation_id VARCHAR(36) NOT NULL,
    role VARCHAR(20) NOT NULL,
    content TEXT,
    tool_calls JSON,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_conv_id (conversation_id),
    FOREIGN KEY (conversation_id) REFERENCES conversation(id) ON DELETE CASCADE
);
```

- [ ] **Step 2: Add MyBatis configuration to application.yml**

Append to `application.yml`:

```yaml
mybatis:
  mapper-locations: classpath:mapper/*.xml
  type-aliases-package: com.limou.agent_demo.entity

spring:
  sql:
    init:
      mode: always
      schema-locations: classpath:schema.sql
```

- [ ] **Step 3: Create Conversation entity**

Create `src/main/java/com/limou/agent_demo/entity/Conversation.java`:

```java
package com.limou.agent_demo.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class Conversation {
    private String id;
    private String title;
    private String model;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

- [ ] **Step 4: Create Message entity**

Create `src/main/java/com/limou/agent_demo/entity/Message.java`:

```java
package com.limou.agent_demo.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class Message {
    private String id;
    private String conversationId;
    private String role;
    private String content;
    private String toolCalls;
    private LocalDateTime createdAt;
}
```

- [ ] **Step 5: Create ConversationMapper interface**

Create `src/main/java/com/limou/agent_demo/mapper/ConversationMapper.java`:

```java
package com.limou.agent_demo.mapper;

import com.limou.agent_demo.entity.Conversation;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface ConversationMapper {

    @Insert("INSERT INTO conversation (id, title, model) VALUES (#{id}, #{title}, #{model})")
    void insert(Conversation conv);

    @Select("SELECT * FROM conversation WHERE id = #{id}")
    Conversation selectById(String id);

    @Select("SELECT * FROM conversation ORDER BY updated_at DESC LIMIT #{limit} OFFSET #{offset}")
    List<Conversation> selectAll(@Param("offset") int offset, @Param("limit") int limit);

    @Delete("DELETE FROM conversation WHERE id = #{id}")
    int deleteById(String id);

    @Update("UPDATE conversation SET title = #{title} WHERE id = #{id}")
    int updateTitle(@Param("id") String id, @Param("title") String title);
}
```

- [ ] **Step 6: Create MessageMapper interface**

Create `src/main/java/com/limou/agent_demo/mapper/MessageMapper.java`:

```java
package com.limou.agent_demo.mapper;

import com.limou.agent_demo.entity.Message;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface MessageMapper {

    @Insert("INSERT INTO message (id, conversation_id, role, content, tool_calls) " +
            "VALUES (#{id}, #{conversationId}, #{role}, #{content}, #{toolCalls})")
    void insert(Message msg);

    @Select("SELECT * FROM message WHERE conversation_id = #{conversationId} ORDER BY created_at ASC")
    List<Message> selectByConversationId(String conversationId);

    @Select("SELECT * FROM message WHERE conversation_id = #{conversationId} " +
            "ORDER BY created_at DESC LIMIT #{limit}")
    List<Message> selectRecentByConversationId(@Param("conversationId") String conversationId,
                                               @Param("limit") int limit);

    @Delete("DELETE FROM message WHERE conversation_id = #{conversationId}")
    int deleteByConversationId(String conversationId);
}
```

- [ ] **Step 7: Verify build**

Run: `mvn compile -f "D:\idea-study\Agent_Demo\pom.xml"`

Expected: BUILD SUCCESS

- [ ] **Step 8: Commit**

```bash
git add src/main/resources/schema.sql src/main/resources/application.yml src/main/java/com/limou/agent_demo/entity/ src/main/java/com/limou/agent_demo/mapper/
git commit -m "feat: add conversation and message entities with MyBatis mappers"
```

---

### Task 3: DTO Layer

**Files:**
- Create: `D:\idea-study\Agent_Demo\src\main\java\com\limou\agent_demo\dto\ChatRequest.java`
- Create: `D:\idea-study\Agent_Demo\src\main\java\com\limou\agent_demo\dto\ChatEvent.java`
- Create: `D:\idea-study\Agent_Demo\src\main\java\com\limou\agent_demo\dto\ConversationVO.java`

**Interfaces:**
- Produces:
  - `ChatRequest`: conversationId (String, optional), message (String), confirm (boolean, default false)
  - `ChatEvent`: type (String: thinking/tool_call/tool_result/message/done), data (Object)
  - `ConversationVO`: id, title, model, createdAt, updatedAt, messageCount (int), firstMessage (String)

- [ ] **Step 1: Create ChatRequest**

Create `src/main/java/com/limou/agent_demo/dto/ChatRequest.java`:

```java
package com.limou.agent_demo.dto;

import lombok.Data;

@Data
public class ChatRequest {
    private String conversationId;
    private String message;
    private boolean confirm;
}
```

- [ ] **Step 2: Create ChatEvent**

Create `src/main/java/com/limou/agent_demo/dto/ChatEvent.java`:

```java
package com.limou.agent_demo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatEvent {
    private String type;
    private Object data;

    public static ChatEvent thinking() {
        return new ChatEvent("thinking", "Agent is thinking...");
    }

    public static ChatEvent toolCall(String tool, Object args) {
        return new ChatEvent("tool_call", java.util.Map.of("tool", tool, "args", args));
    }

    public static ChatEvent toolResult(Object result) {
        return new ChatEvent("tool_result", java.util.Map.of("result", result));
    }

    public static ChatEvent message(String text) {
        return new ChatEvent("message", text);
    }

    public static ChatEvent done(String conversationId, String messageId) {
        return new ChatEvent("done", java.util.Map.of("conversationId", conversationId, "messageId", messageId));
    }

    public static ChatEvent error(String error) {
        return new ChatEvent("error", java.util.Map.of("message", error));
    }
}
```

- [ ] **Step 3: Create ConversationVO**

Create `src/main/java/com/limou/agent_demo/dto/ConversationVO.java`:

```java
package com.limou.agent_demo.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class ConversationVO {
    private String id;
    private String title;
    private String model;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private int messageCount;
    private String firstMessage;
}
```

- [ ] **Step 4: Verify build**

Run: `mvn compile -f "D:\idea-study\Agent_Demo\pom.xml"`

Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/limou/agent_demo/dto/
git commit -m "feat: add DTO classes for chat requests, events, and conversation views"
```

---

### Task 4: AI Configuration

**Files:**
- Create: `D:\idea-study\Agent_Demo\src\main\java\com\limou\agent_demo\config\AiConfig.java`

**Interfaces:**
- Produces: `ChatClient` bean (with ChatMemory advisor), `ChatMemory` bean (InMemoryChatMemory)

- [ ] **Step 1: Create AiConfig**

Create `src/main/java/com/limou/agent_demo/config/AiConfig.java`:

```java
package com.limou.agent_demo.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.ai.chat.memory.MessageChatMemoryAdvisor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {

    @Bean
    public ChatMemory chatMemory() {
        return new InMemoryChatMemory();
    }

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder, ChatMemory chatMemory) {
        return builder
                .defaultAdvisors(new MessageChatMemoryAdvisor(chatMemory))
                .build();
    }
}
```

- [ ] **Step 2: Verify build**

Run: `mvn compile -f "D:\idea-study\Agent_Demo\pom.xml"`

Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/limou/agent_demo/config/
git commit -m "feat: add Spring AI ChatClient and ChatMemory bean configuration"
```

---

### Task 5: Tool Layer

**Files:**
- Create: `D:\idea-study\Agent_Demo\src\main\java\com\limou\agent_demo\tool\ProcessTool.java`
- Create: `D:\idea-study\Agent_Demo\src\main\java\com\limou\agent_demo\tool\FileTool.java`
- Create: `D:\idea-study\Agent_Demo\src\main\java\com\limou\agent_demo\tool\InputTool.java`
- Create: `D:\idea-study\Agent_Demo\src\main\java\com\limou\agent_demo\tool\ToolSafety.java`

**Interfaces:**
- Produces:
  - `ProcessTool` beans: openApp(String appPath), openApp(String appPath, String args), closeApp(String processName), listRunningApps()
  - `FileTool` beans: readFile(String filePath), readFileLines(String filePath, int n), countLines(String filePath), listDir(String dirPath), writeFile(String filePath, String content), searchInFile(String filePath, String keyword)
  - `InputTool` beans: typeText(String text), pressKeys(String keyCombo), typeToApp(String appName, String text)
  - `ToolSafety`: pathAllowed(String path) → boolean, commandBlocked(String cmd) → boolean

- [ ] **Step 1: Create ToolSafety**

Create `src/main/java/com/limou/agent_demo/tool/ToolSafety.java`:

```java
package com.limou.agent_demo.tool;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

@Component
public class ToolSafety {

    private static final Set<String> BLOCKED_COMMANDS = Set.of(
            "format", "del /f", "rm -rf", "shutdown", "taskkill /f /im svchost"
    );

    private final List<String> allowedPaths;

    public ToolSafety(@Value("${agent.safety.allowed-paths:${user.home},${user.dir}}") String paths) {
        this.allowedPaths = List.of(paths.split(","));
    }

    public boolean isPathAllowed(String filePath) {
        Path p = Path.of(filePath).toAbsolutePath().normalize();
        return allowedPaths.stream().anyMatch(allowed -> p.startsWith(Path.of(allowed.trim())));
    }

    public boolean isCommandBlocked(String command) {
        String lower = command.toLowerCase();
        return BLOCKED_COMMANDS.stream().anyMatch(lower::contains);
    }
}
```

- [ ] **Step 2: Create ProcessTool**

Create `src/main/java/com/limou/agent_demo/tool/ProcessTool.java`:

```java
package com.limou.agent_demo.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class ProcessTool {

    private final ToolSafety safety;

    public ProcessTool(ToolSafety safety) {
        this.safety = safety;
    }

    @Tool(description = "Launch an application or executable on this computer")
    public String openApp(@ToolParam(description = "Full path to the executable, e.g. notepad.exe or C:\\app\\my.exe") String appPath) {
        if (safety.isCommandBlocked(appPath)) {
            return "Blocked: '" + appPath + "' is not allowed for safety reasons";
        }
        try {
            new ProcessBuilder(appPath).start();
            return "Successfully launched: " + appPath;
        } catch (Exception e) {
            return "Failed to launch '" + appPath + "': " + e.getMessage();
        }
    }

    @Tool(description = "Launch an application with command-line arguments")
    public String openAppWithArgs(
            @ToolParam(description = "Full path to the executable") String appPath,
            @ToolParam(description = "Command-line arguments") String args) {
        if (safety.isCommandBlocked(appPath)) {
            return "Blocked: '" + appPath + "' is not allowed";
        }
        try {
            new ProcessBuilder(appPath, args).start();
            return "Successfully launched: " + appPath + " " + args;
        } catch (Exception e) {
            return "Failed to launch: " + e.getMessage();
        }
    }

    @Tool(description = "Close an application by its process name, e.g. notepad.exe")
    public String closeApp(@ToolParam(description = "Process name to kill, e.g. notepad.exe") String processName) {
        try {
            ProcessBuilder pb = new ProcessBuilder("taskkill", "/f", "/im", processName);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes());
            return "Close result: " + output;
        } catch (Exception e) {
            return "Failed to close '" + processName + "': " + e.getMessage();
        }
    }

    @Tool(description = "List currently running processes on this computer")
    public String listRunningApps() {
        try {
            ProcessBuilder pb = new ProcessBuilder("tasklist");
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes());
            if (output.length() > 4000) {
                output = output.substring(0, 4000) + "\n... (truncated)";
            }
            return output;
        } catch (Exception e) {
            return "Failed to list processes: " + e.getMessage();
        }
    }
}
```

- [ ] **Step 3: Create FileTool**

Create `src/main/java/com/limou/agent_demo/tool/FileTool.java`:

```java
package com.limou.agent_demo.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.util.stream.Collectors;

@Component
public class FileTool {

    private final ToolSafety safety;

    public FileTool(ToolSafety safety) {
        this.safety = safety;
    }

    @Tool(description = "Read the entire content of a file")
    public String readFile(@ToolParam(description = "Full path to the file") String filePath) {
        if (!safety.isPathAllowed(filePath)) return "Access denied: " + filePath;
        try {
            return Files.readString(Path.of(filePath));
        } catch (IOException e) {
            return "Error reading file: " + e.getMessage();
        }
    }

    @Tool(description = "Read the first N lines of a file")
    public String readFileLines(
            @ToolParam(description = "Full path to the file") String filePath,
            @ToolParam(description = "Number of lines to read") int n) {
        if (!safety.isPathAllowed(filePath)) return "Access denied: " + filePath;
        try {
            return Files.lines(Path.of(filePath)).limit(n)
                    .collect(Collectors.joining("\n"));
        } catch (IOException e) {
            return "Error reading file: " + e.getMessage();
        }
    }

    @Tool(description = "Count how many lines are in a file")
    public String countLines(@ToolParam(description = "Full path to the file") String filePath) {
        if (!safety.isPathAllowed(filePath)) return "Access denied: " + filePath;
        try {
            long count = Files.lines(Path.of(filePath)).count();
            return "File '" + filePath + "' has " + count + " lines";
        } catch (IOException e) {
            return "Error reading file: " + e.getMessage();
        }
    }

    @Tool(description = "List all files and directories in a given directory")
    public String listDir(@ToolParam(description = "Full path to the directory") String dirPath) {
        if (!safety.isPathAllowed(dirPath)) return "Access denied: " + dirPath;
        try (var stream = Files.list(Path.of(dirPath))) {
            return stream.map(p -> (Files.isDirectory(p) ? "[DIR]  " : "[FILE] ") + p.getFileName())
                    .collect(Collectors.joining("\n"));
        } catch (IOException e) {
            return "Error listing directory: " + e.getMessage();
        }
    }

    @Tool(description = "Write text content to a file (creates or overwrites)")
    public String writeFile(
            @ToolParam(description = "Full path to the file") String filePath,
            @ToolParam(description = "Content to write") String content) {
        if (!safety.isPathAllowed(filePath)) return "Access denied: " + filePath;
        try {
            Files.writeString(Path.of(filePath), content,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            return "Successfully wrote to '" + filePath + "'";
        } catch (IOException e) {
            return "Error writing file: " + e.getMessage();
        }
    }

    @Tool(description = "Search for a keyword in a file and return matching lines")
    public String searchInFile(
            @ToolParam(description = "Full path to the file") String filePath,
            @ToolParam(description = "Keyword to search for") String keyword) {
        if (!safety.isPathAllowed(filePath)) return "Access denied: " + filePath;
        try {
            String results = Files.lines(Path.of(filePath))
                    .filter(line -> line.contains(keyword))
                    .collect(Collectors.joining("\n"));
            return results.isEmpty() ? "No matches found" : results;
        } catch (IOException e) {
            return "Error reading file: " + e.getMessage();
        }
    }
}
```

- [ ] **Step 4: Create InputTool**

Create `src/main/java/com/limou/agent_demo/tool/InputTool.java`:

```java
package com.limou.agent_demo.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.awt.*;
import java.awt.event.KeyEvent;

@Component
public class InputTool {

    @Tool(description = "Type text into the currently focused window using keyboard simulation")
    public String typeText(@ToolParam(description = "Text to type") String text) {
        try {
            Robot robot = new Robot();
            robot.setAutoDelay(50);
            for (char c : text.toCharArray()) {
                typeChar(robot, c);
            }
            return "Typed text successfully";
        } catch (AWTException e) {
            return "Failed to type text: " + e.getMessage();
        }
    }

    @Tool(description = "Press a keyboard shortcut, e.g. ctrl+s, alt+tab, ctrl+c")
    public String pressKeys(@ToolParam(description = "Key combination like ctrl+s or alt+tab") String keyCombo) {
        try {
            Robot robot = new Robot();
            robot.setAutoDelay(50);
            String[] parts = keyCombo.toLowerCase().split("\\+");
            int[] keyCodes = new int[parts.length];
            for (int i = 0; i < parts.length; i++) {
                keyCodes[i] = getKeyCode(parts[i].trim());
            }
            for (int keyCode : keyCodes) {
                robot.keyPress(keyCode);
            }
            for (int i = keyCodes.length - 1; i >= 0; i--) {
                robot.keyRelease(keyCodes[i]);
            }
            return "Pressed: " + keyCombo;
        } catch (AWTException e) {
            return "Failed to press keys: " + e.getMessage();
        }
    }

    @Tool(description = "Switch to an application by name and type text into it. On Windows, uses Alt+Tab to switch.")
    public String typeToApp(
            @ToolParam(description = "Application name (partial match) to switch to") String appName,
            @ToolParam(description = "Text to type after switching") String text) {
        try {
            Robot robot = new Robot();
            robot.setAutoDelay(80);
            robot.keyPress(KeyEvent.VK_ALT);
            robot.keyPress(KeyEvent.VK_TAB);
            robot.keyRelease(KeyEvent.VK_TAB);
            robot.keyRelease(KeyEvent.VK_ALT);
            robot.delay(300);
            for (char c : text.toCharArray()) {
                typeChar(robot, c);
            }
            return "Switched to '" + appName + "' and typed text";
        } catch (AWTException e) {
            return "Failed: " + e.getMessage();
        }
    }

    private void typeChar(Robot robot, char c) {
        switch (c) {
            case '\n' -> { robot.keyPress(KeyEvent.VK_ENTER); robot.keyRelease(KeyEvent.VK_ENTER); }
            case '\t' -> { robot.keyPress(KeyEvent.VK_TAB); robot.keyRelease(KeyEvent.VK_TAB); }
            default -> {
                boolean upper = Character.isUpperCase(c);
                if (upper) robot.keyPress(KeyEvent.VK_SHIFT);
                int code = KeyEvent.getExtendedKeyCodeForChar(c);
                if (code != KeyEvent.VK_UNDEFINED) {
                    robot.keyPress(code);
                    robot.keyRelease(code);
                }
                if (upper) robot.keyRelease(KeyEvent.VK_SHIFT);
            }
        }
    }

    private int getKeyCode(String key) {
        return switch (key) {
            case "ctrl", "control" -> KeyEvent.VK_CONTROL;
            case "alt" -> KeyEvent.VK_ALT;
            case "shift" -> KeyEvent.VK_SHIFT;
            case "tab" -> KeyEvent.VK_TAB;
            case "enter" -> KeyEvent.VK_ENTER;
            case "esc", "escape" -> KeyEvent.VK_ESCAPE;
            case "win", "windows" -> KeyEvent.VK_WINDOWS;
            default -> key.length() == 1 ? KeyEvent.getExtendedKeyCodeForChar(key.charAt(0)) : KeyEvent.VK_UNDEFINED;
        };
    }
}
```

- [ ] **Step 5: Verify build**

Run: `mvn compile -f "D:\idea-study\Agent_Demo\pom.xml"`

Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/limou/agent_demo/tool/
git commit -m "feat: add ProcessTool, FileTool, InputTool with safety checks"
```

---

### Task 6: Agent Service

**Files:**
- Create: `D:\idea-study\Agent_Demo\src\main\java\com\limou\agent_demo\service\AgentService.java`

**Interfaces:**
- Consumes:
  - `ChatClient` (from Task 4 AiConfig)
  - `ChatMemory` (from Task 4 AiConfig)
  - `ConversationMapper`, `MessageMapper` (from Task 2)
  - All `@Tool` beans (from Task 5) — auto-discovered by Spring AI
  - `ChatRequest` (from Task 3)
- Produces: `Flux<ChatEvent>` (SSE stream of chat events)

- [ ] **Step 1: Create AgentService**

Create `src/main/java/com/limou/agent_demo/service/AgentService.java`:

```java
package com.limou.agent_demo.service;

import com.limou.agent_demo.dto.ChatEvent;
import com.limou.agent_demo.dto.ChatRequest;
import com.limou.agent_demo.entity.Conversation;
import com.limou.agent_demo.entity.Message;
import com.limou.agent_demo.mapper.ConversationMapper;
import com.limou.agent_demo.mapper.MessageMapper;
import com.limou.agent_demo.tool.ToolSafety;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.MessageChatMemoryAdvisor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.UUID;

@Service
public class AgentService {

    private final ChatClient chatClient;
    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;
    private final ToolSafety toolSafety;

    public AgentService(ChatClient chatClient,
                        ConversationMapper conversationMapper,
                        MessageMapper messageMapper, ToolSafety toolSafety) {
        this.chatClient = chatClient;
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
        this.toolSafety = toolSafety;
    }

    public Flux<ChatEvent> streamChat(ChatRequest request) {
        return Flux.create(sink -> {
            try {
                String conversationId = getOrCreateConversationId(request);
                String userMsgId = UUID.randomUUID().toString();

                sink.next(ChatEvent.thinking());

                StringBuilder fullResponse = new StringBuilder();

                chatClient.prompt()
                        .user(request.getMessage())
                        .advisors(a -> a.param(
                                MessageChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID,
                                conversationId))
                        .stream()
                        .content()
                        .doOnNext(chunk -> {
                            fullResponse.append(chunk);
                            sink.next(ChatEvent.message(chunk));
                        })
                        .doOnComplete(() -> {
                            persistMessages(request.getMessage(), userMsgId,
                                    fullResponse.toString(), conversationId);
                            sink.next(ChatEvent.done(conversationId, userMsgId));
                            sink.complete();
                        })
                        .doOnError(error -> {
                            sink.next(ChatEvent.error("Chat error: " + error.getMessage()));
                            sink.complete();
                        })
                        .subscribe();
            } catch (Exception e) {
                sink.next(ChatEvent.error("System error: " + e.getMessage()));
                sink.complete();
            }
        });
    }

    private String getOrCreateConversationId(ChatRequest request) {
        if (request.getConversationId() != null && !request.getConversationId().isEmpty()) {
            Conversation existing = conversationMapper.selectById(request.getConversationId());
            if (existing != null) return request.getConversationId();
        }
        String newId = UUID.randomUUID().toString();
        Conversation conv = new Conversation();
        conv.setId(newId);
        conv.setTitle(truncate(request.getMessage(), 100));
        conv.setModel("deepseek-chat");
        conversationMapper.insert(conv);
        return newId;
    }

    private void persistMessages(String userText, String userMsgId,
                                  String assistantText, String conversationId) {
        Message userMsg = new Message();
        userMsg.setId(userMsgId);
        userMsg.setConversationId(conversationId);
        userMsg.setRole("user");
        userMsg.setContent(userText);
        messageMapper.insert(userMsg);

        String assistantMsgId = UUID.randomUUID().toString();
        Message assistantMsg = new Message();
        assistantMsg.setId(assistantMsgId);
        assistantMsg.setConversationId(conversationId);
        assistantMsg.setRole("assistant");
        assistantMsg.setContent(assistantText);
        messageMapper.insert(assistantMsg);
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "New Chat";
        return s.length() <= maxLen ? s : s.substring(0, maxLen);
    }
}
```

- [ ] **Step 2: Verify build**

Run: `mvn compile -f "D:\idea-study\Agent_Demo\pom.xml"`

Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/limou/agent_demo/service/
git commit -m "feat: add AgentService with SSE streaming and conversation persistence"
```

---

### Task 7: Chat Controller

**Files:**
- Create: `D:\idea-study\Agent_Demo\src\main\java\com\limou\agent_demo\controller\ChatController.java`

**Interfaces:**
- Consumes: `AgentService` (from Task 6), `ChatRequest` (from Task 3)
- Produces: `POST /api/chat/stream` (SSE), `POST /api/chat` (non-streaming JSON)

- [ ] **Step 1: Create ChatController**

Create `src/main/java/com/limou/agent_demo/controller/ChatController.java`:

```java
package com.limou.agent_demo.controller;

import com.limou.agent_demo.dto.ChatEvent;
import com.limou.agent_demo.dto.ChatRequest;
import com.limou.agent_demo.service.AgentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/chat")
@Tag(name = "Chat", description = "AI Agent chat with tool calling")
public class ChatController {

    private final AgentService agentService;

    public ChatController(AgentService agentService) {
        this.agentService = agentService;
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Stream chat with Agent (SSE)", description = "Agent can call tools automatically. Returns SSE events: thinking, message, tool_call, tool_result, done, error.")
    public Flux<ChatEvent> streamChat(@RequestBody ChatRequest request) {
        return agentService.streamChat(request);
    }

    @PostMapping
    @Operation(summary = "Non-streaming chat", description = "Returns full response at once, no SSE")
    public Flux<ChatEvent> chat(@RequestBody ChatRequest request) {
        return agentService.streamChat(request);
    }
}
```

- [ ] **Step 2: Verify build**

Run: `mvn compile -f "D:\idea-study\Agent_Demo\pom.xml"`

Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/limou/agent_demo/controller/ChatController.java
git commit -m "feat: add ChatController with SSE and non-streaming endpoints"
```

---

### Task 8: Conversation Controller

**Files:**
- Create: `D:\idea-study\Agent_Demo\src\main\java\com\limou\agent_demo\controller\ConversationController.java`

**Interfaces:**
- Consumes: `ConversationMapper`, `MessageMapper` (from Task 2)
- Produces: CRUD REST endpoints for conversations

- [ ] **Step 1: Create ConversationController**

Create `src/main/java/com/limou/agent_demo/controller/ConversationController.java`:

```java
package com.limou.agent_demo.controller;

import com.limou.agent_demo.dto.ConversationVO;
import com.limou.agent_demo.entity.Conversation;
import com.limou.agent_demo.entity.Message;
import com.limou.agent_demo.mapper.ConversationMapper;
import com.limou.agent_demo.mapper.MessageMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/conversations")
@Tag(name = "Conversations", description = "Conversation CRUD")
public class ConversationController {

    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;

    public ConversationController(ConversationMapper conversationMapper,
                                   MessageMapper messageMapper) {
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
    }

    @GetMapping
    @Operation(summary = "List conversations (paginated)")
    public List<ConversationVO> list(
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "20") int limit) {
        return conversationMapper.selectAll(offset, limit).stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    @PostMapping
    @Operation(summary = "Create a new empty conversation")
    public Map<String, String> create(@RequestBody Map<String, String> body) {
        String id = UUID.randomUUID().toString();
        Conversation conv = new Conversation();
        conv.setId(id);
        conv.setTitle(body.getOrDefault("title", "New Chat"));
        conv.setModel(body.getOrDefault("model", "deepseek-chat"));
        conversationMapper.insert(conv);
        return Map.of("id", id);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get conversation detail with message history")
    public ConversationVO detail(@PathVariable String id) {
        Conversation conv = conversationMapper.selectById(id);
        if (conv == null) throw new RuntimeException("Conversation not found: " + id);
        return toVO(conv);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a conversation and all its messages")
    public Map<String, String> delete(@PathVariable String id) {
        conversationMapper.deleteById(id);
        return Map.of("status", "deleted");
    }

    private ConversationVO toVO(Conversation conv) {
        List<Message> messages = messageMapper.selectByConversationId(conv.getId());
        String firstMessage = messages.stream()
                .filter(m -> "user".equals(m.getRole()))
                .findFirst()
                .map(Message::getContent)
                .orElse("");
        return ConversationVO.builder()
                .id(conv.getId())
                .title(conv.getTitle())
                .model(conv.getModel())
                .createdAt(conv.getCreatedAt())
                .updatedAt(conv.getUpdatedAt())
                .messageCount(messages.size())
                .firstMessage(firstMessage)
                .build();
    }
}
```

- [ ] **Step 2: Verify build**

Run: `mvn compile -f "D:\idea-study\Agent_Demo\pom.xml"`

Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/limou/agent_demo/controller/ConversationController.java
git commit -m "feat: add ConversationController with CRUD endpoints"
```

---

### Task 9: Tool Controller

**Files:**
- Create: `D:\idea-study\Agent_Demo\src\main\java\com\limou\agent_demo\controller\ToolController.java`

**Interfaces:**
- Consumes: Spring context tool beans (auto-discovered)
- Produces: `GET /api/tools` listing all registered tools

- [ ] **Step 1: Create ToolController**

Create `src/main/java/com/limou/agent_demo/controller/ToolController.java`:

```java
package com.limou.agent_demo.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/tools")
@Tag(name = "Tools", description = "Available Agent tools")
public class ToolController {

    private final ToolCallbackProvider toolCallbackProvider;

    public ToolController(ToolCallbackProvider toolCallbackProvider) {
        this.toolCallbackProvider = toolCallbackProvider;
    }

    @GetMapping
    @Operation(summary = "List all available tools the Agent can use")
    public List<Map<String, Object>> listTools() {
        ToolCallback[] callbacks = toolCallbackProvider.getToolCallbacks();
        return java.util.Arrays.stream(callbacks)
                .map(tc -> Map.<String, Object>of(
                        "name", tc.getToolDefinition().name(),
                        "description", tc.getToolDefinition().description()
                ))
                .collect(Collectors.toList());
    }
}
```

- [ ] **Step 2: Verify build**

Run: `mvn compile -f "D:\idea-study\Agent_Demo\pom.xml"`

Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/limou/agent_demo/controller/ToolController.java
git commit -m "feat: add ToolController to list available Agent tools"
```

---

### Task 10: Integration Build & Verify

**Files:**
- None (verification only)

**Interfaces:**
- Verifies: Full Spring context starts, all beans wired, API endpoints accessible

- [ ] **Step 1: Full compile**

Run: `mvn clean compile -f "D:\idea-study\Agent_Demo\pom.xml"`

Expected: BUILD SUCCESS

- [ ] **Step 2: Start application**

Run: `mvn spring-boot:run -f "D:\idea-study\Agent_Demo\pom.xml"`

Wait for: "Started AgentDemoApplication"

- [ ] **Step 3: Verify health endpoint**

Run: `curl http://localhost:8123/api/health`

Expected: `ok`

- [ ] **Step 4: Verify Knife4j UI**

Open browser at: `http://localhost:8123/api/doc.html`

Expected: Knife4j UI loads with all controller groups visible

- [ ] **Step 5: Verify tool list endpoint**

Run: `curl http://localhost:8123/api/tools`

Expected: JSON array of tools with name and description

- [ ] **Step 6: Verify conversation CRUD**

Run:
```
curl -X POST http://localhost:8123/api/conversations -H "Content-Type: application/json" -d "{\"title\":\"test\"}"
curl http://localhost:8123/api/conversations
```

Expected: Create returns `{id: "uuid..."}`, List returns array containing the new conversation

- [ ] **Step 7: Verify chat (with valid API key)**

Run:
```
curl -X POST http://localhost:8123/api/chat/stream \
  -H "Content-Type: application/json" \
  -d "{\"message\":\"hello, count lines in pom.xml\", \"confirm\":true}"
```

Expected: SSE stream with thinking → message chunks → done events

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "feat: complete Desktop Agent implementation - all endpoints verified"
```
