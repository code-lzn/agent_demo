package com.limou.agent_demo.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/tools")
@Tag(name = "Tools", description = "Available Agent tools")
public class ToolController {

    private final ToolCallbackProvider toolCallbackProvider;

    public ToolController(ToolCallbackProvider toolCallbackProvider) {
        this.toolCallbackProvider = toolCallbackProvider;
    }

    @GetMapping
    @Operation(summary = "List all available tools the Agent can use")
    public List<Map<String, Object>> listTools() {
        ToolCallback[] callbacks = toolCallbackProvider.getToolCallbacks();
        return java.util.Arrays.stream(callbacks)
                .map(tc -> Map.<String, Object>of(
                        "name", tc.getToolDefinition().name(),
                        "description", tc.getToolDefinition().description()
                ))
                .collect(Collectors.toList());
    }
}
