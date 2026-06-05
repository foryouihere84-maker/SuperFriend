package com.superfriend.superfriend.agent.planner;

import java.util.*;

public class SubTask {
    private String subTaskId;
    private String description;
    private String assignedTool;
    private Map<String, Object> parameters;
    private Set<String> dependencies;
    private SubTaskStatus status;
    private Object result;
    private String error;
    
    public SubTask(String subTaskId, String description) {
        this.subTaskId = subTaskId;
        this.description = description;
        this.parameters = new HashMap<>();
        this.dependencies = new HashSet<>();
        this.status = SubTaskStatus.PENDING;
    }
    
    public String getSubTaskId() { return subTaskId; }
    public String getDescription() { return description; }
    public String getAssignedTool() { return assignedTool; }
    public Map<String, Object> getParameters() { return parameters; }
    public Set<String> getDependencies() { return dependencies; }
    public SubTaskStatus getStatus() { return status; }
    public Object getResult() { return result; }
    public String getError() { return error; }
    
    public void setAssignedTool(String assignedTool) { this.assignedTool = assignedTool; }
    public void setStatus(SubTaskStatus status) { this.status = status; }
    public void setResult(Object result) { this.result = result; }
    public void setError(String error) { this.error = error; }
    
    public void addParameter(String key, Object value) {
        parameters.put(key, value);
    }
    
    public void addDependency(String dependency) {
        dependencies.add(dependency);
    }
}
