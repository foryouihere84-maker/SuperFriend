package com.superfriend.superfriend.agent;

import com.superfriend.superfriend.agent.error.ErrorRecoveryManager;
import com.superfriend.superfriend.agent.executor.ToolExecutor;
import com.superfriend.superfriend.agent.planner.*;
import com.superfriend.superfriend.agent.tool.*;
import com.superfriend.superfriend.agent.planner.*;
import com.superfriend.superfriend.agent.retry.RetryStrategy;
import com.superfriend.superfriend.agent.tool.*;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class IntelligentAgentService {
    
    private final TaskAnalyzer taskAnalyzer;
    private final TaskDecomposer taskDecomposer;
    private final TaskExecutor taskExecutor;
    private final ToolSelector toolSelector;
    private final ToolRegistry toolRegistry;
    private final ErrorRecoveryManager errorRecoveryManager;
    
    public IntelligentAgentService(TaskAnalyzer taskAnalyzer,
                                    TaskDecomposer taskDecomposer,
                                    TaskExecutor taskExecutor,
                                    ToolSelector toolSelector,
                                    ToolRegistry toolRegistry,
                                    ErrorRecoveryManager errorRecoveryManager) {
        this.taskAnalyzer = taskAnalyzer;
        this.taskDecomposer = taskDecomposer;
        this.taskExecutor = taskExecutor;
        this.toolSelector = toolSelector;
        this.toolRegistry = toolRegistry;
        this.errorRecoveryManager = errorRecoveryManager;
    }
    
    public Map<String, Object> processRequest(String userRequest) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            TaskAnalysis analysis = taskAnalyzer.analyze(userRequest);
            result.put("analysis", analysis);
            
            TaskPlan plan = taskDecomposer.decompose(analysis);
            result.put("plan", plan);
            
            Map<String, Object> executionResult = taskExecutor.executeTask(plan);
            result.putAll(executionResult);
            
            return result;
            
        } catch (Exception e) {
            result.put("error", e.getMessage());
            result.put("success", false);
            return result;
        }
    }
    
    public List<Tool> suggestTools(String userRequest) {
        TaskContext context = new TaskContext();
        return toolSelector.selectTools(userRequest, context);
    }
    
    public Tool getTool(String toolName) {
        return toolRegistry.getTool(toolName);
    }
    
    public List<Tool> getAllTools() {
        return toolRegistry.getAllTools();
    }
    
    public Map<String, Object> executeTool(String toolName, Map<String, Object> parameters) {
        Tool tool = toolRegistry.getTool(toolName);
        if (tool == null) {
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("error", "Tool not found: " + toolName);
            return result;
        }
        
        ToolExecutor executor = new ToolExecutor(toolRegistry, 
            new RetryStrategy());
        ToolResult toolResult = executor.executeTool(tool, parameters);
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", toolResult.isSuccess());
        result.put("data", toolResult.getData());
        result.put("error", toolResult.getError());
        result.put("executionTime", toolResult.getExecutionTime());
        
        return result;
    }
}
