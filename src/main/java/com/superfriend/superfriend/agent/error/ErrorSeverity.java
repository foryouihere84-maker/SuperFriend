package com.superfriend.superfriend.agent.error;

public enum ErrorSeverity {
    LOW("Low impact, can be handled gracefully"),
    MEDIUM("Moderate impact, may affect functionality"),
    HIGH("High impact, critical functionality affected"),
    CRITICAL("Critical impact, system may be unavailable");
    
    private final String description;
    
    ErrorSeverity(String description) {
        this.description = description;
    }
    
    public String getDescription() { return description; }
}
