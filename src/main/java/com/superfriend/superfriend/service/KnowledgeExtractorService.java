package com.superfriend.superfriend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.superfriend.superfriend.config.KnowledgeExtractionProperties;
import com.superfriend.superfriend.dto.LLMCompleteResponse;
import com.superfriend.superfriend.dto.LLMRequest;
import com.superfriend.superfriend.entity.AIModelConfig;
import com.superfriend.superfriend.entity.KnowledgeNode;
import com.superfriend.superfriend.entity.KnowledgeRelation;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;

/**
 * 统一的知识提取服务
 * 使用 LLM 从最近一轮对话中提取结构化知识节点和关系
 *
 * 核心原则：
 * 1. 只提取最近一轮对话（用户询问 + AI回答）
 * 2. 节点名称与已有节点做相似性检查，重复则取消保存
 * 3. 只丰富化本轮提取的节点
 */
@Slf4j
@Service
public class KnowledgeExtractorService {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private LLMClient llmClient;

    @Autowired
    @Lazy
    private AIModelConfigService modelConfigService;

    @Autowired
    @Lazy
    private KnowledgeGraphService knowledgeGraphService;

    @Autowired
    @Lazy
    private GraphRefinementService graphRefinementService;

    @Autowired
    @Lazy
    private NodeEnrichmentService nodeEnrichmentService;

    @Autowired
    private KnowledgeExtractionProperties properties;

    /**
     * 正在处理的 session 集合，用于防抖
     */
    private final Set<String> processingSessions = ConcurrentHashMap.newKeySet();

    /**
     * 已完成提取的 session 队列，用于限制内存大小
     */
    private final LinkedBlockingQueue<String> completedSessionQueue = new LinkedBlockingQueue<>(1000);

    /**
     * 已完成提取的 session 集合，防止重复提取
     */
    private final Set<String> completedSessions = ConcurrentHashMap.newKeySet();

    /**
     * 相似度阈值，超过此值认为节点名称重复
     */
    private static final double SIMILARITY_THRESHOLD = 0.85;

    /**
     * 检查是否应该触发知识提取
     * 每轮对话结束后检查，只要有用户消息就触发
     */
    public boolean shouldTriggerExtraction(List<Map<String, Object>> messages) {
        if (messages == null || messages.isEmpty()) {
            log.info("[KnowledgeExtractor] shouldTriggerExtraction: messages 为空");
            return false;
        }

        // 检查是否有用户消息
        boolean hasUserMessage = messages.stream()
            .anyMatch(msg -> "user".equals(msg.get("role")));

        if (!hasUserMessage) {
            log.info("[KnowledgeExtractor] shouldTriggerExtraction: 没有用户消息");
            return false;
        }

        // 检查是否有助手回复（至少一轮完整对话）
        boolean hasAssistantReply = messages.stream()
            .anyMatch(msg -> "assistant".equals(msg.get("role")));

        if (!hasAssistantReply) {
            log.info("[KnowledgeExtractor] shouldTriggerExtraction: 没有助手回复");
            return false;
        }

        log.info("[KnowledgeExtractor] shouldTriggerExtraction: 满足条件，可以触发提取");
        return true;
    }

    /**
     * 计算消息的总文本长度
     */
    public int calculateTotalTextLength(List<Map<String, Object>> messages) {
        if (messages == null) return 0;

        int totalLength = 0;
        for (Map<String, Object> msg : messages) {
            String role = (String) msg.get("role");
            String content = (String) msg.get("content");

            if (("user".equals(role) || "assistant".equals(role)) && content != null) {
                totalLength += content.length();
            }
        }
        return totalLength;
    }

