package com.superfriend.superfriend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.superfriend.superfriend.dto.MemoryPalaceDTO;
import com.superfriend.superfriend.entity.MemoryConnection;
import com.superfriend.superfriend.entity.MemoryEvent;
import com.superfriend.superfriend.entity.MemoryPalace;
import com.superfriend.superfriend.mapper.MemoryConnectionMapper;
import com.superfriend.superfriend.mapper.MemoryEventMapper;
import com.superfriend.superfriend.mapper.MemoryPalaceMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 记忆宫殿核心服务
 * 负责记忆的创建、更新、删除和智能提取
 */
@Slf4j
@Service
public class MemoryPalaceService {

    @Autowired
    private MemoryPalaceMapper memoryPalaceMapper;

    @Autowired
    private MemoryEventMapper memoryEventMapper;

    @Autowired
    private MemoryConnectionMapper memoryConnectionMapper;

    @Autowired
    private MemoryTriggerAnalyzer triggerAnalyzer;

    @Autowired
    private MemoryLifecycleService lifecycleService;

    @Autowired
    private MemoryRetrievalService retrievalService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    @Lazy
    private LLMClient llmClient;

    @Autowired
    @Lazy
    private AIModelConfigService modelConfigService;

    // ==================== 记忆创建 ====================

    /**
     * 智能创建记忆（从对话中提取）
     *
     * @param userId 用户ID
     * @param sessionId 会话ID
     * @param userMessage 用户消息
     * @param assistantReply 助手回复
     * @return 创建的记忆（如果触发）
     */
    @Transactional
    public MemoryPalace createFromConversation(Long userId, String sessionId,
                                                String userMessage, String assistantReply) {
        // 1. 分析是否值得记忆
        MemoryTriggerAnalyzer.TriggerResult triggerResult = triggerAnalyzer.analyze(userMessage, assistantReply);

        if (!triggerResult.isShouldRemember()) {
            log.debug("不值得记忆: {}", triggerResult.getReason());
            return null;
        }

        // 2. 提取记忆内容
        MemoryContent extractedContent = extractMemoryContent(userMessage, assistantReply, triggerResult);

        if (extractedContent == null || extractedContent.title == null || extractedContent.title.isEmpty()) {
            log.debug("无法提取有效记忆内容");
            return null;
        }

        // 3. 检查是否与已有记忆重复
        if (isDuplicateMemory(userId, extractedContent.title)) {
            log.debug("记忆重复，跳过创建: {}", extractedContent.title);
            // 如果重复，强化已有记忆
            MemoryPalace existing = findSimilarMemory(userId, extractedContent.title);
            if (existing != null) {
                lifecycleService.reinforceMemory(existing.getId(), sessionId);
            }
            return null;
        }

        // 4. 创建记忆
        MemoryPalace memory = new MemoryPalace();
        memory.setUserId(userId);
        memory.setMemoryType(triggerResult.getMemoryType());
        memory.setCategory(triggerResult.getCategory());
        memory.setTitle(extractedContent.title);
        memory.setContent(extractedContent.content);
        memory.setKeywords(extractedContent.keywords != null ?
            String.join(",", extractedContent.keywords) : "");
        memory.setMemoryTime(LocalDateTime.now());
        memory.setSessionId(sessionId);
        memory.setImportance(triggerResult.getImportance());
        memory.setConfidence(BigDecimal.valueOf(0.8));
        memory.setAccessCount(0);
        memory.setReinforceCount(0);
        memory.setDecayRate(MemoryPalace.getDefaultDecayRate(triggerResult.getMemoryType()));
        memory.setLastAccessTime(LocalDateTime.now());
        memory.setEmotionTag(triggerResult.getEmotionTag());
        memory.setSourceText(userMessage);
        memory.setStatus(MemoryPalace.STATUS_ACTIVE);

        // 计算初始有效分数
        BigDecimal effectiveScore = lifecycleService.calculateEffectiveScore(memory);
        memory.setEffectiveScore(effectiveScore);

        // 保存
        memoryPalaceMapper.insert(memory);

        // 记录事件
        recordEvent(userId, memory.getId(), MemoryEvent.EVENT_CREATE,
            "触发原因: " + triggerResult.getReason(), sessionId);

        log.info("创建记忆: id={}, type={}, title={}, reason={}",
            memory.getId(), memory.getMemoryType(), memory.getTitle(), triggerResult.getReason());

        return memory;
    }

