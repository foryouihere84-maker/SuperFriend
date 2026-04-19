package com.superfriend.superfriend.service;

import com.superfriend.superfriend.config.KnowledgeExtractionProperties;
import com.superfriend.superfriend.constant.ChatMode;
import com.superfriend.superfriend.entity.ChatCompression;
import com.superfriend.superfriend.entity.ChatHistory;
import com.superfriend.superfriend.entity.ChatMessageRecord;
import com.superfriend.superfriend.mapper.ChatCompressionMapper;
import com.superfriend.superfriend.mapper.ChatHistoryMapper;
import com.superfriend.superfriend.mapper.ChatMessageMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Slf4j
@Service
public class ContextMangerService {

    @Autowired
    private ChatHistoryMapper historyMapper;

    @Autowired
    private ChatMessageMapper messageMapper;

    @Autowired
    private ChatCompressionMapper compressionMapper;

    @Autowired
    @Lazy
    private KnowledgeGraphService knowledgeGraphService;

    @Autowired
    @Lazy
    private ContextCompressionService compressionService;

    @Autowired
    @Lazy
    private KnowledgeExtractorService knowledgeExtractorService;

    @Autowired
    @Lazy
    private ConversationGraphService conversationGraphService;

    @Autowired
    private KnowledgeExtractionProperties extractionProperties;

    @Autowired
    @Lazy
    private UserProfileService userProfileService;

    @Autowired
    @Lazy
    private SystemContextBuilder systemContextBuilder;

    @Autowired
    @Lazy
    private MultimodalContentStorageService multimodalContentStorageService;

    // ==================== 会话查询 ====================

    public List<ChatHistory> getHistoriesByUserId(Long userId) {
        return historyMapper.findByUserId(userId);
    }

    public List<ChatHistory> getHistoriesByUserIdAndMode(Long userId, String mode) {
        return historyMapper.findByUserIdAndMode(userId, mode);
    }

    public ChatHistory getHistoryBySessionId(String sessionId) {
        return historyMapper.findBySessionId(sessionId);
    }

    public List<ChatMessageRecord> getMessagesBySessionId(String sessionId) {
        return messageMapper.findBySessionId(sessionId);
    }

    public ChatCompression getCompressionRecord(String sessionId) {
        return compressionMapper.findLatestBySessionId(sessionId);
    }

    public Map<String, Object> getHistoryWithMessages(String sessionId) {
        ChatHistory history = historyMapper.findBySessionId(sessionId);
        List<ChatMessageRecord> messages = messageMapper.findBySessionId(sessionId);
        Map<String, Object> result = new HashMap<>();
        result.put("history", history);
        result.put("messages", messages);
        return result;
    }

    /**
     * 获取用于知识提取的消息列表
     * 将 ChatMessageRecord 转换为 Map 格式
     */
    public List<Map<String, Object>> getMessagesForExtraction(String sessionId) {
        List<Map<String, Object>> messages = new ArrayList<>();
        List<ChatMessageRecord> records = getMessagesBySessionId(sessionId);
        for (ChatMessageRecord record : records) {
            Map<String, Object> msg = new HashMap<>();
            msg.put("role", record.getRole());
            msg.put("content", record.getContent());
            messages.add(msg);
        }
        return messages;
    }

