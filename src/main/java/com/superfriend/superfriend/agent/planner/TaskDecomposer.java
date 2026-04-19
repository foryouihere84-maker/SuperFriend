package com.superfriend.superfriend.agent.planner;

import org.springframework.stereotype.Component;

@Component
public class TaskDecomposer {
    
    public TaskPlan decompose(TaskAnalysis analysis) {
        TaskPlan plan = new TaskPlan(analysis.getTaskId(), analysis.getOriginalRequest());
        
        switch (analysis.getComplexity()) {
            case SIMPLE:
                decomposeSimpleTask(analysis, plan);
                break;
            case MODERATE:
                decomposeModerateTask(analysis, plan);
                break;
            case COMPLEX:
                decomposeComplexTask(analysis, plan);
                break;
            case VERY_COMPLEX:
                decomposeVeryComplexTask(analysis, plan);
                break;
        }
        
        return plan;
    }
    
    private void decomposeSimpleTask(TaskAnalysis analysis, TaskPlan plan) {
        SubTask subTask = createSubTask("subtask_1", analysis.getOriginalRequest());
        assignToolToSubTask(subTask, analysis.getIntent());
        plan.addSubTask(subTask);
    }
    
    private void decomposeModerateTask(TaskAnalysis analysis, TaskPlan plan) {
        String[] parts = splitRequest(analysis.getOriginalRequest());
        
        for (int i = 0; i < parts.length; i++) {
            SubTask subTask = createSubTask("subtask_" + (i + 1), parts[i]);
            assignToolToSubTask(subTask, analysis.getIntent());
            
            if (i > 0) {
                subTask.addDependency("subtask_" + i);
            }
            
            plan.addSubTask(subTask);
        }
    }
    
    private void decomposeComplexTask(TaskAnalysis analysis, TaskPlan plan) {
        SubTask analysisTask = createSubTask("subtask_analysis", 
            "Analyze the request: " + analysis.getOriginalRequest());
        analysisTask.setAssignedTool("data_analysis");
        plan.addSubTask(analysisTask);
        
        SubTask searchTask = createSubTask("subtask_search", 
            "Search for relevant information");
        searchTask.setAssignedTool("web_search");
        searchTask.addDependency("subtask_analysis");
        plan.addSubTask(searchTask);
        
        SubTask processTask = createSubTask("subtask_process", 
            "Process the search results");
        processTask.setAssignedTool("data_analysis");
        processTask.addDependency("subtask_search");
        plan.addSubTask(processTask);
        
        SubTask synthesisTask = createSubTask("subtask_synthesis", 
            "Synthesize final answer");
        synthesisTask.setAssignedTool("data_analysis");
        synthesisTask.addDependency("subtask_process");
        plan.addSubTask(synthesisTask);
    }
    
    private void decomposeVeryComplexTask(TaskAnalysis analysis, TaskPlan plan) {
        SubTask planningTask = createSubTask("subtask_planning", 
            "Create detailed execution plan");
        planningTask.setAssignedTool("data_analysis");
        plan.addSubTask(planningTask);
        
        SubTask researchTask = createSubTask("subtask_research", 
            "Conduct comprehensive research");
        researchTask.setAssignedTool("web_search");
        researchTask.addDependency("subtask_planning");
        plan.addSubTask(researchTask);
        
        SubTask analysisTask = createSubTask("subtask_analysis", 
            "Analyze research findings");
        analysisTask.setAssignedTool("data_analysis");
        analysisTask.addDependency("subtask_research");
        plan.addSubTask(analysisTask);
        
        SubTask evaluationTask = createSubTask("subtask_evaluation", 
            "Evaluate different approaches");
        evaluationTask.setAssignedTool("data_analysis");
        evaluationTask.addDependency("subtask_analysis");
        plan.addSubTask(evaluationTask);
        
        SubTask synthesisTask = createSubTask("subtask_synthesis", 
            "Synthesize comprehensive solution");
        synthesisTask.setAssignedTool("data_analysis");
        synthesisTask.addDependency("subtask_evaluation");
        plan.addSubTask(synthesisTask);
        
        SubTask validationTask = createSubTask("subtask_validation", 
            "Validate the solution");
        validationTask.setAssignedTool("data_analysis");
        validationTask.addDependency("subtask_synthesis");
        plan.addSubTask(validationTask);
    }
    
    private SubTask createSubTask(String subTaskId, String description) {
        return new SubTask(subTaskId, description);
    }
    
    private void assignToolToSubTask(SubTask subTask, TaskIntent intent) {
        switch (intent) {
            case SEARCH:
                subTask.setAssignedTool("web_search");
                break;
            case ANALYZE:
                subTask.setAssignedTool("data_analysis");
                break;
            case CREATE:
            case MODIFY:
                subTask.setAssignedTool("data_analysis");
                break;
            case COMBINE:
                subTask.setAssignedTool("data_analysis");
                break;
            default:
                subTask.setAssignedTool("web_search");
        }
    }
    
    private String[] splitRequest(String request) {
        String[] separators = {" and ", " then ", " after ", " 然后 ", " 之后 "};
        String result = request;
        
        for (String separator : separators) {
            result = result.replace(separator, "|");
        }
        
        return result.split("\\|");
    }
}
