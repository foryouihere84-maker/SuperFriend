-- ========================================
-- 知识图谱表结构修复
-- 添加缺失的 description 字段
-- ========================================

-- 检查并添加 description 字段到 knowledge_node 表
ALTER TABLE knowledge_node
ADD COLUMN IF NOT EXISTS description TEXT COMMENT '详细描述（来自长期记忆）' AFTER name;

-- 如果上面的语法不支持，使用以下方式：
-- 先检查字段是否存在，不存在则添加
-- SET @dbname = DATABASE();
-- SET @tablename = 'knowledge_node';
-- SET @columnname = 'description';
-- SET @preparedStatement = (SELECT IF(
--     (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @columnname) > 0,
--     'SELECT 1',
--     CONCAT('ALTER TABLE ', @tablename, ' ADD COLUMN ', @columnname, ' TEXT COMMENT ''详细描述（来自长期记忆）'' AFTER name')
-- ));
-- PREPARE alterIfNotExists FROM @preparedStatement;
-- EXECUTE alterIfNotExists;
-- DEALLOCATE PREPARE alterIfNotExists;
