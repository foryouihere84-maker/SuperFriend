package com.superfriend.superfriend.service;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Service
@Slf4j
public class AgentMetricsService {

    private final ConcurrentHashMap<String, ExecutionMetrics> executionMetrics = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ToolMetrics> toolMetrics = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ModelMetrics> modelMetrics = new ConcurrentHashMap<>();
    
    private final AtomicLong totalExecutions = new AtomicLong(0);
    private final AtomicLong successfulExecutions = new AtomicLong(0);
    private final AtomicLong failedExecutions = new AtomicLong(0);
    
    private final LinkedList<ExecutionSummary> recentExecutions = new LinkedList<>();
    private static final int MAX_RECENT_EXECUTIONS = 100;

    @Data
    public static class ExecutionMetrics {
        private String executionId;
        private String sessionId;
        private String model;
        private Long userId;
        private String mode;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private long durationMs;
        private boolean success;
        private String errorMessage;
        
        private int totalIterations;
        private int toolCallsCount;
        private int successfulToolCalls;
        private int failedToolCalls;
        
        private long totalInputTokens;
        private long totalOutputTokens;
        private long totalTokens;
        
        private int reflectionCount;
        private int correctionCount;
        
        private double averageToolExecutionTime;
        private double progressAtFailure;
    }

    @Data
    public static class ToolMetrics {
        private String toolName;
        private String serverName;
        private AtomicLong totalCalls = new AtomicLong(0);
        private AtomicLong successfulCalls = new AtomicLong(0);
        private AtomicLong failedCalls = new AtomicLong(0);
        private AtomicLong totalExecutionTimeMs = new AtomicLong(0);
        private AtomicLong totalRetries = new AtomicLong(0);
        
        private LinkedList<Long> recentExecutionTimes = new LinkedList<>();
        private static final int MAX_RECENT_TIMES = 50;
        
        public double getSuccessRate() {
            long total = totalCalls.get();
            return total > 0 ? (double) successfulCalls.get() / total : 0.0;
        }
        
        public double getAverageExecutionTime() {
            long calls = totalCalls.get();
            return calls > 0 ? (double) totalExecutionTimeMs.get() / calls : 0.0;
        }
        
        public void recordExecution(long durationMs, boolean success, boolean retried) {
            totalCalls.incrementAndGet();
            totalExecutionTimeMs.addAndGet(durationMs);
            
            if (success) {
                successfulCalls.incrementAndGet();
            } else {
                failedCalls.incrementAndGet();
            }
            
            if (retried) {
                totalRetries.incrementAndGet();
            }
            
            synchronized (recentExecutionTimes) {
                recentExecutionTimes.addLast(durationMs);
                if (recentExecutionTimes.size() > MAX_RECENT_TIMES) {
                    recentExecutionTimes.removeFirst();
                }
            }
        }
    }

    @Data
    public static class ModelMetrics {
        private String modelId;
        private AtomicLong totalCalls = new AtomicLong(0);
        private AtomicLong totalInputTokens = new AtomicLong(0);
        private AtomicLong totalOutputTokens = new AtomicLong(0);
        private AtomicLong totalErrors = new AtomicLong(0);
        private AtomicLong totalLatencyMs = new AtomicLong(0);
        
        public double getAverageLatency() {
            long calls = totalCalls.get();
            return calls > 0 ? (double) totalLatencyMs.get() / calls : 0.0;
        }
    }

    @Data
    public static class ExecutionSummary {
        private String executionId;
        private String sessionId;
        private String model;
        private String mode;
        private LocalDateTime startTime;
        private long durationMs;
        private boolean success;
        private int toolCallsCount;
        private long totalTokens;
    }

    @Data
    public static class AggregatedMetrics {
        private long totalExecutions;
        private long successfulExecutions;
        private long failedExecutions;
        private double successRate;
        private double averageExecutionTimeMs;
        private long totalToolCalls;
        private long totalTokensUsed;
        private Map<String, ToolMetricsSummary> topTools;
        private Map<String, ModelMetricsSummary> modelStats;
    }

