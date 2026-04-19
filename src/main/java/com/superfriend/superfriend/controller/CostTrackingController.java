package com.superfriend.superfriend.controller;

import com.superfriend.superfriend.dto.AIChatResponse;
import com.superfriend.superfriend.service.CostTrackingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v16/cost")
@CrossOrigin(origins = "*")
@Tag(name = "成本追踪", description = "API 调用成本追踪和预算管理")
public class CostTrackingController {

    @Autowired
    private CostTrackingService costTrackingService;

    @GetMapping("/metrics")
    @Operation(summary = "获取成本指标", description = "获取全局成本统计指标")
    public ResponseEntity<Map<String, Object>> getMetrics() {
        return ResponseEntity.ok(costTrackingService.getCostMetrics());
    }

    @GetMapping("/session/{sessionId}")
    @Operation(summary = "获取会话成本", description = "获取指定会话的成本详情")
    public ResponseEntity<?> getSessionCost(@PathVariable String sessionId) {
        CostTrackingService.SessionCost cost = costTrackingService.getSessionCost(sessionId);
        if (cost == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(cost);
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "获取用户成本", description = "获取指定用户的成本统计")
    public ResponseEntity<?> getUserCost(@PathVariable Long userId) {
        CostTrackingService.UserCost cost = costTrackingService.getUserCost(userId);
        if (cost == null) {
            Map<String, Object> empty = new HashMap<>();
            empty.put("userId", userId);
            empty.put("totalTokens", 0);
            empty.put("totalApiCalls", 0);
            empty.put("totalCost", 0.0);
            return ResponseEntity.ok(empty);
        }
        return ResponseEntity.ok(cost);
    }

    @GetMapping("/daily/{date}")
    @Operation(summary = "获取每日成本", description = "获取指定日期的成本统计")
    public ResponseEntity<?> getDailyCost(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        CostTrackingService.DailyCost cost = costTrackingService.getDailyCost(date);
        if (cost == null) {
            Map<String, Object> empty = new HashMap<>();
            empty.put("date", date);
            empty.put("totalTokens", 0);
            empty.put("totalApiCalls", 0);
            empty.put("totalCost", 0.0);
            return ResponseEntity.ok(empty);
        }
        return ResponseEntity.ok(cost);
    }

    @GetMapping("/report")
    @Operation(summary = "生成成本报告", description = "生成指定日期范围内的成本报告")
    public ResponseEntity<CostTrackingService.CostReport> generateReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(costTrackingService.generateReport(startDate, endDate));
    }

    @GetMapping("/budget/{userId}")
    @Operation(summary = "检查预算状态", description = "检查指定用户的预算使用情况")
    public ResponseEntity<CostTrackingService.BudgetStatus> checkBudget(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "10.0") double dailyBudget,
            @RequestParam(defaultValue = "100.0") double monthlyBudget) {
        return ResponseEntity.ok(costTrackingService.checkBudget(userId, dailyBudget, monthlyBudget));
    }

    @GetMapping("/pricing/{modelId}")
    @Operation(summary = "获取模型定价", description = "获取指定模型的定价信息")
    public ResponseEntity<CostTrackingService.ModelPricing> getModelPricing(@PathVariable String modelId) {
        return ResponseEntity.ok(costTrackingService.getModelPricing(modelId));
    }

    @PostMapping("/pricing/{modelId}")
    @Operation(summary = "设置模型定价", description = "设置指定模型的定价信息")
    public ResponseEntity<Map<String, Object>> setModelPricing(
            @PathVariable String modelId,
            @RequestParam double inputPricePer1k,
            @RequestParam double outputPricePer1k) {
        costTrackingService.setModelPricing(modelId, inputPricePer1k, outputPricePer1k);
        Map<String, Object> result = new HashMap<>();
        result.put("modelId", modelId);
        result.put("inputPricePer1k", inputPricePer1k);
        result.put("outputPricePer1k", outputPricePer1k);
        result.put("message", "定价设置成功");
        return ResponseEntity.ok(result);
    }

    @PostMapping("/cleanup")
    @Operation(summary = "清理过期数据", description = "清理过期的成本追踪数据")
    public ResponseEntity<Map<String, Object>> cleanupData(
            @RequestParam(defaultValue = "30") int maxAgeDays) {
        costTrackingService.cleanupOldData(maxAgeDays);
        Map<String, Object> result = new HashMap<>();
        result.put("message", "清理完成");
        result.put("maxAgeDays", maxAgeDays);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/calculate")
    @Operation(summary = "计算成本", description = "计算指定模型和 token 数量的成本")
    public ResponseEntity<Map<String, Object>> calculateCost(
            @RequestParam String modelId,
            @RequestParam long inputTokens,
            @RequestParam long outputTokens) {
        double cost = costTrackingService.calculateCost(modelId, inputTokens, outputTokens);
        Map<String, Object> result = new HashMap<>();
        result.put("modelId", modelId);
        result.put("inputTokens", inputTokens);
        result.put("outputTokens", outputTokens);
        result.put("totalTokens", inputTokens + outputTokens);
        result.put("cost", cost);
        return ResponseEntity.ok(result);
    }
}