    /**
     * 从最近一轮对话中提取知识
     * 只提取最后一轮用户询问和AI回答
     */
    public ExtractionResult extractFromConversation(Long userId, String sessionId,
                                                     List<Map<String, Object>> messages,
                                                     String modelName) {
        log.info("开始知识提取：userId={}, sessionId={}, messageCount={}",
            userId, sessionId, messages != null ? messages.size() : 0);

        if (userId == null || messages == null || messages.isEmpty()) {
            log.info("跳过知识提取：参数无效");
            return new ExtractionResult();
        }

        try {
            // 1. 获取模型配置
            AIModelConfig modelConfig = resolveModelConfig(userId, modelName);
            if (modelConfig == null) {
                log.warn("用户 {} 没有配置可用的模型，跳过知识提取", userId);
                return new ExtractionResult();
            }

            // 2. 提取最近一轮对话（用户询问 + AI回答）
            String conversationText = extractLastTurnConversation(messages);
            if (conversationText.isEmpty()) {
                log.info("没有找到完整的对话轮次，跳过提取");
                return new ExtractionResult();
            }
            log.info("[KnowledgeExtractor] 最近一轮对话长度：{}，内容预览：{}",
                conversationText.length(),
                conversationText.length() > 300 ? conversationText.substring(0, 300) + "..." : conversationText);

            // 3. 获取已有节点名称列表（用于相似性检查）
            Set<String> existingNodeNames = getExistingNodeNames(userId);
            log.info("[KnowledgeExtractor] 已有节点数量：{}", existingNodeNames.size());

            // 4. 调用 LLM 提取知识
            String prompt = buildExtractionPrompt(conversationText, existingNodeNames);
            String llmResponse = callLLMNonStream(prompt, modelConfig);

            if (llmResponse == null || llmResponse.trim().isEmpty()) {
                log.warn("LLM 返回空响应，跳过知识提取");
                return new ExtractionResult();
            }

            // 5. 解析结果
            log.debug("[KnowledgeExtractor] LLM 响应长度：{}", llmResponse.length());
            ExtractionResult result = parseExtractionResult(llmResponse);

            // 6. 相似性检查，过滤重复节点
            List<KnowledgeNode> uniqueNodes = filterDuplicateNodes(result.getNodes(), existingNodeNames);
            if (uniqueNodes.isEmpty()) {
                log.info("所有节点都与已有节点重复，跳过保存");
                return new ExtractionResult();
            }

            // 6.1 同步过滤关系列表，只保留两端节点都有效的关系
            List<KnowledgeRelation> validRelations = filterValidRelations(
                result.getRelations(), uniqueNodes, userId);

            // 7. 设置节点为对话级 scope
            for (KnowledgeNode node : uniqueNodes) {
                node.setScope(KnowledgeNode.SCOPE_CONVERSATION);
            }

            // 8. 保存节点和关系，返回实际保存的节点ID列表
            List<Long> savedNodeIds = saveNodesAndRelations(userId, sessionId, uniqueNodes, validRelations);

            // 9. 只丰富化本轮保存的节点
            enrichSavedNodes(savedNodeIds);

            log.info("知识提取完成：提取了 {} 个节点（过滤了 {} 个重复），保存了 {} 个节点，有效关系 {} 条",
                result.getNodes().size(), result.getNodes().size() - uniqueNodes.size(), savedNodeIds.size(), validRelations.size());

            // 更新结果中的节点列表为实际保存的
            result.setNodes(uniqueNodes);
            result.setRelations(validRelations);
            return result;

        } catch (Exception e) {
            log.error("知识提取失败：{}", e.getMessage(), e);
            return new ExtractionResult();
        }
    }

