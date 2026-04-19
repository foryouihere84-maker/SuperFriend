-- 添加模型能力字段到 ai_model_config 表
-- 执行时间：2024-xx-xx
-- 说明：支持多模态能力标识，自动选择合适的模型

-- 添加支持的输入模态类型字段
ALTER TABLE ai_model_config
ADD COLUMN supported_modalities VARCHAR(100) DEFAULT 'text' COMMENT '支持的输入模态类型（逗号分隔）：text,image,audio,video'
AFTER extra_params;

-- 添加支持的输出模态类型字段
ALTER TABLE ai_model_config
ADD COLUMN output_modalities VARCHAR(100) DEFAULT 'text' COMMENT '支持的输出模态类型（逗号分隔）：text,image,audio,video'
AFTER supported_modalities;

-- 添加模型能力详细描述字段
ALTER TABLE ai_model_config
ADD COLUMN capabilities TEXT DEFAULT NULL COMMENT '模型能力详细描述（JSON格式）'
AFTER output_modalities;

-- 更新一些常见模型的能力标识
-- OpenAI GPT-4 Vision
UPDATE ai_model_config
SET supported_modalities = 'text,image',
    output_modalities = 'text'
WHERE model_id LIKE 'gpt-4-vision%'
   OR model_id LIKE 'gpt-4o%'
   OR model_id LIKE 'gpt-4-turbo%';

-- OpenAI GPT-4 (纯文本)
UPDATE ai_model_config
SET supported_modalities = 'text',
    output_modalities = 'text'
WHERE model_id LIKE 'gpt-4%' AND model_id NOT LIKE 'gpt-4-vision%' AND model_id NOT LIKE 'gpt-4o%' AND model_id NOT LIKE 'gpt-4-turbo%';

-- OpenAI GPT-3.5
UPDATE ai_model_config
SET supported_modalities = 'text',
    output_modalities = 'text'
WHERE model_id LIKE 'gpt-3.5%';

-- Claude 3 系列（支持图片）
UPDATE ai_model_config
SET supported_modalities = 'text,image',
    output_modalities = 'text'
WHERE model_id LIKE 'claude-3%'
   OR model_id LIKE 'claude%opus%'
   OR model_id LIKE 'claude%sonnet%'
   OR model_id LIKE 'claude%haiku%';

-- DeepSeek
UPDATE ai_model_config
SET supported_modalities = 'text',
    output_modalities = 'text'
WHERE model_id LIKE 'deepseek%';

-- Gemini 系列（支持图片、音频、视频）
UPDATE ai_model_config
SET supported_modalities = 'text,image,audio,video',
    output_modalities = 'text'
WHERE model_id LIKE 'gemini%';

-- Qwen-VL 系列（支持图片）
UPDATE ai_model_config
SET supported_modalities = 'text,image',
    output_modalities = 'text'
WHERE model_id LIKE 'qwen-vl%'
   OR model_id LIKE 'qwen%vl%';

-- Qwen-Audio 系列（支持音频）
UPDATE ai_model_config
SET supported_modalities = 'text,audio',
    output_modalities = 'text'
WHERE model_id LIKE 'qwen-audio%'
   OR model_id LIKE 'qwen%audio%';

-- DALL-E（图片生成模型）
UPDATE ai_model_config
SET supported_modalities = 'text',
    output_modalities = 'image'
WHERE model_id LIKE 'dall-e%'
   OR model_id LIKE 'dalle%';

-- Stable Diffusion（图片生成模型）
UPDATE ai_model_config
SET supported_modalities = 'text,image',
    output_modalities = 'image'
WHERE model_id LIKE 'stable-diffusion%'
   OR model_id LIKE 'sd%';

-- 添加索引以优化查询
ALTER TABLE ai_model_config
ADD INDEX idx_supported_modalities (supported_modalities(50));
