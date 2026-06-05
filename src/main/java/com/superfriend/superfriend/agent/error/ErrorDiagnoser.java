package com.superfriend.superfriend.agent.error;

import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class ErrorDiagnoser {
    
    private final ErrorClassifier classifier;
    
    public ErrorDiagnoser(ErrorClassifier classifier) {
        this.classifier = classifier;
    }
    
    public ErrorDiagnosis diagnose(String errorMessage, String componentName, 
                                     Map<String, Object> context) {
        ErrorType errorType = classifier.classifyError(errorMessage, componentName);
        ErrorRecord record = new ErrorRecord(errorType, errorMessage, componentName);
        
        ErrorDiagnosis diagnosis = new ErrorDiagnosis(record);
        diagnosis.setSeverity(determineSeverity(errorType));
        diagnosis.setRootCause(identifyRootCause(errorType, errorMessage, context));
        diagnosis.setRecoveryStrategies(suggestRecoveryStrategies(errorType));
        diagnosis.setPreventionMeasures(suggestPreventionMeasures(errorType));
        
        return diagnosis;
    }
    
    private ErrorSeverity determineSeverity(ErrorType errorType) {
        switch (errorType) {
            case NETWORK_ERROR:
            case TIMEOUT_ERROR:
                return ErrorSeverity.MEDIUM;
            case AUTHENTICATION_ERROR:
            case AUTHORIZATION_ERROR:
            case VALIDATION_ERROR:
                return ErrorSeverity.HIGH;
            case RATE_LIMIT_ERROR:
            case SERVER_ERROR:
                return ErrorSeverity.MEDIUM;
            case RESOURCE_NOT_FOUND:
                return ErrorSeverity.LOW;
            case TOOL_ERROR:
                return ErrorSeverity.HIGH;
            default:
                return ErrorSeverity.MEDIUM;
        }
    }
    
    private String identifyRootCause(ErrorType errorType, String errorMessage, 
                                      Map<String, Object> context) {
        switch (errorType) {
            case NETWORK_ERROR:
                return "Network connectivity issue or DNS resolution failure";
            case TIMEOUT_ERROR:
                return "Operation exceeded timeout threshold";
            case AUTHENTICATION_ERROR:
                return "Invalid or expired credentials";
            case AUTHORIZATION_ERROR:
                return "Insufficient permissions for the requested operation";
            case VALIDATION_ERROR:
                return "Input data does not meet validation requirements";
            case RESOURCE_NOT_FOUND:
                return "Requested resource does not exist or has been removed";
            case RATE_LIMIT_ERROR:
                return "API rate limit exceeded";
            case SERVER_ERROR:
                return "Internal server error or service unavailable";
            case TOOL_ERROR:
                return "Tool execution failed due to invalid parameters or tool malfunction";
            default:
                return "Unknown error cause";
        }
    }
    
    private List<String> suggestRecoveryStrategies(ErrorType errorType) {
        List<String> strategies = new ArrayList<>();
        
        switch (errorType) {
            case NETWORK_ERROR:
                strategies.add("Retry the operation after a short delay");
                strategies.add("Check network connectivity");
                strategies.add("Try using a different network connection");
                break;
            case TIMEOUT_ERROR:
                strategies.add("Increase timeout threshold");
                strategies.add("Retry with exponential backoff");
                strategies.add("Check if the service is overloaded");
                break;
            case AUTHENTICATION_ERROR:
                strategies.add("Refresh authentication credentials");
                strategies.add("Verify API key or token validity");
                strategies.add("Re-authenticate with the service");
                break;
            case AUTHORIZATION_ERROR:
                strategies.add("Check user permissions");
                strategies.add("Request necessary permissions");
                strategies.add("Use an account with higher privileges");
                break;
            case VALIDATION_ERROR:
                strategies.add("Review and correct input parameters");
                strategies.add("Validate input data format");
                strategies.add("Check API documentation for requirements");
                break;
            case RESOURCE_NOT_FOUND:
                strategies.add("Verify resource identifier");
                strategies.add("Check if resource exists");
                strategies.add("Use alternative resource if available");
                break;
            case RATE_LIMIT_ERROR:
                strategies.add("Wait and retry after rate limit resets");
                strategies.add("Reduce request frequency");
                strategies.add("Implement request throttling");
                break;
            case SERVER_ERROR:
                strategies.add("Retry the operation");
                strategies.add("Check service status page");
                strategies.add("Use fallback service if available");
                break;
            case TOOL_ERROR:
                strategies.add("Verify tool parameters");
                strategies.add("Check tool availability");
                strategies.add("Try alternative tool");
                break;
            default:
                strategies.add("Retry the operation");
                strategies.add("Check system logs");
                strategies.add("Contact support if issue persists");
        }
        
        return strategies;
    }
    
    private List<String> suggestPreventionMeasures(ErrorType errorType) {
        List<String> measures = new ArrayList<>();
        
        switch (errorType) {
            case NETWORK_ERROR:
                measures.add("Implement network health checks");
                measures.add("Use connection pooling");
                measures.add("Add circuit breaker pattern");
                break;
            case TIMEOUT_ERROR:
                measures.add("Set appropriate timeout values");
                measures.add("Implement retry with exponential backoff");
                measures.add("Monitor service response times");
                break;
            case AUTHENTICATION_ERROR:
                measures.add("Implement credential refresh mechanism");
                measures.add("Monitor credential expiration");
                measures.add("Use secure credential storage");
                break;
            case AUTHORIZATION_ERROR:
                measures.add("Implement proper permission checks");
                measures.add("Use role-based access control");
                measures.add("Audit user permissions regularly");
                break;
            case VALIDATION_ERROR:
                measures.add("Implement input validation");
                measures.add("Use schema validation");
                measures.add("Provide clear error messages");
                break;
            case RESOURCE_NOT_FOUND:
                measures.add("Implement resource existence checks");
                measures.add("Use caching for frequently accessed resources");
                measures.add("Provide fallback mechanisms");
                break;
            case RATE_LIMIT_ERROR:
                measures.add("Implement rate limiting");
                measures.add("Use request queuing");
                measures.add("Monitor API usage");
                break;
            case SERVER_ERROR:
                measures.add("Implement circuit breaker");
                measures.add("Use multiple service instances");
                measures.add("Monitor service health");
                break;
            case TOOL_ERROR:
                measures.add("Validate tool parameters before execution");
                measures.add("Implement tool health checks");
                measures.add("Provide fallback tools");
                break;
            default:
                measures.add("Implement comprehensive error logging");
                measures.add("Add monitoring and alerting");
                measures.add("Regular system health checks");
        }
        
        return measures;
    }
}
