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
