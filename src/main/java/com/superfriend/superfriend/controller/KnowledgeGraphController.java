package com.superfriend.superfriend.controller;

import com.superfriend.superfriend.dto.ApiResponse;
import com.superfriend.superfriend.dto.GraphContextDTO;
import com.superfriend.superfriend.dto.KnowledgeNodeDTO;
import com.superfriend.superfriend.entity.KnowledgeRelation;
import com.superfriend.superfriend.service.KnowledgeGraphService;
import com.superfriend.superfriend.service.NodeEnrichmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 统一的知识图谱控制器
 * 合并了原 KnowledgeGraphController 和 MemoryController 的功能
 * 提供知识图谱的 CRUD、查询和记忆管理接口
 */
@Slf4j
@RestController
@RequestMapping("/api/v16/knowledge-graph")
@CrossOrigin(origins = "*")
@Tag(name = "知识图谱", description = "统一的知识图谱与记忆管理接口")
public class KnowledgeGraphController {

    @Autowired
    private KnowledgeGraphService knowledgeGraphService;

    @Autowired
    private NodeEnrichmentService nodeEnrichmentService;

    // ==================== 节点管理 ====================

    @GetMapping("/nodes")
    @Operation(summary = "获取用户所有节点", description = "获取当前用户的所有知识节点")
    public ApiResponse<List<KnowledgeNodeDTO>> getAllNodes(@RequestParam Long userId) {
        List<KnowledgeNodeDTO> nodes = knowledgeGraphService.getAllNodes(userId);
        return ApiResponse.success(nodes);
    }

    @GetMapping("/nodes/type/{type}")
    @Operation(summary = "按类型获取节点", description = "获取指定类型的知识节点")
    public ApiResponse<List<KnowledgeNodeDTO>> getNodesByType(
            @RequestParam Long userId,
            @PathVariable String type) {
        List<KnowledgeNodeDTO> nodes = knowledgeGraphService.getNodesByType(userId, type);
        return ApiResponse.success(nodes);
    }

    @GetMapping("/nodes/session/{sessionId}")
    @Operation(summary = "按会话获取节点", description = "获取来自指定会话的知识节点")
    public ApiResponse<List<KnowledgeNodeDTO>> getNodesBySession(
            @RequestParam Long userId,
            @PathVariable String sessionId) {
        List<KnowledgeNodeDTO> nodes = knowledgeGraphService.getNodesBySession(userId, sessionId);
        return ApiResponse.success(nodes);
    }

    @GetMapping("/nodes/{id}")
    @Operation(summary = "获取单个节点", description = "根据ID获取知识节点详情")
    public ApiResponse<KnowledgeNodeDTO> getNodeById(@PathVariable Long id) {
        KnowledgeNodeDTO node = knowledgeGraphService.getNodeById(id);
        if (node == null) {
            return ApiResponse.error("节点不存在");
        }
        return ApiResponse.success(node);
    }

    @PostMapping("/nodes")
    @Operation(summary = "添加节点", description = "手动添加一个知识节点（支持详细描述、关键词、重要性）")
    public ApiResponse<KnowledgeNodeDTO> addNode(@RequestBody KnowledgeNodeDTO dto) {
        if (dto.getUserId() == null) {
            return ApiResponse.error("用户ID不能为空");
        }
        if (dto.getName() == null || dto.getName().trim().isEmpty()) {
            return ApiResponse.error("节点名称不能为空");
        }
        KnowledgeNodeDTO node = knowledgeGraphService.addNode(dto);
        return ApiResponse.success(node);
    }

    @PutMapping("/nodes/{id}")
    @Operation(summary = "更新节点", description = "更新知识节点信息（包括详细描述、关键词、重要性）")
    public ApiResponse<KnowledgeNodeDTO> updateNode(
            @PathVariable Long id,
            @RequestBody KnowledgeNodeDTO dto) {
        KnowledgeNodeDTO node = knowledgeGraphService.updateNode(id, dto);
        if (node == null) {
            return ApiResponse.error("节点不存在");
        }
        return ApiResponse.success(node);
    }

    @DeleteMapping("/nodes/{id}")
    @Operation(summary = "删除节点", description = "删除知识节点及其所有关系")
    public ApiResponse<Void> deleteNode(@PathVariable Long id) {
        boolean success = knowledgeGraphService.deleteNode(id);
        if (!success) {
            return ApiResponse.error("节点不存在或删除失败");
        }
        return ApiResponse.success(null);
    }

    // ==================== 关系管理 ====================

    @GetMapping("/relations")
    @Operation(summary = "获取用户所有关系", description = "获取当前用户的所有知识关系")
    public ApiResponse<List<GraphContextDTO.RelationDTO>> getAllRelations(@RequestParam Long userId) {
        List<GraphContextDTO.RelationDTO> relations = knowledgeGraphService.getAllRelations(userId);
        return ApiResponse.success(relations);
    }

