package com.superfriend.superfriend.service;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class SemanticToolSelectionService {

    private final Map<String, ToolStatistics> toolStatistics = new ConcurrentHashMap<>();

    @Data
    public static class ToolScore {
        private String toolName;
        private String serverName;
        private double semanticScore;
        private double successRate;
        private double finalScore;
        private String reason;
        private List<String> matchedKeywords;
    }

    @Data
    public static class ToolStatistics {
        private String toolName;
        private String serverName;
        private int totalCalls;
        private int successfulCalls;
        private long totalDurationMs;
        private long averageDurationMs;
        private List<String> commonErrors;
        private LocalDateTime lastUsed;
    }

    public void recordToolCall(String toolName, String serverName, boolean success, long durationMs) {
        String key = serverName + "__" + toolName;

        ToolStatistics stats = toolStatistics.computeIfAbsent(key, k -> {
            ToolStatistics s = new ToolStatistics();
            s.setToolName(toolName);
            s.setServerName(serverName);
            s.setTotalCalls(0);
            s.setSuccessfulCalls(0);
            s.setTotalDurationMs(0);
            s.setCommonErrors(new ArrayList<>());
            return s;
        });

        stats.setTotalCalls(stats.getTotalCalls() + 1);
        if (success) {
            stats.setSuccessfulCalls(stats.getSuccessfulCalls() + 1);
        }
        stats.setTotalDurationMs(stats.getTotalDurationMs() + durationMs);
        stats.setAverageDurationMs(stats.getTotalDurationMs() / stats.getTotalCalls());
        stats.setLastUsed(LocalDateTime.now());
    }

    public void recordToolError(String toolName, String serverName, String error) {
        String key = serverName + "__" + toolName;
        ToolStatistics stats = toolStatistics.get(key);

        if (stats != null && stats.getCommonErrors().size() < 10) {
            if (!stats.getCommonErrors().contains(error)) {
                stats.getCommonErrors().add(error);
            }
        }
    }

    public void clearStatistics() {
        toolStatistics.clear();
        log.info("已清除工具统计");
    }

    public Map<String, ToolStatistics> getToolStatistics() {
        return new HashMap<>(toolStatistics);
    }
}
