package com.superfriend.superfriend.agent.planner;

public enum TaskStatus {
    PENDING("Waiting to start"),
    IN_PROGRESS("Currently executing"),
    COMPLETED("Successfully completed"),
    FAILED("Failed to complete"),
    CANCELLED("Cancelled by user");
    
    private final String description;
    
    TaskStatus(String description) {
        this.description = description;
    }
    
    public String getDescription() { return description; }
}
