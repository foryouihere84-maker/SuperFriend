package com.superfriend.superfriend.event;

import lombok.Data;

@Data
public class CostUpdateEvent {
    private final String sessionId;
    private final Long userId;
    
    public CostUpdateEvent(String sessionId, Long userId) {
        this.sessionId = sessionId;
        this.userId = userId;
    }
}