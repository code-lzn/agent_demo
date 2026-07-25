package com.limou.agent_demo.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.awt.*;
import java.awt.event.KeyEvent;

@Component
public class InputTool {

    @Tool(description = "Type text into the currently focused window using keyboard simulation")
    public String typeText(@ToolParam(description = "Text to type") String text) {
        try {
            Robot robot = new Robot();
            robot.setAutoDelay(50);
            for (char c : text.toCharArray()) {
                typeChar(robot, c);
            }
            return "Typed text successfully";
        } catch (AWTException e) {
            return "Failed to type text: " + e.getMessage();
        }
    }

    @Tool(description = "Press a keyboard shortcut, e.g. ctrl+s, alt+tab, ctrl+c")
    public String pressKeys(@ToolParam(description = "Key combination like ctrl+s or alt+tab") String keyCombo) {
        try {
            Robot robot = new Robot();
            robot.setAutoDelay(50);
            String[] parts = keyCombo.toLowerCase().split("\\+");
            int[] keyCodes = new int[parts.length];
            for (int i = 0; i < parts.length; i++) {
                keyCodes[i] = getKeyCode(parts[i].trim());
            }
            for (int keyCode : keyCodes) {
                robot.keyPress(keyCode);
            }
            for (int i = keyCodes.length - 1; i >= 0; i--) {
                robot.keyRelease(keyCodes[i]);
            }
            return "Pressed: " + keyCombo;
        } catch (AWTException e) {
            return "Failed to press keys: " + e.getMessage();
        }
    }

    @Tool(description = "Switch to an application by name and type text into it. On Windows, uses Alt+Tab to switch.")
    public String typeToApp(
            @ToolParam(description = "Application name (partial match) to switch to") String appName,
            @ToolParam(description = "Text to type after switching") String text) {
        try {
            Robot robot = new Robot();
            robot.setAutoDelay(80);
            robot.keyPress(KeyEvent.VK_ALT);
            robot.keyPress(KeyEvent.VK_TAB);
            robot.keyRelease(KeyEvent.VK_TAB);
            robot.keyRelease(KeyEvent.VK_ALT);
            robot.delay(300);
            for (char c : text.toCharArray()) {
                typeChar(robot, c);
            }
            return "Switched to '" + appName + "' and typed text";
        } catch (AWTException e) {
            return "Failed: " + e.getMessage();
        }
    }

    private void typeChar(Robot robot, char c) {
        switch (c) {
            case '\n' -> { robot.keyPress(KeyEvent.VK_ENTER); robot.keyRelease(KeyEvent.VK_ENTER); }
            case '\t' -> { robot.keyPress(KeyEvent.VK_TAB); robot.keyRelease(KeyEvent.VK_TAB); }
            default -> {
                boolean upper = Character.isUpperCase(c);
                if (upper) robot.keyPress(KeyEvent.VK_SHIFT);
                int code = KeyEvent.getExtendedKeyCodeForChar(c);
                if (code != KeyEvent.VK_UNDEFINED) {
                    robot.keyPress(code);
                    robot.keyRelease(code);
                }
                if (upper) robot.keyRelease(KeyEvent.VK_SHIFT);
            }
        }
    }

    private int getKeyCode(String key) {
        return switch (key) {
            case "ctrl", "control" -> KeyEvent.VK_CONTROL;
            case "alt" -> KeyEvent.VK_ALT;
            case "shift" -> KeyEvent.VK_SHIFT;
            case "tab" -> KeyEvent.VK_TAB;
            case "enter" -> KeyEvent.VK_ENTER;
            case "esc", "escape" -> KeyEvent.VK_ESCAPE;
            case "win", "windows" -> KeyEvent.VK_WINDOWS;
            default -> key.length() == 1 ? KeyEvent.getExtendedKeyCodeForChar(key.charAt(0)) : KeyEvent.VK_UNDEFINED;
        };
    }
}
