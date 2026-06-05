package com.superfriend.superfriend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.superfriend.superfriend.dto.LLMCallRecord;
import com.superfriend.superfriend.dto.LLMRequest;
import com.superfriend.superfriend.dto.ToolDefinitionForLLM;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * LLM 调用监控服务
 * 用于记录和查询 LLM 的请求和响应信息
 * 支持内存缓存 + 文件持久化存储
 */
@Slf4j
@Service
public class LLMCallMonitorService {

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 文件存储目录
     */
    private static final String STORAGE_DIR = "llm_monitor";

    /**
     * 内存缓存：按会话 ID 分组
     */
    private final Map<String, List<LLMCallRecord>> sessionRecordsCache = new ConcurrentHashMap<>();

    /**
     * 每会话最大记录数
     */
    private static final int MAX_RECORDS_PER_SESSION = 50;

    /**
     * 最大响应内容长度（超过则截断）
     */
    private static final int MAX_RESPONSE_LENGTH = 50000;

    /**
     * 记录过期时间（小时）
     */
    private static final int RECORD_EXPIRY_HOURS = 72;

    /**
     * 开始记录 LLM 调用
     *
     * @param sessionId 会话 ID
     * @param request   LLM 请求
     * @param tools     工具定义
     * @return 调用记录
     */
    public LLMCallRecord startCall(String sessionId, LLMRequest request,
                                    List<ToolDefinitionForLLM> tools) {
        // 防御性检查
        if (request == null) {
            log.warn("[LLMMonitor] startCall called with null request: sessionId={}", sessionId);
            return null;
        }

        log.info("[LLMMonitor] startCall called: sessionId={}, model={}", sessionId, request.getModel());

        String callId = generateCallId();

        // 构建工具定义列表
        List<Map<String, Object>> toolsList = null;
        if (tools != null && !tools.isEmpty()) {
            toolsList = new ArrayList<>();
            for (ToolDefinitionForLLM tool : tools) {
                if (tool.getName() == null || tool.getName().isEmpty() ||
                    tool.getName().startsWith("[未启动]")) {
                    continue;
                }
                Map<String, Object> toolDef = new HashMap<>();
                toolDef.put("type", "function");
                Map<String, Object> function = new HashMap<>();
                function.put("name", tool.getName());
                function.put("description", tool.getDescription());
                if (tool.getParameters() != null) {
                    function.put("parameters", tool.getParameters());
                }
                toolDef.put("function", function);
                toolsList.add(toolDef);
            }
        }

        LLMCallRecord record = LLMCallRecord.builder()
                .callId(callId)
                .sessionId(sessionId)
                .model(request.getModel())
                .apiUrl(sanitizeApiUrl(request.getApiUrl()))
                .requestTime(LocalDateTime.now())
                .messages(request.getMessages() != null ?
                        new ArrayList<>(request.getMessages()) : null)
                .tools(toolsList)
                .temperature(request.getTemperature())
                .maxTokens(request.getMaxTokens())
                .success(true)
                .build();

        // 添加到内存缓存
        addToCache(sessionId, record);

        log.debug("[LLMMonitor] 开始记录调用: callId={}, sessionId={}, model={}",
                callId, sessionId, request.getModel());

        return record;
    }

