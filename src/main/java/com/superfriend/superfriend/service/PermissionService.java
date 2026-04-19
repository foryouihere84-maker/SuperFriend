package com.superfriend.superfriend.service;

import com.superfriend.superfriend.dto.*;
import com.superfriend.superfriend.entity.AgentPermissionPolicy;
import com.superfriend.superfriend.mapper.AgentPermissionPolicyMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
public class PermissionService {

    @Autowired
    private AgentPermissionPolicyMapper policyMapper;

    private static final Set<String> READ_TOOL_PATTERNS = new HashSet<>(Arrays.asList(
            "read_file", "list_directory", "get_", "search_", "query_",
            "get_system_info", "list_files", "read_graph", "search_nodes"
    ));

    private static final Set<String> WRITE_TOOL_PATTERNS = new HashSet<>(Arrays.asList(
            "write_file", "create_", "save_", "upload_", "send_",
            "create_entities", "create_relations", "write_file"
    ));

    private static final Set<String> DELETE_TOOL_PATTERNS = new HashSet<>(Arrays.asList(
            "delete_", "remove_", "batch_delete", "rm_"
    ));

    private static final Set<String> EXECUTE_TOOL_PATTERNS = new HashSet<>(Arrays.asList(
            "run_command", "execute_", "shell_", "navigate_", "click_",
            "install_", "kill_", "browser"
    ));

    public PermissionCheckResult checkPermission(Long userId, String toolName, Map<String, Object> arguments) {
        String operationType = inferOperationType(toolName);

        Long effectiveUserId = userId != null ? userId : 0L;
        List<AgentPermissionPolicy> policies = policyMapper.findMatchingPolicies(effectiveUserId, toolName, operationType);

        if (policies == null || policies.isEmpty()) {
            log.debug("No matching policy for tool={}, operation={}, userId={}, defaulting to auto_allow",
                    toolName, operationType, userId);
            return PermissionCheckResult.autoAllow("默认允许所有操作");
        }

        for (AgentPermissionPolicy policy : policies) {
            String level = policy.getPermissionLevel();

            if ("auto_allow".equals(level)) {
                if (matchesResourcePattern(policy, arguments)) {
                    log.info("Permission auto_allowed: tool={}, policy={}", toolName, policy.getId());
                    PermissionCheckResult result = PermissionCheckResult.autoAllow(
                            policy.getDescription() != null ? policy.getDescription() : "策略自动允许");
                    result.setMatchedPolicyId(String.valueOf(policy.getId()));
                    return result;
                }
            } else if ("deny".equals(level)) {
                if (matchesResourcePattern(policy, arguments)) {
                    log.warn("Permission denied: tool={}, policy={}", toolName, policy.getId());
                    PermissionCheckResult result = PermissionCheckResult.denied(
                            policy.getDescription() != null ? policy.getDescription() : "策略拒绝");
                    result.setMatchedPolicyId(String.valueOf(policy.getId()));
                    return result;
                }
            }
        }

        log.debug("Permission auto_allowed by default: tool={}, operation={}", toolName, operationType);
        return PermissionCheckResult.autoAllow("默认允许所有操作");
    }

    public String inferOperationType(String toolName) {
        if (toolName == null) return "execute";

        String lowerName = toolName.toLowerCase();

        for (String pattern : DELETE_TOOL_PATTERNS) {
            if (lowerName.contains(pattern)) return "delete";
        }
        for (String pattern : WRITE_TOOL_PATTERNS) {
            if (lowerName.contains(pattern)) return "write";
        }
        for (String pattern : READ_TOOL_PATTERNS) {
            if (lowerName.contains(pattern)) return "read";
        }
        for (String pattern : EXECUTE_TOOL_PATTERNS) {
            if (lowerName.contains(pattern)) return "execute";
        }

        return "execute";
    }

    private boolean matchesResourcePattern(AgentPermissionPolicy policy, Map<String, Object> arguments) {
        String pattern = policy.getResourcePattern();
        if (pattern == null || pattern.trim().isEmpty()) {
            return true;
        }

        if (arguments == null || arguments.isEmpty()) {
            return true;
        }

        for (Object value : arguments.values()) {
            if (value != null && matchesGlobPattern(pattern, value.toString())) {
                return true;
            }
        }

        return false;
    }

    private boolean matchesGlobPattern(String pattern, String text) {
        if (pattern == null || text == null) return false;

        String regex = pattern
                .replace("\\", "\\\\")
                .replace(".", "\\.")
                .replace("*", ".*")
                .replace("?", ".");

        try {
            return text.matches(regex);
        } catch (Exception e) {
            return text.contains(pattern.replace("*", "").replace("?", ""));
        }
    }

    public List<AgentPermissionPolicy> getUserPolicies(Long userId) {
        return policyMapper.findByUserId(userId);
    }

    public AgentPermissionPolicy addPolicy(PermissionPolicyDTO dto) {
        AgentPermissionPolicy policy = new AgentPermissionPolicy();
        policy.setUserId(dto.getUserId());
        policy.setToolName(dto.getToolName());
        policy.setOperationType(dto.getOperationType());
        policy.setResourcePattern(dto.getResourcePattern());
        policy.setPermissionLevel(dto.getPermissionLevel());
        policy.setDescription(dto.getDescription());
        policyMapper.insert(policy);
        log.info("Added permission policy: userId={}, tool={}, level={}", dto.getUserId(), dto.getToolName(), dto.getPermissionLevel());
        return policy;
    }

    public AgentPermissionPolicy updatePolicy(Long id, PermissionPolicyDTO dto) {
        AgentPermissionPolicy policy = policyMapper.findById(id);
        if (policy == null) return null;

        if (dto.getToolName() != null) policy.setToolName(dto.getToolName());
        if (dto.getOperationType() != null) policy.setOperationType(dto.getOperationType());
        if (dto.getResourcePattern() != null) policy.setResourcePattern(dto.getResourcePattern());
        if (dto.getPermissionLevel() != null) policy.setPermissionLevel(dto.getPermissionLevel());
        if (dto.getDescription() != null) policy.setDescription(dto.getDescription());

        policyMapper.update(policy);
        log.info("Updated permission policy: id={}", id);
        return policy;
    }

    public boolean deletePolicy(Long id) {
        int rows = policyMapper.deleteById(id);
        log.info("Deleted permission policy: id={}, rows={}", id, rows);
        return rows > 0;
    }
}
