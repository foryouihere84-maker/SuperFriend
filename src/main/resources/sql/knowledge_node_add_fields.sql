-- 为知识图谱节点添加头像、图片和详细描述字段
-- 执行时间: 2026-04-15

ALTER TABLE knowledge_node
ADD COLUMN avatar VARCHAR(500) DEFAULT NULL COMMENT '节点头像URL或emoji' AFTER description,
ADD COLUMN image VARCHAR(1000) DEFAULT NULL COMMENT '节点图片URL，点击头像展示' AFTER avatar,
ADD COLUMN detailed_description TEXT DEFAULT NULL COMMENT '详细描述，点击头像展示' AFTER image;

-- 为现有节点设置默认头像（根据类型）
UPDATE knowledge_node SET avatar = '👤' WHERE node_type = 'USER' AND avatar IS NULL;
UPDATE knowledge_node SET avatar = '📁' WHERE node_type = 'PROJECT' AND avatar IS NULL;
UPDATE knowledge_node SET avatar = '⚙️' WHERE node_type = 'TECHNOLOGY' AND avatar IS NULL;
UPDATE knowledge_node SET avatar = '💡' WHERE node_type = 'CONCEPT' AND avatar IS NULL;
UPDATE knowledge_node SET avatar = '📋' WHERE node_type = 'TASK' AND avatar IS NULL;
UPDATE knowledge_node SET avatar = '❌' WHERE node_type = 'ERROR' AND avatar IS NULL;
UPDATE knowledge_node SET avatar = '✅' WHERE node_type = 'SOLUTION' AND avatar IS NULL;
UPDATE knowledge_node SET avatar = '⭐' WHERE node_type = 'PREFERENCE' AND avatar IS NULL;
