package com.superfriend.superfriend.dto;

import lombok.Data;

@Data
public class EmbeddingModelConfigDTO {
    private String configId;
    private String name;
    private String provider;
    private String apiUrl;
    private String apiKey;
    private String modelId;
    private Integer dimensions;
    private Integer maxInputTokens;
    private Boolean isDefault;
    private Boolean isEnabled;
    private Integer sortOrder;
}
