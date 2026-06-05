package com.superfriend.superfriend.controller;

import com.superfriend.superfriend.dto.MemoryPalaceDTO;
import com.superfriend.superfriend.entity.MemoryPalace;
import com.superfriend.superfriend.service.MemoryLifecycleService;
import com.superfriend.superfriend.service.MemoryPalaceService;
import com.superfriend.superfriend.service.MemoryRetrievalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 记忆宫殿控制器
 * 宫殿式记忆系统的 API 接口
 */
@Slf4j
@RestController
@RequestMapping("/api/memory-palace")
@Tag(name = "记忆宫殿", description = "宫殿式记忆系统 API")
public class MemoryPalaceController {

    @Autowired
    private MemoryPalaceService memoryPalaceService;

    @Autowired
    private MemoryRetrievalService retrievalService;

    @Autowired
    private MemoryLifecycleService lifecycleService;

    // ==================== 记忆创建 ====================

    @PostMapping("/create")
    @Operation(summary = "手动创建记忆", description = "手动添加一条记忆到记忆宫殿")
    public Map<String, Object> createMemory(@RequestBody MemoryPalaceDTO.CreateRequest request) {
        Map<String, Object> result = new HashMap<>();

        try {
            MemoryPalace memory = memoryPalaceService.createMemory(request);
            result.put("success", true);
            result.put("memory", retrievalService.toDTO(memory));
            result.put("message", "记忆创建成功");
        } catch (Exception e) {
            log.error("创建记忆失败: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("message", "创建失败: " + e.getMessage());
        }

        return result;
    }

    @PostMapping("/extract")
    @Operation(summary = "从对话提取记忆", description = "从对话中智能提取记忆")
    public Map<String, Object> extractFromConversation(@RequestBody Map<String, Object> body) {
        Long userId = body.get("userId") != null ? Long.valueOf(body.get("userId").toString()) : null;
        String sessionId = (String) body.get("sessionId");
        String userMessage = (String) body.get("userMessage");
        String assistantReply = (String) body.get("assistantReply");

        Map<String, Object> result = new HashMap<>();

        try {
            MemoryPalace memory = memoryPalaceService.createFromConversation(
                userId, sessionId, userMessage, assistantReply);

            if (memory != null) {
                result.put("success", true);
                result.put("memory", retrievalService.toDTO(memory));
                result.put("message", "记忆提取成功");
            } else {
                result.put("success", false);
                result.put("message", "未触发记忆条件");
            }
        } catch (Exception e) {
            log.error("提取记忆失败: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("message", "提取失败: " + e.getMessage());
        }

        return result;
    }

    // ==================== 记忆查询 ====================

    @GetMapping("/list/{userId}")
    @Operation(summary = "获取用户所有记忆", description = "获取用户记忆宫殿中的所有记忆")
    public Map<String, Object> getAllMemories(@PathVariable Long userId) {
        Map<String, Object> result = new HashMap<>();

        try {
            List<MemoryPalaceDTO> memories = memoryPalaceService.getAllMemories(userId);
            result.put("success", true);
            result.put("memories", memories);
            result.put("total", memories.size());
        } catch (Exception e) {
            log.error("获取记忆列表失败: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("message", "获取失败: " + e.getMessage());
        }

        return result;
    }

    @GetMapping("/{memoryId}")
    @Operation(summary = "获取记忆详情", description = "获取单条记忆的详细信息")
    public Map<String, Object> getMemory(@PathVariable Long memoryId) {
        Map<String, Object> result = new HashMap<>();

        try {
            MemoryPalaceDTO memory = memoryPalaceService.getMemory(memoryId);
            if (memory != null) {
                result.put("success", true);
                result.put("memory", memory);
            } else {
                result.put("success", false);
                result.put("message", "记忆不存在");
            }
        } catch (Exception e) {
            log.error("获取记忆详情失败: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("message", "获取失败: " + e.getMessage());
        }

        return result;
    }

    @GetMapping("/stats/{userId}")
    @Operation(summary = "获取记忆统计", description = "获取用户记忆宫殿的统计信息")
    public Map<String, Object> getStats(@PathVariable Long userId) {
        Map<String, Object> result = new HashMap<>();

        try {
            MemoryPalaceDTO.StatsDTO stats = memoryPalaceService.getStats(userId);
            result.put("success", true);
            result.put("stats", stats);
        } catch (Exception e) {
            log.error("获取统计信息失败: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("message", "获取失败: " + e.getMessage());
        }

        return result;
    }

    // ==================== 时间线 ====================

    @GetMapping("/timeline/{userId}")
    @Operation(summary = "获取记忆时间线", description = "按时间线展示记忆")
    public Map<String, Object> getTimeline(
            @PathVariable Long userId,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate to) {
        Map<String, Object> result = new HashMap<>();

        try {
            List<MemoryPalaceDTO.TimelineDTO> timeline = retrievalService.getTimeline(userId, from, to);
            result.put("success", true);
            result.put("timeline", timeline);
        } catch (Exception e) {
            log.error("获取时间线失败: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("message", "获取失败: " + e.getMessage());
        }

        return result;
    }

    @GetMapping("/by-date/{userId}/{date}")
    @Operation(summary = "按日期获取记忆", description = "获取指定日期的记忆")
    public Map<String, Object> getMemoriesByDate(
            @PathVariable Long userId,
            @PathVariable @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date) {
        Map<String, Object> result = new HashMap<>();

        try {
            List<MemoryPalaceDTO> memories = retrievalService.getMemoriesByDate(userId, date);
            result.put("success", true);
            result.put("memories", memories);
            result.put("date", date.toString());
        } catch (Exception e) {
            log.error("按日期获取记忆失败: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("message", "获取失败: " + e.getMessage());
        }

        return result;
    }

    // ==================== 搜索 ====================

    @GetMapping("/search/{userId}")
    @Operation(summary = "搜索记忆", description = "按关键词搜索记忆")
    public Map<String, Object> searchMemories(
            @PathVariable Long userId,
            @RequestParam String keyword,
            @RequestParam(required = false) String memoryType,
            @RequestParam(defaultValue = "20") int limit) {
        Map<String, Object> result = new HashMap<>();

        try {
            List<MemoryPalaceDTO> memories = retrievalService.searchMemories(userId, keyword, memoryType, limit);
            result.put("success", true);
            result.put("memories", memories);
            result.put("keyword", keyword);
        } catch (Exception e) {
            log.error("搜索记忆失败: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("message", "搜索失败: " + e.getMessage());
        }

        return result;
    }

    // ==================== 记忆操作 ====================

    @PutMapping("/{memoryId}")
    @Operation(summary = "更新记忆", description = "更新记忆内容")
    public Map<String, Object> updateMemory(
            @PathVariable Long memoryId,
            @RequestBody MemoryPalaceDTO updateDTO) {
        Map<String, Object> result = new HashMap<>();

        try {
            MemoryPalace memory = memoryPalaceService.updateMemory(memoryId, updateDTO);
            if (memory != null) {
                result.put("success", true);
                result.put("memory", retrievalService.toDTO(memory));
                result.put("message", "更新成功");
            } else {
                result.put("success", false);
                result.put("message", "记忆不存在");
            }
        } catch (Exception e) {
            log.error("更新记忆失败: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("message", "更新失败: " + e.getMessage());
        }

        return result;
    }

    @DeleteMapping("/{memoryId}")
    @Operation(summary = "删除记忆", description = "删除指定记忆")
    public Map<String, Object> deleteMemory(@PathVariable Long memoryId) {
        Map<String, Object> result = new HashMap<>();

        try {
            memoryPalaceService.deleteMemory(memoryId);
            result.put("success", true);
            result.put("message", "删除成功");
        } catch (Exception e) {
            log.error("删除记忆失败: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("message", "删除失败: " + e.getMessage());
        }

        return result;
    }

    @DeleteMapping("/all/{userId}")
    @Operation(summary = "删除所有记忆", description = "删除用户所有记忆")
    public Map<String, Object> deleteAllMemories(@PathVariable Long userId) {
        Map<String, Object> result = new HashMap<>();

        try {
            memoryPalaceService.deleteAllMemories(userId);
            result.put("success", true);
            result.put("message", "所有记忆已删除");
        } catch (Exception e) {
            log.error("删除所有记忆失败: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("message", "删除失败: " + e.getMessage());
        }

        return result;
    }

    // ==================== 记忆强化 ====================

    @PostMapping("/{memoryId}/reinforce")
    @Operation(summary = "强化记忆", description = "强化记忆，提高其重要性和有效分数")
    public Map<String, Object> reinforceMemory(@PathVariable Long memoryId) {
        Map<String, Object> result = new HashMap<>();

        try {
            lifecycleService.reinforceMemory(memoryId, null);
            result.put("success", true);
            result.put("message", "记忆已强化");
        } catch (Exception e) {
            log.error("强化记忆失败: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("message", "强化失败: " + e.getMessage());
        }

        return result;
    }

    @PostMapping("/{memoryId}/change-type")
    @Operation(summary = "更改记忆类型", description = "更改记忆的类型（CORE/IMPORTANT/NORMAL/EPHEMERAL）")
    public Map<String, Object> changeMemoryType(
            @PathVariable Long memoryId,
            @RequestParam String memoryType) {
        Map<String, Object> result = new HashMap<>();

        try {
            memoryPalaceService.changeMemoryType(memoryId, memoryType);
            result.put("success", true);
            result.put("message", "类型已更改");
        } catch (Exception e) {
            log.error("更改记忆类型失败: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("message", "更改失败: " + e.getMessage());
        }

        return result;
    }

    // ==================== 上下文生成 ====================

    @GetMapping("/context/{userId}")
    @Operation(summary = "生成记忆上下文", description = "生成用于注入 LLM 的记忆上下文")
    public Map<String, Object> generateContext(
            @PathVariable Long userId,
            @RequestParam String currentMessage) {
        Map<String, Object> result = new HashMap<>();

        try {
            String context = retrievalService.generateMemoryContext(userId, currentMessage);
            result.put("success", true);
            result.put("context", context);
        } catch (Exception e) {
            log.error("生成记忆上下文失败: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("message", "生成失败: " + e.getMessage());
        }

        return result;
    }

    // ==================== 记忆关联 ====================

    @PostMapping("/connect")
    @Operation(summary = "创建记忆关联", description = "在两条记忆之间创建关联")
    public Map<String, Object> createConnection(@RequestBody Map<String, Object> body) {
        Long userId = Long.valueOf(body.get("userId").toString());
        Long sourceId = Long.valueOf(body.get("sourceMemoryId").toString());
        Long targetId = Long.valueOf(body.get("targetMemoryId").toString());
        String connectionType = (String) body.getOrDefault("connectionType", "RELATED");

        Map<String, Object> result = new HashMap<>();

        try {
            memoryPalaceService.createConnection(userId, sourceId, targetId, connectionType, null);
            result.put("success", true);
            result.put("message", "关联创建成功");
        } catch (Exception e) {
            log.error("创建记忆关联失败: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("message", "创建失败: " + e.getMessage());
        }

        return result;
    }
}
