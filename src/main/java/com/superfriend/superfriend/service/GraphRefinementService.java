package com.superfriend.superfriend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.superfriend.superfriend.dto.GraphFactDTO;
import com.superfriend.superfriend.dto.KnowledgeNodeDTO;
import com.superfriend.superfriend.entity.KnowledgeNode;
import com.superfriend.superfriend.entity.KnowledgeRelation;
import com.superfriend.superfriend.mapper.KnowledgeNodeMapper;
import com.superfriend.superfriend.mapper.KnowledgeRelationMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 图谱提炼服务
 * 负责将对话级图谱提炼到全局图谱，处理信息冲突
 */
@Slf4j
@Service
public class GraphRefinementService {

    @Autowired
    private KnowledgeNodeMapper nodeMapper;

    @Autowired
    private KnowledgeRelationMapper relationMapper;

    @Autowired
    private UserProfileService userProfileService;

    @Autowired
    private ObjectMapper objectMapper;

    private static final double TIME_DECAY_FACTOR = 0.95;
    private static final double CONFLICT_THRESHOLD = 0.15; // 置信度差异阈值
    private static final double SIMILARITY_THRESHOLD = 0.7;

    // ==================== 核心提炼方法 ====================

    /**
     * 异步提炼对话图谱到全局图谱
     * 默认保留对话级图谱（全局和会话级并存，各有用途）
     */
    @Async("asyncTaskExecutor")
    @Transactional
    public void refineConversationGraphAsync(Long userId, String sessionId) {
        refineConversationGraphAsync(userId, sessionId, false);
    }

    /**
     * 异步提炼对话图谱到全局图谱
     *
     * @param userId 用户ID
     * @param sessionId 会话ID
     * @param cleanupAfterRefine 提炼后是否清理对话级图谱
     */
    @Async("asyncTaskExecutor")
    @Transactional
    public void refineConversationGraphAsync(Long userId, String sessionId, boolean cleanupAfterRefine) {
        try {
            refineConversationGraph(userId, sessionId, cleanupAfterRefine);
        } catch (Exception e) {
            log.error("Failed to refine conversation graph: userId={}, sessionId={}, error={}",
                    userId, sessionId, e.getMessage(), e);
        }
    }

    /**
     * 提炼对话图谱到全局图谱
     * 默认保留对话级图谱（全局和会话级并存，各有用途）
     */
    public void refineConversationGraph(Long userId, String sessionId) {
        refineConversationGraph(userId, sessionId, false);
    }

    /**
     * 提炼对话图谱到全局图谱
     *
     * @param userId 用户ID
     * @param sessionId 会话ID
     * @param cleanupAfterRefine 提炼后是否清理对话级图谱
     */
    public void refineConversationGraph(Long userId, String sessionId, boolean cleanupAfterRefine) {
        log.info("Starting graph refinement: userId={}, sessionId={}, cleanup={}", userId, sessionId, cleanupAfterRefine);

        // 1. 获取对话级图谱节点
        List<KnowledgeNode> conversationNodes = nodeMapper.findConversationNodes(userId, sessionId);
        if (conversationNodes.isEmpty()) {
            log.info("No conversation nodes found for sessionId={}", sessionId);
            return;
        }

        // 2. 提取事实信息
        List<GraphFactDTO> facts = extractFactsFromNodes(conversationNodes);

        // 3. 更新用户画像
        if (!facts.isEmpty()) {
            userProfileService.updateUserProfile(userId, sessionId, facts);
        }

        // 4. 合并节点到全局图谱
        mergeNodesToGlobal(userId, sessionId, conversationNodes);

        // 5. 合并关系到全局图谱
        mergeRelationsToGlobal(userId, sessionId);

        // 6. 可选：清理对话级图谱（默认保留，全局和会话级并存）
        if (cleanupAfterRefine) {
            cleanupConversationGraph(userId, sessionId);
        }

        log.info("Graph refinement completed: userId={}, sessionId={}, nodes={}, facts={}",
                userId, sessionId, conversationNodes.size(), facts.size());
    }

    /**
     * 清理对话级图谱
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

    // ==================== 事实提取 ====================

    /**
     * 从节点中提取事实信息
     */
    private List<GraphFactDTO> extractFactsFromNodes(List<KnowledgeNode> nodes) {
        List<GraphFactDTO> facts = new ArrayList<>();

        for (KnowledgeNode node : nodes) {
            GraphFactDTO fact = convertNodeToFact(node);
            if (fact != null) {
                facts.add(fact);
            }
        }

        return facts;
    }

