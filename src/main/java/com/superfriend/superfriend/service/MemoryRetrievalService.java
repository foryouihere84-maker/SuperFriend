package com.superfriend.superfriend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.superfriend.superfriend.dto.MemoryPalaceDTO;
import com.superfriend.superfriend.entity.MemoryPalace;
import com.superfriend.superfriend.mapper.MemoryPalaceMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 记忆检索服务
 * 负责记忆的检索、关键词匹配和上下文生成
 */
@Slf4j
@Service
public class MemoryRetrievalService {

    @Autowired
    private MemoryPalaceMapper memoryPalaceMapper;

    @Autowired
    private MemoryLifecycleService lifecycleService;

    @Autowired
    private ObjectMapper objectMapper;

    // ==================== 核心检索方法 ====================

    /**
     * 检索相关记忆（用于注入到 LLM 上下文）
     *
     * @param userId 用户ID
     * @param currentMessage 当前消息
     * @param limit 最大返回数量
     * @return 相关记忆列表
     */
    @Transactional
    public List<MemoryPalace> retrieveRelevantMemories(Long userId, String currentMessage, int limit) {
        if (userId == null || currentMessage == null || currentMessage.trim().isEmpty()) {
            return Collections.emptyList();
        }

        List<MemoryPalace> result = new ArrayList<>();

        // 1. 提取当前消息的关键词
        List<String> keywords = extractKeywords(currentMessage);
        log.debug("提取关键词: {}", keywords);

        // 2. 按关键词匹配检索
        List<MemoryPalace> allMemories = memoryPalaceMapper.findByUserId(userId);

        // 3. 计算相关性分数并排序
        List<ScoredMemory> scoredMemories = allMemories.stream()
            .map(m -> new ScoredMemory(m, calculateRelevanceScore(m, keywords, currentMessage)))
            .filter(sm -> sm.score > 0)
            .sorted((a, b) -> Double.compare(b.score, a.score))
            .collect(Collectors.toList());

        // 4. 优先返回核心记忆
        List<MemoryPalace> coreMemories = scoredMemories.stream()
            .filter(sm -> MemoryPalace.TYPE_CORE.equals(sm.memory.getMemoryType()))
            .limit(3)
            .map(sm -> sm.memory)
            .collect(Collectors.toList());
        result.addAll(coreMemories);

        // 5. 补充重要记忆
        if (result.size() < limit) {
            List<MemoryPalace> importantMemories = scoredMemories.stream()
                .filter(sm -> MemoryPalace.TYPE_IMPORTANT.equals(sm.memory.getMemoryType()))
                .filter(sm -> !result.contains(sm.memory))
                .limit(limit - result.size())
                .map(sm -> sm.memory)
                .collect(Collectors.toList());
            result.addAll(importantMemories);
        }

        // 6. 补充普通记忆
        if (result.size() < limit) {
            List<MemoryPalace> normalMemories = scoredMemories.stream()
                .filter(sm -> MemoryPalace.TYPE_NORMAL.equals(sm.memory.getMemoryType()))
                .filter(sm -> !result.contains(sm.memory))
                .limit(limit - result.size())
                .map(sm -> sm.memory)
                .collect(Collectors.toList());
            result.addAll(normalMemories);
        }

        // 7. 更新访问记录
        for (MemoryPalace memory : result) {
            memoryPalaceMapper.incrementAccessCount(memory.getId());
        }

        log.info("检索到 {} 条相关记忆 (userId={}, keywords={})", result.size(), userId, keywords);

        return result;
    }

