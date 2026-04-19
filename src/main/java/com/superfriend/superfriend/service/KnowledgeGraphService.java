package com.superfriend.superfriend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 统一的知识图谱服务
 * 合并了原 KnowledgeGraphService 和 MemoryService 的功能
 * 管理用户的知识节点和关系，提供图谱查询、上下文生成和记忆管理
 */
@Slf4j
@Service
public class KnowledgeGraphService {

    @Autowired
    private KnowledgeNodeMapper nodeMapper;

    @Autowired
    private KnowledgeRelationMapper relationMapper;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    @Lazy
    private NodeEnrichmentService nodeEnrichmentService;

    @Autowired
    private TencentCosService tencentCosService;

    private static final int MAX_CONTEXT_NODES = 20;
    private static final int MAX_CONTEXT_RELATIONS = 30;
    private static final int MAX_SEARCH_RESULTS = 10;
    private static final int MAX_CONTEXT_LENGTH = 1500;
    private static final double SIMILARITY_THRESHOLD = 0.7;

    // COS URL 匹配正则，用于提取 objectKey
    private static final Pattern COS_URL_PATTERN = Pattern.compile(
        "https?://[^/]+\\.cos\\.[^/]+\\.myqcloud\\.com/(.+?)(?:\\?|$)"
    );

    // ==================== 节点管理 ====================

    public List<KnowledgeNodeDTO> getAllNodes(Long userId) {
        List<KnowledgeNode> nodes = nodeMapper.findByUserId(userId);
        return nodes.stream().map(this::toDTO).collect(Collectors.toList());
    }

    public List<KnowledgeNodeDTO> getNodesByType(Long userId, String nodeType) {
        List<KnowledgeNode> nodes = nodeMapper.findByUserIdAndType(userId, nodeType);
        return nodes.stream().map(this::toDTO).collect(Collectors.toList());
    }

    public List<KnowledgeNodeDTO> getNodesBySession(Long userId, String sessionId) {
        List<KnowledgeNode> nodes = nodeMapper.findBySessionId(userId, sessionId);
        return nodes.stream().map(this::toDTO).collect(Collectors.toList());
    }

    public KnowledgeNodeDTO getNodeById(Long id) {
        KnowledgeNode node = nodeMapper.findById(id);
        return node != null ? toDTO(node) : null;
    }

    public KnowledgeNodeDTO addNode(KnowledgeNodeDTO dto) {
        KnowledgeNode node = toEntity(dto);
        node.setConfidence(dto.getConfidence() != null ? dto.getConfidence() : BigDecimal.ONE);
        node.setImportance(dto.getImportance() != null ? dto.getImportance() : 5);
        node.setAccessCount(0);
        nodeMapper.insert(node);
        log.info("Knowledge node added: id={}, type={}, name={}, userId={}",
            node.getId(), node.getNodeType(), node.getName(), node.getUserId());
        return toDTO(node);
    }

    /**
     * 直接添加节点实体（供 KnowledgeExtractorService 使用）
     * 不触发丰富化，由调用方统一处理
     */
    public KnowledgeNode addNodeDirectly(KnowledgeNode node) {
        if (node.getConfidence() == null) {
            node.setConfidence(BigDecimal.ONE);
        }
        if (node.getImportance() == null) {
            node.setImportance(5);
        }
        node.setAccessCount(0);
        nodeMapper.insert(node);
        log.info("Knowledge node added directly: id={}, type={}, name={}",
            node.getId(), node.getNodeType(), node.getName());
        return node;
    }

    /**
     * 根据节点名称查找节点ID
     */
    public Long findNodeIdByName(Long userId, String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        // 先在全局图谱中查找
        KnowledgeNode globalNode = nodeMapper.findByUserIdAndScopeAndName(userId, KnowledgeNode.SCOPE_GLOBAL, name);
        if (globalNode != null) {
            return globalNode.getId();
        }
        // 再在对话级图谱中查找
        KnowledgeNode convNode = nodeMapper.findByUserIdAndScopeAndName(userId, KnowledgeNode.SCOPE_CONVERSATION, name);
        if (convNode != null) {
            return convNode.getId();
        }
        return null;
    }

    public KnowledgeNodeDTO updateNode(Long id, KnowledgeNodeDTO dto) {
        KnowledgeNode existing = nodeMapper.findById(id);
        if (existing == null) {
            return null;
        }
        if (dto.getNodeType() != null) existing.setNodeType(dto.getNodeType());
        if (dto.getName() != null) existing.setName(dto.getName());
        if (dto.getDescription() != null) existing.setDescription(dto.getDescription());
        if (dto.getAvatar() != null) existing.setAvatar(dto.getAvatar());
        if (dto.getImage() != null) existing.setImage(dto.getImage());
        if (dto.getDetailedDescription() != null) existing.setDetailedDescription(dto.getDetailedDescription());
        if (dto.getKeywords() != null) existing.setKeywords(dto.getKeywords());
        if (dto.getImportance() != null) existing.setImportance(dto.getImportance());
        if (dto.getProperties() != null) existing.setProperties(dto.getProperties());
        if (dto.getConfidence() != null) existing.setConfidence(dto.getConfidence());
        nodeMapper.update(existing);
        log.info("Knowledge node updated: id={}", id);
        return toDTO(existing);
    }

    public boolean deleteNode(Long id) {
        // 先获取节点信息，用于删除 COS 资源
        KnowledgeNode node = nodeMapper.findById(id);
        if (node != null) {
            deleteCosResources(node);
        }
        relationMapper.deleteByNodeId(id);
        int rows = nodeMapper.deleteById(id);
        log.info("Knowledge node deleted: id={}, rows={}", id, rows);
        return rows > 0;
    }

