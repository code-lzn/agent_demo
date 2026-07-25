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
 * System clipboard tool for reading, writing, and pasting text.
 */
@Component
public class ClipboardTool {

    // ==================== Clipboard Read/Write ====================

    @Tool(description = "Read text content from the system clipboard")
    public String getClipboard() {
        try {
            String text = (String) Toolkit.getDefaultToolkit()
                    .getSystemClipboard()
                    .getData(DataFlavor.stringFlavor);
            return text == null || text.isEmpty()
                    ? "Clipboard is empty"
                    : text;
        } catch (UnsupportedFlavorException | IOException e) {
            return "Failed to read clipboard: " + e.getMessage();
        } catch (IllegalStateException e) {
            return "Cannot access clipboard, may be locked by another program";
        }
    }

    @Tool(description = "Write text to the system clipboard, so it can be pasted with Ctrl+V into any application")
    public String setClipboard(@ToolParam(description = "Text to write to the clipboard") String text) {
        try {
            StringSelection selection = new StringSelection(text);
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);
            String preview = text.length() > 100 ? text.substring(0, 100) + "..." : text;
            return "Copied to clipboard: " + preview;
        } catch (IllegalStateException e) {
            return "Cannot access clipboard: " + e.getMessage();
        }
    }

    // ==================== Paste ====================

    @Tool(description = "Paste clipboard content at the current cursor position by simulating Ctrl+V." +
            " Use this together with setClipboard for efficient text input — much faster than typing character by character")
    public String paste() {
        try {
            Robot robot = new Robot();
            robot.setAutoDelay(30);
            robot.keyPress(KeyEvent.VK_CONTROL);
            robot.keyPress(KeyEvent.VK_V);
            robot.delay(50);
            robot.keyRelease(KeyEvent.VK_V);
            robot.keyRelease(KeyEvent.VK_CONTROL);
            return "Pasted successfully";
        } catch (AWTException e) {
            return "Paste failed: " + e.getMessage();
        }
    }

    @Tool(description = "Copy text to clipboard and paste it into the current window in one step." +
            " Equivalent to setClipboard followed by paste. Ideal for large blocks of text")
    public String typeViaClipboard(@ToolParam(description = "Text to paste") String text) {
        String setResult = setClipboard(text);
        if (setResult.startsWith("Cannot")) return setResult;
        robotDelay(100);
        return paste();
    }

    private void robotDelay(int ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
