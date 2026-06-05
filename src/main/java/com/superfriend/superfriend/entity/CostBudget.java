package com.superfriend.superfriend.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CostBudget {
    private Long id;
    private Long userId;
    private Double dailyBudget;
    private Double monthlyBudget;
    private Double sessionBudget;
    private Double reflectionBudgetRatio;
    private Double thoughtBudgetRatio;
    private Boolean strictMode;
    private Boolean alertEnabled;
    private Double alertThreshold;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createdTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updatedTime;
}
