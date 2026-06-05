package com.superfriend.superfriend.agent.error;

import org.springframework.stereotype.Component;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 错误恢复管理器
 *
 * 【增强版】支持分层恢复策略：
 * - LEVEL_1: 相同工具重试
 * - LEVEL_2: 替代工具尝试
 * - LEVEL_3: 步骤参数调整
 * - LEVEL_4: 从当前步骤重新规划
 * - LEVEL_5: 完全重新规划
 * - LEVEL_6: 回退到 ReAct 模式
 */
@Component
public class ErrorRecoveryManager {

    private final ErrorDiagnoser diagnoser;
    private final Map<String, RecoveryStrategy> recoveryStrategies;
    private final Map<String, Integer> failureCountMap = new ConcurrentHashMap<>();

    // 恢复级别定义
    public enum RecoveryLevel {
        LEVEL_1_RETRY_SAME_TOOL(1, "相同工具重试"),
        LEVEL_2_TRY_ALTERNATIVE_TOOL(2, "替代工具尝试"),
        LEVEL_3_ADJUST_STEP_PARAMS(3, "步骤参数调整"),
        LEVEL_4_REPLAN_FROM_STEP(4, "从当前步骤重新规划"),
        LEVEL_5_FULL_REPLAN(5, "完全重新规划"),
        LEVEL_6_FALLBACK_TO_REACT(6, "回退到 ReAct 模式");

        private final int level;
        private final String description;

        RecoveryLevel(int level, String description) {
            this.level = level;
            this.description = description;
        }

        public int getLevel() {
            return level;
        }

        public String getDescription() {
            return description;
        }

        public RecoveryLevel nextLevel() {
            int nextOrdinal = this.ordinal() + 1;
            if (nextOrdinal < values().length) {
                return values()[nextOrdinal];
            }
            return this;
        }

        public boolean canEscalate() {
            return this != LEVEL_6_FALLBACK_TO_REACT;
        }
    }

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
        recoveryStrategies.put(ErrorType.VALIDATION_ERROR.name(), new ValidationErrorRecoveryStrategy());
        recoveryStrategies.put(ErrorType.RESOURCE_NOT_FOUND.name(), new ResourceNotFoundRecoveryStrategy());
        recoveryStrategies.put(ErrorType.PERMISSION_DENIED.name(), new PermissionDeniedRecoveryStrategy());
    }

    /**
     * 尝试恢复
     */
    public RecoveryResult attemptRecovery(String errorMessage, String componentName,
                                           Map<String, Object> context) {
        ErrorDiagnosis diagnosis = diagnoser.diagnose(errorMessage, componentName, context);
        RecoveryStrategy strategy = recoveryStrategies.get(diagnosis.getErrorRecord().getErrorType().name());

        if (strategy == null) {
            return RecoveryResult.failure("No recovery strategy available for this error type");
        }

        return strategy.recover(diagnosis, context);
    }

    /**
     * 确定恢复级别
     * 【新增】基于错误类型和失败次数确定恢复级别
     */
    public RecoveryLevel determineRecoveryLevel(ErrorType errorType, int failedAttempts, String componentName) {
        // 获取组件的累计失败次数
        String key = componentName != null ? componentName : "unknown";
        int totalFailures = failureCountMap.getOrDefault(key, 0) + failedAttempts;

        // 基于错误类型确定基础级别
        RecoveryLevel baseLevel = getBaseRecoveryLevel(errorType);

        // 基于失败次数升级
        RecoveryLevel adjustedLevel = adjustLevelByFailures(baseLevel, totalFailures);

        logRecoveryDecision(errorType, failedAttempts, totalFailures, adjustedLevel);
        return adjustedLevel;
    }

    /**
     * 基于错误类型获取基础恢复级别
     */
    private RecoveryLevel getBaseRecoveryLevel(ErrorType errorType) {
        switch (errorType) {
            case NETWORK_ERROR:
            case TIMEOUT_ERROR:
            case RATE_LIMIT_ERROR:
                return RecoveryLevel.LEVEL_1_RETRY_SAME_TOOL;

            case SERVER_ERROR:
                return RecoveryLevel.LEVEL_2_TRY_ALTERNATIVE_TOOL;

            case VALIDATION_ERROR:
                return RecoveryLevel.LEVEL_3_ADJUST_STEP_PARAMS;

            case RESOURCE_NOT_FOUND:
                return RecoveryLevel.LEVEL_4_REPLAN_FROM_STEP;

            case AUTHENTICATION_ERROR:
            case PERMISSION_DENIED:
                return RecoveryLevel.LEVEL_5_FULL_REPLAN;

            default:
                return RecoveryLevel.LEVEL_2_TRY_ALTERNATIVE_TOOL;
        }
    }

    /**
     * 基于失败次数调整恢复级别
     */
    private RecoveryLevel adjustLevelByFailures(RecoveryLevel baseLevel, int totalFailures) {
        if (totalFailures <= 1) {
            return baseLevel;
        } else if (totalFailures <= 2) {
            return baseLevel.nextLevel();
        } else if (totalFailures <= 3) {
            RecoveryLevel next = baseLevel.nextLevel();
            return next.canEscalate() ? next.nextLevel() : next;
        } else {
            // 4次以上失败，直接跳到高级别恢复
            return RecoveryLevel.LEVEL_5_FULL_REPLAN;
        }
    }

    /**
     * 记录失败次数
     */
    public void recordFailure(String componentName) {
        String key = componentName != null ? componentName : "unknown";
        failureCountMap.merge(key, 1, Integer::sum);
    }

    /**
     * 清除失败记录
     */
    public void clearFailures(String componentName) {
        String key = componentName != null ? componentName : "unknown";
        failureCountMap.remove(key);
    }

    /**
     * 获取失败次数
     */
    public int getFailureCount(String componentName) {
        String key = componentName != null ? componentName : "unknown";
        return failureCountMap.getOrDefault(key, 0);
    }

    public boolean shouldRetry(ErrorType errorType) {
        switch (errorType) {
            case NETWORK_ERROR:
            case TIMEOUT_ERROR:
            case RATE_LIMIT_ERROR:
            case SERVER_ERROR:
            case VALIDATION_ERROR:
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
            case VALIDATION_ERROR:
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
            case VALIDATION_ERROR:
                return 500;
            default:
                return 1000;
        }
    }

    /**
     * 获取替代工具建议
     */
    public List<String> getAlternativeTools(String failedTool, ErrorType errorType) {
        Map<String, List<String>> alternatives = new HashMap<>();

        // 网页抓取工具替代
        alternatives.put("fetch", Arrays.asList("puppeteer_navigate", "brave-search__search"));
        alternatives.put("puppeteer_navigate", Arrays.asList("fetch", "brave-search__search"));

        // 搜索工具替代
        alternatives.put("brave-search__search", Arrays.asList("duckduckgo-search", "fetch"));
        alternatives.put("duckduckgo-search", Arrays.asList("brave-search__search", "fetch"));

        // 文件操作工具替代
        alternatives.put("read_file", Arrays.asList("bash-sandbox__execute"));
        alternatives.put("write_file", Arrays.asList("bash-sandbox__execute"));

        return alternatives.getOrDefault(failedTool, Collections.emptyList());
    }

    private void logRecoveryDecision(ErrorType errorType, int failedAttempts,
                                      int totalFailures, RecoveryLevel level) {
        System.out.println("[Recovery] ErrorType=" + errorType +
            ", FailedAttempts=" + failedAttempts +
            ", TotalFailures=" + totalFailures +
            ", RecoveryLevel=" + level.getDescription());
    }
}
