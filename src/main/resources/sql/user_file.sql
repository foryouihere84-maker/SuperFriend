-- 用户文件持久化存储表
-- 用于管理用户上传的文件，支持 24 小时 TTL 和 lastAccessTime 更新

CREATE TABLE IF NOT EXISTS user_file (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    session_id VARCHAR(100) COMMENT '关联的对话会话ID',

    -- 文件基本信息
    file_id VARCHAR(100) NOT NULL UNIQUE COMMENT '文件唯一标识',
    file_name VARCHAR(500) NOT NULL COMMENT '原始文件名',
    stored_name VARCHAR(500) NOT NULL COMMENT '存储文件名（UUID）',
    file_path VARCHAR(1000) NOT NULL COMMENT '文件存储路径',
    file_size BIGINT NOT NULL DEFAULT 0 COMMENT '文件大小（字节）',
    mime_type VARCHAR(200) COMMENT 'MIME类型',
    file_type VARCHAR(50) NOT NULL DEFAULT 'unknown' COMMENT '文件类型分类：document, image, video, audio, archive, other',

    -- 文件状态
    status VARCHAR(20) NOT NULL DEFAULT 'active' COMMENT '文件状态：active, deleted, expired',
    is_sensitive TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否为敏感文件',

    -- 时间管理
    upload_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
    last_access_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最后访问时间',
    expire_time DATETIME COMMENT '过期时间（用于敏感文件短TTL）',

    -- 元数据
    metadata JSON COMMENT '额外元数据（JSON格式）',

    -- 索引优化
    INDEX idx_user_id (user_id),
    INDEX idx_session_id (session_id),
    INDEX idx_status (status),
    INDEX idx_last_access (last_access_time),
    INDEX idx_upload_time (upload_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户文件持久化存储表';