    /**
     * 获取压缩后的消息列表（优先使用压缩消息）
     * 1. 先查询 chat_compression 表
     * 2. 如果有压缩记录，返回摘要消息 + 最近的消息
     * 3. 如果没有，返回原始消息
     */
    public List<Map<String, Object>> getMessagesWithCompression(String sessionId) {
        ChatCompression compression = compressionMapper.findLatestBySessionId(sessionId);
        
        if (compression != null) {
            log.info("使用压缩消息: sessionId={}, level={}, 压缩率={}%", 
                sessionId, compression.getCompressionLevel(), compression.getCompressionRatio());
            
            List<Map<String, Object>> messages = new ArrayList<>();
            
            Map<String, Object> summaryMsg = new HashMap<>();
            summaryMsg.put("role", "system");
            summaryMsg.put("content", "【上下文摘要 - 已压缩 " + compression.getOriginalMessageCount() + " 条旧消息】\n\n" + compression.getSummary());
            messages.add(summaryMsg);
            
            if (compression.getPreservedToolResults() != null && !compression.getPreservedToolResults().isEmpty()) {
                try {
                    com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
                    java.util.List<Map<String, String>> toolResults = objectMapper.readValue(
                        compression.getPreservedToolResults(), 
                        new com.fasterxml.jackson.core.type.TypeReference<java.util.List<Map<String, String>>>() {}
                    );
                    
                    Map<String, Object> toolRefMsg = new HashMap<>();
                    toolRefMsg.put("role", "system");
                    StringBuilder toolRefContent = new StringBuilder("【保留的关键工具结果】\n\n");
                    for (Map<String, String> tr : toolResults) {
                        String toolName = tr.get("name");
                        String content = tr.get("content");
                        if (toolName != null) {
                            toolRefContent.append("工具: ").append(toolName).append("\n");
                        }
                        if (content != null) {
                            toolRefContent.append(content).append("\n\n");
                        }
                    }
                    toolRefMsg.put("content", toolRefContent.toString().trim());
                    messages.add(toolRefMsg);
                } catch (Exception e) {
                    log.warn("解析保留的工具结果失败: {}", e.getMessage());
                }
            }
            
            List<ChatMessageRecord> allRecords = messageMapper.findBySessionId(sessionId);
            int recentCount = allRecords.size() - compression.getOriginalMessageCount() + compression.getCompressedMessageCount();
            int startIndex = Math.max(0, allRecords.size() - Math.max(recentCount, 5));
            
            for (int i = startIndex; i < allRecords.size(); i++) {
                ChatMessageRecord record = allRecords.get(i);
                Map<String, Object> msg = new HashMap<>();
                msg.put("role", record.getRole());
                msg.put("content", record.getContent());
                messages.add(msg);
            }
            
            return messages;
        }
        
        return getMessagesForExtraction(sessionId);
    }

    // ==================== 会话管理 ====================

    @Transactional
    public void deleteSession(String sessionId) {
        // 获取会话信息以获取 userId
        ChatHistory history = historyMapper.findBySessionId(sessionId);
        Long userId = history != null ? history.getUserId() : null;

        // 删除消息和历史记录
        messageMapper.deleteBySessionId(sessionId);
        historyMapper.deleteBySessionId(sessionId);
        compressionMapper.deleteBySessionId(sessionId);

        // 删除关联的知识图谱节点和关系
        if (userId != null) {
            try {
                int deletedNodes = knowledgeGraphService.deleteConversationGraph(userId, sessionId);
                log.info("已删除会话关联的知识图谱，sessionId={}, userId={}, deletedNodes={}", sessionId, userId, deletedNodes);
            } catch (Exception e) {
                log.warn("删除会话关联的知识图谱失败，sessionId={}, error={}", sessionId, e.getMessage());
            }
        }

        log.info("对话历史已删除，sessionId={}，userId={}", sessionId,userId);
    }

    @Transactional
    public void deleteSessionById(Long id) {
        ChatHistory history = historyMapper.findBySessionId(String.valueOf(id));
        if (history != null && history.getSessionId() != null) {
            compressionMapper.deleteBySessionId(history.getSessionId());

            // 删除关联的知识图谱节点和关系
            Long userId = history.getUserId();
            if (userId != null) {
                try {
                    int deletedNodes = knowledgeGraphService.deleteConversationGraph(userId, history.getSessionId());
                    log.info("已删除会话关联的知识图谱，sessionId={}, userId={}, deletedNodes={}", history.getSessionId(), userId, deletedNodes);
                } catch (Exception e) {
                    log.warn("删除会话关联的知识图谱失败，sessionId={}, error={}", history.getSessionId(), e.getMessage());
                }
            }
        }
        messageMapper.deleteByHistoryId(id);
        historyMapper.deleteById(id);
        log.info("对话历史已删除，id={}", id);
    }

