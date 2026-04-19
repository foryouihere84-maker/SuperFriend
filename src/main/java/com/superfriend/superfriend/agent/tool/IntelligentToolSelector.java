package com.superfriend.superfriend.agent.tool;

import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class IntelligentToolSelector implements ToolSelector {
    
    private final ToolRegistry toolRegistry;
    
    public IntelligentToolSelector(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }
    
    @Override
    public List<Tool> selectTools(String userRequest, TaskContext context) {
        List<Tool> allTools = toolRegistry.getAllTools();
        
        Map<Tool, Double> scores = new HashMap<>();
        for (Tool tool : allTools) {
            double score = calculateToolScore(tool, userRequest, context);
            scores.put(tool, score);
        }
        
        return scores.entrySet().stream()
            .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
            .limit(5)
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
    }
    
    @Override
    public Tool selectBestTool(List<Tool> candidates, Map<String, Object> parameters) {
        if (candidates.isEmpty()) {
            return null;
        }
        
        return candidates.stream()
            .max(Comparator.comparingDouble(tool -> {
                ToolMetadata metadata = toolRegistry.getMetadata(tool.getName());
                if (metadata == null) {
                    return 0.0;
                }
                return metadata.getReliability() / (metadata.getCost() + 1);
            }))
            .orElse(null);
    }
    
    private double calculateToolScore(Tool tool, String userRequest, TaskContext context) {
        double score = 0.0;
        
        score += calculateRelevanceScore(tool, userRequest);
        score += calculateReliabilityScore(tool);
        score += calculateCostScore(tool);
        score += calculateHistoryScore(tool, context);
        
        return score;
    }
    
    private double calculateRelevanceScore(Tool tool, String userRequest) {
        String lowerRequest = userRequest.toLowerCase();
        String lowerName = tool.getName().toLowerCase();
        String lowerDesc = tool.getDescription().toLowerCase();
        String lowerCategory = tool.getCategory().toLowerCase();
        
        double score = 0.0;
        
        if (lowerRequest.contains(lowerName)) {
            score += 0.4;
        }
        
        String[] keywords = lowerDesc.split("\\s+");
        for (String keyword : keywords) {
            if (lowerRequest.contains(keyword)) {
                score += 0.1;
            }
        }
        
        if (lowerRequest.contains(lowerCategory)) {
            score += 0.2;
        }
        
        return Math.min(score, 1.0);
    }
    
    private double calculateReliabilityScore(Tool tool) {
        ToolMetadata metadata = toolRegistry.getMetadata(tool.getName());
        if (metadata == null) {
            return 0.5;
        }
        return metadata.getReliability() * 0.3;
    }
    
    private double calculateCostScore(Tool tool) {
        double cost = tool.getCost();
        return Math.max(0, 0.2 - cost * 0.05);
    }
    
    private double calculateHistoryScore(Tool tool, TaskContext context) {
        return context.getToolScore(tool.getName()) * 0.2;
    }
}
