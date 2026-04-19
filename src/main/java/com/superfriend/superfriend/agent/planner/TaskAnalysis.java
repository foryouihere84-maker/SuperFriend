package com.superfriend.superfriend.agent.planner;

public class TaskAnalysis {
    private String taskId;
    private String originalRequest;
    private TaskIntent intent;
    private TaskComplexity complexity;
    private String summary;
    private int estimatedSteps;
    private boolean requiresMultipleTools;
    
    public TaskAnalysis(String taskId, String originalRequest) {
        this.taskId = taskId;
        this.originalRequest = originalRequest;
        this.estimatedSteps = 1;
        this.requiresMultipleTools = false;
    }
    
    public String getTaskId() { return taskId; }
    public String getOriginalRequest() { return originalRequest; }
    public TaskIntent getIntent() { return intent; }
    public TaskComplexity getComplexity() { return complexity; }
    public String getSummary() { return summary; }
    public int getEstimatedSteps() { return estimatedSteps; }
    public boolean requiresMultipleTools() { return requiresMultipleTools; }
    
    public void setIntent(TaskIntent intent) { this.intent = intent; }
    public void setComplexity(TaskComplexity complexity) { this.complexity = complexity; }
    public void setSummary(String summary) { this.summary = summary; }
    public void setEstimatedSteps(int estimatedSteps) { this.estimatedSteps = estimatedSteps; }
    public void setRequiresMultipleTools(boolean requiresMultipleTools) { 
        this.requiresMultipleTools = requiresMultipleTools; 
    }
}
