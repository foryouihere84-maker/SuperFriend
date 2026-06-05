package com.superfriend.superfriend.agent.tool;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class ToolRegistry {
    
    private final Map<String, Tool> tools = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> categoryIndex = new ConcurrentHashMap<>();
    private final Map<String, ToolMetadata> metadataMap = new ConcurrentHashMap<>();
    
    public void register(Tool tool) {
        tools.put(tool.getName(), tool);
        
        String category = tool.getCategory();
        categoryIndex.computeIfAbsent(category, k -> ConcurrentHashMap.newKeySet()).add(tool.getName());
        
        ToolMetadata metadata = new ToolMetadata(
            tool.getName(),
            tool.getDescription(),
            tool.getCategory(),
            tool.getCost(),
            tool.getReliability()
        );
        metadataMap.put(tool.getName(), metadata);
    }
    
    public Tool getTool(String name) {
        return tools.get(name);
    }
    
    public List<Tool> getAllTools() {
        return new ArrayList<>(tools.values());
    }
    
    public List<Tool> getToolsByCategory(String category) {
        Set<String> toolNames = categoryIndex.get(category);
        if (toolNames == null) {
            return Collections.emptyList();
        }
        
        List<Tool> result = new ArrayList<>();
        for (String name : toolNames) {
            Tool tool = tools.get(name);
            if (tool != null) {
                result.add(tool);
            }
        }
        return result;
    }
    
    public Set<String> getCategories() {
        return categoryIndex.keySet();
    }
    
    public ToolMetadata getMetadata(String toolName) {
        return metadataMap.get(toolName);
    }
    
    public void updateToolReliability(String toolName, boolean success) {
        ToolMetadata metadata = metadataMap.get(toolName);
        if (metadata != null) {
            metadata.recordExecution(success);
        }
    }
    
    public List<Tool> searchTools(String keyword) {
        List<Tool> results = new ArrayList<>();
        String lowerKeyword = keyword.toLowerCase();
        
        for (Tool tool : tools.values()) {
            if (tool.getName().toLowerCase().contains(lowerKeyword) ||
                tool.getDescription().toLowerCase().contains(lowerKeyword) ||
                tool.getCategory().toLowerCase().contains(lowerKeyword)) {
                results.add(tool);
            }
        }
        return results;
    }
    
    public boolean hasTool(String name) {
        return tools.containsKey(name);
    }
}
