package com.superfriend.superfriend.agent.planner;

import java.util.*;
import java.util.stream.Collectors;

public class TaskPlan {
    private String taskId;
    private String originalRequest;
    private List<SubTask> subTasks;
    private TaskStatus status;
    private Date createdAt;
    private Date startedAt;
    private Date completedAt;
    
    public TaskPlan(String taskId, String originalRequest) {
        this.taskId = taskId;
        this.originalRequest = originalRequest;
        this.subTasks = new ArrayList<>();
        this.status = TaskStatus.PENDING;
        this.createdAt = new Date();
    }
    
    public String getTaskId() { return taskId; }
    public String getOriginalRequest() { return originalRequest; }
    public List<SubTask> getSubTasks() { return subTasks; }
    public TaskStatus getStatus() { return status; }
    public Date getCreatedAt() { return createdAt; }
    public Date getStartedAt() { return startedAt; }
    public Date getCompletedAt() { return completedAt; }
    
    public void setStatus(TaskStatus status) { 
        this.status = status;
        if (status == TaskStatus.IN_PROGRESS && startedAt == null) {
            this.startedAt = new Date();
        }
        if (status == TaskStatus.COMPLETED || status == TaskStatus.FAILED) {
            this.completedAt = new Date();
        }
    }
    
    public void addSubTask(SubTask subTask) {
        subTasks.add(subTask);
    }
    
    public SubTask getSubTask(String subTaskId) {
        return subTasks.stream()
            .filter(st -> st.getSubTaskId().equals(subTaskId))
            .findFirst()
            .orElse(null);
    }
    
    public List<SubTask> getReadySubTasks() {
        return subTasks.stream()
            .filter(st -> st.getStatus() == SubTaskStatus.PENDING)
            .filter(st -> {
                for (String dep : st.getDependencies()) {
                    SubTask depTask = getSubTask(dep);
                    if (depTask == null || depTask.getStatus() != SubTaskStatus.COMPLETED) {
                        return false;
                    }
                }
                return true;
            })
            .collect(Collectors.toList());
    }
    
    public boolean allSubTasksCompleted() {
        return subTasks.stream()
            .allMatch(st -> st.getStatus() == SubTaskStatus.COMPLETED);
    }
    
    public boolean hasFailedSubTask() {
        return subTasks.stream()
            .anyMatch(st -> st.getStatus() == SubTaskStatus.FAILED);
    }
}
