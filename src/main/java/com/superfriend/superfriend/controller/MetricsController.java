package com.superfriend.superfriend.controller;

import com.superfriend.superfriend.service.AgentMetricsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/metrics")
@Tag(name = "指标管理", description = "Agent执行指标查询接口")
@Slf4j
public class MetricsController {

    @Autowired
    private AgentMetricsService metricsService;

    @GetMapping("/aggregated")
    @Operation(summary = "获取聚合指标", description = "获取所有执行的聚合统计指标")
    public ResponseEntity<AgentMetricsService.AggregatedMetrics> getAggregatedMetrics() {
        AgentMetricsService.AggregatedMetrics metrics = metricsService.getAggregatedMetrics();
        return ResponseEntity.ok(metrics);
    }

    @GetMapping("/recent")
    @Operation(summary = "获取最近执行记录", description = "获取最近执行的记录列表")
    public ResponseEntity<List<AgentMetricsService.ExecutionSummary>> getRecentExecutions(
            @RequestParam(defaultValue = "20") int limit) {
        List<AgentMetricsService.ExecutionSummary> executions = metricsService.getRecentExecutions(limit);
        return ResponseEntity.ok(executions);
    }

    @GetMapping("/execution/{executionId}")
    @Operation(summary = "获取执行详情", description = "获取指定执行的详细指标")
    public ResponseEntity<?> getExecutionMetrics(@PathVariable String executionId) {
        AgentMetricsService.ExecutionMetrics metrics = metricsService.getExecutionMetrics(executionId);
        if (metrics == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(metrics);
    }

    @GetMapping("/tools/top")
    @Operation(summary = "获取热门工具", description = "获取调用次数最多的工具列表")
    public ResponseEntity<List<AgentMetricsService.ToolMetrics>> getTopTools(
            @RequestParam(defaultValue = "10") int limit) {
        List<AgentMetricsService.ToolMetrics> tools = metricsService.getTopTools(limit);
        return ResponseEntity.ok(tools);
    }

    @GetMapping("/tools/problematic")
    @Operation(summary = "获取问题工具", description = "获取成功率低于阈值的工具列表")
    public ResponseEntity<List<AgentMetricsService.ToolMetrics>> getProblematicTools(
            @RequestParam(defaultValue = "0.7") double threshold) {
        List<AgentMetricsService.ToolMetrics> tools = metricsService.getProblematicTools(threshold);
        return ResponseEntity.ok(tools);
    }

    @GetMapping("/tools/{serverName}/{toolName}")
    @Operation(summary = "获取工具指标", description = "获取指定工具的详细指标")
    public ResponseEntity<?> getToolMetrics(
            @PathVariable String serverName,
            @PathVariable String toolName) {
        AgentMetricsService.ToolMetrics metrics = metricsService.getToolMetrics(toolName, serverName);
        if (metrics == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(metrics);
    }

    @GetMapping("/models/{modelId}")
    @Operation(summary = "获取模型指标", description = "获取指定模型的详细指标")
    public ResponseEntity<?> getModelMetrics(@PathVariable String modelId) {
        AgentMetricsService.ModelMetrics metrics = metricsService.getModelMetrics(modelId);
        if (metrics == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(metrics);
    }

    @GetMapping("/health")
    @Operation(summary = "获取健康状态", description = "获取Agent执行的健康状态")
    public ResponseEntity<Map<String, Object>> getHealthStatus() {
        Map<String, Object> health = metricsService.getHealthStatus();
        return ResponseEntity.ok(health);
    }

    @GetMapping("/dashboard")
    @Operation(summary = "获取仪表盘数据", description = "获取仪表盘展示所需的聚合数据")
    public ResponseEntity<Map<String, Object>> getDashboard() {
        Map<String, Object> dashboard = new HashMap<>();
        
        AgentMetricsService.AggregatedMetrics aggregated = metricsService.getAggregatedMetrics();
        dashboard.put("aggregated", aggregated);
        
        List<AgentMetricsService.ExecutionSummary> recentExecutions = metricsService.getRecentExecutions(10);
        dashboard.put("recentExecutions", recentExecutions);
        
        List<AgentMetricsService.ToolMetrics> topTools = metricsService.getTopTools(5);
        dashboard.put("topTools", topTools);
        
        Map<String, Object> health = metricsService.getHealthStatus();
        dashboard.put("health", health);
        
        return ResponseEntity.ok(dashboard);
    }

    @DeleteMapping("/cleanup")
    @Operation(summary = "清理过期指标", description = "清理超过指定小时数的过期指标")
    public ResponseEntity<Map<String, String>> cleanupOldMetrics(
            @RequestParam(defaultValue = "24") int maxAgeHours) {
        metricsService.cleanupOldMetrics(maxAgeHours);
        
        Map<String, String> result = new HashMap<>();
        result.put("status", "success");
        result.put("message", "已清理 " + maxAgeHours + " 小时前的过期指标");
        
        return ResponseEntity.ok(result);
    }
}
