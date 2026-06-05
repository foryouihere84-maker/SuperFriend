-- ========================================
-- 用户技能选择状态迁移脚本
-- ========================================

-- 1. 在 user_skills 表中添加 is_selected 字段
ALTER TABLE user_skills 
ADD COLUMN is_selected TINYINT DEFAULT 0 COMMENT '是否被选中：1-是，0-否' AFTER is_enabled;

-- 2. 为 is_selected 字段创建索引
ALTER TABLE user_skills 
ADD INDEX idx_user_selected (user_id, is_selected);

-- 3. 初始化已有用户技能的选择状态（默认全部选中）
UPDATE user_skills 
SET is_selected = 1 
WHERE is_enabled = 1;

-- 4. 从 skills 表迁移已有的选择状态到 user_skills 表
-- 如果 skills.is_selected = 1，则更新对应的 user_skills.is_selected = 1
UPDATE user_skills us
JOIN skills s ON us.skill_id = s.id
SET us.is_selected = s.is_selected
WHERE s.is_selected = 1;

-- 5. 可选：移除 skills 表中的 is_selected 字段（如果不再需要全局选择状态）
-- ALTER TABLE skills DROP COLUMN is_selected;
