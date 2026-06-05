package com.superfriend.superfriend.exception;

/**
 * LLM API 调用异常
 */
public class LLMAPIException extends RuntimeException {
    
    public LLMAPIException(String message) {
        super(message);
    }
    
    public LLMAPIException(String message, Throwable cause) {
        super(message, cause);
    }
}