    /**
     * 异步创建记忆
     */
    @Async("asyncTaskExecutor")
    public void createFromConversationAsync(Long userId, String sessionId,
                                             String userMessage, String assistantReply) {
        try {
            createFromConversation(userId, sessionId, userMessage, assistantReply);
        } catch (Exception e) {
            log.error("异步创建记忆失败: userId={}, sessionId={}, error={}",
                userId, sessionId, e.getMessage());
        }
    }

    /**
     * 手动创建记忆
     */
    @Transactional
    public MemoryPalace createMemory(MemoryPalaceDTO.CreateRequest request) {
        MemoryPalace memory = new MemoryPalace();
        memory.setUserId(request.getUserId());
        memory.setMemoryType(request.getMemoryType() != null ? request.getMemoryType() : MemoryPalace.TYPE_NORMAL);
        memory.setCategory(request.getCategory());
        memory.setTitle(request.getTitle());
        memory.setContent(request.getContent());
        memory.setKeywords(request.getKeywords());
        memory.setMemoryTime(LocalDateTime.now());
        memory.setSessionId(request.getSessionId());
        memory.setImportance(request.getImportance() != null ? request.getImportance() : 5);
        memory.setConfidence(BigDecimal.valueOf(0.9));
        memory.setAccessCount(0);
        memory.setReinforceCount(0);
        memory.setDecayRate(MemoryPalace.getDefaultDecayRate(memory.getMemoryType()));
        memory.setLastAccessTime(LocalDateTime.now());
        memory.setEmotionTag(request.getEmotionTag());
        memory.setSourceText(request.getSourceText());
        memory.setStatus(MemoryPalace.STATUS_ACTIVE);

        if (request.getContextTags() != null && !request.getContextTags().isEmpty()) {
            try {
                memory.setContextTags(objectMapper.writeValueAsString(request.getContextTags()));
            } catch (Exception e) {
                log.warn("序列化 contextTags 失败: {}", e.getMessage());
            }
        }

        BigDecimal effectiveScore = lifecycleService.calculateEffectiveScore(memory);
        memory.setEffectiveScore(effectiveScore);

        memoryPalaceMapper.insert(memory);

        recordEvent(request.getUserId(), memory.getId(), MemoryEvent.EVENT_CREATE,
            "手动创建", request.getSessionId());

        log.info("手动创建记忆: id={}, title={}", memory.getId(), memory.getTitle());

        return memory;
    }

    // ==================== 记忆内容提取 ====================

    /**
     * 从对话中提取记忆内容
     */
    private MemoryContent extractMemoryContent(String userMessage, String assistantReply,
                                               MemoryTriggerAnalyzer.TriggerResult triggerResult) {
        MemoryContent content = new MemoryContent();

        String category = triggerResult.getCategory();

        // 根据不同类型提取
        if (MemoryPalace.CATEGORY_PREFERENCE.equals(category)) {
            content.title = extractPreferenceTitle(userMessage);
            content.content = extractPreferenceContent(userMessage);
        } else if (MemoryPalace.CATEGORY_SKILL.equals(category)) {
            content.title = extractSkillTitle(userMessage);
            content.content = extractSkillContent(userMessage);
        } else if (MemoryPalace.CATEGORY_DECISION.equals(category)) {
            content.title = extractDecisionTitle(userMessage);
            content.content = extractDecisionContent(userMessage);
        } else if (MemoryPalace.CATEGORY_FACT.equals(category)) {
            content.title = extractFactTitle(userMessage, assistantReply);
            content.content = extractFactContent(userMessage, assistantReply);
        } else {
            // 默认提取
            content.title = extractGeneralTitle(userMessage);
            content.content = userMessage;
        }

        // 提取关键词
        content.keywords = extractKeywordsFromText(userMessage + " " + (assistantReply != null ? assistantReply : ""));

        return content;
    }

    private String extractPreferenceTitle(String message) {
        // 提取偏好主题
        Pattern pattern = Pattern.compile("(我喜欢|我讨厌|我偏好|我习惯|我更)(.{2,20})");
        java.util.regex.Matcher matcher = pattern.matcher(message);
        if (matcher.find()) {
            return "偏好: " + matcher.group(2).trim();
        }
        return "偏好: " + message.substring(0, Math.min(30, message.length()));
    }

    private String extractPreferenceContent(String message) {
        return message;
    }

    private String extractSkillTitle(String message) {
        Pattern pattern = Pattern.compile("(我会|我能|我擅长|我精通|我熟悉)(.{2,20})");
        java.util.regex.Matcher matcher = pattern.matcher(message);
        if (matcher.find()) {
            return "技能: " + matcher.group(2).trim();
        }
        return "技能: " + message.substring(0, Math.min(30, message.length()));
    }