    /**
     * 计算相关性分数
     */
    private double calculateRelevanceScore(MemoryPalace memory, List<String> keywords, String currentMessage) {
        double score = 0.0;

        // 1. 关键词匹配分数
        String memoryText = (memory.getTitle() + " " + memory.getContent() + " " +
            (memory.getKeywords() != null ? memory.getKeywords() : "")).toLowerCase();

        int matchedKeywords = 0;
        for (String keyword : keywords) {
            if (memoryText.contains(keyword.toLowerCase())) {
                matchedKeywords++;
            }
        }
        score += matchedKeywords * 0.2;

        // 2. 有效分数权重
        if (memory.getEffectiveScore() != null) {
            score += memory.getEffectiveScore().doubleValue() * 0.3;
        }

        // 3. 记忆类型权重
        switch (memory.getMemoryType()) {
            case MemoryPalace.TYPE_CORE: score += 0.5; break;
            case MemoryPalace.TYPE_IMPORTANT: score += 0.3; break;
            case MemoryPalace.TYPE_NORMAL: score += 0.1; break;
        }

        // 4. 时效性权重（最近的记忆略高）
        if (memory.getMemoryTime() != null) {
            long daysAgo = ChronoUnit.DAYS.between(memory.getMemoryTime(), LocalDateTime.now());
            if (daysAgo < 7) {
                score += 0.1;
            } else if (daysAgo < 30) {
                score += 0.05;
            }
        }

        // 5. 访问频率权重
        if (memory.getAccessCount() != null && memory.getAccessCount() > 0) {
            score += Math.min(0.1, memory.getAccessCount() * 0.01);
        }

        return score;
    }

    // ==================== 上下文生成 ====================

