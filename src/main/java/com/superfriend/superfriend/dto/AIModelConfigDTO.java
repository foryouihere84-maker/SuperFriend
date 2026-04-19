package com.superfriend.superfriend.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class AIModelConfigDTO {
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

    /**
     * 支持的输入模态类型（逗号分隔）
     * 可选值：text, image, audio, video
     */
    private String supportedModalities;

    /**
     * 支持的输出模态类型（逗号分隔）
     * 可选值：text, image, audio, video
     */
    private String outputModalities;

    /**
     * 模型能力详细描述（JSON格式）
     */
    private String capabilities;

    // ============ 便捷方法 ============

    /**
     * 设置支持的输入模态列表
     */
    public void setSupportedModalitiesList(List<String> modalities) {
        if (modalities == null || modalities.isEmpty()) {
            this.supportedModalities = "text";
        } else {
            this.supportedModalities = String.join(",", modalities);
        }
    }

    /**
     * 获取支持的输入模态列表
     */
    public List<String> getSupportedModalitiesList() {
        if (supportedModalities == null || supportedModalities.isEmpty()) {
            return java.util.Collections.singletonList("text");
        }
        return java.util.Arrays.asList(supportedModalities.split(","));
    }

    /**
     * 设置支持的输出模态列表
     */
    public void setOutputModalitiesList(List<String> modalities) {
        if (modalities == null || modalities.isEmpty()) {
            this.outputModalities = "text";
        } else {
            this.outputModalities = String.join(",", modalities);
        }
    }

    /**
     * 获取支持的输出模态列表
     */
    public List<String> getOutputModalitiesList() {
        if (outputModalities == null || outputModalities.isEmpty()) {
            return java.util.Collections.singletonList("text");
        }
        return java.util.Arrays.asList(outputModalities.split(","));
    }
}