    private String extractSkillContent(String message) {
        return message;
    }

    private String extractDecisionTitle(String message) {
        Pattern pattern = Pattern.compile("(我决定|我选择|我定了)(.{2,20})");
        java.util.regex.Matcher matcher = pattern.matcher(message);
        if (matcher.find()) {
            return "决策: " + matcher.group(2).trim();
        }
        return "决策: " + message.substring(0, Math.min(30, message.length()));
    }

    private String extractDecisionContent(String message) {
        return message;
    }

    private String extractFactTitle(String userMessage, String assistantReply) {
        // 从问题中提取主题
        String question = userMessage.replaceAll("(什么是|是什么|怎么|如何|为什么|谁|哪个)", "").trim();
        if (question.length() > 30) {
            question = question.substring(0, 30);
        }
        return "知识点: " + question;
    }

    private String extractFactContent(String userMessage, String assistantReply) {
        StringBuilder sb = new StringBuilder();
        sb.append("问题: ").append(userMessage).append("\n");
        if (assistantReply != null && assistantReply.length() > 200) {
            sb.append("答案: ").append(assistantReply.substring(0, 200)).append("...");
        } else if (assistantReply != null) {
            sb.append("答案: ").append(assistantReply);
        }
        return sb.toString();
    }

    private String extractGeneralTitle(String message) {
        String title = message.replaceAll("[?？!！。]", "").trim();
        if (title.length() > 50) {
            title = title.substring(0, 50);
        }
        return title;
    }

    private List<String> extractKeywordsFromText(String text) {
        // 简化实现：提取重要词汇
        List<String> keywords = new ArrayList<>();
        String[] words = text.split("[\\s,，。.!！?？;；:：\"\"''「」【】]");
        for (String word : words) {
            if (word.length() >= 2 && word.length() <= 10) {
                keywords.add(word);
            }
        }
        return keywords.size() > 5 ? keywords.subList(0, 5) : keywords;
    }

    // ==================== 重复检测 ====================

    /**
     * 检查是否与已有记忆重复
     */
    private boolean isDuplicateMemory(Long userId, String title) {
        MemoryPalace existing = findSimilarMemory(userId, title);
        return existing != null;
    }

    /**
     * 查找相似记忆
     */
    private MemoryPalace findSimilarMemory(Long userId, String title) {
        List<MemoryPalace> memories = memoryPalaceMapper.findByUserId(userId);
        String normalizedTitle = normalizeTitle(title);

        for (MemoryPalace memory : memories) {
            String normalizedExisting = normalizeTitle(memory.getTitle());
            double similarity = calculateSimilarity(normalizedTitle, normalizedExisting);
            if (similarity > 0.85) {
                return memory;
            }
        }
        return null;
    }

    private String normalizeTitle(String title) {
        if (title == null) return "";
        return title.toLowerCase().replaceAll("[\\s\\p{Punct}]", "").trim();
    }

    private double calculateSimilarity(String s1, String s2) {
        if (s1.isEmpty() || s2.isEmpty()) return 0.0;
        if (s1.equals(s2)) return 1.0;

        int maxLen = Math.max(s1.length(), s2.length());
        int editDistance = calculateEditDistance(s1, s2);
        return 1.0 - (double) editDistance / maxLen;
    }

