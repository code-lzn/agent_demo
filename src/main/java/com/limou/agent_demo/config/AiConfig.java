package com.limou.agent_demo.config;

import com.limou.agent_demo.tool.*;
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
    public ChatClient chatClient(ChatClient.Builder builder, ChatMemory chatMemory,ToolCallbackProvider toolCallbackProvider) {
        return builder
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .defaultTools(toolCallbackProvider)
                .build();
    }

    @Bean
    public ToolCallbackProvider toolCallbackProvider(
            ProcessTool processTool, FileTool fileTool, InputTool inputTool,
            WindowTool windowTool, ClipboardTool clipboardTool, SystemTool systemTool,
            WebTool webTool, NotificationTool notificationTool,
            MouseTool mouseTool, AudioTool audioTool,
            DatabaseTool databaseTool, CronTool cronTool,
            LogMonitorTool logMonitorTool, ImageTool imageTool,
            ArchiveTool archiveTool, DownloadTool downloadTool,
            RegistryTool registryTool, PowerTool powerTool,
            EmailTool emailTool,
            GrepTool grepTool, EditTool editTool, GitTool gitTool) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(processTool, fileTool, inputTool,
                        windowTool, clipboardTool, systemTool,
                        webTool, notificationTool,
                        mouseTool, audioTool,
                        databaseTool, cronTool,
                        logMonitorTool, imageTool,
                        archiveTool, downloadTool,
                        registryTool, powerTool,
                        emailTool,
                        grepTool, editTool, gitTool)
                .build();
    }
}
