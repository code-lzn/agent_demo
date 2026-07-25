package com.limou.agent_demo.service;

import com.limou.agent_demo.decision.DecisionEngine;
import com.limou.agent_demo.mapper.ConversationMapper;
import com.limou.agent_demo.mapper.MessageMapper;
import com.limou.agent_demo.tool.ToolCallCapture;
import com.limou.agent_demo.tool.ToolContext;
import com.limou.agent_demo.entity.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.memory.ChatMemory;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentServiceTest {

    @Mock private DecisionEngine decisionEngine;
    @Mock private ConversationMapper conversationMapper;
    @Mock private MessageMapper messageMapper;
    @Mock private LocalRagService localRagService;
    @Mock private ToolContext toolContext;
    @Mock private ToolCallCapture toolCallCapture;
    @Mock private ChatMemory chatMemory;

    private AgentService agentService;

    @BeforeEach
    void setUp() {
        agentService = new AgentService(
                decisionEngine, conversationMapper, messageMapper,
                localRagService, toolContext, toolCallCapture, chatMemory);
    }

    @Test
    void shouldNotPrimeWhenChatMemoryAlreadyHasMessages() {
        String conversationId = "conv-1";
        List<org.springframework.ai.chat.messages.Message> existing = List.of(
                new org.springframework.ai.chat.messages.UserMessage("Hello"));
        when(chatMemory.get(conversationId)).thenReturn(existing);

        try {
            var method = AgentService.class.getDeclaredMethod("primeChatMemory", String.class);
            method.setAccessible(true);
            method.invoke(agentService, conversationId);
        } catch (Exception e) {
            fail("Failed to invoke primeChatMemory: " + e.getMessage());
        }

        verify(chatMemory).get(conversationId);
        verifyNoInteractions(messageMapper);
        verify(chatMemory, never()).add(anyString(), anyList());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldPrimeFromDbWhenChatMemoryIsEmpty() {
        String conversationId = "conv-2";

        // ChatMemory is empty
        when(chatMemory.get(conversationId)).thenReturn(null);

        // DB has 2 messages: user:"Hi" then assistant:"Hello there"
        Message dbMsg1 = new Message();
        dbMsg1.setRole("user");
        dbMsg1.setContent("Hi");
        Message dbMsg2 = new Message();
        dbMsg2.setRole("assistant");
        dbMsg2.setContent("Hello there");
        when(messageMapper.selectRecentByConversationId(conversationId, 20))
                .thenReturn(List.of(dbMsg2, dbMsg1)); // DESC order

        ArgumentCaptor<List<org.springframework.ai.chat.messages.Message>> captor =
                ArgumentCaptor.forClass(List.class);

        // trigger prime by calling it — need to use reflection since it's private
        try {
            var method = AgentService.class.getDeclaredMethod("primeChatMemory", String.class);
            method.setAccessible(true);
            method.invoke(agentService, conversationId);
        } catch (Exception e) {
            fail("Failed to invoke primeChatMemory: " + e.getMessage());
        }

        // Verify DB was queried
        verify(messageMapper).selectRecentByConversationId(conversationId, 20);

        // Verify ChatMemory.add was called with chronologically ordered messages
        verify(chatMemory).add(eq(conversationId), captor.capture());
        List<org.springframework.ai.chat.messages.Message> added = captor.getValue();

        assertEquals(2, added.size());
        assertInstanceOf(org.springframework.ai.chat.messages.UserMessage.class, added.get(0));
        assertEquals("Hi", ((org.springframework.ai.chat.messages.UserMessage) added.get(0)).getText());
        assertInstanceOf(org.springframework.ai.chat.messages.AssistantMessage.class, added.get(1));
        assertEquals("Hello there", ((org.springframework.ai.chat.messages.AssistantMessage) added.get(1)).getText());
    }

    @Test
    void shouldHandleConversationWithNoHistory() {
        String conversationId = "conv-new";

        when(chatMemory.get(conversationId)).thenReturn(null);
        when(messageMapper.selectRecentByConversationId(conversationId, 20))
                .thenReturn(List.of()); // empty DB

        try {
            var method = AgentService.class.getDeclaredMethod("primeChatMemory", String.class);
            method.setAccessible(true);
            method.invoke(agentService, conversationId);
        } catch (Exception e) {
            fail("Failed to invoke primeChatMemory: " + e.getMessage());
        }

        // DB queried, but no messages to add
        verify(messageMapper).selectRecentByConversationId(conversationId, 20);
        verify(chatMemory, never()).add(anyString(), anyList());
    }

    @Test
    void shouldReverseDbOrderToChronological() {
        String conversationId = "conv-5";

        when(chatMemory.get(conversationId)).thenReturn(null);

        // DB returns DESC: newest first (msg3, msg2, msg1)
        List<Message> dbMessages = new ArrayList<>();
        for (int i = 3; i >= 1; i--) {
            Message m = new Message();
            m.setRole("user");
            m.setContent("Message " + i);
            dbMessages.add(m);
        }
        when(messageMapper.selectRecentByConversationId(conversationId, 20))
                .thenReturn(dbMessages);

        try {
            var method = AgentService.class.getDeclaredMethod("primeChatMemory", String.class);
            method.setAccessible(true);
            method.invoke(agentService, conversationId);
        } catch (Exception e) {
            fail("Failed to invoke primeChatMemory: " + e.getMessage());
        }

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<org.springframework.ai.chat.messages.Message>> captor =
                ArgumentCaptor.forClass(List.class);
        verify(chatMemory).add(eq(conversationId), captor.capture());
        List<org.springframework.ai.chat.messages.Message> added = captor.getValue();

        assertEquals(3, added.size());
        // Should be in ASC order: msg1, msg2, msg3
        assertEquals("Message 1", ((org.springframework.ai.chat.messages.UserMessage) added.get(0)).getText());
        assertEquals("Message 2", ((org.springframework.ai.chat.messages.UserMessage) added.get(1)).getText());
        assertEquals("Message 3", ((org.springframework.ai.chat.messages.UserMessage) added.get(2)).getText());
    }
}
