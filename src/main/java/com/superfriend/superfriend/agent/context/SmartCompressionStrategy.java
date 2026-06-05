package com.superfriend.superfriend.agent.context;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;

@Slf4j
@Component
public class SmartCompressionStrategy {

    private static final int TOKEN_ESTIMATE_RATIO = 4;
    private static final int MIN_MESSAGES_TO_KEEP = 5;
<<<<<<< HEAD
    private static final int MAX_TOOL_RESULT_LENGTH = 5000;
    private static final int MAX_ERROR_LENGTH = 5000;
    private static final int MAX_SEARCH_RESULT_LENGTH = 5000;
=======
    private static final int MAX_TOOL_RESULT_LENGTH = 3000;
    private static final int MAX_ERROR_LENGTH = 500;
    private static final int MAX_SEARCH_RESULT_LENGTH = 2000;
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
    
    private static final Pattern ERROR_PATTERN = Pattern.compile(
        "(?i)(error|exception|failed|failure|timeout|超时|失败|错误)"
    );
    
    private static final Pattern SEARCH_PATTERN = Pattern.compile(
        "(?i)(search|result|found|results|搜索|结果)"
    );

    public CompressionDecision shouldCompress(List<Map<String, Object>> messages, 
                                               long currentTokens, 
                                               long maxTokens,
                                               CompressionContext context) {
        CompressionDecision decision = new CompressionDecision();
        decision.setCurrentTokens(currentTokens);
        decision.setMaxTokens(maxTokens);
        decision.setMessageCount(messages.size());
        
        double usageRatio = (double) currentTokens / maxTokens;
        decision.setUsageRatio(usageRatio);
        
        if (usageRatio < 0.5) {
            decision.setNeeded(false);
            decision.setReason("Token使用率低于50%，无需压缩");
            return decision;
        }
        
        if (messages.size() <= MIN_MESSAGES_TO_KEEP) {
            decision.setNeeded(false);
            decision.setReason("消息数量过少，无需压缩");
            return decision;
        }
        
        if (context != null && context.isNearCompletion()) {
            decision.setNeeded(false);
            decision.setReason("任务接近完成，跳过压缩");
            return decision;
        }
        
        if (usageRatio >= 0.9) {
            decision.setNeeded(true);
            decision.setLevel(CompressionLevel.AGGRESSIVE);
            decision.setReason("Token使用率超过90%，执行激进压缩");
            decision.setTargetTokens((long)(maxTokens * 0.5));
        } else if (usageRatio >= 0.75) {
            decision.setNeeded(true);
            decision.setLevel(CompressionLevel.MODERATE);
            decision.setReason("Token使用率超过75%，执行中等压缩");
            decision.setTargetTokens((long)(maxTokens * 0.6));
        } else if (usageRatio >= 0.6) {
            decision.setNeeded(true);
            decision.setLevel(CompressionLevel.LIGHT);
            decision.setReason("Token使用率超过60%，执行轻度压缩");
            decision.setTargetTokens((long)(maxTokens * 0.7));
        } else {
            decision.setNeeded(false);
            decision.setReason("Token使用率在可接受范围内");
        }
        
        log.info("[CompressionStrategy] 压缩决策: needed={}, level={}, usage={}%, target={}", 
                decision.isNeeded(), decision.getLevel(), 
                String.format("%.1f", usageRatio * 100), decision.getTargetTokens());
        
        return decision;
    }

