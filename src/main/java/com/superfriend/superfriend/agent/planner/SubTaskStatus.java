package com.superfriend.superfriend.agent.planner;

public enum SubTaskStatus {
    PENDING("Waiting to start"),
    IN_PROGRESS("Currently executing"),
    COMPLETED("Successfully completed"),
    FAILED("Failed to complete"),
    SKIPPED("Skipped due to dependency failure");
    
    private final String description;
    
    SubTaskStatus(String description) {
        this.description = description;
    }
    
    public String getDescription() { return description; }
}