    @Transactional
    public void batchDeleteSessions(List<String> sessionIds) {
        for (String sessionId : sessionIds) {
            // 获取会话信息以获取 userId
            ChatHistory history = historyMapper.findBySessionId(sessionId);
            Long userId = history != null ? history.getUserId() : null;

            messageMapper.deleteBySessionId(sessionId);
            historyMapper.deleteBySessionId(sessionId);
            compressionMapper.deleteBySessionId(sessionId);

            // 删除关联的知识图谱节点和关系
            if (userId != null) {
                try {
                    knowledgeGraphService.deleteConversationGraph(userId, sessionId);
                } catch (Exception e) {
                    log.warn("删除会话关联的知识图谱失败，sessionId={}, error={}", sessionId, e.getMessage());
                }
            }
        }
        log.info("批量删除对话历史完成，数量={}", sessionIds.size());
    }

    @Transactional
    public void renameSession(String sessionId, String title) {
        ChatHistory history = historyMapper.findBySessionId(sessionId);
        if (history != null) {
            history.setTitle(title);
            historyMapper.upsert(history);
            log.info("对话历史已重命名，sessionId={}, title={}", sessionId, title);
        }
    }

    // ==================== 消息保存 ====================

    @Transactional
    public void saveOrUpdateSession(String sessionId, Long userId, String model, String mode,
                                     List<Map<String, String>> messages) {
        if (sessionId == null || sessionId.isEmpty()) {
            log.warn("sessionId 为空，跳过保存对话历史");
            return;
        }
        ChatHistory history = historyMapper.findBySessionId(sessionId);

        String title = extractTitle(messages);

        if (history == null) {
            history = new ChatHistory();
            history.setSessionId(sessionId);
            history.setUserId(userId != null ? userId : 1L);
            history.setTitle(title);
            history.setModel(model);
            history.setMode(ChatMode.normalize(mode));
            history.setMessageCount(messages.size());
            historyMapper.upsert(history);
        } else {
            history.setTitle(title);
            history.setModel(model);
            history.setMode(ChatMode.normalize(mode));
            history.setMessageCount(messages.size());
            historyMapper.upsert(history);
        }

        messageMapper.deleteBySessionId(sessionId);
        for (Map<String, String> msg : messages) {
            ChatMessageRecord record = new ChatMessageRecord();
            record.setHistoryId(history.getId());
            record.setSessionId(sessionId);
            record.setRole(msg.get("role"));
            String content = msg.get("content");
            if (multimodalContentStorageService.needsProcessing(content)) {
                MultimodalContentStorageService.ProcessedContent processed = 
                    multimodalContentStorageService.processContent(content);
                content = processed.getContent();
                if (!processed.getMediaInfo().isEmpty()) {
                    log.info("消息内容包含多模态数据，已替换 {} 个媒体占位符", 
                        processed.getMediaInfo().size());
                }
            }
            record.setContent(content);
            messageMapper.insert(record);
        }

        log.info("对话历史已保存，sessionId={}, 消息数={}", sessionId, messages.size());
    }

    @Transactional
    public void saveMessage(String sessionId, String role, String content) {
        ChatHistory history = historyMapper.findBySessionId(sessionId);
        if (history == null) {
            log.warn("对话历史不存在，sessionId={}，跳过保存消息", sessionId);
            return;
        }

        if (multimodalContentStorageService.needsProcessing(content)) {
            MultimodalContentStorageService.ProcessedContent processed = 
                multimodalContentStorageService.processContent(content);
            content = processed.getContent();
            if (!processed.getMediaInfo().isEmpty()) {
                log.info("消息内容包含多模态数据，已替换 {} 个媒体占位符", 
                    processed.getMediaInfo().size());
            }
        }

        ChatMessageRecord record = new ChatMessageRecord();
        record.setHistoryId(history.getId());
        record.setSessionId(sessionId);
        record.setRole(role);
        record.setContent(content);
        messageMapper.insert(record);

        int count = messageMapper.findBySessionId(sessionId).size();
        historyMapper.updateMessageCount(sessionId, count);
    }