    @Data
    public static class ToolMetricsSummary {
        private String toolName;
        private long totalCalls;
        private double successRate;
        private double averageExecutionTimeMs;
    }

    @Data
    public static class ModelMetricsSummary {
        private String modelId;
        private long totalCalls;
        private long totalTokens;
        private double averageLatencyMs;
    }

    public String startExecution(String sessionId, String model, Long userId, String mode) {
        String executionId = UUID.randomUUID().toString();
        
        ExecutionMetrics metrics = new ExecutionMetrics();
        metrics.setExecutionId(executionId);
        metrics.setSessionId(sessionId);
        metrics.setModel(model);
        metrics.setUserId(userId);
        metrics.setMode(mode);
        metrics.setStartTime(LocalDateTime.now());
        
        executionMetrics.put(executionId, metrics);
        totalExecutions.incrementAndGet();
        
        log.debug("开始记录执行指标: executionId={}, sessionId={}, model={}", executionId, sessionId, model);
        return executionId;
    }

    public void endExecution(String executionId, boolean success, String errorMessage) {
        ExecutionMetrics metrics = executionMetrics.get(executionId);
        if (metrics == null) {
            log.warn("未找到执行指标: executionId={}", executionId);
            return;
        }
        
        metrics.setEndTime(LocalDateTime.now());
        metrics.setSuccess(success);
        metrics.setErrorMessage(errorMessage);
        
        if (metrics.getStartTime() != null && metrics.getEndTime() != null) {
            metrics.setDurationMs(
                java.time.Duration.between(metrics.getStartTime(), metrics.getEndTime()).toMillis()
            );
        }
        
        if (success) {
            successfulExecutions.incrementAndGet();
        } else {
            failedExecutions.incrementAndGet();
        }
        
        ExecutionSummary summary = createSummary(metrics);
        synchronized (recentExecutions) {
            recentExecutions.addLast(summary);
            if (recentExecutions.size() > MAX_RECENT_EXECUTIONS) {
                recentExecutions.removeFirst();
            }
        }
        
        log.info("执行完成: executionId={}, success={}, durationMs={}, toolCalls={}, tokens={}",
            executionId, success, metrics.getDurationMs(), 
            metrics.getToolCallsCount(), metrics.getTotalTokens());
    }

    public void recordToolCall(String executionId, String toolName, String serverName,
                               long durationMs, boolean success, boolean retried) {
        ExecutionMetrics metrics = executionMetrics.get(executionId);
        if (metrics != null) {
            metrics.setToolCallsCount(metrics.getToolCallsCount() + 1);
            if (success) {
                metrics.setSuccessfulToolCalls(metrics.getSuccessfulToolCalls() + 1);
            } else {
                metrics.setFailedToolCalls(metrics.getFailedToolCalls() + 1);
            }
        }
        
        String toolKey = serverName + "__" + toolName;
        ToolMetrics toolMetrics = this.toolMetrics.computeIfAbsent(toolKey, k -> {
            ToolMetrics tm = new ToolMetrics();
            tm.setToolName(toolName);
            tm.setServerName(serverName);
            return tm;
        });
        toolMetrics.recordExecution(durationMs, success, retried);
        
        log.debug("工具调用记录: tool={}, success={}, durationMs={}, retried={}",
            toolKey, success, durationMs, retried);
    }

