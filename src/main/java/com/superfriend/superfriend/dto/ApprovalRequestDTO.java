package com.superfriend.superfriend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalRequestDTO {
    private String requestId;
    private String sessionId;
    private String toolName;
    private String serverName;
    private String operation;
    private String arguments;
    private String permissionLevel;
    private String description;
    private Long userId;
}
