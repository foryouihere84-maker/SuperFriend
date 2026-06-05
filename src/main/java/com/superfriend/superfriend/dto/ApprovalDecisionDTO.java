package com.superfriend.superfriend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalDecisionDTO {
    private String requestId;
    private String decision;
    private String reason;
    private boolean alwaysAllow;
}
