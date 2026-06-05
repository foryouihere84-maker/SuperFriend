package com.superfriend.superfriend.agent.skill;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class SkillWorkflow {
    private String workflowId;
    private String name;
    private String description;
    private List<WorkflowStep> steps;
    private Map<String, Object> context;
    private WorkflowConfig config;
    
    @Data
    public static class WorkflowStep {
        private String stepId;
        private String skillName;
        private Map<String, Object> parameters;
        private String inputKey;
        private String outputKey;
        private WorkflowCondition condition;
        private WorkflowErrorHandler errorHandler;
        private Integer timeout;
        private Integer retryCount;
    }
    
    @Data
    public static class WorkflowCondition {
        private String type;
        private String expression;
        private String expectedValue;
    }
    
    @Data
    public static class WorkflowErrorHandler {
        private String strategy;
        private String fallbackSkill;
        private Map<String, Object> fallbackParameters;
    }
    
    @Data
    public static class WorkflowConfig {
        private Boolean stopOnError;
        private Integer maxConcurrency;
        private Long timeout;
        private Boolean enableCache;
    }
}
