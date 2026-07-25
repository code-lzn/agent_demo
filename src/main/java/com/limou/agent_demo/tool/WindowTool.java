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
 * Windows 窗口管理工具，通过 JNA 调用 Win32 API。
 */
@Component
public class WindowTool {

    private static final int SW_MINIMIZE = 6;
    private static final int SW_MAXIMIZE = 3;
    private static final int SW_RESTORE = 9;

    // ==================== 查询 ====================

    @Tool(description = "列出当前所有可见窗口，返回窗口标题和句柄")
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
        if (windows.isEmpty()) return "未发现可见窗口";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < windows.size(); i++) {
            sb.append(i + 1).append(". ").append(windows.get(i)).append("\n");
        }
        return sb.toString().stripTrailing();
    }

    @Tool(description = "获取当前活动（前台）窗口的标题")
    public String getActiveWindow() {
        HWND hwnd = User32.INSTANCE.GetForegroundWindow();
        if (hwnd == null) return "无法获取当前活动窗口";
        char[] title = new char[512];
        User32.INSTANCE.GetWindowText(hwnd, title, 512);
        String titleStr = Native.toString(title);
        return titleStr.isEmpty()
                ? "当前活动窗口没有标题（hwnd=" + hwnd.getPointer().toString() + "）"
                : "当前活动窗口: " + titleStr + " | hwnd=" + hwnd.getPointer().toString();
    }

    // ==================== 操作 ====================

    @Tool(description = "按标题查找并聚焦（激活）窗口。支持部分匹配，不区分大小写")
    public String focusWindow(@ToolParam(description = "窗口标题（支持部分匹配）") String title) {
        HWND found = findWindowByTitle(title);
        if (found == null) return "未找到标题包含 '" + title + "' 的窗口";

        // 先还原窗口（对已最小化的窗口生效，对正常窗口无害）
        User32.INSTANCE.ShowWindow(found, SW_RESTORE);
        User32.INSTANCE.SetForegroundWindow(found);
        char[] foundTitle = new char[512];
        User32.INSTANCE.GetWindowText(found, foundTitle, 512);
        return "已聚焦窗口: " + Native.toString(foundTitle);
    }

    @Tool(description = "关闭指定窗口（按标题匹配）")
    public String closeWindow(@ToolParam(description = "窗口标题（支持部分匹配）") String title) {
        HWND found = findWindowByTitle(title);
        if (found == null) return "未找到标题包含 '" + title + "' 的窗口";
        char[] foundTitle = new char[512];
        User32.INSTANCE.GetWindowText(found, foundTitle, 512);
        User32.INSTANCE.PostMessage(found, WinUser.WM_CLOSE, null, null);
        return "已发送关闭指令到窗口: " + Native.toString(foundTitle);
    }

    @Tool(description = "最小化指定窗口")
    public String minimizeWindow(@ToolParam(description = "窗口标题（支持部分匹配）") String title) {
        HWND found = findWindowByTitle(title);
        if (found == null) return "未找到标题包含 '" + title + "' 的窗口";
        User32.INSTANCE.ShowWindow(found, SW_MINIMIZE);
        return "已最小化窗口: " + title;
    }

    @Tool(description = "最大化指定窗口")
    public String maximizeWindow(@ToolParam(description = "窗口标题（支持部分匹配）") String title) {
        HWND found = findWindowByTitle(title);
        if (found == null) return "未找到标题包含 '" + title + "' 的窗口";
        User32.INSTANCE.ShowWindow(found, SW_MAXIMIZE);
        return "已最大化窗口: " + title;
    }

    @Tool(description = "调整窗口大小和位置。x、y 为左上角坐标，width、height 为宽度和高度，单位像素")
    public String resizeWindow(
            @ToolParam(description = "窗口标题（支持部分匹配）") String title,
            @ToolParam(description = "左上角 X 坐标") int x,
            @ToolParam(description = "左上角 Y 坐标") int y,
            @ToolParam(description = "窗口宽度（像素）") int width,
            @ToolParam(description = "窗口高度（像素）") int height) {
        HWND found = findWindowByTitle(title);
        if (found == null) return "未找到标题包含 '" + title + "' 的窗口";
        User32.INSTANCE.SetWindowPos(found, null, x, y, width, height, 0);
        return "已调整窗口 '" + title + "' 为 (" + x + "," + y + ") " + width + "x" + height;
    }

    // ==================== 辅助方法 ====================

    private HWND findWindowByTitle(String title) {
        final String search = title.toLowerCase();
        final HWND[] result = {null};
        User32.INSTANCE.EnumWindows((hwnd, pointer) -> {
            if (User32.INSTANCE.IsWindowVisible(hwnd)) {
                char[] text = new char[512];
                User32.INSTANCE.GetWindowText(hwnd, text, 512);
                if (Native.toString(text).toLowerCase().contains(search)) {
                    result[0] = hwnd;
                    return false; // 找到就停止枚举
                }
            }
            return true;
        }, null);
        return result[0];
    }
}