-- ========================================
-- 技能选择状态迁移脚本
-- ========================================

-- 1. 在 skills 表中增加 is_selected 字段
ALTER TABLE skills 
ADD COLUMN is_selected TINYINT DEFAULT 0 COMMENT '是否被选中：1-是，0-否' AFTER status;

-- 2. 为 is_selected 字段创建索引
ALTER TABLE skills 
ADD INDEX idx_is_selected (is_selected);

-- 3. 将系统级技能（scope=1）默认设置为选中状态
UPDATE skills 
SET is_selected = 1 
WHERE scope = 1;

-- 4. 创建技能选择历史表（可选，用于记录用户的选择历史）
CREATE TABLE IF NOT EXISTS skill_selection_history (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '历史 ID',
    skill_id BIGINT NOT NULL COMMENT '技能 ID',
    user_id BIGINT DEFAULT NULL COMMENT '用户 ID（NULL 表示系统级选择）',
    is_selected TINYINT NOT NULL COMMENT '选择状态：1-选中，0-取消选中',
    selection_reason VARCHAR(200) DEFAULT NULL COMMENT '选择原因',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_skill_id (skill_id),
    INDEX idx_user_id (user_id),
    INDEX idx_created_time (created_time),
    FOREIGN KEY (skill_id) REFERENCES skills(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='技能选择历史表';

-- 5. 创建视图：当前选中的技能（包括系统级和用户级）
CREATE OR REPLACE VIEW v_selected_skills AS
SELECT 
    s.*,
    CASE 
        WHEN s.scope = 1 THEN s.is_selected
        WHEN s.scope = 2 THEN COALESCE(us.is_enabled, 0)
        ELSE 0
    END AS current_selected
FROM skills s
LEFT JOIN user_skills us ON s.id = us.skill_id AND us.is_enabled = 1
WHERE 
    (s.scope = 1 AND s.is_selected = 1)
    OR (s.scope = 2 AND us.is_enabled = 1);
