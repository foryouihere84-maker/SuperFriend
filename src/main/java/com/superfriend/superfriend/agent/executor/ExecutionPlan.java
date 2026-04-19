package com.superfriend.superfriend.agent.executor;

import com.superfriend.superfriend.agent.tool.*;
import com.superfriend.superfriend.agent.tool.Tool;

import java.util.*;
import java.util.stream.Collectors;

public class ExecutionPlan {
    private List<ExecutionStep> steps;
    private double totalCost;
    private double estimatedReliability;
    
    public ExecutionPlan() {
        this.steps = new ArrayList<>();
        this.totalCost = 0.0;
        this.estimatedReliability = 1.0;
    }
    
    public void addStep(ExecutionStep step) {
        steps.add(step);
        totalCost += step.getTool().getCost();
        estimatedReliability *= step.getTool().getReliability();
    }
    
    public List<ExecutionStep> getSteps() {
        return steps;
    }
    
    public double getTotalCost() {
        return totalCost;
    }
    
    public double getEstimatedReliability() {
        return estimatedReliability;
    }
    
    public void optimizeOrder() {
        Map<String, ExecutionStep> stepMap = steps.stream()
            .collect(Collectors.toMap(s -> s.getTool().getName(), s -> s));
        
        List<ExecutionStep> optimized = new ArrayList<>();
        Set<String> processed = new HashSet<>();
        
        while (processed.size() < steps.size()) {
            ExecutionStep nextStep = null;
            int minDependencies = Integer.MAX_VALUE;
            
            for (ExecutionStep step : steps) {
                if (processed.contains(step.getTool().getName())) {
                    continue;
                }
                
                int dependencies = 0;
                for (String dep : step.getDependencies()) {
                    if (!processed.contains(dep)) {
                        dependencies++;
                    }
                }
                
                if (dependencies < minDependencies) {
                    minDependencies = dependencies;
                    nextStep = step;
                }
            }
            
            if (nextStep != null) {
                optimized.add(nextStep);
                processed.add(nextStep.getTool().getName());
            }
        }
        
        this.steps = optimized;
    }
    
    public static class ExecutionStep {
        private Tool tool;
        private Map<String, Object> parameters;
        private Set<String> dependencies;
        private boolean isParallelizable;
        
        public ExecutionStep(Tool tool, Map<String, Object> parameters) {
            this.tool = tool;
            this.parameters = parameters;
            this.dependencies = new HashSet<>();
            this.isParallelizable = false;
        }
        
        public Tool getTool() { return tool; }
        public Map<String, Object> getParameters() { return parameters; }
        public Set<String> getDependencies() { return dependencies; }
        public boolean isParallelizable() { return isParallelizable; }
        
        public void addDependency(String toolName) {
            dependencies.add(toolName);
        }
        
        public void setParallelizable(boolean parallelizable) {
            this.isParallelizable = parallelizable;
        }
    }
}