    /**
     * 删除节点关联的 COS 资源（头像和图片）
     */
    private void deleteCosResources(KnowledgeNode node) {
        if (!tencentCosService.isEnabled() || node == null) {
            return;
        }

        // 删除头像
        if (node.getAvatar() != null && !node.getAvatar().isEmpty()) {
            deleteCosObject(node.getAvatar(), "avatar");
        }

        // 删除图片（可能是多个，逗号分隔）
        if (node.getImage() != null && !node.getImage().isEmpty()) {
            String[] imageUrls = node.getImage().split(",");
            for (String imageUrl : imageUrls) {
                deleteCosObject(imageUrl.trim(), "image");
            }
        }
    }

    /**
     * 删除 COS 上的单个对象
     */
    private void deleteCosObject(String url, String resourceType) {
        if (url == null || url.isEmpty()) {
            return;
        }

        String objectKey = extractCosObjectKey(url);
        if (objectKey != null && !objectKey.isEmpty()) {
            try {
                tencentCosService.deleteFile(objectKey);
                log.info("COS 资源删除成功: type={}, key={}", resourceType, objectKey);
            } catch (Exception e) {
                log.warn("COS 资源删除失败: type={}, key={}, error={}", resourceType, objectKey, e.getMessage());
            }
        }
    }

    /**
     * 从 COS URL 中提取 objectKey
     * 支持格式：
     * - https://bucket.cos.region.myqcloud.com/path/to/file?sign=xxx
     * - https://bucket.cos.region.myqcloud.com/path/to/file
     */
    private String extractCosObjectKey(String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }

        Matcher matcher = COS_URL_PATTERN.matcher(url);
        if (matcher.find()) {
            return matcher.group(1);
        }

