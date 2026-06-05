package com.superfriend.superfriend.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class UserSkill {
    private Long id;
    private Long userId;
    private Long skillId;
    private Integer isEnabled;
    private Boolean isSelected;
    private Integer customPriority;
    private String customTags;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
