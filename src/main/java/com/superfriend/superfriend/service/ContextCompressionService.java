package com.superfriend.superfriend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.superfriend.superfriend.dto.LLMCompleteResponse;
import com.superfriend.superfriend.dto.LLMRequest;
import com.superfriend.superfriend.entity.AIModelConfig;
import com.superfriend.superfriend.entity.ChatCompression;
import com.superfriend.superfriend.mapper.ChatCompressionMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;

@Slf4j
@Service
public class ContextCompressionService {

    @Autowired
    private ChatCompressionMapper compressionMapper;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private LLMClient llmClient;

    @Autowired
    @Lazy
    private AIModelConfigService modelConfigService;

    private static final long LEVEL1_TOKEN_THRESHOLD = 10000;
    private static final long LEVEL2_TOKEN_THRESHOLD = 12000;
    private static final int MAX_TOOL_RESULT_LENGTH = 5000;
    private static final long RECENT_TOKENS_TO_KEEP = 9000;
    private static final int MIN_MESSAGES = 5;

    private static final int SEARCH_RESULT_MAX_LENGTH = 5000;
    private static final int FILE_CONTENT_MAX_LENGTH = 15000;
    private static final int ERROR_MESSAGE_MAX_LENGTH = 5000;
    private static final int DEFAULT_RESULT_MAX_LENGTH = 5000;

    private static final Set<String> SEARCH_TOOLS = new HashSet<>(Arrays.asList(
        "search", "find", "query", "lookup", "retrieve", "web_search", "google", "bing"
    ));
    
    private static final Set<String> FILE_TOOLS = new HashSet<>(Arrays.asList(
        "read", "write", "list", "directory", "file", "folder", "path"
    ));

    private static final Pattern ERROR_PATTERN = Pattern.compile(
        "(?i)(error|exception|failed|failure|not found|不存在|失败|错误)"
    );

    @Data
    public static class MessageImportance {
        private int index;
        private double score;
        private String role;
        private String content;
        private boolean hasToolCall;
        private boolean hasError;
    }

    @Data
    public static class ToolResultConfig {
        private String toolName;
        private int maxLength;
        private boolean preserveStructure;
        private String truncationSuffix;
    }

    public static class CompressionResult {
        public boolean compressed;
        public String level;
        public String summary;
        public int originalCount;
        public int compressedCount;
        public long originalTokens;
        public long compressedTokens;
    }

    public CompressionResult compressIfNeeded(List<Map<String, Object>> messages, String sessionId, Long userId) {
        return compressIfNeeded(messages, sessionId, userId, null);
    }

    public CompressionResult compressIfNeeded(List<Map<String, Object>> messages, String sessionId, Long userId, String modelName) {
        long currentTokens = estimateMessageTokens(messages);

        if (currentTokens <= LEVEL1_TOKEN_THRESHOLD || messages.size() <= MIN_MESSAGES) {
            return createNoOpResult(currentTokens);
        }

        log.info("Context compression triggered: tokens={}, messages={}, sessionId={}",
                currentTokens, messages.size(), sessionId);

        int originalCount = messages.size();
        long originalTokens = currentTokens;

        CompressionResult level2Result = applyLevel2Compression(messages);
        long afterLevel2Tokens = estimateMessageTokens(messages);

        if (afterLevel2Tokens <= LEVEL2_TOKEN_THRESHOLD) {
            log.info("Level 2 compression sufficient: {} -> {} tokens", originalTokens, afterLevel2Tokens);
            saveCompressionRecord(sessionId, originalCount, messages.size(),
                    "Level 2: rules-based compression (truncated tool results, removed redundant messages)",
                    null, "L2", originalTokens, afterLevel2Tokens);

            CompressionResult result = new CompressionResult();
            result.compressed = true;
            result.level = "L2";
            result.summary = String.format("规则压缩完成：%d -> %d 条消息，%d -> %d tokens",
                    originalCount, messages.size(), originalTokens, afterLevel2Tokens);
            result.originalCount = originalCount;
            result.compressedCount = messages.size();
            result.originalTokens = originalTokens;
            result.compressedTokens = afterLevel2Tokens;
            return result;
        }

        CompressionResult level3Result = applyLevel3Compression(messages, sessionId, userId, modelName);
        long afterLevel3Tokens = estimateMessageTokens(messages);

        log.info("Level 3 compression complete: {} -> {} tokens ({} messages remain)",
                originalTokens, afterLevel3Tokens, messages.size());

        return level3Result;
    }

