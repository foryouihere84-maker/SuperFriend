-- ========================================
-- 宫殿式记忆系统 (Memory Palace)
-- ========================================

-- 记忆宫殿核心表
CREATE TABLE IF NOT EXISTS memory_palace (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '记忆ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',

    -- 记忆内容
    memory_type VARCHAR(20) NOT NULL DEFAULT 'NORMAL' COMMENT '记忆类型: CORE/IMPORTANT/NORMAL/EPHEMERAL',
    category VARCHAR(50) COMMENT '分类: PREFERENCE/SKILL/FACT/EXPERIENCE/RELATION/DECISION',
    title VARCHAR(200) NOT NULL COMMENT '记忆标题',
    content TEXT NOT NULL COMMENT '记忆内容',
    keywords VARCHAR(500) COMMENT '关键词(逗号分隔)',

    -- 时间维度
    memory_time DATETIME NOT NULL COMMENT '记忆发生时间',
    session_id VARCHAR(100) COMMENT '来源会话ID',

    -- 重要性
    importance INT DEFAULT 5 COMMENT '重要性 1-10',
    confidence DECIMAL(5,4) DEFAULT 0.8000 COMMENT '置信度',
    access_count INT DEFAULT 0 COMMENT '访问次数',
    reinforce_count INT DEFAULT 0 COMMENT '强化次数',

    -- 衰减
    decay_rate DECIMAL(5,4) DEFAULT 0.0500 COMMENT '衰减率',
    last_access_time DATETIME COMMENT '最后访问时间',
    effective_score DECIMAL(5,4) COMMENT '有效分数',

    -- 关联
    related_memory_ids TEXT COMMENT '关联的记忆ID列表(JSON数组)',
    source_node_id BIGINT COMMENT '关联的知识图谱节点ID(兼容)',

    -- 元数据
    emotion_tag VARCHAR(20) COMMENT '情感标签: positive/neutral/negative',
    context_tags TEXT COMMENT '上下文标签(JSON数组)',
    source_text TEXT COMMENT '原始对话文本(用于溯源)',

    -- 状态
    status VARCHAR(20) DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE/ARCHIVED/DELETED',

    created_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    INDEX idx_user_time (user_id, memory_time),
    INDEX idx_user_type (user_id, memory_type),
    INDEX idx_user_score (user_id, effective_score),
    INDEX idx_user_status (user_id, status),
    INDEX idx_session (session_id),
    INDEX idx_memory_time (memory_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='记忆宫殿核心表';

-- 记忆事件日志（用于时间线导航和审计）
CREATE TABLE IF NOT EXISTS memory_event (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '事件ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    memory_id BIGINT COMMENT '关联的记忆ID(可为空，如批量操作)',
    event_type VARCHAR(30) NOT NULL COMMENT '事件类型: CREATE/ACCESS/REINFORCE/DECAY/ARCHIVE/DELETE/UPGRADE/DOWNGRADE',
    event_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '事件时间',
    event_data TEXT COMMENT '事件详情(JSON)',
    session_id VARCHAR(100) COMMENT '触发事件的会话ID',

    INDEX idx_user_time (user_id, event_time),
    INDEX idx_memory (memory_id),
    INDEX idx_type_time (event_type, event_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='记忆事件日志表';

-- 记忆关联网络
CREATE TABLE IF NOT EXISTS memory_connection (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '关联ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    source_memory_id BIGINT NOT NULL COMMENT '源记忆ID',
    target_memory_id BIGINT NOT NULL COMMENT '目标记忆ID',
    connection_type VARCHAR(30) DEFAULT 'RELATED' COMMENT '关联类型: RELATED/CAUSED_BY/LED_TO/SIMILAR/CONTRADICTS',
    strength DECIMAL(5,4) DEFAULT 0.5000 COMMENT '关联强度 0-1',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    UNIQUE KEY uk_connection (source_memory_id, target_memory_id),
    INDEX idx_user (user_id),
    INDEX idx_source (source_memory_id),
    INDEX idx_target (target_memory_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='记忆关联网络表';

-- 记忆触发规则配置（可选，用于自定义触发条件）
CREATE TABLE IF NOT EXISTS memory_trigger_rule (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '规则ID',
    user_id BIGINT COMMENT '用户ID(为空表示全局规则)',
    rule_name VARCHAR(100) NOT NULL COMMENT '规则名称',
    rule_type VARCHAR(30) NOT NULL COMMENT '规则类型: KEYWORD/PATTERN/EMOTION/INTENT',
    trigger_patterns TEXT NOT NULL COMMENT '触发模式(JSON数组)',
    target_memory_type VARCHAR(20) NOT NULL COMMENT '目标记忆类型',
    target_category VARCHAR(50) COMMENT '目标分类',
    priority INT DEFAULT 5 COMMENT '优先级 1-10',
    enabled TINYINT(1) DEFAULT 1 COMMENT '是否启用',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    INDEX idx_user_enabled (user_id, enabled),
    INDEX idx_priority (priority)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='记忆触发规则配置表';
