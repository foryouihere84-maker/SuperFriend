package com.superfriend.superfriend.agent.error;

import java.util.Date;

public class ErrorRecord {
    private String errorId;
    private ErrorType errorType;
    private String errorMessage;
    private String componentName;
    private Date timestamp;
    private int occurrenceCount;
    private boolean isRecurring;
    private String stackTrace;
    
    public ErrorRecord(ErrorType errorType, String errorMessage, String componentName) {
        this.errorId = generateErrorId();
        this.errorType = errorType;
        this.errorMessage = errorMessage;
        this.componentName = componentName;
        this.timestamp = new Date();
        this.occurrenceCount = 1;
        this.isRecurring = false;
    }
    
    private String generateErrorId() {
        return "error_" + System.currentTimeMillis() + "_" + 
               Integer.toHexString((int)(Math.random() * 10000));
    }
    
    public String getErrorId() { return errorId; }
    public ErrorType getErrorType() { return errorType; }
    public String getErrorMessage() { return errorMessage; }
    public String getComponentName() { return componentName; }
    public Date getTimestamp() { return timestamp; }
    public int getOccurrenceCount() { return occurrenceCount; }
    public boolean isRecurring() { return isRecurring; }
    public String getStackTrace() { return stackTrace; }
    
    public void incrementOccurrence() {
        this.occurrenceCount++;
        this.isRecurring = this.occurrenceCount > 1;
    }
    
    public void setStackTrace(String stackTrace) {
        this.stackTrace = stackTrace;
    }
}
