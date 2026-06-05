package com.superfriend.superfriend.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户画像 DTO
 */
@Data
public class UserProfileDTO {

    private Long id;
    private Long userId;

    /**
     * 兴趣方向列表
     */
    private List<InterestItem> interests;

    /**
     * 性格特征列表
     */
    private List<TraitItem> traits;

    /**
     * 偏好列表
     */
    private List<PreferenceItem> preferences;

    /**
     * 技能列表
     */
    private List<SkillItem> skills;

    /**
     * 用户画像摘要文本
     */
    private String summary;

    private Integer version;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;

    // ==================== 内部类 ====================

    @Data
    public static class InterestItem {
        private String name;
        private BigDecimal confidence;
        private String lastSeen;
        private Integer mentionCount;
    }

    @Data
    public static class TraitItem {
        private String name;
        private BigDecimal confidence;
        private List<String> contexts;
        private Integer mentionCount;
    }

    @Data
    public static class PreferenceItem {
        private String name;
        private BigDecimal confidence;
        private String category;
        private Integer mentionCount;
    }

    @Data
    public static class SkillItem {
        private String name;
        private String level;
        private BigDecimal confidence;
        private Integer mentionCount;
    }
}
