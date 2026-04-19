-- ========================================
-- 对话压缩记录表 (chat_compression)
-- ========================================
CREATE TABLE IF NOT EXISTS chat_compression (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '压缩记录ID',
    session_id VARCHAR(100) NOT NULL COMMENT '会话ID',
    original_message_count INT NOT NULL COMMENT '压缩前的消息数量',
    compressed_message_count INT NOT NULL COMMENT '压缩后的消息数量',
    summary TEXT NOT NULL COMMENT 'LLM 生成的对话摘要',
    preserved_tool_results TEXT COMMENT '保留的工具结果（JSON）',
    compression_level VARCHAR(10) NOT NULL DEFAULT 'L2' COMMENT '压缩级别：L1/L2/L3',
    original_tokens BIGINT DEFAULT NULL COMMENT '压缩前估算 token 数',
    compressed_tokens BIGINT DEFAULT NULL COMMENT '压缩后估算 token 数',
    compression_ratio DECIMAL(5,2) COMMENT '压缩率（百分比）',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_session_id (session_id),
    INDEX idx_created_time (created_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='对话压缩记录表';
