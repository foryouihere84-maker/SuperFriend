package com.superfriend.superfriend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Slf4j
@Service
public class ReflectionService {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    @Lazy
    private AIModelConfigService modelConfigService;

    @Autowired
    private RestTemplate restTemplate;

    @Data
    public static class ReflectionContext {
        private String userRequest;
        private String assistantResponse;
        private List<ToolExecutionRecord> toolExecutions;
        private int iterationCount;
        private String previousThoughts;
    }

    @Data
    public static class ToolExecutionRecord {
        private String toolName;
        private boolean success;
        private String resultSummary;
        private long executionTimeMs;
    }

    @Data
    public static class ReflectionResult {
        private boolean needsCorrection;
        private boolean needsMoreInfo;
        private double confidenceScore;
        private String correctionHint;
        private List<String> missingInfo;
        private List<String> suggestedActions;
        private String reasoning;
        private ReflectionAction action;

        public enum ReflectionAction {
            CONTINUE,
            CORRECT,
            REQUEST_INFO,
            ABANDON
        }
    }

    public ReflectionResult reflect(ReflectionContext context, String model, Long userId) {
        try {
            AIModelConfigService.ResolvedConfig config = modelConfigService.resolveModelConfigWithKey(model, userId);
            if (config == null) {
                return createDefaultResult("无法获取模型配置");
            }

            String reflectionPrompt = buildReflectionPrompt(context);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", config.getModelId());
            
            List<Map<String, String>> messages = new ArrayList<>();
            Map<String, String> systemMsg = new HashMap<>();
            systemMsg.put("role", "system");
            systemMsg.put("content", getReflectionSystemPrompt());
            messages.add(systemMsg);
            
            Map<String, String> userMsg = new HashMap<>();
            userMsg.put("role", "user");
            userMsg.put("content", reflectionPrompt);
            messages.add(userMsg);
            
            requestBody.put("messages", messages);
            requestBody.put("temperature", 0.3);
            requestBody.put("max_tokens", 2000);

            String response = callLLM(config.getApiUrl(), config.getApiKey(), requestBody);

            return parseReflectionResponse(response);

        } catch (Exception e) {
            log.error("Reflection 失败: {}", e.getMessage());
            return createDefaultResult("Reflection 异常: " + e.getMessage());
        }
    }

    private String getReflectionSystemPrompt() {
        return "你是一个专业的 AI 助手反思评估器。你的任务是评估 AI 助手的回答是否完整、准确，并决定下一步行动。\n\n" +
            "你需要返回一个 JSON 格式的评估结果：\n" +
            "{\n" +
            "    \"confidenceScore\": 0.0-1.0,\n" +
            "    \"needsCorrection\": true/false,\n" +
            "    \"needsMoreInfo\": true/false,\n" +
            "    \"correctionHint\": \"如果需要修正，说明修正方向\",\n" +
            "    \"missingInfo\": [\"缺失的信息列表\"],\n" +
            "    \"suggestedActions\": [\"建议的下一步行动\"],\n" +
            "    \"reasoning\": \"评估理由\",\n" +
            "    \"action\": \"CONTINUE/CORRECT/REQUEST_INFO/ABANDON\"\n" +
            "}\n\n" +
            "评估标准：\n" +
            "1. CONTINUE: 回答完整准确，可以直接返回给用户\n" +
            "2. CORRECT: 回答有错误或不完整，需要修正\n" +
            "3. REQUEST_INFO: 缺少关键信息，需要向用户询问\n" +
            "4. ABANDON: 任务无法完成，需要告知用户\n\n" +
            "注意：\n" +
            "- 如果工具执行失败，考虑是否需要尝试其他方法\n" +
            "- 如果回答包含\"我无法\"、\"我不能\"等，评估是否真的无法完成\n" +
            "- 如果回答过于简短或模糊，建议补充信息\n" +
            "- 如果迭代次数过多，考虑是否需要改变策略\n";
    }

    private String buildReflectionPrompt(ReflectionContext context) {
        StringBuilder sb = new StringBuilder();

        sb.append("=== 用户请求 ===\n");
        sb.append(context.getUserRequest()).append("\n\n");

        sb.append("=== AI 助手回答 ===\n");
        sb.append(context.getAssistantResponse() != null ? context.getAssistantResponse() : "(无回答)").append("\n\n");

        sb.append("=== 工具执行记录 ===\n");
        if (context.getToolExecutions() != null && !context.getToolExecutions().isEmpty()) {
            for (int i = 0; i < context.getToolExecutions().size(); i++) {
                ToolExecutionRecord record = context.getToolExecutions().get(i);
                sb.append(String.format("%d. %s: %s (%dms)\n",
                        i + 1,
                        record.getToolName(),
                        record.isSuccess() ? "成功" : "失败",
                        record.getExecutionTimeMs()));
                if (record.getResultSummary() != null) {
                    String summary = record.getResultSummary();
                    if (summary.length() > 200) {
                        summary = summary.substring(0, 200) + "...";
                    }
                    sb.append("   结果: ").append(summary).append("\n");
                }
            }
        } else {
            sb.append("(无工具调用)\n");
        }
        sb.append("\n");

        sb.append("=== 迭代信息 ===\n");
        sb.append("当前迭代次数: ").append(context.getIterationCount()).append("\n\n");

        if (context.getPreviousThoughts() != null && !context.getPreviousThoughts().isEmpty()) {
            sb.append("=== 之前的思考 ===\n");
            sb.append(context.getPreviousThoughts()).append("\n\n");
        }

        sb.append("请评估上述回答并返回 JSON 格式的结果。");

        return sb.toString();
    }

