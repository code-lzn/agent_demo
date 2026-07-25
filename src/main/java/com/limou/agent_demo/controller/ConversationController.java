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
