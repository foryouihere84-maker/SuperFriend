package com.superfriend.superfriend.agent.tool;

import org.springframework.stereotype.Component;
import java.util.Map;

@Component
public class DataAnalysisTool implements Tool {
    
    @Override
    public String getName() {
        return "data_analysis";
    }
    
    @Override
    public String getDescription() {
        return "Analyze data and generate insights";
    }
    
    @Override
    public String getCategory() {
        return "analysis";
    }
    
    @Override
    public double getCost() {
        return 0.2;
    }
    
    @Override
    public double getReliability() {
        return 0.85;
    }
    
    @Override
    public ToolResult execute(Map<String, Object> parameters) {
        String data = (String) parameters.get("data");
        if (data == null || data.isEmpty()) {
            return ToolResult.failure("Data parameter is required", 0);
        }
        
        long startTime = System.currentTimeMillis();
        
        try {
            Thread.sleep(800);
            
            String result = "Analysis results for data: " + data;
            long executionTime = System.currentTimeMillis() - startTime;
            
            return ToolResult.success(result, executionTime);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            long executionTime = System.currentTimeMillis() - startTime;
            return ToolResult.failure("Analysis interrupted", executionTime);
        }
    }
    
    @Override
    public boolean validateParameters(Map<String, Object> parameters) {
        return parameters.containsKey("data") && 
               parameters.get("data") instanceof String &&
               !((String) parameters.get("data")).isEmpty();
    }
}
