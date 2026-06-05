package com.superfriend.superfriend.controller;

import com.superfriend.superfriend.dto.LLMCallRecord;
import com.superfriend.superfriend.service.LLMCallMonitorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * LLM 监控控制器
 * 提供 LLM 调用记录的查询和管理接口
 */
@Slf4j
@RestController
@RequestMapping("/api/v16/llm-monitor")
@CrossOrigin(origins = "*")
@Tag(name = "LLM 监控", description = "LLM 请求/响应监控接口")
public class LLMMonitorController {

    @Resource
    private LLMCallMonitorService llmCallMonitorService;

    /**
     * 获取会话的 LLM 调用记录列表
     *
     * @param sessionId 会话 ID
     * @return 调用记录列表
     */
    @GetMapping("/sessions/{sessionId}/calls")
    @Operation(summary = "获取会话调用记录", description = "获取指定会话的所有 LLM 调用记录")
    public Map<String, Object> getSessionCalls(
            @Parameter(description = "会话 ID") @PathVariable String sessionId) {
        try {
            List<LLMCallRecord> records = llmCallMonitorService.getSessionRecords(sessionId);
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", records);
            result.put("total", records.size());
            return result;
        } catch (Exception e) {
            log.error("[LLMMonitor] 获取会话调用记录失败: sessionId={}, error={}",
                    sessionId, e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return error;
        }
    }

    /**
     * 获取单条调用记录详情
     *
     * @param callId    调用 ID
     * @param sessionId 会话 ID
     * @return 调用记录详情
     */
    @GetMapping("/calls/{callId}")
    @Operation(summary = "获取调用详情", description = "获取单条 LLM 调用记录的详细信息")
    public Map<String, Object> getCallDetail(
            @Parameter(description = "调用 ID") @PathVariable String callId,
            @Parameter(description = "会话 ID") @RequestParam String sessionId) {
        try {
            LLMCallRecord record = llmCallMonitorService.getRecordDetail(callId, sessionId);
            Map<String, Object> result = new HashMap<>();
            if (record == null) {
                result.put("success", false);
                result.put("error", "记录不存在");
            } else {
                result.put("success", true);
                result.put("data", record);
            }
            return result;
        } catch (Exception e) {
            log.error("[LLMMonitor] 获取调用详情失败: callId={}, sessionId={}, error={}",
                    callId, sessionId, e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return error;
        }
    }

    /**
     * 清除会话的监控记录
     *
     * @param sessionId 会话 ID
     * @return 操作结果
     */
    @DeleteMapping("/sessions/{sessionId}/calls")
    @Operation(summary = "清除会话记录", description = "清除指定会话的所有 LLM 调用记录")
    public Map<String, Object> clearSessionCalls(
            @Parameter(description = "会话 ID") @PathVariable String sessionId) {
        try {
            llmCallMonitorService.clearSessionRecords(sessionId);
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "记录已清除");
            return result;
        } catch (Exception e) {
            log.error("[LLMMonitor] 清除会话记录失败: sessionId={}, error={}",
                    sessionId, e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return error;
        }
    }

    /**
     * 获取所有会话的统计信息
     *
     * @return 会话统计信息
     */
    @GetMapping("/stats")
    @Operation(summary = "获取统计信息", description = "获取所有会话的 LLM 调用统计信息")
    public Map<String, Object> getStats() {
        try {
            Map<String, Integer> stats = llmCallMonitorService.getSessionStats();
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", stats);
            result.put("totalSessions", stats.size());
            result.put("totalCalls", stats.values().stream().mapToInt(Integer::intValue).sum());
            return result;
        } catch (Exception e) {
            log.error("[LLMMonitor] 获取统计信息失败: error={}", e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return error;
        }
    }
}