    public void recordModelCall(String executionId, String modelId, 
                                long inputTokens, long outputTokens, long latencyMs) {
        ExecutionMetrics metrics = executionMetrics.get(executionId);
        if (metrics != null) {
            metrics.setTotalInputTokens(metrics.getTotalInputTokens() + inputTokens);
            metrics.setTotalOutputTokens(metrics.getTotalOutputTokens() + outputTokens);
            metrics.setTotalTokens(metrics.getTotalTokens() + inputTokens + outputTokens);
        }
        
        ModelMetrics modelMetrics = this.modelMetrics.computeIfAbsent(modelId, k -> {
            ModelMetrics mm = new ModelMetrics();
            mm.setModelId(modelId);
            return mm;
        });
        modelMetrics.getTotalCalls().incrementAndGet();
        modelMetrics.getTotalInputTokens().addAndGet(inputTokens);
        modelMetrics.getTotalOutputTokens().addAndGet(outputTokens);
        modelMetrics.getTotalLatencyMs().addAndGet(latencyMs);
    }

    public void recordModelError(String modelId) {
        ModelMetrics metrics = modelMetrics.get(modelId);
        if (metrics != null) {
            metrics.getTotalErrors().incrementAndGet();
        }
    }

    public void recordIteration(String executionId) {
        ExecutionMetrics metrics = executionMetrics.get(executionId);
        if (metrics != null) {
            metrics.setTotalIterations(metrics.getTotalIterations() + 1);
        }
    }

    public void recordReflection(String executionId, boolean ledToCorrection) {
        ExecutionMetrics metrics = executionMetrics.get(executionId);
        if (metrics != null) {
            metrics.setReflectionCount(metrics.getReflectionCount() + 1);
            if (ledToCorrection) {
                metrics.setCorrectionCount(metrics.getCorrectionCount() + 1);
            }
        }
    }

    public void recordProgressAtFailure(String executionId, double progress) {
        ExecutionMetrics metrics = executionMetrics.get(executionId);
        if (metrics != null) {
            metrics.setProgressAtFailure(progress);
        }
    }

    public ExecutionMetrics getExecutionMetrics(String executionId) {
        return executionMetrics.get(executionId);
    }

    public ToolMetrics getToolMetrics(String toolName, String serverName) {
        return toolMetrics.get(serverName + "__" + toolName);
    }

    public ModelMetrics getModelMetrics(String modelId) {
        return modelMetrics.get(modelId);
    }

    public AggregatedMetrics getAggregatedMetrics() {
        AggregatedMetrics aggregated = new AggregatedMetrics();
        
        aggregated.setTotalExecutions(totalExecutions.get());
        aggregated.setSuccessfulExecutions(successfulExecutions.get());
        aggregated.setFailedExecutions(failedExecutions.get());
        
        long total = totalExecutions.get();
        aggregated.setSuccessRate(total > 0 ? (double) successfulExecutions.get() / total : 0.0);
        
        long totalToolCalls = toolMetrics.values().stream()
            .mapToLong(m -> m.getTotalCalls().get())
            .sum();
        aggregated.setTotalToolCalls(totalToolCalls);
        
        long totalTokens = modelMetrics.values().stream()
            .mapToLong(m -> m.getTotalInputTokens().get() + m.getTotalOutputTokens().get())
            .sum();
        aggregated.setTotalTokensUsed(totalTokens);
        
        List<ExecutionMetrics> allMetrics = new ArrayList<>(executionMetrics.values());
        if (!allMetrics.isEmpty()) {
            double avgDuration = allMetrics.stream()
                .filter(m -> m.getDurationMs() > 0)
                .mapToLong(ExecutionMetrics::getDurationMs)
                .average()
                .orElse(0.0);
            aggregated.setAverageExecutionTimeMs(avgDuration);
        }
        
        aggregated.setTopTools(
            toolMetrics.values().stream()
                .sorted((a, b) -> Long.compare(b.getTotalCalls().get(), a.getTotalCalls().get()))
                .limit(10)
                .collect(Collectors.toMap(
                    m -> m.getServerName() + "__" + m.getToolName(),
                    m -> {
                        ToolMetricsSummary summary = new ToolMetricsSummary();
                        summary.setToolName(m.getToolName());
                        summary.setTotalCalls(m.getTotalCalls().get());
                        summary.setSuccessRate(m.getSuccessRate());
                        summary.setAverageExecutionTimeMs(m.getAverageExecutionTime());
                        return summary;
                    }
                ))
        );
        
        aggregated.setModelStats(
            modelMetrics.values().stream()
                .collect(Collectors.toMap(
                    ModelMetrics::getModelId,
                    m -> {
                        ModelMetricsSummary summary = new ModelMetricsSummary();
                        summary.setModelId(m.getModelId());
                        summary.setTotalCalls(m.getTotalCalls().get());
                        summary.setTotalTokens(m.getTotalInputTokens().get() + m.getTotalOutputTokens().get());
                        summary.setAverageLatencyMs(m.getAverageLatency());
                        return summary;
                    }
                ))
        );
        
        return aggregated;
    }

