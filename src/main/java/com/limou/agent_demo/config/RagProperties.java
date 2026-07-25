package com.limou.agent_demo.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "agent.rag")
public class RagProperties {

    private boolean enabled = true;

    private String docsPath = "D:/agent/docs";

    private String manifestPath = "D:/agent/docs/rag-manifest.json";

    private int chunkSize = 900;

    private int chunkOverlap = 120;

    private int topK = 5;

    private double minScore = 0.55;

    private boolean rebuildOnStartup = false;

    private boolean autoIndexOnStartup = true;

    private int batchSize = 50;

    private List<String> extensions = List.of(".md", ".txt");
}