    private int calculateEditDistance(String s1, String s2) {
        int m = s1.length();
        int n = s2.length();
        int[][] dp = new int[m + 1][n + 1];

        for (int i = 0; i <= m; i++) dp[i][0] = i;
        for (int j = 0; j <= n; j++) dp[0][j] = j;

        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                if (s1.charAt(i - 1) == s2.charAt(j - 1)) {
                    dp[i][j] = dp[i - 1][j - 1];
                } else {
                    dp[i][j] = Math.min(Math.min(dp[i - 1][j], dp[i][j - 1]), dp[i - 1][j - 1]) + 1;
                }
            }
        }
        return dp[m][n];
    }

    // ==================== 记忆更新 ====================

    /**
     * 更新记忆
     */
    @Transactional
    public MemoryPalace updateMemory(Long memoryId, MemoryPalaceDTO updateDTO) {
        MemoryPalace memory = memoryPalaceMapper.findById(memoryId);
        if (memory == null) {
            return null;
        }

        if (updateDTO.getTitle() != null) {
            memory.setTitle(updateDTO.getTitle());
        }
        if (updateDTO.getContent() != null) {
            memory.setContent(updateDTO.getContent());
        }
        if (updateDTO.getKeywords() != null) {
            memory.setKeywords(updateDTO.getKeywords());
        }
        if (updateDTO.getImportance() != null) {
            memory.setImportance(updateDTO.getImportance());
        }

        memoryPalaceMapper.update(memory);

        recordEvent(memory.getUserId(), memoryId, MemoryEvent.EVENT_ACCESS,
            "记忆更新", null);

        return memory;
    }

    /**
     * 更改记忆类型
     */
    @Transactional
    public void changeMemoryType(Long memoryId, String newType) {
        MemoryPalace memory = memoryPalaceMapper.findById(memoryId);
        if (memory == null) {
            return;
        }

        String oldType = memory.getMemoryType();
        memory.setMemoryType(newType);
        memory.setDecayRate(MemoryPalace.getDefaultDecayRate(newType));
        memory.setEffectiveScore(lifecycleService.calculateEffectiveScore(memory));

        memoryPalaceMapper.update(memory);

        recordEvent(memory.getUserId(), memoryId, MemoryEvent.EVENT_UPGRADE,
            "类型变更: " + oldType + " -> " + newType, null);

        log.info("记忆类型变更: id={}, {} -> {}", memoryId, oldType, newType);
    }

    // ==================== 记忆删除 ====================

    /**
     * 删除记忆
     */
    @Transactional
    public void deleteMemory(Long memoryId) {
        lifecycleService.deleteMemory(memoryId, null);
    }

    /**
     * 删除用户所有记忆
     */
    @Transactional
    public void deleteAllMemories(Long userId) {
        memoryConnectionMapper.deleteByUserId(userId);
        memoryEventMapper.deleteByUserId(userId);
        memoryPalaceMapper.deleteByUserId(userId);

        log.info("删除用户所有记忆: userId={}", userId);
    }

    // ==================== 记忆关联 ====================

    /**
     * 创建记忆关联
     */
    @Transactional
    public void createConnection(Long userId, Long sourceId, Long targetId,
                                 String connectionType, BigDecimal strength) {
        MemoryConnection connection = new MemoryConnection();
        connection.setUserId(userId);
        connection.setSourceMemoryId(sourceId);
        connection.setTargetMemoryId(targetId);
        connection.setConnectionType(connectionType != null ? connectionType : MemoryConnection.TYPE_RELATED);
        connection.setStrength(strength != null ? strength : BigDecimal.valueOf(0.5));

        memoryConnectionMapper.insertOrUpdate(connection);

        log.info("创建记忆关联: source={}, target={}, type={}", sourceId, targetId, connectionType);
    }

    // ==================== 查询方法 ====================

    /**
     * 获取用户所有记忆
     */
    public List<MemoryPalaceDTO> getAllMemories(Long userId) {
        List<MemoryPalace> memories = memoryPalaceMapper.findByUserId(userId);
        return memories.stream()
            .map(retrievalService::toDTO)
            .collect(Collectors.toList());
    }

    /**
     * 获取记忆详情
     */
    public MemoryPalaceDTO getMemory(Long memoryId) {
        MemoryPalace memory = memoryPalaceMapper.findById(memoryId);
        return retrievalService.toDTO(memory);
    }

    /**
     * 获取统计信息
     */
    public MemoryPalaceDTO.StatsDTO getStats(Long userId) {
        MemoryLifecycleService.MemoryPalaceStats stats = lifecycleService.getStats(userId);

        MemoryPalaceDTO.StatsDTO dto = new MemoryPalaceDTO.StatsDTO();
        dto.setTotalMemories(stats.getTotalCount());
        dto.setCoreCount(stats.getCoreCount());
        dto.setImportantCount(stats.getImportantCount());
        dto.setNormalCount(stats.getNormalCount());
        dto.setEphemeralCount(stats.getEphemeralCount());
        dto.setAverageEffectiveScore(stats.getAverageEffectiveScore() != null ?
            stats.getAverageEffectiveScore().doubleValue() : 0.0);
        dto.setOldestMemoryTime(stats.getOldestMemoryTime());
        dto.setNewestMemoryTime(stats.getNewestMemoryTime());

        return dto;
    }

    // ==================== 事件记录 ====================

    private void recordEvent(Long userId, Long memoryId, String eventType,
                            String eventData, String sessionId) {
        MemoryEvent event = new MemoryEvent();
        event.setUserId(userId);
        event.setMemoryId(memoryId);
        event.setEventType(eventType);
        event.setEventTime(LocalDateTime.now());
        event.setEventData(eventData);
        event.setSessionId(sessionId);

        memoryEventMapper.insert(event);
    }

    // ==================== 辅助类 ====================

    @lombok.Data
    private static class MemoryContent {
        String title;
        String content;
        List<String> keywords;
    }
}