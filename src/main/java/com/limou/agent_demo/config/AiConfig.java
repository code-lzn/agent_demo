package com.limou.agent_demo.config;

import com.limou.agent_demo.tool.ClipboardTool;
import com.limou.agent_demo.tool.FileTool;
import com.limou.agent_demo.tool.InputTool;
import com.limou.agent_demo.tool.ProcessTool;
import com.limou.agent_demo.tool.SystemTool;
import com.limou.agent_demo.tool.WindowTool;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {

    @Bean
    public ChatMemory chatMemory() {
        return MessageWindowChatMemory.builder()
                .maxMessages(20)
                .build();
    }

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder, ChatMemory chatMemory) {
        return builder
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }

    @Bean
    public ToolCallbackProvider toolCallbackProvider(
            ProcessTool processTool, FileTool fileTool, InputTool inputTool,
            WindowTool windowTool, ClipboardTool clipboardTool, SystemTool systemTool) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(processTool, fileTool, inputTool,
                        windowTool, clipboardTool, systemTool)
                .build();
    }
}
