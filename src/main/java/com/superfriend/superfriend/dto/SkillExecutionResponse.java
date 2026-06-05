package com.superfriend.superfriend.dto;

import lombok.Data;
import java.util.Map;
import java.util.HashMap;

@Data
public class SkillExecutionResponse {
    private boolean success;
    private Object data;
    private String error;
    private long executionTime;
    private String status;
    private String errorCode;
    private String suggestion;
    private Map<String, Object> metadata = new HashMap<>();

    public static SkillExecutionResponse success(Object data, long executionTime) {
        SkillExecutionResponse response = new SkillExecutionResponse();
        response.setSuccess(true);
        response.setData(data);
        response.setExecutionTime(executionTime);
        response.setStatus("SUCCESS");
        return response;
    }

    public static SkillExecutionResponse failure(String error, long executionTime) {
        SkillExecutionResponse response = new SkillExecutionResponse();
        response.setSuccess(false);
        response.setError(error);
        response.setExecutionTime(executionTime);
        response.setStatus("FAILED");
        return response;
    }
}
