package com.superfriend.superfriend.agent.error;

import java.util.Map;

public interface RecoveryStrategy {
    RecoveryResult recover(ErrorDiagnosis diagnosis, Map<String, Object> context);
}

class NetworkRecoveryStrategy implements RecoveryStrategy {
    @Override
    public RecoveryResult recover(ErrorDiagnosis diagnosis, Map<String, Object> context) {
        int attempt = context.containsKey("attempt") ? 
            (int) context.get("attempt") : 0;
        
        if (attempt < 3) {
            long delay = 1000 * (long) Math.pow(2, attempt);
            return RecoveryResult.retry("Network error detected, retrying after " + 
                delay + "ms", delay);
        }
        
        return RecoveryResult.failure("Network error persists after maximum retries");
    }
}

class TimeoutRecoveryStrategy implements RecoveryStrategy {
    @Override
    public RecoveryResult recover(ErrorDiagnosis diagnosis, Map<String, Object> context) {
        int attempt = context.containsKey("attempt") ? 
            (int) context.get("attempt") : 0;
        
        if (attempt < 3) {
            long delay = 2000 * (long) Math.pow(2, attempt);
            return RecoveryResult.retry("Timeout detected, retrying with increased timeout", delay);
        }
        
        return RecoveryResult.failure("Operation timed out after maximum retries");
    }
}

class AuthenticationRecoveryStrategy implements RecoveryStrategy {
    @Override
    public RecoveryResult recover(ErrorDiagnosis diagnosis, Map<String, Object> context) {
        return RecoveryResult.failure("Authentication failed, please refresh credentials");
    }
}

class RateLimitRecoveryStrategy implements RecoveryStrategy {
    @Override
    public RecoveryResult recover(ErrorDiagnosis diagnosis, Map<String, Object> context) {
        int attempt = context.containsKey("attempt") ? 
            (int) context.get("attempt") : 0;
        
        if (attempt < 5) {
            long delay = 5000 * attempt;
            return RecoveryResult.retry("Rate limit exceeded, waiting " + 
                delay + "ms before retry", delay);
        }
        
        return RecoveryResult.failure("Rate limit exceeded after maximum retries");
    }
}

class ServerErrorRecoveryStrategy implements RecoveryStrategy {
    @Override
    public RecoveryResult recover(ErrorDiagnosis diagnosis, Map<String, Object> context) {
        int attempt = context.containsKey("attempt") ? 
            (int) context.get("attempt") : 0;
        
        if (attempt < 2) {
            long delay = 3000 * attempt;
            return RecoveryResult.retry("Server error detected, retrying after " + 
                delay + "ms", delay);
        }
        
        return RecoveryResult.failure("Server error persists after maximum retries");
    }
}
