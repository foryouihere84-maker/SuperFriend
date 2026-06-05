package com.superfriend.superfriend.agent.error;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@Slf4j
@Component
public class EnhancedErrorHandler {

    private final ErrorRecoveryManager recoveryManager;
    private final Map<String, ErrorStatistics> sessionErrorStats = new ConcurrentHashMap<>();

    private static final Map<ErrorType, ErrorHandlingStrategy> STRATEGIES = new EnumMap<>(ErrorType.class);
    
    static {
        STRATEGIES.put(ErrorType.NETWORK_ERROR, new ErrorHandlingStrategy(
            ErrorAction.RETRY_WITH_BACKOFF, 3, 2000L, true, "网络连接问题，正在重试..."
        ));
        STRATEGIES.put(ErrorType.TIMEOUT_ERROR, new ErrorHandlingStrategy(
            ErrorAction.RETRY_WITH_BACKOFF, 3, 3000L, true, "操作超时，正在重试..."
        ));
        STRATEGIES.put(ErrorType.RATE_LIMIT_ERROR, new ErrorHandlingStrategy(
            ErrorAction.WAIT_AND_RETRY, 5, 5000L, true, "请求频率限制，等待后重试..."
        ));
        STRATEGIES.put(ErrorType.AUTHENTICATION_ERROR, new ErrorHandlingStrategy(
            ErrorAction.ABORT, 0, 0L, false, "认证失败，请检查API密钥配置"
        ));
        STRATEGIES.put(ErrorType.AUTHORIZATION_ERROR, new ErrorHandlingStrategy(
            ErrorAction.ABORT, 0, 0L, false, "权限不足，无法执行此操作"
        ));
        STRATEGIES.put(ErrorType.VALIDATION_ERROR, new ErrorHandlingStrategy(
            ErrorAction.ADJUST_AND_RETRY, 2, 1000L, true, "参数验证失败，正在调整..."
        ));
        STRATEGIES.put(ErrorType.RESOURCE_NOT_FOUND, new ErrorHandlingStrategy(
            ErrorAction.SKIP_OR_FALLBACK, 1, 0L, true, "资源未找到，尝试备选方案..."
        ));
        STRATEGIES.put(ErrorType.SERVER_ERROR, new ErrorHandlingStrategy(
            ErrorAction.RETRY_WITH_BACKOFF, 2, 4000L, true, "服务器错误，正在重试..."
        ));
        STRATEGIES.put(ErrorType.TOOL_ERROR, new ErrorHandlingStrategy(
            ErrorAction.TRY_ALTERNATIVE, 2, 1000L, true, "工具执行失败，尝试备选工具..."
        ));
        STRATEGIES.put(ErrorType.UNKNOWN_ERROR, new ErrorHandlingStrategy(
            ErrorAction.LOG_AND_CONTINUE, 1, 1000L, false, "未知错误，继续执行..."
        ));
    }

    private static final List<ErrorPattern> ERROR_PATTERNS = Arrays.asList(
        new ErrorPattern(Pattern.compile("(?i)(timeout|timed?\\s*out|超时)"), ErrorType.TIMEOUT_ERROR),
        new ErrorPattern(Pattern.compile("(?i)(network|connection|connect|网络|连接)"), ErrorType.NETWORK_ERROR),
        new ErrorPattern(Pattern.compile("(?i)(rate.?limit|429|too.?many|频率|限制)"), ErrorType.RATE_LIMIT_ERROR),
        new ErrorPattern(Pattern.compile("(?i)(unauthorized|401|认证|授权失败)"), ErrorType.AUTHENTICATION_ERROR),
        new ErrorPattern(Pattern.compile("(?i)(forbidden|403|权限|禁止)"), ErrorType.AUTHORIZATION_ERROR),
        new ErrorPattern(Pattern.compile("(?i)(not.?found|404|未找到|不存在)"), ErrorType.RESOURCE_NOT_FOUND),
        new ErrorPattern(Pattern.compile("(?i)(invalid|validation|参数|验证)"), ErrorType.VALIDATION_ERROR),
        new ErrorPattern(Pattern.compile("(?i)(500|502|503|server.?error|服务器)"), ErrorType.SERVER_ERROR)
    );

    @Autowired
    public EnhancedErrorHandler(ErrorRecoveryManager recoveryManager) {
        this.recoveryManager = recoveryManager;
    }