    /**
     * 提取最近一轮完整的对话（用户询问 + AI回答）
     */
    private String extractLastTurnConversation(List<Map<String, Object>> messages) {
        // 从后往前找最后一个用户消息
        int lastUserIndex = -1;
        for (int i = messages.size() - 1; i >= 0; i--) {
            if ("user".equals(messages.get(i).get("role"))) {
                lastUserIndex = i;
                break;
            }
        }

        if (lastUserIndex < 0) {
            return "";
        }

        StringBuilder sb = new StringBuilder();

        // 添加用户消息
        String userContent = (String) messages.get(lastUserIndex).get("content");
        if (userContent != null && !userContent.trim().isEmpty()) {
            // 限制长度
            if (userContent.length() > 2000) {
                userContent = userContent.substring(0, 2000) + "...(已截断)";
            }
            sb.append("[用户]: ").append(userContent).append("\n\n");
        }

        // 找用户消息后的第一个助手回复
        for (int i = lastUserIndex + 1; i < messages.size(); i++) {
            Map<String, Object> msg = messages.get(i);
            if ("assistant".equals(msg.get("role"))) {
                String assistantContent = (String) msg.get("content");
                if (assistantContent != null && !assistantContent.trim().isEmpty()) {
                    // 限制长度
                    if (assistantContent.length() > 2000) {
                        assistantContent = assistantContent.substring(0, 2000) + "...(已截断)";
                    }
                    sb.append("[助手]: ").append(assistantContent).append("\n\n");
                }
                break;
            }
        }

        return sb.toString();
    }

    /**
     * 获取已有节点的名称集合
     */
    private Set<String> getExistingNodeNames(Long userId) {
        return knowledgeGraphService.getAllNodes(userId)
            .stream()
            .map(dto -> normalizeName(dto.getName()))
            .filter(name -> !name.isEmpty())
            .collect(Collectors.toSet());
    }

    /**
     * 标准化节点名称（用于相似性比较）
     */
    private String normalizeName(String name) {
        if (name == null) return "";
        // 转小写，去除空格和标点
        return name.toLowerCase()
            .replaceAll("[\\s\\p{Punct}]", "")
            .trim();
    }

    /**
     * 过滤重复节点（基于名称相似性）
     */
    private List<KnowledgeNode> filterDuplicateNodes(List<KnowledgeNode> nodes, Set<String> existingNames) {
        List<KnowledgeNode> uniqueNodes = new ArrayList<>();

        for (KnowledgeNode node : nodes) {
            String normalizedName = normalizeName(node.getName());
            if (normalizedName.isEmpty()) {
                continue;
            }

            // 检查是否与已有节点相似
            boolean isDuplicate = false;
            for (String existingName : existingNames) {
                double similarity = calculateSimilarity(normalizedName, existingName);
                if (similarity >= SIMILARITY_THRESHOLD) {
                    log.info("节点 '{}' 与已有节点相似度 {:.2f}，跳过保存", node.getName(), similarity);
                    isDuplicate = true;
                    break;
                }
            }

            // 也检查是否与本轮其他新节点重复
            if (!isDuplicate) {
                for (KnowledgeNode existing : uniqueNodes) {
                    double similarity = calculateSimilarity(normalizedName, normalizeName(existing.getName()));
                    if (similarity >= SIMILARITY_THRESHOLD) {
                        log.info("节点 '{}' 与本轮其他节点 '{}' 相似，跳过", node.getName(), existing.getName());
                        isDuplicate = true;
                        break;
                    }
                }
            }

            if (!isDuplicate) {
                uniqueNodes.add(node);
                // 添加到已有名称集合，防止本轮内重复
                existingNames.add(normalizedName);
            }
        }

        return uniqueNodes;
    }

