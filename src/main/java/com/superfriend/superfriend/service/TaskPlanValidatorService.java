package com.superfriend.superfriend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.superfriend.superfriend.dto.TaskPlanDTO;
import com.superfriend.superfriend.dto.TaskStepDTO;
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
public class TaskPlanValidatorService {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    @Lazy
    private AIModelConfigService modelConfigService;

    @Autowired
    private RestTemplate restTemplate;

    @Data
    public static class StepValidationContext {
        private String stepDescription;
        private String expectedOutcome;
        private String actualResult;
        private String toolUsed;
        private boolean toolSuccess;
        private String errorMessage;
        private int stepNumber;
        private int totalSteps;
        private String originalRequest;
    }

    @Data
    public static class StepValidationResult {
        private boolean valid;
        private double confidenceScore;
        private String reason;
        private ValidationAction action;
        private List<String> suggestedFixes;
        private String adjustedStepDescription;
        private List<String> additionalStepsNeeded;

        public enum ValidationAction {
            CONTINUE,
            RETRY_STEP,
            SKIP_STEP,
            ADJUST_PLAN,
            ABORT_PLAN
        }
    }

    @Data
    public static class PlanAdjustment {
        private boolean needsAdjustment;
        private List<AdjustedStep> adjustedSteps;
        private List<String> newSteps;
        private String reason;

        @Data
        public static class AdjustedStep {
            private int stepNumber;
            private String originalDescription;
            private String newDescription;
            private String newTool;
        }
    }

    public StepValidationResult validateStepResult(StepValidationContext context, String model, Long userId) {
        StepValidationResult result = new StepValidationResult();
        result.setValid(true);
        result.setConfidenceScore(1.0);
        result.setAction(StepValidationResult.ValidationAction.CONTINUE);
        result.setSuggestedFixes(new ArrayList<>());

        if (context.getActualResult() == null || context.getActualResult().trim().isEmpty()) {
            result.setValid(false);
            result.setConfidenceScore(0.0);
            result.setReason("步骤结果为空");
            result.setAction(StepValidationResult.ValidationAction.RETRY_STEP);
            result.getSuggestedFixes().add("重新执行该步骤");
            return result;
        }

        if (context.getErrorMessage() != null && !context.getErrorMessage().isEmpty()) {
            result.setValid(false);
            result.setConfidenceScore(0.3);
            result.setReason("步骤执行出错: " + context.getErrorMessage());
            result.setAction(determineActionFromError(context.getErrorMessage()));
            result.getSuggestedFixes().add("检查错误原因并修复");
            return result;
        }

        if (context.getToolUsed() != null && !context.getToolUsed().isEmpty() && !context.isToolSuccess()) {
            result.setValid(false);
            result.setConfidenceScore(0.4);
            result.setReason("工具调用失败: " + context.getToolUsed());
            result.setAction(StepValidationResult.ValidationAction.RETRY_STEP);
            result.getSuggestedFixes().add("尝试使用替代工具");
            return result;
        }

        try {
            StepValidationResult llmResult = validateWithLLM(context, model, userId);
            if (llmResult != null) {
                return llmResult;
            }
        } catch (Exception e) {
            log.warn("LLM 验证失败，使用规则验证: {}", e.getMessage());
        }

        return result;
    }

    private StepValidationResult validateWithLLM(StepValidationContext context, String model, Long userId) {
        try {
            AIModelConfigService.ResolvedConfig config = modelConfigService.resolveModelConfigWithKey(model, userId);
            if (config == null) {
                return null;
            }

            String prompt = buildValidationPrompt(context);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", config.getModelId());
            
            List<Map<String, String>> messages = new ArrayList<>();
            Map<String, String> systemMsg = new HashMap<>();
            systemMsg.put("role", "system");
            systemMsg.put("content", getValidationSystemPrompt());
            messages.add(systemMsg);
            
            Map<String, String> userMsg = new HashMap<>();
            userMsg.put("role", "user");
            userMsg.put("content", prompt);
            messages.add(userMsg);
            
            requestBody.put("messages", messages);
            requestBody.put("temperature", 0.2);
            requestBody.put("max_tokens", 800);

            String response = callLLM(config.getApiUrl(), config.getApiKey(), requestBody);

            return parseValidationResponse(response);

        } catch (Exception e) {
            log.error("LLM 验证调用失败: {}", e.getMessage());
            return null;
        }
    }