    public List<Map<String, Object>> executeCompression(List<Map<String, Object>> messages,
                                                         CompressionDecision decision,
                                                         CompressionContext context) {
        if (!decision.isNeeded()) {
            return messages;
        }
        
        List<Map<String, Object>> compressed = new ArrayList<>();
        long targetTokens = decision.getTargetTokens();
        
        Map<String, Object> systemMessage = extractSystemMessage(messages);
        if (systemMessage != null) {
            compressed.add(systemMessage);
        }
        
        List<Map<String, Object>> recentMessages = extractRecentMessages(
            messages, decision.getLevel());
        
        List<Map<String, Object>> middleMessages = extractMiddleMessages(
            messages, decision.getLevel());
        
        for (Map<String, Object> msg : middleMessages) {
            Map<String, Object> processed = processMessage(msg, decision.getLevel());
            if (processed != null) {
                compressed.add(processed);
            }
        }
        
        compressed.addAll(recentMessages);
        
        long compressedTokens = estimateTokens(compressed);
        if (compressedTokens > targetTokens && decision.getLevel() != CompressionLevel.AGGRESSIVE) {
            log.info("[CompressionStrategy] 压缩后仍超限，升级压缩级别");
            decision.setLevel(CompressionLevel.AGGRESSIVE);
            return executeCompression(messages, decision, context);
        }
        
        log.info("[CompressionStrategy] 压缩完成: {} -> {} 条消息, {} -> {} tokens",
                messages.size(), compressed.size(), 
                decision.getCurrentTokens(), compressedTokens);
        
        return compressed;
    }

    private Map<String, Object> extractSystemMessage(List<Map<String, Object>> messages) {
        return messages.stream()
            .filter(msg -> "system".equals(msg.get("role")))
            .findFirst()
            .orElse(null);
    }

    private List<Map<String, Object>> extractRecentMessages(List<Map<String, Object>> messages,
                                                             CompressionLevel level) {
        int recentCount;
        switch (level) {
            case AGGRESSIVE: recentCount = 3; break;
            case MODERATE: recentCount = 5; break;
            case LIGHT: recentCount = 8; break;
            default: recentCount = 5;
        }
        
        List<Map<String, Object>> recent = new ArrayList<>();
        int startIdx = Math.max(1, messages.size() - recentCount);
        
        for (int i = startIdx; i < messages.size(); i++) {
            recent.add(messages.get(i));
        }
        
        return recent;
    }

    private List<Map<String, Object>> extractMiddleMessages(List<Map<String, Object>> messages,
                                                             CompressionLevel level) {
        int recentCount;
        switch (level) {
            case AGGRESSIVE: recentCount = 3; break;
            case MODERATE: recentCount = 5; break;
            case LIGHT: recentCount = 8; break;
            default: recentCount = 5;
        }
        
        int startIdx = 1;
        int endIdx = messages.size() - recentCount;
        
        if (endIdx <= startIdx) {
            return Collections.emptyList();
        }
        
        List<Map<String, Object>> middle = new ArrayList<>();
        
        if (level == CompressionLevel.AGGRESSIVE) {
            int step = Math.max(1, (endIdx - startIdx) / 3);
            for (int i = startIdx; i < endIdx; i += step) {
                middle.add(messages.get(i));
            }
        } else if (level == CompressionLevel.MODERATE) {
            int step = Math.max(1, (endIdx - startIdx) / 5);
            for (int i = startIdx; i < endIdx; i += step) {
                middle.add(messages.get(i));
            }
        } else {
            middle.addAll(messages.subList(startIdx, endIdx));
        }
        
        return middle;
    }

    private Map<String, Object> processMessage(Map<String, Object> msg, CompressionLevel level) {
        String role = (String) msg.get("role");
        
        if ("system".equals(role)) {
            return msg;
        }
        
        if ("tool".equals(role)) {
            return processToolMessage(msg, level);
        }
        
        if ("assistant".equals(role) && msg.containsKey("tool_calls")) {
            return msg;
        }
        
        String content = (String) msg.get("content");
        if (content == null || content.trim().isEmpty()) {
            return null;
        }
        
        if (level == CompressionLevel.AGGRESSIVE) {
            content = truncateContent(content, 200);
        } else if (level == CompressionLevel.MODERATE) {
            content = truncateContent(content, 500);
        }
        
        Map<String, Object> processed = new HashMap<>(msg);
        processed.put("content", content);
        return processed;
    }

