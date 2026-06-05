package com.superfriend.superfriend.controller;

import com.superfriend.superfriend.dto.ApiResponse;
import com.superfriend.superfriend.dto.ApprovalDecisionDTO;
import com.superfriend.superfriend.dto.ApprovalRequestDTO;
import com.superfriend.superfriend.dto.PermissionPolicyDTO;
import com.superfriend.superfriend.entity.AgentApprovalLog;
import com.superfriend.superfriend.entity.AgentPermissionPolicy;
import com.superfriend.superfriend.mapper.AgentApprovalLogMapper;
import com.superfriend.superfriend.service.ApprovalService;
import com.superfriend.superfriend.service.PermissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/v16/permission")
@CrossOrigin(origins = "*")
@Tag(name = "权限管理", description = "Agent 权限策略管理接口")
public class PermissionController {

    @Autowired
    private PermissionService permissionService;

    @Autowired
    private AgentApprovalLogMapper approvalLogMapper;

    @GetMapping("/policies")
    @Operation(summary = "获取用户权限策略列表", description = "获取指定用户的所有权限策略")
    public ApiResponse<List<PermissionPolicyDTO>> getPolicies(@RequestParam Long userId) {
        try {
            List<AgentPermissionPolicy> policies = permissionService.getUserPolicies(userId);
            List<PermissionPolicyDTO> dtos = policies.stream().map(p -> {
                PermissionPolicyDTO dto = new PermissionPolicyDTO();
                dto.setId(p.getId());
                dto.setUserId(p.getUserId());
                dto.setToolName(p.getToolName());
                dto.setOperationType(p.getOperationType());
                dto.setResourcePattern(p.getResourcePattern());
                dto.setPermissionLevel(p.getPermissionLevel());
                dto.setDescription(p.getDescription());
                return dto;
            }).collect(Collectors.toList());
            return ApiResponse.success(dtos);
        } catch (Exception e) {
            log.error("获取权限策略列表失败：{}", e.getMessage(), e);
            return ApiResponse.error("获取权限策略列表失败：" + e.getMessage());
        }
    }

    @PostMapping("/policies")
    @Operation(summary = "添加权限策略", description = "为用户添加新的权限策略")
    public ApiResponse<PermissionPolicyDTO> addPolicy(@RequestBody PermissionPolicyDTO dto) {
        try {
            AgentPermissionPolicy policy = permissionService.addPolicy(dto);
            PermissionPolicyDTO result = new PermissionPolicyDTO();
            result.setId(policy.getId());
            result.setUserId(policy.getUserId());
            result.setToolName(policy.getToolName());
            result.setOperationType(policy.getOperationType());
            result.setResourcePattern(policy.getResourcePattern());
            result.setPermissionLevel(policy.getPermissionLevel());
            result.setDescription(policy.getDescription());
            return ApiResponse.success("策略添加成功", result);
        } catch (Exception e) {
            log.error("添加权限策略失败：{}", e.getMessage(), e);
            return ApiResponse.error("添加权限策略失败：" + e.getMessage());
        }
    }

    @PutMapping("/policies/{id}")
    @Operation(summary = "更新权限策略", description = "更新指定的权限策略")
    public ApiResponse<PermissionPolicyDTO> updatePolicy(@PathVariable Long id, @RequestBody PermissionPolicyDTO dto) {
        try {
            AgentPermissionPolicy policy = permissionService.updatePolicy(id, dto);
            if (policy == null) {
                return ApiResponse.error("策略不存在");
            }
            PermissionPolicyDTO result = new PermissionPolicyDTO();
            result.setId(policy.getId());
            result.setUserId(policy.getUserId());
            result.setToolName(policy.getToolName());
            result.setOperationType(policy.getOperationType());
            result.setResourcePattern(policy.getResourcePattern());
            result.setPermissionLevel(policy.getPermissionLevel());
            result.setDescription(policy.getDescription());
            return ApiResponse.success("策略更新成功", result);
        } catch (Exception e) {
            log.error("更新权限策略失败：{}", e.getMessage(), e);
            return ApiResponse.error("更新权限策略失败：" + e.getMessage());
        }
    }

    @DeleteMapping("/policies/{id}")
    @Operation(summary = "删除权限策略", description = "删除指定的权限策略")
    public ApiResponse<String> deletePolicy(@PathVariable Long id) {
        try {
            boolean deleted = permissionService.deletePolicy(id);
            if (!deleted) {
                return ApiResponse.error("策略不存在");
            }
            return ApiResponse.success("策略已删除", null);
        } catch (Exception e) {
            log.error("删除权限策略失败：{}", e.getMessage(), e);
            return ApiResponse.error("删除权限策略失败：" + e.getMessage());
        }
    }

    @GetMapping("/approval-logs")
    @Operation(summary = "获取审批日志", description = "获取用户的审批操作日志")
    public ApiResponse<List<AgentApprovalLog>> getApprovalLogs(
            @RequestParam Long userId,
            @RequestParam(required = false, defaultValue = "50") int limit) {
        try {
            if (limit > 200) limit = 200;
            List<AgentApprovalLog> logs = approvalLogMapper.findByUserId(userId, limit);
            return ApiResponse.success(logs);
        } catch (Exception e) {
            log.error("获取审批日志失败：{}", e.getMessage(), e);
            return ApiResponse.error("获取审批日志失败：" + e.getMessage());
        }
    }
}
