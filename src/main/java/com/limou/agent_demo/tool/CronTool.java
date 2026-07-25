package com.limou.agent_demo.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

/**
 * Simple in-process task scheduler. Tasks are ephemeral — they survive only while the app is running.
 */
@Component
public class CronTool {

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final Map<String, ScheduledFuture<?>> tasks = new ConcurrentHashMap<>();

    @Tool(description = "Schedule a one-shot task to execute a command after a delay." +
            " For example, delaySeconds=600 to run a task in 10 minutes." +
            " The command runs via cmd.exe. Returns a task ID for cancellation")
    public String scheduleTask(
            @ToolParam(description = "Delay in seconds before execution") int delaySeconds,
            @ToolParam(description = "Command to execute (same rules as SystemTool.runCommand)") String command) {
        String taskId = UUID.randomUUID().toString().substring(0, 8);
        ScheduledFuture<?> future = scheduler.schedule(() -> {
            try {
                ProcessBuilder pb = new ProcessBuilder("cmd.exe", "/c", command);
                pb.redirectErrorStream(true);
                Process p = pb.start();
                String output = new String(p.getInputStream().readAllBytes());
                p.waitFor();
                // Task completes silently — use NotificationTool if you need alerts
            } catch (Exception ignored) {
            } finally {
                tasks.remove(taskId);
            }
        }, delaySeconds, TimeUnit.SECONDS);
        tasks.put(taskId, future);
        return "Scheduled task '" + taskId + "' to run '" + command + "' in " + delaySeconds + " seconds";
    }

    @Tool(description = "Schedule a recurring task to execute a command at fixed intervals")
    public String scheduleRecurring(
            @ToolParam(description = "Interval in seconds between executions") int intervalSeconds,
            @ToolParam(description = "Command to execute") String command) {
        String taskId = UUID.randomUUID().toString().substring(0, 8);
        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(() -> {
            try {
                ProcessBuilder pb = new ProcessBuilder("cmd.exe", "/c", command);
                pb.redirectErrorStream(true);
                Process p = pb.start();
                p.waitFor();
            } catch (Exception ignored) {
            }
        }, intervalSeconds, intervalSeconds, TimeUnit.SECONDS);
        tasks.put(taskId, future);
        return "Scheduled recurring task '" + taskId + "' to run '" + command + "' every " + intervalSeconds + " seconds";
    }

    @Tool(description = "List all currently active scheduled tasks")
    public String listScheduledTasks() {
        if (tasks.isEmpty()) return "No active scheduled tasks";
        StringBuilder sb = new StringBuilder();
        tasks.forEach((id, future) -> {
            sb.append(id).append(" | done=").append(future.isDone())
                    .append(" | cancelled=").append(future.isCancelled()).append("\n");
        });
        return sb.toString().stripTrailing();
    }

    @Tool(description = "Cancel a scheduled task by its ID (returned by scheduleTask or scheduleRecurring)")
    public String cancelTask(@ToolParam(description = "Task ID to cancel") String taskId) {
        ScheduledFuture<?> future = tasks.get(taskId);
        if (future == null) return "Task not found: " + taskId;
        future.cancel(false);
        tasks.remove(taskId);
        return "Cancelled task: " + taskId;
    }
}