    /**
     * 结束记录 LLM 调用
     *
     * @param record            调用记录
     * @param responseContent   响应内容
     * @param reasoningContent  推理内容
     * @param promptTokens      输入 Token 数
     * @param completionTokens  输出 Token 数
     * @param finishReason      完成原因
     * @param error             错误信息
     */
    public void endCall(LLMCallRecord record, String responseContent, String reasoningContent,
                        Long promptTokens, Long completionTokens,
                        String finishReason, String error) {
        if (record == null) return;

        record.setResponseTime(LocalDateTime.now());
        record.setDurationMs(calculateDuration(record.getRequestTime(), record.getResponseTime()));
        record.setFinishReason(finishReason);

        // Token 统计
        if (promptTokens != null) {
            record.setPromptTokens(promptTokens);
        }
        if (completionTokens != null) {
            record.setCompletionTokens(completionTokens);
        }
        if (promptTokens != null && completionTokens != null) {
            record.setTotalTokens(promptTokens + completionTokens);
        }

        // 响应内容（截断超长内容）
        if (responseContent != null) {
            record.setResponseContent(truncate(responseContent, MAX_RESPONSE_LENGTH));
        }
        if (reasoningContent != null) {
            record.setReasoningContent(truncate(reasoningContent, MAX_RESPONSE_LENGTH));
        }

        // 错误处理
        if (error != null && !error.isEmpty()) {
            record.setSuccess(false);
            record.setErrorMessage(error);
        }

        // 保存到文件
        saveRecordToFile(record);

        log.debug("[LLMMonitor] 结束记录调用: callId={}, duration={}ms, tokens={}, success={}",
                record.getCallId(), record.getDurationMs(), record.getTotalTokens(), record.getSuccess());
    }

    /**
     * 获取会话的所有调用记录
     *
     * @param sessionId 会话 ID
     * @return 调用记录列表
     */
    public List<LLMCallRecord> getSessionRecords(String sessionId) {
        log.info("[LLMMonitor] getSessionRecords called: sessionId={}", sessionId);

        // 先尝试从内存缓存获取
        List<LLMCallRecord> records = sessionRecordsCache.get(sessionId);
        if (records == null) {
            // 从文件加载
            records = loadSessionRecordsFromDisk(sessionId);
            if (records != null) {
                sessionRecordsCache.put(sessionId, records);
            }
        }

        if (records == null || records.isEmpty()) {
            log.info("[LLMMonitor] getSessionRecords: no records found for sessionId={}", sessionId);
            return new ArrayList<>();
        }

        log.info("[LLMMonitor] getSessionRecords: found {} records for sessionId={}", records.size(), sessionId);

        // 返回副本，按时间倒序
        List<LLMCallRecord> result = new ArrayList<>(records);
        result.sort((a, b) -> {
            if (a.getRequestTime() == null) return 1;
            if (b.getRequestTime() == null) return -1;
            return b.getRequestTime().compareTo(a.getRequestTime());
        });
        return result;
    }

    /**
     * 获取单条调用记录详情
     *
     * @param callId    调用 ID
     * @param sessionId 会话 ID
     * @return 调用记录，不存在则返回 null
     */
    public LLMCallRecord getRecordDetail(String callId, String sessionId) {
        // 先从内存缓存查找
        List<LLMCallRecord> records = sessionRecordsCache.get(sessionId);
        if (records != null) {
            for (LLMCallRecord record : records) {
                if (record.getCallId().equals(callId)) {
                    return record;
                }
            }
        }

        // 从文件加载
        return loadRecordFromFile(callId, sessionId);
    }

    /**
     * 清除会话的监控记录
     *
     * @param sessionId 会话 ID
     */
    public void clearSessionRecords(String sessionId) {
        // 清除内存缓存
        sessionRecordsCache.remove(sessionId);

        // 删除文件
        deleteSessionFiles(sessionId);

        log.debug("[LLMMonitor] 清除会话记录: sessionId={}", sessionId);
    }

    /**
     * 获取所有会话的记录数量统计
     *
     * @return 会话 ID -> 记录数量
     */
    public Map<String, Integer> getSessionStats() {
        Map<String, Integer> stats = new HashMap<>();

        // 统计内存缓存
        sessionRecordsCache.forEach((sessionId, records) ->
                stats.put(sessionId, records.size()));

        // 扫描文件系统获取更多会话
        try {
            Path dirPath = Paths.get(STORAGE_DIR);
            if (Files.exists(dirPath)) {
                Set<String> fileSessions = new HashSet<>();
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(dirPath, "llm_*.json")) {
                    for (Path filePath : stream) {
                        String filename = filePath.getFileName().toString();
                        // 文件名格式: llm_{timestamp}_{uuid}.json
                        // 从文件内容读取 sessionId
                        try {
                            String content = new String(Files.readAllBytes(filePath), StandardCharsets.UTF_8);
                            LLMCallRecord record = objectMapper.readValue(content, LLMCallRecord.class);
                            if (record.getSessionId() != null) {
                                fileSessions.add(record.getSessionId());
                            }
                        } catch (Exception ignored) {
                        }
                    }
                }

                // 合并统计
                for (String sessionId : fileSessions) {
                    if (!stats.containsKey(sessionId)) {
                        stats.put(sessionId, getSessionRecords(sessionId).size());
                    }
                }
            }
        } catch (IOException e) {
            log.warn("[LLMMonitor] 扫描文件目录失败: {}", e.getMessage());
        }

