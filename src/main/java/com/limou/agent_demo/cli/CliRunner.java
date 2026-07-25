package com.limou.agent_demo.cli;

import com.limou.agent_demo.dto.ChatRequest;
import com.limou.agent_demo.service.AgentService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@Component
public class CliRunner implements ApplicationRunner {

    private final AgentService agentService;

    public CliRunner(AgentService agentService) {
        this.agentService = agentService;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!args.containsOption("cli")) return;

        System.out.println();
        System.out.println("╔══════════════════════════════════════╗");
        System.out.println("║       Desktop Agent CLI              ║");
        System.out.println("║   输入消息，输入 exit 退出           ║");
        System.out.println("╚══════════════════════════════════════╝");
        System.out.println();

        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        final String[] conversationId = { null };

        while (true) {
            System.out.print("> ");
            String input = reader.readLine();
            if (input == null || input.equalsIgnoreCase("exit") || input.equalsIgnoreCase("quit")) {
                System.out.println("再见！");
                break;
            }
            if (input.isBlank()) continue;

            ChatRequest request = new ChatRequest();
            request.setMessage(input);
            request.setConfirm(true);
            request.setConversationId(conversationId[0]);

            CountDownLatch latch = new CountDownLatch(1);

            agentService.streamChat(request)
                    .doOnNext(event -> {
                        switch (event.getType()) {
                            case "thinking" -> System.out.print("[思考中...]");
                            case "tool_call" -> {
                                @SuppressWarnings("unchecked")
                                var data = (Map<String, Object>) event.getData();
                                System.out.println("\n[工具: " + data.get("tool") + "]");
                            }
                            case "tool_result" -> System.out.println("[工具完成]");
                            case "message" -> System.out.print(event.getData().toString());
                            case "done" -> {
                                @SuppressWarnings("unchecked")
                                var data = (Map<String, Object>) event.getData();
                                conversationId[0] = data.get("conversationId").toString();
                                System.out.println();
                                latch.countDown();
                            }
                            case "error" -> {
                                @SuppressWarnings("unchecked")
                                var data = (Map<String, Object>) event.getData();
                                System.err.println("\n[错误] " + data.get("message"));
                                latch.countDown();
                            }
                        }
                    })
                    .doOnError(err -> {
                        System.err.println("\n[异常] " + err.getMessage());
                        latch.countDown();
                    })
                    .subscribe();

            latch.await(300, TimeUnit.SECONDS);
            System.out.println();
        }

        System.exit(0);
    }
}
