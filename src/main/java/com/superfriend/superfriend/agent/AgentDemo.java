package com.superfriend.superfriend.agent;

import com.superfriend.superfriend.agent.planner.*;
import com.superfriend.superfriend.agent.tool.*;
import com.superfriend.superfriend.agent.planner.TaskAnalysis;
import com.superfriend.superfriend.agent.planner.TaskPlan;
import com.superfriend.superfriend.agent.tool.Tool;
import com.superfriend.superfriend.agent.tool.ToolRegistry;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class AgentDemo implements CommandLineRunner {
    
    private final IntelligentAgentService agentService;
    private final ToolRegistry toolRegistry;
    
    public AgentDemo(IntelligentAgentService agentService, ToolRegistry toolRegistry) {
        this.agentService = agentService;
        this.toolRegistry = toolRegistry;
    }
    
    @Override
    public void run(String... args) throws Exception {
        System.out.println("=== Intelligent Agent Demo ===\n");
        
        demoToolRegistration();
        demoToolSelection();
        demoTaskPlanning();
        demoToolExecution();
        demoErrorHandling();
        
        System.out.println("\n=== Demo Complete ===");
    }
    
    private void demoToolRegistration() {
        System.out.println("1. Tool Registration Demo");
        System.out.println("   Registered tools:");
        
        for (Tool tool : toolRegistry.getAllTools()) {
            System.out.println("   - " + tool.getName() + " (" + tool.getCategory() + ")");
        }
        System.out.println();
    }
    
    private void demoToolSelection() {
        System.out.println("2. Intelligent Tool Selection Demo");
        
        String request = "I need to search for information about AI";
        List<Tool> suggestedTools = agentService.suggestTools(request);
        
        System.out.println("   Request: " + request);
        System.out.println("   Suggested tools:");
        for (Tool tool : suggestedTools) {
            System.out.println("   - " + tool.getName() + ": " + tool.getDescription());
        }
        System.out.println();
    }
    
    private void demoTaskPlanning() {
        System.out.println("3. Task Planning Demo");
        
        String[] requests = {
            "Search for information",
            "Search for information and then analyze the results",
            "Search for information, analyze the results, and create a summary"
        };
        
        for (String request : requests) {
            System.out.println("   Request: " + request);
            
            Map<String, Object> result = agentService.processRequest(request);
            TaskAnalysis analysis = (TaskAnalysis) result.get("analysis");
            TaskPlan plan = (TaskPlan) result.get("plan");
            
            System.out.println("   Intent: " + analysis.getIntent());
            System.out.println("   Complexity: " + analysis.getComplexity());
            System.out.println("   Estimated Steps: " + analysis.getEstimatedSteps());
            System.out.println("   Subtasks: " + plan.getSubTasks().size());
            System.out.println();
        }
    }
    
    private void demoToolExecution() {
        System.out.println("4. Tool Execution Demo");
        
        Map<String, Object> params = new HashMap<>();
        params.put("query", "artificial intelligence");
        
        System.out.println("   Executing web_search tool...");
        Map<String, Object> result = agentService.executeTool("web_search", params);
        
        System.out.println("   Success: " + result.get("success"));
        System.out.println("   Data: " + result.get("data"));
        System.out.println("   Execution Time: " + result.get("executionTime") + "ms");
        System.out.println();
    }
    
    private void demoErrorHandling() {
        System.out.println("5. Error Handling Demo");
        
        Map<String, Object> invalidParams = new HashMap<>();
        
        System.out.println("   Attempting to execute with invalid parameters...");
        Map<String, Object> result = agentService.executeTool("web_search", invalidParams);
        
        System.out.println("   Success: " + result.get("success"));
        System.out.println("   Error: " + result.get("error"));
        System.out.println();
    }
}
