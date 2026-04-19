package com.superfriend.superfriend.agent.error;

import org.springframework.stereotype.Component;

@Component
public class ErrorClassifier {
    
    public ErrorType classifyError(String errorMessage, String componentName) {
        String lowerMessage = errorMessage.toLowerCase();
        
        if (isNetworkError(lowerMessage)) {
            return ErrorType.NETWORK_ERROR;
        }
        
        if (isTimeoutError(lowerMessage)) {
            return ErrorType.TIMEOUT_ERROR;
        }
        
        if (isAuthenticationError(lowerMessage)) {
            return ErrorType.AUTHENTICATION_ERROR;
        }
        
        if (isAuthorizationError(lowerMessage)) {
            return ErrorType.AUTHORIZATION_ERROR;
        }
        
        if (isValidationError(lowerMessage)) {
            return ErrorType.VALIDATION_ERROR;
        }
        
        if (isResourceNotFoundError(lowerMessage)) {
            return ErrorType.RESOURCE_NOT_FOUND;
        }
        
        if (isRateLimitError(lowerMessage)) {
            return ErrorType.RATE_LIMIT_ERROR;
        }
        
        if (isServerError(lowerMessage)) {
            return ErrorType.SERVER_ERROR;
        }
        
        if (isToolError(lowerMessage, componentName)) {
            return ErrorType.TOOL_ERROR;
        }
        
        return ErrorType.UNKNOWN_ERROR;
    }
    
    private boolean isNetworkError(String message) {
        return message.contains("network") || 
               message.contains("connection") ||
               message.contains("connect") ||
               message.contains("unreachable") ||
               message.contains("dns") ||
               message.contains("socket") ||
               message.contains("network");
    }
    
    private boolean isTimeoutError(String message) {
        return message.contains("timeout") || 
               message.contains("timed out") ||
               message.contains("time out");
    }
    
    private boolean isAuthenticationError(String message) {
        return message.contains("authentication") || 
               message.contains("auth") ||
               message.contains("unauthorized") ||
               message.contains("login") ||
               message.contains("credential");
    }
    
    private boolean isAuthorizationError(String message) {
        return message.contains("authorization") || 
               message.contains("forbidden") ||
               message.contains("permission") ||
               message.contains("access denied");
    }
    
    private boolean isValidationError(String message) {
        return message.contains("validation") || 
               message.contains("invalid") ||
               message.contains("malformed") ||
               message.contains("bad request") ||
               message.contains("400");
    }
    
    private boolean isResourceNotFoundError(String message) {
        return message.contains("not found") || 
               message.contains("404") ||
               message.contains("does not exist") ||
               message.contains("missing");
    }
    
    private boolean isRateLimitError(String message) {
        return message.contains("rate limit") || 
               message.contains("too many requests") ||
               message.contains("429");
    }
    
    private boolean isServerError(String message) {
        return message.contains("500") || 
               message.contains("502") ||
               message.contains("503") ||
               message.contains("504") ||
               message.contains("internal server error") ||
               message.contains("service unavailable");
    }
    
    private boolean isToolError(String message, String componentName) {
        return componentName != null && 
               (componentName.contains("tool") || componentName.contains("Tool")) &&
               (message.contains("execution") || message.contains("failed"));
    }
}
