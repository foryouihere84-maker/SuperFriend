package com.superfriend.superfriend.controller;

import com.superfriend.superfriend.dto.ApiResponse;
import com.superfriend.superfriend.dto.TaskPlanDTO;
import com.superfriend.superfriend.service.TaskPlannerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v16/task-plan")
@CrossOrigin(origins = "*")
@Tag(name = "任务计划管理", description = "Agent 任务计划查询与管理接口")
public class TaskPlanController {

    @Autowired
    private TaskPlannerService taskPlannerService;

    @GetMapping("/{planId}")
    @Operation(summary = "获取计划详情", description = "根据 planId 获取任务计划及其所有步骤")
    public ApiResponse<TaskPlanDTO> getPlan(@PathVariable String planId) {
        try {
            TaskPlanDTO plan = taskPlannerService.getPlanByPlanId(planId);
            if (plan == null) {
                return ApiResponse.error("计划不存在");
            }
            return ApiResponse.success(plan);
        } catch (Exception e) {
            log.error("获取计划详情失败：{}", e.getMessage(), e);
            return ApiResponse.error("获取计划详情失败：" + e.getMessage());
        }
    }

    @GetMapping("/session/{sessionId}")
    @Operation(summary = "获取会话的计划列表", description = "根据 sessionId 获取该会话的所有任务计划")
    public ApiResponse<List<TaskPlanDTO>> getPlansBySession(@PathVariable String sessionId) {
        try {
            List<TaskPlanDTO> plans = taskPlannerService.getPlansBySessionId(sessionId);
            return ApiResponse.success(plans);
        } catch (Exception e) {
            log.error("获取会话计划列表失败：{}", e.getMessage(), e);
            return ApiResponse.error("获取会话计划列表失败：" + e.getMessage());
        }
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "获取用户的计划列表", description = "根据 userId 获取该用户最近的任务计划")
    public ApiResponse<List<TaskPlanDTO>> getPlansByUser(
            @PathVariable Long userId,
            @RequestParam(required = false, defaultValue = "20") int limit) {
        try {
            if (limit > 100) limit = 100;
            List<TaskPlanDTO> plans = taskPlannerService.getPlansByUserId(userId, limit);
            return ApiResponse.success(plans);
        } catch (Exception e) {
            log.error("获取用户计划列表失败：{}", e.getMessage(), e);
            return ApiResponse.error("获取用户计划列表失败：" + e.getMessage());
        }
    }
}
