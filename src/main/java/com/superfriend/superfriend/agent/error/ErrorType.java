package com.superfriend.superfriend.agent.error;

<<<<<<< HEAD
/**
 * 错误类型枚举
 *
 * 【增强版】支持更多错误类型分类
 */
public enum ErrorType {
    // 网络相关错误
    NETWORK_ERROR("Network connectivity issue", ErrorCategory.TRANSIENT),
    TIMEOUT_ERROR("Operation timed out", ErrorCategory.TRANSIENT),
    RATE_LIMIT_ERROR("Rate limit exceeded", ErrorCategory.TRANSIENT),

    // 认证授权错误
    AUTHENTICATION_ERROR("Authentication failed", ErrorCategory.PERMANENT),
    AUTHORIZATION_ERROR("Authorization denied", ErrorCategory.PERMANENT),
    PERMISSION_DENIED("Permission denied", ErrorCategory.PERMANENT),

    // 数据相关错误
    VALIDATION_ERROR("Input validation failed", ErrorCategory.RECOVERABLE),
    RESOURCE_NOT_FOUND("Requested resource not found", ErrorCategory.RECOVERABLE),
    DATA_FORMAT_ERROR("Data format error", ErrorCategory.RECOVERABLE),

    // 服务端错误
    SERVER_ERROR("Server internal error", ErrorCategory.TRANSIENT),
    SERVICE_UNAVAILABLE("Service unavailable", ErrorCategory.TRANSIENT),

    // 工具执行错误
    TOOL_ERROR("Tool execution error", ErrorCategory.RECOVERABLE),
    TOOL_NOT_FOUND("Tool not found", ErrorCategory.PERMANENT),
    TOOL_PARAMETER_ERROR("Tool parameter error", ErrorCategory.RECOVERABLE),

    // 执行相关错误
    EXECUTION_ERROR("Execution error", ErrorCategory.RECOVERABLE),
    STEP_FAILED("Step execution failed", ErrorCategory.RECOVERABLE),
    PLAN_FAILED("Plan execution failed", ErrorCategory.RECOVERABLE),

    // 未知错误
    UNKNOWN_ERROR("Unknown error", ErrorCategory.UNKNOWN);

    private final String description;
    private final ErrorCategory category;

    ErrorType(String description, ErrorCategory category) {
        this.description = description;
        this.category = category;
    }

    public String getDescription() { return description; }
    public ErrorCategory getCategory() { return category; }

    /**
     * 是否可重试
     */
    public boolean isRetryable() {
        return category == ErrorCategory.TRANSIENT || category == ErrorCategory.RECOVERABLE;
    }

    /**
     * 是否需要用户干预
     */
    public boolean needsUserIntervention() {
        return category == ErrorCategory.PERMANENT;
    }

    /**
     * 获取默认重试次数
     */
    public int getDefaultMaxRetries() {
        switch (category) {
            case TRANSIENT:
                return 3;
            case RECOVERABLE:
                return 2;
            default:
                return 0;
        }
    }
}

/**
 * 错误类别
 */
enum ErrorCategory {
    TRANSIENT("暂时性错误，可重试"),
    RECOVERABLE("可恢复错误，需要调整"),
    PERMANENT("永久性错误，需要用户干预"),
    UNKNOWN("未知类别");

    private final String description;

    ErrorCategory(String description) {
        this.description = description;
    }

=======
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
    
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
    public String getDescription() { return description; }
}
