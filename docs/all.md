## SpringAI的框架的搭建

### 1.框架的搭建

```
@PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
@Operation(summary = "Stream chat with Agent (SSE)", description = "Agent can call tools automatically. Returns SSE events: thinking, message, tool_call, tool_result, done, error.")
public Flux<ChatEvent> streamChat(@RequestBody ChatRequest request) {
    return agentService.streamChat(request);
}
```

将工具加入到Agent里面



```
@Bean
public ChatClient chatClient(ChatClient.Builder builder, ChatMemory chatMemory, ToolCallbackProvider toolCallbackProvider) {
    return builder
            .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
            .defaultTools(toolCallbackProvider)
            .build();
}
```

### 2.嵌入到命令行

![image-20260725171501605](C:\Users\henan\AppData\Roaming\Typora\typora-user-images\image-20260725171501605.png)

基本已经可以