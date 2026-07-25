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
