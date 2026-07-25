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
