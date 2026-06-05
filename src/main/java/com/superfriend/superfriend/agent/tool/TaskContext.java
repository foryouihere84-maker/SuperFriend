package com.superfriend.superfriend.agent.tool;

import java.util.HashMap;
import java.util.Map;

public class TaskContext {
    private Map<String, Object> variables;
    private Map<String, Object> history;
    private Map<String, Double> toolScores;
    
    public TaskContext() {
        this.variables = new HashMap<>();
        this.history = new HashMap<>();
        this.toolScores = new HashMap<>();
    }
    
    public void setVariable(String key, Object value) {
        variables.put(key, value);
    }
    
    public Object getVariable(String key) {
        return variables.get(key);
    }
    
    public void recordToolUsage(String toolName, double score) {
        toolScores.put(toolName, score);
    }
    
    public double getToolScore(String toolName) {
        return toolScores.getOrDefault(toolName, 0.5);
    }
    
    public Map<String, Object> getVariables() { return variables; }
    public Map<String, Object> getHistory() { return history; }
    public Map<String, Double> getToolScores() { return toolScores; }
}
