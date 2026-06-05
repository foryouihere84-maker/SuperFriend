package com.superfriend.superfriend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ExecutionTraceService {

    @Autowired
    private ObjectMapper objectMapper;

    private static final String TRACE_DIR = "traces";
    private static final int MAX_TRACES_PER_SESSION = 100;
    private static final long TRACE_EXPIRY_HOURS = 72;

    private final Map<String, ExecutionTrace> activeTraces = new ConcurrentHashMap<>();
    private final Map<String, List<ExecutionTrace>> sessionTraces = new ConcurrentHashMap<>();
    private final AtomicLong traceCounter = new AtomicLong(0);

    @Data
    public static class ExecutionTrace {
        private String traceId;
        private String sessionId;
        private String planId;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private Long durationMs;
        private TraceStatus status;
        private String userMessage;
        private String model;
        private Long userId;
        private List<TraceEvent> events;
        private TraceSummary summary;
        private Map<String, Object> metadata;
        private String error;
    }

    @Data
    public static class TraceEvent {
        private String eventId;
        private LocalDateTime timestamp;
        private TraceEventType type;
        private String name;
        private String description;
        private Long durationMs;
        private Map<String, Object> inputData;
        private Map<String, Object> outputData;
        private boolean success;
        private String errorMessage;
        private int sequence;
        private String parentEventId;
        private List<String> childEventIds;
    }

    @Data
    public static class TraceSummary {
        private int totalEvents;
        private int successfulEvents;
        private int failedEvents;
        private int toolCalls;
        private int llmCalls;
        private int skillCalls;
        private long totalToolTimeMs;
        private long totalLlmTimeMs;
        private long totalSkillTimeMs;
        private List<String> toolsUsed;
        private List<String> skillsUsed;
        private int reflectionCount;
        private int fallbackCount;
        private int retryCount;
    }

    public enum TraceStatus {
        RUNNING,
        COMPLETED,
        FAILED,
        CANCELLED,
        TIMEOUT
    }

    public enum TraceEventType {
        PLAN_START,
        PLAN_END,
        STEP_START,
        STEP_END,
        TOOL_CALL,
        TOOL_RESULT,
        LLM_CALL,
        LLM_RESPONSE,
        SKILL_CALL,
        SKILL_RESULT,
        REFLECTION,
        FALLBACK,
        RETRY,
        ERROR,
        CHECKPOINT,
        PROGRESS
    }

    public ExecutionTrace startTrace(String sessionId, String planId, String userMessage, String model, Long userId) {
        String traceId = generateTraceId();

        ExecutionTrace trace = new ExecutionTrace();
        trace.setTraceId(traceId);
        trace.setSessionId(sessionId);
        trace.setPlanId(planId);
        trace.setStartTime(LocalDateTime.now());
        trace.setStatus(TraceStatus.RUNNING);
        trace.setUserMessage(userMessage);
        trace.setModel(model);
        trace.setUserId(userId);
        trace.setEvents(new ArrayList<>());
        trace.setMetadata(new HashMap<>());
        trace.setSummary(new TraceSummary());

        activeTraces.put(traceId, trace);
        sessionTraces.computeIfAbsent(sessionId, k -> new ArrayList<>()).add(trace);

        log.debug("开始追踪: traceId={}, sessionId={}", traceId, sessionId);

        return trace;
    }

    public String addEvent(String traceId, TraceEventType type, String name, String description) {
        return addEvent(traceId, type, name, description, null, null, null, true, null);
    }

    public String addEvent(String traceId, TraceEventType type, String name, String description,
                           Map<String, Object> inputData, Map<String, Object> outputData,
                           Long durationMs, boolean success, String errorMessage) {
        ExecutionTrace trace = activeTraces.get(traceId);
        if (trace == null) {
            log.warn("追踪不存在: {}", traceId);
            return null;
        }

        String eventId = generateEventId();
        TraceEvent event = new TraceEvent();
        event.setEventId(eventId);
        event.setTimestamp(LocalDateTime.now());
        event.setType(type);
        event.setName(name);
        event.setDescription(description);
        event.setInputData(inputData);
        event.setOutputData(outputData);
        event.setDurationMs(durationMs);
        event.setSuccess(success);
        event.setErrorMessage(errorMessage);
        event.setSequence(trace.getEvents().size());
        event.setChildEventIds(new ArrayList<>());

        trace.getEvents().add(event);
        updateSummary(trace, event);

        log.debug("添加追踪事件: traceId={}, type={}, name={}", traceId, type, name);

        return eventId;
    }

    public void addToolCallEvent(String traceId, String toolName, String serverName,
                                 Map<String, Object> arguments, String result,
                                 long durationMs, boolean success, String error) {
        Map<String, Object> input = new HashMap<>();
        input.put("toolName", toolName);
        input.put("serverName", serverName);
        input.put("arguments", arguments);

        Map<String, Object> output = new HashMap<>();
        output.put("result", result != null && result.length() > 500 ? result.substring(0, 500) + "..." : result);

        addEvent(traceId, TraceEventType.TOOL_CALL, toolName, "调用工具: " + serverName + "__" + toolName,
                input, output, durationMs, success, error);
    }

    public void addLLMCallEvent(String traceId, String prompt, String response,
                                long durationMs, boolean success, String error) {
        Map<String, Object> input = new HashMap<>();
        input.put("prompt", prompt != null && prompt.length() > 500 ? prompt.substring(0, 500) + "..." : prompt);

        Map<String, Object> output = new HashMap<>();
        output.put("response", response != null && response.length() > 500 ? response.substring(0, 500) + "..." : response);

        addEvent(traceId, TraceEventType.LLM_CALL, "LLM调用", "调用大语言模型",
                input, output, durationMs, success, error);
    }

    public void addSkillCallEvent(String traceId, String skillName, Map<String, Object> context,
                                  String result, long durationMs, boolean success, String error) {
        Map<String, Object> input = new HashMap<>();
        input.put("skillName", skillName);
        input.put("context", context);

        Map<String, Object> output = new HashMap<>();
        output.put("result", result);

        addEvent(traceId, TraceEventType.SKILL_CALL, skillName, "执行技能: " + skillName,
                input, output, durationMs, success, error);
    }

    public void addReflectionEvent(String traceId, String reason, boolean needsCorrection, double confidence) {
        Map<String, Object> data = new HashMap<>();
        data.put("reason", reason);
        data.put("needsCorrection", needsCorrection);
        data.put("confidence", confidence);

        addEvent(traceId, TraceEventType.REFLECTION, "Reflection", "反思检查: " + reason,
                null, data, null, true, null);
    }

    public void addFallbackEvent(String traceId, String originalTool, String alternativeTool, String strategy) {
        Map<String, Object> data = new HashMap<>();
        data.put("originalTool", originalTool);
        data.put("alternativeTool", alternativeTool);
        data.put("strategy", strategy);

        addEvent(traceId, TraceEventType.FALLBACK, "Fallback", "回退策略: " + originalTool + " -> " + alternativeTool,
                null, data, null, true, null);
    }

    public void addRetryEvent(String traceId, String operation, int attempt, String reason) {
        Map<String, Object> data = new HashMap<>();
        data.put("operation", operation);
        data.put("attempt", attempt);
        data.put("reason", reason);

        addEvent(traceId, TraceEventType.RETRY, "Retry", "重试: " + operation + " (第" + attempt + "次)",
                null, data, null, true, null);
    }

    public void endTrace(String traceId, TraceStatus status, String error) {
        ExecutionTrace trace = activeTraces.get(traceId);
        if (trace == null) {
            log.warn("追踪不存在: {}", traceId);
            return;
        }

        trace.setEndTime(LocalDateTime.now());
        trace.setStatus(status);
        trace.setError(error);

        if (trace.getStartTime() != null && trace.getEndTime() != null) {
            long duration = java.time.Duration.between(trace.getStartTime(), trace.getEndTime()).toMillis();
            trace.setDurationMs(duration);
        }

        finalizeSummary(trace);

        saveTraceToFile(trace);

        activeTraces.remove(traceId);

        log.info("结束追踪: traceId={}, status={}, duration={}ms, events={}",
                traceId, status, trace.getDurationMs(), trace.getEvents().size());
    }

    public ExecutionTrace getTrace(String traceId) {
        ExecutionTrace trace = activeTraces.get(traceId);
        if (trace != null) {
            return trace;
        }

        return loadTraceFromFile(traceId);
    }

    public List<ExecutionTrace> getSessionTraces(String sessionId) {
        List<ExecutionTrace> traces = sessionTraces.get(sessionId);
        if (traces == null) {
            traces = loadSessionTracesFromDisk(sessionId);
            if (traces != null) {
                sessionTraces.put(sessionId, traces);
            }
        }
        return traces != null ? new ArrayList<>(traces) : new ArrayList<>();
    }

    public List<ExecutionTrace> getRecentTraces(int limit) {
        List<ExecutionTrace> allTraces = new ArrayList<>();

        for (List<ExecutionTrace> traces : sessionTraces.values()) {
            allTraces.addAll(traces);
        }

        allTraces.sort(Comparator.comparing(ExecutionTrace::getStartTime).reversed());

        return allTraces.stream().limit(limit).collect(Collectors.toList());
    }

    public TraceStatistics getStatistics(String sessionId) {
        List<ExecutionTrace> traces = getSessionTraces(sessionId);

        TraceStatistics stats = new TraceStatistics();
        stats.setSessionId(sessionId);
        stats.setTotalTraces(traces.size());

        if (traces.isEmpty()) {
            return stats;
        }

        long totalDuration = 0;
        int totalEvents = 0;
        int successfulTraces = 0;
        int failedTraces = 0;
        Map<String, Integer> toolUsage = new HashMap<>();
        Map<String, Integer> toolSuccess = new HashMap<>();

        for (ExecutionTrace trace : traces) {
            if (trace.getDurationMs() != null) {
                totalDuration += trace.getDurationMs();
            }

            if (trace.getSummary() != null) {
                totalEvents += trace.getSummary().getTotalEvents();

                if (trace.getSummary().getToolsUsed() != null) {
                    for (String tool : trace.getSummary().getToolsUsed()) {
                        toolUsage.merge(tool, 1, Integer::sum);
                    }
                }
            }

            if (trace.getStatus() == TraceStatus.COMPLETED) {
                successfulTraces++;
            } else if (trace.getStatus() == TraceStatus.FAILED) {
                failedTraces++;
            }
        }

        stats.setTotalDurationMs(totalDuration);
        stats.setTotalEvents(totalEvents);
        stats.setSuccessfulTraces(successfulTraces);
        stats.setFailedTraces(failedTraces);
        stats.setToolUsage(toolUsage);
        stats.setToolSuccessRate(toolSuccess);

        if (!traces.isEmpty()) {
            stats.setAverageDurationMs(totalDuration / traces.size());
        }

        return stats;
    }

    public String exportTraceAsJson(String traceId) {
        ExecutionTrace trace = getTrace(traceId);
        if (trace == null) {
            return null;
        }

        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(trace);
        } catch (Exception e) {
            log.error("导出追踪失败: {}", e.getMessage());
            return null;
        }
    }

    public String exportTraceAsMarkdown(String traceId) {
        ExecutionTrace trace = getTrace(traceId);
        if (trace == null) {
            return null;
        }

        StringBuilder sb = new StringBuilder();

        sb.append("# 执行追踪报告\n\n");
        sb.append("**追踪ID**: ").append(trace.getTraceId()).append("\n");
        sb.append("**会话ID**: ").append(trace.getSessionId()).append("\n");
        sb.append("**状态**: ").append(trace.getStatus()).append("\n");
        sb.append("**开始时间**: ").append(trace.getStartTime()).append("\n");
        sb.append("**结束时间**: ").append(trace.getEndTime()).append("\n");
        sb.append("**总耗时**: ").append(trace.getDurationMs()).append("ms\n\n");

        if (trace.getUserMessage() != null) {
            sb.append("## 用户请求\n```\n").append(trace.getUserMessage()).append("\n```\n\n");
        }

        if (trace.getSummary() != null) {
            TraceSummary summary = trace.getSummary();
            sb.append("## 执行摘要\n");
            sb.append("- 总事件数: ").append(summary.getTotalEvents()).append("\n");
            sb.append("- 成功事件: ").append(summary.getSuccessfulEvents()).append("\n");
            sb.append("- 失败事件: ").append(summary.getFailedEvents()).append("\n");
            sb.append("- 工具调用: ").append(summary.getToolCalls()).append("\n");
            sb.append("- LLM调用: ").append(summary.getLlmCalls()).append("\n");
            sb.append("- 技能调用: ").append(summary.getSkillCalls()).append("\n");
            sb.append("- 反思次数: ").append(summary.getReflectionCount()).append("\n");
            sb.append("- 回退次数: ").append(summary.getFallbackCount()).append("\n");
            sb.append("- 重试次数: ").append(summary.getRetryCount()).append("\n\n");
        }

        if (trace.getEvents() != null && !trace.getEvents().isEmpty()) {
            sb.append("## 事件详情\n\n");
            sb.append("| 序号 | 时间 | 类型 | 名称 | 耗时 | 状态 |\n");
            sb.append("|------|------|------|------|------|------|\n");

            for (TraceEvent event : trace.getEvents()) {
                sb.append(String.format("| %d | %s | %s | %s | %sms | %s |\n",
                        event.getSequence(),
                        event.getTimestamp().format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                        event.getType(),
                        event.getName(),
                        event.getDurationMs() != null ? event.getDurationMs() : "-",
                        event.isSuccess() ? "✓" : "✗"));
            }
        }

        if (trace.getError() != null) {
            sb.append("\n## 错误信息\n```\n").append(trace.getError()).append("\n```\n");
        }

        return sb.toString();
    }

    @org.springframework.scheduling.annotation.Scheduled(cron = "0 30 * * * *")  // 每小时30分执行
    public void cleanupExpiredTraces() {
        LocalDateTime expiryTime = LocalDateTime.now().minusHours(TRACE_EXPIRY_HOURS);
        int removed = 0;

        for (List<ExecutionTrace> traces : sessionTraces.values()) {
            Iterator<ExecutionTrace> iterator = traces.iterator();
            while (iterator.hasNext()) {
                ExecutionTrace trace = iterator.next();
                if (trace.getStartTime() != null && trace.getStartTime().isBefore(expiryTime)) {
                    deleteTraceFile(trace);
                    iterator.remove();
                    removed++;
                }
            }
        }

        if (removed > 0) {
            log.info("清理过期追踪: {} 个", removed);
        }
    }

    private void updateSummary(ExecutionTrace trace, TraceEvent event) {
        TraceSummary summary = trace.getSummary();
        if (summary == null) {
            summary = new TraceSummary();
            trace.setSummary(summary);
        }

        summary.setTotalEvents(summary.getTotalEvents() + 1);

        if (event.isSuccess()) {
            summary.setSuccessfulEvents(summary.getSuccessfulEvents() + 1);
        } else {
            summary.setFailedEvents(summary.getFailedEvents() + 1);
        }

        switch (event.getType()) {
            case TOOL_CALL:
                summary.setToolCalls(summary.getToolCalls() + 1);
                if (event.getDurationMs() != null) {
                    summary.setTotalToolTimeMs(summary.getTotalToolTimeMs() + event.getDurationMs());
                }
                if (summary.getToolsUsed() == null) {
                    summary.setToolsUsed(new ArrayList<>());
                }
                if (!summary.getToolsUsed().contains(event.getName())) {
                    summary.getToolsUsed().add(event.getName());
                }
                break;
            case LLM_CALL:
                summary.setLlmCalls(summary.getLlmCalls() + 1);
                if (event.getDurationMs() != null) {
                    summary.setTotalLlmTimeMs(summary.getTotalLlmTimeMs() + event.getDurationMs());
                }
                break;
            case SKILL_CALL:
                summary.setSkillCalls(summary.getSkillCalls() + 1);
                if (event.getDurationMs() != null) {
                    summary.setTotalSkillTimeMs(summary.getTotalSkillTimeMs() + event.getDurationMs());
                }
                if (summary.getSkillsUsed() == null) {
                    summary.setSkillsUsed(new ArrayList<>());
                }
                if (!summary.getSkillsUsed().contains(event.getName())) {
                    summary.getSkillsUsed().add(event.getName());
                }
                break;
            case REFLECTION:
                summary.setReflectionCount(summary.getReflectionCount() + 1);
                break;
            case FALLBACK:
                summary.setFallbackCount(summary.getFallbackCount() + 1);
                break;
            case RETRY:
                summary.setRetryCount(summary.getRetryCount() + 1);
                break;
        }
    }

    private void finalizeSummary(ExecutionTrace trace) {
        TraceSummary summary = trace.getSummary();
        if (summary == null) {
            return;
        }

        if (summary.getToolsUsed() == null) {
            summary.setToolsUsed(new ArrayList<>());
        }
        if (summary.getSkillsUsed() == null) {
            summary.setSkillsUsed(new ArrayList<>());
        }
    }

    private String generateTraceId() {
        return "trace_" + System.currentTimeMillis() + "_" + traceCounter.incrementAndGet();
    }

    private String generateEventId() {
        return "evt_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8);
    }

    private void saveTraceToFile(ExecutionTrace trace) {
        try {
            Path dirPath = Paths.get(TRACE_DIR);
            if (!Files.exists(dirPath)) {
                Files.createDirectories(dirPath);
            }

            String filename = trace.getTraceId() + ".json";
            Path filePath = dirPath.resolve(filename);

            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(trace);
            Files.write(filePath, json.getBytes(java.nio.charset.StandardCharsets.UTF_8), 
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            log.debug("保存追踪到文件: {}", filename);

        } catch (Exception e) {
            log.error("保存追踪失败: {}", e.getMessage());
        }
    }

    private ExecutionTrace loadTraceFromFile(String traceId) {
        try {
            Path filePath = Paths.get(TRACE_DIR, traceId + ".json");
            if (!Files.exists(filePath)) {
                return null;
            }

            String content = new String(Files.readAllBytes(filePath), java.nio.charset.StandardCharsets.UTF_8);
            return objectMapper.readValue(content, ExecutionTrace.class);

        } catch (Exception e) {
            log.warn("加载追踪失败: {}", e.getMessage());
            return null;
        }
    }

    private List<ExecutionTrace> loadSessionTracesFromDisk(String sessionId) {
        try {
            Path dirPath = Paths.get(TRACE_DIR);
            if (!Files.exists(dirPath)) {
                return null;
            }

            List<ExecutionTrace> traces = new ArrayList<>();

            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dirPath, "trace_*.json")) {
                for (Path filePath : stream) {
                    try {
                        String content = new String(Files.readAllBytes(filePath), java.nio.charset.StandardCharsets.UTF_8);
                        ExecutionTrace trace = objectMapper.readValue(content, ExecutionTrace.class);
                        if (sessionId.equals(trace.getSessionId())) {
                            traces.add(trace);
                        }
                    } catch (Exception e) {
                        log.warn("加载追踪文件失败: {}", filePath);
                    }
                }
            }

            traces.sort(Comparator.comparing(ExecutionTrace::getStartTime));

            return traces;

        } catch (Exception e) {
            log.error("加载追踪目录失败: {}", e.getMessage());
            return null;
        }
    }

    private void deleteTraceFile(ExecutionTrace trace) {
        try {
            Path filePath = Paths.get(TRACE_DIR, trace.getTraceId() + ".json");
            Files.deleteIfExists(filePath);
        } catch (Exception e) {
            log.warn("删除追踪文件失败: {}", e.getMessage());
        }
    }

    @Data
    public static class TraceStatistics {
        private String sessionId;
        private int totalTraces;
        private int successfulTraces;
        private int failedTraces;
        private long totalDurationMs;
        private long averageDurationMs;
        private int totalEvents;
        private Map<String, Integer> toolUsage;
        private Map<String, Integer> toolSuccessRate;
    }
}
