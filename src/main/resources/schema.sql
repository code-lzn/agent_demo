CREATE TABLE IF NOT EXISTS conversation (
    id VARCHAR(36) PRIMARY KEY,
    title VARCHAR(200),
    model VARCHAR(50),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS message (
    id VARCHAR(36) PRIMARY KEY,
    conversation_id VARCHAR(36) NOT NULL,
    role VARCHAR(20) NOT NULL,
    content TEXT,
    tool_calls JSON,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_conv_id (conversation_id),
    FOREIGN KEY (conversation_id) REFERENCES conversation(id) ON DELETE CASCADE
);