        return null;
    }

    /**
     * 批量删除节点的 COS 资源
     */
    private void deleteCosResourcesForNodes(List<KnowledgeNode> nodes) {
        if (!tencentCosService.isEnabled() || nodes == null || nodes.isEmpty()) {
            return;
        }

        int deletedCount = 0;
        for (KnowledgeNode node : nodes) {
            if (node.getAvatar() != null && !node.getAvatar().isEmpty()) {
                deleteCosObject(node.getAvatar(), "avatar");
                deletedCount++;
            }
            if (node.getImage() != null && !node.getImage().isEmpty()) {
                String[] imageUrls = node.getImage().split(",");
                for (String imageUrl : imageUrls) {
                    deleteCosObject(imageUrl.trim(), "image");
                    deletedCount++;
                }
            }
        }
        log.info("批量删除 COS 资源完成: count={}", deletedCount);
    }

    /**
     * 清除用户的所有知识图谱数据（节点和关系）
     * 注意：这会删除全局和对话级所有数据，包括 COS 上的资源
     */
    @Transactional
    public int deleteAllByUserId(Long userId) {
        // 先获取所有节点，用于删除 COS 资源
        List<KnowledgeNode> allNodes = nodeMapper.findByUserId(userId);

        // 删除 COS 资源
        deleteCosResourcesForNodes(allNodes);

        int relationRows = relationMapper.deleteByUserId(userId);
        int nodeRows = nodeMapper.deleteByUserId(userId);
        log.info("Knowledge graph cleared (ALL): userId={}, relations={}, nodes={}, cosResources={}",
            userId, relationRows, nodeRows, allNodes.size());
        return nodeRows;
    }

    /**
     * 清除用户的全局图谱数据
     * 只删除 scope=GLOBAL 的节点和 source_session_id 为空的关系，包括 COS 资源
     */
    @Transactional
    public int deleteGlobalGraph(Long userId) {
        // 先获取全局图谱节点，用于删除 COS 资源
        List<KnowledgeNode> globalNodes = nodeMapper.findByUserIdAndScope(userId, KnowledgeNode.SCOPE_GLOBAL);

        // 删除 COS 资源
        deleteCosResourcesForNodes(globalNodes);

        int relationRows = relationMapper.deleteAllGlobalRelations(userId);
        int nodeRows = nodeMapper.deleteAllGlobalNodes(userId);
        log.info("Global graph cleared: userId={}, relations={}, nodes={}, cosResources={}",
            userId, relationRows, nodeRows, globalNodes.size());
        return nodeRows;
    }

    /**
     * 清除用户的所有对话级图谱数据
     * 只删除 scope=CONVERSATION 的节点和 source_session_id 不为空的关系，包括 COS 资源
     */
    @Transactional
    public int deleteAllConversationGraphs(Long userId) {
        // 先获取所有对话级图谱节点，用于删除 COS 资源
        List<KnowledgeNode> convNodes = nodeMapper.findByUserIdAndScope(userId, KnowledgeNode.SCOPE_CONVERSATION);

        // 删除 COS 资源
        deleteCosResourcesForNodes(convNodes);

        int relationRows = relationMapper.deleteAllConversationRelations(userId);
        int nodeRows = nodeMapper.deleteAllConversationNodes(userId);
        log.info("All conversation graphs cleared: userId={}, relations={}, nodes={}, cosResources={}",
            userId, relationRows, nodeRows, convNodes.size());
        return nodeRows;
    }

    /**
     * 清除单个对话的图谱数据
     * 只删除指定 sessionId 的节点和关系，保留全局图谱和其他对话图谱，包括 COS 资源
     */
    @Transactional
    public int deleteConversationGraph(Long userId, String sessionId) {
        // 先获取对话级图谱节点，用于删除 COS 资源
        List<KnowledgeNode> convNodes = nodeMapper.findConversationNodes(userId, sessionId);

        // 删除 COS 资源
        deleteCosResourcesForNodes(convNodes);

        int relationRows = relationMapper.deleteBySessionId(userId, sessionId);
        int nodeRows = nodeMapper.deleteConversationNodes(userId, sessionId);
        log.info("Conversation graph cleared: userId={}, sessionId={}, relations={}, nodes={}, cosResources={}",
            userId, sessionId, relationRows, nodeRows, convNodes.size());
        return nodeRows;
    }

    // ==================== 关系管理 ====================

    public List<GraphContextDTO.RelationDTO> getAllRelations(Long userId) {
        List<KnowledgeRelation> relations = relationMapper.findByUserIdWithNodes(userId);
        return relations.stream().map(this::toRelationDTO).collect(Collectors.toList());
    }

    @Transactional
    public KnowledgeRelation addRelation(Long userId, Long sourceNodeId, Long targetNodeId,
                                         String relationType, String properties, BigDecimal weight) {
        // 检查是否已存在
        KnowledgeRelation existing = relationMapper.findExistingRelation(sourceNodeId, targetNodeId, relationType);
        if (existing != null) {
            // 更新权重
            if (weight != null) {
                existing.setWeight(weight);
                relationMapper.update(existing);
            }
            return existing;
        }

        KnowledgeRelation relation = new KnowledgeRelation();
        relation.setUserId(userId);
        relation.setSourceNodeId(sourceNodeId);
        relation.setTargetNodeId(targetNodeId);
        relation.setRelationType(relationType);
        relation.setProperties(properties);
        relation.setWeight(weight != null ? weight : BigDecimal.ONE);
        relationMapper.insert(relation);
        log.info("Knowledge relation added: {} -> {} ({})", sourceNodeId, targetNodeId, relationType);
        return relation;
    }

    public boolean deleteRelation(Long id) {
        int rows = relationMapper.deleteById(id);
        return rows > 0;
    }

    public boolean updateRelation(Long id, Long sourceNodeId, Long targetNodeId,
                                  String relationType, BigDecimal weight) {
        KnowledgeRelation existing = relationMapper.findById(id);
        if (existing == null) {
            return false;
        }
        if (sourceNodeId != null) existing.setSourceNodeId(sourceNodeId);
        if (targetNodeId != null) existing.setTargetNodeId(targetNodeId);
        if (relationType != null) existing.setRelationType(relationType);
        if (weight != null) existing.setWeight(weight);
        relationMapper.update(existing);
        log.info("Knowledge relation updated: id={}", id);
        return true;
    }

    // ==================== 图谱查询 ====================

    /**
     * 搜索相关节点（支持按名称、描述、关键词搜索）
     */
    public List<KnowledgeNodeDTO> searchNodes(Long userId, String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return getAllNodes(userId);
        }
        List<KnowledgeNode> nodes = nodeMapper.searchByKeyword(userId, keyword.trim(), MAX_SEARCH_RESULTS);
        // 增加访问计数
        for (KnowledgeNode node : nodes) {
            nodeMapper.incrementAccessCount(node.getId());
        }
        return nodes.stream().map(this::toDTO).collect(Collectors.toList());
    }

    /**
     * 按指定 scope 搜索节点
     * 全局图谱页面只搜索 scope=GLOBAL 的节点
     */
    public List<KnowledgeNodeDTO> searchNodesByScope(Long userId, String scope, String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            List<KnowledgeNode> nodes = nodeMapper.findByUserIdAndScope(userId, scope);
            return nodes.stream().map(this::toDTO).collect(Collectors.toList());
        }
        // 获取指定 scope 的所有节点，然后过滤匹配关键词的
        List<KnowledgeNode> allNodes = nodeMapper.findByUserIdAndScope(userId, scope);
        String lowerKeyword = keyword.trim().toLowerCase();
        List<KnowledgeNode> matchedNodes = allNodes.stream()
            .filter(node -> {
                if (node.getName() != null && node.getName().toLowerCase().contains(lowerKeyword)) return true;
                if (node.getDescription() != null && node.getDescription().toLowerCase().contains(lowerKeyword)) return true;
                if (node.getKeywords() != null && node.getKeywords().toLowerCase().contains(lowerKeyword)) return true;
                return false;
            })
            .limit(MAX_SEARCH_RESULTS)
            .collect(Collectors.toList());
        // 增加访问计数
        for (KnowledgeNode node : matchedNodes) {
            nodeMapper.incrementAccessCount(node.getId());
        }
        return matchedNodes.stream().map(this::toDTO).collect(Collectors.toList());
    }

    /**
     * 搜索对话级图谱节点
     * 对话图谱页面只搜索当前对话的节点
     */
    public List<KnowledgeNodeDTO> searchConversationNodes(Long userId, String sessionId, String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            List<KnowledgeNode> nodes = nodeMapper.findConversationNodes(userId, sessionId);
            return nodes.stream().map(this::toDTO).collect(Collectors.toList());
        }
        // 获取对话级图谱的所有节点
        List<KnowledgeNode> allNodes = nodeMapper.findConversationNodes(userId, sessionId);
        String lowerKeyword = keyword.trim().toLowerCase();
        List<KnowledgeNode> matchedNodes = allNodes.stream()
            .filter(node -> {
                if (node.getName() != null && node.getName().toLowerCase().contains(lowerKeyword)) return true;
                if (node.getDescription() != null && node.getDescription().toLowerCase().contains(lowerKeyword)) return true;
                if (node.getKeywords() != null && node.getKeywords().toLowerCase().contains(lowerKeyword)) return true;
                return false;
            })
            .limit(MAX_SEARCH_RESULTS)
            .collect(Collectors.toList());
        // 增加访问计数
        for (KnowledgeNode node : matchedNodes) {
            nodeMapper.incrementAccessCount(node.getId());
        }
        return matchedNodes.stream().map(this::toDTO).collect(Collectors.toList());
    }

    /**
     * 获取完整的图谱数据（用于前端可视化）
     * 全局图谱页面只显示 scope=GLOBAL 的节点和 source_session_id IS NULL 的关系
     */
    public GraphContextDTO getFullGraph(Long userId) {
        // 只查询全局图谱节点（scope = 'GLOBAL'）
        List<KnowledgeNode> nodes = nodeMapper.findByUserIdAndScope(userId, KnowledgeNode.SCOPE_GLOBAL);
        // 只查询全局关系（source_session_id IS NULL）
        List<KnowledgeRelation> relations = relationMapper.findGlobalRelationsByUserId(userId);

        GraphContextDTO result = new GraphContextDTO();

        // 转换节点
        List<GraphContextDTO.NodeDTO> nodeDTOs = nodes.stream()
            .map(this::toNodeDTOForGraph)
            .collect(Collectors.toList());
        result.setNodes(nodeDTOs);

        // 转换关系
        List<GraphContextDTO.RelationDTO> relationDTOs = relations.stream()
            .map(this::toRelationDTO)
            .collect(Collectors.toList());
        result.setRelations(relationDTOs);

        // 统计信息
        GraphContextDTO.GraphStats stats = new GraphContextDTO.GraphStats();
        stats.setTotalNodes(nodes.size());
        stats.setTotalRelations(relations.size());
        stats.setNodeTypeCount((int) nodes.stream().map(KnowledgeNode::getNodeType).distinct().count());
        stats.setRelationTypeCount((int) relations.stream().map(KnowledgeRelation::getRelationType).distinct().count());
        result.setStats(stats);

        return result;
    }

    /**
     * 获取单个对话的知识图谱（对话级图谱）
     */
    public GraphContextDTO getConversationGraph(Long userId, String sessionId) {
        // 获取对话级节点
        List<KnowledgeNode> nodes = nodeMapper.findConversationNodes(userId, sessionId);

        GraphContextDTO result = new GraphContextDTO();

        // 转换节点
        List<GraphContextDTO.NodeDTO> nodeDTOs = nodes.stream()
            .map(this::toNodeDTOForGraph)
            .collect(Collectors.toList());
        result.setNodes(nodeDTOs);

        // 获取关系
        List<KnowledgeRelation> relations = relationMapper.findBySessionId(userId, sessionId);
        List<GraphContextDTO.RelationDTO> relationDTOs = relations.stream()
            .map(this::toRelationDTO)
            .collect(Collectors.toList());
        result.setRelations(relationDTOs);

        // 统计信息
        GraphContextDTO.GraphStats stats = new GraphContextDTO.GraphStats();
        stats.setTotalNodes(nodes.size());
        stats.setTotalRelations(relations.size());
        stats.setNodeTypeCount((int) nodes.stream().map(KnowledgeNode::getNodeType).distinct().count());
        stats.setRelationTypeCount((int) relations.stream().map(KnowledgeRelation::getRelationType).distinct().count());
        result.setStats(stats);

        return result;
    }

    /**
     * 查询与用户查询相关的图谱上下文
     */
    public GraphContextDTO queryRelevantGraph(Long userId, String query) {
        // 1. 提取关键词
        List<String> keywords = extractKeywords(query);

        // 2. 搜索相关节点
        Set<Long> relevantNodeIds = new HashSet<>();
        List<KnowledgeNode> relevantNodes = new ArrayList<>();

        for (String keyword : keywords) {
            if (relevantNodes.size() >= MAX_CONTEXT_NODES) break;
            List<KnowledgeNode> found = nodeMapper.searchByKeyword(userId, keyword, 5);
            for (KnowledgeNode node : found) {
                if (!relevantNodeIds.contains(node.getId())) {
                    relevantNodeIds.add(node.getId());
                    relevantNodes.add(node);
                    nodeMapper.incrementAccessCount(node.getId());
                }
            }
        }

        // 3. 补充高重要性和高置信度节点
        if (relevantNodes.size() < MAX_CONTEXT_NODES / 2) {
            List<KnowledgeNode> topNodes = nodeMapper.findTopByConfidence(userId, MAX_CONTEXT_NODES - relevantNodes.size());
            for (KnowledgeNode node : topNodes) {
                if (!relevantNodeIds.contains(node.getId())) {
                    relevantNodeIds.add(node.getId());
                    relevantNodes.add(node);
                }
            }
        }

        // 4. 查询相关关系
        List<KnowledgeRelation> relevantRelations = new ArrayList<>();
        if (!relevantNodeIds.isEmpty()) {
            String nodeIdsStr = relevantNodeIds.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
            relevantRelations = relationMapper.findByNodeIds(userId, nodeIdsStr);
        }

        // 5. 按重要性排序
        relevantNodes.sort((a, b) -> {
            int impA = a.getImportance() != null ? a.getImportance() : 5;
            int impB = b.getImportance() != null ? b.getImportance() : 5;
            if (impA != impB) return Integer.compare(impB, impA);
            return Integer.compare(
                b.getAccessCount() != null ? b.getAccessCount() : 0,
                a.getAccessCount() != null ? a.getAccessCount() : 0);
        });

        // 6. 截取
        if (relevantNodes.size() > MAX_CONTEXT_NODES) {
            relevantNodes = relevantNodes.subList(0, MAX_CONTEXT_NODES);
        }

        // 7. 构建结果
        GraphContextDTO result = new GraphContextDTO();
        result.setNodes(relevantNodes.stream().map(this::toNodeDTOForGraph).collect(Collectors.toList()));
        result.setRelations(relevantRelations.stream().map(this::toRelationDTO).collect(Collectors.toList()));
        result.setTextContext(buildTextContext(relevantNodes, relevantRelations));

        return result;
    }

    /**
     * 生成用于LLM的图谱上下文文本（查询所有图谱）
     * 注意：此方法会查询全局图谱，不建议在对话中使用
     */
    public String generateGraphContextForLLM(Long userId, String query) {
        GraphContextDTO graph = queryRelevantGraph(userId, query);
        return graph.getTextContext();
    }

    /**
     * 生成用于LLM的对话级图谱上下文文本
     * 只查询当前对话的知识图谱，不包含全局图谱
     *
     * @param userId 用户ID
     * @param sessionId 会话ID
     * @param query 用户查询（用于关键词匹配）
     * @return 对话级图谱上下文文本
     */
    public String generateConversationGraphContextForLLM(Long userId, String sessionId, String query) {
        if (userId == null || sessionId == null) {
            return "";
        }

        // 1. 获取当前对话的节点
        List<KnowledgeNode> conversationNodes = nodeMapper.findConversationNodes(userId, sessionId);

        if (conversationNodes.isEmpty()) {
            return "";
        }

        // 2. 如果有查询关键词，进行过滤
        List<KnowledgeNode> relevantNodes = new ArrayList<>();
        if (query != null && !query.trim().isEmpty()) {
            List<String> keywords = extractKeywords(query);
            Set<Long> relevantNodeIds = new HashSet<>();

            for (KnowledgeNode node : conversationNodes) {
                // 检查节点是否与关键词相关
                for (String keyword : keywords) {
                    if (isNodeMatchKeyword(node, keyword) && !relevantNodeIds.contains(node.getId())) {
                        relevantNodeIds.add(node.getId());
                        relevantNodes.add(node);
                    }
                }
            }

            // 如果没有匹配的节点，返回部分高重要性节点
            if (relevantNodes.isEmpty() && !conversationNodes.isEmpty()) {
                conversationNodes.sort((a, b) -> {
                    int impA = a.getImportance() != null ? a.getImportance() : 5;
                    int impB = b.getImportance() != null ? b.getImportance() : 5;
                    return Integer.compare(impB, impA);
                });
                int limit = Math.min(MAX_CONTEXT_NODES / 2, conversationNodes.size());
                relevantNodes = conversationNodes.subList(0, limit);
            }
        } else {
            // 没有查询，按重要性排序返回
            conversationNodes.sort((a, b) -> {
                int impA = a.getImportance() != null ? a.getImportance() : 5;
                int impB = b.getImportance() != null ? b.getImportance() : 5;
                return Integer.compare(impB, impA);
            });
            int limit = Math.min(MAX_CONTEXT_NODES, conversationNodes.size());
            relevantNodes = new ArrayList<>(conversationNodes.subList(0, limit));
        }

        // 3. 获取相关的关系
        List<KnowledgeRelation> relevantRelations = relationMapper.findBySessionId(userId, sessionId);

        // 4. 构建上下文文本
        return buildConversationContextText(relevantNodes, relevantRelations);
    }

    /**
     * 检查节点是否匹配关键词
     */
    private boolean isNodeMatchKeyword(KnowledgeNode node, String keyword) {
        if (keyword == null || keyword.isEmpty()) return false;
        String lowerKeyword = keyword.toLowerCase();

        if (node.getName() != null && node.getName().toLowerCase().contains(lowerKeyword)) {
            return true;
        }
        if (node.getDescription() != null && node.getDescription().toLowerCase().contains(lowerKeyword)) {
            return true;
        }
        if (node.getKeywords() != null && node.getKeywords().toLowerCase().contains(lowerKeyword)) {
            return true;
        }
        return false;
    }

    /**
     * 构建对话级图谱上下文文本
     */
    private String buildConversationContextText(List<KnowledgeNode> nodes, List<KnowledgeRelation> relations) {
        if (nodes.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("📊 当前对话知识：\n");
        sb.append("```\n");

        // 按类型分组
        Map<String, List<KnowledgeNode>> groupedNodes = nodes.stream()
            .collect(Collectors.groupingBy(KnowledgeNode::getNodeType));

        String[] typeOrder = {"TECHNOLOGY", "PROJECT", "PREFERENCE", "TASK", "ERROR", "SOLUTION", "CONCEPT", "USER"};
        String[] typeLabels = {"技术栈", "项目", "偏好", "任务", "错误", "解决方案", "概念", "用户信息"};

        for (int i = 0; i < typeOrder.length; i++) {
            String type = typeOrder[i];
            List<KnowledgeNode> typeNodes = groupedNodes.get(type);
            if (typeNodes != null && !typeNodes.isEmpty()) {
                sb.append("【").append(typeLabels[i]).append("】\n");
                for (KnowledgeNode node : typeNodes) {
                    sb.append("- ").append(node.getName());
                    if (node.getDescription() != null && !node.getDescription().isEmpty()) {
                        sb.append(" - ").append(node.getDescription());
                    }
                    sb.append("\n");
                }
                sb.append("\n");
            }
        }

        // 添加关键关系
        if (!relations.isEmpty()) {
            sb.append("【关键关系】\n");
            int relCount = 0;
            for (KnowledgeRelation rel : relations) {
                if (relCount++ >= 5) break;
                if (rel.getSourceNode() != null && rel.getTargetNode() != null) {
                    sb.append("- ").append(rel.getSourceNode().getName())
                      .append(" ").append(GraphContextDTO.getRelationTypeName(rel.getRelationType()))
                      .append(" ").append(rel.getTargetNode().getName())
                      .append("\n");
                }
            }
        }

        sb.append("```\n");

        String result = sb.toString();
        if (result.length() > MAX_CONTEXT_LENGTH) {
            result = result.substring(0, MAX_CONTEXT_LENGTH) + "\n...(更多知识已省略)\n```\n";
        }

        return result;
    }

    // ==================== 图谱更新 ====================

    /**
     * 合并并更新图谱
     * 支持对话级（CONVERSATION）和全局级（GLOBAL）图谱
     */
    @Transactional
    public void mergeAndUpdateGraph(Long userId, String sessionId,
                                    List<KnowledgeNode> newNodes,
                                    List<KnowledgeRelation> newRelations) {
        if (newNodes == null || newNodes.isEmpty()) {
            return;
        }

        log.info("Merging knowledge graph: userId={}, newNodes={}, newRelations={}",
            userId, newNodes.size(), newRelations != null ? newRelations.size() : 0);

        // 合并节点
        Map<String, KnowledgeNode> nodeMap = new HashMap<>();
        for (KnowledgeNode newNode : newNodes) {
            String key = newNode.getNodeType() + ":" + newNode.getName();

            // 确定节点 scope（默认为 CONVERSATION）
            String scope = newNode.getScope() != null ? newNode.getScope() : KnowledgeNode.SCOPE_CONVERSATION;

            // 检查是否已存在（按 user_id + scope + name 唯一性检查）
            // 规则：全局图谱和对话图谱中，相同名称的节点只能存在一个
            KnowledgeNode existing = nodeMapper.findByUserIdAndScopeAndName(userId, scope, newNode.getName());

            if (existing != null) {
                // 已存在同名节点，更新
                if (newNode.getConfidence() != null &&
                    (existing.getConfidence() == null ||
                     newNode.getConfidence().compareTo(existing.getConfidence()) > 0)) {
                    existing.setConfidence(newNode.getConfidence());
                }
                if (newNode.getImportance() != null &&
                    (existing.getImportance() == null ||
                     newNode.getImportance() > existing.getImportance())) {
                    existing.setImportance(newNode.getImportance());
                }
                if (newNode.getDescription() != null && !newNode.getDescription().isEmpty()) {
                    if (existing.getDescription() == null || existing.getDescription().isEmpty() ||
                        newNode.getDescription().length() > existing.getDescription().length()) {
                        existing.setDescription(newNode.getDescription());
                    }
                }
                if (newNode.getKeywords() != null && !newNode.getKeywords().isEmpty()) {
                    existing.setKeywords(mergeKeywords(existing.getKeywords(), newNode.getKeywords()));
                }
                nodeMapper.update(existing);
                nodeMap.put(key, existing);
                log.debug("Updated existing node: scope={}, name={}", scope, newNode.getName());

                // 检查已存在节点是否缺少丰富化信息，如果缺少则触发丰富化
                if (needsEnrichment(existing)) {
                    try {
                        nodeEnrichmentService.enrichNodeAsync(existing.getId());
                        log.debug("触发已存在节点的丰富化: nodeId={}, name={}", existing.getId(), existing.getName());
                    } catch (Exception e) {
                        log.warn("触发已存在节点丰富化失败: nodeId={}, error={}", existing.getId(), e.getMessage());
                    }
                }
            } else {
                // 不存在，创建新节点
                newNode.setUserId(userId);
                newNode.setSourceSessionId(sessionId);
                newNode.setScope(scope);  // 确保 scope 被设置
                if (newNode.getConfidence() == null) {
                    newNode.setConfidence(BigDecimal.ONE);
                }
                if (newNode.getImportance() == null) {
                    newNode.setImportance(5);
                }
                nodeMapper.insert(newNode);
                nodeMap.put(key, newNode);
                log.debug("Created new node with scope={}: {}", scope, newNode.getName());

                // 异步丰富化节点（获取头像、图片、详细描述）
                try {
                    nodeEnrichmentService.enrichNodeAsync(newNode.getId());
                } catch (Exception e) {
                    log.warn("触发节点丰富化失败: nodeId={}, error={}", newNode.getId(), e.getMessage());
                }
            }
        }

        // 合并关系
        // 先构建名称到已持久化节点的映射，用于将关系中的节点引用解析为数据库ID
        Map<String, KnowledgeNode> nameToNode = new HashMap<>();
        for (KnowledgeNode n : nodeMap.values()) {
            nameToNode.put(n.getName(), n);
        }

        if (newRelations != null) {
            for (KnowledgeRelation newRel : newRelations) {
                // 通过节点引用中的名称解析为数据库ID
                if (newRel.getSourceNodeId() == null && newRel.getSourceNode() != null) {
                    KnowledgeNode resolved = nameToNode.get(newRel.getSourceNode().getName());
                    if (resolved != null) {
                        newRel.setSourceNodeId(resolved.getId());
                    }
                }
                if (newRel.getTargetNodeId() == null && newRel.getTargetNode() != null) {
                    KnowledgeNode resolved = nameToNode.get(newRel.getTargetNode().getName());
                    if (resolved != null) {
                        newRel.setTargetNodeId(resolved.getId());
                    }
                }

                if (newRel.getSourceNodeId() == null || newRel.getTargetNodeId() == null) {
                    log.warn("跳过无法解析节点ID的关系：source={}, target={}, type={}",
                        newRel.getSourceNode() != null ? newRel.getSourceNode().getName() : "null",
                        newRel.getTargetNode() != null ? newRel.getTargetNode().getName() : "null",
                        newRel.getRelationType());
                    continue;
                }

                KnowledgeRelation existing = relationMapper.findExistingRelation(
                    newRel.getSourceNodeId(), newRel.getTargetNodeId(), newRel.getRelationType());

                if (existing != null) {
                    // 更新权重
                    if (newRel.getWeight() != null) {
                        BigDecimal newWeight = existing.getWeight().add(newRel.getWeight())
                            .divide(BigDecimal.valueOf(2), 2, BigDecimal.ROUND_HALF_UP);
                        existing.setWeight(newWeight);
                        relationMapper.update(existing);
                    }
                } else {
                    newRel.setUserId(userId);
                    newRel.setSourceSessionId(sessionId);
                    if (newRel.getWeight() == null) {
                        newRel.setWeight(BigDecimal.ONE);
                    }
                    relationMapper.insert(newRel);
                }
            }
        }

        log.info("Knowledge graph merged successfully for userId={}", userId);
    }

    // ==================== 统计信息 ====================

    public Map<String, Object> getStats(Long userId) {
        Map<String, Object> stats = new LinkedHashMap<>();
        int total = nodeMapper.countByUserId(userId);
        stats.put("total", total);

        List<Map<String, Object>> typeStats = nodeMapper.countByType(userId);
        Map<String, Integer> typeMap = new LinkedHashMap<>();
        for (Map<String, Object> row : typeStats) {
            String type = (String) row.get("node_type");
            Number cnt = (Number) row.get("cnt");
            typeMap.put(type, cnt != null ? cnt.intValue() : 0);
        }
        stats.put("types", typeMap);

        int relationCount = relationMapper.countByUserId(userId);
        stats.put("totalRelations", relationCount);

        return stats;
    }

    // ==================== 私有方法 ====================

    private List<String> extractKeywords(String query) {
        if (query == null || query.trim().isEmpty()) {
            return Collections.emptyList();
        }

        List<String> keywords = new ArrayList<>();
        String[] tokens = query.replaceAll("[\\uFF0C\\u3002\\uFF01\\uFF1F\\u3001\\uFF1B\\uFF1A\\u201C\\u201D\\u2018\\u2019\\uFF08\\uFF09\\u3010\\u3011\\u300A\\u300B\\s]+", " ")
                .trim().split("\\s+");

        Set<String> stopWords = new HashSet<>(Arrays.asList(
            "的", "了", "在", "是", "我", "有", "和", "就", "不", "人", "都", "一", "一个",
            "上", "也", "很", "到", "说", "要", "去", "你", "会", "着", "没有", "看", "好",
            "自己", "这", "他", "她", "它", "们", "那", "什么", "怎么", "如何", "请", "帮",
            "the", "a", "an", "is", "are", "was", "were", "be", "been", "being",
            "have", "has", "had", "do", "does", "did", "will", "would", "could",
            "should", "may", "might", "can", "shall", "to", "of", "in", "for",
            "on", "with", "at", "by", "from", "as", "into", "through", "and",
            "but", "or", "not", "no", "if", "then", "so", "than", "too", "very"
        ));

        for (String token : tokens) {
            if (token.length() >= 2 && !stopWords.contains(token.toLowerCase())) {
                keywords.add(token);
            }
        }

        return keywords.size() > 5 ? keywords.subList(0, 5) : keywords;
    }

    private String buildTextContext(List<KnowledgeNode> nodes, List<KnowledgeRelation> relations) {
        if (nodes.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("📊 用户知识图谱：\n");
        sb.append("```\n");

        // 按类型分组
        Map<String, List<KnowledgeNode>> groupedNodes = nodes.stream()
            .collect(Collectors.groupingBy(KnowledgeNode::getNodeType));

        String[] typeOrder = {"TECHNOLOGY", "PROJECT", "PREFERENCE", "TASK", "ERROR", "SOLUTION", "CONCEPT", "USER"};
        String[] typeLabels = {"技术栈", "项目", "偏好", "任务", "错误", "解决方案", "概念", "用户信息"};

        for (int i = 0; i < typeOrder.length; i++) {
            String type = typeOrder[i];
            List<KnowledgeNode> typeNodes = groupedNodes.get(type);
            if (typeNodes != null && !typeNodes.isEmpty()) {
                sb.append("【").append(typeLabels[i]).append("】\n");
                for (KnowledgeNode node : typeNodes) {
                    sb.append("- ").append(node.getName());

                    // properties 中的 level 信息
                    if (node.getProperties() != null && !node.getProperties().isEmpty()) {
                        try {
                            Map<String, Object> props = objectMapper.readValue(node.getProperties(),
                                new TypeReference<Map<String, Object>>() {});
                            if (props.containsKey("level")) {
                                sb.append(" (").append(props.get("level")).append(")");
                            }
                        } catch (Exception e) {
                            // ignore
                        }
                    }

                    // 详细描述
                    if (node.getDescription() != null && !node.getDescription().isEmpty()) {
                        sb.append(" - ").append(node.getDescription());
                    }

                    // 重要性标记
                    if (node.getImportance() != null && node.getImportance() >= 8) {
                        sb.append(" [重要]");
                    }

                    sb.append("\n");
                }
                sb.append("\n");
            }
        }

        // 添加关键关系
        if (!relations.isEmpty()) {
            sb.append("【关键关系】\n");
            int relCount = 0;
            for (KnowledgeRelation rel : relations) {
                if (relCount++ >= 5) break;
                if (rel.getSourceNode() != null && rel.getTargetNode() != null) {
                    sb.append("- ").append(rel.getSourceNode().getName())
                      .append(" ").append(GraphContextDTO.getRelationTypeName(rel.getRelationType()))
                      .append(" ").append(rel.getTargetNode().getName())
                      .append("\n");
                }
            }
        }

        sb.append("```\n");

        String result = sb.toString();
        if (result.length() > MAX_CONTEXT_LENGTH) {
            result = result.substring(0, MAX_CONTEXT_LENGTH) + "\n...(更多知识已省略)\n```\n";
        }

        return result;
    }

    /**
     * 相似度去重检查
     */
    private boolean isDuplicateNode(Long userId, String newName) {
        String normalized = newName.toLowerCase().replaceAll("\\s+", "");
        if (normalized.length() < 3) return false;

        List<KnowledgeNode> existingNodes = nodeMapper.findByUserId(userId);
        for (KnowledgeNode existing : existingNodes) {
            String existingName = existing.getName().toLowerCase().replaceAll("\\s+", "");
            if (existingName.equals(normalized)) return true;
            if (calculateSimilarity(normalized, existingName) > SIMILARITY_THRESHOLD) {
                return true;
            }
        }
        return false;
    }

    private double calculateSimilarity(String s1, String s2) {
        if (s1.length() < 3 || s2.length() < 3) return 0;
        int longer = Math.max(s1.length(), s2.length());
        int lcs = longestCommonSubstring(s1, s2);
        return (double) lcs / longer;
    }

    private int longestCommonSubstring(String s1, String s2) {
        int m = s1.length();
        int n = s2.length();
        int maxLen = 0;
        int[] prev = new int[n + 1];
        int[] curr = new int[n + 1];

        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                if (s1.charAt(i - 1) == s2.charAt(j - 1)) {
                    curr[j] = prev[j - 1] + 1;
                    if (curr[j] > maxLen) maxLen = curr[j];
                } else {
                    curr[j] = 0;
                }
            }
            int[] temp = prev;
            prev = curr;
            curr = temp;
        }
        return maxLen;
    }

    /**
     * 合并关键词（去重）
     */
    private String mergeKeywords(String existing, String newKeywords) {
        if (existing == null || existing.isEmpty()) return newKeywords;
        if (newKeywords == null || newKeywords.isEmpty()) return existing;

        Set<String> allKeywords = new LinkedHashSet<>();
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
     * 检查节点是否需要丰富化
     * 如果节点缺少头像、图片或详细描述，则需要丰富化
     */
    private boolean needsEnrichment(KnowledgeNode node) {
        if (node == null) return false;

        // 检查是否缺少头像
        boolean missingAvatar = node.getAvatar() == null || node.getAvatar().isEmpty();
        // 检查是否缺少图片
        boolean missingImage = node.getImage() == null || node.getImage().isEmpty();
        // 检查是否缺少详细描述
        boolean missingDetailedDesc = node.getDetailedDescription() == null || node.getDetailedDescription().isEmpty();

        return missingAvatar || missingImage || missingDetailedDesc;
    }

    private KnowledgeNodeDTO toDTO(KnowledgeNode entity) {
        KnowledgeNodeDTO dto = new KnowledgeNodeDTO();
        dto.setId(entity.getId());
        dto.setUserId(entity.getUserId());
        dto.setNodeType(entity.getNodeType());
        dto.setName(entity.getName());
        dto.setDescription(entity.getDescription());
        dto.setAvatar(entity.getAvatar());
        dto.setImage(entity.getImage());
        dto.setDetailedDescription(entity.getDetailedDescription());
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

    private KnowledgeNode toEntity(KnowledgeNodeDTO dto) {
        KnowledgeNode entity = new KnowledgeNode();
        entity.setUserId(dto.getUserId());
        entity.setNodeType(dto.getNodeType() != null ? dto.getNodeType() : "CONCEPT");
        entity.setName(dto.getName());
        entity.setDescription(dto.getDescription());
        entity.setAvatar(dto.getAvatar());
        entity.setImage(dto.getImage());
        entity.setDetailedDescription(dto.getDetailedDescription());
        entity.setKeywords(dto.getKeywords());
        entity.setImportance(dto.getImportance() != null ? dto.getImportance() : 5);
        entity.setProperties(dto.getProperties());
        entity.setSourceSessionId(dto.getSourceSessionId());
        entity.setConfidence(dto.getConfidence());
        return entity;
    }

    private GraphContextDTO.NodeDTO toNodeDTOForGraph(KnowledgeNode entity) {
        GraphContextDTO.NodeDTO dto = new GraphContextDTO.NodeDTO();
        dto.setId(entity.getId());
        dto.setType(entity.getNodeType());
        // 设置类型名称
        if (entity.getNodeType() != null) {
            switch (entity.getNodeType()) {
                case "USER": dto.setTypeName("用户"); break;
                case "PROJECT": dto.setTypeName("项目"); break;
                case "TECHNOLOGY": dto.setTypeName("技术栈"); break;
                case "CONCEPT": dto.setTypeName("概念"); break;
                case "TASK": dto.setTypeName("任务"); break;
                case "ERROR": dto.setTypeName("错误"); break;
                case "SOLUTION": dto.setTypeName("解决方案"); break;
                case "PREFERENCE": dto.setTypeName("偏好"); break;
                case "CHARACTER": dto.setTypeName("角色"); break;
                case "WORK": dto.setTypeName("作品"); break;
                default: dto.setTypeName("其他");
            }
        }
        dto.setName(entity.getName());
        dto.setDescription(entity.getDescription());
        dto.setAvatar(entity.getAvatar());
        dto.setImage(entity.getImage());
        dto.setDetailedDescription(entity.getDetailedDescription());
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
        dto.setTypeName(GraphContextDTO.getRelationTypeName(entity.getRelationType()));
        dto.setWeight(entity.getWeight());

        if (entity.getSourceNode() != null) {
            dto.setSourceName(entity.getSourceNode().getName());
        }
        if (entity.getTargetNode() != null) {
            dto.setTargetName(entity.getTargetNode().getName());
        }

        return dto;
    }
}
