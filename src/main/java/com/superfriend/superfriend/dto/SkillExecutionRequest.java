package com.superfriend.superfriend.dto;

import lombok.Data;
import java.util.Map;
import java.util.HashMap;

@Data
public class SkillExecutionRequest {
    private String skillName;
    private String sessionId;
    private String userId;
    private String userRequest;
    private Map<String, Object> parameters = new HashMap<>();
    private Map<String, Object> variables = new HashMap<>();
}
