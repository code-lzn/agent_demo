package com.limou.agent_demo.dto;

import lombok.Data;

@Data
public class ChatRequest {
    private String conversationId;
    private String message;
    private boolean confirm;
}
