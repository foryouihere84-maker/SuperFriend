package com.superfriend.superfriend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.superfriend.superfriend.agent.planner.EnhancedPlanExecutor;
import com.superfriend.superfriend.dto.*;
import com.superfriend.superfriend.entity.AgentTaskPlan;
import com.superfriend.superfriend.entity.AgentTaskStep;
import com.superfriend.superfriend.entity.AIModelConfig;
import com.superfriend.superfriend.mapper.AgentTaskPlanMapper;
import com.superfriend.superfriend.mapper.AgentTaskStepMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@Slf4j
@Service
public class TaskPlannerService {

    // 【改进】复杂度判断缓存 - 缓存最近 1000 个查询结果，5 分钟过期
    private static final int CACHE_MAX_SIZE = 1000;
    private static final long CACHE_TTL_MS = 5 * 60 * 1000; // 5 分钟
    private final Map<String, CacheEntry> complexityCache = new ConcurrentHashMap<>();

    private static class CacheEntry {
        final boolean isComplex;
        final long timestamp;
        CacheEntry(boolean isComplex) {
            this.isComplex = isComplex;
            this.timestamp = System.currentTimeMillis();
        }
        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_TTL_MS;
        }
    }

    private String getCacheKey(String userMessage) {
        // 【改进】使用消息内容+长度作为缓存键，避免哈希冲突
        // 格式: length_hashCode，大幅降低冲突概率
        if (userMessage == null || userMessage.isEmpty()) {
            return "0_0";
        }
        return userMessage.length() + "_" + userMessage.hashCode();
    }

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private LLMClient llmClient;

    @Autowired
    private AgentTaskPlanMapper planMapper;

    @Autowired
    private AgentTaskStepMapper stepMapper;

    @Autowired
    private McpHostService mcpHostService;

    @Autowired
    @Lazy
    private AgentSessionManager sessionManager;

    @Autowired
    @Lazy
    private AIModelConfigService modelConfigService;

    @Autowired
    @Lazy
    private TaskPlanValidatorService planValidatorService;

    @Autowired
    @Lazy
    private ExecutionCheckpointService checkpointService;

    @Autowired
    @Lazy
    private EnhancedPlanExecutor enhancedPlanExecutor;

    @Autowired
    @Lazy
    private MemoryPalaceService memoryPalaceService;

    @Autowired
    @Lazy
    private CostTrackingService costTrackingService;

    private AIModelConfig resolveModelConfig(Long userId, String modelName) {
        if (modelName != null && !modelName.isEmpty()) {
            AIModelConfig config = modelConfigService.resolveModelConfig(modelName, userId);
            if (config != null) return config;
        }
        AIModelConfig config = modelConfigService.getDefaultModel(userId);
        if (config == null) {
            List<AIModelConfig> models = modelConfigService.getAvailableModelEntities(userId);
            if (models != null && !models.isEmpty()) {
                config = models.get(0);
            }
        }
        return config;
    }

    // 简单问题快速判断关键词
    private static final List<String> SIMPLE_GREETING_PATTERNS = Arrays.asList(
        "你是谁", "你叫什么", "你是什么", "自我介绍", "你好", "hello", "hi ",
        "嗨", "早上好", "下午好", "晚上好", "早安", "晚安",
        "谢谢", "感谢", "thanks", "thank you", "再见", "拜拜",
        "帮我", "能做什么", "你会什么", "你有什么功能", "你能做什么",
        "怎么用", "如何使用"
    );

    private static final int COMPLEXITY_CHECK_MAX_RETRIES = 3;
    private static final long COMPLEXITY_CHECK_RETRY_DELAY_MS = 1000;

    public boolean isComplexTask(String userMessage, Long userId) {
        return isComplexTask(userMessage, userId, null);
    }

    public boolean isComplexTask(String userMessage, Long userId, String modelName) {
        // 【改进】快速预判断
        if (isSimpleQuestion(userMessage)) {
            log.info("任务复杂度判断：简单问题，跳过LLM判断 - {}", userMessage);
            return false;
        }

        if (isObviouslyComplexTask(userMessage)) {
            log.info("任务复杂度判断：明显复杂任务，跳过LLM判断 - {}", userMessage);
            return true;
        }

        // 【改进】检查缓存
        String cacheKey = getCacheKey(userMessage);
        CacheEntry cached = complexityCache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            log.info("任务复杂度判断：使用缓存结果 - {}", cached.isComplex);
            return cached.isComplex;
        }

        // 清理过期缓存
        if (complexityCache.size() > CACHE_MAX_SIZE) {
            complexityCache.entrySet().removeIf(entry -> entry.getValue().isExpired());
        }

        AIModelConfig modelConfig = resolveModelConfig(userId, modelName);
        if (modelConfig == null) {
            log.warn("用户 {} 没有配置模型，跳过复杂度判断", userId);
            return false;
        }

        String prompt = "判断以下用户请求是否需要多步骤执行计划。\n\n" +
            "如果只需要一次工具调用或直接回答，返回 {\"complex\": false, \"reason\": \"简要原因\"}\n" +
            "如果需要多次工具调用、信息收集、多步处理、数据分析等，返回 {\"complex\": true, \"reason\": \"简要原因\"}\n\n" +
            "用户请求：" + userMessage + "\n\n" +
            "请严格按 JSON 格式返回，不要包含其他内容。";

        for (int attempt = 1; attempt <= COMPLEXITY_CHECK_MAX_RETRIES; attempt++) {
            try {
                String response = callLLMNonStream(prompt, modelConfig.getModelId(), modelConfig.getApiUrl(), modelConfig.getApiKey(), null, userId);

                if (response != null && !response.isEmpty()) {
                    boolean result;
                    if (response.contains("\"complex\": true")) {
                        result = true;
                        log.info("任务复杂度判断：复杂任务 (attempt {}) - {}", attempt, extractJsonField(response, "reason"));
                    } else if (response.contains("\"complex\": false")) {
                        result = false;
                        log.info("任务复杂度判断：普通任务 (attempt {}) - {}", attempt, extractJsonField(response, "reason"));
                    } else {
                        log.warn("任务复杂度判断：响应格式不正确 (attempt {}) - {}", attempt, response);
                        continue;
                    }
                    // 【改进】缓存结果
                    complexityCache.put(cacheKey, new CacheEntry(result));
                    return result;
                }
            } catch (Exception e) {
                log.warn("复杂度判断失败 (attempt {}/{}): {}", attempt, COMPLEXITY_CHECK_MAX_RETRIES, e.getMessage());
            }

            if (attempt < COMPLEXITY_CHECK_MAX_RETRIES) {
                try {
                    Thread.sleep(COMPLEXITY_CHECK_RETRY_DELAY_MS * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        boolean fallbackResult = heuristicComplexityCheck(userMessage);
        log.info("复杂度判断 LLM 调用失败，使用启发式判断: {}", fallbackResult);
        // 【改进】缓存启发式结果
        complexityCache.put(cacheKey, new CacheEntry(fallbackResult));
        return fallbackResult;
    }

    /**
     * 快速判断是否为明显复杂任务（不需要 LLM 判断）
     * 包括：多步骤操作、比较分析、数据收集等
     */
    private boolean isObviouslyComplexTask(String message) {
        if (message == null || message.trim().isEmpty()) {
            return false;
        }
        String trimmed = message.trim().toLowerCase();

        // 多步骤关键词
        String[] multiStepPatterns = {
            "然后", "接着", "之后", "再", "并且", "同时",
            "第一步", "第二步", "首先", "其次", "最后",
            "分别", "各自", "每个", "所有"
        };
        for (String pattern : multiStepPatterns) {
            if (trimmed.contains(pattern)) {
                return true;
            }
        }

        // 比较分析关键词
        String[] analysisPatterns = {
            "比较", "对比", "分析", "评估", "评价",
            "优缺点", "利弊", "哪个更好", "选择哪个",
            "全面", "详细", "完整", "深入"
        };
        for (String pattern : analysisPatterns) {
            if (trimmed.contains(pattern)) {
                return true;
            }
        }

        // 数据收集关键词（多个来源）
        String[] dataCollectionPatterns = {
            "收集", "汇总", "整理", "统计",
            "多个", "几个", "各种", "不同来源"
        };
        for (String pattern : dataCollectionPatterns) {
            if (trimmed.contains(pattern)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 启发式复杂度判断（作为 LLM 调用失败的后备方案）
     */
    private boolean heuristicComplexityCheck(String message) {
        if (message == null || message.trim().isEmpty()) {
            return false;
        }
        String trimmed = message.trim().toLowerCase();

        // 计算复杂度指标
        int complexityScore = 0;

        // 长度因素：较长的问题可能更复杂
        if (trimmed.length() > 100) complexityScore += 2;
        else if (trimmed.length() > 50) complexityScore += 1;

        // 关键词因素
        String[] complexKeywords = {
            "分析", "比较", "对比", "评估", "研究",
            "收集", "整理", "汇总", "统计",
            "多个", "几个", "所有", "每个",
            "然后", "接着", "之后", "并且",
            "详细", "全面", "完整", "深入"
        };
        for (String keyword : complexKeywords) {
            if (trimmed.contains(keyword)) {
                complexityScore += 1;
            }
        }

        // 疑问句数量：多个疑问句可能表示复杂任务
        int questionCount = 0;
        for (int i = 0; i < trimmed.length(); i++) {
            if (trimmed.charAt(i) == '？' || trimmed.charAt(i) == '?') {
                questionCount++;
            }
        }
        if (questionCount >= 2) complexityScore += 2;
        else if (questionCount == 1) complexityScore += 1;

        // 判断阈值：分数 >= 3 认为是复杂任务
        return complexityScore >= 3;
    }

    /**
     * 快速判断是否为简单问题（不需要工具调用或规划）
     * 包括：打招呼、自我介绍、感谢、简单闲聊、功能询问等
     */
    private boolean isSimpleQuestion(String message) {
        if (message == null || message.trim().isEmpty()) {
            return true;
        }
        String trimmed = message.trim().toLowerCase();
        // 短文本（<=30字符）且包含简单问题关键词
        if (trimmed.length() <= 30) {
            for (String pattern : SIMPLE_GREETING_PATTERNS) {
                if (trimmed.contains(pattern)) {
                    return true;
                }
            }
        }
        // 纯问候（非常短的文本）
        if (trimmed.length() <= 10) {
            // 如果只是简单的几个字，很可能是打招呼或简单提问
            if (trimmed.matches("^[\\u4e00-\\u9fa5a-zA-Z0-9\\s!?？！，。,.]+$")) {
                for (String pattern : SIMPLE_GREETING_PATTERNS) {
                    if (trimmed.contains(pattern)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public TaskPlanDTO generatePlan(String userMessage, String sessionId, Long userId,
                                     List<McpToolDefinition> availableTools) {
        return generatePlan(userMessage, sessionId, userId, availableTools, null);
    }

    public TaskPlanDTO generatePlan(String userMessage, String sessionId, Long userId,
                                     List<McpToolDefinition> availableTools, String modelName) {
        try {
            AIModelConfig modelConfig = resolveModelConfig(userId, modelName);
            if (modelConfig == null) {
                log.warn("用户 {} 没有配置模型，跳过计划生成", userId);
                return null;
            }

            String toolsDescription = buildToolsDescription(availableTools);

            String prompt = "你是一个任务规划专家。请将以下用户请求分解为具体的执行步骤。\n\n" +
                "要求：\n" +
                "1. 每个步骤应该是一个明确的、可执行的操作\n" +
                "2. 步骤之间有逻辑依赖关系\n" +
                "3. 标注每个步骤需要使用的工具（如果需要），工具名格式为 server__tool_name\n" +
                "4. 步骤数量控制在 2-8 个之间\n" +
                "5. 最后一个步骤应该是\"汇总结果并回复用户\"\n\n" +
                "可用工具：\n" + toolsDescription + "\n\n" +
                "用户请求：" + userMessage + "\n\n" +
                "请严格按以下 JSON 格式返回，不要包含其他内容：\n" +
                "{\"summary\": \"计划摘要（一句话描述整体方案）\", \"steps\": [{\"description\": \"步骤描述\", \"tool\": \"工具名（可选，不需要工具时留空字符串）\"}]}";

            String response = callLLMNonStream(prompt, modelConfig.getModelId(), modelConfig.getApiUrl(), modelConfig.getApiKey(), sessionId, userId);
            if (response == null) {
                log.error("LLM 返回空响应，无法生成计划");
                return null;
            }

            response = extractJson(response);

            // 验证提取的 JSON 是否有效
            if (response == null || response.isEmpty() || !response.trim().startsWith("{")) {
                log.warn("LLM 未返回有效 JSON，计划生成失败，将降级为 ReAct 模式。原始响应: {}",
                        response != null && response.length() > 200 ? response.substring(0, 200) + "..." : response);
                return null;
            }

            JsonNode root;
            try {
                root = objectMapper.readTree(response);
            } catch (Exception e) {
                log.warn("JSON 解析失败，计划生成失败，将降级为 ReAct 模式。错误: {}, JSON: {}",
                        e.getMessage(), response.length() > 200 ? response.substring(0, 200) + "..." : response);
                return null;
            }
            String summary = root.path("summary").asText("无摘要");
            JsonNode stepsNode = root.path("steps");

            if (!stepsNode.isArray() || stepsNode.size() == 0) {
                log.error("LLM 返回的计划没有步骤");
                return null;
            }

            String planId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);

            AgentTaskPlan plan = new AgentTaskPlan();
            plan.setPlanId(planId);
            plan.setSessionId(sessionId);
            plan.setUserId(userId != null ? userId : 1L);
            plan.setOriginalRequest(userMessage);
            plan.setPlanSummary(summary);
            plan.setStatus("pending");
            plan.setTotalSteps(stepsNode.size());
            plan.setCompletedSteps(0);
            planMapper.insert(plan);

            List<TaskStepDTO> stepDTOs = new ArrayList<>();
            for (int i = 0; i < stepsNode.size(); i++) {
                JsonNode stepNode = stepsNode.get(i);
                String stepId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);

                AgentTaskStep step = new AgentTaskStep();
                step.setStepId(stepId);
                step.setPlanId(planId);
                step.setStepNumber(i + 1);
                step.setDescription(stepNode.path("description").asText());
                step.setStatus("pending");
                String toolName = stepNode.path("tool").asText("");
                if (toolName.isEmpty() || "null".equalsIgnoreCase(toolName)) {
                    toolName = null;
                }
                step.setToolName(toolName);
                stepMapper.insert(step);

                TaskStepDTO dto = new TaskStepDTO();
                dto.setStepId(stepId);
                dto.setStepNumber(i + 1);
                dto.setDescription(stepNode.path("description").asText());
                dto.setStatus("pending");
                dto.setToolName(toolName);
                stepDTOs.add(dto);
            }

            TaskPlanDTO planDTO = new TaskPlanDTO();
            planDTO.setPlanId(planId);
            planDTO.setSessionId(sessionId);
            planDTO.setUserId(userId);
            planDTO.setOriginalRequest(userMessage);
            planDTO.setPlanSummary(summary);
            planDTO.setStatus("pending");
            planDTO.setTotalSteps(stepsNode.size());
            planDTO.setCompletedSteps(0);
            planDTO.setSteps(stepDTOs);

            EnhancedPlanExecutor.ExecutionPlan executionPlan = convertToExecutionPlan(planDTO);
            EnhancedPlanExecutor.PlanValidationResult validationResult = 
                enhancedPlanExecutor.validatePlan(executionPlan);
            
            if (!validationResult.isValid()) {
                log.warn("计划验证失败: confidence={}, issues={}", 
                    validationResult.getConfidence(), 
                    validationResult.getIssues().stream()
                        .map(issue -> issue.getType() + ": " + issue.getMessage())
                        .collect(java.util.stream.Collectors.joining("; ")));
                
                if (validationResult.getIssues().stream()
                    .anyMatch(issue -> issue.getSeverity() == EnhancedPlanExecutor.PlanIssueSeverity.CRITICAL)) {
                    log.error("计划存在严重问题，返回null");
                    return null;
                }
            } else {
                log.info("计划验证通过: confidence={}", validationResult.getConfidence());
            }

            log.info("任务计划已生成：planId={}, 步骤数={}, 验证置信度={}", 
                planId, stepsNode.size(), validationResult.getConfidence());
            return planDTO;

        } catch (Exception e) {
            log.error("生成任务计划失败：{}", e.getMessage(), e);
            return null;
        }
    }

    public void executePlan(TaskPlanDTO planDTO,
                            List<Map<String, Object>> messages,
                            List<McpToolDefinition> availableTools,
                            String sessionId,
                            String model,
                            Long userId,
                            Consumer<AIChatResponse> onResponse) {
        executePlan(planDTO, messages, availableTools, sessionId, model, userId, null, false, onResponse);
    }

    /**
     * 执行任务计划（带意图和知识提取支持）
     *
     * @param planDTO 任务计划
     * @param messages 消息列表
     * @param availableTools 可用工具
     * @param sessionId 会话ID
     * @param model 模型名称
     * @param userId 用户ID
     * @param userIntent 用户意图（可选）
     * @param enableKnowledgeExtraction 是否启用知识提取
     * @param onResponse 响应回调
     */
    public void executePlan(TaskPlanDTO planDTO,
                            List<Map<String, Object>> messages,
                            List<McpToolDefinition> availableTools,
                            String sessionId,
                            String model,
                            Long userId,
                            UserIntent userIntent,
                            boolean enableKnowledgeExtraction,
                            Consumer<AIChatResponse> onResponse) {

        AIModelConfig resolvedConfig = modelConfigService.resolveModelConfig(model, userId);
        if (resolvedConfig == null) {
            AIChatResponse errResp = new AIChatResponse();
            errResp.setType("error");
            errResp.setContent("未找到可用的模型配置，请先在模型配置页面添加模型");
            onResponse.accept(errResp);
            return;
        }
        String effectiveApiUrl = resolvedConfig.getApiUrl();
        String effectiveApiKey = resolvedConfig.getApiKey();
        String effectiveModel = resolvedConfig.getModelId();

        String planId = planDTO.getPlanId();

        AgentTaskPlan plan = planMapper.findByPlanId(planId);
        if (plan == null) {
            log.error("计划不存在：{}", planId);
            sendPlanError(onResponse, sessionId, model, "计划不存在");
            return;
        }

        plan.setStatus("executing");
        plan.setStartedTime(LocalDateTime.now());
        planMapper.updateByPlanId(plan);

        List<AgentTaskStep> steps = stepMapper.findByPlanId(planId);
        if (steps.isEmpty()) {
            log.error("计划没有步骤：{}", planId);
            sendPlanError(onResponse, sessionId, model, "计划没有可执行的步骤");
            return;
        }

        ExecutionCheckpointService.ExecutionState initialState = new ExecutionCheckpointService.ExecutionState();
        initialState.setUserMessage(plan.getOriginalRequest());
        initialState.setModel(model);
        initialState.setUserId(userId);
        initialState.setMessages(new ArrayList<>(messages));
        initialState.setCurrentStepIndex(0);
        initialState.setPlan(planDTO);

        checkpointService.createCheckpoint(
                sessionId, planId, 0, steps.size(),
                "plan_start", ExecutionCheckpointService.CheckpointType.PLAN_START,
                initialState, "计划开始执行"
        );

        StringBuilder allStepResults = new StringBuilder();

        // 【改进】计划执行开始时间，用于计算预计剩余时间
        long planStartTime = System.currentTimeMillis();
        List<Long> stepDurations = new ArrayList<>();  // 记录每步耗时

        for (AgentTaskStep step : steps) {
            if (sessionManager.shouldInterrupt(sessionId)) {
                AgentSessionManager.InterruptMode interruptMode = sessionManager.getInterruptMode(sessionId);
                if (interruptMode == AgentSessionManager.InterruptMode.CANCEL) {
                    log.info("用户取消了任务计划 (session={}, planId={})", sessionId, planId);
                    plan.setStatus("cancelled");
                    plan.setCompletedTime(LocalDateTime.now());
                    planMapper.updateByPlanId(plan);
                    sendPlanError(onResponse, sessionId, model, "⏹ 任务计划已被用户取消。");
                    sessionManager.endExecution(sessionId);
                    return;
                } else if (interruptMode == AgentSessionManager.InterruptMode.APPEND) {
                    String appendContext = sessionManager.getAppendedContext(sessionId);
                    log.info("用户追加了指令到任务计划 (session={}): {}", sessionId, appendContext);
                    sessionManager.clearInterrupt(sessionId);
                    Map<String, Object> appendMsg = new HashMap<>();
                    appendMsg.put("role", "user");
                    appendMsg.put("content", "[用户追加指令] " + appendContext);
                    messages.add(appendMsg);
                }
            }

            stepMapper.updateStatus(step.getStepId(), "executing");

            AIChatResponse stepStartResp = new AIChatResponse();
            stepStartResp.setContent(null);
            stepStartResp.setSessionId(sessionId);
            stepStartResp.setModel(model);
            stepStartResp.setDone(false);
            stepStartResp.setType("step_start");
            Map<String, Object> stepStartData = new HashMap<>();
            stepStartData.put("stepNumber", step.getStepNumber());
            stepStartData.put("description", step.getDescription());
            stepStartData.put("planId", planId);
            stepStartData.put("totalSteps", steps.size());
            stepStartResp.setToolCalls(stepStartData);
            onResponse.accept(stepStartResp);

            long startTime = System.currentTimeMillis();

            try {
                // 【改进】根据步骤复杂度动态计算迭代次数
                int stepMaxIterations = calculateStepMaxIterations(step, steps.size());
                String stepResult = executeStep(step, messages, availableTools, sessionId, effectiveModel, effectiveApiUrl, effectiveApiKey, stepMaxIterations, userId, onResponse);
                long duration = System.currentTimeMillis() - startTime;
                stepDurations.add(duration);  // 【改进】记录步骤耗时

                stepMapper.updateResult(step.getStepId(), "completed", stepResult, null, (int) duration);

                AIChatResponse stepCompleteResp = new AIChatResponse();
                stepCompleteResp.setContent(null);
                stepCompleteResp.setSessionId(sessionId);
                stepCompleteResp.setModel(model);
                stepCompleteResp.setDone(false);
                stepCompleteResp.setType("step_complete");
                Map<String, Object> stepCompleteData = new HashMap<>();
                stepCompleteData.put("stepNumber", step.getStepNumber());
                stepCompleteData.put("description", step.getDescription());
                stepCompleteData.put("duration", duration);
                // 发送完整结果，不再截断
                stepCompleteData.put("result", stepResult);
                stepCompleteData.put("planId", planId);
                stepCompleteData.put("totalSteps", steps.size());
                stepCompleteData.put("completedSteps", step.getStepNumber());
                stepCompleteResp.setToolCalls(stepCompleteData);
                onResponse.accept(stepCompleteResp);

                TaskPlanValidatorService.StepValidationContext validationContext = new TaskPlanValidatorService.StepValidationContext();
                validationContext.setStepDescription(step.getDescription());
                validationContext.setActualResult(stepResult);
                validationContext.setToolUsed(step.getToolName());
                validationContext.setToolSuccess(true);
                validationContext.setStepNumber(step.getStepNumber());
                validationContext.setTotalSteps(steps.size());
                validationContext.setOriginalRequest(plan.getOriginalRequest());

                TaskPlanValidatorService.StepValidationResult validationResult =
                        planValidatorService.validateStepResult(validationContext, model, userId);

                if (!validationResult.isValid() &&
                        validationResult.getAction() == TaskPlanValidatorService.StepValidationResult.ValidationAction.RETRY_STEP) {
                    log.info("步骤 {} 验证失败，尝试重试: {}", step.getStepNumber(), validationResult.getReason());

                    // 【改进】动态配置重试次数，根据步骤重要性调整
                    int maxRetries = 1;  // 默认重试1次
                    String stepDescLower = step.getDescription().toLowerCase();
                    if (stepDescLower.contains("关键") || stepDescLower.contains("核心") || stepDescLower.contains("重要")) {
                        maxRetries = 3;  // 关键步骤重试3次
                    } else if (stepDescLower.contains("获取") || stepDescLower.contains("查询")) {
                        maxRetries = 2;  // 获取类步骤重试2次
                    }
                    log.info("步骤 {} 最大重试次数: {}", step.getStepNumber(), maxRetries);

                    for (int retryAttempt = 0; retryAttempt < maxRetries; retryAttempt++) {
                        try {
                            log.info("步骤 {} 第 {} 次重试", step.getStepNumber(), retryAttempt + 1);
                            String retryResult = executeStep(step, messages, availableTools, sessionId, effectiveModel, effectiveApiUrl, effectiveApiKey, 10, userId, onResponse);
                            if (retryResult != null && !retryResult.isEmpty()) {
                                stepResult = retryResult;
                                log.info("步骤 {} 第 {} 次重试成功", step.getStepNumber(), retryAttempt + 1);
                                break;
                            }
                        } catch (Exception retryEx) {
                            log.warn("步骤 {} 第 {} 次重试失败: {}", step.getStepNumber(), retryAttempt + 1, retryEx.getMessage());
                            if (retryAttempt == maxRetries - 1) {
                                log.error("步骤 {} 所有重试均失败", step.getStepNumber());
                            }
                        }
                    }
                }

                if (stepResult != null && !stepResult.isEmpty()) {
                    allStepResults.append("步骤 ").append(step.getStepNumber()).append("（").append(step.getDescription()).append("）的结果：\n");
                    allStepResults.append(stepResult).append("\n\n");

                    // 【关键修复】将步骤结果添加到 messages 中，让下一个步骤能看到
                    Map<String, Object> stepResultMsg = new HashMap<>();
                    stepResultMsg.put("role", "assistant");
                    stepResultMsg.put("content", "[步骤" + step.getStepNumber() + "完成] " + stepResult);
                    messages.add(stepResultMsg);
                }

                ExecutionCheckpointService.ExecutionState stepState = new ExecutionCheckpointService.ExecutionState();
                stepState.setUserMessage(plan.getOriginalRequest());
                stepState.setModel(model);
                stepState.setUserId(userId);
                stepState.setMessages(new ArrayList<>(messages));
                stepState.setCurrentStepIndex(step.getStepNumber());
                stepState.setPlan(planDTO);
                stepState.setLastResponse(stepResult);

                checkpointService.createCheckpoint(
                        sessionId, planId, step.getStepNumber(), steps.size(),
                        "step_" + step.getStepNumber(), ExecutionCheckpointService.CheckpointType.STEP_COMPLETE,
                        stepState, "步骤 " + step.getStepNumber() + " 完成"
                );

                plan.setCompletedSteps(step.getStepNumber());
                planMapper.updateByPlanId(plan);

                for (TaskStepDTO stepDTO : planDTO.getSteps()) {
                    if (stepDTO.getStepNumber().equals(step.getStepNumber())) {
                        stepDTO.setStatus("completed");
                        stepDTO.setResult(stepResult);
                        break;
                    }
                }
                planDTO.setCompletedSteps(step.getStepNumber());

                // 【改进】步骤完成后执行记忆提取
                if (enableKnowledgeExtraction && stepResult != null && !stepResult.isEmpty()) {
                    try {
                        // 使用记忆宫殿系统提取记忆
                        memoryPalaceService.createFromConversationAsync(userId, sessionId, step.getDescription(), stepResult);
                        log.info("步骤 {} 记忆提取完成", step.getStepNumber());
                    } catch (Exception keEx) {
                        log.warn("步骤 {} 记忆提取失败: {}", step.getStepNumber(), keEx.getMessage());
                    }
                }

                EnhancedPlanExecutor.PlanProgress progress = enhancedPlanExecutor.evaluateProgress(
                    convertToExecutionPlan(planDTO));
                
                AIChatResponse progressResp = new AIChatResponse();
                progressResp.setContent(null);
                progressResp.setSessionId(sessionId);
                progressResp.setModel(model);
                progressResp.setDone(false);
                progressResp.setType("plan_progress");
                Map<String, Object> progressData = new HashMap<>();
                progressData.put("completionRate", progress.getCompletionRate());
                progressData.put("successRate", progress.getSuccessRate());
                progressData.put("healthScore", progress.getHealthScore());
                progressData.put("phase", progress.getPhase().name());
                progressData.put("completedSteps", progress.getCompletedSteps());
                progressData.put("totalSteps", progress.getTotalSteps());

                // 【改进】计算预计剩余时间
                if (!stepDurations.isEmpty()) {
                    long avgStepDuration = (long) stepDurations.stream().mapToLong(Long::longValue).average().orElse(0);
                    int remainingSteps = steps.size() - step.getStepNumber();
                    long estimatedRemainingMs = avgStepDuration * remainingSteps;
                    progressData.put("estimatedRemainingMs", estimatedRemainingMs);
                    progressData.put("avgStepDurationMs", avgStepDuration);
                    progressData.put("elapsedMs", System.currentTimeMillis() - planStartTime);
                }

                progressResp.setToolCalls(progressData);
                onResponse.accept(progressResp);
                
                log.info("计划进度: completionRate={}%, successRate={}%, healthScore={}, phase={}",
                    String.format("%.1f", progress.getCompletionRate() * 100), 
                    String.format("%.1f", progress.getSuccessRate() * 100),
                    String.format("%.2f", progress.getHealthScore()),
                    progress.getPhase());

            } catch (Exception e) {
                long duration = System.currentTimeMillis() - startTime;
                log.error("步骤 {} 执行失败：{}", step.getStepNumber(), e.getMessage(), e);

                stepMapper.updateResult(step.getStepId(), "failed", null, e.getMessage(), (int) duration);

                checkpointService.createErrorCheckpoint(
                        sessionId, planId, step.getStepNumber(), steps.size(),
                        e.getMessage(), messages, model, userId, plan.getOriginalRequest()
                );

                EnhancedPlanExecutor.AdjustmentContext adjustmentContext = new EnhancedPlanExecutor.AdjustmentContext();
                adjustmentContext.setReason(EnhancedPlanExecutor.AdjustmentReason.STEP_FAILED);
                adjustmentContext.setFailedStepIndex(step.getStepNumber() - 1);
                adjustmentContext.setErrorMessage(e.getMessage());
                
                String alternativeTool = findAlternativeTool(step.getToolName());
                adjustmentContext.setAlternativeTool(alternativeTool);
                
                EnhancedPlanExecutor.ExecutionPlan currentPlan = convertToExecutionPlan(planDTO);
                EnhancedPlanExecutor.ExecutionPlan adjustedPlan = enhancedPlanExecutor.adjustPlan(currentPlan, adjustmentContext);
                
                if (adjustedPlan.getAdjustmentCount() > currentPlan.getAdjustmentCount()) {
                    log.info("计划已动态调整: adjustmentCount={}, 添加重试步骤", adjustedPlan.getAdjustmentCount());

                    AIChatResponse adjustResp = new AIChatResponse();
                    adjustResp.setContent(null);
                    adjustResp.setSessionId(sessionId);
                    adjustResp.setModel(model);
                    adjustResp.setDone(false);
                    adjustResp.setType("plan_adjusted");
                    Map<String, Object> adjustData = new HashMap<>();
                    adjustData.put("reason", "step_failed");
                    adjustData.put("failedStep", step.getStepNumber());
                    adjustData.put("alternativeTool", alternativeTool);
                    adjustData.put("adjustmentCount", adjustedPlan.getAdjustmentCount());

                    // 【改进】添加完整的调整后步骤列表
                    List<Map<String, Object>> adjustedStepsList = new ArrayList<>();
                    for (EnhancedPlanExecutor.PlanStep ps : adjustedPlan.getSteps()) {
                        Map<String, Object> stepInfo = new HashMap<>();
                        stepInfo.put("stepNumber", ps.getStepNumber());
                        stepInfo.put("description", ps.getDescription());
                        stepInfo.put("toolName", ps.getToolName());
                        stepInfo.put("status", ps.getStatus() != null ? ps.getStatus().name() : "PENDING");
                        adjustedStepsList.add(stepInfo);
                    }
                    adjustData.put("adjustedSteps", adjustedStepsList);
                    adjustData.put("planSummary", adjustedPlan.getSummary());

                    adjustResp.setToolCalls(adjustData);
                    onResponse.accept(adjustResp);
                    
                    if (alternativeTool != null) {
                        String originalTool = step.getToolName();
                        try {
                            step.setToolName(alternativeTool);
                            String retryResult = executeStep(step, messages, availableTools, sessionId, effectiveModel, effectiveApiUrl, effectiveApiKey, 10, userId, onResponse);
                            
                            if (retryResult != null && !retryResult.isEmpty()) {
                                long retryDuration = System.currentTimeMillis() - startTime;
                                stepMapper.updateResult(step.getStepId(), "completed", retryResult, null, (int) retryDuration);
                                log.info("使用备选工具 {} 重试成功", alternativeTool);
                                
                                allStepResults.append("步骤 ").append(step.getStepNumber()).append("（").append(step.getDescription()).append("）的结果：\n");
                                allStepResults.append(retryResult).append("\n\n");
                                
                                for (TaskStepDTO stepDTO : planDTO.getSteps()) {
                                    if (stepDTO.getStepNumber().equals(step.getStepNumber())) {
                                        stepDTO.setStatus("completed");
                                        stepDTO.setResult(retryResult);
                                        stepDTO.setToolName(alternativeTool);
                                        break;
                                    }
                                }
                                planDTO.setCompletedSteps(step.getStepNumber());
                                
                                EnhancedPlanExecutor.PlanProgress progress = enhancedPlanExecutor.evaluateProgress(
                                    convertToExecutionPlan(planDTO));
                                
                                AIChatResponse progressResp = new AIChatResponse();
                                progressResp.setContent(null);
                                progressResp.setSessionId(sessionId);
                                progressResp.setModel(model);
                                progressResp.setDone(false);
                                progressResp.setType("plan_progress");
                                Map<String, Object> progressData = new HashMap<>();
                                progressData.put("completionRate", progress.getCompletionRate());
                                progressData.put("successRate", progress.getSuccessRate());
                                progressData.put("healthScore", progress.getHealthScore());
                                progressData.put("phase", progress.getPhase().name());
                                progressData.put("completedSteps", progress.getCompletedSteps());
                                progressData.put("totalSteps", progress.getTotalSteps());
                                progressResp.setToolCalls(progressData);
                                onResponse.accept(progressResp);
                                
                                continue;
                            }
                        } catch (Exception retryEx) {
                            log.warn("备选工具 {} 执行失败: {}", alternativeTool, retryEx.getMessage());
                            step.setToolName(originalTool);
                        }
                    }
                }

                TaskPlanValidatorService.StepValidationContext validationContext = new TaskPlanValidatorService.StepValidationContext();
                validationContext.setStepDescription(step.getDescription());
                validationContext.setActualResult(null);
                validationContext.setToolUsed(step.getToolName());
                validationContext.setToolSuccess(false);
                validationContext.setErrorMessage(e.getMessage());
                validationContext.setStepNumber(step.getStepNumber());
                validationContext.setTotalSteps(steps.size());
                validationContext.setOriginalRequest(plan.getOriginalRequest());

                TaskPlanValidatorService.StepValidationResult validationResult = 
                        planValidatorService.validateStepResult(validationContext, model, userId);

                AIChatResponse stepErrorResp = new AIChatResponse();
                stepErrorResp.setContent(null);
                stepErrorResp.setSessionId(sessionId);
                stepErrorResp.setModel(model);
                stepErrorResp.setDone(false);
                stepErrorResp.setType("step_error");
                Map<String, Object> stepErrorData = new HashMap<>();
                stepErrorData.put("stepNumber", step.getStepNumber());
                stepErrorData.put("description", step.getDescription());
                stepErrorData.put("error", e.getMessage());
                stepErrorData.put("planId", planId);
                stepErrorResp.setToolCalls(stepErrorData);
                onResponse.accept(stepErrorResp);

                if (validationResult.getAction() == TaskPlanValidatorService.StepValidationResult.ValidationAction.ADJUST_PLAN) {
                    log.info("步骤 {} 失败，尝试调整计划", step.getStepNumber());

                    TaskPlanValidatorService.PlanAdjustment adjustment = 
                            planValidatorService.generatePlanAdjustment(planDTO, step.getStepNumber(), e.getMessage(), model, userId);

                    if (adjustment.isNeedsAdjustment() && adjustment.getAdjustedSteps() != null) {
                        for (TaskPlanValidatorService.PlanAdjustment.AdjustedStep adjustedStep : adjustment.getAdjustedSteps()) {
                            if (adjustedStep.getStepNumber() > step.getStepNumber()) {
                                for (AgentTaskStep s : steps) {
                                    if (s.getStepNumber() == adjustedStep.getStepNumber()) {
                                        s.setDescription(adjustedStep.getNewDescription());
                                        if (adjustedStep.getNewTool() != null) {
                                            s.setToolName(adjustedStep.getNewTool());
                                        }
                                        stepMapper.updateByStepId(s);
                                        log.info("调整步骤 {}: {}", s.getStepNumber(), adjustedStep.getNewDescription());
                                    }
                                }
                            }
                        }
                    }
                }

                boolean canContinue = checkIfCanContinue(step, steps, e.getMessage());
                if (!canContinue || validationResult.getAction() == TaskPlanValidatorService.StepValidationResult.ValidationAction.ABORT_PLAN) {
                    plan.setStatus("failed");
                    plan.setCompletedTime(LocalDateTime.now());
                    planMapper.updateByPlanId(plan);

                    // 【修复】发送 plan_complete 事件（status=failed）
                    AIChatResponse planFailedResp = new AIChatResponse();
                    planFailedResp.setContent(null);
                    planFailedResp.setSessionId(sessionId);
                    planFailedResp.setModel(model);
                    planFailedResp.setDone(false);
                    planFailedResp.setType("plan_complete");
                    Map<String, Object> failedData = new HashMap<>();
                    failedData.put("planId", planId);
                    failedData.put("status", "failed");
                    failedData.put("failedStep", step.getStepNumber());
                    failedData.put("error", e.getMessage());
                    planFailedResp.setToolCalls(failedData);
                    onResponse.accept(planFailedResp);

                    sendPlanError(onResponse, sessionId, model,
                        "计划执行失败，步骤 " + step.getStepNumber() + " 出错：" + e.getMessage());
                    return;
                }
            }
        }

        plan.setStatus("completed");
        plan.setCompletedTime(LocalDateTime.now());
        plan.setCompletedSteps(steps.size());
        planMapper.updateByPlanId(plan);

        // 【修复】发送 plan_complete 事件，通知前端计划已完成
        AIChatResponse planCompleteResp = new AIChatResponse();
        planCompleteResp.setContent(null);
        planCompleteResp.setSessionId(sessionId);
        planCompleteResp.setModel(model);
        planCompleteResp.setDone(false);
        planCompleteResp.setType("plan_complete");
        Map<String, Object> completeData = new HashMap<>();
        completeData.put("planId", planId);
        completeData.put("totalSteps", steps.size());
        completeData.put("completedSteps", steps.size());
        completeData.put("status", "completed");
        planCompleteResp.setToolCalls(completeData);
        onResponse.accept(planCompleteResp);

        String summaryPrompt = "以下是各步骤的执行结果，请汇总并给用户一个完整的回复。\n\n" +
            "用户原始请求：" + plan.getOriginalRequest() + "\n\n" +
            "执行结果：\n" + allStepResults.toString() + "\n" +
            "请给出最终回复：";

        try {
            Map<String, Object> summaryMsg = new HashMap<>();
            summaryMsg.put("role", "user");
            summaryMsg.put("content", summaryPrompt);
            messages.add(summaryMsg);

            String summary = callLLMNonStream(summaryPrompt, effectiveModel, effectiveApiUrl, effectiveApiKey, sessionId, userId);

            AIChatResponse finalResp = new AIChatResponse();
            finalResp.setContent(summary);
            finalResp.setSessionId(sessionId);
            finalResp.setModel(model);
            finalResp.setDone(true);
            finalResp.setType("result");
            onResponse.accept(finalResp);

        } catch (Exception e) {
            log.error("生成最终总结失败：{}", e.getMessage(), e);
            AIChatResponse fallbackResp = new AIChatResponse();
            fallbackResp.setContent("任务计划已执行完成，共 " + steps.size() + " 个步骤。\n\n" + allStepResults.toString());
            fallbackResp.setSessionId(sessionId);
            fallbackResp.setModel(model);
            fallbackResp.setDone(true);
            fallbackResp.setType("result");
            onResponse.accept(fallbackResp);
        }

        log.info("任务计划执行完成：planId={}, 总步骤={}", planId, steps.size());
    }

    /**
     * 【改进】根据步骤复杂度动态计算迭代次数
     *
     * @param step 步骤信息
     * @param totalSteps 总步骤数
     * @return 该步骤的最大迭代次数
     */
    private int calculateStepMaxIterations(AgentTaskStep step, int totalSteps) {
        // 基础迭代次数
        int baseIterations = 10;

        String stepDesc = step.getDescription() != null ? step.getDescription().toLowerCase() : "";
        String toolName = step.getToolName() != null ? step.getToolName().toLowerCase() : "";

        // 根据步骤类型调整
        if (stepDesc.contains("搜索") || stepDesc.contains("查询") || stepDesc.contains("获取")) {
            // 信息获取类步骤：较少迭代
            baseIterations = 8;
        } else if (stepDesc.contains("分析") || stepDesc.contains("处理") || stepDesc.contains("计算")) {
            // 分析处理类步骤：中等迭代
            baseIterations = 12;
        } else if (stepDesc.contains("验证") || stepDesc.contains("确认") || stepDesc.contains("检查")) {
            // 验证类步骤：较少迭代
            baseIterations = 6;
        } else if (stepDesc.contains("汇总") || stepDesc.contains("总结") || stepDesc.contains("回复")) {
            // 汇总回复类步骤：较多迭代（需要综合信息）
            baseIterations = 15;
        }

        // 根据工具类型调整
        if (toolName.contains("puppeteer") || toolName.contains("browser")) {
            // 浏览器操作需要更多迭代
            baseIterations += 5;
        } else if (toolName.contains("fetch") || toolName.contains("http")) {
            // HTTP 请求较快，减少迭代
            baseIterations -= 2;
        }

        // 根据总步骤数调整（步骤多时，每个步骤分配较少迭代）
        if (totalSteps > 6) {
            baseIterations = (int) (baseIterations * 0.8);
        } else if (totalSteps <= 3) {
            baseIterations = (int) (baseIterations * 1.2);
        }

        // 确保在合理范围内
        return Math.max(5, Math.min(20, baseIterations));
    }

    private String executeStep(AgentTaskStep step,
                               List<Map<String, Object>> messages,
                               List<McpToolDefinition> availableTools,
                               String sessionId,
                               String model,
                               String apiUrl,
                               String apiKey,
                               Consumer<AIChatResponse> onResponse) throws Exception {
        return executeStep(step, messages, availableTools, sessionId, model, apiUrl, apiKey, 10, null, onResponse);
    }

    /**
     * 执行单个步骤（带动态迭代配置）
     *
     * @param step 步骤信息
     * @param messages 消息列表
     * @param availableTools 可用工具
     * @param sessionId 会话ID
     * @param model 模型
     * @param apiUrl API URL
     * @param apiKey API Key
     * @param maxIterations 最大迭代次数（动态配置）
     * @param userId 用户ID（用于成本追踪）
     * @param onResponse 响应回调
     * @return 步骤执行结果
     */
    private String executeStep(AgentTaskStep step,
                               List<Map<String, Object>> messages,
                               List<McpToolDefinition> availableTools,
                               String sessionId,
                               String model,
                               String apiUrl,
                               String apiKey,
                               int maxIterations,
                               Long userId,
                               Consumer<AIChatResponse> onResponse) throws Exception {

        String stepPrompt = "请执行以下步骤：\n\n" +
            "步骤描述：" + step.getDescription() + "\n";

        if (step.getToolName() != null && !step.getToolName().isEmpty()) {
            stepPrompt += "建议使用的工具：" + step.getToolName() + "\n";
        }

        if (messages != null && !messages.isEmpty()) {
            stepPrompt += "\n之前的上下文信息：\n";
            int contextLimit = Math.min(messages.size(), 6);
            for (int i = messages.size() - contextLimit; i < messages.size(); i++) {
                Map<String, Object> msg = messages.get(i);
                String role = (String) msg.get("role");
                String content = (String) msg.get("content");
                if (content != null && !content.isEmpty()) {
                    stepPrompt += role + ": " + (content.length() > 5000 ? content.substring(0, 5000) + "..." : content) + "\n";
                }
            }
        }

        stepPrompt += "\n请执行此步骤并返回结果。如果需要调用工具，请调用工具。如果不需要工具，直接给出结果。";

        StringBuilder stepResultBuilder = new StringBuilder();

        // 【改进】动态配置迭代次数，根据步骤复杂度调整
        int effectiveMaxIterations = maxIterations;
        String stepDesc = step.getDescription().toLowerCase();
        if (stepDesc.contains("分析") || stepDesc.contains("比较") || stepDesc.contains("综合")) {
            effectiveMaxIterations = Math.max(maxIterations, 15);
        } else if (stepDesc.contains("获取") || stepDesc.contains("查询") || stepDesc.contains("搜索")) {
            effectiveMaxIterations = Math.max(maxIterations, 8);
        }
        log.info("步骤 {} 执行，最大迭代次数: {} (原始: {})", step.getStepNumber(), effectiveMaxIterations, maxIterations);

        // 【改进】步骤执行超时控制
        long stepStartTime = System.currentTimeMillis();
        final long STEP_TIMEOUT_MS = 180000; // 3分钟超时
        long totalTokensUsed = 0;  // 成本追踪

        List<Map<String, Object>> stepMessages = new ArrayList<>();

        Map<String, Object> sysMsg = new HashMap<>();
        sysMsg.put("role", "system");
        sysMsg.put("content", "你正在执行任务计划的一个步骤。请专注于完成当前步骤的任务，给出简洁的结果。" +
            "可用工具格式为 server__tool_name。完成当前步骤后，直接给出结果，不要继续执行后续步骤。\n\n" +
            "⚡ SKILLS 工作流提示：\n" +
            "- 如果上一步调用了 load_skill 并返回了技能详情，根据详情中的 Instructions 和 Available Scripts 决定下一步\n" +
            "- 执行技能脚本：使用 run_skill_script(skill_name, script_name, parameters)\n" +
            "- 读取参考文档：使用 read_skill_resource(skill_name, resource_path)\n" +
            "- 生成文档（Word/PDF/Excel）时，必须使用 run_skill_script，不要直接用 bash-sandbox 写文件");
        stepMessages.add(sysMsg);

        Map<String, Object> userStepMsg = new HashMap<>();
        userStepMsg.put("role", "user");
        userStepMsg.put("content", stepPrompt);
        stepMessages.add(userStepMsg);

        for (int iter = 0; iter < effectiveMaxIterations; iter++) {
            // 【改进】步骤超时检查
            if (System.currentTimeMillis() - stepStartTime > STEP_TIMEOUT_MS) {
                log.warn("步骤 {} 执行超时 ({}ms)，强制结束", step.getStepNumber(), STEP_TIMEOUT_MS);
                AIChatResponse timeoutResp = new AIChatResponse();
                timeoutResp.setContent(null);
                timeoutResp.setSessionId(sessionId);
                timeoutResp.setModel(model);
                timeoutResp.setDone(false);
                timeoutResp.setType("step_timeout");
                Map<String, Object> timeoutData = new HashMap<>();
                timeoutData.put("stepNumber", step.getStepNumber());
                timeoutData.put("elapsedMs", System.currentTimeMillis() - stepStartTime);
                timeoutResp.setToolCalls(timeoutData);
                onResponse.accept(timeoutResp);
                break;
            }

            // 【改进】发送步骤进度事件
            if (iter > 0 && iter % 3 == 0) {
                AIChatResponse progressResp = new AIChatResponse();
                progressResp.setContent(null);
                progressResp.setSessionId(sessionId);
                progressResp.setModel(model);
                progressResp.setDone(false);
                progressResp.setType("step_progress");
                Map<String, Object> progressData = new HashMap<>();
                progressData.put("stepNumber", step.getStepNumber());
                progressData.put("iteration", iter);
                progressData.put("maxIterations", effectiveMaxIterations);
                progressResp.setToolCalls(progressData);
                onResponse.accept(progressResp);
            }

            String stepResponse = callLLMWithToolsNonStream(stepMessages, model, availableTools, apiUrl, apiKey, sessionId, userId);
            if (stepResponse == null) {
                break;
            }

            JsonNode responseNode = objectMapper.readTree(stepResponse);
            JsonNode choices = responseNode.path("choices");

            // 【改进】成本追踪：提取 usage 信息
            JsonNode usageNode = responseNode.path("usage");
            if (usageNode != null && !usageNode.isMissingNode()) {
                try {
                    int promptTokens = usageNode.path("prompt_tokens").asInt(0);
                    int completionTokens = usageNode.path("completion_tokens").asInt(0);
                    totalTokensUsed += promptTokens + completionTokens;
                    log.debug("步骤 {} 迭代 {} Token 使用: prompt={}, completion={}, 累计={}",
                        step.getStepNumber(), iter + 1, promptTokens, completionTokens, totalTokensUsed);
                } catch (Exception e) {
                    log.debug("无法解析 usage 信息: {}", e.getMessage());
                }
            }

            if (!choices.isArray() || choices.size() == 0) {
                break;
            }

            JsonNode messageNode = choices.get(0).path("message");
            String content = messageNode.path("content").asText(null);
            JsonNode toolCallsNode = messageNode.path("tool_calls");

            Map<String, Object> assistantMsg = new HashMap<>();
            assistantMsg.put("role", "assistant");
            assistantMsg.put("content", content);
            if (toolCallsNode.isArray() && toolCallsNode.size() > 0) {
                List<Map<String, Object>> toolCallsArray = new ArrayList<>();
                for (JsonNode tcNode : toolCallsNode) {
                    Map<String, Object> tcItem = new HashMap<>();
                    tcItem.put("id", tcNode.path("id").asText());
                    tcItem.put("type", "function");
                    Map<String, Object> function = new HashMap<>();
                    function.put("name", tcNode.path("function").path("name").asText());
                    function.put("arguments", tcNode.path("function").path("arguments").asText());
                    tcItem.put("function", function);
                    toolCallsArray.add(tcItem);
                }
                assistantMsg.put("tool_calls", toolCallsArray);
            }
            stepMessages.add(assistantMsg);

            if (content != null && !content.trim().isEmpty()) {
                stepResultBuilder.append(content);
            }

            if (!toolCallsNode.isArray() || toolCallsNode.size() == 0) {
                break;
            }

            for (JsonNode tcNode : toolCallsNode) {
                String toolCallId = tcNode.path("id").asText();
                String functionName = tcNode.path("function").path("name").asText();
                String argumentsStr = tcNode.path("function").path("arguments").asText();

                String serverName;
                String toolName;
                String[] parts = functionName.split("__", 2);
                if (parts.length == 2) {
                    serverName = parts[0];
                    toolName = parts[1];
                } else {
                    serverName = "";
                    toolName = functionName;
                }

                Map<String, Object> arguments;
                try {
                    arguments = objectMapper.readValue(argumentsStr, Map.class);
                } catch (Exception e) {
                    arguments = new HashMap<>();
                }

                McpToolCallResponse toolResult;
                try {
                    // 【改进】发送工具调用进度事件
                    AIChatResponse toolProgressResp = new AIChatResponse();
                    toolProgressResp.setContent(null);
                    toolProgressResp.setSessionId(sessionId);
                    toolProgressResp.setModel(model);
                    toolProgressResp.setDone(false);
                    toolProgressResp.setType("step_tool_call");
                    Map<String, Object> toolProgressData = new HashMap<>();
                    toolProgressData.put("stepNumber", step.getStepNumber());
                    toolProgressData.put("toolName", functionName);
                    toolProgressData.put("serverName", serverName);
                    toolProgressResp.setToolCalls(toolProgressData);
                    onResponse.accept(toolProgressResp);

                    toolResult = mcpHostService.callTool(serverName, toolName, arguments, onResponse);
                } catch (Exception e) {
                    toolResult = new McpToolCallResponse();
                    toolResult.setSuccess(false);
                    toolResult.setError(e.getMessage());
                    List<McpToolCallResponse.ContentItem> items = new ArrayList<>();
                    McpToolCallResponse.ContentItem item = new McpToolCallResponse.ContentItem();
                    item.setType("text");
                    item.setText("工具调用失败：" + e.getMessage());
                    items.add(item);
                    toolResult.setContent(items);
                }

                String resultContent = toolResult.isSuccess()
                    ? (toolResult.getContent() != null ? toolResult.getContent().stream()
                        .filter(c -> "text".equals(c.getType()) && c.getText() != null)
                        .map(McpToolCallResponse.ContentItem::getText)
                        .reduce((a, b) -> a + "\n" + b).orElse("") : "")
                    : "工具调用失败：" + toolResult.getError();

                // 【修复】对于关键工具（load_skill, read_skill_resource等）不截断结果
                // 这些工具返回的内容对后续执行至关重要
                boolean isCriticalTool = "load_skill".equals(functionName)
                    || "read_skill_resource".equals(functionName)
                    || functionName.contains("get_skill");

                if (!isCriticalTool && resultContent.length() > 5000) {
                    resultContent = resultContent.substring(0, 5000) + "...[截断，完整内容请查看日志]";
                }

                Map<String, Object> toolMsg = new HashMap<>();
                toolMsg.put("role", "tool");
                toolMsg.put("tool_call_id", toolCallId);
                toolMsg.put("content", resultContent);
                toolMsg.put("name", functionName);
                stepMessages.add(toolMsg);

                if (toolResult.isSuccess()) {
                    stepResultBuilder.append("\n[").append(functionName).append(" 结果]: ").append(resultContent).append("\n");
                }
            }
        }

        // 【改进】记录步骤成本
        if (totalTokensUsed > 0) {
            try {
                costTrackingService.recordApiCall(sessionId, userId, model,
                    (long) (totalTokensUsed * 0.7), (long) (totalTokensUsed * 0.3),
                    new HashMap<>());
                log.info("步骤 {} 成本记录: model={}, totalTokens={}, userId={}",
                    step.getStepNumber(), model, totalTokensUsed, userId);
            } catch (Exception e) {
                log.warn("记录步骤成本失败: {}", e.getMessage());
            }
        }

        return stepResultBuilder.toString().trim();
    }

    private boolean checkIfCanContinue(AgentTaskStep failedStep, List<AgentTaskStep> allSteps, String errorMsg) {
        boolean isLastStep = failedStep.getStepNumber().equals(allSteps.get(allSteps.size() - 1).getStepNumber());
        if (isLastStep) {
            return false;
        }

        String description = failedStep.getDescription().toLowerCase();
        if (description.contains("汇总") || description.contains("总结") || description.contains("回复")) {
            return true;
        }

        return false;
    }

    private String callLLMNonStream(String prompt, String model, String apiUrl, String apiKey, String sessionId, Long userId) throws Exception {
        List<Map<String, Object>> messages = new ArrayList<>();
        Map<String, Object> msg = new HashMap<>();
        msg.put("role", "user");
        msg.put("content", prompt);
        messages.add(msg);

        LLMRequest request = LLMRequest.fromConfig(model, apiUrl, apiKey)
                .toBuilder()
                .messages(messages)
                .sessionId(sessionId)
                .userId(userId)
                .build();

        log.info("[TaskPlanner] 调用 LLM (chatComplete): model={}", model);

        LLMCompleteResponse response = llmClient.chatComplete(request);
        if (!response.isSuccess()) {
            throw new RuntimeException("LLM API 请求失败: " + (response.getError() != null ? response.getError() : "unknown error"));
        }
        return response.getContent();
    }

    private String callLLMWithToolsNonStream(List<Map<String, Object>> messages,
                                             String model,
                                             List<McpToolDefinition> tools,
                                             String apiUrl,
                                             String apiKey,
                                             String sessionId,
                                             Long userId) throws Exception {
        List<ToolDefinitionForLLM> toolDefs = new ArrayList<>();
        if (tools != null && !tools.isEmpty()) {
            for (McpToolDefinition tool : tools) {
                if (tool.getName() == null || tool.getName().isEmpty() || tool.getName().startsWith("[未启动]")) {
                    continue;
                }
                ToolDefinitionForLLM def = ToolDefinitionForLLM.fromMcpTool(tool);
                if (def != null) {
                    toolDefs.add(def);
                }
            }
        }

        LLMRequest request = LLMRequest.fromConfig(model, apiUrl, apiKey)
                .toBuilder()
                .messages(messages)
                .sessionId(sessionId)
                .userId(userId)
                .build();

        log.info("[TaskPlanner] 调用 LLM (chatCompleteWithTools): model={}, tools={}", model, toolDefs.size());

        LLMCompleteResponse response;
        if (!toolDefs.isEmpty()) {
            response = llmClient.chatCompleteWithTools(request, toolDefs);
        } else {
            response = llmClient.chatComplete(request);
        }

        if (!response.isSuccess()) {
            throw new RuntimeException("LLM API 请求失败: " + (response.getError() != null ? response.getError() : "unknown error"));
        }

        Map<String, Object> result = new HashMap<>();
        Map<String, Object> choice = new HashMap<>();
        Map<String, Object> message = new HashMap<>();

        if (response.hasContent()) {
            message.put("content", response.getContent());
        }

        if (response.hasToolCalls()) {
            List<Map<String, Object>> toolCallsArray = new ArrayList<>();
            for (LLMCompleteResponse.ToolCall tc : response.getToolCalls()) {
                Map<String, Object> tcItem = new HashMap<>();
                tcItem.put("id", tc.getId());
                Map<String, Object> function = new HashMap<>();
                function.put("name", tc.getName());
                function.put("arguments", tc.getArgumentsStr());
                tcItem.put("function", function);
                toolCallsArray.add(tcItem);
            }
            message.put("tool_calls", toolCallsArray);
        }

        choice.put("message", message);
        result.put("choices", Collections.singletonList(choice));

        // 【改进】添加 usage 信息用于成本追踪
        if (response.getUsage() != null) {
            result.put("usage", response.getUsage());
        }

        return objectMapper.writeValueAsString(result);
    }

    private String buildToolsDescription(List<McpToolDefinition> tools) {
        if (tools == null || tools.isEmpty()) {
            return "无可用工具";
        }
        StringBuilder sb = new StringBuilder();
        for (McpToolDefinition tool : tools) {
            if (tool.getName() == null || tool.getName().startsWith("[未启动]")) continue;
            String desc = tool.getDescription() != null ? tool.getDescription() : "";
            if (desc.length() > 100) desc = desc.substring(0, 97) + "...";
            sb.append("- ").append(tool.getName()).append(": ").append(desc).append("\n");
        }
        return sb.toString();
    }

    private String extractJsonField(String json, String field) {
        String pattern = "\"" + field + "\"";
        int idx = json.indexOf(pattern);
        if (idx < 0) return "";
        int colonIdx = json.indexOf(":", idx);
        if (colonIdx < 0) return "";
        int valueStart = json.indexOf("\"", colonIdx);
        if (valueStart < 0) return "";
        int valueEnd = json.indexOf("\"", valueStart + 1);
        if (valueEnd < 0) return "";
        return json.substring(valueStart + 1, valueEnd);
    }

    private String extractJson(String text) {
        if (text == null || text.isEmpty()) return text;

        // 去除思考标签
        String cleaned = text;
        String[] thinkingTags = {"<think>", "</think>", "<reasoning>", "</reasoning>",
                "<thought>", "</thought>", "<reflection>", "</reflection>"};
        for (String tag : thinkingTags) {
            cleaned = cleaned.replace(tag, "");
        }
        // 移除 <think>...</think> 块（多行）
        cleaned = cleaned.replaceAll("<think[\\s\\S]*?</think>", "");
        cleaned = cleaned.replaceAll("<reasoning[\\s\\S]*?</reasoning>", "");

        int braceStart = cleaned.indexOf('{');
        int braceEnd = cleaned.lastIndexOf('}');
        if (braceStart >= 0 && braceEnd > braceStart) {
            String json = cleaned.substring(braceStart, braceEnd + 1);
            // 尝试修复常见的 LLM JSON 错误
            json = fixCommonJsonErrors(json);
            return json;
        }
        return cleaned.trim();
    }

    /**
     * 修复 LLM 返回的常见 JSON 错误
     * 例如：{"steps": [{"tool": ""}]} 末尾多余的 }
     */
    private String fixCommonJsonErrors(String json) {
        if (json == null || json.isEmpty()) return json;

        // 先尝试直接解析，如果成功就不修复
        try {
            objectMapper.readTree(json);
            return json;
        } catch (Exception ignored) {
            // 需要修复
        }

        // 修复策略：从外层开始，逐步尝试移除末尾多余的 }
        String fixed = json.trim();
        // 统计 { 和 } 的数量
        long openBraces = fixed.chars().filter(c -> c == '{').count();
        long closeBraces = fixed.chars().filter(c -> c == '}').count();

        if (closeBraces > openBraces) {
            // 从末尾移除多余的 }
            int excess = (int) (closeBraces - openBraces);
            int removed = 0;
            StringBuilder sb = new StringBuilder(fixed);
            for (int i = sb.length() - 1; i >= 0 && removed < excess; i--) {
                if (sb.charAt(i) == '}') {
                    sb.deleteCharAt(i);
                    removed++;
                }
            }
            fixed = sb.toString();
            log.info("修复 JSON：移除了 {} 个多余的 }}", removed);
        }

        // 再次验证
        try {
            objectMapper.readTree(fixed);
            return fixed;
        } catch (Exception e) {
            log.warn("JSON 修复后仍无法解析: {}", e.getMessage());
            return json; // 返回原始内容
        }
    }

    private void sendPlanError(Consumer<AIChatResponse> onResponse, String sessionId, String model, String message) {
        AIChatResponse resp = new AIChatResponse();
        resp.setContent(message);
        resp.setSessionId(sessionId);
        resp.setModel(model);
        resp.setDone(true);
        resp.setType("result");
        onResponse.accept(resp);
    }

    public TaskPlanDTO getPlanByPlanId(String planId) {
        AgentTaskPlan plan = planMapper.findByPlanId(planId);
        if (plan == null) return null;

        List<AgentTaskStep> steps = stepMapper.findByPlanId(planId);

        TaskPlanDTO dto = new TaskPlanDTO();
        dto.setPlanId(plan.getPlanId());
        dto.setSessionId(plan.getSessionId());
        dto.setUserId(plan.getUserId());
        dto.setOriginalRequest(plan.getOriginalRequest());
        dto.setPlanSummary(plan.getPlanSummary());
        dto.setStatus(plan.getStatus());
        dto.setTotalSteps(plan.getTotalSteps());
        dto.setCompletedSteps(plan.getCompletedSteps());

        List<TaskStepDTO> stepDTOs = new ArrayList<>();
        for (AgentTaskStep step : steps) {
            TaskStepDTO stepDTO = new TaskStepDTO();
            stepDTO.setStepId(step.getStepId());
            stepDTO.setStepNumber(step.getStepNumber());
            stepDTO.setDescription(step.getDescription());
            stepDTO.setStatus(step.getStatus());
            stepDTO.setToolName(step.getToolName());
            stepDTO.setResult(step.getResult());
            stepDTO.setError(step.getError());
            stepDTO.setExecutionTimeMs(step.getExecutionTimeMs());
            stepDTOs.add(stepDTO);
        }
        dto.setSteps(stepDTOs);
        return dto;
    }

    public List<TaskPlanDTO> getPlansBySessionId(String sessionId) {
        List<AgentTaskPlan> plans = planMapper.findBySessionId(sessionId);
        List<TaskPlanDTO> result = new ArrayList<>();
        for (AgentTaskPlan plan : plans) {
            result.add(getPlanByPlanId(plan.getPlanId()));
        }
        return result;
    }

    public List<TaskPlanDTO> getPlansByUserId(Long userId, int limit) {
        List<AgentTaskPlan> plans = planMapper.findByUserId(userId, limit);
        List<TaskPlanDTO> result = new ArrayList<>();
        for (AgentTaskPlan plan : plans) {
            result.add(getPlanByPlanId(plan.getPlanId()));
        }
        return result;
    }

    private EnhancedPlanExecutor.ExecutionPlan convertToExecutionPlan(TaskPlanDTO dto) {
        EnhancedPlanExecutor.ExecutionPlan plan = new EnhancedPlanExecutor.ExecutionPlan();
        plan.setPlanId(dto.getPlanId());
        plan.setSessionId(dto.getSessionId());
        plan.setOriginalRequest(dto.getOriginalRequest());
        plan.setSummary(dto.getPlanSummary());
        plan.setTotalSteps(dto.getTotalSteps() != null ? dto.getTotalSteps() : 0);
        plan.setCompletedSteps(dto.getCompletedSteps() != null ? dto.getCompletedSteps() : 0);
        plan.setCreatedAt(LocalDateTime.now());
        
        List<EnhancedPlanExecutor.PlanStep> steps = new ArrayList<>();
        if (dto.getSteps() != null) {
            for (TaskStepDTO stepDTO : dto.getSteps()) {
                EnhancedPlanExecutor.PlanStep step = new EnhancedPlanExecutor.PlanStep();
                step.setStepId(stepDTO.getStepId());
                step.setStepNumber(stepDTO.getStepNumber() != null ? stepDTO.getStepNumber() : 0);
                step.setDescription(stepDTO.getDescription());
                step.setToolName(stepDTO.getToolName());
                step.setStatus(convertStatus(stepDTO.getStatus()));
                step.setResult(stepDTO.getResult());
                step.setError(stepDTO.getError());
                steps.add(step);
            }
        }
        plan.setSteps(steps);
        return plan;
    }

    private TaskPlanDTO convertToTaskPlanDTO(EnhancedPlanExecutor.ExecutionPlan plan) {
        TaskPlanDTO dto = new TaskPlanDTO();
        dto.setPlanId(plan.getPlanId());
        dto.setSessionId(plan.getSessionId());
        dto.setOriginalRequest(plan.getOriginalRequest());
        dto.setPlanSummary(plan.getSummary());
        dto.setTotalSteps(plan.getTotalSteps());
        dto.setCompletedSteps(plan.getCompletedSteps());
        dto.setStatus("executing");
        
        List<TaskStepDTO> stepDTOs = new ArrayList<>();
        if (plan.getSteps() != null) {
            for (EnhancedPlanExecutor.PlanStep step : plan.getSteps()) {
                TaskStepDTO stepDTO = new TaskStepDTO();
                stepDTO.setStepId(step.getStepId());
                stepDTO.setStepNumber(step.getStepNumber());
                stepDTO.setDescription(step.getDescription());
                stepDTO.setToolName(step.getToolName());
                stepDTO.setStatus(convertStatusToString(step.getStatus()));
                stepDTO.setResult(step.getResult() != null ? String.valueOf(step.getResult()) : null);
                stepDTO.setError(step.getError());
                stepDTOs.add(stepDTO);
            }
        }
        dto.setSteps(stepDTOs);
        return dto;
    }

    private EnhancedPlanExecutor.StepStatus convertStatus(String status) {
        if (status == null) return EnhancedPlanExecutor.StepStatus.PENDING;
        switch (status.toLowerCase()) {
            case "completed": return EnhancedPlanExecutor.StepStatus.COMPLETED;
            case "failed": return EnhancedPlanExecutor.StepStatus.FAILED;
            case "running": case "executing": return EnhancedPlanExecutor.StepStatus.RUNNING;
            case "skipped": return EnhancedPlanExecutor.StepStatus.SKIPPED;
            default: return EnhancedPlanExecutor.StepStatus.PENDING;
        }
    }

    private String convertStatusToString(EnhancedPlanExecutor.StepStatus status) {
        if (status == null) return "pending";
        switch (status) {
            case COMPLETED: return "completed";
            case FAILED: return "failed";
            case RUNNING: return "executing";
            case SKIPPED: return "skipped";
            default: return "pending";
        }
    }

    public EnhancedPlanExecutor.PlanProgress getPlanProgress(String planId) {
        TaskPlanDTO dto = getPlanByPlanId(planId);
        if (dto == null) return null;
        EnhancedPlanExecutor.ExecutionPlan plan = convertToExecutionPlan(dto);
        return enhancedPlanExecutor.evaluateProgress(plan);
    }

    private String findAlternativeTool(String failedTool) {
        if (failedTool == null) return null;
        
        Map<String, String> alternatives = new HashMap<>();
        alternatives.put("fetch", "puppeteer_navigate");
        alternatives.put("puppeteer_navigate", "fetch");
        alternatives.put("brave_web_search", "tavily_search");
        alternatives.put("tavily_search", "brave_web_search");
        alternatives.put("google_search", "bing_search");
        alternatives.put("bing_search", "google_search");
        alternatives.put("read_file", "filesystem_read");
        alternatives.put("write_file", "filesystem_write");
        
        return alternatives.get(failedTool);
    }

    public TaskPlanDTO replanAfterFailure(String planId, String errorMessage, int completedStepCount) {
        TaskPlanDTO originalPlan = getPlanByPlanId(planId);
        if (originalPlan == null) return null;
        
        EnhancedPlanExecutor.ExecutionPlan executionPlan = convertToExecutionPlan(originalPlan);
        
        EnhancedPlanExecutor.ReplanContext context = new EnhancedPlanExecutor.ReplanContext();
        context.setReason(EnhancedPlanExecutor.ReplanReason.PARTIAL_FAILURE);
        context.setErrorMessage(errorMessage);
        context.setCompletedStepCount(completedStepCount);
        
        EnhancedPlanExecutor.ExecutionPlan newPlan = enhancedPlanExecutor.replan(executionPlan, context);
        
        return convertToTaskPlanDTO(newPlan);
    }
}
