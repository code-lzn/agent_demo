package com.limou.agent_demo.service;

import com.limou.agent_demo.dto.ChatEvent;
import com.limou.agent_demo.dto.ChatRequest;
import com.limou.agent_demo.entity.Conversation;
import com.limou.agent_demo.entity.Message;
import com.limou.agent_demo.mapper.ConversationMapper;
import com.limou.agent_demo.mapper.MessageMapper;
import com.limou.agent_demo.tool.ToolSafety;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.UUID;

@Service
public class AgentService {

    private static final String CHAT_MEMORY_CONVERSATION_ID = "chat_memory_conversation_id";

    private final ChatClient chatClient;
    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;
    private final ToolSafety toolSafety;

    public AgentService(ChatClient chatClient,
                        ConversationMapper conversationMapper,
                        MessageMapper messageMapper,
                        ToolSafety toolSafety) {
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
                        .system("你是 Windows 桌面 AI 助手，可以直接控制这台电脑。用户要你操作电脑时，立即调用对应的工具函数完成任务，严禁说你做不到或编造伪代码。每次操作前用一句话说明，操作后用中文汇报结果。")
                        .user(request.getMessage())
                        .advisors(a -> a.param(
                                CHAT_MEMORY_CONVERSATION_ID,
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
