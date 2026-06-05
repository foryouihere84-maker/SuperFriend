-- ========================================
-- superfriend 数据库表结构
-- ========================================

USE superfriend;

-- ========================================
-- 1. 用户表 (users)
-- ========================================
CREATE TABLE IF NOT EXISTS users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '用户 ID',
    username VARCHAR(50) NOT NULL UNIQUE COMMENT '用户名',
    email VARCHAR(100) NOT NULL UNIQUE COMMENT '邮箱',
    password VARCHAR(255) NOT NULL COMMENT '密码（BCrypt 加密）',
    display_name VARCHAR(100) DEFAULT NULL COMMENT '显示名称',
    avatar_url VARCHAR(500) DEFAULT NULL COMMENT '头像 URL',
    status TINYINT DEFAULT 1 COMMENT '状态：1-正常，0-禁用',
    last_login_time DATETIME DEFAULT NULL COMMENT '最后登录时间',
    last_login_ip VARCHAR(50) DEFAULT NULL COMMENT '最后登录 IP',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_username (username),
    INDEX idx_email (email),
    INDEX idx_status (status),
    INDEX idx_created_time (created_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- ========================================
-- 2. 角色表 (roles)
-- ========================================
CREATE TABLE IF NOT EXISTS roles (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '角色 ID',
    role_name VARCHAR(50) NOT NULL UNIQUE COMMENT '角色名称',
    role_code VARCHAR(50) NOT NULL UNIQUE COMMENT '角色编码',
    description VARCHAR(200) DEFAULT NULL COMMENT '角色描述',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_role_code (role_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色表';

-- ========================================
-- 3. 用户角色关联表 (user_roles)
-- ========================================
CREATE TABLE IF NOT EXISTS user_roles (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '关联 ID',
    user_id BIGINT NOT NULL COMMENT '用户 ID',
    role_id BIGINT NOT NULL COMMENT '角色 ID',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_user_role (user_id, role_id),
    INDEX idx_user_id (user_id),
    INDEX idx_role_id (role_id),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户角色关联表';

-- ========================================
-- 4. 登录日志表 (login_logs)
-- ========================================
CREATE TABLE IF NOT EXISTS login_logs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '日志 ID',
    user_id BIGINT DEFAULT NULL COMMENT '用户 ID（未登录时为 NULL）',
    username VARCHAR(50) DEFAULT NULL COMMENT '用户名',
    login_type VARCHAR(20) NOT NULL COMMENT '登录类型：password-密码登录，register-注册',
    login_ip VARCHAR(50) DEFAULT NULL COMMENT '登录 IP',
    user_agent VARCHAR(500) DEFAULT NULL COMMENT '用户代理',
    status TINYINT NOT NULL COMMENT '状态：1-成功，0-失败',
    error_message VARCHAR(500) DEFAULT NULL COMMENT '错误信息',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '登录时间',
    INDEX idx_user_id (user_id),
    INDEX idx_username (username),
    INDEX idx_login_type (login_type),
    INDEX idx_status (status),
    INDEX idx_created_time (created_time),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='登录日志表';

-- ========================================
-- 5. 密码重置表 (password_resets)
-- ========================================
CREATE TABLE IF NOT EXISTS password_resets (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '重置 ID',
    user_id BIGINT NOT NULL COMMENT '用户 ID',
    token VARCHAR(255) NOT NULL UNIQUE COMMENT '重置令牌',
    expires_at DATETIME NOT NULL COMMENT '过期时间',
    used TINYINT DEFAULT 0 COMMENT '是否已使用：1-已使用，0-未使用',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_user_id (user_id),
    INDEX idx_token (token),
    INDEX idx_expires_at (expires_at),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='密码重置表';

-- ========================================
-- 6. 技能表 (skills) - 系统和项目技能
-- ========================================
CREATE TABLE IF NOT EXISTS skills (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '技能 ID',
    name VARCHAR(100) NOT NULL UNIQUE COMMENT '技能名称',
    description TEXT COMMENT '技能描述',
    category VARCHAR(50) COMMENT '分类',
    version VARCHAR(20) DEFAULT '1.0.0' COMMENT '版本',
    author VARCHAR(100) COMMENT '作者',
    priority TINYINT DEFAULT 5 COMMENT '优先级：1-低，5-中，8-高，10-关键',
    scope TINYINT NOT NULL COMMENT '作用域：1-系统，2-项目',
    instructions TEXT COMMENT '执行指令',
    parameters JSON COMMENT '参数配置',
    required_tools JSON COMMENT '所需工具列表',
    tags JSON COMMENT '标签列表',
    status TINYINT DEFAULT 1 COMMENT '状态：1-启用，0-禁用',
    skill_file_path VARCHAR(500) COMMENT '技能文件路径',
    
    -- 统计信息
    execution_count BIGINT DEFAULT 0 COMMENT '执行次数',
    success_rate DECIMAL(5,4) DEFAULT 1.0 COMMENT '成功率',
    average_execution_time INT DEFAULT 0 COMMENT '平均执行时间(ms)',
    last_used_time DATETIME DEFAULT NULL COMMENT '最后使用时间',
    
    -- 元数据
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_name (name),
    INDEX idx_category (category),
    INDEX idx_scope (scope),
    INDEX idx_status (status),
    INDEX idx_created_time (created_time),
    INDEX idx_last_used (last_used_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='技能表';

-- ========================================
-- 7. 用户技能关联表 (user_skills) - 用户自定义技能
-- ========================================
CREATE TABLE IF NOT EXISTS user_skills (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '关联 ID',
    user_id BIGINT NOT NULL COMMENT '用户 ID',
    skill_id BIGINT NOT NULL COMMENT '技能 ID',
    is_enabled TINYINT DEFAULT 1 COMMENT '是否启用：1-是，0-否',
    custom_priority TINYINT DEFAULT NULL COMMENT '自定义优先级（覆盖技能表的优先级）',
    custom_tags JSON COMMENT '自定义标签（追加到技能标签）',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_user_skill (user_id, skill_id),
    INDEX idx_user_id (user_id),
    INDEX idx_skill_id (skill_id),
    INDEX idx_is_enabled (is_enabled),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (skill_id) REFERENCES skills(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户技能关联表';

-- ========================================
-- 8. 技能脚本表 (skill_scripts)
-- ========================================
CREATE TABLE IF NOT EXISTS skill_scripts (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '脚本 ID',
    skill_id BIGINT NOT NULL COMMENT '技能 ID',
    script_type VARCHAR(20) NOT NULL COMMENT '脚本类型：python, javascript, etc',
    script_name VARCHAR(100) NOT NULL COMMENT '脚本名称',
    script_content TEXT NOT NULL COMMENT '脚本内容',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_skill_script (skill_id, script_type),
    FOREIGN KEY (skill_id) REFERENCES skills(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='技能脚本表';

-- ========================================
-- 9. 技能执行历史表 (skill_executions)
-- ========================================
CREATE TABLE IF NOT EXISTS skill_executions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '执行 ID',
    skill_id BIGINT NOT NULL COMMENT '技能 ID',
    session_id VARCHAR(100) COMMENT '会话 ID',
    user_id BIGINT COMMENT '用户 ID',
    user_request TEXT COMMENT '用户请求',
    result TEXT COMMENT '执行结果',
    success TINYINT NOT NULL COMMENT '是否成功：1-是，0-否',
    execution_time INT COMMENT '执行时间(ms)',
    error_message TEXT COMMENT '错误信息',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '执行时间',
    INDEX idx_skill_id (skill_id),
    INDEX idx_user_id (user_id),
    INDEX idx_session_id (session_id),
    INDEX idx_created_time (created_time),
    FOREIGN KEY (skill_id) REFERENCES skills(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='技能执行历史表';

-- ========================================
-- 插入初始角色数据
-- ========================================
INSERT INTO roles (role_name, role_code, description) VALUES
('USER', 'USER', 'Normal user with basic permissions'),
('ADMIN', 'ADMIN', 'Administrator with full permissions')
ON DUPLICATE KEY UPDATE role_name=VALUES(role_name);
