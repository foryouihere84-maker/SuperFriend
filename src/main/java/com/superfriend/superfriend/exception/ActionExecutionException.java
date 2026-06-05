package com.superfriend.superfriend.exception;

/**
 * 动作执行异常
 */
public class ActionExecutionException extends RuntimeException {
    
    public ActionExecutionException(String message) {
        super(message);
    }
    
    public ActionExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