    private String getValidationSystemPrompt() {
        return "你是一个任务步骤执行结果验证器。你需要评估步骤执行结果是否达到了预期目标。\n\n" +
            "返回 JSON 格式：\n" +
            "{\n" +
            "    \"valid\": true/false,\n" +
            "    \"confidenceScore\": 0.0-1.0,\n" +
            "    \"reason\": \"验证理由\",\n" +
            "    \"action\": \"CONTINUE/RETRY_STEP/SKIP_STEP/ADJUST_PLAN/ABORT_PLAN\",\n" +
            "    \"suggestedFixes\": [\"建议的修复措施\"],\n" +
            "    \"adjustedStepDescription\": \"如果需要调整，新的步骤描述\",\n" +
            "    \"additionalStepsNeeded\": [\"如果需要额外步骤，描述它们\"]\n" +
            "}\n\n" +
            "判断标准：\n" +
            "- CONTINUE: 结果符合预期，可以继续下一步\n" +
            "- RETRY_STEP: 结果不完整或有错误，需要重试当前步骤\n" +
            "- SKIP_STEP: 当前步骤非关键且无法完成，可以跳过\n" +
            "- ADJUST_PLAN: 需要调整后续计划\n" +
            "- ABORT_PLAN: 任务无法完成，需要终止\n\n" +
            "注意：\n" +
            "- 如果结果是\"未找到\"、\"无数据\"等，判断是否影响整体任务\n" +
            "- 如果工具返回错误信息，评估是否可以尝试其他方法\n" +
            "- 如果步骤是关键步骤，失败时应建议重试或调整计划\n";
    }