    // ==================== 上下文保存（快捷方法） ====================

    public void saveContext(String sessionId, Long userId, String model,
                            String message, List<Map<String, String>> history,
                            String assistantContent) {
        saveContext(sessionId, userId, model, ChatMode.MEDIUM_TASK, message, history, assistantContent, false);
    }

    public void saveContext(String sessionId, Long userId, String model, String processType,
                            String message, List<Map<String, String>> history,
                            String assistantContent) {
        saveContext(sessionId, userId, model, processType, message, history, assistantContent, false);
    }

    public void saveContext(String sessionId, Long userId, String model, String processType,
                            String message, List<Map<String, String>> history,
                            String assistantContent, boolean enableKnowledgeExtraction) {
        List<Map<String, String>> allMessages = buildMessageList(message, history, assistantContent);
        saveOrUpdateSession(sessionId, userId, model, processType, allMessages);

        // 只有用户主动开启时才触发知识提取
        if (enableKnowledgeExtraction) {
            checkAndTriggerExtraction(userId, sessionId, model);
        }
    }

    public void saveContext(String sessionId, Long userId, String model, String processType,
                            List<Map<String, String>> allMessages) {
        saveContext(sessionId, userId, model, processType, allMessages, false);
    }

    public void saveContext(String sessionId, Long userId, String model, String processType,
                            List<Map<String, String>> allMessages, boolean enableKnowledgeExtraction) {
        saveOrUpdateSession(sessionId, userId, model, processType, allMessages);

        // 只有用户主动开启时才触发知识提取
        if (enableKnowledgeExtraction) {
            checkAndTriggerExtraction(userId, sessionId, model);
        }
    }

    /**
     * 检查并触发知识提取
     * 每轮对话完成后都提取最新一轮的知识点
     */
    private void checkAndTriggerExtraction(Long userId, String sessionId, String model) {
        if (userId == null || sessionId == null) {
            return;
        }

        // 如果正在处理中，跳过（防止重复提取）
        if (knowledgeExtractorService.isProcessing(sessionId)) {
            log.debug("Session {} 正在处理中，跳过", sessionId);
            return;
        }

        // 获取当前对话的所有消息
        List<Map<String, Object>> messages = new ArrayList<>();
        List<ChatMessageRecord> records = getMessagesBySessionId(sessionId);
        for (ChatMessageRecord record : records) {
            Map<String, Object> msg = new HashMap<>();
            msg.put("role", record.getRole());
            msg.put("content", record.getContent());
            messages.add(msg);
        }

        // 检查是否有完整的对话轮次
        if (knowledgeExtractorService.shouldTriggerExtraction(messages)) {
            log.info("触发知识提取: sessionId={}, messageCount={}", sessionId, messages.size());

            // 异步提取（会自动去重，只提取新知识）
            knowledgeExtractorService.extractAsync(userId, sessionId, messages, model);
        }
    }

    public void saveTurn(String sessionId, Long userId, String model, String mode,
                         String userMessage, String assistantMessage) {
        List<Map<String, String>> existingMessages = new ArrayList<>();
        List<ChatMessageRecord> records = getMessagesBySessionId(sessionId);
        for (ChatMessageRecord record : records) {
            Map<String, String> msg = new HashMap<>();
            msg.put("role", record.getRole());
            msg.put("content", record.getContent());
            existingMessages.add(msg);
        }
        
        Map<String, String> userMsg = new HashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", userMessage);
        existingMessages.add(userMsg);

        Map<String, String> assistantMsg = new HashMap<>();
        assistantMsg.put("role", "assistant");
        assistantMsg.put("content", assistantMessage);
        existingMessages.add(assistantMsg);

        saveOrUpdateSession(sessionId, userId, model, mode, existingMessages);
    }