    private CompressionResult applyLevel2Compression(List<Map<String, Object>> messages) {
        List<Map<String, Object>> compressed = new ArrayList<>();

        for (Map<String, Object> msg : messages) {
            String role = (String) msg.get("role");

            if ("system".equals(role)) {
                compressed.add(msg);
                continue;
            }

            if ("tool".equals(role)) {
                String toolName = (String) msg.get("name");
                String content = (String) msg.get("content");
                
                if (content != null && !content.isEmpty()) {
                    int dynamicMaxLength = getDynamicToolResultMaxLength(toolName, content);
                    
                    if (content.length() > dynamicMaxLength) {
                        Map<String, Object> truncatedMsg = new HashMap<>(msg);
                        String truncated = truncateToolResult(content, toolName, dynamicMaxLength);
                        truncatedMsg.put("content", truncated);
                        compressed.add(truncatedMsg);
                    } else {
                        compressed.add(msg);
                    }
                } else {
                    compressed.add(msg);
                }
                continue;
            }

            if ("assistant".equals(role) && msg.containsKey("tool_calls")) {
                compressed.add(msg);
                continue;
            }

            String content = (String) msg.get("content");
            if (content != null && content.trim().isEmpty() && !"user".equals(role)) {
                continue;
            }

            compressed.add(msg);
        }

        messages.clear();
        messages.addAll(compressed);
        return null;
    }

    int getDynamicToolResultMaxLength(String toolName, String content) {
        if (toolName == null) {
            return DEFAULT_RESULT_MAX_LENGTH;
        }
        
        String lowerToolName = toolName.toLowerCase();
        
        if (ERROR_PATTERN.matcher(content).find()) {
            return ERROR_MESSAGE_MAX_LENGTH;
        }
        
        for (String searchKeyword : SEARCH_TOOLS) {
            if (lowerToolName.contains(searchKeyword)) {
                return SEARCH_RESULT_MAX_LENGTH;
            }
        }
        
        for (String fileKeyword : FILE_TOOLS) {
            if (lowerToolName.contains(fileKeyword)) {
                return FILE_CONTENT_MAX_LENGTH;
            }
        }
        
        if (content.contains("{") && content.contains("}")) {
            return FILE_CONTENT_MAX_LENGTH;
        }
        
        if (content.length() < 50) {
            return content.length();
        }
        
        return DEFAULT_RESULT_MAX_LENGTH;
    }

    String truncateToolResult(String content, String toolName, int maxLength) {
        if (content.length() <= maxLength) {
            return content;
        }
        
        boolean isError = ERROR_PATTERN.matcher(content).find();
        
        if (isError) {
            int errorIndex = -1;
            for (String keyword : Arrays.asList("error", "Error", "ERROR", "exception", "Exception")) {
                int idx = content.indexOf(keyword);
                if (idx != -1 && (errorIndex == -1 || idx < errorIndex)) {
                    errorIndex = idx;
                }
            }
            
            if (errorIndex != -1) {
                int start = Math.max(0, errorIndex - 50);
                int end = Math.min(content.length(), errorIndex + maxLength - 50);
                return "...[上下文]..." + content.substring(start, end) + "...[错误信息已截断]";
            }
        }
        
        if (content.contains("\n")) {
            String[] lines = content.split("\n");
            StringBuilder result = new StringBuilder();
            int currentLength = 0;
            
            for (String line : lines) {
                if (currentLength + line.length() + 1 > maxLength - 50) {
                    break;
                }
                result.append(line).append("\n");
                currentLength += line.length() + 1;
            }
            
            result.append("...[已截断，原始 ").append(content.length()).append(" 字符]");
            return result.toString();
        }
        
        return content.substring(0, maxLength) + "...[已截断，原始 " + content.length() + " 字符]";
    }

