-- ========================================
-- 技能表扩展 - 添加脚本和资源存储字段
-- ========================================

-- 添加 scripts_json 字段（存储脚本内容，JSON 格式）
ALTER TABLE skills ADD COLUMN IF NOT EXISTS scripts_json LONGTEXT COMMENT '脚本内容（JSON 格式）' AFTER tags;

-- 添加 resources_json 字段（存储资源元数据，JSON 格式）
ALTER TABLE skills ADD COLUMN IF NOT EXISTS resources_json LONGTEXT COMMENT '资源元数据（JSON 格式）' AFTER scripts_json;

-- 添加 timeout 字段（脚本执行超时时间）
ALTER TABLE skills ADD COLUMN IF NOT EXISTS timeout BIGINT DEFAULT 60000 COMMENT '执行超时时间（毫秒）' AFTER resources_json;

-- 添加 license 字段
ALTER TABLE skills ADD COLUMN IF NOT EXISTS license VARCHAR(50) COMMENT '许可证' AFTER author;

-- 添加 compatibility 字段
ALTER TABLE skills ADD COLUMN IF NOT EXISTS compatibility VARCHAR(100) COMMENT '兼容性要求' AFTER license;

-- 更新注释
ALTER TABLE skills MODIFY COLUMN scope TINYINT NOT NULL COMMENT '作用域：1-系统，2-项目，3-用户';
