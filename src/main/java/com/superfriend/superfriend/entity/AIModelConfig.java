package com.superfriend.superfriend.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class AIModelConfig {
    private Long id;
    private String configId;
    private Long userId;
    private String name;
    private String provider;
    private String apiUrl;
    private String apiKey;
    private String modelId;
    private Integer maxTokens;
    private BigDecimal temperature;
    private Boolean isDefault;
    private Boolean isEnabled;
    private Integer sortOrder;
    private String extraParams;

    // ============ 模型能力标识 ============

    /**
     * 支持的模态类型（逗号分隔）
     * 可选值：text, image, audio, video
     * 例如："text,image" 表示支持文本和图片
     */
    private String supportedModalities;

    /**
     * 支持的输出模态类型（逗号分隔）
     * 可选值：text, image, audio, video
     * 例如："text,audio" 表示可以输出文本和音频
     */
    private String outputModalities;

    /**
     * 模型能力标志（JSON 格式）
     * 包含更详细的能力描述
     */
    private String capabilities;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createdTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updatedTime;

    // ============ 便捷方法 ============

    /**
     * 判断是否支持指定输入模态
     * 策略：显式配置 → 按配置；未配置 → text 返回 true，其他模态乐观返回 true
     * 原因：模型更新快，硬编码模型名不可维护；实际不支持时由调用方降级处理
     */
    public boolean supportsModality(String modality) {
        if (supportedModalities != null && !supportedModalities.isEmpty()) {
            String[] modalities = supportedModalities.split(",");
            for (String m : modalities) {
                if (m.trim().equalsIgnoreCase(modality)) {
                    return true;
                }
            }
            return false;
        }
        // 未配置：乐观策略，都返回 true，由实际调用结果决定
        return true;
    }

    /**
     * 判断是否支持指定输出模态
     * 策略：显式配置 → 按配置；未配置 → 乐观返回 true，交给 3 层 fallback 处理失败
     */
    public boolean supportsOutputModality(String modality) {
        if (outputModalities != null && !outputModalities.isEmpty()) {
            String[] modalities = outputModalities.split(",");
            for (String m : modalities) {
                if (m.trim().equalsIgnoreCase(modality)) {
                    return true;
                }
            }
            return false;
        }
        // 未配置：乐观策略，让 Tier 1 尝试调用，失败了自然降级到 Tier 2
        return true;
    }

    /**
     * 判断是否支持图片输入
     */
    public boolean supportsImage() {
        return supportsModality("image");
    }

    /**
     * 判断是否支持音频输入
     */
    public boolean supportsAudio() {
        return supportsModality("audio");
    }

    /**
     * 判断是否支持视频输入
     */
    public boolean supportsVideo() {
        return supportsModality("video");
    }

    /**
     * 判断是否支持图片输出
     */
    public boolean supportsImageOutput() {
        return supportsOutputModality("image");
    }

    /**
     * 判断是否支持音频输出
     */
    public boolean supportsAudioOutput() {
        return supportsOutputModality("audio");
    }

    /**
     * 判断是否支持视频输出
     */
    public boolean supportsVideoOutput() {
        return supportsOutputModality("video");
    }
}
