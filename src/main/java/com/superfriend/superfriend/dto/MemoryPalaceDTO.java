package com.superfriend.superfriend.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 记忆宫殿DTO
 */
@Data
public class MemoryPalaceDTO {

    private Long id;
    private Long userId;

    private String memoryType;
    private String category;
    private String title;
    private String content;
    private String keywords;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime memoryTime;
    private String sessionId;

    private Integer importance;
    private BigDecimal confidence;
    private Integer accessCount;
    private Integer reinforceCount;

    private BigDecimal decayRate;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime lastAccessTime;
    private BigDecimal effectiveScore;

    private List<Long> relatedMemoryIds;
    private Long sourceNodeId;

    private String emotionTag;
    private List<String> contextTags;
    private String sourceText;

    private String status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createdTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updatedTime;

    /**
     * 时间线DTO
     */
    @Data
    public static class TimelineDTO {
        private LocalDate date;
        private Integer memoryCount;
        private List<MemoryPalaceDTO> memories;
    }

    /**
     * 统计DTO
     */
    @Data
    public static class StatsDTO {
        private Integer totalMemories;
        private Integer coreCount;
        private Integer importantCount;
        private Integer normalCount;
        private Integer ephemeralCount;
        private Integer archivedCount;
        private Double averageEffectiveScore;
        private Integer totalAccessCount;
        private Integer totalReinforceCount;
        private LocalDateTime oldestMemoryTime;
        private LocalDateTime newestMemoryTime;
    }

    /**
     * 创建请求
     */
    @Data
    public static class CreateRequest {
        private Long userId;
        private String memoryType;
        private String category;
        private String title;
        private String content;
        private String keywords;
        private String sessionId;
        private Integer importance;
        private String emotionTag;
        private List<String> contextTags;
        private String sourceText;
    }

    /**
     * 搜索请求
     */
    @Data
    public static class SearchRequest {
        private Long userId;
        private String keyword;
        private String memoryType;
        private String category;
        private LocalDate fromDate;
        private LocalDate toDate;
        private Integer limit;
        private Integer offset;
    }
}
