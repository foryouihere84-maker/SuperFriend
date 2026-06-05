package com.superfriend.superfriend.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class EmbeddingModelConfig {
    private Long id;
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

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createdTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updatedTime;
}
