package com.superfriend.superfriend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PermissionPolicyDTO {
    private Long id;
    private Long userId;
    private String toolName;
    private String operationType;
    private String resourcePattern;
    private String permissionLevel;
    private String description;
}
