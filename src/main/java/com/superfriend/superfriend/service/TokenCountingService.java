package com.superfriend.superfriend.service;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;

@Slf4j
@Service
public class TokenCountingService {

    private final Map<String, TokenizerConfig> tokenizerConfigs = new HashMap<>();

    private static final double DEFAULT_CHARS_PER_TOKEN = 4.0;
    private static final int MESSAGE_OVERHEAD_TOKENS = 4;
    private static final int NAME_FIELD_TOKENS = 1;
    private static final int SYSTEM_MESSAGE_OVERHEAD = 3;

    public TokenCountingService() {
        initTokenizerConfigs();
    }

    @Data
    public static class TokenizerConfig {
        private String modelFamily;
        private double charsPerToken;
        private int messageOverhead;
        private int nameFieldTokens;
        private Map<String, Double> specialTokenWeights;
        private boolean supportsFunctionCalling;
        private int functionCallOverhead;
    }

    @Data
    public static class TokenEstimate {
        private long inputTokens;
        private long estimatedOutputTokens;
        private long totalTokens;
        private double estimatedCost;
        private String model;
        private Map<String, Long> breakdown;
    }

    @Data
    public static class MessageTokenCount {
        private long roleTokens;
        private long contentTokens;
        private long nameTokens;
        private long totalTokens;
    }

    private void initTokenizerConfigs() {
        TokenizerConfig gpt4Config = new TokenizerConfig();
        gpt4Config.setModelFamily("gpt");
        gpt4Config.setCharsPerToken(4.0);
        gpt4Config.setMessageOverhead(4);
        gpt4Config.setNameFieldTokens(1);
        gpt4Config.setSupportsFunctionCalling(true);
        gpt4Config.setFunctionCallOverhead(10);
        gpt4Config.setSpecialTokenWeights(new HashMap<>());
        tokenizerConfigs.put("gpt-4", gpt4Config);
        tokenizerConfigs.put("gpt-4-turbo", gpt4Config);
        tokenizerConfigs.put("gpt-4o", gpt4Config);
        tokenizerConfigs.put("gpt-4o-mini", gpt4Config);
        tokenizerConfigs.put("gpt-3.5-turbo", gpt4Config);

        TokenizerConfig claudeConfig = new TokenizerConfig();
        claudeConfig.setModelFamily("claude");
        claudeConfig.setCharsPerToken(3.5);
        claudeConfig.setMessageOverhead(5);
        claudeConfig.setNameFieldTokens(1);
        claudeConfig.setSupportsFunctionCalling(true);
        claudeConfig.setFunctionCallOverhead(15);
        claudeConfig.setSpecialTokenWeights(new HashMap<>());
        tokenizerConfigs.put("claude-3-opus", claudeConfig);
        tokenizerConfigs.put("claude-3-sonnet", claudeConfig);
        tokenizerConfigs.put("claude-3-haiku", claudeConfig);
        tokenizerConfigs.put("claude-3.5-sonnet", claudeConfig);

        TokenizerConfig deepseekConfig = new TokenizerConfig();
        deepseekConfig.setModelFamily("deepseek");
        deepseekConfig.setCharsPerToken(2.5);
        deepseekConfig.setMessageOverhead(4);
        deepseekConfig.setNameFieldTokens(1);
        deepseekConfig.setSupportsFunctionCalling(true);
        deepseekConfig.setFunctionCallOverhead(12);
        deepseekConfig.setSpecialTokenWeights(new HashMap<>());
        tokenizerConfigs.put("deepseek-chat", deepseekConfig);
        tokenizerConfigs.put("deepseek-reasoner", deepseekConfig);

        TokenizerConfig geminiConfig = new TokenizerConfig();
        geminiConfig.setModelFamily("gemini");
        geminiConfig.setCharsPerToken(4.0);
        geminiConfig.setMessageOverhead(4);
        geminiConfig.setNameFieldTokens(1);
        geminiConfig.setSupportsFunctionCalling(true);
        geminiConfig.setFunctionCallOverhead(10);
        geminiConfig.setSpecialTokenWeights(new HashMap<>());
        tokenizerConfigs.put("gemini-pro", geminiConfig);
        tokenizerConfigs.put("gemini-1.5-pro", geminiConfig);

        TokenizerConfig qwenConfig = new TokenizerConfig();
        qwenConfig.setModelFamily("qwen");
        qwenConfig.setCharsPerToken(2.0);
        qwenConfig.setMessageOverhead(4);
        qwenConfig.setNameFieldTokens(1);
        qwenConfig.setSupportsFunctionCalling(true);
        qwenConfig.setFunctionCallOverhead(10);
        qwenConfig.setSpecialTokenWeights(new HashMap<>());
        tokenizerConfigs.put("qwen-max", qwenConfig);
        tokenizerConfigs.put("qwen-plus", qwenConfig);

        TokenizerConfig llamaConfig = new TokenizerConfig();
        llamaConfig.setModelFamily("llama");
        llamaConfig.setCharsPerToken(4.0);
        llamaConfig.setMessageOverhead(4);
        llamaConfig.setNameFieldTokens(1);
        llamaConfig.setSupportsFunctionCalling(false);
        llamaConfig.setFunctionCallOverhead(0);
        llamaConfig.setSpecialTokenWeights(new HashMap<>());
        tokenizerConfigs.put("llama-3-70b", llamaConfig);
        tokenizerConfigs.put("llama-3-8b", llamaConfig);

        TokenizerConfig defaultConfig = new TokenizerConfig();
        defaultConfig.setModelFamily("default");
        defaultConfig.setCharsPerToken(DEFAULT_CHARS_PER_TOKEN);
        defaultConfig.setMessageOverhead(MESSAGE_OVERHEAD_TOKENS);
        defaultConfig.setNameFieldTokens(NAME_FIELD_TOKENS);
        defaultConfig.setSupportsFunctionCalling(false);
        defaultConfig.setFunctionCallOverhead(0);
        defaultConfig.setSpecialTokenWeights(new HashMap<>());
        tokenizerConfigs.put("default", defaultConfig);
    }