    private String callLLM(String apiUrl, String apiKey, Map<String, Object> requestBody) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + apiKey);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    buildFullApiUrl(apiUrl),
                    HttpMethod.POST,
                    entity,
                    String.class
            );

            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("LLM API 调用失败: " + response.getStatusCode());
            }

            return response.getBody();
        } catch (Exception e) {
            log.error("LLM 调用失败: {}", e.getMessage());
            throw new RuntimeException("LLM 调用失败: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private ReflectionResult parseReflectionResponse(String response) {
        try {
            Map<String, Object> responseMap = objectMapper.readValue(response, Map.class);
            List<Map<String, Object>> choices = (List<Map<String, Object>>) responseMap.get("choices");

            if (choices == null || choices.isEmpty()) {
                return createDefaultResult("LLM 返回空结果");
            }

            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
            String content = (String) message.get("content");

            String jsonContent = extractJson(content);

            // 尝试修复可能被截断的 JSON
            jsonContent = repairTruncatedJson(jsonContent);

            Map<String, Object> result = objectMapper.readValue(jsonContent, Map.class);

            ReflectionResult reflectionResult = new ReflectionResult();

            reflectionResult.setConfidenceScore(getDouble(result, "confidenceScore", 0.5));
            reflectionResult.setNeedsCorrection(getBoolean(result, "needsCorrection", false));
            reflectionResult.setNeedsMoreInfo(getBoolean(result, "needsMoreInfo", false));
            reflectionResult.setCorrectionHint(getString(result, "correctionHint", null));
            reflectionResult.setMissingInfo(getStringList(result, "missingInfo"));
            reflectionResult.setSuggestedActions(getStringList(result, "suggestedActions"));
            reflectionResult.setReasoning(getString(result, "reasoning", null));

            String actionStr = getString(result, "action", "CONTINUE");
            try {
                reflectionResult.setAction(ReflectionResult.ReflectionAction.valueOf(actionStr.toUpperCase()));
            } catch (IllegalArgumentException e) {
                reflectionResult.setAction(ReflectionResult.ReflectionAction.CONTINUE);
            }

            return reflectionResult;

        } catch (Exception e) {
            log.warn("解析 Reflection 响应失败: {}", e.getMessage());
            return createDefaultResult("解析失败: " + e.getMessage());
        }
    }

    private String extractJson(String content) {
        int start = content.indexOf('{');
        int end = content.lastIndexOf('}');

        if (start >= 0 && end > start) {
            return content.substring(start, end + 1);
        }

        return content;
    }

    /**
     * 尝试修复被截断的 JSON 字符串
     */
    private String repairTruncatedJson(String json) {
        if (json == null || json.isEmpty()) {
            return json;
        }

        // 检查是否被截断（没有正确闭合）
        int openBraces = 0;
        int openBrackets = 0;
        boolean inString = false;
        boolean escaped = false;

        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);

            if (escaped) {
                escaped = false;
                continue;
            }

            if (c == '\\') {
                escaped = true;
                continue;
            }

            if (c == '"') {
                inString = !inString;
                continue;
            }

            if (!inString) {
                if (c == '{') openBraces++;
                else if (c == '}') openBraces--;
                else if (c == '[') openBrackets++;
                else if (c == ']') openBrackets--;
            }
        }

        // 如果 JSON 完整，直接返回
        if (openBraces == 0 && openBrackets == 0 && !inString) {
            return json;
        }

        // 尝试修复截断的 JSON
        StringBuilder repaired = new StringBuilder(json);

        // 如果在字符串中间截断，先闭合字符串
        if (inString) {
            repaired.append("\"");
        }

        // 闭合未闭合的数组和对象
        for (int i = 0; i < openBrackets; i++) {
            repaired.append("]");
        }
        for (int i = 0; i < openBraces; i++) {
            repaired.append("}");
        }

        log.debug("修复截断的 JSON: 原长度={}, 修复后长度={}", json.length(), repaired.length());
        return repaired.toString();
    }

    private ReflectionResult createDefaultResult(String reason) {
        ReflectionResult result = new ReflectionResult();
        result.setNeedsCorrection(false);
        result.setNeedsMoreInfo(false);
        result.setConfidenceScore(0.5);
        result.setReasoning(reason);
        result.setAction(ReflectionResult.ReflectionAction.CONTINUE);
        return result;
    }

    private double getDouble(Map<String, Object> map, String key, double defaultValue) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return defaultValue;
    }

    private boolean getBoolean(Map<String, Object> map, String key, boolean defaultValue) {
        Object value = map.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return defaultValue;
    }

    private String getString(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        return value != null ? String.valueOf(value) : defaultValue;
    }

    @SuppressWarnings("unchecked")
    private List<String> getStringList(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof List) {
            List<String> result = new ArrayList<>();
            for (Object item : (List<?>) value) {
                result.add(String.valueOf(item));
            }
            return result;
        }
        return new ArrayList<>();
    }
    
    private String buildFullApiUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isEmpty()) {
            return "/chat/completions";
        }
        String url = baseUrl.trim();
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        if (url.endsWith("/chat/completions")) {
            return url;
        }
        return url + "/chat/completions";
    }
}
