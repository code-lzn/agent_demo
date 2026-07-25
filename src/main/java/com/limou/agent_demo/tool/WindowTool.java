package com.limou.agent_demo.tool;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinUser;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Windows window management tool using JNA to call Win32 API.
 */
@Component
public class WindowTool {

    private final ToolContext toolContext;

    private static final int SW_MINIMIZE = 6;
    private static final int SW_MAXIMIZE = 3;
    private static final int SW_RESTORE = 9;

    public WindowTool(ToolContext toolContext) {
        this.toolContext = toolContext;
    }

    // ==================== Query ====================

    @Tool(description = "List all visible windows with their titles and window handles (hwnd)")
    public String listWindows() {
        List<String> windows = new ArrayList<>();
        User32.INSTANCE.EnumWindows((hwnd, pointer) -> {
            if (User32.INSTANCE.IsWindowVisible(hwnd)) {
                char[] title = new char[512];
                User32.INSTANCE.GetWindowText(hwnd, title, 512);
                String titleStr = Native.toString(title);
                if (!titleStr.isEmpty()) {
                    windows.add(titleStr + " | hwnd=" + hwnd.getPointer().toString());
                }
            }
            return true;
        }, null);
        if (windows.isEmpty()) return "No visible windows found";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < windows.size(); i++) {
            sb.append(i + 1).append(". ").append(windows.get(i)).append("\n");
        }
        return sb.toString().stripTrailing();
    }

    @Tool(description = "Get the title of the currently active (foreground) window")
    public String getActiveWindow() {
        HWND hwnd = User32.INSTANCE.GetForegroundWindow();
        if (hwnd == null) return "Cannot get active window";
        char[] title = new char[512];
        User32.INSTANCE.GetWindowText(hwnd, title, 512);
        String titleStr = Native.toString(title);
        return titleStr.isEmpty()
                ? "Active window has no title (hwnd=" + hwnd.getPointer().toString() + ")"
                : "Active window: " + titleStr + " | hwnd=" + hwnd.getPointer().toString();
    }

    // ==================== Actions ====================

    @Tool(description = "Find a window by title (partial match, case-insensitive) and bring it to the foreground." +
            " If the window is minimized, it will be restored first")
    public String focusWindow(@ToolParam(description = "Window title (partial match supported)") String title) {
        if (!toolContext.isConfirmed()) return "Confirmation required: set confirm=true to perform this operation";
        HWND found = findWindowByTitle(title);
        if (found == null) return "No window found with title containing '" + title + "'";

        User32.INSTANCE.ShowWindow(found, SW_RESTORE);
        User32.INSTANCE.SetForegroundWindow(found);
        char[] foundTitle = new char[512];
        User32.INSTANCE.GetWindowText(found, foundTitle, 512);
        return "Focused window: " + Native.toString(foundTitle);
    }

    @Tool(description = "Close a window by title (partial match). Sends WM_CLOSE message to the window")
    public String closeWindow(@ToolParam(description = "Window title (partial match supported)") String title) {
        if (!toolContext.isConfirmed()) return "Confirmation required: set confirm=true to perform this operation";
        HWND found = findWindowByTitle(title);
        if (found == null) return "No window found with title containing '" + title + "'";
        char[] foundTitle = new char[512];
        User32.INSTANCE.GetWindowText(found, foundTitle, 512);
        User32.INSTANCE.PostMessage(found, WinUser.WM_CLOSE, null, null);
        return "Sent close command to window: " + Native.toString(foundTitle);
    }

    @Tool(description = "Minimize a window by title")
    public String minimizeWindow(@ToolParam(description = "Window title (partial match supported)") String title) {
        if (!toolContext.isConfirmed()) return "Confirmation required: set confirm=true to perform this operation";
        HWND found = findWindowByTitle(title);
        if (found == null) return "No window found with title containing '" + title + "'";
        User32.INSTANCE.ShowWindow(found, SW_MINIMIZE);
        return "Minimized window: " + title;
    }

    @Tool(description = "Maximize a window by title")
    public String maximizeWindow(@ToolParam(description = "Window title (partial match supported)") String title) {
        if (!toolContext.isConfirmed()) return "Confirmation required: set confirm=true to perform this operation";
        HWND found = findWindowByTitle(title);
        if (found == null) return "No window found with title containing '" + title + "'";
        User32.INSTANCE.ShowWindow(found, SW_MAXIMIZE);
        return "Maximized window: " + title;
    }

    @Tool(description = "Resize and reposition a window. x,y = top-left corner, width,height in pixels")
    public String resizeWindow(
            @ToolParam(description = "Window title (partial match supported)") String title,
            @ToolParam(description = "Top-left X coordinate") int x,
            @ToolParam(description = "Top-left Y coordinate") int y,
            @ToolParam(description = "Width in pixels") int width,
            @ToolParam(description = "Height in pixels") int height) {
        if (!toolContext.isConfirmed()) return "Confirmation required: set confirm=true to perform this operation";
        HWND found = findWindowByTitle(title);
        if (found == null) return "No window found with title containing '" + title + "'";
        User32.INSTANCE.SetWindowPos(found, null, x, y, width, height, 0);
        return "Resized window '" + title + "' to (" + x + "," + y + ") " + width + "x" + height;
    }

    // ==================== Helper ====================

    private HWND findWindowByTitle(String title) {
        final String search = title.toLowerCase();
        final HWND[] result = {null};
        User32.INSTANCE.EnumWindows((hwnd, pointer) -> {
            if (User32.INSTANCE.IsWindowVisible(hwnd)) {
                char[] text = new char[512];
                User32.INSTANCE.GetWindowText(hwnd, text, 512);
                if (Native.toString(text).toLowerCase().contains(search)) {
                    result[0] = hwnd;
                    return false;
                }
            }
            return true;
        }, null);
        return result[0];
    }
}
