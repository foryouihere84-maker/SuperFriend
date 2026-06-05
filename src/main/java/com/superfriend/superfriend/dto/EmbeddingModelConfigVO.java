package com.superfriend.superfriend.dto;

import lombok.Data;

@Data
public class EmbeddingModelConfigVO {
    private String configId;
    private String name;
    private String provider;
    private String apiUrl;
    private String apiKeyMasked;
    private String modelId;
    private Integer dimensions;
    private Integer maxInputTokens;
    private Boolean isDefault;
    private Boolean isEnabled;
    private Integer sortOrder;
}
