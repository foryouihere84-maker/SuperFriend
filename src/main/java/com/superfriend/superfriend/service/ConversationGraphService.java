package com.superfriend.superfriend.service;

import com.superfriend.superfriend.dto.GraphContextDTO;
import com.superfriend.superfriend.dto.KnowledgeNodeDTO;
import com.superfriend.superfriend.entity.KnowledgeNode;
import com.superfriend.superfriend.entity.KnowledgeRelation;
import com.superfriend.superfriend.mapper.KnowledgeNodeMapper;
import com.superfriend.superfriend.mapper.KnowledgeRelationMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 对话级图谱服务
 * 管理单次对话的知识图谱，支持对话结束后提炼到全局图谱
 */
@Slf4j
@Service
public class ConversationGraphService {

    @Autowired
    private KnowledgeNodeMapper nodeMapper;

    @Autowired
    private KnowledgeRelationMapper relationMapper;

    @Autowired
    @Lazy
    private GraphRefinementService refinementService;

    @Autowired
    @Lazy
    private KnowledgeGraphService knowledgeGraphService;

    // ==================== 对话图谱管理 ====================

    /**
     * 创建对话级图谱节点
     * 规则：对话图谱中相同名称的节点只能存在一个，已存在则更新
     */
    public KnowledgeNodeDTO addConversationNode(Long userId, String sessionId,
                                                 String nodeType, String name,
                                                 String description, String keywords,
                                                 Integer importance, String properties,
                                                 BigDecimal confidence) {
        // 唯一性检查：对话图谱中相同名称的节点只能存在一个
        KnowledgeNode existing = nodeMapper.findByUserIdAndScopeAndName(
                userId, KnowledgeNode.SCOPE_CONVERSATION, name);

        if (existing != null) {
            // 已存在同名节点，更新
            if (description != null && !description.isEmpty()) {
                if (existing.getDescription() == null || existing.getDescription().isEmpty() ||
                    description.length() > existing.getDescription().length()) {
                    existing.setDescription(description);
                }
            }
            if (keywords != null && !keywords.isEmpty()) {
                existing.setKeywords(mergeKeywords(existing.getKeywords(), keywords));
            }
            if (importance != null && (existing.getImportance() == null || importance > existing.getImportance())) {
                existing.setImportance(importance);
            }
            if (properties != null && !properties.isEmpty()) {
                existing.setProperties(properties);
            }
            if (confidence != null && (existing.getConfidence() == null || confidence.compareTo(existing.getConfidence()) > 0)) {
                existing.setConfidence(confidence);
            }
            nodeMapper.update(existing);
            log.debug("Updated existing conversation node: sessionId={}, name={}, type={}", sessionId, name, nodeType);
            return toDTO(existing);
        }

        // 不存在，创建新节点
        KnowledgeNode node = new KnowledgeNode();
        node.setUserId(userId);
        node.setNodeType(nodeType);
        node.setScope(KnowledgeNode.SCOPE_CONVERSATION);
        node.setName(name);
        node.setDescription(description);
        node.setKeywords(keywords);
        node.setImportance(importance != null ? importance : 5);
        node.setProperties(properties);
        node.setSourceSessionId(sessionId);
        node.setConfidence(confidence != null ? confidence : BigDecimal.valueOf(0.8));
        node.setAccessCount(0);

        nodeMapper.insert(node);
        log.debug("Added conversation node: sessionId={}, name={}, type={}", sessionId, name, nodeType);

        return toDTO(node);
    }

    /**
     * 合并关键词（去重）
     */
    private String mergeKeywords(String existing, String newKeywords) {
        if (existing == null || existing.isEmpty()) return newKeywords;
        if (newKeywords == null || newKeywords.isEmpty()) return existing;

        java.util.Set<String> allKeywords = new java.util.LinkedHashSet<>();
        for (String kw : existing.split(",")) {
            String trimmed = kw.trim();
            if (!trimmed.isEmpty()) allKeywords.add(trimmed);
        }
        for (String kw : newKeywords.split(",")) {
            String trimmed = kw.trim();
            if (!trimmed.isEmpty()) allKeywords.add(trimmed);
        }
        return String.join(",", allKeywords);
    }

    /**
     * 获取对话级图谱
     */
    public GraphContextDTO getConversationGraph(Long userId, String sessionId) {
        List<KnowledgeNode> nodes = nodeMapper.findConversationNodes(userId, sessionId);

        GraphContextDTO result = new GraphContextDTO();

        List<GraphContextDTO.NodeDTO> nodeDTOs = nodes.stream()
                .map(this::toNodeDTO)
                .collect(Collectors.toList());
        result.setNodes(nodeDTOs);

        // 获取关系
        List<KnowledgeRelation> relations = relationMapper.findBySessionId(userId, sessionId);
        List<GraphContextDTO.RelationDTO> relationDTOs = relations.stream()
                .map(this::toRelationDTO)
                .collect(Collectors.toList());
        result.setRelations(relationDTOs);

        // 统计
        GraphContextDTO.GraphStats stats = new GraphContextDTO.GraphStats();
        stats.setTotalNodes(nodes.size());
        stats.setTotalRelations(relations.size());
        result.setStats(stats);

        return result;
    }

