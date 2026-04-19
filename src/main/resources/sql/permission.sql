-- ========================================
-- Permissions 权限系统数据库表
-- ========================================

-- 权限策略表
CREATE TABLE IF NOT EXISTS agent_permission_policy (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    tool_name VARCHAR(200) DEFAULT NULL COMMENT '工具名（NULL 表示匹配所有工具）',
    operation_type VARCHAR(50) DEFAULT NULL COMMENT '操作类型：read/write/delete/execute',
    resource_pattern VARCHAR(500) DEFAULT NULL COMMENT '资源匹配模式（支持通配符，如 C:\\Users\\*）',
    permission_level VARCHAR(20) NOT NULL DEFAULT 'confirm' COMMENT '权限级别：auto_allow/confirm/deny',
    description VARCHAR(200) DEFAULT NULL COMMENT '策略描述',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_tool (user_id, tool_name),
    INDEX idx_permission_level (permission_level)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent 权限策略表';

-- 操作审批记录表
CREATE TABLE IF NOT EXISTS agent_approval_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    session_id VARCHAR(100) NOT NULL,
    tool_name VARCHAR(200) NOT NULL,
    operation VARCHAR(50) NOT NULL,
    arguments TEXT COMMENT '工具参数（JSON）',
    permission_level VARCHAR(20) NOT NULL COMMENT '要求的权限级别',
    decision VARCHAR(20) NOT NULL COMMENT '审批决定：approved/denied/auto_allowed/timed_out',
    decided_by VARCHAR(50) DEFAULT NULL COMMENT '审批者：user/auto/system',
    reason VARCHAR(500) DEFAULT NULL COMMENT '审批原因',
    response_time_ms INT DEFAULT NULL COMMENT '审批响应时间',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_session_id (session_id),
    INDEX idx_tool_name (tool_name),
    INDEX idx_decision (decision)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent 操作审批记录表';

-- 信任边界表
CREATE TABLE IF NOT EXISTS agent_trust_boundary (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    boundary_name VARCHAR(100) NOT NULL COMMENT '边界名称',
    boundary_type VARCHAR(30) NOT NULL COMMENT '边界类型：path/network/process/database',
    pattern VARCHAR(500) NOT NULL COMMENT '边界模式（路径/域名/进程名/数据库名）',
    trust_level TINYINT DEFAULT 5 COMMENT '信任级别 1-10（10=完全信任）',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_type (user_id, boundary_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent 信任边界表';

-- 默认权限策略（系统级，user_id=0 表示对所有用户生效）
INSERT IGNORE INTO agent_permission_policy (user_id, tool_name, operation_type, permission_level, description) VALUES
(0, NULL, 'read', 'auto_allow', '所有读取操作自动允许'),
(0, NULL, 'write', 'confirm', '所有写入操作需要用户确认'),
(0, NULL, 'delete', 'confirm', '所有删除操作需要用户确认'),
(0, NULL, 'execute', 'confirm', '所有执行操作需要用户确认');