    List<MessageImportance> calculateMessageImportance(List<Map<String, Object>> messages) {
        List<MessageImportance> importanceList = new ArrayList<>();
        
        for (int i = 0; i < messages.size(); i++) {
            Map<String, Object> msg = messages.get(i);
            MessageImportance importance = new MessageImportance();
            importance.setIndex(i);
            importance.setRole((String) msg.get("role"));
            importance.setContent((String) msg.get("content"));
            importance.setHasToolCall(msg.containsKey("tool_calls"));
            
            double score = 0.0;
            String role = importance.getRole();
            String content = importance.getContent();
            
            if ("user".equals(role)) {
                score += 10.0;
                if (content != null) {
                    if (content.contains("?") || content.contains("？")) {
                        score += 3.0;
                    }
                    if (content.length() > 100) {
                        score += 2.0;
                    }
                    if (content.contains("重要") || content.contains("关键") || 
                        content.contains("必须") || content.contains("important")) {
                        score += 5.0;
                    }
                }
            } else if ("assistant".equals(role)) {
                score += 5.0;
                if (importance.isHasToolCall()) {
                    score += 3.0;
                }
            } else if ("tool".equals(role)) {
                score += 2.0;
                if (content != null && ERROR_PATTERN.matcher(content).find()) {
                    score += 4.0;
                    importance.setHasError(true);
                }
            }
            
            double recencyBonus = (i / (double) messages.size()) * 5.0;
            score += recencyBonus;
            
            importance.setScore(score);
            importanceList.add(importance);
        }
        
        return importanceList;
    }

    List<Map<String, Object>> selectMessagesByImportance(
            List<Map<String, Object>> messages, 
            int maxMessages,
            int keepRecent) {
        
        if (messages.size() <= maxMessages) {
            return new ArrayList<>(messages);
        }
        
        List<Map<String, Object>> systemMessages = new ArrayList<>();
        List<Map<String, Object>> nonSystemMessages = new ArrayList<>();
        
        for (Map<String, Object> msg : messages) {
            if ("system".equals(msg.get("role"))) {
                systemMessages.add(msg);
            } else {
                nonSystemMessages.add(msg);
            }
        }
        
        if (nonSystemMessages.size() <= maxMessages - systemMessages.size()) {
            List<Map<String, Object>> result = new ArrayList<>(systemMessages);
            result.addAll(nonSystemMessages);
            return result;
        }
        
        List<Map<String, Object>> recentMessages = new ArrayList<>();
        List<Map<String, Object>> olderMessages = new ArrayList<>();
        
        int recentStart = Math.max(0, nonSystemMessages.size() - keepRecent);
        for (int i = 0; i < nonSystemMessages.size(); i++) {
            if (i >= recentStart) {
                recentMessages.add(nonSystemMessages.get(i));
            } else {
                olderMessages.add(nonSystemMessages.get(i));
            }
        }
        
        int remainingSlots = maxMessages - systemMessages.size() - recentMessages.size();
        
        if (remainingSlots <= 0 || olderMessages.isEmpty()) {
            List<Map<String, Object>> result = new ArrayList<>(systemMessages);
            result.addAll(recentMessages);
            return result;
        }
        
        List<MessageImportance> importanceList = calculateMessageImportance(olderMessages);
        importanceList.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
        
        List<Map<String, Object>> selectedOlder = new ArrayList<>();
        for (int i = 0; i < Math.min(remainingSlots, importanceList.size()); i++) {
            int originalIndex = importanceList.get(i).getIndex();
            selectedOlder.add(olderMessages.get(originalIndex));
        }
        
        selectedOlder.sort((a, b) -> {
            int indexA = olderMessages.indexOf(a);
            int indexB = olderMessages.indexOf(b);
            return Integer.compare(indexA, indexB);
        });
        
        List<Map<String, Object>> result = new ArrayList<>(systemMessages);
        result.addAll(selectedOlder);
        result.addAll(recentMessages);
        
        return result;
    }

    String compressReflections(List<Map<String, Object>> messages) {
        List<String> reflectionContents = new ArrayList<>();
        List<Integer> reflectionIndices = new ArrayList<>();
        
        for (int i = 0; i < messages.size(); i++) {
            Map<String, Object> msg = messages.get(i);
            String role = (String) msg.get("role");
            String content = (String) msg.get("content");
            
            if ("user".equals(role) && content != null && 
                content.contains("请修正")) {
                reflectionContents.add(content);
                reflectionIndices.add(i);
            }
        }
        
        if (reflectionContents.size() < 2) {
            return null;
        }
        
        StringBuilder compressed = new StringBuilder();
        compressed.append("【历史反思汇总】\n");
        
        for (int i = 0; i < reflectionContents.size(); i++) {
            String content = reflectionContents.get(i);
            String keyPoint = extractKeyPoint(content);
            compressed.append("- ").append(keyPoint).append("\n");
        }
        
        for (int i = reflectionIndices.size() - 1; i >= 0; i--) {
            messages.remove((int) reflectionIndices.get(i));
        }
        
        return compressed.toString();
    }

