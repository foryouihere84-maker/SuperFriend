package com.superfriend.superfriend.agent.error;

public class RecoveryResult {
    private boolean success;
    private String message;
    private long retryDelay;
    private RecoveryAction action;
    
    private RecoveryResult(boolean success, String message, long retryDelay, RecoveryAction action) {
        this.success = success;
        this.message = message;
        this.retryDelay = retryDelay;
        this.action = action;
    }
    
    public static RecoveryResult success(String message) {
        return new RecoveryResult(true, message, 0, RecoveryAction.CONTINUE);
    }
    
    public static RecoveryResult failure(String message) {
        return new RecoveryResult(false, message, 0, RecoveryAction.ABORT);
    }
    
    public static RecoveryResult retry(String message, long delay) {
        return new RecoveryResult(false, message, delay, RecoveryAction.RETRY);
    }
    
    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
    public long getRetryDelay() { return retryDelay; }
    public RecoveryAction getAction() { return action; }
}

enum RecoveryAction {
    CONTINUE("Continue execution"),
    RETRY("Retry the operation"),
    ABORT("Abort the operation"),
    FALLBACK("Use fallback mechanism");
    
    private final String description;
    
    RecoveryAction(String description) {
        this.description = description;
    }
    
    public String getDescription() { return description; }
}