    /**
     * 过滤有效的关系
     * 只保留两端节点都在本轮提取的节点或已有节点中的关系
     */
    private List<KnowledgeRelation> filterValidRelations(List<KnowledgeRelation> relations,
                                                          List<KnowledgeNode> uniqueNodes,
                                                          Long userId) {
        if (relations == null || relations.isEmpty()) {
            return new ArrayList<>();
        }

        // 构建本轮节点名称集合
        Set<String> newNodeNames = uniqueNodes.stream()
            .map(node -> normalizeName(node.getName()))
            .filter(name -> !name.isEmpty())
            .collect(Collectors.toSet());

        List<KnowledgeRelation> validRelations = new ArrayList<>();

        for (KnowledgeRelation rel : relations) {
            String sourceName = rel.getSourceNode() != null ? rel.getSourceNode().getName() : null;
            String targetName = rel.getTargetNode() != null ? rel.getTargetNode().getName() : null;

            if (sourceName == null || targetName == null) {
                log.debug("关系缺少节点名称，跳过");
                continue;
            }

            String normalizedSource = normalizeName(sourceName);
            String normalizedTarget = normalizeName(targetName);

            // 检查源节点是否有效（在本轮节点中或已有节点中）
            boolean sourceValid = newNodeNames.contains(normalizedSource) ||
                isExistingNode(userId, sourceName);
            // 检查目标节点是否有效
            boolean targetValid = newNodeNames.contains(normalizedTarget) ||
                isExistingNode(userId, targetName);

            if (sourceValid && targetValid) {
                validRelations.add(rel);
                log.debug("关系有效: {} -> {}", sourceName, targetName);
            } else {
                log.info("关系无效（节点不存在）: {} -> {} (sourceValid={}, targetValid={})",
                    sourceName, targetName, sourceValid, targetValid);
            }
        }

        return validRelations;
    }

    /**
     * 检查节点是否已存在（通过名称查找）
     */
    private boolean isExistingNode(Long userId, String nodeName) {
        if (nodeName == null || nodeName.trim().isEmpty()) {
            return false;
        }
        Long nodeId = knowledgeGraphService.findNodeIdByName(userId, nodeName);
        return nodeId != null;
    }

    /**
     * 计算两个字符串的相似度（基于编辑距离）
     */
    private double calculateSimilarity(String s1, String s2) {
        if (s1.isEmpty() || s2.isEmpty()) return 0.0;
        if (s1.equals(s2)) return 1.0;

        int maxLen = Math.max(s1.length(), s2.length());
        int editDistance = calculateEditDistance(s1, s2);

        return 1.0 - (double) editDistance / maxLen;
    }

