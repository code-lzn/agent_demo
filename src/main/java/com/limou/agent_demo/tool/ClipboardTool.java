package com.limou.agent_demo.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.KeyEvent;
import java.io.IOException;

/**
 * 系统剪贴板工具，支持读写剪贴板和粘贴操作。
 */
@Component
public class ClipboardTool {

    // ==================== 剪贴板读写 ====================

    @Tool(description = "读取系统剪贴板中的文本内容")
    public String getClipboard() {
        try {
            String text = (String) Toolkit.getDefaultToolkit()
                    .getSystemClipboard()
                    .getData(DataFlavor.stringFlavor);
            return text == null || text.isEmpty()
                    ? "剪贴板为空"
                    : text;
        } catch (UnsupportedFlavorException | IOException e) {
            return "读取剪贴板失败: " + e.getMessage();
        } catch (IllegalStateException e) {
            return "无法访问剪贴板，可能正在被其他程序占用";
        }
    }

    @Tool(description = "将文本写入系统剪贴板，之后可通过粘贴（Ctrl+V）粘贴到任意应用")
    public String setClipboard(@ToolParam(description = "要写入剪贴板的文本") String text) {
        try {
            StringSelection selection = new StringSelection(text);
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);
            String preview = text.length() > 100 ? text.substring(0, 100) + "..." : text;
            return "已写入剪贴板: " + preview;
        } catch (IllegalStateException e) {
            return "无法访问剪贴板: " + e.getMessage();
        }
    }

    // ==================== 粘贴 ====================

    @Tool(description = "在当前焦点窗口执行粘贴操作（模拟 Ctrl+V）。如果需要粘贴大量中文文本，先将内容 setClipboard 再用此方法粘贴，比逐字符打字快得多")
    public String paste() {
        try {
            Robot robot = new Robot();
            robot.setAutoDelay(30);
            robot.keyPress(KeyEvent.VK_CONTROL);
            robot.keyPress(KeyEvent.VK_V);
            robot.delay(50);
            robot.keyRelease(KeyEvent.VK_V);
            robot.keyRelease(KeyEvent.VK_CONTROL);
            return "已粘贴";
        } catch (AWTException e) {
            return "粘贴失败: " + e.getMessage();
        }
    }

    @Tool(description = "写入剪贴板并自动粘贴到当前窗口。等同于先 setClipboard 再 paste")
    public String typeViaClipboard(@ToolParam(description = "要粘贴的文本内容") String text) {
        String setResult = setClipboard(text);
        if (setResult.startsWith("无法")) return setResult;
        robotDelay(100); // 等剪贴板就绪
        return paste();
    }

    private void robotDelay(int ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}