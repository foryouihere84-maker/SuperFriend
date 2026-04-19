package com.superfriend.superfriend.agent.error;

import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class ErrorRecoveryManager {
    
    private final ErrorDiagnoser diagnoser;
    private final Map<String, RecoveryStrategy> recoveryStrategies;
    
    public ErrorRecoveryManager(ErrorDiagnoser diagnoser) {
        this.diagnoser = diagnoser;
        this.recoveryStrategies = new HashMap<>();
        initializeRecoveryStrategies();
    }
    
    private void initializeRecoveryStrategies() {
        recoveryStrategies.put(ErrorType.NETWORK_ERROR.name(), new NetworkRecoveryStrategy());
        recoveryStrategies.put(ErrorType.TIMEOUT_ERROR.name(), new TimeoutRecoveryStrategy());
        recoveryStrategies.put(ErrorType.AUTHENTICATION_ERROR.name(), new AuthenticationRecoveryStrategy());
        recoveryStrategies.put(ErrorType.RATE_LIMIT_ERROR.name(), new RateLimitRecoveryStrategy());
        recoveryStrategies.put(ErrorType.SERVER_ERROR.name(), new ServerErrorRecoveryStrategy());
    }
    
    public RecoveryResult attemptRecovery(String errorMessage, String componentName, 
                                           Map<String, Object> context) {
        ErrorDiagnosis diagnosis = diagnoser.diagnose(errorMessage, componentName, context);
        RecoveryStrategy strategy = recoveryStrategies.get(diagnosis.getErrorRecord().getErrorType().name());
        
        if (strategy == null) {
            return RecoveryResult.failure("No recovery strategy available for this error type");
        }
        
        return strategy.recover(diagnosis, context);
    }
    
    public boolean shouldRetry(ErrorType errorType) {
        switch (errorType) {
            case NETWORK_ERROR:
            case TIMEOUT_ERROR:
            case RATE_LIMIT_ERROR:
            case SERVER_ERROR:
                return true;
            default:
                return false;
        }
    }
    
    public int getMaxRetryAttempts(ErrorType errorType) {
        switch (errorType) {
            case NETWORK_ERROR:
            case TIMEOUT_ERROR:
                return 3;
            case RATE_LIMIT_ERROR:
                return 5;
            case SERVER_ERROR:
                return 2;
            default:
                return 0;
        }
    }
    
    public long getRetryDelay(ErrorType errorType, int attempt) {
        switch (errorType) {
            case NETWORK_ERROR:
                return 1000 * (long) Math.pow(2, attempt);
            case TIMEOUT_ERROR:
                return 2000 * (long) Math.pow(2, attempt);
            case RATE_LIMIT_ERROR:
                return 5000 * attempt;
            case SERVER_ERROR:
                return 3000 * attempt;
            default:
                return 1000;
        }
    }
}