    /**
     * 生成记忆上下文（注入到 LLM）
     */
    public String generateMemoryContext(Long userId, String currentMessage) {
        List<MemoryPalace> memories = retrieveRelevantMemories(userId, currentMessage, 10);

        if (memories.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("## 🧠 记忆宫殿\n\n");
        sb.append("以下是与当前问题相关的用户记忆，可在回答中参考：\n\n");

        // 按类型分组
        Map<String, List<MemoryPalace>> grouped = memories.stream()
            .collect(Collectors.groupingBy(MemoryPalace::getMemoryType));

        // 核心记忆 - 使用表格
        if (grouped.containsKey(MemoryPalace.TYPE_CORE)) {
            sb.append("### ⭐ 核心记忆（长期重要）\n");
            sb.append("| 标题 | 分类 | 时间 | 内容摘要 |\n");
            sb.append("|------|------|------|----------|\n");
            for (MemoryPalace m : grouped.get(MemoryPalace.TYPE_CORE)) {
                sb.append("| **").append(m.getTitle()).append("** ");
                sb.append("| ").append(getCategoryLabel(m.getCategory())).append(" ");
                sb.append("| ").append(formatTime(m.getMemoryTime())).append(" ");
                String content = "";
                if (m.getContent() != null && !m.getContent().isEmpty()) {
                    content = m.getContent().length() > 50
                        ? m.getContent().substring(0, 50).replace("\n", " ") + "..."
                        : m.getContent().replace("\n", " ");
                }
                sb.append("| ").append(content).append(" |\n");
            }
            sb.append("\n");
        }

        // 重要记忆 - 使用表格
        if (grouped.containsKey(MemoryPalace.TYPE_IMPORTANT)) {
            sb.append("### 📌 重要记忆\n");
            sb.append("| 标题 | 分类 | 时间 |\n");
            sb.append("|------|------|------|\n");
            for (MemoryPalace m : grouped.get(MemoryPalace.TYPE_IMPORTANT)) {
                sb.append("| **").append(m.getTitle()).append("** ");
                sb.append("| ").append(getCategoryLabel(m.getCategory())).append(" ");
                sb.append("| ").append(formatTime(m.getMemoryTime())).append(" |\n");
            }
            sb.append("\n");
        }

        // 普通记忆 - 简洁列表
        if (grouped.containsKey(MemoryPalace.TYPE_NORMAL)) {
            sb.append("### 📎 相关记忆\n");
            for (MemoryPalace m : grouped.get(MemoryPalace.TYPE_NORMAL)) {
                sb.append("- ").append(m.getTitle());
                sb.append(" [").append(getCategoryLabel(m.getCategory())).append("]\n");
            }
        }

        sb.append("\n💡 **提示**: 如果用户问题涉及这些记忆，优先使用记忆中的信息。\n");

        return sb.toString();
    }

    /**
     * 获取分类标签
     */
    private String getCategoryLabel(String category) {
        if (category == null) return "其他";
        switch (category) {
            case MemoryPalace.CATEGORY_PREFERENCE: return "偏好";
            case MemoryPalace.CATEGORY_SKILL: return "技能";
            case MemoryPalace.CATEGORY_DECISION: return "决策";
            case MemoryPalace.CATEGORY_FACT: return "事实";
            case MemoryPalace.CATEGORY_RELATION: return "关系";
            case MemoryPalace.CATEGORY_EXPERIENCE: return "经历";
            default: return category;
        }
    }

    /**
     * 格式化时间显示
     */
    private String formatTime(LocalDateTime time) {
        if (time == null) return "";
        long days = ChronoUnit.DAYS.between(time, LocalDateTime.now());
        if (days == 0) return "今天";
        if (days == 1) return "昨天";
        if (days < 7) return days + "天前";
        if (days < 30) return (days / 7) + "周前";
        if (days < 365) return (days / 30) + "月前";
        return (days / 365) + "年前";
    }

    // ==================== 关键词提取 ====================

    /**
     * 从消息中提取关键词
     */
    private List<String> extractKeywords(String message) {
        if (message == null || message.trim().isEmpty()) {
            return Collections.emptyList();
        }

        List<String> keywords = new ArrayList<>();

        // 分词（简化实现，按空格和标点分割）
        String[] tokens = message.replaceAll("[\\uFF0C\\u3002\\uFF01\\uFF1F\\u3001\\uFF1B\\uFF1A\\u201C\\u201D\\u2018\\u2019\\uFF08\\uFF09\\u3010\\u3011\\u300A\\u300B\\s]+", " ")
            .trim().split("\\s+");

        // 停用词
        Set<String> stopWords = new HashSet<>(Arrays.asList(
            "的", "了", "在", "是", "我", "有", "和", "就", "不", "人", "都", "一", "一个",
            "上", "也", "很", "到", "说", "要", "去", "你", "会", "着", "没有", "看", "好",
            "自己", "这", "他", "她", "它", "们", "那", "什么", "怎么", "如何", "请", "帮",
            "the", "a", "an", "is", "are", "was", "were", "be", "been", "being",
            "have", "has", "had", "do", "does", "did", "will", "would", "could",
            "should", "may", "might", "can", "shall", "to", "of", "in", "for",
            "on", "with", "at", "by", "from", "as", "into", "through", "and",
            "but", "or", "not", "no", "if", "then", "so", "than", "too", "very"
        ));

        for (String token : tokens) {
            if (token.length() >= 2 && !stopWords.contains(token.toLowerCase())) {
                keywords.add(token);
            }
        }

        return keywords.size() > 10 ? keywords.subList(0, 10) : keywords;
    }

    // ==================== 时间线检索 ====================

    /**
     * 获取时间线
     */
    public List<MemoryPalaceDTO.TimelineDTO> getTimeline(Long userId, LocalDate from, LocalDate to) {
        if (from == null) {
            from = LocalDate.now().minusDays(30);
        }
        if (to == null) {
            to = LocalDate.now().plusDays(1);
        }

        List<MemoryPalace> memories = memoryPalaceMapper.findByTimeRange(
            userId,
            from.atStartOfDay(),
            to.plusDays(1).atStartOfDay()
        );

        // 按日期分组
        Map<LocalDate, List<MemoryPalace>> grouped = memories.stream()
            .collect(Collectors.groupingBy(m -> m.getMemoryTime().toLocalDate()));

        List<MemoryPalaceDTO.TimelineDTO> timeline = new ArrayList<>();
        for (LocalDate date : grouped.keySet().stream().sorted(Comparator.reverseOrder()).collect(Collectors.toList())) {
            MemoryPalaceDTO.TimelineDTO dto = new MemoryPalaceDTO.TimelineDTO();
            dto.setDate(date);
            dto.setMemoryCount(grouped.get(date).size());
            dto.setMemories(grouped.get(date).stream()
                .map(this::toDTO)
                .collect(Collectors.toList()));
            timeline.add(dto);
        }

        return timeline;
    }

    /**
     * 按日期获取记忆
     */
    public List<MemoryPalaceDTO> getMemoriesByDate(Long userId, LocalDate date) {
        List<MemoryPalace> memories = memoryPalaceMapper.findByTimeRange(
            userId,
            date.atStartOfDay(),
            date.plusDays(1).atStartOfDay()
        );
        return memories.stream().map(this::toDTO).collect(Collectors.toList());
    }

    // ==================== 搜索 ====================

    /**
     * 搜索记忆
     */
    public List<MemoryPalaceDTO> searchMemories(Long userId, String keyword, String memoryType, int limit) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return Collections.emptyList();
        }

