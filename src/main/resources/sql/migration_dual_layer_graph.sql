-- ========================================
-- 双层知识图谱架构 - 数据库迁移脚本
-- 执行日期: 2026-04-12
-- 说明: 添加用户画像表、图谱变更历史表，扩展知识节点表
-- ========================================

-- ==================== 1. 新建表 ====================

-- 1.1 用户画像表（全局图谱的用户画像）
CREATE TABLE IF NOT EXISTS user_profile (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL UNIQUE COMMENT '用户ID',
    interests JSON COMMENT '兴趣方向 [{"name":"后端开发","confidence":0.85,"lastSeen":"2026-04-12"}]',
    traits JSON COMMENT '性格特征 [{"name":"注重细节","confidence":0.7,"contexts":["代码评审","文档编写"]}]',
    preferences JSON COMMENT '偏好 [{"name":"简洁代码风格","confidence":0.8,"category":"coding"}]',
    skills JSON COMMENT '技能 [{"name":"Spring Boot","level":"熟练","confidence":0.9}]',
    summary TEXT COMMENT '用户画像摘要文本（LLM生成）',
    version INT DEFAULT 1 COMMENT '版本号',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户画像表';

-- 1.2 图谱变更历史表（用于追溯和回滚）
CREATE TABLE IF NOT EXISTS graph_fact_history (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL COMMENT '用户ID',
    fact_type VARCHAR(50) NOT NULL COMMENT '事实类型: INTEREST/TRAIT/PREFERENCE/SKILL/NODE',
    fact_key VARCHAR(100) NOT NULL COMMENT '事实键（用于定位）',
    old_value JSON COMMENT '旧值',
    new_value JSON COMMENT '新值',
    old_confidence DECIMAL(3,2) COMMENT '旧置信度',
    new_confidence DECIMAL(3,2) COMMENT '新置信度',
    reason VARCHAR(50) NOT NULL COMMENT '变更原因: REPLACE/MERGE/CONFLICT/ACCUMULATE/TIME_DECAY',
    source_session_id VARCHAR(100) COMMENT '来源会话ID',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_fact_type (fact_type),
    INDEX idx_fact_key (fact_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='图谱变更历史表';

-- ==================== 2. 修改现有表 ====================

-- 2.1 为 knowledge_node 表添加 scope 字段
-- 先检查字段是否存在，不存在则添加
SET @dbname = DATABASE();
SET @tablename = 'knowledge_node';
SET @columnname = 'scope';
SET @preparedStatement = (SELECT IF(
    (
        SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = @dbname
        AND TABLE_NAME = @tablename
        AND COLUMN_NAME = @columnname
    ) > 0,
    'SELECT 1',
    CONCAT('ALTER TABLE ', @tablename, ' ADD COLUMN ', @columnname, ' VARCHAR(20) DEFAULT ''GLOBAL'' COMMENT ''CONVERSATION/GLOBAL'' AFTER node_type')
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

-- 2.2 为 knowledge_relation 表添加索引（如果不存在）
SET @indexname = 'idx_source_session_id';
SET @preparedStatement = (SELECT IF(
    (
        SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
        WHERE TABLE_SCHEMA = @dbname
        AND TABLE_NAME = 'knowledge_relation'
        AND INDEX_NAME = @indexname
    ) > 0,
    'SELECT 1',
    CONCAT('CREATE INDEX ', @indexname, ' ON knowledge_relation (source_session_id)')
));
PREPARE createIndexIfNotExists FROM @preparedStatement;
EXECUTE createIndexIfNotExists;
DEALLOCATE PREPARE createIndexIfNotExists;

-- ==================== 3. 更新现有数据 ====================

-- 3.1 将现有 knowledge_node 数据的 scope 设置为 GLOBAL（如果为 NULL）
UPDATE knowledge_node SET scope = 'GLOBAL' WHERE scope IS NULL OR scope = '';

-- ==================== 4. 验证 ====================

-- 验证表创建成功
SELECT 'user_profile' AS table_name, COUNT(*) AS exists_check FROM information_schema.tables
WHERE table_schema = DATABASE() AND table_name = 'user_profile'
UNION ALL
SELECT 'graph_fact_history', COUNT(*) FROM information_schema.tables
WHERE table_schema = DATABASE() AND table_name = 'graph_fact_history';

-- 验证字段添加成功
SELECT COLUMN_NAME, COLUMN_TYPE, COLUMN_DEFAULT, COLUMN_COMMENT
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'knowledge_node' AND COLUMN_NAME = 'scope';

-- ==================== 完成 ====================
SELECT '数据库迁移完成！' AS status;

-- ==================== 5. 添加节点唯一约束 ====================

-- 5.1 为 knowledge_node 表添加唯一约束（user_id + scope + name）
-- 确保：全局图谱和对话图谱中，相同名称的节点只能存在一个
SET @ukname = 'uk_user_scope_name';
SET @preparedStatement = (SELECT IF(
    (
        SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
        WHERE TABLE_SCHEMA = @dbname
        AND TABLE_NAME = 'knowledge_node'
        AND INDEX_NAME = @ukname
    ) > 0,
    'SELECT 1',
    CONCAT('ALTER TABLE knowledge_node ADD UNIQUE KEY ', @ukname, ' (user_id, scope, name)')
));
PREPARE addUniqueKey FROM @preparedStatement;
EXECUTE addUniqueKey;
DEALLOCATE PREPARE addUniqueKey;

-- 验证唯一约束添加成功
SELECT INDEX_NAME, COLUMN_NAME, NON_UNIQUE
FROM INFORMATION_SCHEMA.STATISTICS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'knowledge_node' AND INDEX_NAME = 'uk_user_scope_name';
