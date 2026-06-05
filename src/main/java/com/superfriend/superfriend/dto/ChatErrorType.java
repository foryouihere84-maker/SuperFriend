package com.superfriend.superfriend.dto;

import lombok.Getter;

/**
 * AI 对话错误类型枚举
 * 用于分类错误并提供用户友好的错误信息
 */
@Getter
public enum ChatErrorType {

    // ============ 网络相关错误 ============
    NETWORK_ERROR("NETWORK_ERROR", "网络连接失败", "请检查网络连接后重试", true),
    TIMEOUT("TIMEOUT", "请求超时", "请求处理时间过长，请稍后重试", true),
    SERVICE_UNAVAILABLE("SERVICE_UNAVAILABLE", "服务暂时不可用", "服务正在维护，请稍后再试", true),

    // ============ 模型相关错误 ============
    MODEL_NOT_FOUND("MODEL_NOT_FOUND", "模型未找到", "请检查模型配置或选择其他模型", false),
    MODEL_ERROR("MODEL_ERROR", "模型响应异常", "AI 模型处理失败，请重试", true),
    MODEL_OVERLOADED("MODEL_OVERLOADED", "模型繁忙", "AI 模型当前负载较高，请稍后重试", true),
    CONTEXT_TOO_LONG("CONTEXT_TOO_LONG", "上下文过长", "对话内容超出模型处理限制，请新建会话", false),

    // ============ 工具相关错误 ============
    TOOL_NOT_FOUND("TOOL_NOT_FOUND", "工具未找到", "所需工具不可用，请联系管理员", false),
    TOOL_EXECUTION_FAILED("TOOL_EXECUTION_FAILED", "工具执行失败", "工具执行过程中出错，请重试", true),
    TOOL_TIMEOUT("TOOL_TIMEOUT", "工具执行超时", "工具响应时间过长，请重试或简化请求", true),

    // ============ MCP 相关错误 ============
    MCP_SERVER_ERROR("MCP_SERVER_ERROR", "MCP 服务异常", "MCP 服务启动失败，请检查服务配置", false),
    MCP_SERVER_TIMEOUT("MCP_SERVER_TIMEOUT", "MCP 服务超时", "MCP 服务响应超时，请重试", true),

    // ============ 任务规划相关错误 ============
    PLAN_GENERATION_FAILED("PLAN_GENERATION_FAILED", "计划生成失败", "无法生成执行计划，正在尝试其他方式", true),
    PLAN_EXECUTION_FAILED("PLAN_EXECUTION_FAILED", "计划执行失败", "任务执行过程中出错，部分步骤可能未完成", true),

    // ============ 用户输入相关错误 ============
    INVALID_INPUT("INVALID_INPUT", "输入无效", "请检查输入内容后重试", false),
    EMPTY_MESSAGE("EMPTY_MESSAGE", "消息为空", "请输入消息内容", false),
    USER_CANCELLED("USER_CANCELLED", "请求已取消", "用户取消了请求", true),

    // ============ 权限相关错误 ============
    UNAUTHORIZED("UNAUTHORIZED", "未授权", "请登录后重试", false),
    RATE_LIMITED("RATE_LIMITED", "请求过于频繁", "请稍后再试", true),

    // ============ 其他错误 ============
    INTERNAL_ERROR("INTERNAL_ERROR", "内部错误", "系统发生错误，请稍后重试", true),
    UNKNOWN("UNKNOWN", "未知错误", "发生未知错误，请联系管理员", false);

    private final String code;
    private final String title;
    private final String suggestion;
    private final boolean retryable;

    ChatErrorType(String code, String title, String suggestion, boolean retryable) {
        this.code = code;
        this.title = title;
        this.suggestion = suggestion;
        this.retryable = retryable;
    }

    /**
     * 根据异常类型推断错误类型
     */
    public static ChatErrorType fromException(Exception e) {
        String message = e.getMessage();
        if (message == null) {
            return INTERNAL_ERROR;
        }

        String lowerMessage = message.toLowerCase();

        // 网络相关
        if (lowerMessage.contains("connection") || lowerMessage.contains("network") ||
            lowerMessage.contains("socket") || lowerMessage.contains("connect")) {
            return NETWORK_ERROR;
        }
        if (lowerMessage.contains("timeout") || lowerMessage.contains("timed out")) {
            return TIMEOUT;
        }

        // 模型相关
        if (lowerMessage.contains("model not found") || lowerMessage.contains("model not available")) {
            return MODEL_NOT_FOUND;
        }
        if (lowerMessage.contains("context") && lowerMessage.contains("long")) {
            return CONTEXT_TOO_LONG;
        }
        if (lowerMessage.contains("overload") || lowerMessage.contains("rate limit")) {
            return MODEL_OVERLOADED;
        }

        // 工具相关
        if (lowerMessage.contains("tool") && lowerMessage.contains("not found")) {
            return TOOL_NOT_FOUND;
        }
        if (lowerMessage.contains("tool") && (lowerMessage.contains("failed") || lowerMessage.contains("error"))) {
            return TOOL_EXECUTION_FAILED;
        }

        // MCP 相关
        if (lowerMessage.contains("mcp") && lowerMessage.contains("server")) {
            return MCP_SERVER_ERROR;
        }

        // 计划相关
        if (lowerMessage.contains("plan") && lowerMessage.contains("failed")) {
            return PLAN_EXECUTION_FAILED;
        }

        return INTERNAL_ERROR;
    }

    /**
     * 创建错误响应
     */
    public AIChatResponse toResponse(String sessionId, String model) {
        AIChatResponse response = new AIChatResponse();
        response.setType("error");
        response.setSessionId(sessionId);
        response.setModel(model);
        response.setDone(true);
        response.setError(this.title + ": " + this.suggestion);
        response.setErrorType(this.code);
        response.setErrorTitle(this.title);
        response.setErrorSuggestion(this.suggestion);
        response.setRetryable(this.retryable);
        return response;
    }
}