    public ErrorClassification classifyError(String errorMessage, String toolName, String sessionId) {
        ErrorType type = classifyErrorType(errorMessage);
        ErrorSeverity severity = determineSeverity(type, errorMessage);
        ErrorHandlingStrategy strategy = STRATEGIES.getOrDefault(type, 
            STRATEGIES.get(ErrorType.UNKNOWN_ERROR));
        
        ErrorStatistics stats = getOrCreateStats(sessionId);
        stats.recordError(type, toolName);
        
        ErrorClassification classification = new ErrorClassification();
        classification.setType(type);
        classification.setSeverity(severity);
        classification.setStrategy(strategy);
        classification.setErrorMessage(errorMessage);
        classification.setToolName(toolName);
        classification.setTimestamp(LocalDateTime.now());
        classification.setSessionErrorCount(stats.getTotalErrors());
        classification.setConsecutiveErrors(stats.getConsecutiveErrors());
        
        log.info("[ErrorHandler] sessionId={}, type={}, severity={}, tool={}, consecutive={}",
                sessionId, type, severity, toolName, stats.getConsecutiveErrors());
        
        return classification;
    }

    public ErrorHandlingResult handle(ErrorClassification classification, String sessionId, 
                                       int currentAttempt, Map<String, Object> context) {
        ErrorHandlingStrategy strategy = classification.getStrategy();
        ErrorStatistics stats = getOrCreateStats(sessionId);
        
        if (currentAttempt >= strategy.getMaxRetries()) {
            log.warn("[ErrorHandler] sessionId={}, 达到最大重试次数: {}/{}", 
                    sessionId, currentAttempt, strategy.getMaxRetries());
            return ErrorHandlingResult.exhausted(classification);
        }
        
        switch (strategy.getAction()) {
            case RETRY_WITH_BACKOFF:
                return handleRetryWithBackoff(classification, currentAttempt, context);
            case WAIT_AND_RETRY:
                return handleWaitAndRetry(classification, currentAttempt, context);
            case ADJUST_AND_RETRY:
                return handleAdjustAndRetry(classification, currentAttempt, context);
            case TRY_ALTERNATIVE:
                return handleTryAlternative(classification, context);
            case SKIP_OR_FALLBACK:
                return handleSkipOrFallback(classification, context);
            case ABORT:
                return ErrorHandlingResult.abort(classification);
            case LOG_AND_CONTINUE:
            default:
                return ErrorHandlingResult.continueExecution(classification);
        }
    }

    private ErrorHandlingResult handleRetryWithBackoff(ErrorClassification classification, 
                                                        int attempt, Map<String, Object> context) {
        long delay = calculateBackoffDelay(classification.getStrategy(), attempt);
        
        log.info("[ErrorHandler] 重试策略: delay={}ms, attempt={}", delay, attempt);
        
        return ErrorHandlingResult.retry(classification, delay, 
            "等待 " + delay + "ms 后重试 (尝试 " + (attempt + 1) + ")");
    }

    private ErrorHandlingResult handleWaitAndRetry(ErrorClassification classification, 
                                                    int attempt, Map<String, Object> context) {
        long baseDelay = classification.getStrategy().getBaseDelayMs();
        long delay = baseDelay * (attempt + 1);
        
        log.info("[ErrorHandler] 等待重试策略: delay={}ms, attempt={}", delay, attempt);
        
        return ErrorHandlingResult.retry(classification, delay,
            "速率限制，等待 " + delay + "ms 后重试");
    }

    private ErrorHandlingResult handleAdjustAndRetry(ErrorClassification classification, 
                                                      int attempt, Map<String, Object> context) {
        Map<String, Object> adjustedParams = adjustParameters(context, classification);
        
        log.info("[ErrorHandler] 参数调整策略: adjustedParams={}", adjustedParams.keySet());
        
        return ErrorHandlingResult.retryWithAdjustment(classification, 1000L, 
            adjustedParams, "已调整参数，重新尝试");
    }

    private ErrorHandlingResult handleTryAlternative(ErrorClassification classification, 
                                                      Map<String, Object> context) {
        String alternativeTool = findAlternativeTool(classification.getToolName(), context);
        
        if (alternativeTool != null) {
            log.info("[ErrorHandler] 备选工具策略: alternative={}", alternativeTool);
            return ErrorHandlingResult.useAlternative(classification, alternativeTool,
                "使用备选工具: " + alternativeTool);
        }
        
        return ErrorHandlingResult.skip(classification, "无可用备选工具，跳过此步骤");
    }