    /**
     * 计算编辑距离
     */
    private int calculateEditDistance(String s1, String s2) {
        int m = s1.length();
        int n = s2.length();

        int[][] dp = new int[m + 1][n + 1];

        for (int i = 0; i <= m; i++) dp[i][0] = i;
        for (int j = 0; j <= n; j++) dp[0][j] = j;

        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                if (s1.charAt(i - 1) == s2.charAt(j - 1)) {
                    dp[i][j] = dp[i - 1][j - 1];
                } else {
                    dp[i][j] = Math.min(
                        Math.min(dp[i - 1][j], dp[i][j - 1]),
                        dp[i - 1][j - 1]
                    ) + 1;
                }
            }
        }

        return dp[m][n];
    }

    /**
     * 保存节点和关系，返回保存成功的节点ID列表
     */
    private List<Long> saveNodesAndRelations(Long userId, String sessionId,
                                              List<KnowledgeNode> nodes,
                                              List<KnowledgeRelation> relations) {
        List<Long> savedNodeIds = new ArrayList<>();

        // 保存节点
        Map<String, Long> nameToIdMap = new HashMap<>();
        for (KnowledgeNode node : nodes) {
            node.setUserId(userId);
            node.setSourceSessionId(sessionId);
            if (node.getConfidence() == null) {
                node.setConfidence(BigDecimal.ONE);
            }
            if (node.getImportance() == null) {
                node.setImportance(5);
            }

            // 调用 KnowledgeGraphService 的添加方法
            KnowledgeNode savedNode = knowledgeGraphService.addNodeDirectly(node);
            if (savedNode != null && savedNode.getId() != null) {
                savedNodeIds.add(savedNode.getId());
                nameToIdMap.put(node.getName(), savedNode.getId());
                log.debug("保存节点成功: id={}, name={}", savedNode.getId(), node.getName());
            }
        }

        // 保存关系
        for (KnowledgeRelation rel : relations) {
            Long sourceId = nameToIdMap.get(rel.getSourceNode() != null ? rel.getSourceNode().getName() : null);
            Long targetId = nameToIdMap.get(rel.getTargetNode() != null ? rel.getTargetNode().getName() : null);

            // 如果关系中的节点不在本轮提取中，尝试从已有节点中查找
            if (sourceId == null && rel.getSourceNode() != null) {
                sourceId = knowledgeGraphService.findNodeIdByName(userId, rel.getSourceNode().getName());
            }
            if (targetId == null && rel.getTargetNode() != null) {
                targetId = knowledgeGraphService.findNodeIdByName(userId, rel.getTargetNode().getName());
            }

            if (sourceId != null && targetId != null) {
                try {
                    knowledgeGraphService.addRelation(userId, sourceId, targetId,
                        rel.getRelationType(), null, rel.getWeight());
                    log.debug("保存关系成功: {} -> {}", sourceId, targetId);
                } catch (Exception e) {
                    log.warn("保存关系失败: {}", e.getMessage());
                }
            }
        }

        return savedNodeIds;
    }

    /**
     * 丰富化保存的节点
     */
    private void enrichSavedNodes(List<Long> nodeIds) {
        for (Long nodeId : nodeIds) {
            try {
                nodeEnrichmentService.enrichNodeAsync(nodeId);
                log.debug("触发节点丰富化: nodeId={}", nodeId);
            } catch (Exception e) {
                log.warn("触发节点丰富化失败: nodeId={}, error={}", nodeId, e.getMessage());
            }
        }
    }

    /**
     * 异步提取知识
     * 每轮对话完成后都会调用，通过相似性检查自动过滤重复节点
     */
    @Async("asyncTaskExecutor")
    public void extractAsync(Long userId, String sessionId,
                             List<Map<String, Object>> messages,
                             String modelName) {
        log.info("[KnowledgeExtractor] extractAsync 被调用: userId={}, sessionId={}, messages={}",
            userId, sessionId, messages != null ? messages.size() : 0);

        // 防抖检查：如果正在处理中，跳过
        if (!processingSessions.add(sessionId)) {
            log.info("[KnowledgeExtractor] Session {} 正在处理中，跳过重复提取", sessionId);
            return;
        }

        try {
            log.info("[KnowledgeExtractor] 开始异步知识提取: userId={}, sessionId={}", userId, sessionId);

            ExtractionResult result = extractFromConversation(userId, sessionId, messages, modelName);

            log.info("[KnowledgeExtractor] 提取完成: userId={}, sessionId={}, nodes={}",
                    userId, sessionId, result.getNodes().size());

            // 全局图谱节点生成已禁用
            // 提取完成后，自动提炼到全局图谱
            // if (result.getNodes() != null && !result.getNodes().isEmpty()) {
            //     log.info("[KnowledgeExtractor] 触发图谱提炼: userId={}, sessionId={}", userId, sessionId);
            //     graphRefinementService.refineConversationGraph(userId, sessionId);
            // }

        } catch (Exception e) {
            log.error("[KnowledgeExtractor] 提取失败: userId={}, sessionId={}, error={}",
                    userId, sessionId, e.getMessage(), e);
        } finally {
            processingSessions.remove(sessionId);
        }
    }

    /**
     * 强制重新提取
     */
    @Async("asyncTaskExecutor")
    public void forceExtractAsync(Long userId, String sessionId,
                                   List<Map<String, Object>> messages,
                                   String modelName) {
        completedSessions.remove(sessionId);
        extractAsync(userId, sessionId, messages, modelName);
    }

    public boolean isExtractionCompleted(String sessionId) {
        return completedSessions.contains(sessionId);
    }

    public boolean isProcessing(String sessionId) {
        return processingSessions.contains(sessionId);
    }

    public void clearExtractionStatus(String sessionId) {
        processingSessions.remove(sessionId);
        completedSessions.remove(sessionId);
        completedSessionQueue.remove(sessionId);
    }

    public void triggerGraphRefinement(Long userId, String sessionId) {
        log.info("触发图谱提炼已禁用: userId={}, sessionId={}", userId, sessionId);
        // graphRefinementService.refineConversationGraph(userId, sessionId);
    }

    private void markAsCompleted(String sessionId) {
        if (!completedSessionQueue.offer(sessionId)) {
            String oldest = completedSessionQueue.poll();
            if (oldest != null) {
                completedSessions.remove(oldest);
            }
            completedSessionQueue.offer(sessionId);
        }
        completedSessions.add(sessionId);
    }

    private AIModelConfig resolveModelConfig(Long userId, String modelName) {
        List<AIModelConfig> allModels = modelConfigService.getAvailableModelEntities(userId);
        if (allModels == null || allModels.isEmpty()) {
            return null;
        }

        Set<String> reasoningKeywords = new HashSet<>(Arrays.asList(
            "reasoner", "-r1", "o1-", "o3-", "deepseek-reasoner",
            "thinking", "reasoning", "think", "cot"
        ));

        AIModelConfig firstNonReasoning = null;
        AIModelConfig fallback = null;

        for (AIModelConfig model : allModels) {
            String mid = model.getModelId() != null ? model.getModelId().toLowerCase() : "";
            boolean isReasoning = reasoningKeywords.stream().anyMatch(mid::contains);

            if (!isReasoning) {
                if (Boolean.TRUE.equals(model.getIsDefault())) {
                    return model;
                }
                if (firstNonReasoning == null) {
                    firstNonReasoning = model;
                }
            } else {
                if (fallback == null) {
                    fallback = model;
                }
            }
        }

        return firstNonReasoning != null ? firstNonReasoning : fallback;
    }

    /**
     * 构建知识提取 Prompt
     * 核心思路：让 LLM 站在"用户视角"判断什么值得记住
     */
    private String buildExtractionPrompt(String conversationText, Set<String> existingNodeNames) {
        String existingNamesStr = existingNodeNames.isEmpty() ? "无" :
            String.join(", ", existingNodeNames.stream().limit(20).collect(Collectors.toList()));

        return "你是一个知识管理助手。用户主动开启了知识提取功能，请帮他提取**最核心、最有价值**的信息。\n\n" +

               "## 提取原则\n\n" +
               "### 核心原则：少而精\n" +
               "- 宁缺毋滥，只提取真正重要的信息\n" +
               "- 每次提取建议 1-5 个节点\n" +
               "- 如果没有值得记录的信息，返回空数组\n\n" +

               "### 判断标准\n" +
               "问自己：**如果我是用户，一年后还会在意这个信息吗？**\n\n" +

               "### 应该提取的场景\n" +
               "1. **用户明确表达偏好**\n" +
               "   - \"我喜欢简洁的代码风格\"\n" +
               "   - \"我习惯用深色主题\"\n" +
               "   → 提取：用户的偏好\n\n" +

               "2. **用户询问并得到答案的知识点**\n" +
               "   - \"纲手是谁？\" → 提取：纲手（核心角色信息）\n" +
               "   - \"Spring Boot 怎么配置数据源？\" → 提取：数据源配置方法\n" +
               "   → 提取：用户真正想了解的知识\n\n" +

               "3. **用户提及的重要实体**\n" +
               "   - \"帮我生成纲手的图片\"\n" +
               "   → 提取：纲手（用户关注的核心实体）\n\n" +

               "4. **问题中的核心概念**\n" +
               "   - \"《三体》里有哪些重要角色？\"\n" +
               "   → 提取：三体、叶文洁、罗辑（主角+关键配角，不是所有角色）\n\n" +

               "### 不应该提取的场景\n" +
               "1. **临时性信息**\n" +
               "   - \"我现在在吃饭\" ❌\n" +
               "   - \"今天天气不错\" ❌\n\n" +

               "2. **通用常识**\n" +
               "   - \"Java 是编程语言\" ❌\n" +
               "   - \"MySQL 是数据库\" ❌\n\n" +

               "3. **无关细节**\n" +
               "   - 用户问\"纲手是谁\"，不要提取\"火影忍者第5代火影的徒弟的...\"\n" +
               "   - 只提取核心：纲手\n\n" +

               "4. **模糊意向**\n" +
               "   - \"我可能会学 Python\" ❌（还没确定）\n" +
               "   - \"我决定学 Python\" ✅（明确决策）\n\n" +

               "## 实体类型\n" +
               "- TECHNOLOGY: 技术栈\n" +
               "- PROJECT: 项目\n" +
               "- PREFERENCE: 用户偏好\n" +
               "- CONCEPT: 概念/知识点\n" +
               "- CHARACTER: 角色/人物\n" +
               "- WORK: 作品（小说、游戏、电影等）\n\n" +

               "## 已有节点（避免重复）\n" + existingNamesStr + "\n\n" +

               "## 对话内容\n" + conversationText + "\n\n" +

               "## 返回格式\n" +
               "```json\n" +
               "{\n" +
               "  \"nodes\": [\n" +
               "    {\"type\": \"CHARACTER\", \"name\": \"纲手\", \"description\": \"火影忍者中的角色，五代火影，医疗忍术大师\", \"keywords\": \"火影,医疗,五代\", \"importance\": 8}\n" +
               "  ],\n" +
               "  \"relations\": []\n" +
               "}\n" +
               "```\n\n" +
               "importance 范围 1-10，只有真正重要的才给 7 分以上。";
    }

    private ExtractionResult parseExtractionResult(String llmResponse) {
        ExtractionResult result = new ExtractionResult();

        try {
            String jsonStr = extractJson(llmResponse);
            JsonNode root = objectMapper.readTree(jsonStr);

            JsonNode nodesNode = root.path("nodes");
            if (nodesNode.isArray()) {
                for (JsonNode nodeNode : nodesNode) {
                    String name = nodeNode.path("name").asText("");
                    if (name.isEmpty()) continue;

                    KnowledgeNode node = new KnowledgeNode();
                    node.setNodeType(validateNodeType(nodeNode.path("type").asText("CONCEPT")));
                    node.setName(name);
                    node.setDescription(nodeNode.path("description").asText(""));
                    node.setKeywords(nodeNode.path("keywords").asText(""));
                    node.setImportance(Math.min(10, Math.max(1, nodeNode.path("importance").asInt(5))));
                    node.setConfidence(BigDecimal.valueOf(nodeNode.path("confidence").asDouble(0.8)));

                    result.getNodes().add(node);
                }
            }

            JsonNode relationsNode = root.path("relations");
            if (relationsNode.isArray()) {
                Map<String, KnowledgeNode> nameToNode = new HashMap<>();
                for (KnowledgeNode node : result.getNodes()) {
                    nameToNode.put(node.getName(), node);
                }

                for (JsonNode relNode : relationsNode) {
                    String sourceName = relNode.path("sourceName").asText("");
                    String targetName = relNode.path("targetName").asText("");
                    if (sourceName.isEmpty() || targetName.isEmpty()) continue;

                    KnowledgeRelation relation = new KnowledgeRelation();
                    relation.setRelationType(validateRelationType(relNode.path("type").asText("相关")));
                    relation.setWeight(BigDecimal.valueOf(relNode.path("weight").asDouble(1.0)));

                    KnowledgeNode sourceNode = nameToNode.get(sourceName);
                    KnowledgeNode targetNode = nameToNode.get(targetName);

                    if (sourceNode == null) {
                        sourceNode = new KnowledgeNode();
                        sourceNode.setNodeType("CONCEPT");
                        sourceNode.setName(sourceName);
                        sourceNode.setConfidence(BigDecimal.valueOf(0.7));
                        sourceNode.setImportance(5);
                        result.getNodes().add(sourceNode);
                        nameToNode.put(sourceName, sourceNode);
                    }

                    if (targetNode == null) {
                        targetNode = new KnowledgeNode();
                        targetNode.setNodeType("CONCEPT");
                        targetNode.setName(targetName);
                        targetNode.setConfidence(BigDecimal.valueOf(0.7));
                        targetNode.setImportance(5);
                        result.getNodes().add(targetNode);
                        nameToNode.put(targetName, targetNode);
                    }

                    relation.setSourceNode(sourceNode);
                    relation.setTargetNode(targetNode);
                    result.getRelations().add(relation);
                }
            }

        } catch (Exception e) {
            log.error("解析知识提取结果失败：{}", e.getMessage(), e);
        }

        return result;
    }

    private String extractJson(String text) {
        String jsonBlock = extractMarkdownJsonBlock(text);
        if (jsonBlock != null) {
            return jsonBlock;
        }

        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }

    private String extractMarkdownJsonBlock(String text) {
        int codeStart = text.indexOf("```json");
        if (codeStart < 0) {
            codeStart = text.indexOf("```");
        }
        if (codeStart >= 0) {
            int jsonStart = text.indexOf('{', codeStart);
            int jsonEnd = text.lastIndexOf('}');
            if (jsonStart >= 0 && jsonEnd > jsonStart) {
                return text.substring(jsonStart, jsonEnd + 1);
            }
        }
        return null;
    }

    private String validateNodeType(String type) {
        Set<String> validTypes = new HashSet<>(Arrays.asList(
            "USER", "PROJECT", "TECHNOLOGY", "CONCEPT", "TASK", "ERROR", "SOLUTION", "PREFERENCE",
            "CHARACTER", "WORK"
        ));
        return validTypes.contains(type) ? type : "CONCEPT";
    }

    private String validateRelationType(String type) {
        if (type == null || type.trim().isEmpty()) {
            return "相关";
        }
        String trimmed = type.trim();
        // 限制最多10个字符（中文字符）
        if (trimmed.length() > 10) {
            trimmed = trimmed.substring(0, 10);
        }
        return trimmed;
    }

    private String callLLMNonStream(String prompt, AIModelConfig modelConfig) throws Exception {
        List<Map<String, Object>> requestMessages = new ArrayList<>();

        Map<String, Object> systemMsg = new HashMap<>();
        systemMsg.put("role", "system");
        systemMsg.put("content", "你是一个知识提取助手。请严格按照要求的 JSON 格式返回结果，不要添加额外的解释。");
        requestMessages.add(systemMsg);

        Map<String, Object> userMsg = new HashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", prompt);
        requestMessages.add(userMsg);

        LLMRequest request = LLMRequest.fromConfig(modelConfig.getModelId(), modelConfig.getApiUrl(), modelConfig.getApiKey())
                .toBuilder()
                .messages(requestMessages)
                .temperature(0.3)
                .maxTokens(4096)
                .build();

        log.info("[KnowledgeExtractor] 调用 LLM: model={}", modelConfig.getModelId());

        LLMCompleteResponse response = llmClient.chatComplete(request);
        if (!response.isSuccess()) {
            throw new RuntimeException("LLM API returned error: " + (response.getError() != null ? response.getError() : "unknown"));
        }

        String content = response.getContent();
        if (content == null || content.trim().isEmpty()) {
            log.warn("[KnowledgeExtractor] LLM content 为空！model={}", modelConfig.getModelId());
            return null;
        }

        return content;
    }

    @Data
    public static class ExtractionResult {
        private List<KnowledgeNode> nodes = new ArrayList<>();
        private List<KnowledgeRelation> relations = new ArrayList<>();
    }
}
