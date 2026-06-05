package com.superfriend.superfriend.agent.skill;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class WorkflowResult {
    private String workflowId;
    private Boolean success;
    private Map<String, Object> outputs;
    private List<StepResult> stepResults;
    private Long totalExecutionTime;
    private String error;
    
    @Data
    public static class StepResult {
        private String stepId;
        private String skillName;
        private Boolean success;
        private Object output;
        private Long executionTime;
        private String error;
    }
    
    public static WorkflowResult success(String workflowId, Map<String, Object> outputs, List<StepResult> stepResults, Long totalExecutionTime) {
        WorkflowResult result = new WorkflowResult();
        result.setWorkflowId(workflowId);
        result.setSuccess(true);
        result.setOutputs(outputs);
        result.setStepResults(stepResults);
        result.setTotalExecutionTime(totalExecutionTime);
        return result;
    }
    
    public static WorkflowResult failure(String workflowId, String error, List<StepResult> stepResults, Long totalExecutionTime) {
        WorkflowResult result = new WorkflowResult();
        result.setWorkflowId(workflowId);
        result.setSuccess(false);
        result.setError(error);
        result.setStepResults(stepResults);
        result.setTotalExecutionTime(totalExecutionTime);
        return result;
    }
}
