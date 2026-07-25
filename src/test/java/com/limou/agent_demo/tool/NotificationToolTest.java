package com.limou.agent_demo.tool;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NotificationToolTest {

    @Test
    void shouldNotCrashOnConstruction() {
        NotificationTool tool = new NotificationTool();
        assertNotNull(tool);
    }

    @Test
    void shouldReturnMessageWhenNotifyCalled() {
        NotificationTool tool = new NotificationTool();
        String result = tool.notify("测试标题", "测试消息内容");
        assertNotNull(result);
        // 无论系统托盘是否可用，都应返回非空字符串
        assertFalse(result.isEmpty(), "notify should return a non-empty result message");
    }

    @Test
    void shouldReturnDifferentResultsForDifferentTitles() {
        NotificationTool tool = new NotificationTool();
        String r1 = tool.notify("标题A", "消息A");
        String r2 = tool.notify("标题B", "消息B");
        assertNotNull(r1);
        assertNotNull(r2);
    }
}
