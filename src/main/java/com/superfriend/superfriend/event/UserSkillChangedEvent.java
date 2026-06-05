package com.superfriend.superfriend.event;

import lombok.Data;

@Data
public class UserSkillChangedEvent {
    private final Long userId;
    private final ChangeType changeType;

    public enum ChangeType {
        SELECTION_CHANGED,  // is_selected 变更
        ENABLED_CHANGED,    // is_enabled 变更
        ADDED,              // 新增关联
        UPDATED,            // 更新
        REMOVED             // 删除
    }

    public UserSkillChangedEvent(Long userId, ChangeType changeType) {
        this.userId = userId;
        this.changeType = changeType;
    }
}
