package com.superfriend.superfriend.agent.planner;

import org.springframework.stereotype.Component;

@Component
public class TaskAnalyzer {
    
    public TaskAnalysis analyze(String userRequest) {
        String taskId = generateTaskId(userRequest);
        TaskAnalysis analysis = new TaskAnalysis(taskId, userRequest);
        
        analysis.setIntent(determineIntent(userRequest));
        analysis.setComplexity(determineComplexity(userRequest));
        analysis.setSummary(generateSummary(userRequest));
        analysis.setEstimatedSteps(estimateSteps(userRequest));
        analysis.setRequiresMultipleTools(checkMultipleTools(userRequest));
        
        return analysis;
    }
    
    private String generateTaskId(String request) {
        return "task_" + System.currentTimeMillis() + "_" + 
               Integer.toHexString(request.hashCode());
    }
    
    private TaskIntent determineIntent(String request) {
        String lowerRequest = request.toLowerCase();
        
        if (lowerRequest.contains("search") || lowerRequest.contains("find") || 
            lowerRequest.contains("look for") || lowerRequest.contains("查询")) {
            return TaskIntent.SEARCH;
        }
        
        if (lowerRequest.contains("analyze") || lowerRequest.contains("analysis") ||
            lowerRequest.contains("统计") || lowerRequest.contains("分析")) {
            return TaskIntent.ANALYZE;
        }
        
        if (lowerRequest.contains("create") || lowerRequest.contains("generate") ||
            lowerRequest.contains("make") || lowerRequest.contains("创建") || 
            lowerRequest.contains("生成")) {
            return TaskIntent.CREATE;
        }
        
        if (lowerRequest.contains("modify") || lowerRequest.contains("change") ||
            lowerRequest.contains("update") || lowerRequest.contains("修改") ||
            lowerRequest.contains("更新")) {
            return TaskIntent.MODIFY;
        }
        
        if (lowerRequest.contains("and") || lowerRequest.contains("then") ||
            lowerRequest.contains("after") || lowerRequest.contains("然后") ||
            lowerRequest.contains("之后")) {
            return TaskIntent.COMBINE;
        }
        
        return TaskIntent.UNKNOWN;
    }
    
    private TaskComplexity determineComplexity(String request) {
        String lowerRequest = request.toLowerCase();
        int complexityScore = 0;
        
        if (lowerRequest.contains("and") || lowerRequest.contains("then") ||
            lowerRequest.contains("after") || lowerRequest.contains("然后")) {
            complexityScore += 2;
        }
        
        if (lowerRequest.contains("analyze") || lowerRequest.contains("create") ||
            lowerRequest.contains("generate") || lowerRequest.contains("分析") ||
            lowerRequest.contains("创建") || lowerRequest.contains("生成")) {
            complexityScore += 1;
        }
        
        if (lowerRequest.contains("multiple") || lowerRequest.contains("several") ||
            lowerRequest.contains("various") || lowerRequest.contains("多个") ||
            lowerRequest.contains("各种")) {
            complexityScore += 2;
        }
        
        if (lowerRequest.length() > 200) {
            complexityScore += 1;
        }
        
        if (complexityScore <= 1) {
            return TaskComplexity.SIMPLE;
        } else if (complexityScore <= 3) {
            return TaskComplexity.MODERATE;
        } else if (complexityScore <= 5) {
            return TaskComplexity.COMPLEX;
        } else {
            return TaskComplexity.VERY_COMPLEX;
        }
    }
    
    private String generateSummary(String request) {
        if (request.length() <= 100) {
            return request;
        }
        return request.substring(0, 100) + "...";
    }
    
    private int estimateSteps(String request) {
        TaskComplexity complexity = determineComplexity(request);
        switch (complexity) {
            case SIMPLE:
                return 1;
            case MODERATE:
                return 3;
            case COMPLEX:
                return 7;
            case VERY_COMPLEX:
                return 12;
            default:
                return 1;
        }
    }
    
    private boolean checkMultipleTools(String request) {
        TaskComplexity complexity = determineComplexity(request);
        return complexity != TaskComplexity.SIMPLE;
    }
}
