package com.limou.agent_demo.tool;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WebToolTest {

    private WebTool webTool;
    private ToolSafety safety;

    // 国内可访问的测试站点
    private static final String ECHO_SERVER = "https://httpbun.com";

    @BeforeEach
    void setUp() {
        safety = new ToolSafety(
                System.getProperty("user.home") + "," + System.getProperty("user.dir"),
                "dir,echo,type,curl,ipconfig,ping,whoami,netstat,find,findstr,tasklist,where,ver,systeminfo,nslookup,tracert,pathping,getmac,arp,route"
        );
        webTool = new WebTool(safety);
    }

    // ========== URL 安全测试 ==========

    @Test
    void shouldBlockLocalhost() {
        String result = webTool.httpGet("http://localhost:8080/api/test", "");
        assertTrue(result.contains("Access denied"),
                "localhost should be blocked, got: " + result);
    }

    @Test
    void shouldBlock127001() {
        String result = webTool.httpGet("http://127.0.0.1:3000/data", "");
        assertTrue(result.contains("Access denied"),
                "127.0.0.1 should be blocked, got: " + result);
    }

    @Test
    void shouldBlock192168() {
        String result = webTool.httpGet("http://192.168.1.1/admin", "");
        assertTrue(result.contains("Access denied"),
                "192.168.x.x should be blocked, got: " + result);
    }

    @Test
    void shouldBlockFileProtocol() {
        String result = webTool.httpGet("file:///C:/Windows/System32/drivers/etc/hosts", "");
        assertTrue(result.contains("Access denied"),
                "file:// protocol should be blocked, got: " + result);
    }

    @Test
    void shouldBlockTenDotNetwork() {
        String result = webTool.httpGet("http://10.0.0.1:8080/internal", "");
        assertTrue(result.contains("Access denied"),
                "10.x.x.x should be blocked, got: " + result);
    }

    // ========== 基础连通性 ==========

    @Test
    void shouldGetBaidu() {
        String result = webTool.httpGet("https://www.baidu.com", "");
        assertTrue(result.contains("HTTP 200"),
                "Baidu should return 200, got: " + result);
    }

    // ========== HTTP GET ==========

    @Test
    void shouldGetWithQueryParam() {
        String result = webTool.httpGet(ECHO_SERVER + "/get?foo=bar", "");
        assertTrue(result.contains("HTTP 200"),
                "Echo server should return 200, got: " + result);
    }

    @Test
    void shouldReturn404() {
        String result = webTool.httpGet(ECHO_SERVER + "/status/404", "");
        assertTrue(result.contains("HTTP 404"),
                "Should return 404, got: " + result);
    }

    // ========== HTTP POST ==========

    @Test
    void shouldPostJson() {
        String result = webTool.httpPost(
                ECHO_SERVER + "/post",
                "{\"name\":\"agent\"}",
                "application/json",
                "");
        assertTrue(result.contains("HTTP 200") || result.contains("HTTP 201"),
                "POST should return 2xx, got: " + result);
    }

    // ========== HTTP PUT/DELETE ==========

    @Test
    void shouldPutJson() {
        String result = webTool.httpPut(
                ECHO_SERVER + "/put",
                "{\"updated\":true}",
                "");
        assertTrue(result.contains("HTTP 200") || result.contains("HTTP 201"),
                "PUT should return 2xx, got: " + result);
    }

    @Test
    void shouldDelete() {
        String result = webTool.httpDelete(ECHO_SERVER + "/delete", "");
        assertTrue(result.contains("HTTP 200") || result.contains("HTTP 204"),
                "DELETE should return 2xx, got: " + result);
    }

    // ========== Headers 测试 ==========

    @Test
    void shouldSendCustomHeaders() {
        String result = webTool.httpGet(
                ECHO_SERVER + "/headers",
                "{\"X-Test-Header\":\"hello-agent\"}");
        assertTrue(result.contains("HTTP 200"),
                "Should return 200, got: " + result);
    }
}