    private ErrorHandlingResult handleSkipOrFallback(ErrorClassification classification, 
                                                      Map<String, Object> context) {
        String fallbackValue = generateFallbackValue(classification, context);
        
        log.info("[ErrorHandler] 跳过/回退策略: fallback provided");
        
        return ErrorHandlingResult.skip(classification, 
            "资源不可用，使用默认值继续: " + fallbackValue);
    }

    public boolean shouldAbort(String sessionId, ErrorType type) {
        ErrorStatistics stats = getOrCreateStats(sessionId);
        
        if (stats.getConsecutiveErrors() >= 10) {
            log.warn("[ErrorHandler] sessionId={}, 连续错误过多，建议中止", sessionId);
            return true;
        }
        
        if (stats.getErrorCountByType(type) >= 5) {
            log.warn("[ErrorHandler] sessionId={}, 同类型错误过多: {}", sessionId, type);
            return true;
        }
        
        return type == ErrorType.AUTHENTICATION_ERROR || type == ErrorType.AUTHORIZATION_ERROR;
    }

    public void recordSuccess(String sessionId) {
        ErrorStatistics stats = getOrCreateStats(sessionId);
        stats.recordSuccess();
        log.debug("[ErrorHandler] sessionId={}, 记录成功，重置连续错误计数", sessionId);
    }

    public ErrorStatistics getOrCreateStats(String sessionId) {
        return sessionErrorStats.computeIfAbsent(sessionId, k -> new ErrorStatistics());
    }

    public void cleanupSession(String sessionId) {
        sessionErrorStats.remove(sessionId);
        log.debug("[ErrorHandler] 清理会话错误统计: sessionId={}", sessionId);
    }

    private ErrorType classifyErrorType(String errorMessage) {
        if (errorMessage == null || errorMessage.isEmpty()) {
            return ErrorType.UNKNOWN_ERROR;
        }
        
        for (ErrorPattern pattern : ERROR_PATTERNS) {
            if (pattern.getPattern().matcher(errorMessage).find()) {
                return pattern.getType();
            }
        }
        
        return ErrorType.UNKNOWN_ERROR;
    }

    private ErrorSeverity determineSeverity(ErrorType type, String errorMessage) {
        switch (type) {
            case AUTHENTICATION_ERROR:
            case AUTHORIZATION_ERROR:
                return ErrorSeverity.CRITICAL;
            case NETWORK_ERROR:
            case TIMEOUT_ERROR:
            case RATE_LIMIT_ERROR:
                return ErrorSeverity.HIGH;
            case SERVER_ERROR:
            case TOOL_ERROR:
                return ErrorSeverity.MEDIUM;
            case VALIDATION_ERROR:
            case RESOURCE_NOT_FOUND:
                return ErrorSeverity.LOW;
            default:
                return ErrorSeverity.LOW;
        }
    }

    private long calculateBackoffDelay(ErrorHandlingStrategy strategy, int attempt) {
        long baseDelay = strategy.getBaseDelayMs();
        return baseDelay * (long) Math.pow(2, attempt);
    }

    private Map<String, Object> adjustParameters(Map<String, Object> original, 
                                                   ErrorClassification classification) {
        Map<String, Object> adjusted = new HashMap<>(original);
        
        String errorMsg = classification.getErrorMessage().toLowerCase();
        
        if (errorMsg.contains("timeout")) {
            adjusted.put("timeout", 60000);
        }
        if (errorMsg.contains("length") || errorMsg.contains("size")) {
            adjusted.put("max_length", 5000);
        }
        if (errorMsg.contains("invalid") && adjusted.containsKey("format")) {
            adjusted.put("format", "text");
        }
        
        return adjusted;
    }

    private String findAlternativeTool(String failedTool, Map<String, Object> context) {
        Map<String, String> alternatives = getToolAlternatives();
        return alternatives.get(failedTool);
    }

    private Map<String, String> getToolAlternatives() {
        Map<String, String> alternatives = new HashMap<>();
        alternatives.put("fetch", "puppeteer_navigate");
        alternatives.put("brave_web_search", "tavily_search");
        alternatives.put("tavily_search", "serper_search");
        alternatives.put("google_search", "bing_search");
        return alternatives;
    }

    private String generateFallbackValue(ErrorClassification classification, 
                                          Map<String, Object> context) {
        return "[信息不可用: " + classification.getErrorMessage() + "]";
    }