    private String buildValidationPrompt(StepValidationContext context) {
        StringBuilder sb = new StringBuilder();

        sb.append("=== 原始任务 ===\n");
        sb.append(context.getOriginalRequest()).append("\n\n");

        sb.append("=== 当前步骤 ===\n");
        sb.append(String.format("步骤 %d/%d: %s\n\n", 
                context.getStepNumber(), context.getTotalSteps(), context.getStepDescription()));

        if (context.getExpectedOutcome() != null && !context.getExpectedOutcome().isEmpty()) {
            sb.append("=== 预期结果 ===\n");
            sb.append(context.getExpectedOutcome()).append("\n\n");
        }

        sb.append("=== 实际结果 ===\n");
        String result = context.getActualResult();
        if (result != null && result.length() > 1000) {
            result = result.substring(0, 1000) + "...(已截断)";
        }
        sb.append(result != null ? result : "(无结果)").append("\n\n");

        if (context.getToolUsed() != null && !context.getToolUsed().isEmpty()) {
            sb.append("=== 工具信息 ===\n");
            sb.append("使用工具: ").append(context.getToolUsed()).append("\n");
            sb.append("工具状态: ").append(context.isToolSuccess() ? "成功" : "失败").append("\n\n");
        }

        if (context.getErrorMessage() != null && !context.getErrorMessage().isEmpty()) {
            sb.append("=== 错误信息 ===\n");
            sb.append(context.getErrorMessage()).append("\n\n");
        }

        sb.append("请验证此步骤的执行结果并返回 JSON 格式的验证结果。");

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
    private StepValidationResult parseValidationResponse(String response) {
        try {
            Map<String, Object> responseMap = objectMapper.readValue(response, Map.class);
            List<Map<String, Object>> choices = (List<Map<String, Object>>) responseMap.get("choices");

            if (choices == null || choices.isEmpty()) {
                return null;
            }

            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
            String content = (String) message.get("content");

            String jsonContent = extractJson(content);

            Map<String, Object> result = objectMapper.readValue(jsonContent, Map.class);

            StepValidationResult validationResult = new StepValidationResult();

            validationResult.setValid(getBoolean(result, "valid", true));
            validationResult.setConfidenceScore(getDouble(result, "confidenceScore", 0.8));
            validationResult.setReason(getString(result, "reason", null));

            String actionStr = getString(result, "action", "CONTINUE");
            try {
                validationResult.setAction(StepValidationResult.ValidationAction.valueOf(actionStr.toUpperCase()));
            } catch (IllegalArgumentException e) {
                validationResult.setAction(StepValidationResult.ValidationAction.CONTINUE);
            }

            validationResult.setSuggestedFixes(getStringList(result, "suggestedFixes"));
            validationResult.setAdjustedStepDescription(getString(result, "adjustedStepDescription", null));
            validationResult.setAdditionalStepsNeeded(getStringList(result, "additionalStepsNeeded"));

            return validationResult;

        } catch (Exception e) {
            log.warn("解析验证响应失败: {}", e.getMessage());
            return null;
        }
    }

    private StepValidationResult.ValidationAction determineActionFromError(String errorMessage) {
        if (errorMessage == null) {
            return StepValidationResult.ValidationAction.CONTINUE;
        }

        String lowerError = errorMessage.toLowerCase();

        if (lowerError.contains("permission") || lowerError.contains("权限")) {
            return StepValidationResult.ValidationAction.SKIP_STEP;
        }

        if (lowerError.contains("not found") || lowerError.contains("未找到") || lowerError.contains("不存在")) {
            return StepValidationResult.ValidationAction.ADJUST_PLAN;
        }

        if (lowerError.contains("timeout") || lowerError.contains("超时")) {
            return StepValidationResult.ValidationAction.RETRY_STEP;
        }

        if (lowerError.contains("rate limit") || lowerError.contains("限制")) {
            return StepValidationResult.ValidationAction.RETRY_STEP;
        }

        return StepValidationResult.ValidationAction.RETRY_STEP;
    }

    public PlanAdjustment generatePlanAdjustment(
            TaskPlanDTO originalPlan,
            int failedStepNumber,
            String failureReason,
            String model,
            Long userId) {

        PlanAdjustment adjustment = new PlanAdjustment();
        adjustment.setNeedsAdjustment(false);

        try {
            AIModelConfigService.ResolvedConfig config = modelConfigService.resolveModelConfigWithKey(model, userId);
            if (config == null) {
                return adjustment;
            }

            String prompt = buildAdjustmentPrompt(originalPlan, failedStepNumber, failureReason);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", config.getModelId());
            
            List<Map<String, String>> messages = new ArrayList<>();
            Map<String, String> systemMsg = new HashMap<>();
            systemMsg.put("role", "system");
            systemMsg.put("content", getAdjustmentSystemPrompt());
            messages.add(systemMsg);
            
            Map<String, String> userMsg = new HashMap<>();
            userMsg.put("role", "user");
            userMsg.put("content", prompt);
            messages.add(userMsg);
            
            requestBody.put("messages", messages);
            requestBody.put("temperature", 0.3);
            requestBody.put("max_tokens", 1000);

            String response = callLLM(config.getApiUrl(), config.getApiKey(), requestBody);

            return parseAdjustmentResponse(response);

        } catch (Exception e) {
            log.error("生成计划调整失败: {}", e.getMessage());
            return adjustment;
        }
    }

    private String getAdjustmentSystemPrompt() {
        return "你是一个任务计划调整专家。当一个步骤执行失败时，你需要分析失败原因并建议如何调整计划。\n\n" +
            "返回 JSON 格式：\n" +
            "{\n" +
            "    \"needsAdjustment\": true/false,\n" +
            "    \"reason\": \"调整原因\",\n" +
            "    \"adjustedSteps\": [\n" +
            "        {\n" +
            "            \"stepNumber\": 1,\n" +
            "            \"originalDescription\": \"原描述\",\n" +
            "            \"newDescription\": \"新描述\",\n" +
            "            \"newTool\": \"新工具名（可选）\"\n" +
            "        }\n" +
            "    ],\n" +
            "    \"newSteps\": [\"如果需要添加新步骤，描述它们\"]\n" +
            "}\n\n" +
            "调整原则：\n" +
            "- 如果失败是非关键性的，可以跳过或简化该步骤\n" +
            "- 如果需要替代方法，调整步骤描述和工具\n" +
            "- 如果发现遗漏的步骤，添加新步骤\n" +
            "- 如果任务无法完成，设置 needsAdjustment 为 false\n";
    }

    private String buildAdjustmentPrompt(TaskPlanDTO plan, int failedStepNumber, String failureReason) {
        StringBuilder sb = new StringBuilder();

        sb.append("=== 原始任务 ===\n");
        sb.append(plan.getOriginalRequest()).append("\n\n");

        sb.append("=== 原始计划 ===\n");
        sb.append("摘要: ").append(plan.getPlanSummary()).append("\n");
        sb.append("步骤:\n");
        for (TaskStepDTO step : plan.getSteps()) {
            String status = step.getStepNumber() < failedStepNumber ? "✓" : 
                           (step.getStepNumber() == failedStepNumber ? "✗" : "○");
            sb.append(String.format("%d. %s %s", step.getStepNumber(), status, step.getDescription()));
            if (step.getToolName() != null && !step.getToolName().isEmpty()) {
                sb.append(" [").append(step.getToolName()).append("]");
            }
            sb.append("\n");
        }
        sb.append("\n");

        sb.append("=== 失败信息 ===\n");
        sb.append(String.format("失败步骤: %d\n", failedStepNumber));
        sb.append("失败原因: ").append(failureReason).append("\n\n");

        sb.append("请分析失败原因并建议如何调整计划。返回 JSON 格式的结果。");

        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private PlanAdjustment parseAdjustmentResponse(String response) {
        try {
            Map<String, Object> responseMap = objectMapper.readValue(response, Map.class);
            List<Map<String, Object>> choices = (List<Map<String, Object>>) responseMap.get("choices");

            if (choices == null || choices.isEmpty()) {
                return new PlanAdjustment();
            }

            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
            String content = (String) message.get("content");

            String jsonContent = extractJson(content);

            Map<String, Object> result = objectMapper.readValue(jsonContent, Map.class);

            PlanAdjustment adjustment = new PlanAdjustment();
            adjustment.setNeedsAdjustment(getBoolean(result, "needsAdjustment", false));
            adjustment.setReason(getString(result, "reason", null));

            List<Map<String, Object>> adjustedStepsList = (List<Map<String, Object>>) result.get("adjustedSteps");
            if (adjustedStepsList != null) {
                List<PlanAdjustment.AdjustedStep> adjustedSteps = new ArrayList<>();
                for (Map<String, Object> stepMap : adjustedStepsList) {
                    PlanAdjustment.AdjustedStep step = new PlanAdjustment.AdjustedStep();
                    step.setStepNumber(getInt(stepMap, "stepNumber", 0));
                    step.setOriginalDescription(getString(stepMap, "originalDescription", null));
                    step.setNewDescription(getString(stepMap, "newDescription", null));
                    step.setNewTool(getString(stepMap, "newTool", null));
                    adjustedSteps.add(step);
                }
                adjustment.setAdjustedSteps(adjustedSteps);
            }

            adjustment.setNewSteps(getStringList(result, "newSteps"));

            return adjustment;

        } catch (Exception e) {
            log.warn("解析调整响应失败: {}", e.getMessage());
            return new PlanAdjustment();
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

    private int getInt(Map<String, Object> map, String key, int defaultValue) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
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
