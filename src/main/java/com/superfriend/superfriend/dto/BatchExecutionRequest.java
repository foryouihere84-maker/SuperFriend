package com.superfriend.superfriend.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class BatchExecutionRequest {
    private List<SkillExecutionRequest> requests;
    private Boolean stopOnError = false;
    private Map<String, Object> commonParameters;
    private Map<String, Object> commonVariables;
}
