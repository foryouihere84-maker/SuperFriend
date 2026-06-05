package com.superfriend.superfriend.agent.executor;

import com.superfriend.superfriend.agent.retry.RetryStrategy;
import com.superfriend.superfriend.agent.tool.*;
<<<<<<< HEAD
=======
import com.superfriend.superfriend.agent.tool.TaskContext;
import com.superfriend.superfriend.agent.tool.Tool;
import com.superfriend.superfriend.agent.tool.ToolRegistry;
import com.superfriend.superfriend.agent.tool.ToolResult;
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class ToolExecutor {
<<<<<<< HEAD

    private final ToolRegistry toolRegistry;
    private final RetryStrategy retryStrategy;

=======
    
    private final ToolRegistry toolRegistry;
    private final RetryStrategy retryStrategy;
    
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
    public ToolExecutor(ToolRegistry toolRegistry, RetryStrategy retryStrategy) {
        this.toolRegistry = toolRegistry;
        this.retryStrategy = retryStrategy;
    }
<<<<<<< HEAD

=======
    
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
    public ToolResult executeTool(Tool tool, Map<String, Object> parameters) {
        if (!tool.validateParameters(parameters)) {
            return ToolResult.failure("Invalid parameters", 0);
        }
<<<<<<< HEAD

=======
        
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
        return retryStrategy.executeWithRetry(() -> {
            long startTime = System.currentTimeMillis();
            ToolResult result = tool.execute(parameters);
            long executionTime = System.currentTimeMillis() - startTime;
<<<<<<< HEAD

            toolRegistry.updateToolReliability(tool.getName(), result.isSuccess());

            return result;
        }, tool.getName());
    }
=======
            
            toolRegistry.updateToolReliability(tool.getName(), result.isSuccess());
            
            return result;
        }, tool.getName());
    }
    
    public Map<String, ToolResult> executePlan(ExecutionPlan plan, TaskContext context) {
        Map<String, ToolResult> results = new HashMap<>();
        
        for (ExecutionPlan.ExecutionStep step : plan.getSteps()) {
            Map<String, Object> parameters = new HashMap<>(step.getParameters());
            
            for (String dep : step.getDependencies()) {
                ToolResult depResult = results.get(dep);
                if (depResult != null && depResult.isSuccess()) {
                    context.setVariable(dep, depResult.getData());
                }
            }
            
            ToolResult result = executeTool(step.getTool(), parameters);
            results.put(step.getTool().getName(), result);
            
            if (!result.isSuccess()) {
                break;
            }
        }
        
        return results;
    }
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
}
