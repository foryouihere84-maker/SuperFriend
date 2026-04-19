package com.superfriend.superfriend.agent.error;

public enum ErrorType {
    NETWORK_ERROR("Network connectivity issue"),
    TIMEOUT_ERROR("Operation timed out"),
    AUTHENTICATION_ERROR("Authentication failed"),
    AUTHORIZATION_ERROR("Authorization denied"),
    VALIDATION_ERROR("Input validation failed"),
    RESOURCE_NOT_FOUND("Requested resource not found"),
    RATE_LIMIT_ERROR("Rate limit exceeded"),
    SERVER_ERROR("Server internal error"),
    UNKNOWN_ERROR("Unknown error"),
    TOOL_ERROR("Tool execution error");
    
    private final String description;
    
    ErrorType(String description) {
        this.description = description;
    }
    
    public String getDescription() { return description; }
}
