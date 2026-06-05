package com.superfriend.superfriend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PermissionCheckResult {
    private boolean allowed;
    private boolean requiresApproval;
    private boolean denied;
    private String permissionLevel;
    private String reason;
    private String matchedPolicyId;

    public static PermissionCheckResult autoAllow(String reason) {
        PermissionCheckResult result = new PermissionCheckResult();
        result.allowed = true;
        result.requiresApproval = false;
        result.denied = false;
        result.permissionLevel = "auto_allow";
        result.reason = reason;
        return result;
    }

    public static PermissionCheckResult needsApproval(String reason) {
        PermissionCheckResult result = new PermissionCheckResult();
        result.allowed = false;
        result.requiresApproval = true;
        result.denied = false;
        result.permissionLevel = "confirm";
        result.reason = reason;
        return result;
    }

    public static PermissionCheckResult denied(String reason) {
        PermissionCheckResult result = new PermissionCheckResult();
        result.allowed = false;
        result.requiresApproval = false;
        result.denied = true;
        result.permissionLevel = "deny";
        result.reason = reason;
        return result;
    }
}
