package com.superfriend.superfriend.agent.planner;

import com.superfriend.superfriend.agent.executor.ToolExecutor;
import com.superfriend.superfriend.agent.tool.Tool;
import com.superfriend.superfriend.agent.tool.ToolRegistry;
import com.superfriend.superfriend.agent.tool.ToolResult;
import org.springframework.stereotype.Component;
import java.util.*;

@Component("plannerTaskExecutor")
public class TaskExecutor {
    
    private final ToolExecutor toolExecutor;
    private final ToolRegistry toolRegistry;
    private final DependencyManager dependencyManager;
    
    public TaskExecutor(ToolExecutor toolExecutor, ToolRegistry toolRegistry, 
                        DependencyManager dependencyManager) {
        this.toolExecutor = toolExecutor;
        this.toolRegistry = toolRegistry;
        this.dependencyManager = dependencyManager;
    }
    
    public Map<String, Object> executeTask(TaskPlan plan) {
        plan.setStatus(TaskStatus.IN_PROGRESS);
        
        Map<String, Object> results = new HashMap<>();
        List<List<SubTask>> parallelGroups = dependencyManager.findParallelizableSubTasks(plan);
        
        for (List<SubTask> group : parallelGroups) {
            List<SubTask> completedGroup = executeParallelGroup(group, results, plan);
            
            for (SubTask subTask : completedGroup) {
                if (subTask.getStatus() == SubTaskStatus.FAILED) {
                    plan.setStatus(TaskStatus.FAILED);
                    results.put("error", "Task failed: " + subTask.getError());
                    return results;
                }
            }
        }
        
        if (plan.allSubTasksCompleted()) {
            plan.setStatus(TaskStatus.COMPLETED);
            results.put("success", true);
            results.put("message", "Task completed successfully");
        } else {
            plan.setStatus(TaskStatus.FAILED);
            results.put("error", "Task failed to complete all subtasks");
        }
        
        return results;
    }
    
    private List<SubTask> executeParallelGroup(List<SubTask> group, 
                                               Map<String, Object> results, 
                                               TaskPlan plan) {
        List<SubTask> completedGroup = new ArrayList<>();
        
        for (SubTask subTask : group) {
            subTask.setStatus(SubTaskStatus.IN_PROGRESS);
            
            Tool tool = toolRegistry.getTool(subTask.getAssignedTool());
            if (tool == null) {
                subTask.setStatus(SubTaskStatus.FAILED);
                subTask.setError("Tool not found: " + subTask.getAssignedTool());
                completedGroup.add(subTask);
                continue;
            }
            
            Map<String, Object> parameters = prepareParameters(subTask, results);
            ToolResult result = toolExecutor.executeTool(tool, parameters);
            
            if (result.isSuccess()) {
                subTask.setStatus(SubTaskStatus.COMPLETED);
                subTask.setResult(result.getData());
                results.put(subTask.getSubTaskId(), result.getData());
            } else {
                subTask.setStatus(SubTaskStatus.FAILED);
                subTask.setError(result.getError());
            }
            
            completedGroup.add(subTask);
        }
        
        return completedGroup;
    }
    
    private Map<String, Object> prepareParameters(SubTask subTask, Map<String, Object> results) {
        Map<String, Object> parameters = new HashMap<>(subTask.getParameters());
        
        for (String dep : subTask.getDependencies()) {
            if (results.containsKey(dep)) {
                parameters.put("dependency_" + dep, results.get(dep));
            }
        }
        
        return parameters;
    }
}