    @Data
    public static class ErrorClassification {
        private ErrorType type;
        private ErrorSeverity severity;
        private ErrorHandlingStrategy strategy;
        private String errorMessage;
        private String toolName;
        private LocalDateTime timestamp;
        private int sessionErrorCount;
        private int consecutiveErrors;
    }

    @Data
    public static class ErrorHandlingStrategy {
        private final ErrorAction action;
        private final int maxRetries;
        private final long baseDelayMs;
        private final boolean recoverable;
        private final String userMessage;
        
        public ErrorHandlingStrategy(ErrorAction action, int maxRetries, 
                                      long baseDelayMs, boolean recoverable, String userMessage) {
            this.action = action;
            this.maxRetries = maxRetries;
            this.baseDelayMs = baseDelayMs;
            this.recoverable = recoverable;
            this.userMessage = userMessage;
        }
    }

    public enum ErrorAction {
        RETRY_WITH_BACKOFF,
        WAIT_AND_RETRY,
        ADJUST_AND_RETRY,
        TRY_ALTERNATIVE,
        SKIP_OR_FALLBACK,
        ABORT,
        LOG_AND_CONTINUE
    }

    @Data
    public static class ErrorHandlingResult {
        private final boolean shouldRetry;
        private final boolean shouldAbort;
        private final boolean shouldSkip;
        private final boolean useAlternative;
        private final long delayMs;
        private final String alternativeTool;
        private final Map<String, Object> adjustedParams;
        private final String message;
        private final ErrorClassification classification;

        public static ErrorHandlingResult retry(ErrorClassification classification, 
                                                 long delay, String message) {
            ErrorHandlingResult result = new ErrorHandlingResult(
                true, false, false, false, delay, null, null, message, classification);
            return result;
        }

        public static ErrorHandlingResult retryWithAdjustment(ErrorClassification classification,
                                                               long delay, Map<String, Object> params,
                                                               String message) {
            return new ErrorHandlingResult(
                true, false, false, false, delay, null, params, message, classification);
        }

        public static ErrorHandlingResult useAlternative(ErrorClassification classification,
                                                          String tool, String message) {
            return new ErrorHandlingResult(
                false, false, false, true, 0, tool, null, message, classification);
        }

        public static ErrorHandlingResult skip(ErrorClassification classification, String message) {
            return new ErrorHandlingResult(
                false, false, true, false, 0, null, null, message, classification);
        }

        public static ErrorHandlingResult abort(ErrorClassification classification) {
            return new ErrorHandlingResult(
                false, true, false, false, 0, null, null, 
                classification.getStrategy().getUserMessage(), classification);
        }

        public static ErrorHandlingResult continueExecution(ErrorClassification classification) {
            return new ErrorHandlingResult(
                false, false, false, false, 0, null, null, 
                "继续执行", classification);
        }

        public static ErrorHandlingResult exhausted(ErrorClassification classification) {
            return new ErrorHandlingResult(
                false, true, false, false, 0, null, null,
                "已达到最大重试次数", classification);
        }
    }

    @Data
    public static class ErrorStatistics {
        private int totalErrors = 0;
        private int consecutiveErrors = 0;
        private int totalSuccesses = 0;
        private final Map<ErrorType, Integer> errorsByType = new EnumMap<>(ErrorType.class);
        private final Map<String, Integer> errorsByTool = new HashMap<>();
        private LocalDateTime lastErrorTime;

        public void recordError(ErrorType type, String toolName) {
            totalErrors++;
            consecutiveErrors++;
            errorsByType.merge(type, 1, Integer::sum);
            if (toolName != null) {
                errorsByTool.merge(toolName, 1, Integer::sum);
            }
            lastErrorTime = LocalDateTime.now();
        }

        public void recordSuccess() {
            totalSuccesses++;
            consecutiveErrors = 0;
        }

        public int getErrorCountByType(ErrorType type) {
            return errorsByType.getOrDefault(type, 0);
        }

        public double getErrorRate() {
            int total = totalErrors + totalSuccesses;
            return total == 0 ? 0 : (double) totalErrors / total;
        }
    }

    @Data
    private static class ErrorPattern {
        private final Pattern pattern;
        private final ErrorType type;
        
        public ErrorPattern(Pattern pattern, ErrorType type) {
            this.pattern = pattern;
            this.type = type;
        }
    }
}
