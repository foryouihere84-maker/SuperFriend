package com.superfriend.superfriend.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class AgentTrustBoundary {
    private Long id;
    private Long userId;
    private String boundaryName;
    private String boundaryType;
    private String pattern;
    private Integer trustLevel;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createdTime;
}
