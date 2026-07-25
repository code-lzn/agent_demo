package com.limou.agent_demo.tool;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
public class ToolCallCapture {

    private final ThreadLocal<List<Map<String, Object>>> captured = ThreadLocal.withInitial(ArrayList::new);

    public void record(String toolName, String args, String result) {
        captured.get().add(Map.of("tool", toolName, "args", args, "result", result));
    }

    public List<Map<String, Object>> drain() {
        List<Map<String, Object>> calls = captured.get();
        if (calls.isEmpty()) return Collections.emptyList();
        List<Map<String, Object>> snapshot = List.copyOf(calls);
        calls.clear();
        return snapshot;
    }

    public void clear() {
        captured.get().clear();
        captured.remove();
    }
}