    private String extractKeyPoint(String reflectionContent) {
        if (reflectionContent == null || reflectionContent.isEmpty()) {
            return "";
        }
        
        String content = reflectionContent.replace("请修正: ", "").replace("请修正：", "");
        
        if (content.length() > 100) {
            int endPos = content.indexOf("。");
            if (endPos > 0 && endPos < 100) {
                return content.substring(0, endPos + 1);
            }
            return content.substring(0, 100) + "...";
        }
        
        return content;
    }

    private CompressionResult applyLevel3Compression(List<Map<String, Object>> messages, String sessionId, Long userId, String modelName) {
        int originalCount = messages.size();
        long originalTokens = estimateMessageTokens(messages);

        String reflectionSummary = compressReflections(messages);
        
        List<Map<String, Object>> systemMessages = new ArrayList<>();
        List<Map<String, Object>> nonSystemMessages = new ArrayList<>();

        for (Map<String, Object> msg : messages) {
            String role = (String) msg.get("role");
            if ("system".equals(role)) {
                systemMessages.add(msg);
            } else {
                nonSystemMessages.add(msg);
            }
        }

        if (estimateMessageTokens(nonSystemMessages) <= RECENT_TOKENS_TO_KEEP) {
            CompressionResult result = new CompressionResult();
            result.compressed = reflectionSummary != null;
            result.level = reflectionSummary != null ? "L2.5" : null;
            result.summary = reflectionSummary;
            result.originalTokens = originalTokens;
            result.compressedTokens = estimateMessageTokens(messages);
            return result;
        }

        List<Long> messageTokens = new ArrayList<>();
        for (Map<String, Object> msg : nonSystemMessages) {
            messageTokens.add(estimateSingleMessageTokens(msg));
        }

        List<Map<String, Object>> recentMessages = new ArrayList<>();
        List<Map<String, Object>> oldMessages = new ArrayList<>();
        long recentTokenCount = 0;

        for (int i = nonSystemMessages.size() - 1; i >= 0; i--) {
            long msgTokens = messageTokens.get(i);
            if (recentTokenCount + msgTokens <= RECENT_TOKENS_TO_KEEP) {
                recentMessages.add(0, nonSystemMessages.get(i));
                recentTokenCount += msgTokens;
            } else {
                oldMessages.add(0, nonSystemMessages.get(i));
            }
        }

        log.info("【上下文压缩】滑动窗口: 总消息={}, 最近消息={}({}tokens), 旧消息={}({}tokens)", 
            nonSystemMessages.size(), recentMessages.size(), recentTokenCount, 
            oldMessages.size(), estimateMessageTokens(oldMessages));

        if (oldMessages.isEmpty()) {
            CompressionResult result = new CompressionResult();
            result.compressed = reflectionSummary != null;
            result.level = reflectionSummary != null ? "L2.5" : null;
            result.summary = reflectionSummary;
            result.originalTokens = originalTokens;
            result.compressedTokens = estimateMessageTokens(messages);
            return result;
        }

<<<<<<< HEAD
        String summary = generateLLMSummary(oldMessages, sessionId, userId, modelName);
=======
        String summary = generateLLMSummary(oldMessages, userId, modelName);
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
        
        List<Map<String, Object>> preservedToolResults = extractImportantToolResults(oldMessages);

        messages.clear();
        messages.addAll(systemMessages);

        if (reflectionSummary != null) {
            Map<String, Object> reflectionMsg = new HashMap<>();
            reflectionMsg.put("role", "system");
            reflectionMsg.put("content", reflectionSummary);
            messages.add(reflectionMsg);
        }

        if (summary != null && !summary.isEmpty()) {
            Map<String, Object> summaryMsg = new HashMap<>();
            summaryMsg.put("role", "system");
            summaryMsg.put("content", "【上下文摘要 - 已压缩 " + oldMessages.size() + " 条旧消息】\n\n" + summary);
            messages.add(summaryMsg);
        }

        if (!preservedToolResults.isEmpty()) {
            Map<String, Object> toolRefMsg = new HashMap<>();
            toolRefMsg.put("role", "system");
            StringBuilder toolRefContent = new StringBuilder("【保留的关键工具结果】\n\n");
            for (Map<String, Object> toolResult : preservedToolResults) {
                String toolName = (String) toolResult.get("name");
                String content = (String) toolResult.get("content");
                if (toolName != null) {
                    toolRefContent.append("工具: ").append(toolName).append("\n");
                }
                if (content != null) {
                    int maxLen = getDynamicToolResultMaxLength(toolName, content);
                    String truncated = content.length() > maxLen ? 
                        truncateToolResult(content, toolName, maxLen) : content;
                    toolRefContent.append(truncated).append("\n\n");
                }
            }
            toolRefMsg.put("content", toolRefContent.toString().trim());
            messages.add(toolRefMsg);
        }

        messages.addAll(recentMessages);

        long compressedTokens = estimateMessageTokens(messages);
        String preservedJson = null;
        try {
            if (!preservedToolResults.isEmpty()) {
                List<Map<String, String>> serializable = new ArrayList<>();
                for (Map<String, Object> tr : preservedToolResults) {
                    Map<String, String> item = new HashMap<>();
                    item.put("name", (String) tr.get("name"));
                    item.put("content", (String) tr.get("content"));
                    serializable.add(item);
                }
                preservedJson = objectMapper.writeValueAsString(serializable);
            }
        } catch (Exception e) {
            log.warn("Failed to serialize preserved tool results", e);
        }

        saveCompressionRecord(sessionId, originalCount, messages.size(),
                summary, preservedJson, "L3", originalTokens, compressedTokens);

        CompressionResult result = new CompressionResult();
        result.compressed = true;
        result.level = "L3";
        result.summary = summary;
        result.originalCount = originalCount;
        result.compressedCount = messages.size();
        result.originalTokens = originalTokens;
        result.compressedTokens = compressedTokens;
        return result;
    }

<<<<<<< HEAD
    private String generateLLMSummary(List<Map<String, Object>> messages, String sessionId, Long userId, String modelName) {
=======
    private String generateLLMSummary(List<Map<String, Object>> messages, Long userId, String modelName) {
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
        StringBuilder historyText = new StringBuilder();
        for (Map<String, Object> msg : messages) {
            String role = (String) msg.get("role");
            String content = (String) msg.get("content");
            if (content == null || content.trim().isEmpty()) {
                continue;
            }

            String displayRole;
            switch (role) {
                case "user": displayRole = "用户"; break;
                case "assistant": displayRole = "助手"; break;
                case "tool": displayRole = "工具结果"; break;
                default: displayRole = role; break;
            }

            String truncatedContent = content.length() > 200 ? content.substring(0, 200) + "..." : content;
            historyText.append("[").append(displayRole).append("]: ").append(truncatedContent).append("\n\n");
        }

        if (historyText.length() > 3000) {
            historyText = new StringBuilder(historyText.substring(0, 3000) + "\n...(更多内容已省略)");
        }

        String prompt = "请将以下对话历史压缩为简洁的摘要，保留关键信息。\n\n" +
                "要求：\n" +
                "1. 保留用户的核心需求和意图\n" +
                "2. 保留工具调用的关键结果（数据、文件路径、重要结论）\n" +
                "3. 保留未完成的任务或待跟进的事项\n" +
                "4. 使用结构化格式：\n" +
                "   - 用户意图：...\n" +
                "   - 已完成：...\n" +
                "   - 关键数据：...\n" +
                "   - 待处理：...\n" +
                "5. 摘要控制在 300 字以内\n\n" +
                "对话历史：\n" + historyText.toString();

        try {
            AIModelConfig modelConfig = resolveModelConfig(userId, modelName);
            if (modelConfig != null) {
<<<<<<< HEAD
                String llmResponse = callLLMNonStream(prompt, modelConfig, sessionId, userId);
=======
                String llmResponse = callLLMNonStream(prompt, modelConfig);
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
                if (llmResponse != null && !llmResponse.trim().isEmpty()) {
                    log.info("LLM summary generated successfully ({} chars)", llmResponse.length());
                    return llmResponse;
                }
            } else {
                log.warn("用户 {} 没有配置模型，跳过 LLM 摘要生成", userId);
            }
        } catch (Exception e) {
            log.error("Failed to generate LLM summary, falling back to rule-based summary", e);
        }

        return generateFallbackSummary(messages);
    }

    private AIModelConfig resolveModelConfig(Long userId, String modelName) {
        if (userId == null) return null;
        if (modelName != null && !modelName.isEmpty()) {
            AIModelConfig config = modelConfigService.resolveModelConfig(modelName, userId);
            if (config != null) return config;
        }
        AIModelConfig config = modelConfigService.getDefaultModel(userId);
        if (config == null) {
            List<AIModelConfig> models = modelConfigService.getAvailableModelEntities(userId);
            if (models != null && !models.isEmpty()) {
                config = models.get(0);
            }
        }
        return config;
    }

    private String generateFallbackSummary(List<Map<String, Object>> messages) {
        StringBuilder summary = new StringBuilder();
        int userMsgCount = 0;
        int toolCallCount = 0;
        List<String> userIntents = new ArrayList<>();
        List<String> toolNames = new ArrayList<>();

        for (Map<String, Object> msg : messages) {
            String role = (String) msg.get("role");
            String content = (String) msg.get("content");

            if ("user".equals(role) && content != null && !content.trim().isEmpty()) {
                userMsgCount++;
                if (userIntents.size() < 3) {
                    String intent = content.length() > 100 ? content.substring(0, 100) + "..." : content;
                    userIntents.add(intent);
                }
            }

            if ("tool".equals(role)) {
                toolCallCount++;
                String name = (String) msg.get("name");
                if (name != null && toolNames.size() < 5) {
                    toolNames.add(name);
                }
            }
        }

        summary.append("- 用户意图：共 ").append(userMsgCount).append(" 条消息\n");
        if (!userIntents.isEmpty()) {
            summary.append("  主要问题：").append(String.join("; ", userIntents)).append("\n");
        }
        summary.append("- 已完成：").append(toolCallCount).append(" 次工具调用");
        if (!toolNames.isEmpty()) {
            summary.append("（").append(String.join(", ", toolNames)).append("）");
        }
        summary.append("\n- 关键数据：见保留的工具结果\n- 待处理：基于最近对话继续");

        return summary.toString();
    }

    private List<Map<String, Object>> extractImportantToolResults(List<Map<String, Object>> messages) {
        List<Map<String, Object>> toolResults = new ArrayList<>();
        Set<String> seenToolNames = new HashSet<>();

        for (int i = messages.size() - 1; i >= 0; i--) {
            Map<String, Object> msg = messages.get(i);
            String role = (String) msg.get("role");

            if ("tool".equals(role)) {
                String toolName = (String) msg.get("name");
                String content = (String) msg.get("content");

                if (content != null && !content.trim().isEmpty()) {
                    if (toolName != null && !seenToolNames.contains(toolName)) {
                        seenToolNames.add(toolName);
                        toolResults.add(0, msg);
                        if (toolResults.size() >= 3) {
                            break;
                        }
                    }
                }
            }
        }

        return toolResults;
    }

    private List<List<Map<String, Object>>> groupToolCallPairs(List<Map<String, Object>> messages) {
        List<List<Map<String, Object>>> groups = new ArrayList<>();
        List<Map<String, Object>> currentGroup = new ArrayList<>();

        for (int i = 0; i < messages.size(); i++) {
            Map<String, Object> msg = messages.get(i);
            String role = (String) msg.get("role");

            if ("assistant".equals(role) && msg.containsKey("tool_calls")) {
                if (!currentGroup.isEmpty()) {
                    groups.add(currentGroup);
                }
                currentGroup = new ArrayList<>();
                currentGroup.add(msg);

                Object toolCallsObj = msg.get("tool_calls");
                if (toolCallsObj instanceof List) {
                    List<?> toolCalls = (List<?>) toolCallsObj;
                    Set<String> callIds = new HashSet<>();
                    for (Object tc : toolCalls) {
                        if (tc instanceof Map) {
                            Object id = ((Map<?, ?>) tc).get("id");
                            if (id != null) {
                                callIds.add(id.toString());
                            }
                        }
                    }

                    for (int j = i + 1; j < messages.size(); j++) {
                        Map<String, Object> nextMsg = messages.get(j);
                        String nextRole = (String) nextMsg.get("role");
                        if ("tool".equals(nextRole) && nextMsg.containsKey("tool_call_id")) {
                            String toolCallId = (String) nextMsg.get("tool_call_id");
                            if (callIds.contains(toolCallId)) {
                                currentGroup.add(nextMsg);
                                i = j;
                            } else {
                                break;
                            }
                        } else {
                            break;
                        }
                    }
                }

                groups.add(currentGroup);
                currentGroup = new ArrayList<>();
            } else {
                currentGroup.add(msg);
            }
        }

        if (!currentGroup.isEmpty()) {
            groups.add(currentGroup);
        }

        return groups;
    }

<<<<<<< HEAD
    private String callLLMNonStream(String prompt, AIModelConfig modelConfig, String sessionId, Long userId) throws Exception {
=======
    private String callLLMNonStream(String prompt, AIModelConfig modelConfig) throws Exception {
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
        List<Map<String, Object>> requestMessages = new ArrayList<>();
        Map<String, Object> systemMsg = new HashMap<>();
        systemMsg.put("role", "system");
        systemMsg.put("content", "你是一个对话摘要助手。请严格按照要求的格式输出摘要，不要添加额外的解释。");
        requestMessages.add(systemMsg);

        Map<String, Object> userMsg = new HashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", prompt);
        requestMessages.add(userMsg);

        LLMRequest request = LLMRequest.fromConfig(modelConfig.getModelId(), modelConfig.getApiUrl(), modelConfig.getApiKey())
                .toBuilder()
                .messages(requestMessages)
                .temperature(0.3)
                .maxTokens(500)
<<<<<<< HEAD
                .sessionId(sessionId)
                .userId(userId)
=======
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
                .build();

        log.info("[ContextCompression] 调用 LLM (chatComplete): model={}", modelConfig.getModelId());

        LLMCompleteResponse response = llmClient.chatComplete(request);
        if (!response.isSuccess()) {
            throw new RuntimeException("LLM API returned error: " + (response.getError() != null ? response.getError() : "unknown"));
        }
        return response.getContent();
    }

    private byte[] readAllBytes(InputStream is) throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] data = new byte[4096];
        int nRead;
        while ((nRead = is.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }
        return buffer.toByteArray();
    }

    private void saveCompressionRecord(String sessionId, int originalCount, int compressedCount,
                                        String summary, String preservedToolResults,
                                        String level, long originalTokens, long compressedTokens) {
        try {
            compressionMapper.deleteBySessionId(sessionId);
            
            ChatCompression record = new ChatCompression();
            record.setSessionId(sessionId);
            record.setOriginalMessageCount(originalCount);
            record.setCompressedMessageCount(compressedCount);
            record.setSummary(summary);
            record.setPreservedToolResults(preservedToolResults);
            record.setCompressionLevel(level);
            record.setOriginalTokens(originalTokens);
            record.setCompressedTokens(compressedTokens);

            if (originalTokens > 0) {
                double ratio = ((double) (originalTokens - compressedTokens) / originalTokens) * 100;
                record.setCompressionRatio(BigDecimal.valueOf(ratio).setScale(2, RoundingMode.HALF_UP));
            }

            compressionMapper.insert(record);
            log.info("Compression record saved: sessionId={}, level={}, ratio={}%",
                    sessionId, level, record.getCompressionRatio());
        } catch (Exception e) {
            log.warn("Failed to save compression record: {}", e.getMessage());
        }
    }

    private CompressionResult createNoOpResult(long tokens) {
        CompressionResult result = new CompressionResult();
        result.compressed = false;
        result.originalTokens = tokens;
        result.compressedTokens = tokens;
        return result;
    }

    long estimateMessageTokens(List<Map<String, Object>> messages) {
        long totalTokens = 0;
        for (Map<String, Object> msg : messages) {
            totalTokens += estimateSingleMessageTokens(msg);
        }
        return totalTokens;
    }

    long estimateSingleMessageTokens(Map<String, Object> msg) {
        long tokens = 4;
        String content = (String) msg.get("content");
        if (content != null) {
            tokens += content.getBytes(StandardCharsets.UTF_8).length / 3;
        }

        if (msg.containsKey("tool_calls")) {
            Object toolCallsObj = msg.get("tool_calls");
            if (toolCallsObj instanceof List) {
                List<?> toolCalls = (List<?>) toolCallsObj;
                tokens += toolCalls.size() * 150;
            }
        }
        
        if (msg.containsKey("name")) {
            tokens += 10;
        }
        
        if (msg.containsKey("tool_call_id")) {
            tokens += 5;
        }
        
        return tokens;
    }
}
