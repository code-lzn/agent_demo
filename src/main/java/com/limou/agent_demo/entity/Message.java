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
