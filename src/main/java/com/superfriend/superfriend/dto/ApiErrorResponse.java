package com.superfriend.superfriend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiErrorResponse {
    private Boolean success = false;
    private String errorCode;
    private String message;
    private String suggestion;
    private Map<String, Object> details;
    private LocalDateTime timestamp;
    
    public static ApiErrorResponse of(String errorCode, String message, String suggestion) {
        return ApiErrorResponse.builder()
            .success(false)
            .errorCode(errorCode)
            .message(message)
            .suggestion(suggestion)
            .timestamp(LocalDateTime.now())
            .build();
    }
    
    public static ApiErrorResponse of(String errorCode, String message, String suggestion, Map<String, Object> details) {
        return ApiErrorResponse.builder()
            .success(false)
            .errorCode(errorCode)
            .message(message)
            .suggestion(suggestion)
            .details(details)
            .timestamp(LocalDateTime.now())
            .build();
    }
}
