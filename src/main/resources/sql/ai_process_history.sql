-- ========================================
-- AI 处理历史记录表 (ai_process_history)
-- ========================================
CREATE TABLE IF NOT EXISTS ai_process_history (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '历史记录ID',
    name VARCHAR(255) NOT NULL COMMENT '处理名称',
    create_time DATETIME NOT NULL COMMENT '处理时间',
    related_notes TEXT COMMENT '关联的笔记（JSON格式）',
    content LONGTEXT COMMENT 'AI生成的内容',
    process_type VARCHAR(50) NOT NULL COMMENT '处理类型：extract-提炼, polish-润色, summarize-总结, expand-拓展',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_user_id (user_id),
    INDEX idx_process_type (process_type),
    INDEX idx_create_time (create_time),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI处理历史记录表';
