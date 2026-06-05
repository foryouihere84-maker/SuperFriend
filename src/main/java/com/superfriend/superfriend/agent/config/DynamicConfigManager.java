package com.superfriend.superfriend.agent.config;

import com.superfriend.superfriend.agent.planner.TaskComplexity;
import com.superfriend.superfriend.agent.reasoning.ReasoningChain;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class DynamicConfigManager {

    private final Map<String, SessionConfig> sessionConfigs = new ConcurrentHashMap<>();

    private static final int BASE_MAX_ITERATIONS = 20;
    private static final int BASE_MAX_CONSECUTIVE_ERRORS = 5;
    private static final long BASE_TOOL_TIMEOUT_MS = 120000;
    private static final long BASE_COMPRESSION_THRESHOLD = 10000;

    public SessionConfig getOrCreateConfig(String sessionId) {
        return sessionConfigs.computeIfAbsent(sessionId, SessionConfig::new);
    }

    public SessionConfig adjustForTaskComplexity(String sessionId, TaskComplexity complexity) {
        SessionConfig config = getOrCreateConfig(sessionId);
        
        switch (complexity) {
            case SIMPLE:
                config.setMaxIterations(BASE_MAX_ITERATIONS);
                config.setMaxConsecutiveErrors(3);
                config.setToolTimeoutMs(60000L);
                config.setCompressionThreshold(8000L);
                config.setEnableReflection(false);
                config.setEnableParallelExecution(false);
                break;
            case MODERATE:
                config.setMaxIterations(BASE_MAX_ITERATIONS * 2);
                config.setMaxConsecutiveErrors(5);
                config.setToolTimeoutMs(BASE_TOOL_TIMEOUT_MS);
                config.setCompressionThreshold(BASE_COMPRESSION_THRESHOLD);
                config.setEnableReflection(true);
                config.setEnableParallelExecution(true);
                break;
            case COMPLEX:
                config.setMaxIterations(BASE_MAX_ITERATIONS * 3);
                config.setMaxConsecutiveErrors(8);
                config.setToolTimeoutMs(180000L);
                config.setCompressionThreshold(12000L);
                config.setEnableReflection(true);
                config.setEnableParallelExecution(true);
                break;
            case VERY_COMPLEX:
                config.setMaxIterations(BASE_MAX_ITERATIONS * 4);
                config.setMaxConsecutiveErrors(10);
                config.setToolTimeoutMs(300000L);
                config.setCompressionThreshold(15000L);
                config.setEnableReflection(true);
                config.setEnableParallelExecution(true);
                break;
        }
        
        log.info("[DynamicConfig] sessionId={}, complexity={}, config={}", 
                sessionId, complexity, config.summary());
        return config;
    }

    public SessionConfig adjustForReasoningChain(String sessionId, ReasoningChain chain) {
        SessionConfig config = getOrCreateConfig(sessionId);
        
        if (chain == null) {
            return config;
        }
        
        double successRate = chain.getSuccessRate();
        int actionCount = chain.getActionCount();
        int failureCount = chain.getFailureCount();
        
        if (successRate < 0.5 && failureCount >= 3) {
            int newMaxErrors = Math.min(config.getMaxConsecutiveErrors() + 2, 15);
            config.setMaxConsecutiveErrors(newMaxErrors);
            log.info("[DynamicConfig] 低成功率调整: successRate={}, 增加maxConsecutiveErrors至{}", 
                    successRate, newMaxErrors);
        }
        
        if (actionCount > 10 && successRate > 0.8) {
            int newMaxIterations = Math.min(config.getMaxIterations() + 10, 100);
            config.setMaxIterations(newMaxIterations);
            log.info("[DynamicConfig] 高成功率调整: actionCount={}, 增加maxIterations至{}", 
                    actionCount, newMaxIterations);
        }
        
        if (chain.getFailedActions().size() >= 3) {
            long newTimeout = Math.min(config.getToolTimeoutMs() * 2, 600000L);
            config.setToolTimeoutMs(newTimeout);
            log.info("[DynamicConfig] 多次失败调整: 增加toolTimeoutMs至{}", newTimeout);
        }
        
        return config;
    }

    public SessionConfig adjustForErrorPattern(String sessionId, ErrorPattern pattern) {
        SessionConfig config = getOrCreateConfig(sessionId);
        
        switch (pattern) {
            case FREQUENT_TIMEOUTS:
                config.setToolTimeoutMs(Math.min(config.getToolTimeoutMs() * 2, 600000L));
                log.info("[DynamicConfig] 频繁超时调整: toolTimeoutMs={}", config.getToolTimeoutMs());
                break;
            case RATE_LIMITING:
                config.setRequestDelayMs(Math.max(config.getRequestDelayMs(), 2000L));
                log.info("[DynamicConfig] 速率限制调整: requestDelayMs={}", config.getRequestDelayMs());
                break;
            case NETWORK_INSTABILITY:
                config.setMaxConsecutiveErrors(Math.min(config.getMaxConsecutiveErrors() + 3, 15));
                config.setRetryDelayMs(Math.max(config.getRetryDelayMs(), 3000L));
                log.info("[DynamicConfig] 网络不稳定调整: maxConsecutiveErrors={}, retryDelayMs={}", 
                        config.getMaxConsecutiveErrors(), config.getRetryDelayMs());
                break;
            case TOOL_ERRORS:
                config.setEnableAlternativeTools(true);
                log.info("[DynamicConfig] 工具错误调整: 启用备选工具");
                break;
        }
        
        return config;
    }

    public SessionConfig adjustForTokenUsage(String sessionId, long currentTokens, long maxTokens) {
        SessionConfig config = getOrCreateConfig(sessionId);
        
        double usageRatio = (double) currentTokens / maxTokens;
        
        if (usageRatio > 0.8) {
            long newThreshold = (long) (maxTokens * 0.6);
            config.setCompressionThreshold(newThreshold);
            config.setEnableAggressiveCompression(true);
            log.info("[DynamicConfig] 高Token使用调整: usageRatio={}, compressionThreshold={}", 
                    String.format("%.2f", usageRatio), newThreshold);
        } else if (usageRatio > 0.6) {
            long newThreshold = (long) (maxTokens * 0.7);
            config.setCompressionThreshold(newThreshold);
            log.info("[DynamicConfig] 中等Token使用调整: compressionThreshold={}", newThreshold);
        }
        
        return config;
    }

    public void updateProgress(String sessionId, double progress) {
        SessionConfig config = getOrCreateConfig(sessionId);
        config.setProgress(progress);
        
        if (progress > 0.8) {
            config.setNearCompletion(true);
        }
    }

    public void cleanupSession(String sessionId) {
        sessionConfigs.remove(sessionId);
        log.debug("[DynamicConfig] 清理会话配置: sessionId={}", sessionId);
    }

    public int getActiveSessionCount() {
        return sessionConfigs.size();
    }

    public enum ErrorPattern {
        FREQUENT_TIMEOUTS,
        RATE_LIMITING,
        NETWORK_INSTABILITY,
        TOOL_ERRORS
    }

    @Data
    public static class SessionConfig {
        private final String sessionId;
        private int maxIterations = BASE_MAX_ITERATIONS;
        private int maxConsecutiveErrors = BASE_MAX_CONSECUTIVE_ERRORS;
        private long toolTimeoutMs = BASE_TOOL_TIMEOUT_MS;
        private long compressionThreshold = BASE_COMPRESSION_THRESHOLD;
        private long requestDelayMs = 0;
        private long retryDelayMs = 1000;
        private boolean enableReflection = true;
        private boolean enableParallelExecution = true;
        private boolean enableAlternativeTools = false;
        private boolean enableAggressiveCompression = false;
        private double progress = 0.0;
        private boolean nearCompletion = false;
        private TaskComplexity detectedComplexity;
        private long lastAdjustmentTime = System.currentTimeMillis();

        public SessionConfig(String sessionId) {
            this.sessionId = sessionId;
        }

        public String summary() {
            return String.format(
                "SessionConfig[maxIter=%d, maxErr=%d, timeout=%dms, compress=%d, progress=%.0f%%]",
                maxIterations, maxConsecutiveErrors, toolTimeoutMs, compressionThreshold, progress * 100
            );
        }

        public boolean shouldCompress(long currentTokens) {
            return currentTokens >= compressionThreshold;
        }

        public boolean canContinue(int currentIteration, int consecutiveErrors) {
            return currentIteration < maxIterations && consecutiveErrors < maxConsecutiveErrors;
        }

        public long getEffectiveRetryDelay(int attempt) {
            return retryDelayMs * (long) Math.pow(2, Math.min(attempt, 5));
        }
    }
}
