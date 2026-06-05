package com.superfriend.superfriend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class LLMRequest {
    private String model;
    private String apiUrl;
    private String apiKey;
    private List<Map<String, Object>> messages;
    private Double temperature;
    private Integer maxTokens;
    @Builder.Default
    private boolean stream = true;
    private Map<String, Object> extraParams;
    private String sessionId;
    private Long userId;

    public static LLMRequest fromConfig(String modelId, String apiUrl, String apiKey) {
        return LLMRequest.builder()
            .model(modelId)
            .apiUrl(apiUrl)
            .apiKey(apiKey)
            .build();
    }

    public static LLMRequest fromConfig(String modelId, String apiUrl, String apiKey,
                                         List<Map<String, Object>> messages) {
        return LLMRequest.builder()
            .model(modelId)
            .apiUrl(apiUrl)
            .apiKey(apiKey)
            .messages(messages)
            .build();
    }
}
