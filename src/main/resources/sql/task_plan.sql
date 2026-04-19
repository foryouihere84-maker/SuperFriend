-- Task Planner 数据库表
-- 任务计划表
CREATE TABLE IF NOT EXISTS agent_task_plan (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    plan_id VARCHAR(50) NOT NULL UNIQUE COMMENT '计划唯一标识（UUID）',
    session_id VARCHAR(100) NOT NULL COMMENT '关联的会话ID',
    user_id BIGINT NOT NULL,
    original_request TEXT NOT NULL COMMENT '用户原始请求',
    plan_summary TEXT COMMENT '计划摘要',
    status VARCHAR(20) NOT NULL DEFAULT 'pending' COMMENT '状态：pending/executing/completed/failed/paused',
    total_steps INT DEFAULT 0 COMMENT '总步骤数',
    completed_steps INT DEFAULT 0 COMMENT '已完成步骤数',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_time DATETIME DEFAULT NULL,
    completed_time DATETIME DEFAULT NULL,
    INDEX idx_session_id (session_id),
    INDEX idx_user_id (user_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent 任务计划表';

-- 任务步骤表
CREATE TABLE IF NOT EXISTS agent_task_step (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    step_id VARCHAR(50) NOT NULL UNIQUE COMMENT '步骤唯一标识',
    plan_id VARCHAR(50) NOT NULL COMMENT '关联的计划ID',
    step_number INT NOT NULL COMMENT '步骤序号（从1开始）',
    description TEXT NOT NULL COMMENT '步骤描述',
    status VARCHAR(20) NOT NULL DEFAULT 'pending' COMMENT '状态：pending/executing/completed/failed/skipped',
    tool_name VARCHAR(200) DEFAULT NULL COMMENT '需要调用的工具名',
    tool_arguments TEXT DEFAULT NULL COMMENT '工具参数（JSON）',
    result TEXT COMMENT '步骤执行结果',
    error TEXT COMMENT '步骤执行错误',
    execution_time_ms INT DEFAULT NULL COMMENT '执行耗时',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_time DATETIME DEFAULT NULL,
    INDEX idx_plan_id (plan_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent 任务步骤表';
