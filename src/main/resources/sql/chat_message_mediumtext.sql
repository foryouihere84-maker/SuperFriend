-- 将 chat_message.content 列从 TEXT (64KB) 改为 MEDIUMTEXT (16MB)
-- 支持存储包含文件解析内容的增强消息
ALTER TABLE chat_message MODIFY COLUMN content MEDIUMTEXT NOT NULL COMMENT '消息内容（支持长文本和文件内容）';