    private Map<String, Object> processToolMessage(Map<String, Object> msg, CompressionLevel level) {
        String content = (String) msg.get("content");
        String toolName = (String) msg.get("name");
        
        if (content == null) {
            return msg;
        }
        
        int maxLen = getMaxToolResultLength(toolName, content, level);
        
        if (content.length() > maxLen) {
            content = truncateToolResult(content, toolName, maxLen);
            Map<String, Object> processed = new HashMap<>(msg);
            processed.put("content", content);
            return processed;
        }
        
        return msg;
    }

    private int getMaxToolResultLength(String toolName, String content, CompressionLevel level) {
        int baseLen;
        
        if (ERROR_PATTERN.matcher(content).find()) {
            baseLen = MAX_ERROR_LENGTH;
        } else if (toolName != null && SEARCH_PATTERN.matcher(toolName).find()) {
            baseLen = MAX_SEARCH_RESULT_LENGTH;
        } else {
            baseLen = MAX_TOOL_RESULT_LENGTH;
        }
        
        switch (level) {
            case AGGRESSIVE: return (int)(baseLen * 0.5);
            case MODERATE: return (int)(baseLen * 0.7);
            case LIGHT: return baseLen;
            default: return baseLen;
        }
    }

    private String truncateContent(String content, int maxLen) {
        if (content == null || content.length() <= maxLen) {
            return content;
        }
        return content.substring(0, maxLen) + "...[已截断]";
    }

    private String truncateToolResult(String content, String toolName, int maxLen) {
        if (content == null || content.length() <= maxLen) {
            return content;
        }
        
        String truncated = content.substring(0, maxLen);
        
        int lastNewline = truncated.lastIndexOf('\n');
        if (lastNewline > maxLen * 0.8) {
            truncated = truncated.substring(0, lastNewline);
        }
        
        return truncated + "\n...[结果已截断，原始长度: " + content.length() + " 字符]";
    }

    public long estimateTokens(List<Map<String, Object>> messages) {
        long total = 0;
        
        for (Map<String, Object> msg : messages) {
            String role = (String) msg.get("role");
            total += role != null ? role.length() / TOKEN_ESTIMATE_RATIO : 0;
            
            String content = (String) msg.get("content");
            total += content != null ? content.length() / TOKEN_ESTIMATE_RATIO : 0;
            
            if (msg.containsKey("tool_calls")) {
                total += 50;
            }
        }
        
        return total;
    }

    public CompressionSummary getCompressionSummary(List<Map<String, Object>> original,
                                                     List<Map<String, Object>> compressed) {
        CompressionSummary summary = new CompressionSummary();
        summary.setOriginalMessages(original.size());
        summary.setCompressedMessages(compressed.size());
        summary.setOriginalTokens(estimateTokens(original));
        summary.setCompressedTokens(estimateTokens(compressed));
        summary.setReductionRatio(1.0 - (double) summary.getCompressedMessages() / summary.getOriginalMessages());
        summary.setTokenReductionRatio(1.0 - (double) summary.getCompressedTokens() / summary.getOriginalTokens());
        summary.setTimestamp(LocalDateTime.now());
        
        return summary;
    }

    @Data
    public static class CompressionDecision {
        private boolean needed;
        private CompressionLevel level;
        private String reason;
        private long currentTokens;
        private long maxTokens;
        private long targetTokens;
        private double usageRatio;
        private int messageCount;
    }

    public enum CompressionLevel {
        LIGHT,
        MODERATE,
        AGGRESSIVE
    }

    @Data
    public static class CompressionContext {
        private String sessionId;
        private double progress;
        private boolean nearCompletion;
        private int iteration;
        private int maxIterations;
    }

    @Data
    public static class CompressionSummary {
        private int originalMessages;
        private int compressedMessages;
        private long originalTokens;
        private long compressedTokens;
        private double reductionRatio;
        private double tokenReductionRatio;
        private LocalDateTime timestamp;
    }
}