    /**
     * 将节点转换为事实
     */
    private GraphFactDTO convertNodeToFact(KnowledgeNode node) {
        GraphFactDTO fact = new GraphFactDTO();
        fact.setFactKey(node.getName());
        fact.setSourceSessionId(node.getSourceSessionId());
        fact.setConfidence(node.getConfidence() != null ? node.getConfidence() : BigDecimal.valueOf(0.8));
        fact.setMentionCount(1);

        String nodeType = node.getNodeType();
        switch (nodeType) {
            case KnowledgeNode.TYPE_TECHNOLOGY:
                fact.setFactType("SKILL");
                fact.setLevel(extractLevelFromProperties(node.getProperties()));
                break;
            case KnowledgeNode.TYPE_PREFERENCE:
                fact.setFactType("PREFERENCE");
                fact.setCategory(extractCategoryFromProperties(node.getProperties()));
                break;
            case KnowledgeNode.TYPE_CONCEPT:
                if (isTraitKeyword(node.getKeywords())) {
                    fact.setFactType("TRAIT");
                } else {
                    fact.setFactType("INTEREST");
                }
                break;
            default:
                fact.setFactType("INTEREST");
        }

        // 提取上下文
        if (node.getDescription() != null) {
            fact.setContexts(Arrays.asList(node.getDescription()));
        }

        return fact;
    }

    private String extractLevelFromProperties(String properties) {
        if (properties == null || properties.isEmpty()) return "了解";
        try {
            Map<String, Object> props = objectMapper.readValue(properties, new TypeReference<Map<String, Object>>() {});
            Object level = props.get("level");
            return level != null ? level.toString() : "了解";
        } catch (Exception e) {
            return "了解";
        }
    }

    private String extractCategoryFromProperties(String properties) {
        if (properties == null || properties.isEmpty()) return "general";
        try {
            Map<String, Object> props = objectMapper.readValue(properties, new TypeReference<Map<String, Object>>() {});
            Object category = props.get("category");
            return category != null ? category.toString() : "general";
        } catch (Exception e) {
            return "general";
        }
    }

    private boolean isTraitKeyword(String keywords) {
        if (keywords == null) return false;
        String lower = keywords.toLowerCase();
        // 性格特征相关关键词
        return lower.contains("性格") || lower.contains("特质") || lower.contains("风格") ||
               lower.contains("习惯") || lower.contains("注重") || lower.contains("偏好");
    }

    // ==================== 节点合并 ====================

    /**
     * 合并节点到全局图谱
     */
    private void mergeNodesToGlobal(Long userId, String sessionId, List<KnowledgeNode> conversationNodes) {
        for (KnowledgeNode convNode : conversationNodes) {
            // 查找全局图谱中是否存在相似节点
            KnowledgeNode globalNode = findSimilarGlobalNode(userId, convNode);

            if (globalNode != null) {
                // 存在相似节点，合并
                mergeNode(globalNode, convNode, sessionId);
            } else {
                // 不存在，创建新的全局节点
                createGlobalNode(userId, sessionId, convNode);
            }
        }
    }

    /**
     * 查找相似的全局节点
     * 使用 scope + name 精确匹配，确保全局图谱中同名节点只有一个
     */
    private KnowledgeNode findSimilarGlobalNode(Long userId, KnowledgeNode convNode) {
        // 精确匹配：按 scope=GLOBAL + name 查询
        KnowledgeNode exact = nodeMapper.findByUserIdAndScopeAndName(
                userId, KnowledgeNode.SCOPE_GLOBAL, convNode.getName());
        if (exact != null) {
            return exact;
        }

        // 相似度匹配（仅当精确匹配失败时）
        List<KnowledgeNode> globalNodes = nodeMapper.findByUserIdAndScope(userId, KnowledgeNode.SCOPE_GLOBAL);
        for (KnowledgeNode global : globalNodes) {
            if (calculateSimilarity(convNode.getName(), global.getName()) > SIMILARITY_THRESHOLD) {
                return global;
            }
        }

        return null;
    }

    /**
     * 合并节点
     */
    private void mergeNode(KnowledgeNode global, KnowledgeNode conv, String sessionId) {
        // 计算有效置信度（考虑时间衰减）
        BigDecimal effectiveGlobalConf = calculateEffectiveConfidence(
                global.getConfidence(), global.getUpdatedTime());
        BigDecimal convConf = conv.getConfidence() != null ? conv.getConfidence() : BigDecimal.valueOf(0.8);

        // 检测冲突
        ConflictResult conflict = detectConflict(global, conv, effectiveGlobalConf, convConf);

        if (conflict.isContradictory()) {
            // 矛盾信息处理
            handleConflict(global, conv, sessionId, conflict);
        } else {
            // 正常合并
            normalMerge(global, conv, sessionId);
        }

        nodeMapper.update(global);
    }