        return stats;
    }

    /**
     * 定时清理过期记录（每小时执行）
     */
    @Scheduled(cron = "0 0 * * * *")
    public void cleanupExpiredRecords() {
        LocalDateTime expiryTime = LocalDateTime.now().minusHours(RECORD_EXPIRY_HOURS);
        int removedFromCache = 0;
        int removedFiles = 0;

        // 清理内存缓存
        for (List<LLMCallRecord> records : sessionRecordsCache.values()) {
            Iterator<LLMCallRecord> iterator = records.iterator();
            while (iterator.hasNext()) {
                LLMCallRecord record = iterator.next();
                if (record.getRequestTime() != null && record.getRequestTime().isBefore(expiryTime)) {
                    iterator.remove();
                    removedFromCache++;
                }
            }
        }

        // 清理空会话
        sessionRecordsCache.entrySet().removeIf(entry -> entry.getValue().isEmpty());

        // 清理过期文件
        try {
            Path dirPath = Paths.get(STORAGE_DIR);
            if (Files.exists(dirPath)) {
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(dirPath, "llm_*.json")) {
                    for (Path filePath : stream) {
                        try {
                            String content = new String(Files.readAllBytes(filePath), StandardCharsets.UTF_8);
                            LLMCallRecord record = objectMapper.readValue(content, LLMCallRecord.class);
                            if (record.getRequestTime() != null && record.getRequestTime().isBefore(expiryTime)) {
                                Files.delete(filePath);
                                removedFiles++;
                            }
                        } catch (Exception e) {
                            // 解析失败的文件，检查修改时间
                            try {
                                long lastModified = Files.getLastModifiedTime(filePath).toMillis();
                                if (System.currentTimeMillis() - lastModified > RECORD_EXPIRY_HOURS * 60 * 60 * 1000L) {
                                    Files.delete(filePath);
                                    removedFiles++;
                                }
                            } catch (IOException ignored) {
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            log.warn("[LLMMonitor] 清理文件失败: {}", e.getMessage());
        }

        if (removedFromCache > 0 || removedFiles > 0) {
            log.info("[LLMMonitor] 清理过期记录: 缓存 {} 条, 文件 {} 个", removedFromCache, removedFiles);
        }
    }

    // ========== 私有方法 ==========

    private String generateCallId() {
        return "llm_" + System.currentTimeMillis() + "_" +
                UUID.randomUUID().toString().substring(0, 8);
    }

    private void addToCache(String sessionId, LLMCallRecord record) {
        // 防止 null sessionId 导致 NPE
        if (sessionId == null) {
            sessionId = "no-session";
            log.warn("[LLMMonitor] addToCache called with null sessionId, using 'no-session' as fallback");
        }

        sessionRecordsCache.computeIfAbsent(sessionId, k -> new ArrayList<>())
                .add(record);

        // 限制每会话记录数
        List<LLMCallRecord> records = sessionRecordsCache.get(sessionId);
        while (records.size() > MAX_RECORDS_PER_SESSION) {
            records.remove(0); // 移除最旧的
        }
    }

    private void saveRecordToFile(LLMCallRecord record) {
        try {
            Path dirPath = Paths.get(STORAGE_DIR);
            if (!Files.exists(dirPath)) {
                Files.createDirectories(dirPath);
            }

            String filename = record.getCallId() + ".json";
            Path filePath = dirPath.resolve(filename);

            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(record);
            Files.write(filePath, json.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            log.debug("[LLMMonitor] 保存记录到文件: {}", filename);

        } catch (Exception e) {
            log.error("[LLMMonitor] 保存记录失败: {}", e.getMessage());
        }
    }

    private LLMCallRecord loadRecordFromFile(String callId, String sessionId) {
        try {
            Path filePath = Paths.get(STORAGE_DIR, callId + ".json");
            if (!Files.exists(filePath)) {
                return null;
            }

            String content = new String(Files.readAllBytes(filePath), StandardCharsets.UTF_8);
            LLMCallRecord record = objectMapper.readValue(content, LLMCallRecord.class);

            // 验证 sessionId 匹配
            if (sessionId != null && !sessionId.equals(record.getSessionId())) {
                return null;
            }

            return record;

        } catch (Exception e) {
            log.warn("[LLMMonitor] 加载记录失败: callId={}, error={}", callId, e.getMessage());
            return null;
        }
    }

    private List<LLMCallRecord> loadSessionRecordsFromDisk(String sessionId) {
        try {
            Path dirPath = Paths.get(STORAGE_DIR);
            if (!Files.exists(dirPath)) {
                return new ArrayList<>();
            }

            List<LLMCallRecord> records = new ArrayList<>();

            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dirPath, "llm_*.json")) {
                for (Path filePath : stream) {
                    try {
                        String content = new String(Files.readAllBytes(filePath), StandardCharsets.UTF_8);
                        LLMCallRecord record = objectMapper.readValue(content, LLMCallRecord.class);
                        if (sessionId.equals(record.getSessionId())) {
                            records.add(record);
                        }
                    } catch (Exception e) {
                        log.warn("[LLMMonitor] 加载记录文件失败: {}", filePath);
                    }
                }
            }

            // 按时间排序
            records.sort(Comparator.comparing(
                    LLMCallRecord::getRequestTime,
                    Comparator.nullsLast(Comparator.naturalOrder())
            ));

            return records;

        } catch (Exception e) {
            log.error("[LLMMonitor] 加载会话记录失败: sessionId={}, error={}", sessionId, e.getMessage());
            return new ArrayList<>();
        }
    }

    private void deleteSessionFiles(String sessionId) {
        try {
            Path dirPath = Paths.get(STORAGE_DIR);
            if (!Files.exists(dirPath)) {
                return;
            }

            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dirPath, "llm_*.json")) {
                for (Path filePath : stream) {
                    try {
                        String content = new String(Files.readAllBytes(filePath), StandardCharsets.UTF_8);
                        LLMCallRecord record = objectMapper.readValue(content, LLMCallRecord.class);
                        if (sessionId.equals(record.getSessionId())) {
                            Files.delete(filePath);
                        }
                    } catch (Exception e) {
                        // 忽略单个文件删除失败
                    }
                }
            }

            log.debug("[LLMMonitor] 删除会话文件: sessionId={}", sessionId);

        } catch (Exception e) {
            log.warn("[LLMMonitor] 删除会话文件失败: sessionId={}, error={}", sessionId, e.getMessage());
        }
    }

    private Long calculateDuration(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) return null;
        return ChronoUnit.MILLIS.between(start, end);
    }

    private String truncate(String str, int maxLength) {
        if (str == null) return null;
        if (str.length() <= maxLength) return str;
        return str.substring(0, maxLength) + "...[截断]";
    }

    private String sanitizeApiUrl(String apiUrl) {
        if (apiUrl == null || apiUrl.isEmpty()) {
            return "";
        }
        String sanitized = apiUrl.trim();
        while (sanitized.endsWith(",") || sanitized.endsWith("/")) {
            if (sanitized.endsWith(",")) {
                sanitized = sanitized.substring(0, sanitized.length() - 1).trim();
            } else if (sanitized.endsWith("/") && !sanitized.endsWith("//")) {
                break;
            } else {
                break;
            }
        }
        return sanitized;
    }
}