    public List<ExecutionSummary> getRecentExecutions(int limit) {
        synchronized (recentExecutions) {
            return recentExecutions.stream()
                .limit(limit)
                .collect(Collectors.toList());
        }
    }

    public List<ToolMetrics> getTopTools(int limit) {
        return toolMetrics.values().stream()
            .sorted((a, b) -> Long.compare(b.getTotalCalls().get(), a.getTotalCalls().get()))
            .limit(limit)
            .collect(Collectors.toList());
    }

    public List<ToolMetrics> getProblematicTools(double successRateThreshold) {
        return toolMetrics.values().stream()
            .filter(m -> m.getTotalCalls().get() >= 5)
            .filter(m -> m.getSuccessRate() < successRateThreshold)
            .sorted((a, b) -> Double.compare(a.getSuccessRate(), b.getSuccessRate()))
            .collect(Collectors.toList());
    }

    public Map<String, Object> getHealthStatus() {
        Map<String, Object> health = new HashMap<>();
        
        double overallSuccessRate = totalExecutions.get() > 0 
            ? (double) successfulExecutions.get() / totalExecutions.get() 
            : 1.0;
        health.put("overallSuccessRate", overallSuccessRate);
        
        List<ToolMetrics> problematicTools = getProblematicTools(0.7);
        health.put("problematicTools", problematicTools.stream()
            .map(m -> {
                Map<String, Object> toolInfo = new HashMap<>();
                toolInfo.put("tool", m.getServerName() + "__" + m.getToolName());
                toolInfo.put("successRate", m.getSuccessRate());
                toolInfo.put("totalCalls", m.getTotalCalls().get());
                return toolInfo;
            })
            .collect(Collectors.toList()));
        
        long recentFailures = recentExecutions.stream()
            .filter(e -> !e.isSuccess())
            .count();
        health.put("recentFailureCount", recentFailures);
        
        String status = "healthy";
        if (overallSuccessRate < 0.5) {
            status = "unhealthy";
        } else if (overallSuccessRate < 0.8 || !problematicTools.isEmpty()) {
            status = "degraded";
        }
        health.put("status", status);
        
        return health;
    }

    public void cleanupOldMetrics(int maxAgeHours) {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(maxAgeHours);
        
        executionMetrics.entrySet().removeIf(entry -> {
            ExecutionMetrics m = entry.getValue();
            return m.getEndTime() != null && m.getEndTime().isBefore(cutoff);
        });
        
        log.info("清理过期指标，当前执行记录数: {}", executionMetrics.size());
    }

    private ExecutionSummary createSummary(ExecutionMetrics metrics) {
        ExecutionSummary summary = new ExecutionSummary();
        summary.setExecutionId(metrics.getExecutionId());
        summary.setSessionId(metrics.getSessionId());
        summary.setModel(metrics.getModel());
        summary.setMode(metrics.getMode());
        summary.setStartTime(metrics.getStartTime());
        summary.setDurationMs(metrics.getDurationMs());
        summary.setSuccess(metrics.isSuccess());
        summary.setToolCallsCount(metrics.getToolCallsCount());
        summary.setTotalTokens(metrics.getTotalTokens());
        return summary;
    }
}