    /**
     * 创建新的全局节点
     * 全局节点的 sourceSessionId 设置为 null，以便正确区分全局图谱和对话图谱
     */
    private void createGlobalNode(Long userId, String sessionId, KnowledgeNode convNode) {
        KnowledgeNode globalNode = new KnowledgeNode();
        globalNode.setUserId(userId);
        globalNode.setNodeType(convNode.getNodeType());
        globalNode.setScope(KnowledgeNode.SCOPE_GLOBAL);
        globalNode.setName(convNode.getName());
        globalNode.setDescription(convNode.getDescription());
        globalNode.setKeywords(convNode.getKeywords());
        globalNode.setImportance(convNode.getImportance() != null ? convNode.getImportance() : 5);
        globalNode.setProperties(convNode.getProperties());
        globalNode.setSourceSessionId(null);
        globalNode.setConfidence(convNode.getConfidence() != null ? convNode.getConfidence() : BigDecimal.valueOf(0.8));
        globalNode.setAccessCount(0);

        nodeMapper.insert(globalNode);
        log.debug("Created global node: name={}, type={}", globalNode.getName(), globalNode.getNodeType());
    }

    // ==================== 冲突检测与处理 ====================

    /**
     * 检测冲突
     */
    private ConflictResult detectConflict(KnowledgeNode global, KnowledgeNode conv,
                                          BigDecimal globalConf, BigDecimal convConf) {
        ConflictResult result = new ConflictResult();
        result.setContradictory(false);

        // 检查描述是否矛盾
        if (global.getDescription() != null && conv.getDescription() != null) {
            if (isContradictoryDescription(global.getDescription(), conv.getDescription())) {
                result.setContradictory(true);
                result.setReason("描述矛盾");
                return result;
            }
        }

        // 检查属性是否矛盾
        if (global.getProperties() != null && conv.getProperties() != null) {
            if (isContradictoryProperties(global.getProperties(), conv.getProperties())) {
                result.setContradictory(true);
                result.setReason("属性矛盾");
                return result;
            }
        }

        return result;
    }

    /**
     * 判断描述是否矛盾
     */
    private boolean isContradictoryDescription(String desc1, String desc2) {
        // 简单的矛盾词检测
        String[][] contradictionPairs = {
                {"喜欢", "讨厌"},
                {"擅长", "不擅长"},
                {"经常", "从不"},
                {"重要", "不重要"},
                {"需要", "不需要"}
        };

        String lower1 = desc1.toLowerCase();
        String lower2 = desc2.toLowerCase();

        for (String[] pair : contradictionPairs) {
            if ((lower1.contains(pair[0]) && lower2.contains(pair[1])) ||
                (lower1.contains(pair[1]) && lower2.contains(pair[0]))) {
                return true;
            }
        }

        return false;
    }

    /**
     * 判断属性是否矛盾
     */
    private boolean isContradictoryProperties(String props1, String props2) {
        try {
            Map<String, Object> map1 = objectMapper.readValue(props1, new TypeReference<Map<String, Object>>() {});
            Map<String, Object> map2 = objectMapper.readValue(props2, new TypeReference<Map<String, Object>>() {});

            // 检查 level 属性
            if (map1.containsKey("level") && map2.containsKey("level")) {
                // 级别差异过大视为矛盾
                int diff = Math.abs(getLevelRank(map1.get("level").toString()) - getLevelRank(map2.get("level").toString()));
                if (diff >= 3) return true;
            }

        } catch (Exception e) {
            // 解析失败，不认为矛盾
        }

        return false;
    }

    /**
     * 处理冲突
     */
    private void handleConflict(KnowledgeNode global, KnowledgeNode conv, String sessionId, ConflictResult conflict) {
        BigDecimal globalConf = global.getConfidence() != null ? global.getConfidence() : BigDecimal.valueOf(0.5);
        BigDecimal convConf = conv.getConfidence() != null ? conv.getConfidence() : BigDecimal.valueOf(0.8);

        // 计算有效置信度
        BigDecimal effectiveGlobalConf = calculateEffectiveConfidence(globalConf, global.getUpdatedTime());

        if (convConf.compareTo(effectiveGlobalConf) > 0) {
            // 新信息置信度更高，替换
            global.setDescription(conv.getDescription());
            global.setProperties(mergeProperties(global.getProperties(), conv.getProperties()));
            global.setConfidence(convConf);
            log.info("Conflict resolved by replacement: node={}, reason={}", global.getName(), conflict.getReason());
        } else {
            // 保留旧信息，但记录冲突
            log.info("Conflict detected but kept old value: node={}, reason={}", global.getName(), conflict.getReason());
            // 可以在这里添加备选值记录
        }
    }