    public TokenizerConfig getTokenizerConfig(String modelId) {
        if (modelId == null) {
            return tokenizerConfigs.get("default");
        }
        String normalizedModelId = modelId.toLowerCase();
        for (Map.Entry<String, TokenizerConfig> entry : tokenizerConfigs.entrySet()) {
            if (normalizedModelId.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return tokenizerConfigs.get("default");
    }

    public long countTokens(String text, String modelId) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        TokenizerConfig config = getTokenizerConfig(modelId);
        long baseTokens = (long) Math.ceil(text.length() / config.getCharsPerToken());
        long whitespaceTokens = countWhitespaceTokens(text);
        long specialTokens = countSpecialTokens(text, config);
        return baseTokens + whitespaceTokens + specialTokens;
    }

    private long countWhitespaceTokens(String text) {
        int consecutiveSpaces = 0;
        int extraTokens = 0;
        for (char c : text.toCharArray()) {
            if (c == ' ') {
                consecutiveSpaces++;
            } else {
                if (consecutiveSpaces > 1) {
                    extraTokens += (consecutiveSpaces - 1) / 2;
                }
                consecutiveSpaces = 0;
            }
        }
        return extraTokens;
    }

    private long countSpecialTokens(String text, TokenizerConfig config) {
        long specialTokens = 0;
        Map<String, Double> weights = config.getSpecialTokenWeights();
        if (weights != null) {
            for (Map.Entry<String, Double> entry : weights.entrySet()) {
                int count = countOccurrences(text, entry.getKey());
                specialTokens += (long) (count * entry.getValue());
            }
        }
        String[] codePatterns = {"{", "}", "[", "]", "(", ")", ";", "=>", "->", "::"};
        for (String pattern : codePatterns) {
            specialTokens += countOccurrences(text, pattern) / 2;
        }
        return specialTokens;
    }

    private int countOccurrences(String text, String pattern) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(pattern, index)) != -1) {
            count++;
            index += pattern.length();
        }
        return count;
    }

    public MessageTokenCount countMessageTokens(Map<String, Object> message, String modelId) {
        MessageTokenCount count = new MessageTokenCount();
        TokenizerConfig config = getTokenizerConfig(modelId);
        
        String role = (String) message.get("role");
        count.setRoleTokens(role != null ? role.length() / 4 + 1 : 0);
        
        String content = (String) message.get("content");
        if (content != null) {
            count.setContentTokens(countTokens(content, modelId));
        } else {
            count.setContentTokens(0);
        }
        
        String name = (String) message.get("name");
        count.setNameTokens(name != null ? config.getNameFieldTokens() : 0);
        
        count.setTotalTokens(count.getRoleTokens() + count.getContentTokens() + 
                            count.getNameTokens() + config.getMessageOverhead());
        
        return count;
    }

    public long countMessagesTokens(List<Map<String, Object>> messages, String modelId) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        TokenizerConfig config = getTokenizerConfig(modelId);
        long totalTokens = 0;
        for (Map<String, Object> message : messages) {
            MessageTokenCount count = countMessageTokens(message, modelId);
            totalTokens += count.getTotalTokens();
        }
        totalTokens += SYSTEM_MESSAGE_OVERHEAD;
        return totalTokens;
    }

    public long countFunctionTokens(List<Map<String, Object>> functions, String modelId) {
        if (functions == null || functions.isEmpty()) {
            return 0;
        }
        TokenizerConfig config = getTokenizerConfig(modelId);
        if (!config.isSupportsFunctionCalling()) {
            return 0;
        }
        long totalTokens = config.getFunctionCallOverhead();
        for (Map<String, Object> function : functions) {
            String json = convertToJson(function);
            totalTokens += countTokens(json, modelId);
        }
        return totalTokens;
    }

    private String convertToJson(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(entry.getKey()).append("\":");
            Object value = entry.getValue();
            if (value instanceof String) {
                sb.append("\"").append(value).append("\"");
            } else if (value instanceof Map) {
                sb.append(convertToJson((Map<String, Object>) value));
            } else {
                sb.append(value);
            }
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }

    public TokenEstimate estimateTokens(String modelId, 
                                        List<Map<String, Object>> messages,
                                        List<Map<String, Object>> functions,
                                        int maxOutputTokens) {
        TokenEstimate estimate = new TokenEstimate();
        estimate.setModel(modelId);
        estimate.setBreakdown(new HashMap<>());
        
        long messageTokens = countMessagesTokens(messages, modelId);
        long functionTokens = countFunctionTokens(functions, modelId);
        long inputTokens = messageTokens + functionTokens;
        
        estimate.setInputTokens(inputTokens);
        estimate.setEstimatedOutputTokens(maxOutputTokens);
        estimate.setTotalTokens(inputTokens + maxOutputTokens);
        
        estimate.getBreakdown().put("messages", messageTokens);
        estimate.getBreakdown().put("functions", functionTokens);
        estimate.getBreakdown().put("estimatedOutput", (long) maxOutputTokens);
        
        return estimate;
    }

    public TokenEstimate estimateReflectionCost(String modelId, 
                                                String userRequest,
                                                String currentThought,
                                                String action,
                                                String observation,
                                                List<String> previousSteps) {
        List<Map<String, Object>> messages = new ArrayList<>();
        
        Map<String, Object> systemMsg = new HashMap<>();
        systemMsg.put("role", "system");
        systemMsg.put("content", "你是一个专业的 AI Agent 执行步骤评估器。返回严格的 JSON 格式。");
        messages.add(systemMsg);
        
        StringBuilder userContent = new StringBuilder();
        userContent.append("=== 原始任务 ===\n").append(userRequest).append("\n\n");
        userContent.append("=== 当前思考 ===\n").append(currentThought != null ? currentThought : "").append("\n\n");
        userContent.append("=== 执行的动作 ===\n").append(action != null ? action : "").append("\n\n");
        userContent.append("=== 观察到的结果 ===\n").append(observation != null ? observation : "").append("\n\n");
        if (previousSteps != null && !previousSteps.isEmpty()) {
            userContent.append("=== 之前的步骤 ===\n");
            for (String step : previousSteps) {
                userContent.append(step).append("\n");
            }
        }
        userContent.append("\n请评估并返回 JSON。");
        
        Map<String, Object> userMsg = new HashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", userContent.toString());
        messages.add(userMsg);
        
        return estimateTokens(modelId, messages, null, 500);
    }

    public TokenEstimate estimateThoughtGenerationCost(String modelId,
                                                       String userRequest,
                                                       List<Map<String, Object>> history,
                                                       List<Map<String, Object>> tools,
                                                       String context) {
        List<Map<String, Object>> messages = new ArrayList<>();
        
        Map<String, Object> systemMsg = new HashMap<>();
        systemMsg.put("role", "system");
        systemMsg.put("content", buildThoughtSystemPrompt(tools));
        messages.add(systemMsg);
        
        if (history != null) {
            messages.addAll(history);
        }
        
        Map<String, Object> userMsg = new HashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", userRequest + (context != null ? "\n\n" + context : ""));
        messages.add(userMsg);
        
        return estimateTokens(modelId, messages, tools, 1000);
    }

    private String buildThoughtSystemPrompt(List<Map<String, Object>> tools) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一个智能助手，请按照结构化思维进行思考。\n");
        sb.append("返回 JSON 格式: {\"thought\": \"...\", \"confidence\": 0.0-1.0, \"next_action\": \"...\"}\n");
        if (tools != null && !tools.isEmpty()) {
            sb.append("可用工具: ");
            for (Map<String, Object> tool : tools) {
                String name = (String) tool.get("name");
                if (name != null) {
                    sb.append(name).append(", ");
                }
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    public boolean isWithinBudget(TokenEstimate estimate, 
                                  double remainingBudget,
                                  double inputPricePer1k,
                                  double outputPricePer1k) {
        double inputCost = (estimate.getInputTokens() / 1000.0) * inputPricePer1k;
        double outputCost = (estimate.getEstimatedOutputTokens() / 1000.0) * outputPricePer1k;
        double totalCost = inputCost + outputCost;
        estimate.setEstimatedCost(totalCost);
        return totalCost <= remainingBudget;
    }

    public long estimateStringTokens(String text) {
        return countTokens(text, "default");
    }

    public Map<String, Object> getTokenStats(String text, String modelId) {
        Map<String, Object> stats = new HashMap<>();
        stats.put("text", text.substring(0, Math.min(100, text.length())) + (text.length() > 100 ? "..." : ""));
        stats.put("textLength", text.length());
        stats.put("tokenCount", countTokens(text, modelId));
        stats.put("modelId", modelId);
        TokenizerConfig config = getTokenizerConfig(modelId);
        stats.put("charsPerToken", config.getCharsPerToken());
        stats.put("modelFamily", config.getModelFamily());
        return stats;
    }
}
