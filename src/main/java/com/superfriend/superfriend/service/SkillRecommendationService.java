package com.superfriend.superfriend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.superfriend.superfriend.agent.skill.*;
import com.superfriend.superfriend.dto.LLMCompleteResponse;
import com.superfriend.superfriend.dto.LLMRequest;
import com.superfriend.superfriend.entity.AIModelConfig;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class SkillRecommendationService {

    @Autowired
    private SkillRegistry skillRegistry;

    @Autowired
    private LLMClient llmClient;

    @Autowired
    private SkillService skillService;

    @Autowired
    @Lazy
    private AIModelConfigService modelConfigService;

    @Autowired
    private SkillActivationAnalyzer activationAnalyzer;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${skills.recommendation.threshold:0.5}")
    private double recommendationThreshold;

    @Value("${skills.recommendation.max-results:5}")
    private int maxRecommendations;

    @Value("${skills.recommendation.auto-execute-threshold:0.8}")
    private double autoExecuteThreshold;

    @Value("${skills.recommendation.use-ai:true}")
    private boolean useAIRecommendation;

    public List<SkillRecommendation> recommendSkills(String userMessage, Long userId) {
        return recommendSkills(userMessage, userId, null);
    }

    public List<SkillRecommendation> recommendSkills(String userMessage, Long userId, String modelName) {
        log.info("开始分析用户意图，推荐技能: {}{}", userMessage, modelName != null ? " (model=" + modelName + ")" : "");

        if (useAIRecommendation) {
            List<SkillRecommendation> aiRecommendations = recommendSkillsWithAI(userMessage, userId, modelName);
            if (aiRecommendations != null && !aiRecommendations.isEmpty()) {
                return aiRecommendations;
            }
            log.info("AI 推荐未返回结果，回退到规则匹配");
        }

        return recommendSkillsWithRules(userMessage, userId);
    }

    public List<SkillRecommendation> recommendSkillsWithAI(String userMessage, Long userId) {
        return recommendSkillsWithAI(userMessage, userId, null);
    }

    public List<SkillRecommendation> recommendSkillsWithAI(String userMessage, Long userId, String modelName) {
        log.info("使用 AI 分析选择技能: {}", userMessage);

        try {
            AIModelConfig modelConfig = modelConfigService.resolveModelConfig(modelName, userId);
            if (modelConfig == null) {
                log.warn("用户 {} 没有配置模型，跳过 AI 技能推荐", userId);
                return null;
            }
            
            List<Skill> allSkills = getAvailableSkills(userId);
            if (allSkills.isEmpty()) {
                log.warn("没有可用的技能");
                return Collections.emptyList();
            }
            
            String skillCatalog = buildSkillCatalogPrompt(allSkills);
            String systemPrompt = buildAISkillSelectionPrompt(skillCatalog);
            String userPrompt = buildSkillSelectionUserPrompt(userMessage);
            
            log.debug("技能目录:\n{}", skillCatalog);
            
            String aiResponse = callAI(modelConfig, systemPrompt, userPrompt, 2000);
            log.debug("AI 响应: {}", aiResponse);
            
            return parseAISkillRecommendations(aiResponse, allSkills);
            
        } catch (Exception e) {
            log.error("AI 技能推荐失败: {}", e.getMessage(), e);
            return null;
        }
    }
    
    public List<SkillRecommendation> recommendSkillsWithRules(String userMessage, Long userId) {
        List<SkillRecommendation> recommendations = new ArrayList<>();
        
        List<Skill> allSkills = getAvailableSkills(userId);
        
        for (Skill skill : allSkills) {
            SkillRecommendation recommendation = analyzeSkillMatch(skill, userMessage);
            if (recommendation != null && recommendation.getScore() >= recommendationThreshold) {
                recommendations.add(recommendation);
            }
        }
        
        recommendations.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
        
        if (recommendations.size() > maxRecommendations) {
            recommendations = recommendations.subList(0, maxRecommendations);
        }
        
        log.info("规则匹配推荐了 {} 个技能", recommendations.size());
        return recommendations;
    }
    
    private List<Skill> getAvailableSkills(Long userId) {
        List<Skill> allSkills = new ArrayList<>(skillRegistry.getAllSkills());
        
        if (userId != null) {
            List<com.superfriend.superfriend.entity.UserSkill> userSkills = skillService.findEnabledUserSkillsByUserId(userId);
            for (com.superfriend.superfriend.entity.UserSkill userSkill : userSkills) {
                com.superfriend.superfriend.entity.Skill dbSkill = skillService.findById(userSkill.getSkillId());
                if (dbSkill != null && allSkills.stream().noneMatch(s -> s.getName().equals(dbSkill.getName()))) {
                    Skill agentSkill = convertToAgentSkill(dbSkill);
                    if (agentSkill != null) {
                        allSkills.add(agentSkill);
                    }
                }
            }
        }
        
        return allSkills;
    }
    
    private String buildSkillCatalogPrompt(List<Skill> skills) {
        StringBuilder sb = new StringBuilder();
        
        Map<String, List<Skill>> skillsByCategory = skills.stream()
            .filter(s -> s.getCategory() != null)
            .collect(Collectors.groupingBy(Skill::getCategory));
        
        List<Skill> uncategorizedSkills = skills.stream()
            .filter(s -> s.getCategory() == null)
            .collect(Collectors.toList());
        
        sb.append("✅ 你有 ").append(skills.size()).append(" 个可用技能：\n\n");
        
        for (Map.Entry<String, List<Skill>> entry : skillsByCategory.entrySet()) {
            String category = entry.getKey();
            List<Skill> categorySkills = entry.getValue();
            
            sb.append("**").append(category).append("** (")
              .append(categorySkills.size()).append(" 个技能):\n");
            
            for (Skill skill : categorySkills) {
                sb.append("  - **").append(skill.getName()).append("**");
                
                String description = skill.getDescription();
                if (description != null && !description.isEmpty()) {
                    String shortDesc = description.split("\n")[0];
                    if (shortDesc.length() > 100) {
                        shortDesc = shortDesc.substring(0, 100) + "...";
                    }
                    sb.append(": ").append(shortDesc);
                }
                
                sb.append("\n");
            }
            sb.append("\n");
        }
        
        if (!uncategorizedSkills.isEmpty()) {
            sb.append("**其他** (").append(uncategorizedSkills.size()).append(" 个技能):\n");
            for (Skill skill : uncategorizedSkills) {
                sb.append("  - **").append(skill.getName()).append("**\n");
            }
            sb.append("\n");
        }
        
        return sb.toString();
    }
    
    private String buildAISkillSelectionPrompt(String skillCatalog) {
        StringBuilder sb = new StringBuilder();
        
        sb.append("你是一个智能技能选择助手。根据用户的请求，从可用技能列表中选择最合适的技能。\n\n");
        
        sb.append("⚠️ 重要规则：\n");
        sb.append("1. 只能从下面列出的技能中选择，不要推荐不存在的技能\n");
        sb.append("2. 根据用户需求选择最匹配的技能\n");
        sb.append("3. 如果需要多个技能配合，按执行顺序排列\n");
        sb.append("4. 如果没有合适的技能，返回空列表\n");
        sb.append("5. 必须以 JSON 格式返回结果\n\n");
        
        sb.append(skillCatalog);
        
        sb.append("\n📝 返回格式（JSON）：\n");
        sb.append("```json\n");
        sb.append("{\n");
        sb.append("  \"selected_skills\": [\n");
        sb.append("    {\n");
        sb.append("      \"name\": \"技能名称\",\n");
        sb.append("      \"confidence\": 0.95,\n");
        sb.append("      \"reason\": \"选择原因\",\n");
        sb.append("      \"parameters\": {\n");
        sb.append("        \"参数名\": \"从用户消息中提取的参数值\"\n");
        sb.append("      }\n");
        sb.append("    }\n");
        sb.append("  ],\n");
        sb.append("  \"analysis\": \"对用户意图的分析\",\n");
        sb.append("  \"needs_clarification\": false,\n");
        sb.append("  \"clarification_question\": \"如果需要澄清，在这里提问\"\n");
        sb.append("}\n");
        sb.append("```\n");
        
        return sb.toString();
    }
    
    private String buildSkillSelectionUserPrompt(String userMessage) {
        StringBuilder sb = new StringBuilder();
        sb.append("用户请求：").append(userMessage).append("\n\n");
        sb.append("请分析用户意图，选择合适的技能来处理这个请求。\n");
        sb.append("如果需要从用户消息中提取参数，请在 parameters 字段中提供。\n");
        sb.append("直接返回 JSON，不要有其他说明文字。");
        return sb.toString();
    }
    
    private List<SkillRecommendation> parseAISkillRecommendations(String aiResponse, List<Skill> availableSkills) {
        List<SkillRecommendation> recommendations = new ArrayList<>();
        
        try {
            String jsonStr = aiResponse;
            if (aiResponse.contains("```json")) {
                jsonStr = aiResponse.substring(aiResponse.indexOf("```json") + 7, aiResponse.lastIndexOf("```"));
            } else if (aiResponse.contains("```")) {
                jsonStr = aiResponse.substring(aiResponse.indexOf("```") + 3, aiResponse.lastIndexOf("```"));
            }
            
            JsonNode root = objectMapper.readTree(jsonStr.trim());
            
            String analysis = root.path("analysis").asText("");
            log.info("AI 分析: {}", analysis);
            
            boolean needsClarification = root.path("needs_clarification").asBoolean(false);
            if (needsClarification) {
                String question = root.path("clarification_question").asText("");
                log.info("需要澄清: {}", question);
            }
            
            JsonNode selectedSkills = root.path("selected_skills");
            if (selectedSkills.isArray()) {
                Map<String, Skill> skillMap = availableSkills.stream()
                    .collect(Collectors.toMap(Skill::getName, s -> s, (a, b) -> a));
                
                for (JsonNode skillNode : selectedSkills) {
                    String skillName = skillNode.path("name").asText("");
                    double confidence = skillNode.path("confidence").asDouble(0.5);
                    String reason = skillNode.path("reason").asText("");
                    
                    Skill skill = skillMap.get(skillName);
                    if (skill == null) {
                        log.warn("AI 推荐了不存在的技能: {}", skillName);
                        continue;
                    }
                    
                    SkillRecommendation recommendation = new SkillRecommendation();
                    recommendation.setSkillName(skillName);
                    recommendation.setDescription(skill.getDescription());
                    recommendation.setCategory(skill.getCategory());
                    recommendation.setScore(confidence);
                    recommendation.setConfidence(confidence >= 0.8 ? "HIGH" : confidence >= 0.5 ? "MEDIUM" : "LOW");
                    recommendation.setAutoExecute(confidence >= autoExecuteThreshold);
                    recommendation.setReason(reason);
                    
                    Map<String, Object> parameters = new HashMap<>();
                    JsonNode paramsNode = skillNode.path("parameters");
                    if (paramsNode.isObject()) {
                        Iterator<Map.Entry<String, JsonNode>> fields = paramsNode.fields();
                        while (fields.hasNext()) {
                            Map.Entry<String, JsonNode> entry = fields.next();
                            parameters.put(entry.getKey(), extractValue(entry.getValue()));
                        }
                    }
                    recommendation.setSuggestedParameters(parameters);
                    
                    recommendations.add(recommendation);
                    log.info("AI 推荐技能: {} (置信度: {}, 原因: {})", skillName, confidence, reason);
                }
            }
            
            recommendations.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
            
            if (recommendations.size() > maxRecommendations) {
                recommendations = recommendations.subList(0, maxRecommendations);
            }
            
        } catch (Exception e) {
            log.error("解析 AI 技能推荐响应失败: {}", e.getMessage());
        }
        
        return recommendations;
    }
    
    private Object extractValue(JsonNode node) {
        if (node.isTextual()) {
            return node.asText();
        } else if (node.isInt()) {
            return node.asInt();
        } else if (node.isLong()) {
            return node.asLong();
        } else if (node.isDouble()) {
            return node.asDouble();
        } else if (node.isBoolean()) {
            return node.asBoolean();
        } else {
            return node.toString();
        }
    }
    
    private Skill convertToAgentSkill(com.superfriend.superfriend.entity.Skill dbSkill) {
        try {
            return new DatabaseSkill(dbSkill);
        } catch (Exception e) {
            log.warn("转换技能失败: {}", dbSkill.getName(), e);
            return null;
        }
    }

    public SkillRecommendation analyzeSkillMatch(Skill skill, String userMessage) {
        try {
            SkillActivationAnalyzer.ActivationResult activationResult = 
                activationAnalyzer.analyzeActivation(skill, userMessage);
            
            if (!activationResult.isActivated()) {
                return null;
            }
            
            SkillRecommendation recommendation = new SkillRecommendation();
            recommendation.setSkillName(skill.getName());
            recommendation.setDescription(skill.getDescription());
            recommendation.setCategory(skill.getCategory());
            recommendation.setScore(activationResult.getScore());
            recommendation.setConfidence(activationResult.getConfidence());
            recommendation.setAutoExecute(activationResult.getScore() >= autoExecuteThreshold);
            
            Map<String, Object> parameters = extractParameters(skill, userMessage);
            recommendation.setSuggestedParameters(parameters);
            
            return recommendation;
        } catch (Exception e) {
            log.error("分析技能匹配失败: {} - {}", skill.getName(), e.getMessage());
            return null;
        }
    }

    public AIIntentAnalysis analyzeIntentWithAI(String userMessage, Long userId) {
        return analyzeIntentWithAI(userMessage, userId, null);
    }

    public AIIntentAnalysis analyzeIntentWithAI(String userMessage, Long userId, String modelName) {
        log.info("使用 AI 分析用户意图: {}", userMessage);

        try {
            AIModelConfig modelConfig = modelConfigService.resolveModelConfig(modelName, userId);
            if (modelConfig == null) {
                log.warn("未找到可用的模型配置，使用规则匹配");
                return analyzeIntentWithRules(userMessage);
            }
            
            String systemPrompt = buildIntentAnalysisPrompt();
            String userPrompt = buildUserPrompt(userMessage);
            
            String aiResponse = callAI(modelConfig, systemPrompt, userPrompt);
            
            return parseAIResponse(aiResponse, userMessage);
            
        } catch (Exception e) {
            log.error("AI 意图分析失败，回退到规则匹配: {}", e.getMessage());
            return analyzeIntentWithRules(userMessage);
        }
    }

    private AIIntentAnalysis analyzeIntentWithRules(String userMessage) {
        AIIntentAnalysis analysis = new AIIntentAnalysis();
        analysis.setUserMessage(userMessage);
        analysis.setIntent(detectIntent(userMessage));
        analysis.setKeywords(extractKeywords(userMessage));
        analysis.setRecommendedSkills(recommendSkills(userMessage, null));
        return analysis;
    }

    private String detectIntent(String message) {
        String lowerMessage = message.toLowerCase();
        
        if (lowerMessage.contains("分析") || lowerMessage.contains("analyze")) {
            return "ANALYSIS";
        } else if (lowerMessage.contains("创建") || lowerMessage.contains("create") || lowerMessage.contains("生成")) {
            return "CREATION";
        } else if (lowerMessage.contains("修改") || lowerMessage.contains("update") || lowerMessage.contains("编辑")) {
            return "MODIFICATION";
        } else if (lowerMessage.contains("查询") || lowerMessage.contains("search") || lowerMessage.contains("查找")) {
            return "QUERY";
        } else if (lowerMessage.contains("删除") || lowerMessage.contains("delete") || lowerMessage.contains("移除")) {
            return "DELETION";
        } else if (lowerMessage.contains("解释") || lowerMessage.contains("explain") || lowerMessage.contains("说明")) {
            return "EXPLANATION";
        }
        
        return "GENERAL";
    }

    private List<String> extractKeywords(String message) {
        String[] words = message.split("\\s+");
        return Arrays.stream(words)
            .filter(w -> w.length() > 2)
            .map(String::toLowerCase)
            .distinct()
            .collect(Collectors.toList());
    }

    private Map<String, Object> extractParameters(Skill skill, String userMessage) {
        Map<String, Object> parameters = new HashMap<>();
        
        Map<String, Object> skillParams = skill.getParameters();
        if (skillParams == null || skillParams.isEmpty()) {
            return parameters;
        }
        
        for (Map.Entry<String, Object> entry : skillParams.entrySet()) {
            String paramName = entry.getKey();
            Object paramDef = entry.getValue();
            
            if (paramDef instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> def = (Map<String, Object>) paramDef;
                Object defaultValue = def.get("default");
                if (defaultValue != null) {
                    parameters.put(paramName, defaultValue);
                }
            }
        }
        
        return parameters;
    }

    private String buildIntentAnalysisPrompt() {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是一个意图分析专家。请分析用户的消息，识别用户的意图并推荐合适的技能。\n\n");
        prompt.append("请以 JSON 格式返回分析结果，包含以下字段：\n");
        prompt.append("- intent: 用户的主要意图（ANALYSIS, CREATION, MODIFICATION, QUERY, DELETION, EXPLANATION, GENERAL）\n");
        prompt.append("- keywords: 关键词列表\n");
        prompt.append("- confidence: 置信度（0-1）\n");
        prompt.append("- suggestedSkillType: 建议的技能类型\n");
        prompt.append("- reasoning: 推理过程\n");
        return prompt.toString();
    }

    private String buildUserPrompt(String userMessage) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("用户消息：").append(userMessage).append("\n\n");
        prompt.append("请分析这条消息的意图。");
        return prompt.toString();
    }

    private String callAI(AIModelConfig config, String systemPrompt, String userPrompt) throws Exception {
        return callAI(config, systemPrompt, userPrompt, 500);
    }

    private String callAI(AIModelConfig config, String systemPrompt, String userPrompt, int maxTokens) throws Exception {
        List<Map<String, Object>> messages = new ArrayList<>();
        Map<String, Object> sysMsg = new HashMap<>();
        sysMsg.put("role", "system");
        sysMsg.put("content", systemPrompt);
        messages.add(sysMsg);
        Map<String, Object> userMsg = new HashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", userPrompt);
        messages.add(userMsg);

        LLMRequest request = LLMRequest.fromConfig(config.getModelId(), config.getApiUrl(), config.getApiKey())
                .toBuilder()
                .messages(messages)
                .temperature(0.3)
                .maxTokens(maxTokens)
                .build();

        log.info("[SkillRecommendation] 调用 LLM (chatComplete): model={}, maxTokens={}", config.getModelId(), maxTokens);

        LLMCompleteResponse response = llmClient.chatComplete(request);
        if (!response.isSuccess()) {
            throw new RuntimeException("AI API 调用失败: " + (response.getError() != null ? response.getError() : "unknown error"));
        }
        if (!response.hasContent()) {
            throw new RuntimeException("AI 返回空内容");
        }
        return response.getContent();
    }

    private AIIntentAnalysis parseAIResponse(String aiResponse, String userMessage) {
        AIIntentAnalysis analysis = new AIIntentAnalysis();
        analysis.setUserMessage(userMessage);
        
        try {
            String jsonStr = aiResponse;
            if (aiResponse.contains("```json")) {
                jsonStr = aiResponse.substring(aiResponse.indexOf("```json") + 7, aiResponse.lastIndexOf("```"));
            } else if (aiResponse.contains("```")) {
                jsonStr = aiResponse.substring(aiResponse.indexOf("```") + 3, aiResponse.lastIndexOf("```"));
            }
            
            JsonNode json = objectMapper.readTree(jsonStr.trim());
            
            analysis.setIntent(json.path("intent").asText("GENERAL"));
            analysis.setConfidence(json.path("confidence").asDouble(0.5));
            analysis.setReasoning(json.path("reasoning").asText(""));
            
            List<String> keywords = new ArrayList<>();
            JsonNode keywordsNode = json.path("keywords");
            if (keywordsNode.isArray()) {
                for (JsonNode kw : keywordsNode) {
                    keywords.add(kw.asText());
                }
            }
            analysis.setKeywords(keywords);
            
            analysis.setRecommendedSkills(recommendSkills(userMessage, null));
            
        } catch (Exception e) {
            log.error("解析 AI 响应失败: {}", e.getMessage());
            return analyzeIntentWithRules(userMessage);
        }
        
        return analysis;
    }

    @Data
    public static class SkillRecommendation {
        private String skillName;
        private String description;
        private String category;
        private double score;
        private String confidence;
        private boolean autoExecute;
        private Map<String, Object> suggestedParameters;
        private String reason;
    }

    @Data
    public static class AIIntentAnalysis {
        private String userMessage;
        private String intent;
        private List<String> keywords;
        private double confidence;
        private String reasoning;
        private String suggestedSkillType;
        private List<SkillRecommendation> recommendedSkills;
    }
}
