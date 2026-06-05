package com.superfriend.superfriend.agent.error;

<<<<<<< HEAD
import java.util.ArrayList;
import java.util.List;

/**
 * 恢复结果
 *
 * 【增强版】支持更多恢复动作和上下文信息
 */
=======
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
public class RecoveryResult {
    private boolean success;
    private String message;
    private long retryDelay;
    private RecoveryAction action;
<<<<<<< HEAD
    private String alternativeTool;       // 替代工具
    private String adjustmentHint;        // 调整提示
    private List<String> suggestions;     // 建议列表
    private ErrorRecoveryManager.RecoveryLevel recoveryLevel;  // 恢复级别

=======
    
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
    private RecoveryResult(boolean success, String message, long retryDelay, RecoveryAction action) {
        this.success = success;
        this.message = message;
        this.retryDelay = retryDelay;
        this.action = action;
<<<<<<< HEAD
        this.suggestions = new ArrayList<>();
    }

    // ==================== 静态工厂方法 ====================

    public static RecoveryResult success(String message) {
        return new RecoveryResult(true, message, 0, RecoveryAction.CONTINUE);
    }

    public static RecoveryResult failure(String message) {
        return new RecoveryResult(false, message, 0, RecoveryAction.ABORT);
    }

    public static RecoveryResult retry(String message, long delay) {
        RecoveryResult result = new RecoveryResult(false, message, delay, RecoveryAction.RETRY);
        result.recoveryLevel = ErrorRecoveryManager.RecoveryLevel.LEVEL_1_RETRY_SAME_TOOL;
        return result;
    }

    /**
     * 【新增】使用替代工具
     */
    public static RecoveryResult withAlternative(String message, String alternativeTool) {
        RecoveryResult result = new RecoveryResult(false, message, 0, RecoveryAction.USE_ALTERNATIVE);
        result.alternativeTool = alternativeTool;
        result.recoveryLevel = ErrorRecoveryManager.RecoveryLevel.LEVEL_2_TRY_ALTERNATIVE_TOOL;
        return result;
    }

    /**
     * 【新增】调整参数后重试
     */
    public static RecoveryResult withAdjustment(String message, String adjustmentHint) {
        RecoveryResult result = new RecoveryResult(false, message, 0, RecoveryAction.ADJUST_AND_RETRY);
        result.adjustmentHint = adjustmentHint;
        result.recoveryLevel = ErrorRecoveryManager.RecoveryLevel.LEVEL_3_ADJUST_STEP_PARAMS;
        return result;
    }

    /**
     * 【新增】重新规划
     */
    public static RecoveryResult replan(String message, String suggestion) {
        RecoveryResult result = new RecoveryResult(false, message, 0, RecoveryAction.REPLAN);
        result.addSuggestion(suggestion);
        result.recoveryLevel = ErrorRecoveryManager.RecoveryLevel.LEVEL_4_REPLAN_FROM_STEP;
        return result;
    }

    /**
     * 【新增】跳过当前步骤
     */
    public static RecoveryResult skip(String message, String suggestion) {
        RecoveryResult result = new RecoveryResult(false, message, 0, RecoveryAction.SKIP);
        result.addSuggestion(suggestion);
        result.recoveryLevel = ErrorRecoveryManager.RecoveryLevel.LEVEL_3_ADJUST_STEP_PARAMS;
        return result;
    }

    /**
     * 【新增】回退到 ReAct 模式
     */
    public static RecoveryResult fallbackToReact(String message) {
        RecoveryResult result = new RecoveryResult(false, message, 0, RecoveryAction.FALLBACK_TO_REACT);
        result.recoveryLevel = ErrorRecoveryManager.RecoveryLevel.LEVEL_6_FALLBACK_TO_REACT;
        return result;
    }

    // ==================== 辅助方法 ====================

    public RecoveryResult addSuggestion(String suggestion) {
        if (suggestions == null) {
            suggestions = new ArrayList<>();
        }
        suggestions.add(suggestion);
        return this;
    }

    public RecoveryResult setRecoveryLevel(ErrorRecoveryManager.RecoveryLevel level) {
        this.recoveryLevel = level;
        return this;
    }

    // ==================== Getter 方法 ====================

=======
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
    
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
    public long getRetryDelay() { return retryDelay; }
    public RecoveryAction getAction() { return action; }
<<<<<<< HEAD
    public String getAlternativeTool() { return alternativeTool; }
    public String getAdjustmentHint() { return adjustmentHint; }
    public List<String> getSuggestions() { return suggestions; }
    public ErrorRecoveryManager.RecoveryLevel getRecoveryLevel() { return recoveryLevel; }

    /**
     * 是否需要重试
     */
    public boolean shouldRetry() {
        return action == RecoveryAction.RETRY || action == RecoveryAction.ADJUST_AND_RETRY;
    }

    /**
     * 是否需要替代方案
     */
    public boolean needsAlternative() {
        return action == RecoveryAction.USE_ALTERNATIVE;
    }

    /**
     * 是否需要重新规划
     */
    public boolean needsReplan() {
        return action == RecoveryAction.REPLAN || action == RecoveryAction.FALLBACK_TO_REACT;
    }

    /**
     * 是否可以跳过
     */
    public boolean canSkip() {
        return action == RecoveryAction.SKIP;
    }
}

/**
 * 恢复动作枚举
 * 【增强版】支持更多恢复动作类型
 */
enum RecoveryAction {
    CONTINUE("继续执行"),
    RETRY("重试操作"),
    ABORT("中止操作"),
    FALLBACK("使用回退机制"),
    USE_ALTERNATIVE("使用替代工具"),
    ADJUST_AND_RETRY("调整参数后重试"),
    REPLAN("重新规划"),
    SKIP("跳过当前步骤"),
    FALLBACK_TO_REACT("回退到 ReAct 模式");

    private final String description;

    RecoveryAction(String description) {
        this.description = description;
    }

=======
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
    
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
    public String getDescription() { return description; }
}
