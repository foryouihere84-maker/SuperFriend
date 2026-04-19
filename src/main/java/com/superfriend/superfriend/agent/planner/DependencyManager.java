package com.superfriend.superfriend.agent.planner;

import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class DependencyManager {
    
    public List<List<SubTask>> findParallelizableSubTasks(TaskPlan plan) {
        List<List<SubTask>> parallelGroups = new ArrayList<>();
        Set<String> processed = new HashSet<>();
        
        while (processed.size() < plan.getSubTasks().size()) {
            List<SubTask> readyGroup = new ArrayList<>();
            
            for (SubTask subTask : plan.getSubTasks()) {
                if (processed.contains(subTask.getSubTaskId())) {
                    continue;
                }
                
                if (canExecute(subTask, processed, plan)) {
                    readyGroup.add(subTask);
                }
            }
            
            if (!readyGroup.isEmpty()) {
                parallelGroups.add(readyGroup);
                for (SubTask subTask : readyGroup) {
                    processed.add(subTask.getSubTaskId());
                }
            } else {
                break;
            }
        }
        
        return parallelGroups;
    }
    
    private boolean canExecute(SubTask subTask, Set<String> processed, TaskPlan plan) {
        for (String dep : subTask.getDependencies()) {
            if (!processed.contains(dep)) {
                return false;
            }
        }
        return true;
    }
    
    public List<String> topologicalSort(TaskPlan plan) {
        Map<String, Integer> inDegree = new HashMap<>();
        Map<String, List<String>> graph = new HashMap<>();
        
        for (SubTask subTask : plan.getSubTasks()) {
            String taskId = subTask.getSubTaskId();
            inDegree.put(taskId, 0);
            graph.put(taskId, new ArrayList<>());
        }
        
        for (SubTask subTask : plan.getSubTasks()) {
            String taskId = subTask.getSubTaskId();
            for (String dep : subTask.getDependencies()) {
                graph.get(dep).add(taskId);
                inDegree.put(taskId, inDegree.get(taskId) + 1);
            }
        }
        
        List<String> sorted = new ArrayList<>();
        Queue<String> queue = new LinkedList<>();
        
        for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.offer(entry.getKey());
            }
        }
        
        while (!queue.isEmpty()) {
            String current = queue.poll();
            sorted.add(current);
            
            for (String neighbor : graph.get(current)) {
                inDegree.put(neighbor, inDegree.get(neighbor) - 1);
                if (inDegree.get(neighbor) == 0) {
                    queue.offer(neighbor);
                }
            }
        }
        
        return sorted;
    }
    
    public boolean hasCycles(TaskPlan plan) {
        Set<String> visited = new HashSet<>();
        Set<String> recursionStack = new HashSet<>();
        
        for (SubTask subTask : plan.getSubTasks()) {
            if (hasCyclesDFS(subTask.getSubTaskId(), visited, recursionStack, plan)) {
                return true;
            }
        }
        
        return false;
    }
    
    private boolean hasCyclesDFS(String taskId, Set<String> visited, 
                                   Set<String> recursionStack, TaskPlan plan) {
        if (recursionStack.contains(taskId)) {
            return true;
        }
        
        if (visited.contains(taskId)) {
            return false;
        }
        
        visited.add(taskId);
        recursionStack.add(taskId);
        
        SubTask subTask = plan.getSubTask(taskId);
        if (subTask != null) {
            for (String dep : subTask.getDependencies()) {
                if (hasCyclesDFS(dep, visited, recursionStack, plan)) {
                    return true;
                }
            }
        }
        
        recursionStack.remove(taskId);
        return false;
    }
}