    @PostMapping("/relations")
    @Operation(summary = "添加关系", description = "在两个节点之间添加关系")
    public ApiResponse<KnowledgeRelation> addRelation(@RequestBody Map<String, Object> request) {
        Long userId = Long.valueOf(request.get("userId").toString());
        Long sourceNodeId = Long.valueOf(request.get("sourceNodeId").toString());
        Long targetNodeId = Long.valueOf(request.get("targetNodeId").toString());
        String relationType = (String) request.get("relationType");
        String properties = (String) request.get("properties");
        BigDecimal weight = request.get("weight") != null ?
            new BigDecimal(request.get("weight").toString()) : BigDecimal.ONE;

        if (relationType == null || relationType.trim().isEmpty()) {
            return ApiResponse.error("关系类型不能为空");
        }

        KnowledgeRelation relation = knowledgeGraphService.addRelation(
            userId, sourceNodeId, targetNodeId, relationType, properties, weight);
        return ApiResponse.success(relation);
    }

    @DeleteMapping("/relations/{id}")
    @Operation(summary = "删除关系", description = "删除指定的知识关系")
    public ApiResponse<Void> deleteRelation(@PathVariable Long id) {
        boolean success = knowledgeGraphService.deleteRelation(id);
        if (!success) {
            return ApiResponse.error("关系不存在或删除失败");
        }
        return ApiResponse.success(null);
    }

    @PutMapping("/relations/{id}")
    @Operation(summary = "更新关系", description = "更新知识关系信息（源节点、目标节点、关系类型、权重）")
    public ApiResponse<Void> updateRelation(@PathVariable Long id, @RequestBody Map<String, Object> request) {
        Long sourceNodeId = request.get("sourceNodeId") != null ? Long.valueOf(request.get("sourceNodeId").toString()) : null;
        Long targetNodeId = request.get("targetNodeId") != null ? Long.valueOf(request.get("targetNodeId").toString()) : null;
        String relationType = (String) request.get("relationType");
        BigDecimal weight = request.get("weight") != null ?
            new BigDecimal(request.get("weight").toString()) : null;

        boolean success = knowledgeGraphService.updateRelation(id, sourceNodeId, targetNodeId, relationType, weight);
        if (!success) {
            return ApiResponse.error("关系不存在或更新失败");
        }
        return ApiResponse.success(null);
    }

    // ==================== 图谱查询 ====================

    @GetMapping("/graph")
    @Operation(summary = "获取完整图谱", description = "获取用户的完整知识图谱数据（用于可视化）")
    public ApiResponse<GraphContextDTO> getFullGraph(@RequestParam Long userId) {
        GraphContextDTO graph = knowledgeGraphService.getFullGraph(userId);
        return ApiResponse.success(graph);
    }

    @GetMapping("/graph/conversation/{sessionId}")
    @Operation(summary = "获取对话级图谱", description = "获取单个对话的知识图谱数据")
    public ApiResponse<GraphContextDTO> getConversationGraph(
            @RequestParam Long userId,
            @PathVariable String sessionId) {
        GraphContextDTO graph = knowledgeGraphService.getConversationGraph(userId, sessionId);
        return ApiResponse.success(graph);
    }