    // ==================== 消息列表构建 ====================

    public List<Map<String, Object>> buildLLMMessages(String sessionId, String userMessage,
                                                       String systemPrompt, Long userId) {
        List<Map<String, Object>> messages = new ArrayList<>();

        String enhancedSystemPrompt = systemContextBuilder.buildChatPrompt(userId, sessionId, userMessage);

        List<Map<String, Object>> history = getMessagesWithCompression(sessionId);
        
        StringBuilder systemContent = new StringBuilder(enhancedSystemPrompt);
        for (Map<String, Object> record : history) {
            String role = (String) record.get("role");
            if ("system".equals(role)) {
                String content = (String) record.get("content");
                if (content != null) {
                    systemContent.append("\n\n").append(content);
                }
            }
        }
        
        Map<String, Object> systemMessage = new HashMap<>();
        systemMessage.put("role", "system");
        systemMessage.put("content", systemContent.toString());
        messages.add(systemMessage);

        for (Map<String, Object> record : history) {
            String role = (String) record.get("role");
            if (!"system".equals(role)) {
                messages.add(record);
            }
        }

        Map<String, Object> userMsg = new HashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", userMessage);
        messages.add(userMsg);

        return messages;
    }

    public List<Map<String, Object>> convertHistoryToLLMMessages(List<Map<String, String>> history,
                                                                  String systemPrompt) {
        List<Map<String, Object>> messages = new ArrayList<>();

        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            Map<String, Object> systemMessage = new HashMap<>();
            systemMessage.put("role", "system");
            systemMessage.put("content", systemPrompt);
            messages.add(systemMessage);
        }

        if (history != null) {
            for (Map<String, String> msg : history) {
                if (msg.containsKey("content") && msg.get("content") != null && !msg.get("content").isEmpty()) {
                    Map<String, Object> msgCopy = new HashMap<>();
                    msgCopy.putAll(msg);
                    messages.add(msgCopy);
                }
            }
        }

