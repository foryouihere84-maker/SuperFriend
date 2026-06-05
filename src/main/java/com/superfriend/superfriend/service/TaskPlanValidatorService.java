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
<<<<<<< HEAD
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 任务计划验证服务
 *
 * 采用三层验证架构：
 * 1. 确定性验证层（快速、可靠、无成本）
 * 2. 规则验证层（中速、较可靠、无成本）
 * 3. LLM 验证层（慢速、需成本、高智能）
 */
=======

>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
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

<<<<<<< HEAD
    // ==================== 数据结构定义 ====================

=======
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
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
<<<<<<< HEAD
        private List<String> previousStepResults;  // 新增：前序步骤结果
        private int retryCount;  // 新增：当前重试次数
=======
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
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
<<<<<<< HEAD
        private ValidationLevel validationLevel;  // 新增：验证层级
=======
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2

        public enum ValidationAction {
            CONTINUE,
            RETRY_STEP,
            SKIP_STEP,
            ADJUST_PLAN,
<<<<<<< HEAD
            ABORT_PLAN,
            FORCE_CONTINUE  // 新增：强制继续（低置信度但无更好选择）
        }

        public enum ValidationLevel {
            DETERMINISTIC,  // 确定性验证
            RULE_BASED,     // 规则验证
            LLM_BASED       // LLM 验证
=======
            ABORT_PLAN
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
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

<<<<<<< HEAD
    // 新增：整体任务验证结果
    @Data
    public static class TaskCompletionResult {
        private boolean completed;
        private double satisfactionScore;
        private String reason;
        private List<String> missingElements;
        private List<String> recommendations;
    }

    // ==================== 核心验证方法 ====================

    /**
     * 验证步骤执行结果 - 三层验证架构
     */
    public StepValidationResult validateStepResult(StepValidationContext context, String model, Long userId) {
        log.info("开始验证步骤 {} 结果，采用三层验证架构", context.getStepNumber());

        // 第一层：确定性验证（快速、可靠、无成本）
        StepValidationResult deterministicResult = validateDeterministically(context);
        if (!deterministicResult.isValid()) {
            log.info("步骤 {} 确定性验证失败: {}", context.getStepNumber(), deterministicResult.getReason());
            return deterministicResult;
        }

        // 第二层：规则验证（中速、较可靠、无成本）
        StepValidationResult ruleResult = validateWithRules(context);
        if (!ruleResult.isValid() && ruleResult.getConfidenceScore() > 0.8) {
            log.info("步骤 {} 规则验证失败（置信度 {}）: {}",
                context.getStepNumber(), ruleResult.getConfidenceScore(), ruleResult.getReason());
            return ruleResult;
        }

        // 第三层：LLM 验证（慢速、需成本、高智能）
        // 仅在规则验证不确定或步骤关键时调用
        if (shouldUseLLMValidation(context, ruleResult)) {
            try {
                StepValidationResult llmResult = validateWithLLM(context, model, userId);
                if (llmResult != null) {
                    log.info("步骤 {} LLM 验证完成: valid={}, confidence={}",
                        context.getStepNumber(), llmResult.isValid(), llmResult.getConfidenceScore());
                    return llmResult;
                }
            } catch (Exception e) {
                log.warn("LLM 验证失败: {}", e.getMessage());
            }
        }

        // 如果规则验证通过，返回规则验证结果
        if (ruleResult.isValid()) {
            return ruleResult;
        }

        // 默认返回低置信度的继续结果
        StepValidationResult defaultResult = new StepValidationResult();
        defaultResult.setValid(true);
        defaultResult.setConfidenceScore(0.6);
        defaultResult.setAction(StepValidationResult.ValidationAction.CONTINUE);
        defaultResult.setReason("验证通过（默认置信度）");
        defaultResult.setSuggestedFixes(new ArrayList<>());
        defaultResult.setValidationLevel(StepValidationResult.ValidationLevel.RULE_BASED);
        return defaultResult;
    }

    // ==================== 第一层：确定性验证 ====================

    /**
     * 确定性验证 - 快速、可靠、无成本
     */
    private StepValidationResult validateDeterministically(StepValidationContext context) {
        StepValidationResult result = new StepValidationResult();
        result.setValidationLevel(StepValidationResult.ValidationLevel.DETERMINISTIC);
        result.setSuggestedFixes(new ArrayList<>());

        // 1. 空结果检查
        if (context.getActualResult() == null || context.getActualResult().trim().isEmpty()) {
            result.setValid(false);
            result.setConfidenceScore(1.0);
            result.setReason("步骤结果为空");
            result.setAction(StepValidationResult.ValidationAction.RETRY_STEP);
            result.getSuggestedFixes().add("重新执行该步骤，确保返回有效结果");
            return result;
        }

        // 2. 错误信息检查
        if (context.getErrorMessage() != null && !context.getErrorMessage().isEmpty()) {
            result.setValid(false);
            result.setConfidenceScore(1.0);
=======
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
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
            result.setReason("步骤执行出错: " + context.getErrorMessage());
            result.setAction(determineActionFromError(context.getErrorMessage()));
            result.getSuggestedFixes().add("检查错误原因并修复");
            return result;
        }

<<<<<<< HEAD
        // 3. 工具调用失败检查
        if (context.getToolUsed() != null && !context.getToolUsed().isEmpty() && !context.isToolSuccess()) {
            result.setValid(false);
            result.setConfidenceScore(1.0);
            result.setReason("工具调用失败: " + context.getToolUsed());
            result.setAction(StepValidationResult.ValidationAction.RETRY_STEP);
            result.getSuggestedFixes().add("尝试使用替代工具或检查工具参数");
            return result;
        }

        // 4. 结果长度检查（过短可能不完整）
        String actualResult = context.getActualResult();
        if (actualResult.length() < 10) {
            result.setValid(false);
            result.setConfidenceScore(0.9);
            result.setReason("步骤结果过短（< 10 字符），可能不完整: " + actualResult);
            result.setAction(StepValidationResult.ValidationAction.RETRY_STEP);
            result.getSuggestedFixes().add("确保步骤返回完整的结果");
            return result;
        }

        // 5. 错误关键词检查
        String[] errorKeywords = {"失败", "错误", "无法", "exception", "error", "failed", "unable", "timeout"};
        String lowerResult = actualResult.toLowerCase();
        for (String keyword : errorKeywords) {
            if (lowerResult.contains(keyword)) {
                result.setValid(false);
                result.setConfidenceScore(0.85);
                result.setReason("步骤结果包含错误关键词: " + keyword);
                result.setAction(determineActionFromError(actualResult));
                result.getSuggestedFixes().add("检查错误原因，尝试替代方案");
                return result;
            }
        }

        // 确定性验证通过
        result.setValid(true);
        result.setConfidenceScore(0.7);
        result.setAction(StepValidationResult.ValidationAction.CONTINUE);
        result.setReason("确定性验证通过");
        return result;
    }

    // ==================== 第二层：规则验证 ====================

    /**
     * 规则验证 - 中速、较可靠、无成本
     */
    private StepValidationResult validateWithRules(StepValidationContext context) {
        StepValidationResult result = new StepValidationResult();
        result.setValidationLevel(StepValidationResult.ValidationLevel.RULE_BASED);
        result.setSuggestedFixes(new ArrayList<>());

        String actualResult = context.getActualResult();
        String stepDesc = context.getStepDescription() != null ? context.getStepDescription().toLowerCase() : "";

        // 1. 结果长度合理性检查
        int minLength = determineMinResultLength(stepDesc);
        if (actualResult.length() < minLength) {
            result.setValid(false);
            result.setConfidenceScore(0.75);
            result.setReason(String.format("结果长度不足（%d < %d），可能不完整", actualResult.length(), minLength));
            result.setAction(StepValidationResult.ValidationAction.RETRY_STEP);
            result.getSuggestedFixes().add("提供更详细的结果");
            return result;
        }

        // 2. 步骤类型特定验证
        if (stepDesc.contains("搜索") || stepDesc.contains("查询") || stepDesc.contains("获取")) {
            if (!hasSubstantiveContent(actualResult)) {
                result.setValid(false);
                result.setConfidenceScore(0.8);
                result.setReason("搜索/查询类步骤未返回实质性内容");
                result.setAction(StepValidationResult.ValidationAction.RETRY_STEP);
                result.getSuggestedFixes().add("确保搜索返回有效结果");
                return result;
            }
        }

        if (stepDesc.contains("分析") || stepDesc.contains("比较") || stepDesc.contains("评估")) {
            if (!hasAnalysisContent(actualResult)) {
                result.setValid(false);
                result.setConfidenceScore(0.75);
                result.setReason("分析类步骤缺少分析内容");
                result.setAction(StepValidationResult.ValidationAction.RETRY_STEP);
                result.getSuggestedFixes().add("提供详细的分析内容");
                return result;
            }
        }

        if (stepDesc.contains("总结") || stepDesc.contains("汇总")) {
            if (!hasSummaryContent(actualResult)) {
                result.setValid(false);
                result.setConfidenceScore(0.7);
                result.setReason("总结类步骤缺少总结内容");
                result.setAction(StepValidationResult.ValidationAction.RETRY_STEP);
                result.getSuggestedFixes().add("提供清晰的总结");
                return result;
            }
        }

        // 3. 敷衍回答检测
        if (isPerfunctoryResponse(actualResult)) {
            result.setValid(false);
            result.setConfidenceScore(0.85);
            result.setReason("检测到敷衍回答，任务可能未真正完成");
            result.setAction(StepValidationResult.ValidationAction.RETRY_STEP);
            result.getSuggestedFixes().add("提供实质性的回答，不要只说'好的'或'我来帮你'");
            return result;
        }

        // 4. 不完整回答检测
        if (isIncompleteResponse(actualResult, stepDesc)) {
            result.setValid(false);
            result.setConfidenceScore(0.8);
            result.setReason("检测到不完整回答");
            result.setAction(StepValidationResult.ValidationAction.RETRY_STEP);
            result.getSuggestedFixes().add("完成当前步骤的所有内容");
            return result;
        }

        // 5. 重试次数检查
        if (context.getRetryCount() >= 3) {
            // 多次重试后，降低验证标准
            result.setValid(true);
            result.setConfidenceScore(0.5);
            result.setAction(StepValidationResult.ValidationAction.FORCE_CONTINUE);
            result.setReason("多次重试后降低验证标准，强制继续");
            return result;
        }

        // 规则验证通过
        result.setValid(true);
        result.setConfidenceScore(0.85);
        result.setAction(StepValidationResult.ValidationAction.CONTINUE);
        result.setReason("规则验证通过");
        return result;
    }

    /**
     * 判断是否需要 LLM 验证
     */
    private boolean shouldUseLLMValidation(StepValidationContext context, StepValidationResult ruleResult) {
        // 1. 规则验证不确定时
        if (ruleResult.getConfidenceScore() < 0.7) {
            return true;
        }

        // 2. 关键步骤（最后一步或包含"关键"、"核心"等关键词）
        String stepDesc = context.getStepDescription() != null ? context.getStepDescription().toLowerCase() : "";
        if (context.getStepNumber() == context.getTotalSteps() ||
            stepDesc.contains("关键") || stepDesc.contains("核心") || stepDesc.contains("重要")) {
            return true;
        }

        // 3. 结果包含复杂内容需要语义理解
        String actualResult = context.getActualResult();
        if (actualResult.length() > 1000 && containsComplexContent(actualResult)) {
            return true;
        }

        return false;
    }

    // ==================== 第三层：LLM 验证 ====================

=======
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

>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
    private StepValidationResult validateWithLLM(StepValidationContext context, String model, Long userId) {
        try {
            AIModelConfigService.ResolvedConfig config = modelConfigService.resolveModelConfigWithKey(model, userId);
            if (config == null) {
                return null;
            }

            String prompt = buildValidationPrompt(context);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", config.getModelId());
<<<<<<< HEAD

=======
            
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
            List<Map<String, String>> messages = new ArrayList<>();
            Map<String, String> systemMsg = new HashMap<>();
            systemMsg.put("role", "system");
            systemMsg.put("content", getValidationSystemPrompt());
            messages.add(systemMsg);
<<<<<<< HEAD

=======
            
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
            Map<String, String> userMsg = new HashMap<>();
            userMsg.put("role", "user");
            userMsg.put("content", prompt);
            messages.add(userMsg);
<<<<<<< HEAD

=======
            
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
            requestBody.put("messages", messages);
            requestBody.put("temperature", 0.2);
            requestBody.put("max_tokens", 800);

            String response = callLLM(config.getApiUrl(), config.getApiKey(), requestBody);

<<<<<<< HEAD
            StepValidationResult result = parseValidationResponse(response);
            if (result != null) {
                result.setValidationLevel(StepValidationResult.ValidationLevel.LLM_BASED);
            }
            return result;
=======
            return parseValidationResponse(response);
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2

        } catch (Exception e) {
            log.error("LLM 验证调用失败: {}", e.getMessage());
            return null;
        }
    }

<<<<<<< HEAD
    // ==================== 辅助方法 ====================

    /**
     * 根据步骤类型确定最小结果长度
     */
    private int determineMinResultLength(String stepDesc) {
        if (stepDesc.contains("简单") || stepDesc.contains("快速")) {
            return 15;
        }
        if (stepDesc.contains("详细") || stepDesc.contains("全面") || stepDesc.contains("深入")) {
            return 100;
        }
        if (stepDesc.contains("分析") || stepDesc.contains("比较") || stepDesc.contains("评估")) {
            return 50;
        }
        return 30;  // 默认最小长度
    }

    /**
     * 检查是否有实质性内容
     */
    private boolean hasSubstantiveContent(String result) {
        // 检查是否包含数据、信息、结果等实质性内容
        String[] substantivePatterns = {
            "结果", "数据", "信息", "内容", "找到", "发现", "获取",
            "result", "data", "found", "retrieved", "content"
        };

        String lowerResult = result.toLowerCase();
        for (String pattern : substantivePatterns) {
            if (lowerResult.contains(pattern)) {
                return true;
            }
        }

        // 检查是否有列表或结构化内容
        if (result.contains("1.") || result.contains("-") || result.contains("*") || result.contains(":")) {
            return true;
        }

        return result.length() > 50;
    }

    /**
     * 检查是否有分析内容
     */
    private boolean hasAnalysisContent(String result) {
        String[] analysisPatterns = {
            "分析", "比较", "对比", "评估", "结论", "观点", "原因", "影响",
            "analysis", "comparison", "conclusion", "because", "therefore"
        };

        String lowerResult = result.toLowerCase();
        for (String pattern : analysisPatterns) {
            if (lowerResult.contains(pattern)) {
                return true;
            }
        }

        return result.length() > 80;
    }

    /**
     * 检查是否有总结内容
     */
    private boolean hasSummaryContent(String result) {
        String[] summaryPatterns = {
            "总结", "汇总", "概括", "要点", "核心", "主要", "关键",
            "summary", "conclusion", "main", "key", "important"
        };

        String lowerResult = result.toLowerCase();
        for (String pattern : summaryPatterns) {
            if (lowerResult.contains(pattern)) {
                return true;
            }
        }

        return result.length() > 30;
    }

    /**
     * 检测敷衍回答
     */
    private boolean isPerfunctoryResponse(String result) {
        String trimmed = result.trim();

        // 常见的敷衍回答模式
        String[] perfunctoryPatterns = {
            "好的，我来帮你", "好的，让我", "我来帮你", "让我来帮你",
            "我将", "我会帮你", "首先，我", "好的，我"
        };

        for (String pattern : perfunctoryPatterns) {
            if (trimmed.startsWith(pattern) && trimmed.length() < 100) {
                return true;
            }
        }

        // 检查是否只有开场白没有实际内容
        if (trimmed.length() < 50 &&
            (trimmed.contains("好的") || trimmed.contains("我来") || trimmed.contains("让我"))) {
            return true;
        }

        return false;
    }

    /**
     * 检测不完整回答
     */
    private boolean isIncompleteResponse(String result, String stepDesc) {
        String trimmed = result.trim();

        // 检查是否以不完整的句子结尾
        if (trimmed.endsWith("，") || trimmed.endsWith(",") ||
            trimmed.endsWith("、") || trimmed.endsWith(":") || trimmed.endsWith("：")) {
            return true;
        }

        // 检查是否有未完成的列表
        int listStarts = countOccurrences(trimmed, "\\d+\\.");
        int listEnds = countOccurrences(trimmed, "\n");
        if (listStarts > 0 && listEnds < listStarts && trimmed.length() < 200) {
            return true;
        }

        // 检查是否有"待续"、"未完"等标记
        if (trimmed.contains("待续") || trimmed.contains("未完") ||
            trimmed.contains("to be continued") || trimmed.contains("...")) {
            return true;
        }

        return false;
    }

    /**
     * 检查是否包含复杂内容
     */
    private boolean containsComplexContent(String result) {
        // 包含代码块
        if (result.contains("```") || result.contains("    ") && result.contains("{")) {
            return true;
        }

        // 包含表格
        if (result.contains("|") && result.contains("---")) {
            return true;
        }

        // 包含多个段落
        if (result.split("\n\n").length > 3) {
            return true;
        }

        return false;
    }

    private int countOccurrences(String text, String pattern) {
        Pattern p = Pattern.compile(pattern);
        Matcher m = p.matcher(text);
        int count = 0;
        while (m.find()) {
            count++;
        }
        return count;
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

    // ==================== LLM 验证相关方法 ====================

=======
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
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
<<<<<<< HEAD
            "- 如果步骤是关键步骤，失败时应建议重试或调整计划\n" +
            "- 警惕敷衍回答，如\"好的，我来帮你\"但没有实际内容\n";
=======
            "- 如果步骤是关键步骤，失败时应建议重试或调整计划\n";
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
    }

    private String buildValidationPrompt(StepValidationContext context) {
        StringBuilder sb = new StringBuilder();

        sb.append("=== 原始任务 ===\n");
        sb.append(context.getOriginalRequest()).append("\n\n");

        sb.append("=== 当前步骤 ===\n");
<<<<<<< HEAD
        sb.append(String.format("步骤 %d/%d: %s\n\n",
=======
        sb.append(String.format("步骤 %d/%d: %s\n\n", 
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
                context.getStepNumber(), context.getTotalSteps(), context.getStepDescription()));

        if (context.getExpectedOutcome() != null && !context.getExpectedOutcome().isEmpty()) {
            sb.append("=== 预期结果 ===\n");
            sb.append(context.getExpectedOutcome()).append("\n\n");
        }

        sb.append("=== 实际结果 ===\n");
        String result = context.getActualResult();
<<<<<<< HEAD
        if (result != null && result.length() > 5000) {
            result = result.substring(0, 5000) + "...(已截断)";
=======
        if (result != null && result.length() > 1000) {
            result = result.substring(0, 1000) + "...(已截断)";
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
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

<<<<<<< HEAD
        if (context.getRetryCount() > 0) {
            sb.append("=== 重试信息 ===\n");
            sb.append("当前重试次数: ").append(context.getRetryCount()).append("\n\n");
        }

        sb.append("请验证此步骤的执行结果并返回 JSON 格式的验证结果。\n");
        sb.append("特别注意：如果结果是敷衍回答（如\"好的，我来帮你\"但没有实际内容），请标记为无效。");
=======
        sb.append("请验证此步骤的执行结果并返回 JSON 格式的验证结果。");
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2

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

<<<<<<< HEAD
    // ==================== 整体任务验证 ====================

    /**
     * 验证整体任务是否完成
     */
    public TaskCompletionResult validateTaskCompletion(TaskPlanDTO plan, String finalResult, Long userId) {
        TaskCompletionResult result = new TaskCompletionResult();
        result.setMissingElements(new ArrayList<>());
        result.setRecommendations(new ArrayList<>());

        // 1. 检查所有步骤是否完成
        boolean allStepsCompleted = plan.getSteps().stream()
            .allMatch(s -> "completed".equals(s.getStatus()));

        if (!allStepsCompleted) {
            result.setCompleted(false);
            result.setSatisfactionScore(0.0);
            result.setReason("存在未完成的步骤");

            List<String> incompleteSteps = new ArrayList<>();
            for (TaskStepDTO step : plan.getSteps()) {
                if (!"completed".equals(step.getStatus())) {
                    incompleteSteps.add("步骤 " + step.getStepNumber() + ": " + step.getDescription());
                }
            }
            result.setMissingElements(incompleteSteps);
            return result;
        }

        // 2. 检查最终结果是否满足原始请求
        String originalRequest = plan.getOriginalRequest();
        if (finalResult == null || finalResult.trim().isEmpty()) {
            result.setCompleted(false);
            result.setSatisfactionScore(0.0);
            result.setReason("最终结果为空");
            return result;
        }

        // 3. 基于规则检查结果质量
        double satisfactionScore = calculateSatisfactionScore(originalRequest, finalResult);
        result.setSatisfactionScore(satisfactionScore);

        if (satisfactionScore < 0.5) {
            result.setCompleted(false);
            result.setReason("最终结果不满足原始请求");
            result.getMissingElements().add("结果与原始请求匹配度低");
            result.getRecommendations().add("考虑重新执行或调整计划");
        } else if (satisfactionScore < 0.8) {
            result.setCompleted(true);
            result.setReason("任务基本完成，但结果可能不够完善");
            result.getRecommendations().add("可以考虑进一步优化结果");
        } else {
            result.setCompleted(true);
            result.setReason("任务完成，结果质量良好");
        }

        return result;
    }

    /**
     * 计算结果满意度分数
     */
    private double calculateSatisfactionScore(String originalRequest, String finalResult) {
        double score = 0.5;  // 基础分

        // 1. 结果长度检查
        if (finalResult.length() > 100) {
            score += 0.1;
        }
        if (finalResult.length() > 300) {
            score += 0.1;
        }

        // 2. 关键词匹配检查
        String[] requestKeywords = extractKeywords(originalRequest);
        String lowerResult = finalResult.toLowerCase();
        int matchedKeywords = 0;
        for (String keyword : requestKeywords) {
            if (lowerResult.contains(keyword.toLowerCase())) {
                matchedKeywords++;
            }
        }
        if (requestKeywords.length > 0) {
            score += 0.2 * ((double) matchedKeywords / requestKeywords.length);
        }

        // 3. 结构化内容检查
        if (finalResult.contains("1.") || finalResult.contains("-") || finalResult.contains("*")) {
            score += 0.1;
        }

        // 4. 截断到 [0, 1]
        return Math.min(1.0, Math.max(0.0, score));
    }

    /**
     * 提取关键词
     */
    private String[] extractKeywords(String text) {
        if (text == null || text.isEmpty()) {
            return new String[0];
        }

        // 简单的关键词提取：移除停用词，保留有意义的词
        String[] stopWords = {"的", "是", "在", "了", "和", "与", "或", "我", "你", "他", "她", "它",
            "这", "那", "有", "为", "以", "及", "等", "但", "如", "而", "也", "都", "就", "着", "过"};

        Set<String> stopWordSet = new HashSet<>(Arrays.asList(stopWords));

        // 分词（简单实现，按空格和标点分割）
        String[] words = text.split("[\\s,，。！？、；：\"'（）【】《》]+");

        List<String> keywords = new ArrayList<>();
        for (String word : words) {
            if (word.length() >= 2 && !stopWordSet.contains(word)) {
                keywords.add(word);
            }
        }

        return keywords.toArray(new String[0]);
    }

    // ==================== 计划调整相关方法 ====================

=======
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

>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
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
<<<<<<< HEAD

=======
            
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
            List<Map<String, String>> messages = new ArrayList<>();
            Map<String, String> systemMsg = new HashMap<>();
            systemMsg.put("role", "system");
            systemMsg.put("content", getAdjustmentSystemPrompt());
            messages.add(systemMsg);
<<<<<<< HEAD

=======
            
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
            Map<String, String> userMsg = new HashMap<>();
            userMsg.put("role", "user");
            userMsg.put("content", prompt);
            messages.add(userMsg);
<<<<<<< HEAD

=======
            
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
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
<<<<<<< HEAD
            String status = step.getStepNumber() < failedStepNumber ? "✓" :
=======
            String status = step.getStepNumber() < failedStepNumber ? "✓" : 
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
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

<<<<<<< HEAD
    // ==================== 工具方法 ====================

    private String extractJson(String content) {
        if (content == null || content.isEmpty()) {
            return content;
        }

        // 去除常见的思考标签
        String cleaned = content;
        String[] thinkingPatterns = {"<think>", "</think>", "<reasoning>", "</reasoning>",
                "<thought>", "</thought>", "<reflection>", "</reflection>"};
        for (String tag : thinkingPatterns) {
            cleaned = cleaned.replace(tag, "");
        }

        // 如果内容包含 <...> 标签包裹的思考内容，尝试移除
        cleaned = cleaned.replaceAll("<think[\\s\\S]*?>", "");
        cleaned = cleaned.replaceAll("<reasoning[\\s\\S]*?</reasoning>", "");
        cleaned = cleaned.replaceAll("<thought[\\s\\S]*?</thought>", "");

        // 提取 JSON 对象
        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');

        if (start >= 0 && end > start) {
            return cleaned.substring(start, end + 1);
        }

        return cleaned.trim();
=======
    private String extractJson(String content) {
        int start = content.indexOf('{');
        int end = content.lastIndexOf('}');

        if (start >= 0 && end > start) {
            return content.substring(start, end + 1);
        }

        return content;
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
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
<<<<<<< HEAD

=======
    
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
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