    /**
     * 添加对话级关系
     */
    @Transactional
    public KnowledgeRelation addConversationRelation(Long userId, String sessionId,
                                                      Long sourceNodeId, Long targetNodeId,
                                                      String relationType, BigDecimal weight) {
        // 检查是否已存在
        KnowledgeRelation existing = relationMapper.findExistingRelation(sourceNodeId, targetNodeId, relationType);
        if (existing != null) {
            return existing;
        }

        KnowledgeRelation relation = new KnowledgeRelation();
        relation.setUserId(userId);
        relation.setSourceNodeId(sourceNodeId);
        relation.setTargetNodeId(targetNodeId);
        relation.setRelationType(relationType);
        relation.setWeight(weight != null ? weight : BigDecimal.ONE);
        relation.setSourceSessionId(sessionId);

        relationMapper.insert(relation);
        log.debug("Added conversation relation: sessionId={}, {} -> {} ({})",
                sessionId, sourceNodeId, targetNodeId, relationType);

        return relation;
    }

    // ==================== 对话结束处理 ====================

    /**
     * 结束对话，触发图谱提炼
     * 对话结束后调用此方法，将对话级图谱提炼到全局图谱
     *
     * 注意：全局图谱节点生成已禁用
     */
    public void finalizeConversationGraph(Long userId, String sessionId) {
        finalizeConversationGraph(userId, sessionId, false);
    }

    /**
     * 结束对话，触发图谱提炼
     *
     * @param userId 用户ID
     * @param sessionId 会话ID
     * @param cleanupAfterRefine 提炼后是否清理对话级图谱
     *
     * 注意：全局图谱节点生成已禁用
     */
    public void finalizeConversationGraph(Long userId, String sessionId, boolean cleanupAfterRefine) {
        log.info("Finalizing conversation graph (全局图谱提炼已禁用): userId={}, sessionId={}, cleanup={}",
            userId, sessionId, cleanupAfterRefine);

        // 全局图谱提炼已禁用
        // 异步提炼到全局图谱
        // refinementService.refineConversationGraphAsync(userId, sessionId, cleanupAfterRefine);
    }

    /**
     * 同步结束对话（等待提炼完成）
     *
     * 注意：全局图谱节点生成已禁用
     */
    @Transactional
    public void finalizeConversationGraphSync(Long userId, String sessionId) {
        finalizeConversationGraphSync(userId, sessionId, false);
    }

    /**
     * 同步结束对话（等待提炼完成）
     *
     * @param userId 用户ID
     * @param sessionId 会话ID
     * @param cleanupAfterRefine 提炼后是否清理对话级图谱
     *
     * 注意：全局图谱节点生成已禁用
     */
    @Transactional
    public void finalizeConversationGraphSync(Long userId, String sessionId, boolean cleanupAfterRefine) {
        log.info("Finalizing conversation graph sync (全局图谱提炼已禁用): userId={}, sessionId={}, cleanup={}",
            userId, sessionId, cleanupAfterRefine);

        // 全局图谱提炼已禁用
        // 同步提炼到全局图谱
        // refinementService.refineConversationGraph(userId, sessionId, cleanupAfterRefine);
    }

    // ==================== 清理方法 ====================

    /**
     * 清理对话级图谱（提炼后可选调用）
     */
    @Transactional
    public void cleanupConversationGraph(Long userId, String sessionId) {
        // 删除对话级关系
        List<KnowledgeRelation> relations = relationMapper.findBySessionId(userId, sessionId);
        for (KnowledgeRelation rel : relations) {
            relationMapper.deleteById(rel.getId());
        }

        // 删除对话级节点
        int nodeRows = nodeMapper.deleteConversationNodes(userId, sessionId);

        log.info("Cleaned up conversation graph: sessionId={}, relations={}, nodes={}",
                sessionId, relations.size(), nodeRows);
    }

    // ==================== 辅助方法 ====================

    private KnowledgeNodeDTO toDTO(KnowledgeNode entity) {
        KnowledgeNodeDTO dto = new KnowledgeNodeDTO();
        dto.setId(entity.getId());
        dto.setUserId(entity.getUserId());
        dto.setNodeType(entity.getNodeType());
        dto.setName(entity.getName());
        dto.setDescription(entity.getDescription());
        dto.setKeywords(entity.getKeywords());
        dto.setImportance(entity.getImportance());
        dto.setProperties(entity.getProperties());
        dto.setSourceSessionId(entity.getSourceSessionId());
        dto.setConfidence(entity.getConfidence());
        dto.setAccessCount(entity.getAccessCount());
        dto.setLastAccessedTime(entity.getLastAccessedTime());
        dto.setCreatedTime(entity.getCreatedTime());
        dto.setUpdatedTime(entity.getUpdatedTime());
        return dto;
    }

    private GraphContextDTO.NodeDTO toNodeDTO(KnowledgeNode entity) {
        GraphContextDTO.NodeDTO dto = new GraphContextDTO.NodeDTO();
        dto.setId(entity.getId());
        dto.setType(entity.getNodeType());
        dto.setName(entity.getName());
        dto.setDescription(entity.getDescription());
        dto.setKeywords(entity.getKeywords());
        dto.setImportance(entity.getImportance());
        dto.setProperties(entity.getProperties());
        dto.setConfidence(entity.getConfidence());
        dto.setAccessCount(entity.getAccessCount());
        return dto;
    }

    private GraphContextDTO.RelationDTO toRelationDTO(KnowledgeRelation entity) {
        GraphContextDTO.RelationDTO dto = new GraphContextDTO.RelationDTO();
        dto.setId(entity.getId());
        dto.setSourceId(entity.getSourceNodeId());
        dto.setTargetId(entity.getTargetNodeId());
        dto.setType(entity.getRelationType());
        dto.setWeight(entity.getWeight());
        return dto;
    }
}
