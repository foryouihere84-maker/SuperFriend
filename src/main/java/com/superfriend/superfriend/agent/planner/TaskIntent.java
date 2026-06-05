package com.superfriend.superfriend.agent.planner;

public enum TaskIntent {
    SEARCH("Search for information"),
    ANALYZE("Analyze data or content"),
    CREATE("Create new content"),
    MODIFY("Modify existing content"),
    COMBINE("Combine multiple operations"),
    UNKNOWN("Unknown intent");
    
    private final String description;
    
    TaskIntent(String description) {
        this.description = description;
    }
    
    public String getDescription() { return description; }
}
