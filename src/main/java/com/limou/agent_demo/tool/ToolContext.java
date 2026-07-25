package com.limou.agent_demo.tool;

import org.springframework.stereotype.Component;

@Component
public class ToolContext {

    private final ThreadLocal<Boolean> confirmFlag = ThreadLocal.withInitial(() -> false);
    private final ThreadLocal<String> conversationId = ThreadLocal.withInitial(() -> null);

    public void begin(String conversationId, boolean confirm) {
        this.conversationId.set(conversationId);
        this.confirmFlag.set(confirm);
    }

    public void clear() {
        confirmFlag.remove();
        conversationId.remove();
    }

    public boolean isConfirmed() {
        return confirmFlag.get();
    }

    public String getConversationId() {
        return conversationId.get();
    }
}
