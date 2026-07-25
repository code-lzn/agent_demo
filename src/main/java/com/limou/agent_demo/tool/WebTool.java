package com.limou.agent_demo.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Component
public class WebTool {

    private final ToolSafety safety;
    private final HttpClient httpClient;

    public WebTool(ToolSafety safety) {
        this.safety = safety;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Tool(description = "Send an HTTP GET request. headers format: JSON object, e.g. {\"Authorization\":\"Bearer xxx\"}")
    public String httpGet(
            @ToolParam(description = "Target URL") String url,
            @ToolParam(description = "Optional request headers as JSON key-value pairs. Can be empty string \"\" if not needed") String headers) {
        return doRequest("GET", url, "", "", headers);
    }

    @Tool(description = "Send an HTTP POST request. headers parameter can be empty string \"\" skipping it. body is the raw request body")
    public String httpPost(
            @ToolParam(description = "Target URL") String url,
            @ToolParam(description = "Request body (raw text/JSON)") String body,
            @ToolParam(description = "Content-Type header value, e.g. application/json. Can be empty string\"\"") String contentType,
            @ToolParam(description = "Optional request headers as JSON key-value pairs. Can be empty string \"\" if not needed") String headers) {
        return doRequest("POST", url, body, contentType, headers);
    }

    @Tool(description = "Send an HTTP PUT request")
    public String httpPut(
            @ToolParam(description = "Target URL") String url,
            @ToolParam(description = "Request body (raw text/JSON)") String body,
            @ToolParam(description = "Optional request headers as JSON key-value pairs. Can be empty string \"\" if not needed") String headers) {
        return doRequest("PUT", url, body, "application/json", headers);
    }

    @Tool(description = "Send an HTTP DELETE request")
    public String httpDelete(
            @ToolParam(description = "Target URL") String url,
            @ToolParam(description = "Optional request headers as JSON key-value pairs. Can be empty string \"\" if not needed") String headers) {
        return doRequest("DELETE", url, "", "", headers);
    }

    private String doRequest(String method, String url, String body, String contentType, String headersJson) {
        // 1. URL 安全检查
        if (!safety.isUrlAllowed(url)) {
            return "Access denied: URL '" + url + "' is not allowed (internal/private network blocked)";
        }

        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(15));

            // 设置 Content-Type
            if (contentType != null && !contentType.isBlank()) {
                requestBuilder.header("Content-Type", contentType.trim());
            }

            // 解析并设置自定义 headers
            if (headersJson != null && !headersJson.isBlank()) {
                parseHeaders(headersJson).forEach((k, v) -> requestBuilder.header(k, v));
            }

            // 设置 User-Agent（避免被拒）
            requestBuilder.header("User-Agent", "AgentDemo/1.0");

            // 方法 + body
            switch (method.toUpperCase()) {
                case "GET" -> requestBuilder.GET();
                case "DELETE" -> requestBuilder.DELETE();
                case "POST", "PUT" -> {
                    byte[] bodyBytes = body != null ? body.getBytes() : new byte[0];
                    requestBuilder.method(method.toUpperCase(),
                            HttpRequest.BodyPublishers.ofByteArray(bodyBytes));
                }
                default -> {
                    return "Unsupported HTTP method: " + method;
                }
            }

            HttpResponse<byte[]> response = httpClient.send(requestBuilder.build(),
                    HttpResponse.BodyHandlers.ofByteArray());

            int status = response.statusCode();
            byte[] responseBody = response.body();

            // 限制响应体大小
            long maxLen = safety.getMaxResponseBytes();
            String bodyStr;
            if (responseBody.length > maxLen) {
                bodyStr = new String(responseBody, 0, (int) maxLen)
                        + "\n\n... (response truncated at " + maxLen + " bytes, total size: " + responseBody.length + " bytes)";
            } else {
                bodyStr = new String(responseBody);
            }

            return String.format("HTTP %d\n\n%s", status, bodyStr);

        } catch (Exception e) {
            return "HTTP request failed: " + e.getMessage();
        }
    }

    /**
     * 简单 JSON 解析：{"key":"value","key2":"value2"}
     * 不引入 Jackson/Gson，手写轻量解析
     */
    private java.util.Map<String, String> parseHeaders(String json) {
        java.util.Map<String, String> map = new java.util.LinkedHashMap<>();
        String s = json.trim();
        if (!s.startsWith("{") || !s.endsWith("}")) return map;
        s = s.substring(1, s.length() - 1);
        if (s.isBlank()) return map;

        // 按逗号分割，但要忽略引号内的逗号
        int depth = 0;
        StringBuilder current = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (c == '"') depth ^= 1;
            if (c == ',' && depth == 0) {
                parseKVPair(current.toString().trim(), map);
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        if (!current.isEmpty()) parseKVPair(current.toString().trim(), map);
        return map;
    }

    private void parseKVPair(String pair, java.util.Map<String, String> map) {
        int colonIdx = -1;
        int depth = 0;
        for (int i = 0; i < pair.length(); i++) {
            char c = pair.charAt(i);
            if (c == '"') depth ^= 1;
            if (c == ':' && depth == 0) { colonIdx = i; break; }
        }
        if (colonIdx == -1) return;
        String key = pair.substring(0, colonIdx).trim().replaceAll("^\"|\"$", "");
        String value = pair.substring(colonIdx + 1).trim().replaceAll("^\"|\"$", "");
        if (!key.isEmpty()) map.put(key, value);
    }
}
