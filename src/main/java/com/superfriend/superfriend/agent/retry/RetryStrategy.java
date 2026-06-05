package com.superfriend.superfriend.agent.retry;

import com.superfriend.superfriend.agent.tool.ToolResult;
import org.springframework.stereotype.Component;

@Component
public class RetryStrategy {
    
    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_DELAY_MS = 1000;
    
    public ToolResult executeWithRetry(RetryableOperation operation, String toolName) {
        int attempt = 0;
        Exception lastException = null;
        
        while (attempt < MAX_RETRIES) {
            try {
                ToolResult result = operation.execute();
                
                if (result.isSuccess()) {
                    return result;
                }
                
                if (shouldRetry(result.getError(), attempt)) {
                    attempt++;
                    if (attempt < MAX_RETRIES) {
                        long delay = calculateDelay(attempt);
                        Thread.sleep(delay);
                    }
                } else {
                    return result;
                }
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return ToolResult.failure("Operation interrupted", 0);
            } catch (Exception e) {
                lastException = e;
                attempt++;
                if (attempt < MAX_RETRIES) {
                    long delay = calculateDelay(attempt);
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return ToolResult.failure("Operation interrupted", 0);
                    }
                }
            }
        }
        
        if (lastException != null) {
            return ToolResult.failure("Failed after " + MAX_RETRIES + " attempts: " + 
                lastException.getMessage(), 0);
        }
        
        return ToolResult.failure("Failed after " + MAX_RETRIES + " attempts", 0);
    }
    
    private boolean shouldRetry(String error, int attempt) {
        if (error == null) {
            return false;
        }
        
        String lowerError = error.toLowerCase();
        
        if (lowerError.contains("timeout") || 
            lowerError.contains("network") ||
            lowerError.contains("connection") ||
            lowerError.contains("temporary") ||
            lowerError.contains("rate limit") ||
            lowerError.contains("503") ||
            lowerError.contains("502")) {
            return true;
        }
        
        if (attempt < MAX_RETRIES && 
            (lowerError.contains("500") || lowerError.contains("504"))) {
            return true;
        }
        
        return false;
    }
    
    private long calculateDelay(int attempt) {
        return INITIAL_DELAY_MS * (long) Math.pow(2, attempt - 1);
    }
}