        String searchKeyword = "%" + keyword.trim() + "%";
        List<MemoryPalace> memories = memoryPalaceMapper.searchByKeyword(userId, searchKeyword, limit);

        if (memoryType != null && !memoryType.isEmpty()) {
            memories = memories.stream()
                .filter(m -> memoryType.equals(m.getMemoryType()))
                .collect(Collectors.toList());
        }

        return memories.stream().map(this::toDTO).collect(Collectors.toList());
    }

    // ==================== 转换方法 ====================

    /**
     * 实体转DTO
     */
    public MemoryPalaceDTO toDTO(MemoryPalace entity) {
        if (entity == null) return null;

        MemoryPalaceDTO dto = new MemoryPalaceDTO();
        dto.setId(entity.getId());
        dto.setUserId(entity.getUserId());
        dto.setMemoryType(entity.getMemoryType());
        dto.setCategory(entity.getCategory());
        dto.setTitle(entity.getTitle());
        dto.setContent(entity.getContent());
        dto.setKeywords(entity.getKeywords());
        dto.setMemoryTime(entity.getMemoryTime());
        dto.setSessionId(entity.getSessionId());
        dto.setImportance(entity.getImportance());
        dto.setConfidence(entity.getConfidence());
        dto.setAccessCount(entity.getAccessCount());
        dto.setReinforceCount(entity.getReinforceCount());
        dto.setDecayRate(entity.getDecayRate());
        dto.setLastAccessTime(entity.getLastAccessTime());
        dto.setEffectiveScore(entity.getEffectiveScore());
        dto.setSourceNodeId(entity.getSourceNodeId());
        dto.setEmotionTag(entity.getEmotionTag());
        dto.setSourceText(entity.getSourceText());
        dto.setStatus(entity.getStatus());
        dto.setCreatedTime(entity.getCreatedTime());
        dto.setUpdatedTime(entity.getUpdatedTime());

        // 解析 JSON 字段
        if (entity.getRelatedMemoryIds() != null && !entity.getRelatedMemoryIds().isEmpty()) {
            try {
                dto.setRelatedMemoryIds(objectMapper.readValue(entity.getRelatedMemoryIds(),
                    new TypeReference<List<Long>>() {}));
            } catch (Exception e) {
                log.warn("解析 relatedMemoryIds 失败: {}", e.getMessage());
            }
        }

        if (entity.getContextTags() != null && !entity.getContextTags().isEmpty()) {
            try {
                dto.setContextTags(objectMapper.readValue(entity.getContextTags(),
                    new TypeReference<List<String>>() {}));
            } catch (Exception e) {
                log.warn("解析 contextTags 失败: {}", e.getMessage());
            }
        }

        return dto;
    }

    // ==================== 辅助类 ====================

    @lombok.Data
    private static class ScoredMemory {
        final MemoryPalace memory;
        final double score;
    }
}
