package com.limou.agent_demo.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.awt.*;
import java.awt.event.InputEvent;

/**
 * Mouse control tool using java.awt.Robot.
 */
@Component
public class MouseTool {

    @Tool(description = "Get the current mouse cursor position in screen coordinates (x, y)")
    public String getMousePos() {
        PointerInfo info = MouseInfo.getPointerInfo();
        if (info == null) return "Cannot get mouse position";
        Point p = info.getLocation();
        return "Mouse position: (" + p.x + ", " + p.y + ")";
    }

    @Tool(description = "Move the mouse cursor to the specified screen coordinates")
    public String mouseMove(
            @ToolParam(description = "X coordinate (pixels from left)") int x,
            @ToolParam(description = "Y coordinate (pixels from top)") int y) {
        try {
            Robot robot = new Robot();
            robot.mouseMove(x, y);
            return "Mouse moved to (" + x + ", " + y + ")";
        } catch (AWTException e) {
            return "Mouse move failed: " + e.getMessage();
        }
    }

    @Tool(description = "Perform a left mouse click at the current position")
    public String mouseClick() {
        try {
            Robot robot = new Robot();
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.delay(50);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            return "Left clicked at current position";
        } catch (AWTException e) {
            return "Mouse click failed: " + e.getMessage();
        }
    }

    @Tool(description = "Perform a right mouse click at the current position")
    public String mouseRightClick() {
        try {
            Robot robot = new Robot();
            robot.mousePress(InputEvent.BUTTON3_DOWN_MASK);
            robot.delay(50);
            robot.mouseRelease(InputEvent.BUTTON3_DOWN_MASK);
            return "Right clicked at current position";
        } catch (AWTException e) {
            return "Right click failed: " + e.getMessage();
        }
    }

    @Tool(description = "Perform a double-click at the current position")
    public String mouseDoubleClick() {
        try {
            Robot robot = new Robot();
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            robot.delay(50);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            return "Double clicked at current position";
        } catch (AWTException e) {
            return "Double click failed: " + e.getMessage();
        }
    }

    @Tool(description = "Click at a specific screen position by moving the mouse there first")
    public String clickAt(
            @ToolParam(description = "X coordinate") int x,
            @ToolParam(description = "Y coordinate") int y) {
        String moveResult = mouseMove(x, y);
        robotDelay(100);
        String clickResult = mouseClick();
        return moveResult + "; " + clickResult;
    }

    @Tool(description = "Drag the mouse from (x1,y1) to (x2,y2), holding the left button")
    public String mouseDrag(
            @ToolParam(description = "Start X") int x1,
            @ToolParam(description = "Start Y") int y1,
            @ToolParam(description = "End X") int x2,
            @ToolParam(description = "End Y") int y2) {
        try {
            Robot robot = new Robot();
            robot.mouseMove(x1, y1);
            robot.delay(50);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.delay(30);
            // Move in steps for smooth drag
            int steps = 10;
            for (int i = 1; i <= steps; i++) {
                int cx = x1 + (x2 - x1) * i / steps;
                int cy = y1 + (y2 - y1) * i / steps;
                robot.mouseMove(cx, cy);
                robot.delay(10);
            }
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            return "Dragged from (" + x1 + "," + y1 + ") to (" + x2 + "," + y2 + ")";
        } catch (AWTException e) {
            return "Mouse drag failed: " + e.getMessage();
        }
    }

    @Tool(description = "Scroll the mouse wheel at the current position." +
            " Positive amount = scroll up, negative = scroll down. Amount 3 = ~one notch")
    public String mouseScroll(@ToolParam(description = "Scroll amount (positive=up, negative=down, 3=~one notch)") int amount) {
        try {
            Robot robot = new Robot();
            robot.mouseWheel(amount);
            return "Scrolled by " + amount;
        } catch (AWTException e) {
            return "Scroll failed: " + e.getMessage();
        }
    }

    private void robotDelay(int ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}