-- ========================================
-- Knowledge Graph 知识图谱数据库表
-- 合并了长期记忆功能，统一的知识管理系统
-- ========================================

-- 知识节点表（合并了长期记忆功能）
CREATE TABLE IF NOT EXISTS knowledge_node (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL COMMENT '用户ID',
    node_type VARCHAR(30) NOT NULL COMMENT '节点类型：USER/PROJECT/TECHNOLOGY/CONCEPT/TASK/ERROR/SOLUTION/PREFERENCE',
    name VARCHAR(200) NOT NULL COMMENT '节点名称',
    description TEXT COMMENT '详细描述（来自长期记忆）',
    keywords VARCHAR(500) COMMENT '关键词标签（逗号分隔）',
    importance TINYINT DEFAULT 5 COMMENT '重要性 1-10（来自长期记忆）',
    properties JSON COMMENT '节点属性（JSON格式）',
    source_session_id VARCHAR(100) COMMENT '来源会话ID',
    confidence DECIMAL(3,2) DEFAULT 1.00 COMMENT '置信度 0-1',
    access_count INT DEFAULT 0 COMMENT '被访问次数',
    last_accessed_time DATETIME COMMENT '最后访问时间',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_user_type (user_id, node_type),
    INDEX idx_name (name(100)),
    INDEX idx_confidence (confidence DESC),
    INDEX idx_importance (importance DESC),
    INDEX idx_keywords (keywords(255)),
    UNIQUE KEY uk_user_scope_name (user_id, scope, name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识节点表（合并长期记忆）';

-- 知识关系表
CREATE TABLE IF NOT EXISTS knowledge_relation (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL COMMENT '用户ID',
    source_node_id BIGINT NOT NULL COMMENT '源节点ID',
    target_node_id BIGINT NOT NULL COMMENT '目标节点ID',
    relation_type VARCHAR(30) NOT NULL COMMENT '关系类型：支持自由定义的中文描述（最多10字符），如：使用、依赖、包含等',
    properties JSON COMMENT '关系属性（JSON格式）',
    weight DECIMAL(3,2) DEFAULT 1.00 COMMENT '关系权重 0-1',
    source_session_id VARCHAR(100) COMMENT '来源会话ID',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_relation (source_node_id, target_node_id, relation_type),
    INDEX idx_user_id (user_id),
    INDEX idx_source (source_node_id),
    INDEX idx_target (target_node_id),
    INDEX idx_relation_type (relation_type),
    FOREIGN KEY (source_node_id) REFERENCES knowledge_node(id) ON DELETE CASCADE,
    FOREIGN KEY (target_node_id) REFERENCES knowledge_node(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识关系表';

-- 会话图谱快照表（记录每次对话使用的图谱上下文）
CREATE TABLE IF NOT EXISTS session_graph_snapshot (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id VARCHAR(100) NOT NULL COMMENT '会话ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    relevant_nodes JSON COMMENT '相关节点ID列表',
    relevant_relations JSON COMMENT '相关关系ID列表',
    graph_context TEXT COMMENT '生成的图谱上下文文本',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_session (session_id),
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会话图谱快照表';

-- ========================================
-- 双层知识图谱架构扩展
-- ========================================

-- 用户画像表（全局图谱的用户画像）
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

-- 图谱变更历史表（用于追溯和回滚）
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

-- 扩展 knowledge_node 表：添加 scope 字段区分对话级/全局级
-- ALTER TABLE knowledge_node ADD COLUMN scope VARCHAR(20) DEFAULT 'GLOBAL'
--     COMMENT 'CONVERSATION/GLOBAL' AFTER node_type;

-- ========================================
-- 数据迁移脚本（如果已有 agent_memory 表）
-- ========================================

-- 将 agent_memory 数据迁移到 knowledge_node
-- INSERT INTO knowledge_node (user_id, node_type, name, description, keywords, importance, source_session_id, confidence, access_count, last_accessed_time, created_time, updated_time)
-- SELECT
--     user_id,
--     CASE category
--         WHEN 'preference' THEN 'PREFERENCE'
--         WHEN 'fact' THEN 'CONCEPT'
--         WHEN 'skill' THEN 'TECHNOLOGY'
--         WHEN 'error_solution' THEN 'SOLUTION'
--         ELSE 'CONCEPT'
--     END as node_type,
--     SUBSTRING(content, 1, 100) as name,
--     content as description,
--     keywords,
--     importance,
--     source_session_id,
--     0.8 as confidence,
--     access_count,
--     last_accessed_time,
--     created_time,
--     updated_time
-- FROM agent_memory
-- WHERE user_id NOT IN (SELECT DISTINCT user_id FROM knowledge_node WHERE name IN (SELECT DISTINCT content FROM agent_memory));

-- ========================================
-- 旧表删除（迁移完成后执行）
-- ========================================

-- DROP TABLE IF EXISTS agent_memory_relation;
-- DROP TABLE IF EXISTS agent_memory;
