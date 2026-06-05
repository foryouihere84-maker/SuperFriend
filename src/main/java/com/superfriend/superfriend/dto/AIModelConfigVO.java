package com.superfriend.superfriend.dto;

import lombok.Data;
import java.util.List;

@Data
public class AIModelConfigVO {
    private String configId;
    private String name;
    private String provider;
    private String apiUrl;
    private String apiKeyMasked;
    private String modelId;
    private Integer maxTokens;
    private Double temperature;
    private Boolean isDefault;
    private Boolean isEnabled;
    private Integer sortOrder;
    private Boolean isSystem;

    // ============ 多模态能力 ============

    /**
     * 支持的输入模态类型（逗号分隔）
     */
    private String supportedModalities;

    /**
     * 支持的输入模态类型列表
     */
    private List<String> inputModalities;

    /**
     * 支持的输出模态类型（逗号分隔）
     */
    private String outputModalities;

    /**
     * 支持的输出模态类型列表
     */
    private List<String> outputModalitiesList;

    /**
     * 模型能力详细描述
     */
    private String capabilities;

    // ============ 便捷方法 ============

    /**
     * 是否支持图片输入
     */
    public boolean supportsImage() {
        return inputModalities != null && inputModalities.contains("image");
    }

    /**
     * 是否支持音频输入
     */
    public boolean supportsAudio() {
        return inputModalities != null && inputModalities.contains("audio");
    }

    /**
     * 是否支持视频输入
     */
    public boolean supportsVideo() {
        return inputModalities != null && inputModalities.contains("video");
    }

    /**
     * 是否支持图片输出
     */
    public boolean supportsImageOutput() {
        return outputModalitiesList != null && outputModalitiesList.contains("image");
    }

    /**
     * 是否支持音频输出
     */
    public boolean supportsAudioOutput() {
        return outputModalitiesList != null && outputModalitiesList.contains("audio");
    }

    /**
     * 是否为多模态模型
     */
    public boolean isMultimodal() {
        return supportsImage() || supportsAudio() || supportsVideo();
    }
}
