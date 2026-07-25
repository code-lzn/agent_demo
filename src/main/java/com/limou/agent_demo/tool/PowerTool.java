package com.limou.agent_demo.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * System power management tool: shutdown, restart, sleep, lock screen.
 */
@Component
public class PowerTool {

    @Tool(description = "Shut down the computer after the specified delay." +
            " Default delay is 60 seconds. Use delaySeconds=0 to shut down immediately")
    public String shutdown(@ToolParam(description = "Delay in seconds before shutdown (0 = immediate)") int delaySeconds) {
        try {
            new ProcessBuilder("shutdown", "/s", "/t", String.valueOf(delaySeconds)).start();
            return delaySeconds == 0
                    ? "Shutting down immediately"
                    : "Shutting down in " + delaySeconds + " seconds";
        } catch (Exception e) {
            return "Shutdown failed: " + e.getMessage();
        }
    }

    @Tool(description = "Restart the computer after the specified delay")
    public String restart(@ToolParam(description = "Delay in seconds before restart (0 = immediate)") int delaySeconds) {
        try {
            new ProcessBuilder("shutdown", "/r", "/t", String.valueOf(delaySeconds)).start();
            return delaySeconds == 0
                    ? "Restarting immediately"
                    : "Restarting in " + delaySeconds + " seconds";
        } catch (Exception e) {
            return "Restart failed: " + e.getMessage();
        }
    }

    @Tool(description = "Put the computer to sleep (standby)")
    public String sleep() {
        try {
            new ProcessBuilder("rundll32.exe", "powrprof.dll,SetSuspendState", "0", "1", "0").start();
            return "Going to sleep";
        } catch (Exception e) {
            return "Sleep failed: " + e.getMessage();
        }
    }

    @Tool(description = "Hibernate the computer")
    public String hibernate() {
        try {
            new ProcessBuilder("shutdown", "/h").start();
            return "Hibernating";
        } catch (Exception e) {
            return "Hibernate failed: " + e.getMessage();
        }
    }

    @Tool(description = "Lock the screen")
    public String lockScreen() {
        try {
            new ProcessBuilder("rundll32.exe", "user32.dll,LockWorkStation").start();
            return "Screen locked";
        } catch (Exception e) {
            return "Lock screen failed: " + e.getMessage();
        }
    }

    @Tool(description = "Cancel a pending shutdown or restart")
    public String cancelShutdown() {
        try {
            new ProcessBuilder("shutdown", "/a").start();
            return "Shutdown/restart cancelled";
        } catch (Exception e) {
            return "Cancel failed: " + e.getMessage();
        }
    }

    @Tool(description = "Log off the current user")
    public String logoff() {
        try {
            new ProcessBuilder("shutdown", "/l").start();
            return "Logging off";
        } catch (Exception e) {
            return "Logoff failed: " + e.getMessage();
        }
    }
}