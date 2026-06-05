package com.superfriend.superfriend.agent.skill;

import lombok.Data;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

@Data
public class SkillContext {
    private String sessionId;
    private String userId;
    private String userRequest;
    private Map<String, Object> parameters;
    private Map<String, Object> variables;
    private List<String> executionHistory;
    private SkillExecutionContext executionContext;

    public SkillContext() {
        this.parameters = new HashMap<>();
        this.variables = new HashMap<>();
        this.executionHistory = new ArrayList<>();
    }

    public void addParameter(String key, Object value) {
        parameters.put(key, value);
    }

    public Object getParameter(String key) {
        return parameters.get(key);
    }

    public void addVariable(String key, Object value) {
        variables.put(key, value);
    }

    public Object getVariable(String key) {
        return variables.get(key);
    }

    public void recordExecution(String step) {
        executionHistory.add(step);
    }

    @Data
    public static class SkillExecutionContext {
        private String currentStep;
        private int totalSteps;
        private int completedSteps;
        private Map<String, Object> intermediateResults;
        private boolean interrupted;

        public SkillExecutionContext() {
            this.intermediateResults = new HashMap<>();
            this.interrupted = false;
        }

        public void addIntermediateResult(String key, Object value) {
            intermediateResults.put(key, value);
        }

        public Object getIntermediateResult(String key) {
            return intermediateResults.get(key);
        }
    }
}
