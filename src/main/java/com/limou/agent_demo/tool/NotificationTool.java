package com.limou.agent_demo.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.awt.*;
import java.awt.TrayIcon.MessageType;

@Component
public class NotificationTool {

    private TrayIcon trayIcon;
    private boolean available;

    public NotificationTool() {
        try {
            if (SystemTray.isSupported()) {
                // 直接使用内存图片作为托盘图标，不依赖外部资源文件
                Image image = new java.awt.image.BufferedImage(16, 16,
                        java.awt.image.BufferedImage.TYPE_INT_ARGB);
                trayIcon = new TrayIcon(image, "Agent Demo");
                trayIcon.setImageAutoSize(true);
                SystemTray.getSystemTray().add(trayIcon);
                available = true;
            } else {
                available = false;
            }
        } catch (Exception e) {
            available = false;
        }
    }

    @Tool(description = "Pop up a Windows system tray notification bubble, so the user knows a task is complete")
    public String notify(
            @ToolParam(description = "Notification title") String title,
            @ToolParam(description = "Notification body text") String message) {
        if (!available) {
            return "System tray not available on this machine";
        }
        try {
            trayIcon.displayMessage(title, message, MessageType.INFO);
            return "Notification sent: " + title;
        } catch (Exception e) {
            return "Failed to send notification: " + e.getMessage();
        }
    }
}