    /**
     * 正常合并
     */
    private void normalMerge(KnowledgeNode global, KnowledgeNode conv, String sessionId) {
        // 累积置信度
        BigDecimal globalConf = global.getConfidence() != null ? global.getConfidence() : BigDecimal.valueOf(0.5);
        BigDecimal convConf = conv.getConfidence() != null ? conv.getConfidence() : BigDecimal.valueOf(0.8);

        BigDecimal newConf = globalConf.add(BigDecimal.valueOf(0.1));
        if (newConf.compareTo(BigDecimal.valueOf(0.95)) > 0) {
            newConf = BigDecimal.valueOf(0.95);
        }
        global.setConfidence(newConf.setScale(2, RoundingMode.HALF_UP));

        // 更新描述（如果新的更详细）
        if (conv.getDescription() != null && !conv.getDescription().isEmpty()) {
            if (global.getDescription() == null || global.getDescription().isEmpty() ||
                conv.getDescription().length() > global.getDescription().length()) {
                global.setDescription(conv.getDescription());
            }
        }

        // 合并关键词
        global.setKeywords(mergeKeywords(global.getKeywords(), conv.getKeywords()));

        // 更新重要性（取较高值）
        if (conv.getImportance() != null &&
            (global.getImportance() == null || conv.getImportance() > global.getImportance())) {
            global.setImportance(conv.getImportance());
        }

        // 合并属性
        global.setProperties(mergeProperties(global.getProperties(), conv.getProperties()));
    }

    // ==================== 关系合并 ====================

    /**
     * 合并关系到全局图谱
     * 全局关系的 sourceSessionId 设置为 null，以便正确区分全局图谱和对话图谱
     */
    private void mergeRelationsToGlobal(Long userId, String sessionId) {
        List<KnowledgeRelation> convRelations = relationMapper.findBySessionId(userId, sessionId);

        for (KnowledgeRelation convRel : convRelations) {
            KnowledgeRelation globalRel = relationMapper.findExistingRelation(
                    convRel.getSourceNodeId(), convRel.getTargetNodeId(), convRel.getRelationType());

            if (globalRel != null) {
                BigDecimal newWeight = globalRel.getWeight().add(BigDecimal.valueOf(0.1));
                if (newWeight.compareTo(BigDecimal.ONE) > 0) {
                    newWeight = BigDecimal.ONE;
                }
                globalRel.setWeight(newWeight);
                relationMapper.update(globalRel);
            } else {
                KnowledgeRelation newGlobalRel = new KnowledgeRelation();
                newGlobalRel.setUserId(userId);
                newGlobalRel.setSourceNodeId(convRel.getSourceNodeId());
                newGlobalRel.setTargetNodeId(convRel.getTargetNodeId());
                newGlobalRel.setRelationType(convRel.getRelationType());
                newGlobalRel.setProperties(convRel.getProperties());
                newGlobalRel.setWeight(convRel.getWeight());
                newGlobalRel.setSourceSessionId(null);
                relationMapper.insert(newGlobalRel);
            }
        }
    }

    // ==================== 辅助方法 ====================

    private BigDecimal calculateEffectiveConfidence(BigDecimal confidence, LocalDateTime lastUpdated) {
        if (confidence == null) return BigDecimal.valueOf(0.5);
        if (lastUpdated == null) return confidence;

        long months = ChronoUnit.MONTHS.between(lastUpdated, LocalDateTime.now());
        double decay = Math.pow(TIME_DECAY_FACTOR, months);
        return confidence.multiply(BigDecimal.valueOf(decay)).setScale(2, RoundingMode.HALF_UP);
    }

    private double calculateSimilarity(String s1, String s2) {
        if (s1 == null || s2 == null) return 0;
        String normalized1 = s1.toLowerCase().replaceAll("\\s+", "");
        String normalized2 = s2.toLowerCase().replaceAll("\\s+", "");

        if (normalized1.equals(normalized2)) return 1.0;
        if (normalized1.length() < 2 || normalized2.length() < 2) return 0;

        int longer = Math.max(normalized1.length(), normalized2.length());
        int lcs = longestCommonSubstring(normalized1, normalized2);
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

    private String mergeProperties(String props1, String props2) {
        if (props1 == null || props1.isEmpty()) return props2;
        if (props2 == null || props2.isEmpty()) return props1;

        try {
            Map<String, Object> map1 = objectMapper.readValue(props1, new TypeReference<Map<String, Object>>() {});
            Map<String, Object> map2 = objectMapper.readValue(props2, new TypeReference<Map<String, Object>>() {});

            map1.putAll(map2);
            return objectMapper.writeValueAsString(map1);
        } catch (Exception e) {
            return props1;
        }
    }

    private int getLevelRank(String level) {
        if (level == null) return 0;
        switch (level.toLowerCase()) {
            case "精通": return 4;
            case "熟练": return 3;
            case "掌握": return 2;
            case "了解": return 1;
            default: return 0;
        }
    }

    // ==================== 内部类 ====================

    @Data
    private static class ConflictResult {
        private boolean contradictory;
        private String reason;
    }
}