        return messages;
    }

    // ==================== 记忆提取 ====================

    public void extractAndSaveMemory(String sessionId, Long userId, String model) {
        if (userId == null) {
            return;
        }
        try {
            List<Map<String, Object>> messages = new ArrayList<>();
            List<ChatMessageRecord> records = getMessagesBySessionId(sessionId);
            for (ChatMessageRecord record : records) {
                Map<String, Object> msg = new HashMap<>();
                msg.put("role", record.getRole());
                msg.put("content", record.getContent());
                messages.add(msg);
            }

            if (messages.size() >= 2) {
                knowledgeExtractorService.extractAsync(userId, sessionId, messages, model);
                log.info("已触发异步知识提取 (session={}, userId={})", sessionId, userId);
            }
        } catch (Exception e) {
            log.warn("自动提取记忆失败：{}", e.getMessage());
        }
    }

    /**
     * 结束对话，触发图谱提炼到全局图谱
     * 在对话结束时调用，将对话级图谱提炼到全局图谱并更新用户画像
     */
    public void finalizeConversation(String sessionId, Long userId) {
        finalizeConversation(sessionId, userId, null);
    }

    /**
     * 结束对话，触发知识提取和图谱提炼
     * 完整流程：知识提取 → 全局提炼 → 用户画像更新
     *
     * @param sessionId 会话ID
     * @param userId 用户ID
     * @param model 模型名称（用于知识提取）
     */
    public void finalizeConversation(String sessionId, Long userId, String model) {
        if (userId == null || sessionId == null) {
            return;
        }

        log.info("开始结束对话流程: sessionId={}, userId={}", sessionId, userId);

        // 1. 检查是否已完成提取
        if (knowledgeExtractorService.isExtractionCompleted(sessionId)) {
            log.info("Session {} 已完成知识提取，直接触发图谱提炼", sessionId);
            // 已提取过，直接触发图谱提炼
            conversationGraphService.finalizeConversationGraph(userId, sessionId);
            return;
        }

        // 2. 获取对话消息
        List<Map<String, Object>> messages = new ArrayList<>();
        List<ChatMessageRecord> records = getMessagesBySessionId(sessionId);
        for (ChatMessageRecord record : records) {
            Map<String, Object> msg = new HashMap<>();
            msg.put("role", record.getRole());
            msg.put("content", record.getContent());
            messages.add(msg);
        }

        // 3. 使用双重触发机制检查是否应该提取
        if (!knowledgeExtractorService.shouldTriggerExtraction(messages)) {
            log.info("不满足知识提取条件，跳过提取: sessionId={}, messageCount={}",
                sessionId, messages.size());
            return;
        }

        // 4. 触发知识提取（提取完成后会自动触发图谱提炼）
        if (extractionProperties.isAutoExtractOnFinalize()) {
            knowledgeExtractorService.extractAsync(userId, sessionId, messages, model);
            log.info("已触发异步知识提取 (session={}, userId={}, messages={})",
                sessionId, userId, messages.size());
        } else {
            // 配置关闭自动提取时，直接触发图谱提炼（假设已有对话级图谱）
            conversationGraphService.finalizeConversationGraph(userId, sessionId);
        }
    }

    /**
     * 强制重新提取并结束对话
     * 忽略已完成标记，重新进行知识提取
     */
    public void forceFinalizeConversation(String sessionId, Long userId, String model) {
        if (userId == null || sessionId == null) {
            return;
        }

        log.info("强制重新提取并结束对话: sessionId={}, userId={}", sessionId, userId);

        // 清除提取状态
        knowledgeExtractorService.clearExtractionStatus(sessionId);

        // 重新执行结束流程
        finalizeConversation(sessionId, userId, model);
    }

    /**
     * 检查会话是否已完成知识提取
     */
    public boolean isExtractionCompleted(String sessionId) {
        return knowledgeExtractorService.isExtractionCompleted(sessionId);
    }

    /**
     * 结束对话并同步提炼图谱
     */
    public void finalizeConversationSync(String sessionId, Long userId) {
        if (userId == null || sessionId == null) {
            return;
        }
        try {
            conversationGraphService.finalizeConversationGraphSync(userId, sessionId);
            log.info("对话图谱提炼完成 (session={}, userId={})", sessionId, userId);
        } catch (Exception e) {
            log.warn("对话图谱提炼失败：{}", e.getMessage());
        }
    }

    // ==================== 私有辅助方法 ====================

    private String extractTitle(List<Map<String, String>> messages) {
        for (Map<String, String> msg : messages) {
            if ("user".equals(msg.get("role")) && msg.get("content") != null && !msg.get("content").isEmpty()) {
                String content = msg.get("content");
                return content.length() > 50 ? content.substring(0, 50) + "..." : content;
            }
        }
        return null;
    }

    private List<Map<String, String>> buildMessageList(String message, List<Map<String, String>> history,
                                                        String assistantContent) {
        List<Map<String, String>> allMessages = new ArrayList<>();
        
        if (history != null) {
            for (Map<String, String> h : history) {
                Map<String, String> m = new HashMap<>();
                m.put("role", h.get("role"));
                m.put("content", h.get("content"));
                allMessages.add(m);
            }
        }
        
        Map<String, String> userMsg = new HashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", message);
        allMessages.add(userMsg);

        Map<String, String> assistantMsg = new HashMap<>();
        assistantMsg.put("role", "assistant");
        assistantMsg.put("content", assistantContent);
        allMessages.add(assistantMsg);

        return allMessages;
    }
}
