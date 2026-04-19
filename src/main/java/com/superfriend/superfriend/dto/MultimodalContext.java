package com.superfriend.superfriend.dto;

import lombok.Data;

import java.util.List;

/**
 * 多模态处理上下文
 * 用于在处理流程中传递多模态相关信息
 */
@Data
public class MultimodalContext {

    /**
     * 用户原始意图
     */
    private UserIntent userIntent;

    /**
     * 文件解析结果（如果有文件解析）
     */
    private ParseResult parseResult;

    /**
     * 增强后的用户消息（注入了解析内容）
     */
    private String enhancedMessage;

    /**
     * 需要使用的模型能力
     */
    private List<String> requiredModalities;

    /**
     * 是否需要切换模型
     */
    private boolean modelSwitchRequired;

    /**
     * 推荐的模型 ID
     */
    private String recommendedModelId;

    /**
     * 生成的图片 URL（如果是图片生成请求）
     */
    private String generatedImageUrl;

    /**
     * 生成的媒体内容 URL（图片/音频/视频通用）
     */
    private String generatedMediaUrl;

    /**
     * 生成的资源 ID（用于前端 IndexedDB 存储引用）
     */
    private String generatedResourceId;

    /**
     * 生成的资源类型（image/audio/video/file）
     */
    private String generatedResourceType;

    /**
     * 处理阶段
     */
    private ProcessingStage stage;

    /**
     * 处理阶段枚举
     */
    public enum ProcessingStage {
        /** 初始阶段 */
        INIT,
        /** 意图识别完成 */
        INTENT_DETECTED,
        /** 文件解析中 */
        PARSING_FILE,
        /** 文件解析完成 */
        FILE_PARSED,
        /** 模型选择完成 */
        MODEL_SELECTED,
        /** LLM 调用中 */
        LLM_CALLING,
        /** 生成中（图片/音频） */
        GENERATING,
        /** 处理完成 */
        COMPLETED,
        /** 处理失败 */
        FAILED
    }

    // ============ 静态工厂方法 ============

    public static MultimodalContext create() {
        MultimodalContext ctx = new MultimodalContext();
        ctx.setStage(ProcessingStage.INIT);
        return ctx;
    }

    public static MultimodalContext withIntent(UserIntent intent) {
        MultimodalContext ctx = new MultimodalContext();
        ctx.setUserIntent(intent);
        ctx.setStage(ProcessingStage.INTENT_DETECTED);
        return ctx;
    }

    // ============ 便捷方法 ============

    public boolean hasFileParseResult() {
        return parseResult != null && parseResult.isSuccess();
    }

    public boolean isImageGeneration() {
        return userIntent != null && userIntent.requiresImageOutput();
    }

    public boolean isAudioGeneration() {
        return userIntent != null && userIntent.requiresAudioOutput();
    }

    public boolean needsFileParsing() {
        return userIntent != null && userIntent.isFileParsing();
    }

    public void advanceTo(ProcessingStage newStage) {
        this.stage = newStage;
    }

    /**
     * 构建用于 LLM 的完整消息
     */
    public String buildMessageForLLM() {
        if (enhancedMessage != null) {
            return enhancedMessage;
        }

        StringBuilder sb = new StringBuilder();

        // 添加用户原始文本
        if (userIntent != null && userIntent.getUserText() != null) {
            sb.append(userIntent.getUserText());
        }

        // 添加文件解析内容
        if (hasFileParseResult()) {
            sb.append("\n\n--- 文件内容 ---\n");
            sb.append(parseResult.getFormattedContentForLLM());
        }

        return sb.toString();
    }
}
