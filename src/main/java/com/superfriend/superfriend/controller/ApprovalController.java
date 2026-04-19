package com.superfriend.superfriend.controller;

import com.superfriend.superfriend.dto.ApiResponse;
import com.superfriend.superfriend.dto.ApprovalDecisionDTO;
import com.superfriend.superfriend.dto.ApprovalRequestDTO;
import com.superfriend.superfriend.service.ApprovalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v16/approval")
@CrossOrigin(origins = "*")
@Tag(name = "审批管理", description = "Agent 工具调用审批接口")
public class ApprovalController {

    @Autowired
    private ApprovalService approvalService;

    @GetMapping("/pending/{requestId}")
    @Operation(summary = "获取待审批请求", description = "根据 requestId 获取待审批的请求详情")
    public ApiResponse<ApprovalRequestDTO> getPendingApproval(@PathVariable String requestId) {
        ApprovalRequestDTO request = approvalService.getPendingApproval(requestId);
        if (request == null) {
            return ApiResponse.error("审批请求不存在或已过期");
        }
        return ApiResponse.success(request);
    }

    @GetMapping("/pending/session/{sessionId}")
    @Operation(summary = "获取会话的待审批列表", description = "获取指定会话的所有待审批请求")
    public ApiResponse<List<ApprovalRequestDTO>> getPendingApprovalsForSession(@PathVariable String sessionId) {
        List<ApprovalRequestDTO> requests = approvalService.getPendingApprovalsForSession(sessionId);
        return ApiResponse.success(requests);
    }

    @PostMapping("/decide")
    @Operation(summary = "审批决定", description = "对工具调用审批请求做出决定（允许/拒绝）")
    public ApiResponse<String> decide(@RequestBody ApprovalDecisionDTO decision) {
        try {
            if (decision.getRequestId() == null || decision.getRequestId().trim().isEmpty()) {
                return ApiResponse.error("缺少 requestId 参数");
            }
            if (decision.getDecision() == null || decision.getDecision().trim().isEmpty()) {
                return ApiResponse.error("缺少 decision 参数");
            }
            if (!"approved".equals(decision.getDecision()) && !"denied".equals(decision.getDecision())) {
                return ApiResponse.error("无效的 decision 参数，支持：approved, denied");
            }

            boolean success = approvalService.decide(decision.getRequestId(), decision);
            if (!success) {
                return ApiResponse.error("审批请求不存在或已处理");
            }
            String msg = "approved".equals(decision.getDecision()) ? "已允许" : "已拒绝";
            return ApiResponse.success(msg);
        } catch (Exception e) {
            log.error("审批决定失败：{}", e.getMessage(), e);
            return ApiResponse.error("审批决定失败：" + e.getMessage());
        }
    }
}