    @GetMapping("/search")
    @Operation(summary = "搜索节点", description = "根据关键词搜索知识节点（支持名称、描述、关键词）")
    public ApiResponse<List<KnowledgeNodeDTO>> searchNodes(
            @RequestParam Long userId,
            @RequestParam String keyword,
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) String sessionId) {
        List<KnowledgeNodeDTO> nodes;
        if (sessionId != null && !sessionId.isEmpty()) {
            // 搜索对话级图谱节点
            nodes = knowledgeGraphService.searchConversationNodes(userId, sessionId, keyword);
        } else if (scope != null && !scope.isEmpty()) {
            // 按指定 scope 搜索
            nodes = knowledgeGraphService.searchNodesByScope(userId, scope, keyword);
        } else {
            // 搜索所有节点
            nodes = knowledgeGraphService.searchNodes(userId, keyword);
        }
        return ApiResponse.success(nodes);
    }

    @GetMapping("/context")
    @Operation(summary = "获取图谱上下文", description = "根据查询生成图谱上下文（用于LLM）")
    public ApiResponse<String> getGraphContext(
            @RequestParam Long userId,
            @RequestParam String query) {
        String context = knowledgeGraphService.generateGraphContextForLLM(userId, query);
        return ApiResponse.success(context);
    }

    // ==================== 统计信息 ====================

    @GetMapping("/stats")
    @Operation(summary = "获取图谱统计", description = "获取知识图谱的统计信息")
    public ApiResponse<Map<String, Object>> getStats(@RequestParam Long userId) {
        Map<String, Object> stats = knowledgeGraphService.getStats(userId);
        return ApiResponse.success(stats);
    }

    // ==================== 批量操作 ====================

    @DeleteMapping("/clear")
    @Operation(summary = "清除全部图谱数据", description = "清除用户的所有知识节点和关系（全局+对话级）")
    public ApiResponse<Map<String, Object>> clearAll(@RequestParam Long userId) {
        int deletedNodes = knowledgeGraphService.deleteAllByUserId(userId);
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("deletedNodes", deletedNodes);
        return ApiResponse.success(result);
    }

    @DeleteMapping("/clear/global")
    @Operation(summary = "清除全局图谱", description = "只清除用户的全局图谱数据，保留对话级图谱")
    public ApiResponse<Map<String, Object>> clearGlobalGraph(@RequestParam Long userId) {
        int deletedNodes = knowledgeGraphService.deleteGlobalGraph(userId);
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("deletedNodes", deletedNodes);
        result.put("scope", "GLOBAL");
        return ApiResponse.success(result);
    }

    @DeleteMapping("/clear/conversation")
    @Operation(summary = "清除所有对话级图谱", description = "清除用户的所有对话级图谱数据，保留全局图谱")
    public ApiResponse<Map<String, Object>> clearAllConversationGraphs(@RequestParam Long userId) {
        int deletedNodes = knowledgeGraphService.deleteAllConversationGraphs(userId);
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("deletedNodes", deletedNodes);
        result.put("scope", "CONVERSATION");
        return ApiResponse.success(result);
    }

    @DeleteMapping("/clear/conversation/{sessionId}")
    @Operation(summary = "清除单个对话图谱", description = "清除指定对话的图谱数据，保留全局图谱和其他对话图谱")
    public ApiResponse<Map<String, Object>> clearConversationGraph(
            @RequestParam Long userId,
            @PathVariable String sessionId) {
        int deletedNodes = knowledgeGraphService.deleteConversationGraph(userId, sessionId);
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("deletedNodes", deletedNodes);
        result.put("sessionId", sessionId);
        return ApiResponse.success(result);
    }

    // ==================== 节点丰富化 ====================

    @PostMapping("/nodes/{id}/enrich")
    @Operation(summary = "手动触发节点丰富化", description = "异步为节点获取头像、图片和详细描述")
    public ApiResponse<Map<String, Object>> enrichNode(@PathVariable Long id) {
        KnowledgeNodeDTO node = knowledgeGraphService.getNodeById(id);
        if (node == null) {
            return ApiResponse.error("节点不存在");
        }

        // 检查节点是否正在丰富化中
        if (nodeEnrichmentService.isNodeEnriching(id)) {
            return ApiResponse.error("节点正在丰富化中，请稍后再试");
        }

        try {
            nodeEnrichmentService.enrichNodeAsync(id);
            log.info("手动触发节点丰富化: nodeId={}, name={}", id, node.getName());

            Map<String, Object> result = new java.util.LinkedHashMap<>();
            result.put("nodeId", id);
            result.put("nodeName", node.getName());
            result.put("message", "丰富化任务已提交，请稍后查看结果");
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("触发节点丰富化失败: nodeId={}, error={}", id, e.getMessage());
            return ApiResponse.error("触发丰富化失败: " + e.getMessage());
        }
    }

    @PostMapping("/nodes/enrich-all")
    @Operation(summary = "批量丰富化节点", description = "为用户所有缺少丰富信息的节点触发丰富化")
    public ApiResponse<Map<String, Object>> enrichAllNodes(@RequestParam Long userId) {
        List<KnowledgeNodeDTO> nodes = knowledgeGraphService.getAllNodes(userId);
        int triggered = 0;
        int skipped = 0;

        for (KnowledgeNodeDTO node : nodes) {
            // 检查是否需要丰富化
            boolean needsEnrichment = (node.getAvatar() == null || node.getAvatar().isEmpty()) ||
                    (node.getImage() == null || node.getImage().isEmpty()) ||
                    (node.getDetailedDescription() == null || node.getDetailedDescription().isEmpty());

            if (needsEnrichment) {
                try {
                    nodeEnrichmentService.enrichNodeAsync(node.getId());
                    triggered++;
                } catch (Exception e) {
                    log.warn("触发节点丰富化失败: nodeId={}, error={}", node.getId(), e.getMessage());
                }
            } else {
                skipped++;
            }
        }

        log.info("批量丰富化完成: userId={}, triggered={}, skipped={}", userId, triggered, skipped);

        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("totalNodes", nodes.size());
        result.put("triggered", triggered);
        result.put("skipped", skipped);
        result.put("message", String.format("已触发 %d 个节点的丰富化，跳过 %d 个已丰富的节点", triggered, skipped));
        return ApiResponse.success(result);
    }
}
