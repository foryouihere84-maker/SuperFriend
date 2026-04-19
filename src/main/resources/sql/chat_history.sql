-- ========================================
-- 对话历史表 (chat_history)
-- ========================================
CREATE TABLE IF NOT EXISTS chat_history (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '历史记录ID',
    session_id VARCHAR(100) NOT NULL COMMENT '会话ID',
    user_id BIGINT NOT NULL DEFAULT 1 COMMENT '用户ID',
    title VARCHAR(255) DEFAULT NULL COMMENT '对话标题（取首条用户消息）',
    model VARCHAR(100) DEFAULT NULL COMMENT '使用的模型',
    mode VARCHAR(20) DEFAULT 'ai' COMMENT '对话模式：ai/mcp',
    message_count INT DEFAULT 0 COMMENT '消息条数',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_session_id (session_id),
    INDEX idx_user_id (user_id),
    INDEX idx_updated_time (updated_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='对话历史表';

-- ========================================
-- 对话消息表 (chat_message)
-- ========================================
CREATE TABLE IF NOT EXISTS chat_message (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '消息ID',
    history_id BIGINT NOT NULL COMMENT '关联的对话历史ID',
    session_id VARCHAR(100) NOT NULL COMMENT '会话ID',
    role VARCHAR(20) NOT NULL COMMENT '角色：user/assistant/system',
    content TEXT NOT NULL COMMENT '消息内容',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_history_id (history_id),
    INDEX idx_session_id (session_id),
    FOREIGN KEY (history_id) REFERENCES chat_history(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='对话消息表';
