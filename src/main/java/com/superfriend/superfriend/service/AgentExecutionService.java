package com.superfriend.superfriend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.superfriend.superfriend.agent.context.SmartCompressionStrategy;
import com.superfriend.superfriend.agent.error.ErrorRecoveryManager;
import com.superfriend.superfriend.agent.error.ErrorType;
import com.superfriend.superfriend.agent.orchestration.AgentEvent;
import com.superfriend.superfriend.agent.reasoning.ReasoningChain;
import com.superfriend.superfriend.agent.skill.Skill;
import com.superfriend.superfriend.agent.skill.SkillContext;
import com.superfriend.superfriend.agent.skill.SkillExecutor;
import com.superfriend.superfriend.agent.skill.SkillResult;
import com.superfriend.superfriend.constant.ChatMode;
import com.superfriend.superfriend.dto.*;
import com.superfriend.superfriend.entity.AIModelConfig;
import com.superfriend.superfriend.entity.ChatCompression;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
<<<<<<< HEAD
import org.springframework.beans.factory.annotation.Qualifier;
=======
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

@Slf4j
@Service
public class AgentExecutionService {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    @Lazy
    private McpHostService mcpHostService;

    @Autowired
    @Lazy
    private AIModelConfigService modelConfigService;

    @Autowired
    @Lazy
    private ContextCompressionService compressionService;

    @Autowired
    @Lazy
    private SmartCompressionStrategy smartCompressionStrategy;

    @Autowired
    @Lazy
    private PermissionService permissionService;

    @Autowired
    @Lazy
    private ApprovalService approvalService;

    @Autowired
    @Lazy
    private AgentSessionManager sessionManager;

    @Autowired
    @Lazy
    private TaskPlannerService taskPlannerService;

    @Autowired
    @Lazy
    private ContextMangerService contextMangerService;

    @Autowired
    @Lazy
    private ErrorRecoveryManager errorRecoveryManager;

    @Autowired
    @Lazy
    private SkillExecutor skillExecutor;

    @Autowired
    @Lazy
    private ReflectionService reflectionService;

    @Autowired
    @Lazy
    private FallbackStrategyService fallbackStrategyService;

    @Autowired
    @Lazy
    private ExecutionTraceService traceService;

    @Autowired
    @Lazy
    private SemanticToolSelectionService semanticToolSelectionService;

    @Autowired
    @Lazy
    private AgentMetricsService metricsService;

    @Autowired
    @Lazy
    private CostTrackingService costTrackingService;

    @Autowired
    @Lazy
    private ObservabilityService observabilityService;

    @Autowired
    @Lazy
    private LLMClient llmClient;

    @Autowired
    @Lazy
    private KnowledgeGraphService knowledgeGraphService;

    @Autowired
    @Lazy
<<<<<<< HEAD
    private MemoryPalaceService memoryPalaceService;
=======
    private KnowledgeExtractorService knowledgeExtractorService;

    @Autowired
    @Lazy
    private UserProfileService userProfileService;
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2

    @Autowired
    @Lazy
    private SystemContextBuilder systemContextBuilder;

    @Autowired
    @Lazy
    private OrioSearchService orioSearchService;

    @Autowired
    @Lazy
    private HistoryMessageProcessor historyMessageProcessor;

    @Autowired
    @Lazy
    private MessageContentBuilder messageContentBuilder;

    @Autowired
    @Lazy
    private com.superfriend.superfriend.agent.config.DynamicConfigManager dynamicConfigManager;

    @Autowired
    @Lazy
<<<<<<< HEAD
    private SessionFileIndexService sessionFileIndexService;

    @Autowired
    @Lazy
    private com.superfriend.superfriend.agent.planner.TaskAnalyzer taskAnalyzer;

    @Autowired
    @Lazy
    private BashSandboxService bashSandboxService;

    @Autowired
    @Lazy
    private SandboxExecutionGuideService sandboxExecutionGuideService;

    @Autowired
    @Qualifier("parallelToolExecutor")
    private ExecutorService parallelExecutor;

    private static final int DEFAULT_MAX_ITERATIONS = 15;
    private static final int DEFAULT_MAX_CONSECUTIVE_ERRORS = 10;
    // 提高重复调用阈值，允许模型在成功执行后继续调用其他工具（如生成文件后发送文件）
    private static final int DEFAULT_REPEAT_CALL_THRESHOLD = 5;
    private static final long DEFAULT_TOOL_TIMEOUT_MS = 120000;
    private static final int MIN_RESULT_FOR_DIRECT_ANSWER = 200;
    private static final int MAX_REASONING_STEPS = 100;
    private static final long SEARCH_TIMEOUT_MS = 30000; // Web搜索超时30秒
=======
    private com.superfriend.superfriend.agent.planner.TaskAnalyzer taskAnalyzer;

    private static final int DEFAULT_MAX_ITERATIONS = 15;
    private static final int DEFAULT_MAX_CONSECUTIVE_ERRORS = 10;
    private static final int DEFAULT_REPEAT_CALL_THRESHOLD = 3;
    private static final long DEFAULT_TOOL_TIMEOUT_MS = 120000;
    private static final int MIN_RESULT_FOR_DIRECT_ANSWER = 200;
    private static final int MAX_REASONING_STEPS = 100;

    private final ExecutorService parallelExecutor = Executors.newFixedThreadPool(6);
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2

    private enum QueryType {
        FACTUAL,
        EXPLORATORY,
        OPERATIONAL
    }

    @Data
    public static class ExecutionConfig {
        private int maxIterations = DEFAULT_MAX_ITERATIONS;
        private int maxConsecutiveErrors = DEFAULT_MAX_CONSECUTIVE_ERRORS;
        private int repeatCallThreshold = DEFAULT_REPEAT_CALL_THRESHOLD;
        private long toolTimeoutMs = DEFAULT_TOOL_TIMEOUT_MS;
        private boolean enableSkills = true;
        private boolean enableReflection = true;
        private boolean enableParallelExecution = true;
        private boolean enableProgressTracking = true;
        private boolean enableIntelligentToolSelection = true;
        private boolean enableWebSearchPreload = true;
        private boolean forceReactMode = false;
        private boolean forcePlanExecuteMode = false;
        private boolean enableKnowledgeExtraction = false;
        private String chatMode;
        private com.superfriend.superfriend.entity.Prompt.Mode promptMode = com.superfriend.superfriend.entity.Prompt.Mode.MCP;
<<<<<<< HEAD
=======
        // 【改进】可配置的模式切换阈值
        private int upgradeToPlanExecuteThreshold = 5;  // 连续错误达到此值时升级到 Plan-Execute
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
    }

    @Data
    public static class ExecutionContext {
        private final String sessionId;
        private final String userMessage;
        private final Long userId;
        private String model;
        private String actualModel;
        private String apiUrl;
        private String apiKey;
        private String traceId;
        private String metricsExecutionId;
        private String observabilityTraceId;
        private String chatMode;
        private com.superfriend.superfriend.entity.Prompt.Mode promptMode = com.superfriend.superfriend.entity.Prompt.Mode.MCP;
        private boolean enableKnowledgeExtraction = false;

        // 用户意图（从 ChatOrchestrator 传递）
        private UserIntent userIntent;

        // 多模态支持：存储原始用户消息内容（可能是 String 或 List<Map<String, Object>>）
        private Object userMessageContent;

        private final List<Map<String, Object>> messages = new ArrayList<>();
        private final List<McpToolDefinition> availableTools = new ArrayList<>();
        private final List<McpToolDefinition> visibleTools = new ArrayList<>();
        private final List<String> recentToolCallSignatures = new ArrayList<>();
        private final Map<String, ToolExecutionResult> toolResults = new ConcurrentHashMap<>();

        // ReasoningChain for structured reasoning
        private final ReasoningChain reasoningChain;

        // Skill detection results
        private final List<Skill> detectedSkills = new ArrayList<>();
        private final Map<String, Double> skillConfidenceScores = new LinkedHashMap<>();
        private Skill activeSkill;

        // Progressive disclosure level
        private int toolVisibilityLevel = 0;

        // Event queue for streaming
        private final ConcurrentLinkedQueue<AgentEvent> eventQueue = new ConcurrentLinkedQueue<>();
        private final List<AgentEvent> eventLog = Collections.synchronizedList(new ArrayList<>());

        private int currentIteration = 0;
        private int consecutiveErrors = 0;
<<<<<<< HEAD
        private int consecutiveNoProgress = 0;
        private int consecutiveCorrections = 0;
        private int lastEvidenceSize = 0;
        private int lastToolCallCount = 0;
=======
        private int consecutiveNoProgress = 0;  // 用于停滞检测
        private int modeSwitchCount = 0;  // 【改进】模式切换计数器
        private static final int MAX_MODE_SWITCHES = 2;  // 最大切换次数
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
        private double progress = 0.0;
        private String currentPhase = "initializing";
        private boolean executionEnded = false;
        private boolean forceTerminated = false;
        private int maxIterations = DEFAULT_MAX_ITERATIONS;
        private int maxConsecutiveErrors = DEFAULT_MAX_CONSECUTIVE_ERRORS;

        private ReflectionResult lastReflection;

        // Evidence store for intermediate results
        private final Map<String, Object> evidenceStore = new LinkedHashMap<>();
        private final Map<String, Object> workingMemory = new LinkedHashMap<>();

        // URL访问历史追踪 - 跨工具共享（线程安全）
        private final Set<String> visitedUrls = Collections.synchronizedSet(new LinkedHashSet<>());
<<<<<<< HEAD
        private final Map<String, String> urlAccessResults = new ConcurrentHashMap<>();
        private final Map<String, Long> urlAccessTimestamps = new ConcurrentHashMap<>();
        private final Map<String, Integer> urlVisitCounts = new ConcurrentHashMap<>();

        // 内存保护：限制URL历史大小
        private static final int MAX_URL_HISTORY = 50;
        private static final int MAX_URL_RESULT_LENGTH = 500;
=======
        private final Map<String, String> urlAccessResults = new ConcurrentHashMap<>(); // url -> 结果摘要
        private final Map<String, Long> urlAccessTimestamps = new ConcurrentHashMap<>(); // url -> 访问时间戳
        private final Map<String, Integer> urlVisitCounts = new ConcurrentHashMap<>(); // url -> 访问次数（用于强制终止检测）

        // 内存保护：限制URL历史大小
        private static final int MAX_URL_HISTORY = 50;
        private static final int MAX_URL_RESULT_LENGTH = 500; // 结果摘要最大长度
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2

        // Dynamic configuration from DynamicConfigManager
        private com.superfriend.superfriend.agent.config.DynamicConfigManager.SessionConfig sessionConfig;

        // Token usage tracking (cumulative)
        private long cumulativeInputTokens = 0;
        private long cumulativeOutputTokens = 0;

        // Last error information for error pattern detection
        private ErrorType lastErrorType = null;
        private String lastErrorMessage = null;

        // 【改进】缓存任务分析结果，避免重复分析
        private com.superfriend.superfriend.agent.planner.TaskAnalysis taskAnalysis;

<<<<<<< HEAD
        // 【新增】沙箱会话管理
        private String bashSandboxSessionId;
        private String bashSandboxWorkingDir;

=======
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
        public ExecutionContext(String sessionId, String userMessage, Long userId) {
            this.sessionId = sessionId;
            this.userMessage = userMessage;
            this.userId = userId;
            this.reasoningChain = new ReasoningChain(sessionId);
        }

        /**
         * 多模态构造函数
         * @param sessionId 会话ID
         * @param userMessage 文本消息（用于日志和简单场景）
         * @param userMessageContent 完整的用户消息内容（支持多模态）
         * @param userId 用户ID
         */
        public ExecutionContext(String sessionId, String userMessage, Object userMessageContent, Long userId) {
            this.sessionId = sessionId;
            this.userMessage = userMessage;
            this.userMessageContent = userMessageContent;
            this.userId = userId;
            this.reasoningChain = new ReasoningChain(sessionId);
        }

        /**
         * 判断是否为多模态消息
         */
        public boolean isMultimodal() {
            return userMessageContent != null && userMessageContent instanceof List;
        }

        /**
         * 获取有效的用户消息内容
         * 优先返回多模态内容，否则返回文本消息
         */
        public Object getEffectiveUserMessageContent() {
            if (userMessageContent != null) {
                return userMessageContent;
            }
            return userMessage;
        }

        // ReasoningChain delegate methods
        public void addThought(String thought) {
            reasoningChain.addThought(thought);
        }

        public void addReflection(String reflection, double confidence) {
            reasoningChain.addReflection(reflection, confidence);
        }

        public void recordAction(String toolName, Map<String, Object> params, Object result, boolean success) {
            reasoningChain.addAction(toolName, params);
            reasoningChain.addObservation(result != null ? String.valueOf(result) : "(空结果)", success);
        }

        public void addDecision(String decision, String rationale) {
            reasoningChain.addDecision(decision, rationale);
        }

        public void emitEvent(AgentEvent event) {
            eventQueue.offer(event);
            eventLog.add(event);
        }

        public AgentEvent pollEvent() {
            return eventQueue.poll();
        }

        public boolean hasPendingEvents() {
            return !eventQueue.isEmpty();
        }

        public void addEvidence(String key, Object value) {
            evidenceStore.put(key, value);
        }

        public Object getEvidence(String key) {
            return evidenceStore.get(key);
        }

        // URL规范化 - 确保相同URL的不同写法被视为相同
        private String normalizeUrl(String url) {
            if (url == null || url.isEmpty()) return url;
            // 移除末尾斜杠，转小写
            String normalized = url.trim().toLowerCase();
            if (normalized.endsWith("/") && !normalized.endsWith("//")) {
                normalized = normalized.substring(0, normalized.length() - 1);
            }
            return normalized;
        }

        // URL访问追踪方法
        public void recordUrlAccess(String url, String resultSummary) {
            String normalizedUrl = normalizeUrl(url);

            // 内存保护：限制历史大小
            if (visitedUrls.size() >= MAX_URL_HISTORY && !visitedUrls.contains(normalizedUrl)) {
<<<<<<< HEAD
=======
                // 移除最旧的条目（LinkedHashSet保持插入顺序）
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
                synchronized (visitedUrls) {
                    if (!visitedUrls.isEmpty()) {
                        String oldest = visitedUrls.iterator().next();
                        visitedUrls.remove(oldest);
                        urlAccessResults.remove(oldest);
                        urlAccessTimestamps.remove(oldest);
                        urlVisitCounts.remove(oldest);
<<<<<<< HEAD
=======
                        log.debug("URL历史已满，移除最旧条目: {}", oldest);
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
                    }
                }
            }

            visitedUrls.add(normalizedUrl);

<<<<<<< HEAD
=======
            // 限制结果摘要长度
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
            String trimmedSummary = resultSummary;
            if (trimmedSummary != null && trimmedSummary.length() > MAX_URL_RESULT_LENGTH) {
                trimmedSummary = trimmedSummary.substring(0, MAX_URL_RESULT_LENGTH) + "...";
            }

            urlAccessResults.put(normalizedUrl, trimmedSummary);
            urlAccessTimestamps.put(normalizedUrl, System.currentTimeMillis());
            urlVisitCounts.merge(normalizedUrl, 1, Integer::sum);
        }

        public boolean hasVisitedUrl(String url) {
            return visitedUrls.contains(normalizeUrl(url));
        }

        public String getUrlAccessResult(String url) {
            return urlAccessResults.get(normalizeUrl(url));
        }

        public int getUrlVisitCount(String url) {
            return urlVisitCounts.getOrDefault(normalizeUrl(url), 0);
        }

        public Set<String> getVisitedUrls() {
            return Collections.unmodifiableSet(visitedUrls);
        }

        // 检查是否有URL被重复访问超过阈值
        public boolean hasExcessiveUrlRevisits(int threshold) {
            return urlVisitCounts.values().stream().anyMatch(count -> count >= threshold);
        }

        public String getExcessiveRevisitWarning(int threshold) {
            List<String> excessiveUrls = urlVisitCounts.entrySet().stream()
                    .filter(e -> e.getValue() >= threshold)
                    .map(e -> e.getKey() + " (" + e.getValue() + "次)")
                    .collect(java.util.stream.Collectors.toList());
            if (excessiveUrls.isEmpty()) return null;
            return "以下URL被重复访问过多: " + String.join(", ", excessiveUrls);
        }

        public String getVisitedUrlsContext() {
            if (visitedUrls.isEmpty()) return "";
            StringBuilder sb = new StringBuilder("\n[已访问的URL历史]\n");
            for (String url : visitedUrls) {
                String result = urlAccessResults.get(url);
                sb.append("- ").append(url);
                if (result != null && !result.isEmpty()) {
                    sb.append(" → ").append(result.length() > 100 ? result.substring(0, 100) + "..." : result);
                }
                sb.append("\n");
            }
            return sb.toString();
        }

        // URL结果缓存 - 避免重复访问
        private final Map<String, ToolExecutionResult> urlResultCache = new ConcurrentHashMap<>();
        private static final long URL_CACHE_TTL_MS = 5 * 60 * 1000; // 5分钟缓存

        public void cacheUrlResult(String url, ToolExecutionResult result) {
            urlResultCache.put(url, result);
        }

        public ToolExecutionResult getCachedUrlResult(String url) {
            String normalizedUrl = normalizeUrl(url);
            ToolExecutionResult cached = urlResultCache.get(normalizedUrl);
            if (cached != null) {
                Long timestamp = urlAccessTimestamps.get(normalizedUrl);
                // 如果有时间戳且未过期，返回缓存
                if (timestamp != null && (System.currentTimeMillis() - timestamp) < URL_CACHE_TTL_MS) {
                    return cached;
                }
                // 如果没有时间戳（首次缓存），也返回（给5秒宽限期）
                if (timestamp == null) {
                    return cached;
                }
                // 缓存过期，清除
                urlResultCache.remove(normalizedUrl);
            }
            return null;
        }

        // 智能工具选择建议
        public String getToolSelectionAdvice(String url) {
            if (url == null || url.isEmpty()) return null;

            StringBuilder advice = new StringBuilder();
            String lowerUrl = url.toLowerCase();

            // 根据URL类型推荐工具
            if (lowerUrl.contains("zhihu.com") || lowerUrl.contains("weibo.com") ||
                lowerUrl.contains("twitter.com") || lowerUrl.contains("x.com")) {
                advice.append("【工具建议】该网站可能需要JavaScript渲染，建议使用 puppeteer_navigate");
            } else if (lowerUrl.contains("jobs.") || lowerUrl.contains("career.") ||
                       lowerUrl.contains("recruit") || lowerUrl.contains("招聘")) {
                advice.append("【工具建议】招聘网站通常需要滚动加载，建议使用 puppeteer_navigate");
            } else if (lowerUrl.contains("github.com") || lowerUrl.contains("stackoverflow.com")) {
                advice.append("【工具建议】该网站支持静态抓取，建议使用 fetch（更快）");
            } else if (lowerUrl.contains("bilibili.com") || lowerUrl.contains("youtube.com")) {
                advice.append("【工具建议】视频网站需要JavaScript，建议使用 puppeteer_navigate");
            }

            // 检查是否已访问过
            if (hasVisitedUrl(url)) {
                advice.append("\n【重要】该URL已被访问过，请使用已有结果或访问其他URL");
            }

            return advice.length() > 0 ? advice.toString() : null;
        }

        public void setVisibleTools(List<McpToolDefinition> tools) {
            this.visibleTools.clear();
            if (tools != null) {
                this.visibleTools.addAll(tools);
            }
        }

        public void setDetectedSkills(List<Skill> skills, Map<String, Double> scores) {
            this.detectedSkills.clear();
            this.skillConfidenceScores.clear();
            if (skills != null) {
                this.detectedSkills.addAll(skills);
            }
            if (scores != null) {
                this.skillConfidenceScores.putAll(scores);
            }
        }

        // ==================== 终止检查方法 ====================

        /**
         * 检查是否应该终止执行
         */
        public TerminationCheck checkTermination() {
            TerminationCheck check = new TerminationCheck();

            if (forceTerminated) {
                check.setShouldTerminate(true);
                check.setReason(TerminationCheck.TerminationReason.USER_CANCELLED);
                check.setSummary("用户取消了执行");
                return check;
            }

            if (consecutiveErrors >= maxConsecutiveErrors) {
                check.setShouldTerminate(true);
                check.setReason(TerminationCheck.TerminationReason.ERROR_LIMIT);
                check.setSummary("连续错误次数达到上限");
                return check;
            }

            if (currentIteration >= maxIterations) {
                check.setShouldTerminate(true);
                check.setReason(TerminationCheck.TerminationReason.MAX_ITERATIONS);
                check.setSummary("达到最大迭代次数");
                return check;
            }

            if (isExecutionStalled()) {
                check.setShouldTerminate(true);
                check.setReason(TerminationCheck.TerminationReason.STALLED);
                check.setSummary("执行停滞，无法继续推进");
                return check;
            }

            check.setShouldTerminate(false);
            return check;
        }

        /**
         * 检查执行是否停滞（连续无进展）
<<<<<<< HEAD
         * 【增强版】细粒度停滞检测，减少误判
         */
        private boolean isExecutionStalled() {
            // 1. 基础检查：连续无进展次数（提高阈值到5，避免正常多步骤任务被误判）
            if (consecutiveNoProgress >= 5) {
                log.debug("停滞检测: 连续无进展次数 {}", consecutiveNoProgress);
                return true;
            }

            // 2. 证据增长检查：如果迭代多次但证据没有增长
            // 提高迭代阈值到10，给复杂任务更多空间
            int currentEvidenceSize = evidenceStore.size();
            if (currentIteration > 10 && currentEvidenceSize == lastEvidenceSize && currentEvidenceSize > 0) {
                log.debug("停滞检测: 迭代 {} 次但证据未增长 ({} 个)", currentIteration, currentEvidenceSize);
                return true;
            }
            lastEvidenceSize = currentEvidenceSize;

            // 3. 工具调用重复模式检查（提高模式长度到3，减少两步合理流程的误判）
            if (hasRepeatingToolPattern(3)) {
                log.debug("停滞检测: 检测到重复工具调用模式");
                return true;
            }

            // 4. URL 访问循环检查（提高阈值到4次）
            if (isStuckInUrlLoop()) {
                log.debug("停滞检测: 陷入 URL 访问循环");
                return true;
            }

            // 5. 访问多个 URL 但获取信息较少（提高阈值）
            if (visitedUrls.size() > 8 && evidenceStore.size() < 2) {
                log.debug("停滞检测: 访问了 {} 个 URL 但仅有 {} 个证据", visitedUrls.size(), evidenceStore.size());
                return true;
            }

            return false;
        }

        /**
         * 检测重复的工具调用模式
         * @param patternLength 模式长度
         */
        private boolean hasRepeatingToolPattern(int patternLength) {
            if (recentToolCallSignatures.size() < patternLength * 2) {
                return false;
            }

            int size = recentToolCallSignatures.size();
            List<String> recent = new ArrayList<>(recentToolCallSignatures.subList(size - patternLength, size));
            List<String> previous = new ArrayList<>(recentToolCallSignatures.subList(size - patternLength * 2, size - patternLength));

            return recent.equals(previous);
=======
         */
        private boolean isExecutionStalled() {
            return consecutiveNoProgress >= 3;
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
        }

        /**
         * 检查是否陷入URL重复访问循环
         */
        public boolean isStuckInUrlLoop() {
<<<<<<< HEAD
            // 如果有URL被访问超过3次，认为陷入循环
            // 阈值从2提高到3，因为某些场景（如先获取页面再提取数据）确实需要多次访问
            return urlVisitCounts.values().stream().anyMatch(count -> count > 3);
=======
            // 如果有URL被访问超过2次，认为陷入循环
            return urlVisitCounts.values().stream().anyMatch(count -> count > 2);
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
        }

        /**
         * 获取停滞诊断信息
         */
        public String getStallDiagnosis() {
            StringBuilder diagnosis = new StringBuilder();

            if (isExecutionStalled()) {
                diagnosis.append("连续无进展次数: ").append(consecutiveNoProgress).append("\n");
            }

            if (isStuckInUrlLoop()) {
                diagnosis.append("URL重复访问检测:\n");
                urlVisitCounts.entrySet().stream()
                    .filter(e -> e.getValue() > 1)
                    .forEach(e -> diagnosis.append("  - ").append(e.getKey()).append(": ").append(e.getValue()).append("次\n"));
            }

            if (visitedUrls.size() > 5 && evidenceStore.size() < 3) {
                diagnosis.append("访问了多个URL但获取的信息较少，可能需要调整策略\n");
            }

            return diagnosis.length() > 0 ? diagnosis.toString() : null;
        }

        /**
         * 强制终止执行
         */
        public void forceTerminate() {
            this.forceTerminated = true;
            this.executionEnded = true;
        }

        /**
         * 检查是否可以继续执行
         */
        public boolean canContinue() {
            return !executionEnded && !forceTerminated &&
                   currentIteration < maxIterations &&
                   consecutiveErrors < maxConsecutiveErrors;
        }

        // ==================== 元认知分析方法 ====================

        /**
         * 执行元认知分析
         */
        public MetaCognition performMetaCognition() {
            MetaCognition meta = new MetaCognition();
            meta.setMissingInfo(new ArrayList<>());
            meta.setAvailableTools(new ArrayList<>());

            meta.setHasEnoughInfo(assessInformationSufficiency());
            meta.setWithinCapability(assessCapability());
            meta.setConfidenceLevel(calculateOverallConfidence());

            if (availableTools != null) {
                for (McpToolDefinition tool : availableTools) {
                    meta.getAvailableTools().add(tool.getName());
                }
            }

            if (!meta.isHasEnoughInfo()) {
                identifyMissingInformation(meta);
            }

            meta.setSuggestedApproach(generateSuggestedApproach(meta));

            if (!meta.isHasEnoughInfo() || !meta.isWithinCapability()) {
                meta.setNeedsUserClarification(true);
                meta.setClarificationQuestion(generateClarificationQuestion(meta));
            }

            return meta;
        }

        private boolean assessInformationSufficiency() {
            // 检查 evidenceStore 中是否有搜索结果
            boolean hasSearchResults = evidenceStore.containsKey("web_search_results");
            boolean hasVisitedUrls = !visitedUrls.isEmpty();

            if (workingMemory.isEmpty() && reasoningChain.getSteps().isEmpty() && !hasSearchResults && !hasVisitedUrls) {
                return false;
            }
            String request = userMessage.toLowerCase();
            if (request.contains("什么") || request.contains("哪个") || request.contains("如何")) {
                return !workingMemory.isEmpty() || reasoningChain.getSteps().size() > 0 || hasSearchResults;
            }
            // 如果已有搜索结果或已访问URL，认为信息可能足够
            if (hasSearchResults || hasVisitedUrls) {
                return true;
            }
            return true;
        }

        private boolean assessCapability() {
            return availableTools != null && !availableTools.isEmpty();
        }

        private double calculateOverallConfidence() {
            double confidence = 0.5;
            if (availableTools != null && !availableTools.isEmpty()) {
                confidence += 0.2;
            }
            if (!workingMemory.isEmpty()) {
                confidence += 0.1;
            }
            if (!reasoningChain.getSteps().isEmpty()) {
                confidence += 0.2 * reasoningChain.getSuccessRate();
            }
            // 考虑已有证据
            if (evidenceStore.containsKey("web_search_results")) {
                confidence += 0.15;
            }
            if (!visitedUrls.isEmpty()) {
                confidence += 0.1 * Math.min(1.0, visitedUrls.size() / 3.0); // 最多增加0.1
            }
            return Math.min(1.0, confidence);
        }

        private void identifyMissingInformation(MetaCognition meta) {
            String request = userMessage.toLowerCase();
            if ((request.contains("修改") || request.contains("删除")) &&
                !workingMemory.containsKey("target_path")) {
                meta.getMissingInfo().add("目标文件路径");
            }
            if ((request.contains("搜索") || request.contains("查找")) &&
                !workingMemory.containsKey("search_query")) {
                meta.getMissingInfo().add("搜索关键词");
            }
            if (meta.getMissingInfo().isEmpty()) {
                meta.getMissingInfo().add("需要更多上下文信息");
            }
        }

        private String generateSuggestedApproach(MetaCognition meta) {
            StringBuilder approach = new StringBuilder();
            if (!meta.isHasEnoughInfo()) {
                approach.append("1. 收集信息: ").append(String.join(", ", meta.getMissingInfo())).append("\n");
            }
            approach.append("2. 使用工具执行任务\n3. 验证结果");
            return approach.toString();
        }

        private String generateClarificationQuestion(MetaCognition meta) {
            if (!meta.getMissingInfo().isEmpty()) {
                return "我需要了解: " + String.join("、", meta.getMissingInfo());
            }
            return "我需要更多信息来帮助您完成这个任务。";
        }

        // ==================== 内部类定义 ====================

        @Data
        public static class TerminationCheck {
            private boolean shouldTerminate;
            private TerminationReason reason;
            private double completionScore;
            private String summary;
            private List<String> remainingTasks = new ArrayList<>();

            public enum TerminationReason {
                GOAL_ACHIEVED, MAX_ITERATIONS, STALLED, ERROR_LIMIT, USER_CANCELLED, NEED_USER_INPUT
            }
        }

        @Data
        public static class MetaCognition {
            private boolean hasEnoughInfo;
            private boolean withinCapability;
            private double confidenceLevel;
            private List<String> missingInfo;
            private List<String> availableTools;
            private String suggestedApproach;
            private boolean needsUserClarification;
            private String clarificationQuestion;
        }

        // 【改进】任务分析结果 getter/setter
        public com.superfriend.superfriend.agent.planner.TaskAnalysis getTaskAnalysis() {
            return taskAnalysis;
        }

        public void setTaskAnalysis(com.superfriend.superfriend.agent.planner.TaskAnalysis taskAnalysis) {
            this.taskAnalysis = taskAnalysis;
        }
    }

    @Data
    public static class ToolExecutionResult {
        private final String toolName;
        private final String serverName;
        private final boolean success;
        private final String result;
        private final String error;
        private final long executionTimeMs;
    }

    @Data
    public static class ReflectionResult {
        private boolean needsCorrection;
        private String correctionHint;
        private double confidenceScore;
        private List<String> missingInfo;
    }

    @Data
    public static class ProgressInfo {
        private final double progress;
        private final String phase;
        private final String description;
        private final int currentStep;
        private final int totalSteps;
    }

    public void executeWithOptimizations(
            String message,
            String sessionId,
            String model,
            List<Map<String, String>> history,
            Long userId,
            ExecutionConfig config,
            Consumer<AIChatResponse> onResponse) {

        ExecutionContext ctx = new ExecutionContext(sessionId, message, userId);
        ctx.setModel(model);
        ctx.setPromptMode(config.getPromptMode());
        ctx.setEnableKnowledgeExtraction(config.isEnableKnowledgeExtraction());

        ExecutionTraceService.ExecutionTrace trace = traceService.startTrace(sessionId, null, message, model, userId);
        ctx.setTraceId(trace.getTraceId());

        String mode;
        if (config.isForcePlanExecuteMode()) {
            mode = "plan_execute";
        } else if (config.isForceReactMode()) {
            mode = "react";
        } else {
            mode = "auto";
        }
        String metricsExecutionId = metricsService.startExecution(sessionId, model, userId, mode);
        ctx.setMetricsExecutionId(metricsExecutionId);

        String observabilityTraceId = observabilityService.startTrace(
            sessionId, 
            userId != null ? userId.toString() : null, 
            model, 
            message
        );
        ctx.setObservabilityTraceId(observabilityTraceId);

        log.info("开始优化执行，Session ID: {}, Model: {}, Trace ID: {}", sessionId, model, trace.getTraceId());

        sessionManager.startExecution(sessionId);

        boolean hasError = false;
        String errorMessage = null;

        try {
            if (!initializeExecution(ctx, history, config, onResponse)) {
                traceService.endTrace(trace.getTraceId(), ExecutionTraceService.TraceStatus.FAILED, "初始化失败");
                metricsService.endExecution(metricsExecutionId, false, "初始化失败");
                return;
            }

            if (config.isEnableWebSearchPreload()) {
                performWebSearchPreload(ctx, config, onResponse);
            }

            if (ctx.isExecutionEnded()) {
                log.info("预搜索已直接回答，结束执行");
                return;
            }

            if (config.isForcePlanExecuteMode()) {
                log.info("[sessionId={}] forcePlanExecuteMode=true，强制使用 Plan-Execute 模式", ctx.getSessionId());
                if (!executeWithTaskPlanner(ctx, config, onResponse)) {
                    // Plan-Execute 模式失败，回退到 ReAct 模式
                    log.warn("[sessionId={}] Plan-Execute 模式执行失败，回退到 ReAct 模式", ctx.getSessionId());
                    updateProgress(ctx, "回退", "Plan-Execute 模式失败，正在切换到 ReAct 模式...", ctx.getProgress(), onResponse);

                    // 清理状态以便重新开始 ReAct 循环
                    resetContextForFallback(ctx);

                    // 继续执行 ReAct 循环
                    executeReActLoop(ctx, config, onResponse);
                }
                return;
            }

            // 【改进】智能选择执行模式
            if (!config.isForceReactMode() && shouldUseTaskPlanner(ctx)) {
                log.info("[sessionId={}] 智能判断为复杂任务，使用 Plan-Execute 模式", ctx.getSessionId());
<<<<<<< HEAD
                if (executeWithTaskPlanner(ctx, config, onResponse)) {
                    return;
                }
                log.info("[sessionId={}] Plan-Execute 模式失败，回退到 ReAct 模式", ctx.getSessionId());
=======
                ctx.setModeSwitchCount(ctx.getModeSwitchCount() + 1);
                if (executeWithTaskPlanner(ctx, config, onResponse)) {
                    return;
                }
                // 【改进】检查切换次数限制
                if (ctx.getModeSwitchCount() >= ExecutionContext.MAX_MODE_SWITCHES) {
                    log.warn("[sessionId={}] 模式切换次数达到上限 {}，强制使用 ReAct 模式",
                        ctx.getSessionId(), ExecutionContext.MAX_MODE_SWITCHES);
                } else {
                    log.info("[sessionId={}] Plan-Execute 模式失败，回退到 ReAct 模式 (切换次数: {}/{})",
                        ctx.getSessionId(), ctx.getModeSwitchCount(), ExecutionContext.MAX_MODE_SWITCHES);
                }
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
            }

            log.info("[sessionId={}] 使用 ReAct 循环模式，maxIterations={}", ctx.getSessionId(), config.getMaxIterations());
            executeReActLoop(ctx, config, onResponse);

        } catch (Exception e) {
            log.error("执行失败：{}", e.getMessage(), e);
            errorMessage = e.getMessage();
            sendErrorResponse(ctx, e, onResponse);
            hasError = true;
            metricsService.recordProgressAtFailure(metricsExecutionId, ctx.getProgress());
        } finally {
            traceService.endTrace(ctx.getTraceId(),
                hasError ? ExecutionTraceService.TraceStatus.FAILED : ExecutionTraceService.TraceStatus.COMPLETED,
                hasError ? "执行异常" : null);
            metricsService.endExecution(metricsExecutionId, !hasError, errorMessage);
            finalizeExecution(ctx, onResponse);
        }
    }

    /**
     * 多模态版本的执行方法
     * 支持文本+图片等多模态消息
     *
     * @param request AI 对话请求（支持多模态）
     * @param config 执行配置
     * @param onResponse 响应回调
     */
    public void executeWithOptimizationsMultimodal(
            AIChatRequest request,
            ExecutionConfig config,
            Consumer<AIChatResponse> onResponse) {
        executeWithOptimizationsMultimodal(request, null, config, onResponse);
    }

    /**
     * 多模态版本的执行方法（带意图）
     * 支持文本+图片等多模态消息，传递用户意图用于优化执行策略
     *
     * @param request AI 对话请求（支持多模态）
     * @param intent 用户意图（可选）
     * @param config 执行配置
     * @param onResponse 响应回调
     */
    public void executeWithOptimizationsMultimodal(
            AIChatRequest request,
            UserIntent intent,
            ExecutionConfig config,
            Consumer<AIChatResponse> onResponse) {

        String sessionId = request.getSessionId();
        String model = request.getModel();
        Long userId = request.getUserId();
        String textMessage = request.getEffectiveText();

        // 从 request 获取知识提取开关，覆盖 config 中的值
        if (request.getEnableKnowledgeExtraction() != null) {
            config.setEnableKnowledgeExtraction(request.getEnableKnowledgeExtraction());
        }

        // 构建多模态内容
        Object userMessageContent = messageContentBuilder.buildUserMessageContent(request);

        ExecutionContext ctx = new ExecutionContext(sessionId, textMessage, userMessageContent, userId);
        ctx.setModel(model);
        ctx.setPromptMode(config.getPromptMode());
        ctx.setEnableKnowledgeExtraction(config.isEnableKnowledgeExtraction());
        // 【改进】设置用户意图
        ctx.setUserIntent(intent);

        ExecutionTraceService.ExecutionTrace trace = traceService.startTrace(sessionId, null, textMessage, model, userId);
        ctx.setTraceId(trace.getTraceId());

        String mode;
        if (config.isForcePlanExecuteMode()) {
            mode = "plan_execute";
        } else if (config.isForceReactMode()) {
            mode = "react";
        } else {
            mode = "auto";
        }
        String metricsExecutionId = metricsService.startExecution(sessionId, model, userId, mode);
        ctx.setMetricsExecutionId(metricsExecutionId);

        String observabilityTraceId = observabilityService.startTrace(
            sessionId,
            userId != null ? userId.toString() : null,
            model,
            textMessage
        );
        ctx.setObservabilityTraceId(observabilityTraceId);

        log.info("开始优化执行（多模态），Session ID: {}, Model: {}, Multimodal: {}, Trace ID: {}",
            sessionId, model, ctx.isMultimodal(), trace.getTraceId());

        sessionManager.startExecution(sessionId);

        boolean hasError = false;
        String errorMessage = null;

        try {
            if (!initializeExecutionMultimodal(ctx, request, config, onResponse)) {
                traceService.endTrace(trace.getTraceId(), ExecutionTraceService.TraceStatus.FAILED, "初始化失败");
                metricsService.endExecution(metricsExecutionId, false, "初始化失败");
                return;
            }

            if (config.isEnableWebSearchPreload()) {
                performWebSearchPreload(ctx, config, onResponse);
            }

            if (ctx.isExecutionEnded()) {
                log.info("预搜索已直接回答，结束执行");
                return;
            }

            if (config.isForcePlanExecuteMode()) {
                // 【修复】即使是 Complex 模式，简单消息也直接走 ReAct，避免不必要的计划生成
                if (isSimpleUserMessage(ctx.getUserMessage())) {
                    log.info("[sessionId={}] forcePlanExecuteMode=true 但消息简单，直接使用 ReAct 模式", ctx.getSessionId());
                    executeReActLoop(ctx, config, onResponse);
                } else {
                    log.info("[sessionId={}] forcePlanExecuteMode=true，强制使用 Plan-Execute 模式", ctx.getSessionId());
                    if (!executeWithTaskPlanner(ctx, config, onResponse)) {
                        log.warn("[sessionId={}] Plan-Execute 模式执行失败，回退到 ReAct 模式", ctx.getSessionId());
                        updateProgress(ctx, "回退", "Plan-Execute 模式失败，正在切换到 ReAct 模式...", ctx.getProgress(), onResponse);
                        resetContextForFallback(ctx);
                        executeReActLoop(ctx, config, onResponse);
                    }
                }
                return;
            }

            // 【改进】智能选择执行模式
            if (!config.isForceReactMode() && shouldUseTaskPlanner(ctx)) {
                log.info("[sessionId={}] 智能判断为复杂任务，使用 Plan-Execute 模式", ctx.getSessionId());
<<<<<<< HEAD
                if (executeWithTaskPlanner(ctx, config, onResponse)) {
                    return;
                }
                log.info("[sessionId={}] Plan-Execute 模式失败，回退到 ReAct 模式", ctx.getSessionId());
=======
                ctx.setModeSwitchCount(ctx.getModeSwitchCount() + 1);
                if (executeWithTaskPlanner(ctx, config, onResponse)) {
                    return;
                }
                // 【改进】检查切换次数限制
                if (ctx.getModeSwitchCount() >= ExecutionContext.MAX_MODE_SWITCHES) {
                    log.warn("[sessionId={}] 模式切换次数达到上限 {}，强制使用 ReAct 模式",
                        ctx.getSessionId(), ExecutionContext.MAX_MODE_SWITCHES);
                } else {
                    log.info("[sessionId={}] Plan-Execute 模式失败，回退到 ReAct 模式 (切换次数: {}/{})",
                        ctx.getSessionId(), ctx.getModeSwitchCount(), ExecutionContext.MAX_MODE_SWITCHES);
                }
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
            }

            log.info("[sessionId={}] 使用 ReAct 循环模式，maxIterations={}", ctx.getSessionId(), config.getMaxIterations());
            executeReActLoop(ctx, config, onResponse);

        } catch (Exception e) {
            log.error("执行失败：{}", e.getMessage(), e);
            errorMessage = e.getMessage();
            sendErrorResponse(ctx, e, onResponse);
            hasError = true;
            metricsService.recordProgressAtFailure(metricsExecutionId, ctx.getProgress());
        } finally {
            traceService.endTrace(ctx.getTraceId(),
                hasError ? ExecutionTraceService.TraceStatus.FAILED : ExecutionTraceService.TraceStatus.COMPLETED,
                hasError ? "执行异常" : null);
            metricsService.endExecution(metricsExecutionId, !hasError, errorMessage);
            finalizeExecution(ctx, onResponse);
        }
    }

    /**
     * Lite 模式已统一到 McpHostService.chatLite() 方法
     * 此处保留注释以说明架构决策
     *
     * Lite 模式特点：
     * - 纯对话，不加载 MCP 工具和 Skills
     * - 保留用户画像和知识图谱上下文注入
     * - 支持搜索预加载
     * - 最大迭代次数：1
     *
     * @see McpHostService#chatLite(String, String, String, List, Long, Consumer)
     */

    /**
     * 重置执行上下文以便从 Plan-Execute 模式回退到 ReAct 模式
     * 清理可能导致问题的中间状态，但保留必要的上下文信息
     *
     * @param ctx 执行上下文
     */
    private void resetContextForFallback(ExecutionContext ctx) {
        log.info("[sessionId={}] 重置上下文以回退到 ReAct 模式", ctx.getSessionId());

        // 重置执行状态标志
        ctx.setExecutionEnded(false);
        ctx.setForceTerminated(false);

        // 重置错误计数
        ctx.setConsecutiveErrors(0);
        ctx.setConsecutiveNoProgress(0);

        // 清理工具执行结果（避免重复处理）
        ctx.getToolResults().clear();

        // 清理最近工具调用签名（避免重复检测误判）
        ctx.getRecentToolCallSignatures().clear();

<<<<<<< HEAD
        // 只清理 Plan-Execute 相关的 evidence，保留其他有用的证据
=======
        // 清理证据存储中的 Plan-Execute 相关数据
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
        ctx.getEvidenceStore().remove("plan_execution");
        ctx.getEvidenceStore().remove("current_step");
        ctx.getEvidenceStore().remove("plan_result");

<<<<<<< HEAD
        // 保留工作内存中有价值的数据（已获取的文件内容、分析结果等）
        // 只清理 Plan-Execute 产生的临时状态
        Map<String, Object> workingMemory = ctx.getWorkingMemory();
        List<String> planKeysToRemove = new ArrayList<>();
        for (String key : workingMemory.keySet()) {
            if (key.startsWith("plan_") || key.startsWith("task_") || key.startsWith("step_")) {
                planKeysToRemove.add(key);
            }
        }
        for (String key : planKeysToRemove) {
            workingMemory.remove(key);
        }

        // 保留 URL 访问历史，避免 ReAct 重复访问已知 URL
        // 只清理 URL 访问时间戳（避免过期检测误判）
        ctx.getUrlAccessTimestamps().clear();

        // 处理消息列表：精简但保留关键上下文
=======
        // 清理工作内存中的临时数据
        ctx.getWorkingMemory().clear();

        // 清理 URL 访问历史（避免重复访问检测误判）
        ctx.getVisitedUrls().clear();
        ctx.getUrlAccessResults().clear();
        ctx.getUrlAccessTimestamps().clear();
        ctx.getUrlVisitCounts().clear();

        // 处理消息列表：移除 Plan-Execute 模式的中间消息
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
        List<Map<String, Object>> messages = ctx.getMessages();
        List<Map<String, Object>> cleanedMessages = new ArrayList<>();

        // 保留系统消息（第一条）
        if (!messages.isEmpty()) {
            Map<String, Object> firstMsg = messages.get(0);
            String role = (String) firstMsg.get("role");
            if ("system".equals(role)) {
                cleanedMessages.add(firstMsg);
            }
        }

        // 从后向前扫描，保留最近的用户消息和有效的对话历史
        // 跳过 Plan-Execute 模式产生的中间消息（通常是包含计划步骤的 assistant 消息）
        boolean foundValidUserMessage = false;
        for (int i = messages.size() - 1; i >= 1; i--) {
            Map<String, Object> msg = messages.get(i);
            String role = (String) msg.get("role");

            // 保留用户消息
            if ("user".equals(role)) {
                if (!foundValidUserMessage) {
                    cleanedMessages.add(msg);
                    foundValidUserMessage = true;
                }
            }
            // 保留带有 tool_calls 的 assistant 消息（表示有实际工具调用）
            else if ("assistant".equals(role) && msg.containsKey("tool_calls")) {
                cleanedMessages.add(msg);
            }
            // 保留 tool 响应消息
            else if ("tool".equals(role)) {
                cleanedMessages.add(msg);
            }
<<<<<<< HEAD
            // 保留包含实质内容的 assistant 消息（非 Plan-Execute 的计划步骤）
            else if ("assistant".equals(role) && !msg.containsKey("tool_calls")) {
                String content = msg.get("content") != null ? msg.get("content").toString() : "";
                // 跳过纯计划步骤消息
                if (!content.contains("## 任务计划") && !content.contains("## 执行步骤")
                    && !content.contains("plan_step") && content.length() > 0) {
                    cleanedMessages.add(msg);
                }
            }
=======
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
        }

        // 反转以保持正确顺序
        java.util.Collections.reverse(cleanedMessages);

        // 如果清理后只剩下系统消息，重新添加原始用户消息
        if (cleanedMessages.size() == 1 && ctx.getUserMessage() != null) {
            Map<String, Object> userMsg = new HashMap<>();
            userMsg.put("role", "user");
            userMsg.put("content", ctx.getUserMessage());
            cleanedMessages.add(userMsg);
            log.debug("[sessionId={}] 重新添加原始用户消息", ctx.getSessionId());
        }

        // 更新消息列表
        messages.clear();
        messages.addAll(cleanedMessages);

        // 重置推理链的部分状态（保留已有的思考步骤）
        // 不完全清空，因为之前的推理可能仍然有价值

        // 重置进度
        ctx.setProgress(0.3);
        ctx.setCurrentPhase("ReAct 回退");

        // 重置迭代计数
        ctx.setCurrentIteration(0);

        log.info("[sessionId={}] 上下文重置完成，messages 数量: {}，已清理 Plan-Execute 中间状态",
            ctx.getSessionId(), messages.size());
    }

    private boolean initializeExecution(
            ExecutionContext ctx,
            List<Map<String, String>> history,
            ExecutionConfig config,
            Consumer<AIChatResponse> onResponse) {

        updateProgress(ctx, "初始化", "正在初始化执行环境...", 0.05, onResponse);

        // DynamicConfigManager: 分析任务复杂度并动态调整配置
        // 【改进】将分析结果保存到上下文，避免重复分析
        try {
            com.superfriend.superfriend.agent.planner.TaskAnalysis taskAnalysis = taskAnalyzer.analyze(ctx.getUserMessage());
            ctx.setTaskAnalysis(taskAnalysis);  // 缓存分析结果
            com.superfriend.superfriend.agent.planner.TaskComplexity complexity = taskAnalysis.getComplexity();
            log.info("[DynamicConfig] 任务分析: complexity={}, estimatedSteps={}, intent={}",
                complexity, taskAnalysis.getEstimatedSteps(), taskAnalysis.getIntent());

            com.superfriend.superfriend.agent.config.DynamicConfigManager.SessionConfig sessionConfig =
                dynamicConfigManager.adjustForTaskComplexity(ctx.getSessionId(), complexity);
            ctx.setSessionConfig(sessionConfig);

            // 用动态配置优化静态配置（取较大值，确保不会降低执行能力）
            config.setMaxIterations(Math.max(config.getMaxIterations(), sessionConfig.getMaxIterations()));
            config.setMaxConsecutiveErrors(Math.max(config.getMaxConsecutiveErrors(), sessionConfig.getMaxConsecutiveErrors()));
            config.setToolTimeoutMs(Math.max(config.getToolTimeoutMs(), sessionConfig.getToolTimeoutMs()));

            log.info("[DynamicConfig] 动态配置已应用: maxIter={}, maxErrors={}, timeoutMs={}",
                config.getMaxIterations(), config.getMaxConsecutiveErrors(), config.getToolTimeoutMs());
        } catch (Exception e) {
            log.warn("[DynamicConfig] 任务分析失败，使用默认配置: {}", e.getMessage());
        }

        AIModelConfig resolvedConfig = modelConfigService.resolveModelConfig(ctx.getModel(), ctx.getUserId());
        if (resolvedConfig == null) {
            sendErrorResponse(ctx, "未找到可用的模型配置", onResponse);
            return false;
        }

        ctx.setActualModel(resolvedConfig.getModelId());
        ctx.setApiUrl(sanitizeApiUrl(resolvedConfig.getApiUrl()));
        ctx.setApiKey(resolvedConfig.getApiKey());

        updateProgress(ctx, "初始化", "正在启动 MCP 服务器...", 0.06, onResponse);
        String startResult = mcpHostService.startSelectedServers();
        log.info("MCP 服务器启动结果：{}", startResult);
        
        if (startResult.contains("不存在") || startResult.contains("失败") || startResult.contains("没有")) {
            sendErrorResponse(ctx, "MCP 服务器启动失败：" + startResult, onResponse);
            return false;
        }

        updateProgress(ctx, "初始化", "正在获取可用工具列表...", 0.08, onResponse);
        
        List<McpToolDefinition> tools;
        if (config.isEnableSkills() && ctx.getUserId() != null) {
            tools = mcpHostService.listSelectedToolsWithSkills(ctx.getUserId());
            log.info("加载 MCP 工具 + Skills 工具，共 {} 个", tools.size());
        } else {
            tools = mcpHostService.listSelectedTools();
            log.info("加载 MCP 工具，共 {} 个", tools.size());
        }
        
        if (tools.isEmpty()) {
            sendErrorResponse(ctx, "当前没有可用的 MCP 工具。请在设置中选择并启用至少一个 MCP 服务器。", onResponse);
            return false;
        }

        log.info("加载 {} 个工具，直接传给模型", tools.size());
        ctx.getAvailableTools().addAll(tools);

        // Set initial visible tools
        ctx.setVisibleTools(tools);
        ctx.setToolVisibilityLevel(3); // Full visibility

        buildMessages(ctx, history);

        updateProgress(ctx, "初始化", "执行环境初始化完成", 0.1, onResponse);
        return true;
    }

    private void buildMessages(ExecutionContext ctx, List<Map<String, String>> history) {
        String systemPrompt = systemContextBuilder.buildAgentPrompt(
            ctx.getUserId(), ctx.getSessionId(), ctx.getUserMessage(), ctx.getPromptMode());

        Map<String, Object> systemMessage = new HashMap<>();
        systemMessage.put("role", "system");
        systemMessage.put("content", systemPrompt);
        ctx.getMessages().add(systemMessage);

        if (history != null && !history.isEmpty()) {
            List<Map<String, Object>> processedHistory = historyMessageProcessor.processHistory(history);
            long historyTokens = compressionService.estimateMessageTokens(processedHistory);
            long compressionThreshold = 10000;
            
            log.info("前端历史消息: count={}, tokens={}, threshold={}", 
                processedHistory.size(), historyTokens, compressionThreshold);
            
            if (historyTokens > compressionThreshold) {
                List<Map<String, Object>> compressedHistory = buildHistoryWithCompression(
                    ctx.getSessionId(), processedHistory, ctx.getUserId(), ctx.getModel());
                ctx.getMessages().addAll(compressedHistory);
            } else {
                ctx.getMessages().addAll(processedHistory);
            }
        } else if (ctx.getSessionId() != null && !ctx.getSessionId().isEmpty()) {
            List<Map<String, Object>> compressedHistory = contextMangerService.getMessagesWithCompression(ctx.getSessionId());
            for (Map<String, Object> msg : compressedHistory) {
                String role = (String) msg.get("role");
                if (!"system".equals(role)) {
                    ctx.getMessages().add(msg);
                }
            }
            log.info("从数据库加载压缩历史消息: sessionId={}, count={}", ctx.getSessionId(), compressedHistory.size());
        }

        Map<String, Object> userMessage = new HashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", ctx.getUserMessage());
        ctx.getMessages().add(userMessage);
    }

    private List<Map<String, Object>> buildHistoryWithCompression(String sessionId, 
            List<Map<String, Object>> frontendHistory, Long userId, String modelName) {
        
        long compressionThreshold = 10000;
        long level2Threshold = 12000;
        
        ChatCompression existingCompression = null;
        if (sessionId != null && !sessionId.isEmpty()) {
            existingCompression = contextMangerService.getCompressionRecord(sessionId);
        }
        
        if (existingCompression != null) {
            log.info("使用已有压缩记录 + 前端近3轮对话: sessionId={}", sessionId);
            
            List<Map<String, Object>> result = new ArrayList<>();
            
            Map<String, Object> summaryMsg = new HashMap<>();
            summaryMsg.put("role", "system");
            summaryMsg.put("content", "【上下文摘要 - 已压缩 " + existingCompression.getOriginalMessageCount() + " 条旧消息】\n\n" + existingCompression.getSummary());
            result.add(summaryMsg);
            
            int recentRounds = 3;
            int recentMessages = recentRounds * 2;
            int startIndex = Math.max(0, frontendHistory.size() - recentMessages);
            for (int i = startIndex; i < frontendHistory.size(); i++) {
                result.add(frontendHistory.get(i));
            }
            
            long tokens = compressionService.estimateMessageTokens(result);
            log.info("压缩记录+近3轮对话: messages={}, tokens={}", result.size(), tokens);
            
            if (tokens > level2Threshold) {
                log.info("压缩记录+近3轮对话仍超阈值，再次压缩");
                ContextCompressionService.CompressionResult compressionResult = 
                    compressionService.compressIfNeeded(result, sessionId, userId, modelName);
                if (compressionResult.compressed) {
                    log.info("二次压缩完成: level={}, {} -> {} messages, {} -> {} tokens",
                        compressionResult.level, compressionResult.originalCount, compressionResult.compressedCount,
                        compressionResult.originalTokens, compressionResult.compressedTokens);
                }
            }
            
            return result;
        }
        
        log.info("无压缩记录，直接压缩前端历史消息: count={}", frontendHistory.size());
        ContextCompressionService.CompressionResult compressionResult = 
            compressionService.compressIfNeeded(frontendHistory, sessionId, userId, modelName);
        
        if (compressionResult.compressed) {
            log.info("压缩完成: level={}, {} -> {} messages, {} -> {} tokens",
                compressionResult.level, compressionResult.originalCount, compressionResult.compressedCount,
                compressionResult.originalTokens, compressionResult.compressedTokens);
        }
        
        return frontendHistory;
    }

    /**
     * 多模态版本的初始化执行方法
     */
    private boolean initializeExecutionMultimodal(
            ExecutionContext ctx,
            AIChatRequest request,
            ExecutionConfig config,
            Consumer<AIChatResponse> onResponse) {

        updateProgress(ctx, "初始化", "正在初始化执行环境...", 0.05, onResponse);

        // DynamicConfigManager: 分析任务复杂度并动态调整配置
        // 【改进】将分析结果保存到上下文，避免重复分析
        try {
            com.superfriend.superfriend.agent.planner.TaskAnalysis taskAnalysis = taskAnalyzer.analyze(ctx.getUserMessage());
            ctx.setTaskAnalysis(taskAnalysis);  // 缓存分析结果
            com.superfriend.superfriend.agent.planner.TaskComplexity complexity = taskAnalysis.getComplexity();
            log.info("[DynamicConfig] 任务分析: complexity={}, estimatedSteps={}, intent={}",
                complexity, taskAnalysis.getEstimatedSteps(), taskAnalysis.getIntent());

            com.superfriend.superfriend.agent.config.DynamicConfigManager.SessionConfig sessionConfig =
                dynamicConfigManager.adjustForTaskComplexity(ctx.getSessionId(), complexity);
            ctx.setSessionConfig(sessionConfig);

            config.setMaxIterations(Math.max(config.getMaxIterations(), sessionConfig.getMaxIterations()));
            config.setMaxConsecutiveErrors(Math.max(config.getMaxConsecutiveErrors(), sessionConfig.getMaxConsecutiveErrors()));
            config.setToolTimeoutMs(Math.max(config.getToolTimeoutMs(), sessionConfig.getToolTimeoutMs()));

            log.info("[DynamicConfig] 动态配置已应用: maxIter={}, maxErrors={}, timeoutMs={}",
                config.getMaxIterations(), config.getMaxConsecutiveErrors(), config.getToolTimeoutMs());
        } catch (Exception e) {
            log.warn("[DynamicConfig] 任务分析失败，使用默认配置: {}", e.getMessage());
        }

        AIModelConfig resolvedConfig = modelConfigService.resolveModelConfig(ctx.getModel(), ctx.getUserId());
        if (resolvedConfig == null) {
            sendErrorResponse(ctx, "未找到可用的模型配置", onResponse);
            return false;
        }

        ctx.setActualModel(resolvedConfig.getModelId());
        ctx.setApiUrl(sanitizeApiUrl(resolvedConfig.getApiUrl()));
        ctx.setApiKey(resolvedConfig.getApiKey());

        updateProgress(ctx, "初始化", "正在启动 MCP 服务器...", 0.06, onResponse);
        String startResult = mcpHostService.startSelectedServers();
        log.info("MCP 服务器启动结果：{}", startResult);

        if (startResult.contains("不存在") || startResult.contains("失败") || startResult.contains("没有")) {
            sendErrorResponse(ctx, "MCP 服务器启动失败：" + startResult, onResponse);
            return false;
        }

        updateProgress(ctx, "初始化", "正在获取可用工具列表...", 0.08, onResponse);

        List<McpToolDefinition> tools;
        if (config.isEnableSkills() && ctx.getUserId() != null) {
            tools = mcpHostService.listSelectedToolsWithSkills(ctx.getUserId());
            log.info("加载 MCP 工具 + Skills 工具，共 {} 个", tools.size());
        } else {
            tools = mcpHostService.listSelectedTools();
            log.info("加载 MCP 工具，共 {} 个", tools.size());
        }

        if (tools.isEmpty()) {
            sendErrorResponse(ctx, "当前没有可用的 MCP 工具。请在设置中选择并启用至少一个 MCP 服务器。", onResponse);
            return false;
        }

        log.info("加载 {} 个工具，直接传给模型", tools.size());
        ctx.getAvailableTools().addAll(tools);

        ctx.setVisibleTools(tools);
        ctx.setToolVisibilityLevel(3);

        // 使用多模态版本的消息构建
        buildMessagesMultimodal(ctx, request);

        updateProgress(ctx, "初始化", "执行环境初始化完成", 0.1, onResponse);
        return true;
    }

    /**
     * 多模态版本的消息构建
     */
    private void buildMessagesMultimodal(ExecutionContext ctx, AIChatRequest request) {
        // 使用 SystemContextBuilder 统一构建系统提示词
        String systemPrompt = systemContextBuilder.buildAgentPrompt(
            ctx.getUserId(), ctx.getSessionId(), ctx.getUserMessage(), ctx.getPromptMode());

        Map<String, Object> systemMessage = new HashMap<>();
        systemMessage.put("role", "system");
        systemMessage.put("content", systemPrompt);
        ctx.getMessages().add(systemMessage);

        // 使用 MessageContentBuilder 处理历史消息（支持多模态）
        ctx.getMessages().addAll(messageContentBuilder.buildHistoryMessages(request));

<<<<<<< HEAD
        // 【改进】使用优化后的提示词（如果有）
        Object userContent = ctx.getEffectiveUserMessageContent();
        UserIntent intent = ctx.getUserIntent();

        // 如果有优化后的提示词，替换用户消息中的文本部分
        if (intent != null && intent.getOptimizedPrompt() != null && !intent.getOptimizedPrompt().isEmpty()) {
            String optimizedPrompt = intent.getOptimizedPrompt();
            log.info("[sessionId={}] 使用优化后的提示词: {}",
                ctx.getSessionId(),
                optimizedPrompt.length() > 100 ? optimizedPrompt.substring(0, 100) + "..." : optimizedPrompt);

            // 如果是多模态消息（List），需要替换其中的文本部分
            if (userContent instanceof List) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> contentList = new ArrayList<>((List<Map<String, Object>>) userContent);
                boolean textReplaced = false;
                for (Map<String, Object> item : contentList) {
                    if ("text".equals(item.get("type"))) {
                        item.put("text", optimizedPrompt);
                        textReplaced = true;
                        break;
                    }
                }
                // 如果没有文本项，添加一个
                if (!textReplaced) {
                    Map<String, Object> textItem = new HashMap<>();
                    textItem.put("type", "text");
                    textItem.put("text", optimizedPrompt);
                    contentList.add(0, textItem);
                }
                userContent = contentList;
            } else if (userContent instanceof String) {
                // 纯文本消息，直接替换
                userContent = optimizedPrompt;
            }
        }

        // 添加用户消息（使用多模态内容）
        Map<String, Object> userMessage = new HashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", userContent);
=======
        // 添加用户消息（使用多模态内容）
        Map<String, Object> userMessage = new HashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", ctx.getEffectiveUserMessageContent());
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
        ctx.getMessages().add(userMessage);

        log.debug("[sessionId={}] 构建消息完成，共 {} 条消息，用户消息类型: {}",
            ctx.getSessionId(), ctx.getMessages().size(), ctx.getEffectiveUserMessageContent().getClass().getSimpleName());
    }

    @SuppressWarnings("unchecked")
    private void cleanIncompleteToolCalls(List<Map<String, Object>> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }

        List<Map<String, Object>> toRemove = new ArrayList<>();
        Set<String> processedToolCallIds = new HashSet<>();

        for (int i = 0; i < messages.size(); i++) {
            Map<String, Object> msg = messages.get(i);
            String role = (String) msg.get("role");

            if ("assistant".equals(role) && msg.containsKey("tool_calls")) {
                Object toolCallsObj = msg.get("tool_calls");
                if (toolCallsObj instanceof List) {
                    List<Map<String, Object>> toolCalls = (List<Map<String, Object>>) toolCallsObj;
                    Set<String> requiredIds = new HashSet<>();
                    for (Map<String, Object> tc : toolCalls) {
                        if (tc.containsKey("id")) {
                            requiredIds.add((String) tc.get("id"));
                        }
                    }

                    if (requiredIds.isEmpty()) {
                        continue;
                    }

                    Set<String> foundIds = new HashSet<>();
                    boolean hasBlockingMessage = false;

                    for (int j = i + 1; j < messages.size(); j++) {
                        Map<String, Object> nextMsg = messages.get(j);
                        String nextRole = (String) nextMsg.get("role");

                        if ("tool".equals(nextRole) && nextMsg.containsKey("tool_call_id")) {
                            String toolCallId = (String) nextMsg.get("tool_call_id");
                            if (requiredIds.contains(toolCallId)) {
                                foundIds.add(toolCallId);
                            }
                        } else if (!"tool".equals(nextRole)) {
                            if (!foundIds.equals(requiredIds)) {
                                hasBlockingMessage = true;
                            }
                            break;
                        }
                    }

                    if (!foundIds.equals(requiredIds) || hasBlockingMessage) {
                        Set<String> missingIds = new HashSet<>(requiredIds);
                        missingIds.removeAll(foundIds);
                        if (!missingIds.isEmpty()) {
                            log.warn("发现不完整的 tool_calls，缺失的 tool_call_id: {}", missingIds);
                        }
                        if (hasBlockingMessage) {
                            log.warn("发现 tool_calls 消息顺序错误，assistant 消息后存在非 tool 消息");
                        }
                        toRemove.add(msg);
                        log.info("移除不完整的 assistant 消息（含 tool_calls）");
                    } else {
                        processedToolCallIds.addAll(requiredIds);
                    }
                }
            }
        }

        List<Map<String, Object>> orphanedToolMessages = new ArrayList<>();
        for (Map<String, Object> msg : messages) {
            String role = (String) msg.get("role");
            if ("tool".equals(role) && msg.containsKey("tool_call_id")) {
                String toolCallId = (String) msg.get("tool_call_id");
                if (!processedToolCallIds.contains(toolCallId) && !isToolCallIdInRemainingMessages(messages, toRemove, toolCallId)) {
                    orphanedToolMessages.add(msg);
                    log.info("移除孤立的 tool 消息，tool_call_id: {}", toolCallId);
                }
            }
        }

        messages.removeAll(toRemove);
        messages.removeAll(orphanedToolMessages);
    }

    @SuppressWarnings("unchecked")
    private boolean isToolCallIdInRemainingMessages(List<Map<String, Object>> messages, 
                                                     List<Map<String, Object>> toRemove, 
                                                     String toolCallId) {
        for (Map<String, Object> msg : messages) {
            if (toRemove.contains(msg)) {
                continue;
            }
            String role = (String) msg.get("role");
            if ("assistant".equals(role) && msg.containsKey("tool_calls")) {
                Object toolCallsObj = msg.get("tool_calls");
                if (toolCallsObj instanceof List) {
                    List<Map<String, Object>> toolCalls = (List<Map<String, Object>>) toolCallsObj;
                    for (Map<String, Object> tc : toolCalls) {
                        if (toolCallId.equals(tc.get("id"))) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private void performWebSearchPreload(
            ExecutionContext ctx,
            ExecutionConfig config,
            Consumer<AIChatResponse> onResponse) {

        String userMessage = ctx.getUserMessage();
        QueryType queryType = classifyQuery(userMessage);
        log.info("查询类型: {} - {}", queryType, userMessage.substring(0, Math.min(50, userMessage.length())));

        // 记录到 ReasoningChain
        ctx.addThought("分析查询类型: " + queryType);

        // 【改进】如果 intent 明确要求搜索，强制执行搜索（即使 OPERATIONAL）
        boolean forceSearch = ctx.getUserIntent() != null && ctx.getUserIntent().isNeedsSearch();
        if (forceSearch) {
            log.info("Intent 明确要求搜索，强制执行预搜索");
            ctx.addThought("Intent 明确要求搜索，强制执行预搜索");
        }

        if (queryType == QueryType.OPERATIONAL && !forceSearch) {
            log.info("操作性查询，跳过预搜索，直接进入 ReAct");
            ctx.addThought("操作性查询，跳过预搜索");
            return;
        }

        updateProgress(ctx, "信息搜索", "正在通过 OrioSearch 搜索相关信息...", 0.12, onResponse);

<<<<<<< HEAD
        // 使用 OrioSearch 进行搜索（带超时控制）
        String searchContext = null;
        try {
            // 使用 CompletableFuture 实现超时控制
            java.util.concurrent.CompletableFuture<String> searchFuture = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                try {
                    return orioSearchService.getSearchContextForLLM(userMessage);
                } catch (Exception e) {
                    log.warn("OrioSearch 搜索异常: {}", e.getMessage());
                    return null;
                }
            });

            searchContext = searchFuture.get(SEARCH_TIMEOUT_MS, TimeUnit.MILLISECONDS);

            // 探索性查询进行扩展搜索
            if (queryType == QueryType.EXPLORATORY && searchContext != null) {
                String expandedQuery = expandQuery(userMessage);
                if (!expandedQuery.equals(userMessage)) {
                    ctx.addThought("扩展搜索查询: " + expandedQuery);
                    String finalSearchContext = searchContext;
                    java.util.concurrent.CompletableFuture<String> expandedFuture = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                        try {
                            return orioSearchService.getSearchContextForLLM(expandedQuery);
                        } catch (Exception e) {
                            return null;
                        }
                    });
                    String expandedContext = expandedFuture.get(SEARCH_TIMEOUT_MS / 2, TimeUnit.MILLISECONDS);
                    if (expandedContext != null && !expandedContext.isEmpty()) {
                        searchContext = finalSearchContext + "\n\n--- 扩展搜索 ---\n" + expandedContext;
                    }
                }
            }
        } catch (java.util.concurrent.TimeoutException e) {
            log.warn("OrioSearch 搜索超时（{}ms），跳过预搜索", SEARCH_TIMEOUT_MS);
            ctx.addReflection("搜索服务超时，将通过工具获取信息", 0.3);
            return;
=======
        // 使用 OrioSearch 进行搜索
        String searchContext = null;
        try {
            searchContext = orioSearchService.getSearchContextForLLM(userMessage);

            // 探索性查询进行扩展搜索
            if (queryType == QueryType.EXPLORATORY) {
                String expandedQuery = expandQuery(userMessage);
                if (!expandedQuery.equals(userMessage)) {
                    ctx.addThought("扩展搜索查询: " + expandedQuery);
                    String expandedContext = orioSearchService.getSearchContextForLLM(expandedQuery);
                    if (expandedContext != null && !expandedContext.isEmpty()) {
                        searchContext = searchContext + "\n\n--- 扩展搜索 ---\n" + expandedContext;
                    }
                }
            }
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
        } catch (Exception e) {
            log.warn("OrioSearch 搜索异常: {}", e.getMessage());
            ctx.addReflection("搜索服务异常: " + e.getMessage(), 0.3);
        }

        if (searchContext == null || searchContext.length() < 50) {
            log.info("搜索结果不足，跳过预搜索");
            ctx.addReflection("搜索结果不足，需要通过工具获取信息", 0.4);
            return;
        }

        log.info("OrioSearch 搜索完成，获取 {} 字符", searchContext.length());

        // 记录搜索结果到 evidenceStore
        ctx.addEvidence("web_search_results", searchContext);
        ctx.addThought("Web 搜索获取到 " + searchContext.length() + " 字符的相关信息");

        if (searchContext.length() >= MIN_RESULT_FOR_DIRECT_ANSWER && queryType != QueryType.EXPLORATORY) {
            log.info("搜索结果充足，询问大模型是否可以直接回答");
            updateProgress(ctx, "判断结果", "分析搜索结果是否足够...", 0.15, onResponse);

            if (canAnswerWithSearchResults(ctx, searchContext, userMessage)) {
                log.info("大模型确认可以直接回答");
                ctx.addDecision("基于搜索结果直接回答", "搜索结果充足且相关");
                updateProgress(ctx, "生成回答", "基于搜索结果生成回答...", 0.18, onResponse);

                if (tryDirectAnswer(ctx, searchContext, onResponse)) {
                    ctx.setExecutionEnded(true);
                    log.info("直接回答成功");
                    return;
                }
            } else {
                log.info("大模型认为需要进一步操作，进入 ReAct 循环");
                ctx.addThought("搜索结果不足以直接回答，需要进一步操作");
            }
        }

        // 将搜索结果作为上下文注入，避免重复用户问题
        StringBuilder contextContent = new StringBuilder();
        contextContent.append("[系统提供的搜索上下文]\n").append(searchContext);

        // 添加从搜索结果中提取的URL列表
        List<String> extractedUrls = extractUrlsFromSearchResults(searchContext);
        if (!extractedUrls.isEmpty()) {
            contextContent.append("\n\n[搜索结果中发现的URL]\n");
            for (int i = 0; i < Math.min(5, extractedUrls.size()); i++) {
                contextContent.append(i + 1).append(". ").append(extractedUrls.get(i)).append("\n");
            }
            if (extractedUrls.size() > 5) {
                contextContent.append("... 共 ").append(extractedUrls.size()).append(" 个URL\n");
            }
        }

        contextContent.append("\n请结合以上信息处理用户的请求。");
        contextContent.append("\n\n【重要提示】如果需要访问URL获取更多信息：");
        contextContent.append("\n1. 优先访问上述发现的URL，不要重复访问");
        contextContent.append("\n2. 每个URL只需访问一次，使用fetch或puppeteer其中一种工具即可");
        contextContent.append("\n3. 如果搜索结果已包含足够信息，请直接回答，无需额外爬取");

        Map<String, Object> contextMsg = new HashMap<>();
        contextMsg.put("role", "user");
        contextMsg.put("content", contextContent.toString());
        ctx.getMessages().add(contextMsg);

        log.info("搜索结果已注入上下文");
        updateProgress(ctx, "信息搜索", "搜索完成", 0.18, onResponse);
    }

    private QueryType classifyQuery(String query) {
        if (query == null || query.isEmpty()) return QueryType.FACTUAL;

        String lower = query.toLowerCase();

        String[] operationalPatterns = {
            "帮我写", "写一个", "写个", "实现", "创建", "生成代码",
            "帮我改", "修改", "重构", "优化代码",
            "执行", "运行", "调用", "操作",
            "发送", "提交", "上传", "下载",
            "删除", "移动", "复制", "重命名"
        };

        for (String pattern : operationalPatterns) {
            if (lower.contains(pattern)) {
                String[] searchHints = {"最新", "最近", "新闻", "官网", "招聘", "校招"};
                for (String hint : searchHints) {
                    if (lower.contains(hint)) {
                        return QueryType.EXPLORATORY;
                    }
                }
                return QueryType.OPERATIONAL;
            }
        }

        String[] exploratoryPatterns = {
            "分析", "比较", "对比", "评价", "评估",
            "怎么样", "好不好", "优缺点", "利弊",
            "全面", "详细", "深入", "完整"
        };

        for (String pattern : exploratoryPatterns) {
            if (lower.contains(pattern)) {
                return QueryType.EXPLORATORY;
            }
        }

        return QueryType.FACTUAL;
    }

    private String expandQuery(String query) {
        String lower = query.toLowerCase();
        StringBuilder expanded = new StringBuilder(query);

        // 动态获取当前年份
        int currentYear = java.time.Year.now().getValue();

        if (lower.contains("招聘") || lower.contains("校招")) {
            expanded.append(" ").append(currentYear);
        }
        if (lower.contains("最新") || lower.contains("最近")) {
            expanded.append(" ").append(currentYear);
        }
        if (lower.contains("技术") || lower.contains("开发")) {
            expanded.append(" 技术岗位");
        }

        return expanded.toString();
    }

    @Data
    private static class SearchResult {
        private String source;
        private String url;
        private String content;
        private long fetchTimeMs;

        public SearchResult(String source, String url, String content) {
            this.source = source;
            this.url = url;
            this.content = content;
            this.fetchTimeMs = System.currentTimeMillis();
        }
    }

    private SearchResult searchWithQuery(String query, String fetchServerName, String braveSearchServerName,
            String tavilySearchServerName, String serperSearchServerName) {
        long start = System.currentTimeMillis();

        if (tavilySearchServerName != null) {
            try {
                Map<String, Object> args = new HashMap<>();
                args.put("query", query);
                args.put("max_results", 5);
                args.put("include_raw_content", true);
                McpToolCallResponse result = mcpHostService.callTool(tavilySearchServerName, "tavily_search", args);
                if (result.isSuccess()) {
                    String text = mcpHostService.compressToolResult(result);
                    if (text != null && text.length() > 50 && !isErrorMessage(text)) {
                        log.info("Tavily 搜索 '{}' 成功，{} 字符，{}ms", 
                                query, text.length(), System.currentTimeMillis() - start);
                        return new SearchResult("tavily_search", null, cleanSearchContent(text));
                    }
                }
            } catch (Exception e) {
                log.warn("Tavily 搜索异常: {}", e.getMessage());
            }
        }

        if (serperSearchServerName != null) {
            try {
                Map<String, Object> args = new HashMap<>();
                args.put("q", query);
                McpToolCallResponse result = mcpHostService.callTool(serperSearchServerName, "serper_search", args);
                if (result.isSuccess()) {
                    String text = mcpHostService.compressToolResult(result);
                    if (text != null && text.length() > 50 && !isErrorMessage(text)) {
                        log.info("Serper 搜索 '{}' 成功，{} 字符，{}ms", 
                                query, text.length(), System.currentTimeMillis() - start);
                        return new SearchResult("serper_search", null, cleanSearchContent(text));
                    }
                }
            } catch (Exception e) {
                log.warn("Serper 搜索异常: {}", e.getMessage());
            }
        }

        if (braveSearchServerName != null) {
            try {
                Map<String, Object> args = new HashMap<>();
                args.put("query", query);
                McpToolCallResponse result = mcpHostService.callTool(braveSearchServerName, "brave_web_search", args);
                if (result.isSuccess()) {
                    String text = mcpHostService.compressToolResult(result);
                    if (text != null && text.length() > 50 && !isErrorMessage(text)) {
                        log.info("Brave 搜索 '{}' 成功，{} 字符，{}ms", 
                                query, text.length(), System.currentTimeMillis() - start);
                        return new SearchResult("brave_search", null, cleanSearchContent(text));
                    }
                }
            } catch (Exception e) {
                log.warn("Brave 搜索异常: {}", e.getMessage());
            }
        }

        if (fetchServerName != null) {
            try {
                String encodedQuery = java.net.URLEncoder.encode(query, "UTF-8");
                String ddgUrl = buildDuckDuckGoUrl(query, encodedQuery);

                Map<String, Object> args = new HashMap<>();
                args.put("url", ddgUrl);
                args.put("max_length", 10000);

                McpToolCallResponse result = mcpHostService.callTool(fetchServerName, "fetch", args);
                if (result.isSuccess()) {
                    String text = mcpHostService.compressToolResult(result);
                    if (text != null && text.length() > 50 && !isErrorMessage(text)) {
                        log.info("DuckDuckGo 搜索 '{}' 成功，{} 字符，{}ms", 
                                query, text.length(), System.currentTimeMillis() - start);
                        return new SearchResult("duckduckgo_search", ddgUrl, cleanSearchContent(text));
                    }
                }
            } catch (Exception e) {
                log.warn("DuckDuckGo 搜索异常: {}", e.getMessage());
            }

            try {
                String encodedQuery = java.net.URLEncoder.encode(query, "UTF-8");
                String searchUrl = "https://www.bing.com/search?q=" + encodedQuery;

                Map<String, Object> args = new HashMap<>();
                args.put("url", searchUrl);
                args.put("max_length", 8000);

                McpToolCallResponse result = mcpHostService.callTool(fetchServerName, "fetch", args);
                if (result.isSuccess()) {
                    String text = mcpHostService.compressToolResult(result);
                    if (text != null && text.length() > 50 && !isErrorMessage(text)) {
                        log.info("Bing 搜索 '{}' 成功，{} 字符，{}ms", 
                                query, text.length(), System.currentTimeMillis() - start);
                        return new SearchResult("bing_search", searchUrl, cleanSearchContent(text));
                    }
                }
            } catch (Exception e) {
                log.warn("Bing 搜索异常: {}", e.getMessage());
            }
        }

        return null;
    }

    private SearchResult fetchUrl(String url, String fetchServerName, String puppeteerServerName) {
        long start = System.currentTimeMillis();

        if (puppeteerServerName != null) {
            try {
                Map<String, Object> navArgs = new HashMap<>();
                navArgs.put("url", url);
                McpToolCallResponse navResult = mcpHostService.callTool(puppeteerServerName, "navigate", navArgs);

                if (navResult.isSuccess()) {
                    Thread.sleep(1500);

                    Map<String, Object> contentArgs = new HashMap<>();
                    McpToolCallResponse contentResult = mcpHostService.callTool(puppeteerServerName, "get_content", contentArgs);

                    if (contentResult.isSuccess()) {
                        String text = mcpHostService.compressToolResult(contentResult);
                        if (text != null && text.length() > 100) {
                            log.info("Puppeteer 抓取 '{}' 成功，{} 字符，{}ms", 
                                    url, text.length(), System.currentTimeMillis() - start);
                            return new SearchResult("puppeteer", url, cleanSearchContent(text));
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Puppeteer 抓取异常: {}", e.getMessage());
            }
        }

        if (fetchServerName != null) {
            try {
                Map<String, Object> args = new HashMap<>();
                args.put("url", url);
                args.put("max_length", 10000);

                McpToolCallResponse result = mcpHostService.callTool(fetchServerName, "fetch", args);
                if (result.isSuccess()) {
                    String text = mcpHostService.compressToolResult(result);
                    if (text != null && text.length() > 100 && !isErrorMessage(text)) {
                        log.info("Fetch 抓取 '{}' 成功，{} 字符，{}ms", 
                                url, text.length(), System.currentTimeMillis() - start);
                        return new SearchResult("fetch", url, cleanSearchContent(text));
                    }
                }
            } catch (Exception e) {
                log.warn("Fetch 抓取异常: {}", e.getMessage());
            }
        }

        return null;
    }

    private String aggregateResults(List<SearchResult> results) {
        if (results.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        Set<String> seenContent = new HashSet<>();

        for (SearchResult result : results) {
            String content = result.getContent();
            if (content == null || content.isEmpty()) continue;

            String contentHash = content.substring(0, Math.min(100, content.length()));
            if (seenContent.contains(contentHash)) continue;
            seenContent.add(contentHash);

            if (result.getUrl() != null) {
                sb.append("【来源: ").append(result.getUrl()).append("】\n");
            } else {
                sb.append("【来源: ").append(result.getSource()).append("】\n");
            }
            sb.append(content).append("\n\n");
        }

        String aggregated = sb.toString();
        if (aggregated.length() > 12000) {
            aggregated = aggregated.substring(0, 12000) + "\n[内容已截断]";
        }

        return aggregated;
    }

    private boolean canDirectlyAnswer(String userMessage) {
        if (userMessage == null) return false;
        String lower = userMessage.toLowerCase();

        String[] needToolsPatterns = {
            "帮我写", "写一个", "写个", "实现", "创建", "生成",
            "帮我改", "修改", "重构", "优化代码",
            "执行", "运行", "调用", "操作",
            "发送", "提交", "上传", "下载",
            "爬取", "抓取", "采集"
        };

        for (String pattern : needToolsPatterns) {
            if (lower.contains(pattern)) return false;
        }

        return true;
    }

    private String buildDuckDuckGoUrl(String query, String encodedQuery) {
        StringBuilder url = new StringBuilder("https://html.duckduckgo.com/html/?q=");
        url.append(encodedQuery);
        
        String lower = query.toLowerCase();
        boolean needLatest = lower.contains("最新") || lower.contains("最近") || 
                lower.contains("新闻") || lower.contains("今天") ||
                lower.contains("昨天") || lower.contains("本周") ||
                lower.contains("本月") || lower.contains("2025") ||
                lower.contains("2024") || lower.contains("招聘") ||
                lower.contains("校招") || lower.contains("行情") ||
                lower.contains("动态") || lower.contains("更新");
        
        if (needLatest) {
            url.append("&s=1");
            url.append("&df=m");
            log.info("DuckDuckGo 搜索启用最新排序: {}", query);
        }
        
        return url.toString();
    }

    private boolean canAnswerWithSearchResults(ExecutionContext ctx, String searchResults, String userQuery) {
        try {
            List<Map<String, Object>> messages = new ArrayList<>();

            Map<String, Object> sysMsg = new HashMap<>();
            sysMsg.put("role", "system");
            sysMsg.put("content", "你是一个判断助手。你的任务是判断提供的搜索结果是否足以直接回答用户的问题。\n\n" +
                    "回答规则：\n" +
                    "1. 如果搜索结果包含了用户问题的答案，或者可以合理推断出答案，回复：YES\n" +
                    "2. 如果需要进一步操作（如爬取特定网站、调用API、执行代码等）才能回答，回复：NO\n" +
                    "3. 如果用户要求执行具体操作（如'爬取'、'下载'、'发送'等），回复：NO\n\n" +
                    "只回复 YES 或 NO，不要有其他内容。");
            messages.add(sysMsg);

<<<<<<< HEAD
            String truncatedResults = searchResults.length() > 5000
                    ? searchResults.substring(0, 5000) + "...[内容已截断]"
=======
            String truncatedResults = searchResults.length() > 3000 
                    ? searchResults.substring(0, 3000) + "...[内容已截断]" 
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
                    : searchResults;

            Map<String, Object> userMsg = new HashMap<>();
            userMsg.put("role", "user");
            userMsg.put("content", "用户问题：" + userQuery + "\n\n搜索结果：\n" + truncatedResults + "\n\n请判断：搜索结果是否足以直接回答？");
            messages.add(userMsg);

            LLMRequest request = LLMRequest.fromConfig(ctx.getActualModel(), ctx.getApiUrl(), ctx.getApiKey())
                    .toBuilder()
                    .messages(messages)
                    .stream(false)
                    .maxTokens(10)
                    .temperature(0.1)
<<<<<<< HEAD
                    .sessionId(ctx.getSessionId())
                    .userId(ctx.getUserId())
=======
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
                    .build();

            LLMCompleteResponse response = llmClient.chatComplete(request);
            String answer = response.getContent();

            if (answer != null) {
                String trimmed = answer.trim().toUpperCase();
                log.info("大模型判断结果: {} (原始: {})", trimmed, answer);
                return trimmed.contains("YES");
            }

            return false;
        } catch (Exception e) {
            log.warn("判断搜索结果是否可回答失败: {}", e.getMessage());
            return false;
        }
    }

    private boolean tryDirectAnswer(ExecutionContext ctx, String searchResults, Consumer<AIChatResponse> onResponse) {
        try {
            List<Map<String, Object>> messages = new ArrayList<>();

            Map<String, Object> sysMsg = new HashMap<>();
            sysMsg.put("role", "system");
            sysMsg.put("content", "你是一个智能助手。请基于提供的搜索结果，直接回答用户的问题。" +
                    "回答要准确、简洁、有条理。如果搜索结果中有具体数据，请引用。" +
                    "如果搜索结果不足以完全回答问题，请说明并提供你已知的相关信息。");
            messages.add(sysMsg);

            Map<String, Object> contextMsg = new HashMap<>();
            contextMsg.put("role", "user");
            contextMsg.put("content", "以下是搜索结果：\n\n" + searchResults + "\n\n请基于以上搜索结果回答：\n" + ctx.getUserMessage());
            messages.add(contextMsg);

            LLMRequest request = LLMRequest.fromConfig(ctx.getActualModel(), ctx.getApiUrl(), ctx.getApiKey())
                    .toBuilder()
                    .messages(messages)
                    .stream(true)
                    .maxTokens(2000)
                    .temperature(0.7)
<<<<<<< HEAD
                    .sessionId(ctx.getSessionId())
                    .userId(ctx.getUserId())
=======
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
                    .build();

            StringBuilder answerBuilder = new StringBuilder();
            final boolean[] success = {false};

            llmClient.streamChat(request, chunk -> {
                if (chunk.hasContent()) {
                    String token = chunk.getContent();
                    answerBuilder.append(token);
                    AIChatResponse response = new AIChatResponse();
                    response.setType("content");
                    response.setContent(token);
                    onResponse.accept(response);
                }
                if (chunk.isDone()) {
                    success[0] = true;
                    AIChatResponse response = new AIChatResponse();
                    response.setType("done");
                    response.setContent(answerBuilder.toString());
                    onResponse.accept(response);
                }
                if (chunk.isError()) {
                    log.error("直接回答流式响应错误: {}", chunk.getError());
                }
            });

            return success[0] && answerBuilder.length() > 50;
        } catch (Exception e) {
            log.warn("直接回答失败: {}", e.getMessage());
            return false;
        }
    }

    private boolean shouldPerformSearch(String userMessage) {
        if (userMessage == null || userMessage.length() < 3) return false;

        String lower = userMessage.toLowerCase();

        String[] noSearchPatterns = {
            "帮我写", "写一个", "写个", "实现一个", "实现个",
            "翻译", "计算", "排序", "算法",
            "解释一下", "什么是", "定义",
            "帮我改", "修改", "优化代码", "重构",
            "debug", "修复", "fix",
            "格式化", "转换"
        };

        for (String pattern : noSearchPatterns) {
            if (lower.contains(pattern)) {
                String[] searchHints = {
                    "最新", "最近", "新闻", "今天", "2025", "2024",
                    "官网", "网站", "链接", "网址",
                    "招聘", "校招", "价格", "行情",
                    "怎么样", "好不好", "评测", "评价"
                };
                for (String hint : searchHints) {
                    if (lower.contains(hint)) return true;
                }
                return false;
            }
        }

        String[] searchPatterns = {
            "搜索", "查找", "查询", "找一下", "搜一下",
            "最新", "最近", "新闻", "今天", "当前",
            "招聘", "校招", "求职", "岗位", "薪资",
            "价格", "行情", "股价", "汇率",
            "怎么样", "好不好", "评测", "评价", "对比",
            "官网", "网站", "链接",
            "天气", "航班", "列车", "快递",
            "谁", "哪", "什么时候", "多少",
            "发生", "事件", "公告", "通知",
            "了解", "介绍", "信息"
        };

        for (String pattern : searchPatterns) {
            if (lower.contains(pattern)) return true;
        }

        java.util.regex.Pattern urlPattern = java.util.regex.Pattern.compile("https?://");
        if (urlPattern.matcher(lower).find()) return true;

        return false;
    }

    private String performSingleSearch(String query, String fetchServerName, String puppeteerServerName,
                                       String braveSearchServerName, Set<String> visitedUrls) {
        StringBuilder result = new StringBuilder();

        if (braveSearchServerName != null) {
            try {
                Map<String, Object> args = new HashMap<>();
                args.put("query", query);
                McpToolCallResponse searchResult = mcpHostService.callTool(braveSearchServerName, "brave_web_search", args);
                if (searchResult.isSuccess()) {
                    String text = mcpHostService.compressToolResult(searchResult);
                    if (text != null && text.length() > 50 && !isErrorMessage(text)) {
                        result.append("【搜索: ").append(query).append("】\n")
                              .append(cleanSearchContent(text)).append("\n");
                        log.info("Brave 搜索 '{}' 成功，{} 字符", query, text.length());
                    } else if (text != null && isErrorMessage(text)) {
                        log.warn("Brave 搜索 '{}' 返回错误: {}", query, text.substring(0, Math.min(200, text.length())));
                    }
                } else {
                    log.warn("Brave 搜索 '{}' 失败: {}", query, searchResult.getError());
                }
            } catch (Exception e) {
                log.warn("Brave 搜索异常: {}", e.getMessage());
            }
        }

        if (result.length() == 0 && fetchServerName != null) {
            try {
                String encodedQuery = java.net.URLEncoder.encode(query, "UTF-8");
                String searchUrl = "https://www.bing.com/search?q=" + encodedQuery;
                visitedUrls.add(searchUrl);

                Map<String, Object> args = new HashMap<>();
                args.put("url", searchUrl);
                args.put("max_length", 8000);

                McpToolCallResponse fetchResult = mcpHostService.callTool(fetchServerName, "fetch", args);
                if (fetchResult.isSuccess()) {
                    String text = mcpHostService.compressToolResult(fetchResult);
                    if (text != null && text.length() > 50 && !isErrorMessage(text)) {
                        result.append("【搜索: ").append(query).append("】\n")
                              .append(cleanSearchContent(text)).append("\n");
                        log.info("Bing 搜索 '{}' 成功，{} 字符", query, text.length());
                    } else if (text != null && isErrorMessage(text)) {
                        log.warn("Bing 搜索 '{}' 返回错误: {}", query, text.substring(0, Math.min(200, text.length())));
                    }
                } else {
                    log.warn("Bing 搜索 '{}' 失败: {}", query, fetchResult.getError());
                }
            } catch (Exception e) {
                log.warn("Bing 搜索异常: {}", e.getMessage());
            }
        }

        return result.toString();
    }

    private boolean isErrorMessage(String text) {
        if (text == null || text.isEmpty()) return false;
        String lower = text.toLowerCase();
        return lower.contains("error") || lower.contains("exception") ||
               lower.contains("input validation") || lower.contains("not of type") ||
               lower.contains("invalid") || lower.contains("failed") ||
               lower.contains("timeout") || lower.contains("refused") ||
               lower.contains("robots.txt") && lower.length() < 500;
    }

    private List<String> extractUrlsFromSearchResults(String searchResults) {
        List<String> urls = new ArrayList<>();
        if (searchResults == null || searchResults.isEmpty()) return urls;

        java.util.regex.Pattern urlPattern = java.util.regex.Pattern.compile(
                "https?://[\\w\\-._~:/?#@!$&'()*+,;=%]+");
        java.util.regex.Matcher matcher = urlPattern.matcher(searchResults);

        Set<String> seen = new HashSet<>();
        while (matcher.find()) {
            String url = matcher.group();
            if (url.endsWith(".") || url.endsWith(",") || url.endsWith(";") || url.endsWith(")")) {
                url = url.substring(0, url.length() - 1);
            }

            if (seen.contains(url)) continue;

            if (url.contains("bing.com/search") || url.contains("google.com/search")
                    || url.contains("baidu.com/s?") || url.contains("google.com/url")) {
                continue;
            }
            if (url.contains("javascript:") || url.contains("data:")) {
                continue;
            }
            if (url.endsWith(".css") || url.endsWith(".js") || url.endsWith(".png")
                    || url.endsWith(".jpg") || url.endsWith(".gif") || url.endsWith(".svg")
                    || url.endsWith(".ico") || url.endsWith(".woff") || url.endsWith(".ttf")) {
                continue;
            }

            seen.add(url);
            urls.add(url);
        }

        if (urls.size() > 15) {
            return urls.subList(0, 15);
        }
        return urls;
    }

    private List<String> extractKnownUrls(String userMessage) {
        List<String> urls = new ArrayList<>();
        String lowerMsg = userMessage.toLowerCase();

        if (lowerMsg.contains("米哈游") || lowerMsg.contains("mihoyo")) {
            urls.add("https://campus.mihoyo.com/");
        }
        if (lowerMsg.contains("腾讯")) {
            urls.add("https://join.qq.com/");
        }
        if (lowerMsg.contains("阿里") || lowerMsg.contains("alibaba")) {
            urls.add("https://talent.alibaba.com/");
        }
        if (lowerMsg.contains("字节") || lowerMsg.contains("bytedance")) {
            urls.add("https://jobs.bytedance.com/");
        }
        if (lowerMsg.contains("华为")) {
            urls.add("https://career.huawei.com/");
        }

        return urls;
    }

    private List<String> deduplicateUrls(List<String> urls) {
        Set<String> seen = new HashSet<>();
        List<String> result = new ArrayList<>();
        for (String url : urls) {
            try {
                String normalized = url.toLowerCase();
                if (normalized.startsWith("http://")) normalized = "https://" + normalized.substring(7);
                if (normalized.endsWith("/")) normalized = normalized.substring(0, normalized.length() - 1);

                if (!seen.contains(normalized)) {
                    seen.add(normalized);
                    result.add(url);
                }
            } catch (Exception e) {
                if (!result.contains(url)) {
                    result.add(url);
                }
            }
        }
        return result;
    }

    private String cleanSearchContent(String content) {
        if (content == null || content.isEmpty()) return "";

        String cleaned = content;
        cleaned = cleaned.replaceAll("<script[^>]*>[\\s\\S]*?</script>", "");
        cleaned = cleaned.replaceAll("<style[^>]*>[\\s\\S]*?</style>", "");
        cleaned = cleaned.replaceAll("<[^>]+>", " ");
        cleaned = cleaned.replaceAll("&nbsp;", " ");
        cleaned = cleaned.replaceAll("&amp;", "&");
        cleaned = cleaned.replaceAll("&lt;", "<");
        cleaned = cleaned.replaceAll("&gt;", ">");
        cleaned = cleaned.replaceAll("&quot;", "\"");
        cleaned = cleaned.replaceAll("&#\\d+;", "");
        cleaned = cleaned.replaceAll("\\s{3,}", "\n\n");
        cleaned = cleaned.replaceAll("[ \\t]+", " ");

        return cleaned.trim();
    }

    private String buildFinalSearchResults(String searchResults, String deepCrawlResults) {
        StringBuilder sb = new StringBuilder();

        if (searchResults != null && !searchResults.isEmpty() && searchResults.length() > 50) {
            sb.append("=== 搜索结果 ===\n").append(searchResults).append("\n\n");
        }

        if (deepCrawlResults != null && !deepCrawlResults.isEmpty() && deepCrawlResults.length() > 50) {
            sb.append("=== 页面详情 ===\n").append(deepCrawlResults);
        }

        return sb.toString().trim();
    }

    private String fetchPageContent(String url, String fetchServerName, String puppeteerServerName) {
        if (puppeteerServerName != null) {
            try {
                Map<String, Object> navArgs = new HashMap<>();
                navArgs.put("url", url);
                McpToolCallResponse navResult = mcpHostService.callTool(puppeteerServerName, "navigate", navArgs);

                if (navResult.isSuccess()) {
                    Thread.sleep(2000);

                    Map<String, Object> contentArgs = new HashMap<>();
                    McpToolCallResponse contentResult = mcpHostService.callTool(puppeteerServerName, "get_content", contentArgs);

                    if (contentResult.isSuccess()) {
                        return mcpHostService.compressToolResult(contentResult);
                    }
                }
            } catch (Exception e) {
                log.warn("Puppeteer 抓取失败: {}", e.getMessage());
            }
        }

        if (fetchServerName != null) {
            try {
                Map<String, Object> args = new HashMap<>();
                args.put("url", url);
                args.put("max_length", 10000);

                McpToolCallResponse result = mcpHostService.callTool(fetchServerName, "fetch", args);
                if (result.isSuccess()) {
                    return mcpHostService.compressToolResult(result);
                }
            } catch (Exception e) {
                log.warn("Fetch 抓取失败: {}", e.getMessage());
            }
        }

        return null;
    }

    private String extractServerName(String toolFullName) {
        if (toolFullName != null && toolFullName.contains("__")) {
            return toolFullName.substring(0, toolFullName.indexOf("__"));
        }
        return null;
    }

    private SkillResult executeSkill(String skillName, ExecutionContext ctx, Consumer<AIChatResponse> onResponse) {
        try {
            SkillContext skillContext = new SkillContext();
            skillContext.setSessionId(ctx.getSessionId());
            skillContext.setUserId(ctx.getUserId() != null ? String.valueOf(ctx.getUserId()) : null);
            skillContext.setUserRequest(ctx.getUserMessage());

            SkillContext.SkillExecutionContext execContext = new SkillContext.SkillExecutionContext();
            skillContext.setExecutionContext(execContext);

            SkillResult result = skillExecutor.execute(skillName, skillContext, ctx.getUserId());

            if (result.isSuccess() && result.getData() != null) {
                result = processSkillOutputFiles(result, skillName, ctx, onResponse);
            }

            return result;

        } catch (Exception e) {
            log.error("执行技能异常: {} - {}", skillName, e.getMessage(), e);
            return SkillResult.failure("执行技能异常: " + e.getMessage());
        }
    }

    private SkillResult processSkillOutputFiles(SkillResult result, String skillName, ExecutionContext ctx, Consumer<AIChatResponse> onResponse) {
        Object data = result.getData();

        if (data instanceof Map) {
            Map<?, ?> dataMap = (Map<?, ?>) data;

            if (dataMap.containsKey("outputFile") || dataMap.containsKey("output_path") || dataMap.containsKey("filePath")) {
                String filePath = (String) (dataMap.get("outputFile") != null ? dataMap.get("outputFile") :
                                            dataMap.get("output_path") != null ? dataMap.get("output_path") :
                                            dataMap.get("filePath"));

                if (filePath != null && (filePath.endsWith(".pdf") || filePath.endsWith(".docx") ||
                                         filePath.endsWith(".xlsx") || filePath.endsWith(".pptx") ||
                                         filePath.endsWith(".md"))) {
                    String base64Content = skillExecutor.convertFileToBase64(filePath);
                    if (base64Content != null) {
                        String fileType = filePath.substring(filePath.lastIndexOf('.') + 1).toLowerCase();
                        String fileName = new File(filePath).getName();
                        long fileSize = new File(filePath).length();

                        // 生成文件 ID（用于前端 IndexedDB 存储）
                        String fileId = ctx.getSessionId() + "-" + System.currentTimeMillis();

                        // 构建文件结果
                        Map<String, Object> fileResult = new HashMap<>();
                        fileResult.put("type", "file");
                        fileResult.put("fileType", fileType);
                        fileResult.put("fileName", fileName);
                        fileResult.put("content", base64Content);
                        fileResult.put("fileSize", fileSize);
                        fileResult.put("resourceId", fileId);

                        result.setData(fileResult);
                        result.getMetadata().put("fileGenerated", true);
                        result.getMetadata().put("filePath", filePath);

                        // 发送 SSE 给前端
                        try {
                            AIChatResponse fileResponse = new AIChatResponse();
                            fileResponse.setType("file");
                            fileResponse.setSessionId(ctx.getSessionId());
                            fileResponse.setResourceId(fileId);
                            fileResponse.setFileName(fileName);
                            fileResponse.setFileType(fileType);
                            fileResponse.setFileSize(fileSize);
                            fileResponse.setContent(base64Content);
                            onResponse.accept(fileResponse);
                            log.info("Skill {} 发送文件 SSE 给前端: {} ({} bytes)", skillName, fileName, fileSize);
                        } catch (Exception e) {
                            log.error("发送文件 SSE 失败: {}", e.getMessage());
                        }

<<<<<<< HEAD
                        // 注册生成文件到会话索引，让后续对话可以引用
                        registerGeneratedFile(ctx.getSessionId(), fileId, fileName, filePath, fileType, fileSize);

=======
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
                        log.info("Skill {} generated file: {} ({} bytes, base64 length: {})",
                            skillName, filePath, fileSize, base64Content.length());
                    }
                }
            }
        } else if (data instanceof String) {
            String dataStr = (String) data;
            if (dataStr.endsWith(".pdf") || dataStr.endsWith(".docx") ||
                dataStr.endsWith(".xlsx") || dataStr.endsWith(".pptx") ||
                dataStr.endsWith(".md")) {
                File file = new File(dataStr);
                if (file.exists()) {
                    String base64Content = skillExecutor.convertFileToBase64(dataStr);
                    if (base64Content != null) {
                        String fileType = dataStr.substring(dataStr.lastIndexOf('.') + 1).toLowerCase();
                        String fileName = file.getName();
                        long fileSize = file.length();

                        // 生成文件 ID
                        String fileId = ctx.getSessionId() + "-" + System.currentTimeMillis();

                        // 构建文件结果
                        Map<String, Object> fileResult = new HashMap<>();
                        fileResult.put("type", "file");
                        fileResult.put("fileType", fileType);
                        fileResult.put("fileName", fileName);
                        fileResult.put("content", base64Content);
                        fileResult.put("fileSize", fileSize);
                        fileResult.put("resourceId", fileId);

                        result.setData(fileResult);
                        result.getMetadata().put("fileGenerated", true);
                        result.getMetadata().put("filePath", dataStr);

                        // 发送 SSE 给前端
                        try {
                            AIChatResponse fileResponse = new AIChatResponse();
                            fileResponse.setType("file");
                            fileResponse.setSessionId(ctx.getSessionId());
                            fileResponse.setResourceId(fileId);
                            fileResponse.setFileName(fileName);
                            fileResponse.setFileType(fileType);
                            fileResponse.setFileSize(fileSize);
                            fileResponse.setContent(base64Content);
                            onResponse.accept(fileResponse);
                            log.info("Skill {} 发送文件 SSE 给前端: {} ({} bytes)", skillName, fileName, fileSize);
                        } catch (Exception e) {
                            log.error("发送文件 SSE 失败: {}", e.getMessage());
                        }

                        log.info("Skill {} generated file: {} ({} bytes)", skillName, dataStr, fileSize);
                    }
                }
            }
        }

        return result;
    }

    /**
     * 检测工具执行结果中的文件导出操作，并发送文件给前端
     * 支持 bash-sandbox 的 export_file 和 write_file 工具导出的文件
     *
     * 统一流程：write_file → read_file（读取内容）→ 发送给前端
     */
    private void checkAndSendExportedFile(McpToolCallResponse toolResult, String toolName, String serverName,
                                          ExecutionContext ctx, Consumer<AIChatResponse> onResponse) {
        // 只检测 export_file 和 write_file 工具
        if (!"export_file".equals(toolName) && !"write_file".equals(toolName)) {
            return;
        }

        try {
            // 从 ContentItem 列表中提取文本内容
            List<McpToolCallResponse.ContentItem> contentItems = toolResult.getContent();
            if (contentItems == null || contentItems.isEmpty()) {
                return;
            }

            StringBuilder contentBuilder = new StringBuilder();
            for (McpToolCallResponse.ContentItem item : contentItems) {
                if ("text".equals(item.getType()) && item.getText() != null) {
                    contentBuilder.append(item.getText());
                }
            }
            String content = contentBuilder.toString();
            if (content.isEmpty()) {
                return;
            }

            // 尝试解析 JSON 输出
            Map<String, Object> outputMap = objectMapper.readValue(content, Map.class);

            String filePath = null;
            long fileSize = 0;

            if ("export_file".equals(toolName)) {
                // export_file: 检查 targetPath 或 target 字段
                if (outputMap.containsKey("targetPath")) {
                    filePath = (String) outputMap.get("targetPath");
                } else if (outputMap.containsKey("target")) {
                    filePath = (String) outputMap.get("target");
                }
                // export_file 可能直接返回文件大小
                if (outputMap.containsKey("size")) {
                    fileSize = ((Number) outputMap.get("size")).longValue();
                }
            } else if ("write_file".equals(toolName)) {
                // write_file: 检查 path 或 absolutePath 字段
                if (outputMap.containsKey("absolutePath")) {
                    filePath = (String) outputMap.get("absolutePath");
                } else if (outputMap.containsKey("path")) {
                    filePath = (String) outputMap.get("path");
                }
                // write_file 返回 bytes 字段表示文件大小
                if (outputMap.containsKey("bytes")) {
                    fileSize = ((Number) outputMap.get("bytes")).longValue();
                }
            }

            if (filePath == null) {
                return;
            }

            // 检查是否是支持的文件类型
            String fileType = null;
            String mimeType = null;
            String lowerPath = filePath.toLowerCase();

            // 文档类型
            if (lowerPath.endsWith(".docx")) {
                fileType = "docx";
                mimeType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            } else if (lowerPath.endsWith(".doc")) {
                fileType = "doc";
                mimeType = "application/msword";
            } else if (lowerPath.endsWith(".pdf")) {
                fileType = "pdf";
                mimeType = "application/pdf";
            } else if (lowerPath.endsWith(".xlsx")) {
                fileType = "xlsx";
                mimeType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            } else if (lowerPath.endsWith(".xls")) {
                fileType = "xls";
                mimeType = "application/vnd.ms-excel";
            } else if (lowerPath.endsWith(".pptx")) {
                fileType = "pptx";
                mimeType = "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            } else if (lowerPath.endsWith(".ppt")) {
                fileType = "ppt";
                mimeType = "application/vnd.ms-powerpoint";
            } else if (lowerPath.endsWith(".rtf")) {
                fileType = "rtf";
                mimeType = "application/rtf";
            } else if (lowerPath.endsWith(".odt")) {
                fileType = "odt";
                mimeType = "application/vnd.oasis.opendocument.text";
            } else if (lowerPath.endsWith(".ods")) {
                fileType = "ods";
                mimeType = "application/vnd.oasis.opendocument.spreadsheet";
            } else if (lowerPath.endsWith(".odp")) {
                fileType = "odp";
                mimeType = "application/vnd.oasis.opendocument.presentation";
            }
            // 文本类型
            else if (lowerPath.endsWith(".md")) {
                fileType = "md";
                mimeType = "text/markdown";
            } else if (lowerPath.endsWith(".txt")) {
                fileType = "txt";
                mimeType = "text/plain";
            } else if (lowerPath.endsWith(".html") || lowerPath.endsWith(".htm")) {
                fileType = "html";
                mimeType = "text/html";
            } else if (lowerPath.endsWith(".css")) {
                fileType = "css";
                mimeType = "text/css";
            } else if (lowerPath.endsWith(".csv")) {
                fileType = "csv";
                mimeType = "text/csv";
            } else if (lowerPath.endsWith(".xml")) {
                fileType = "xml";
                mimeType = "application/xml";
            }
            // 数据类型
            else if (lowerPath.endsWith(".json")) {
                fileType = "json";
                mimeType = "application/json";
            } else if (lowerPath.endsWith(".yaml") || lowerPath.endsWith(".yml")) {
                fileType = "yaml";
                mimeType = "application/x-yaml";
            } else if (lowerPath.endsWith(".sql")) {
                fileType = "sql";
                mimeType = "application/sql";
            } else if (lowerPath.endsWith(".toml")) {
                fileType = "toml";
                mimeType = "application/toml";
            }
            // 代码类型
            else if (lowerPath.endsWith(".js")) {
                fileType = "js";
                mimeType = "application/javascript";
            } else if (lowerPath.endsWith(".ts")) {
                fileType = "ts";
                mimeType = "application/typescript";
            } else if (lowerPath.endsWith(".jsx")) {
                fileType = "jsx";
                mimeType = "application/javascript";
            } else if (lowerPath.endsWith(".tsx")) {
                fileType = "tsx";
                mimeType = "application/typescript";
            } else if (lowerPath.endsWith(".py")) {
                fileType = "py";
                mimeType = "text/x-python";
            } else if (lowerPath.endsWith(".java")) {
                fileType = "java";
                mimeType = "text/x-java-source";
            } else if (lowerPath.endsWith(".go")) {
                fileType = "go";
                mimeType = "text/x-go";
            } else if (lowerPath.endsWith(".rs")) {
                fileType = "rs";
                mimeType = "text/x-rust";
            } else if (lowerPath.endsWith(".c")) {
                fileType = "c";
                mimeType = "text/x-c";
            } else if (lowerPath.endsWith(".cpp") || lowerPath.endsWith(".cc")) {
                fileType = "cpp";
                mimeType = "text/x-c++";
            } else if (lowerPath.endsWith(".h")) {
                fileType = "h";
                mimeType = "text/x-c-header";
            } else if (lowerPath.endsWith(".hpp")) {
                fileType = "hpp";
                mimeType = "text/x-c++-header";
            } else if (lowerPath.endsWith(".cs")) {
                fileType = "cs";
                mimeType = "text/x-csharp";
            } else if (lowerPath.endsWith(".php")) {
                fileType = "php";
                mimeType = "text/x-php";
            } else if (lowerPath.endsWith(".rb")) {
                fileType = "rb";
                mimeType = "text/x-ruby";
            } else if (lowerPath.endsWith(".swift")) {
                fileType = "swift";
                mimeType = "text/x-swift";
            } else if (lowerPath.endsWith(".kt") || lowerPath.endsWith(".kts")) {
                fileType = "kt";
                mimeType = "text/x-kotlin";
            } else if (lowerPath.endsWith(".scala")) {
                fileType = "scala";
                mimeType = "text/x-scala";
            } else if (lowerPath.endsWith(".sh") || lowerPath.endsWith(".bash")) {
                fileType = "sh";
                mimeType = "application/x-sh";
            } else if (lowerPath.endsWith(".ps1")) {
                fileType = "ps1";
                mimeType = "application/x-powershell";
            } else if (lowerPath.endsWith(".bat") || lowerPath.endsWith(".cmd")) {
                fileType = "bat";
                mimeType = "application/x-msdos-program";
            }
            // 图片类型
            else if (lowerPath.endsWith(".png")) {
                fileType = "png";
                mimeType = "image/png";
            } else if (lowerPath.endsWith(".jpg") || lowerPath.endsWith(".jpeg")) {
                fileType = "jpg";
                mimeType = "image/jpeg";
            } else if (lowerPath.endsWith(".gif")) {
                fileType = "gif";
                mimeType = "image/gif";
            } else if (lowerPath.endsWith(".svg")) {
                fileType = "svg";
                mimeType = "image/svg+xml";
            } else if (lowerPath.endsWith(".webp")) {
                fileType = "webp";
                mimeType = "image/webp";
            } else if (lowerPath.endsWith(".ico")) {
                fileType = "ico";
                mimeType = "image/x-icon";
            } else if (lowerPath.endsWith(".bmp")) {
                fileType = "bmp";
                mimeType = "image/bmp";
            }
            // 音频类型
            else if (lowerPath.endsWith(".mp3")) {
                fileType = "mp3";
                mimeType = "audio/mpeg";
            } else if (lowerPath.endsWith(".wav")) {
                fileType = "wav";
                mimeType = "audio/wav";
            } else if (lowerPath.endsWith(".ogg")) {
                fileType = "ogg";
                mimeType = "audio/ogg";
            } else if (lowerPath.endsWith(".m4a")) {
                fileType = "m4a";
                mimeType = "audio/mp4";
            } else if (lowerPath.endsWith(".flac")) {
                fileType = "flac";
                mimeType = "audio/flac";
            }
            // 视频类型
            else if (lowerPath.endsWith(".mp4")) {
                fileType = "mp4";
                mimeType = "video/mp4";
            } else if (lowerPath.endsWith(".webm")) {
                fileType = "webm";
                mimeType = "video/webm";
            } else if (lowerPath.endsWith(".avi")) {
                fileType = "avi";
                mimeType = "video/x-msvideo";
            } else if (lowerPath.endsWith(".mov")) {
                fileType = "mov";
                mimeType = "video/quicktime";
            } else if (lowerPath.endsWith(".mkv")) {
                fileType = "mkv";
                mimeType = "video/x-matroska";
            }
            // 压缩类型
            else if (lowerPath.endsWith(".zip")) {
                fileType = "zip";
                mimeType = "application/zip";
            } else if (lowerPath.endsWith(".tar")) {
                fileType = "tar";
                mimeType = "application/x-tar";
            } else if (lowerPath.endsWith(".gz")) {
                fileType = "gz";
                mimeType = "application/gzip";
            } else if (lowerPath.endsWith(".rar")) {
                fileType = "rar";
                mimeType = "application/x-rar-compressed";
            } else if (lowerPath.endsWith(".7z")) {
                fileType = "7z";
                mimeType = "application/x-7z-compressed";
            }
            // 其他类型
            else if (lowerPath.endsWith(".epub")) {
                fileType = "epub";
                mimeType = "application/epub+zip";
            } else if (lowerPath.endsWith(".mobi")) {
                fileType = "mobi";
                mimeType = "application/x-mobipocket-ebook";
            } else if (lowerPath.endsWith(".tex")) {
                fileType = "tex";
                mimeType = "application/x-tex";
            } else if (lowerPath.endsWith(".log")) {
                fileType = "log";
                mimeType = "text/plain";
            } else if (lowerPath.endsWith(".env")) {
                fileType = "env";
                mimeType = "text/plain";
            } else if (lowerPath.endsWith(".ini")) {
                fileType = "ini";
                mimeType = "text/plain";
            } else if (lowerPath.endsWith(".conf") || lowerPath.endsWith(".config")) {
                fileType = "conf";
                mimeType = "text/plain";
            }

            if (fileType == null) {
                log.debug("导出的文件类型不支持: {}", filePath);
                return;
            }

            // 统一通过 read_file 工具读取沙箱中的文件内容
            String fileContent = null;
            try {
                Map<String, Object> readArgs = new HashMap<>();
                readArgs.put("path", filePath);
                McpToolCallResponse readResult = mcpHostService.callTool(serverName, "read_file", readArgs, onResponse);

                if (readResult.isSuccess() && readResult.getContent() != null && !readResult.getContent().isEmpty()) {
                    // 解析 read_file 返回的 JSON
                    StringBuilder readContentBuilder = new StringBuilder();
                    for (McpToolCallResponse.ContentItem item : readResult.getContent()) {
                        if ("text".equals(item.getType()) && item.getText() != null) {
                            readContentBuilder.append(item.getText());
                        }
                    }
                    String readContent = readContentBuilder.toString();

                    Map<String, Object> readOutputMap = objectMapper.readValue(readContent, Map.class);
                    if (readOutputMap.containsKey("content")) {
                        fileContent = (String) readOutputMap.get("content");
                        if (readOutputMap.containsKey("size")) {
                            fileSize = ((Number) readOutputMap.get("size")).longValue();
                        }
                    }
                }
            } catch (Exception readEx) {
                log.warn("读取沙箱文件失败: {} - {}", filePath, readEx.getMessage());
                return;
            }

            if (fileContent == null || fileContent.isEmpty()) {
                log.warn("沙箱文件内容为空: {}", filePath);
                return;
            }

            // 将文件内容转换为 base64
            String base64Content = java.util.Base64.getEncoder().encodeToString(fileContent.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            String fileName = new File(filePath).getName();
            if (fileSize == 0) {
                fileSize = fileContent.length();
            }
            String fileId = ctx.getSessionId() + "-export-" + System.currentTimeMillis();

            // 发送 SSE 给前端
            AIChatResponse fileResponse = new AIChatResponse();
            fileResponse.setType("file");
            fileResponse.setSessionId(ctx.getSessionId());
            fileResponse.setResourceId(fileId);
            fileResponse.setFileName(fileName);
            fileResponse.setFileType(fileType);
            fileResponse.setFileSize(fileSize);
            fileResponse.setContent("data:" + mimeType + ";base64," + base64Content);
            onResponse.accept(fileResponse);

            log.info("工具 {}.{} 发送导出文件 SSE 给前端: {} ({} bytes, type={})",
                serverName, toolName, fileName, fileSize, fileType);

            // 清除沙箱中的临时文件
            try {
                Map<String, Object> deleteArgs = new HashMap<>();
                deleteArgs.put("path", filePath);
                mcpHostService.callTool(serverName, "delete_file", deleteArgs, onResponse);
                log.debug("已删除沙箱临时文件: {}", filePath);
            } catch (Exception deleteEx) {
                log.warn("删除沙箱文件失败: {} - {}", filePath, deleteEx.getMessage());
            }

        } catch (Exception e) {
            log.debug("解析导出文件结果失败: {}", e.getMessage());
        }
    }

    private String formatSkillResult(Object data) {
        if (data == null) {
            return "无结果";
        }

        try {
            if (data instanceof String) {
                return (String) data;
            } else if (data instanceof Map) {
                StringBuilder sb = new StringBuilder();
                Map<?, ?> map = (Map<?, ?>) data;
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    sb.append("- ").append(entry.getKey()).append(": ")
                            .append(String.valueOf(entry.getValue())).append("\n");
                }
                return sb.toString();
            } else if (data instanceof List) {
                StringBuilder sb = new StringBuilder();
                List<?> list = (List<?>) data;
                for (int i = 0; i < list.size(); i++) {
                    sb.append(i + 1).append(". ").append(String.valueOf(list.get(i))).append("\n");
                }
                return sb.toString();
            } else {
                return objectMapper.writeValueAsString(data);
            }
        } catch (Exception e) {
            return String.valueOf(data);
        }
    }

    /**
     * 【修复】判断用户消息是否为简单消息（问候、感谢等）
     * 避免对简单消息执行不必要的 Plan-Execute 流程
     */
    private boolean isSimpleUserMessage(String message) {
        if (message == null || message.trim().isEmpty()) return true;
        String trimmed = message.trim();
        // 短消息（<=15字）且不包含复杂关键词
        if (trimmed.length() > 15) return false;

        String[] simplePatterns = {
            "你好", "hello", "hi", "hey", "嗨", "哈喽",
            "谢谢", "感谢", "thanks", "thank you",
            "好的", "ok", "okay", "嗯", "是", "对", "好的",
            "再见", "bye", "拜拜",
            "测试", "test"
        };
        String lower = trimmed.toLowerCase();
        for (String pattern : simplePatterns) {
            if (lower.equals(pattern)) return true;
        }
        return false;
    }

    private boolean shouldUseTaskPlanner(ExecutionContext ctx) {
        try {
            // 【改进】优先使用缓存的任务分析结果，避免重复调用 LLM
            com.superfriend.superfriend.agent.planner.TaskAnalysis cachedAnalysis = ctx.getTaskAnalysis();
            if (cachedAnalysis != null) {
                com.superfriend.superfriend.agent.planner.TaskComplexity complexity = cachedAnalysis.getComplexity();
                // 如果本地分析已经确定是复杂任务，直接返回 true，无需再调用 LLM
                if (complexity == com.superfriend.superfriend.agent.planner.TaskComplexity.COMPLEX ||
                    complexity == com.superfriend.superfriend.agent.planner.TaskComplexity.VERY_COMPLEX) {
                    log.info("[sessionId={}] 使用缓存分析结果: complexity={}，跳过 LLM 复杂度判断",
                        ctx.getSessionId(), complexity);
                    return true;
                }
                // 如果本地分析确定是简单任务，直接返回 false
                if (complexity == com.superfriend.superfriend.agent.planner.TaskComplexity.SIMPLE) {
                    log.info("[sessionId={}] 使用缓存分析结果: complexity={}，跳过 LLM 复杂度判断",
                        ctx.getSessionId(), complexity);
                    return false;
                }
                // MODERATE 级别需要进一步判断
                log.info("[sessionId={}] 缓存分析结果为 MODERATE，需要进一步判断", ctx.getSessionId());
            }
            // 缓存不存在或需要进一步判断时，调用 TaskPlannerService
            return taskPlannerService.isComplexTask(ctx.getUserMessage(), ctx.getUserId(), ctx.getModel());
        } catch (Exception e) {
            log.warn("任务复杂度检测失败: {}", e.getMessage());
            return false;
        }
    }

    private boolean executeWithTaskPlanner(
            ExecutionContext ctx,
            ExecutionConfig config,
            Consumer<AIChatResponse> onResponse) {

        updateProgress(ctx, "任务规划", "正在制定执行计划...", 0.2, onResponse);

        try {
            TaskPlanDTO planDTO = taskPlannerService.generatePlan(
                    ctx.getUserMessage(),
                    ctx.getSessionId(),
                    ctx.getUserId(),
                    ctx.getAvailableTools(),
                    ctx.getModel()
            );

            if (planDTO == null) {
                // 【改进】发送计划生成失败通知给前端
                log.info("计划生成失败，继续使用 ReAct 模式");
                AIChatResponse planFailedResp = new AIChatResponse();
                planFailedResp.setContent(null);
                planFailedResp.setSessionId(ctx.getSessionId());
                planFailedResp.setModel(ctx.getActualModel());
                planFailedResp.setDone(false);
                planFailedResp.setType("plan_failed");
                Map<String, Object> failedData = new HashMap<>();
                failedData.put("reason", "计划生成失败，正在切换到 ReAct 模式");
                failedData.put("fallback", "react");
                planFailedResp.setToolCalls(failedData);
                onResponse.accept(planFailedResp);
                return false;
            }

            AIChatResponse planResp = new AIChatResponse();
            planResp.setContent(null);
            planResp.setSessionId(ctx.getSessionId());
            planResp.setModel(ctx.getActualModel());
            planResp.setDone(false);
            planResp.setType("plan");

            Map<String, Object> planData = new HashMap<>();
            planData.put("planId", planDTO.getPlanId());
            planData.put("summary", planDTO.getPlanSummary());
            planData.put("totalSteps", planDTO.getTotalSteps());
            planData.put("mode", config.isForcePlanExecuteMode() ? "complex" : "medium");

            List<Map<String, Object>> stepsData = new ArrayList<>();
            for (TaskStepDTO step : planDTO.getSteps()) {
                Map<String, Object> stepData = new HashMap<>();
                stepData.put("stepNumber", step.getStepNumber());
                stepData.put("description", step.getDescription());
                stepData.put("toolName", step.getToolName());
                stepsData.add(stepData);
            }
            planData.put("steps", stepsData);
            planResp.setToolCalls(planData);
            onResponse.accept(planResp);

            updateProgress(ctx, "计划执行", "正在执行计划步骤...", 0.3, onResponse);

            final StringBuilder finalResponseContent = new StringBuilder();
            final boolean[] reflectionDone = {false};

            Consumer<AIChatResponse> wrappedResponse = response -> {
                if (response.getContent() != null && !response.isDone()) {
                    finalResponseContent.append(response.getContent());
                }
                onResponse.accept(response);
            };

            // 【改进】传递 UserIntent 和知识提取配置
            taskPlannerService.executePlan(planDTO, ctx.getMessages(), ctx.getAvailableTools(),
                    ctx.getSessionId(), ctx.getActualModel(), ctx.getUserId(),
                    ctx.getUserIntent(), config.isEnableKnowledgeExtraction(), wrappedResponse);

            // 优化 Reflection 执行：只在满足条件时执行深度反思
            // 条件：1. 启用了 Reflection；2. 任务足够复杂（工具调用次数 > 2 或响应长度 > 200）
            if (config.isEnableReflection() && !reflectionDone[0] && shouldPerformDeepReflection(ctx)) {
                updateProgress(ctx, "结果验证", "正在对结果进行反思验证...", 0.9, onResponse);

                String responseContent = finalResponseContent.toString();
                if (!responseContent.isEmpty()) {
                    ReflectionResult reflection = performReflection(ctx, responseContent);
                    ctx.setLastReflection(reflection);
                    metricsService.recordReflection(ctx.getMetricsExecutionId(), reflection.isNeedsCorrection());

                    if (reflection.isNeedsCorrection()) {
                        log.info("反思检测到需要修正: {}", reflection.getCorrectionHint());

                        AIChatResponse reflectionResp = new AIChatResponse();
                        reflectionResp.setSessionId(ctx.getSessionId());
                        reflectionResp.setModel(ctx.getActualModel());
                        reflectionResp.setDone(false);
                        reflectionResp.setType("reflection");
                        Map<String, Object> reflectionData = new HashMap<>();
                        reflectionData.put("needsCorrection", true);
                        reflectionData.put("correctionHint", reflection.getCorrectionHint());
                        reflectionData.put("confidenceScore", reflection.getConfidenceScore());
                        reflectionResp.setToolCalls(reflectionData);
                        onResponse.accept(reflectionResp);
                    }
                }
                reflectionDone[0] = true;
            }

            return true;

        } catch (Exception e) {
            log.error("Task Planner 执行失败: {}", e.getMessage());
            return false;
        }
    }

    private void executeReActLoop(
            ExecutionContext ctx,
            ExecutionConfig config,
            Consumer<AIChatResponse> onResponse) {

        updateProgress(ctx, "ReAct 循环", "开始推理循环...", 0.3, onResponse);

        // 设置上下文配置
        ctx.setMaxIterations(config.getMaxIterations());
        ctx.setMaxConsecutiveErrors(config.getMaxConsecutiveErrors());

<<<<<<< HEAD
=======
        // 【改进】使用可配置的升级阈值
        final int UPGRADE_TO_PLAN_EXECUTE_THRESHOLD = config.getUpgradeToPlanExecuteThreshold();

>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
        // 执行元认知分析
        ExecutionContext.MetaCognition metaCognition = ctx.performMetaCognition();
        log.info("元认知评估: hasEnoughInfo={}, withinCapability={}, confidence={}",
                metaCognition.isHasEnoughInfo(), metaCognition.isWithinCapability(), metaCognition.getConfidenceLevel());

        if (metaCognition.isNeedsUserClarification()) {
            AIChatResponse clarificationResp = new AIChatResponse();
            clarificationResp.setSessionId(ctx.getSessionId());
            clarificationResp.setModel(ctx.getActualModel());
            clarificationResp.setDone(false);
            clarificationResp.setType("clarification");
            Map<String, Object> clarificationData = new HashMap<>();
            clarificationData.put("question", metaCognition.getClarificationQuestion());
            clarificationData.put("missingInfo", metaCognition.getMissingInfo());
            clarificationResp.setToolCalls(clarificationData);
            onResponse.accept(clarificationResp);
        }

        for (int iteration = 0; iteration < config.getMaxIterations(); iteration++) {
            ctx.setCurrentIteration(iteration);
            metricsService.recordIteration(ctx.getMetricsExecutionId());

<<<<<<< HEAD
=======
            // 【改进】ReAct → Plan-Execute 升级机制
            // 条件：连续错误达到阈值 且 未强制使用 ReAct 且 未强制使用 Plan-Execute 且 未超过切换次数限制
            if (ctx.getConsecutiveErrors() >= UPGRADE_TO_PLAN_EXECUTE_THRESHOLD
                    && !config.isForceReactMode()
                    && !config.isForcePlanExecuteMode()
                    && ctx.getModeSwitchCount() < ExecutionContext.MAX_MODE_SWITCHES) {
                log.info("[ReAct升级] 连续错误达到 {}，尝试升级到 Plan-Execute (切换次数: {}/{})",
                    ctx.getConsecutiveErrors(), ctx.getModeSwitchCount() + 1, ExecutionContext.MAX_MODE_SWITCHES);
                updateProgress(ctx, "策略升级", "ReAct 模式遇到困难，正在升级到 Plan-Execute 模式...", ctx.getProgress(), onResponse);

                ctx.setModeSwitchCount(ctx.getModeSwitchCount() + 1);
                resetContextForFallback(ctx);
                if (executeWithTaskPlanner(ctx, config, onResponse)) {
                    return;  // Plan-Execute 成功
                }
                // Plan-Execute 也失败了，继续 ReAct
                log.warn("[ReAct升级] Plan-Execute 升级失败，继续 ReAct 模式");
                ctx.setConsecutiveErrors(0);  // 重置错误计数
            }

>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
            if (ctx.getReasoningChain().shouldTerminateEarly()) {
                String terminationReason = ctx.getReasoningChain().getTerminationMessage();
                log.warn("[ReAct循环] ReasoningChain 检测到需要早期终止: {}", terminationReason);
                sendErrorResponse(ctx, "执行被系统优化终止: " + terminationReason + "。请简化您的请求或提供更明确的目标。", onResponse);
                return;
            }

            if (ctx.getReasoningChain().getSteps().size() > MAX_REASONING_STEPS) {
                log.warn("[ReAct循环] 推理步骤超过最大限制: {}", ctx.getReasoningChain().getSteps().size());
                sendErrorResponse(ctx, "推理步骤过多，请简化您的请求。", onResponse);
                return;
            }

            if (ctx.isStuckInUrlLoop()) {
                String diagnosis = ctx.getStallDiagnosis();
                log.warn("检测到执行停滞: {}", diagnosis);
                Map<String, Object> stallMsg = new HashMap<>();
                stallMsg.put("role", "user");
                stallMsg.put("content", "【系统检测到执行停滞】\n" +
                        diagnosis + "\n" +
                        "请立即停止重复操作，基于已有信息给出最终答案。如果信息不足，请说明缺少什么。");
                ctx.getMessages().add(stallMsg);
                ctx.addEvidence("force_terminate", true);
            }

            // 使用 ExecutionContext 的终止检查
            ExecutionContext.TerminationCheck terminationCheck = ctx.checkTermination();
            if (terminationCheck.isShouldTerminate()) {
                log.info("终止判断: reason={}", terminationCheck.getReason());
                sendErrorResponse(ctx, terminationCheck.getSummary(), onResponse);
                return;
            }

            if (!ctx.canContinue()) {
                return;
            }

            handleContextCompression(ctx);

            if (handleInterrupt(ctx, onResponse)) {
                ctx.forceTerminate();
                return;
            }

            double progress = 0.3 + (0.5 * iteration / config.getMaxIterations());
            updateProgress(ctx, "ReAct 循环",
                    String.format("推理迭代 %d/%d", iteration + 1, config.getMaxIterations()),
                    progress, onResponse);

            // 【改进】发送 ReAct 迭代事件，提供更详细的进度信息
            AIChatResponse iterResp = new AIChatResponse();
            iterResp.setSessionId(ctx.getSessionId());
            iterResp.setModel(ctx.getActualModel());
            iterResp.setDone(false);
            iterResp.setType("react_iteration");
            Map<String, Object> iterData = new HashMap<>();
            iterData.put("iteration", iteration + 1);
            iterData.put("maxIterations", config.getMaxIterations());
            iterData.put("consecutiveErrors", ctx.getConsecutiveErrors());
            iterData.put("toolCallsCount", ctx.getToolResults().size());
            iterData.put("reasoningSteps", ctx.getReasoningChain().getSteps().size());
            iterResp.setToolCalls(iterData);
            onResponse.accept(iterResp);

            ModelResponse modelResponse = callModel(ctx, config, onResponse);
            if (modelResponse == null) {
                ctx.setConsecutiveErrors(ctx.getConsecutiveErrors() + 1);
                log.error("【ReAct循环】模型调用返回null，连续错误次数: {}/{}",
                    ctx.getConsecutiveErrors(), config.getMaxConsecutiveErrors());

                // DynamicConfigManager: 根据错误模式动态调整
                if (ctx.getSessionConfig() != null) {
                    try {
                        // 使用存储的错误类型进行推断
                        ErrorType errorType = ctx.getLastErrorType() != null
                            ? ctx.getLastErrorType() : ErrorType.NETWORK_ERROR;
                        com.superfriend.superfriend.agent.config.DynamicConfigManager.ErrorPattern errorPattern =
                            detectErrorPattern(errorType, ctx);
                        if (errorPattern != null) {
                            // 使用 adjustForErrorPattern 返回值，避免重复调用
                            com.superfriend.superfriend.agent.config.DynamicConfigManager.SessionConfig updatedConfig =
                                dynamicConfigManager.adjustForErrorPattern(ctx.getSessionId(), errorPattern);
                            ctx.setSessionConfig(updatedConfig);  // 同步 SessionConfig
                            config.setToolTimeoutMs(updatedConfig.getToolTimeoutMs());
                            config.setMaxConsecutiveErrors(updatedConfig.getMaxConsecutiveErrors());
                            ctx.setMaxConsecutiveErrors(updatedConfig.getMaxConsecutiveErrors());
                            log.info("[DynamicConfig] 错误模式 {} 调整: timeoutMs={}, maxErrors={}, lastError={}",
                                errorPattern, updatedConfig.getToolTimeoutMs(), updatedConfig.getMaxConsecutiveErrors(),
                                ctx.getLastErrorMessage());
                        }
                    } catch (Exception e) {
                        log.warn("[DynamicConfig] 错误模式调整失败: {}", e.getMessage());
                    }
                }

                cleanIncompleteToolCalls(ctx.getMessages());
                log.info("【ReAct循环】已清理不完整的 tool_calls 消息");

                // 使用 ErrorRecoveryManager 判断是否应该重试
                ErrorType retryErrorType = ctx.getLastErrorType() != null
                    ? ctx.getLastErrorType() : ErrorType.NETWORK_ERROR;
                boolean shouldRetry = errorRecoveryManager.shouldRetry(retryErrorType);
                long retryDelay = errorRecoveryManager.getRetryDelay(retryErrorType, ctx.getConsecutiveErrors());

                if (!shouldRetry || ctx.getConsecutiveErrors() >= config.getMaxConsecutiveErrors()) {
                    sendErrorResponse(ctx, "AI 模型连续多次调用失败 (" + ctx.getConsecutiveErrors() + "次)，请检查网络连接或API配置", onResponse);
                    return;
                }

                log.info("【ReAct循环】等待 {}ms 后重试", retryDelay);
                sleep(retryDelay);
                continue;
            }

            ctx.setConsecutiveErrors(0);

            // 检查强制终止标志（URL重复访问过多）
            if (Boolean.TRUE.equals(ctx.getEvidence("force_terminate"))) {
                log.warn("检测到强制终止标志，要求模型直接回答");
                Map<String, Object> forceMsg = new HashMap<>();
                forceMsg.put("role", "user");
                forceMsg.put("content", "系统检测到重复操作过多，请立即基于已有信息给出最终答案，不要再调用任何工具。");
                ctx.getMessages().add(forceMsg);
                // 清除标志，给模型一次机会
                ctx.addEvidence("force_terminate", false);
            }

            if (modelResponse.hasToolCalls()) {
                List<String> toolNames = modelResponse.getToolCalls().stream()
                    .map(ToolCall::getFullName)
                    .collect(java.util.stream.Collectors.toList());
                log.info("【ReAct循环】模型返回工具调用，数量: {}, 工具列表: {}",
                    modelResponse.getToolCalls().size(), toolNames);

                // 再次检查强制终止（如果模型仍然要调用工具）
                if (Boolean.TRUE.equals(ctx.getEvidence("force_terminate"))) {
                    log.warn("强制终止状态下模型仍尝试调用工具，跳过工具调用");
                    Map<String, Object> skipMsg = new HashMap<>();
                    skipMsg.put("role", "user");
                    skipMsg.put("content", "你被禁止继续调用工具。请直接给出最终答案。");
                    ctx.getMessages().add(skipMsg);
                    continue;
                }

                // 智能工具选择建议 - 检查是否选择了最佳工具
                for (ToolCall toolCall : modelResponse.getToolCalls()) {
                    Map<String, Object> args = toolCall.getArguments();
                    if (args != null && args.containsKey("url")) {
                        String url = String.valueOf(args.get("url"));
                        String advice = ctx.getToolSelectionAdvice(url);
                        if (advice != null) {
                            log.info("工具选择建议: {} -> {}", url, advice);

                            // 检查是否选择了次优工具
                            String toolName = toolCall.getToolName().toLowerCase();
                            String lowerUrl = url.toLowerCase();

                            // 如果URL需要JS渲染但选择了fetch，注入建议
                            boolean needsJs = lowerUrl.contains("zhihu.com") || lowerUrl.contains("weibo.com") ||
                                    lowerUrl.contains("jobs.") || lowerUrl.contains("career.") ||
                                    lowerUrl.contains("bilibili.com") || lowerUrl.contains("youtube.com");
                            boolean choseFetch = toolName.contains("fetch");

                            // 如果URL支持静态抓取但选择了puppeteer，注入建议（但不强制）
                            boolean supportsStatic = lowerUrl.contains("github.com") || lowerUrl.contains("stackoverflow.com");
                            boolean chosePuppeteer = toolName.contains("puppeteer");

                            if (needsJs && choseFetch) {
                                log.warn("模型选择了fetch访问需要JS渲染的URL: {}", url);
                                Map<String, Object> adviceMsg = new HashMap<>();
                                adviceMsg.put("role", "user");
                                adviceMsg.put("content", advice + "\n如果fetch结果不理想，可以尝试使用puppeteer_navigate。");
                                ctx.getMessages().add(adviceMsg);
                            } else if (supportsStatic && chosePuppeteer && !ctx.hasVisitedUrl(url)) {
                                // 首次访问静态网站用puppeteer也可以，只是提示下次可以更快
                                log.info("提示：静态网站使用fetch更快，但puppeteer也可以工作");
                            }
                        }
                    }
                }

                // Record thought before action
                ctx.addThought("决定调用工具: " + String.join(", ", toolNames));

                handleToolCalls(ctx, modelResponse, config, onResponse);

                log.info("【ReAct循环】工具执行完成，继续下一轮循环");
                continue;
            }

<<<<<<< HEAD
            // 【新增】处理 LLM 输出被截断的情况（finish_reason = "length"）
            if (modelResponse.isTruncated()) {
                log.warn("【ReAct循环】LLM 输出被截断 (finish_reason=length)，要求续写");
                Map<String, Object> continueMsg = new HashMap<>();
                continueMsg.put("role", "user");
                continueMsg.put("content", "你的输出被截断了，请继续输出剩余内容。从你上次中断的地方继续，不要重复已输出的内容。");
                ctx.getMessages().add(continueMsg);
                continue;
            }

            if (modelResponse.hasContent() || modelResponse.hasReasoningContent()) {
                String responseContent = modelResponse.getContent() != null ? modelResponse.getContent() : "";

                // 【增强】检查内容是否真正完成了任务
                if (!isSubstantiveCompletion(ctx, responseContent)) {
                    log.warn("【ReAct循环】模型返回内容但可能未真正完成任务，继续循环");

                    // 添加提示让模型继续
                    Map<String, Object> continueMsg = new HashMap<>();
                    continueMsg.put("role", "user");
                    continueMsg.put("content", "你的回答似乎不完整。请继续完成任务，确保给出实质性的结果。" +
                        "如果需要调用工具，请调用工具；如果已完成，请给出详细、完整的回答。");
                    ctx.getMessages().add(continueMsg);

                    // 增加无进展计数
                    ctx.setConsecutiveNoProgress(ctx.getConsecutiveNoProgress() + 1);
                    continue;
                }

=======
            if (modelResponse.hasContent() || modelResponse.hasReasoningContent()) {
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
                log.info("【ReAct循环】模型返回内容，任务完成");

                // Record final thought
                ctx.addThought("任务完成，生成最终回答");

                // ReAct 循环结束时执行反思（如果启用且满足条件）
                if (config.isEnableReflection() && shouldPerformDeepReflection(ctx)) {
<<<<<<< HEAD
=======
                    String responseContent = modelResponse.getContent() != null ? modelResponse.getContent() : "";
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
                    if (!responseContent.isEmpty()) {
                        updateProgress(ctx, "结果验证", "正在对结果进行反思验证...", 0.9, onResponse);
                        ReflectionResult reflection = performReflection(ctx, responseContent);
                        ctx.setLastReflection(reflection);
                        metricsService.recordReflection(ctx.getMetricsExecutionId(), reflection.isNeedsCorrection());

                        if (reflection.isNeedsCorrection()) {
                            log.info("反思检测到需要修正: {}", reflection.getCorrectionHint());

<<<<<<< HEAD
                            // 【增强】强制执行修正
                            if (ctx.getConsecutiveCorrections() < 3) {
                                ctx.setConsecutiveCorrections(ctx.getConsecutiveCorrections() + 1);

                                AIChatResponse reflectionResp = new AIChatResponse();
                                reflectionResp.setSessionId(ctx.getSessionId());
                                reflectionResp.setModel(ctx.getActualModel());
                                reflectionResp.setDone(false);
                                reflectionResp.setType("reflection");
                                Map<String, Object> reflectionData = new HashMap<>();
                                reflectionData.put("needsCorrection", true);
                                reflectionData.put("correctionHint", reflection.getCorrectionHint());
                                reflectionData.put("confidenceScore", reflection.getConfidenceScore());
                                reflectionResp.setToolCalls(reflectionData);
                                onResponse.accept(reflectionResp);

                                // 添加修正提示
                                Map<String, Object> correctionMsg = new HashMap<>();
                                correctionMsg.put("role", "user");
                                correctionMsg.put("content", "你的回答存在问题：" + reflection.getCorrectionHint() +
                                    "\n\n请重新回答用户的问题，确保完整准确地解决问题。");
                                ctx.getMessages().add(correctionMsg);

                                log.info("触发修正循环，修正次数: {}", ctx.getConsecutiveCorrections());
                                continue;  // 继续循环，让模型重新回答
                            } else {
                                log.warn("修正次数已达上限 ({}次)，强制结束", ctx.getConsecutiveCorrections());
                            }
=======
                            AIChatResponse reflectionResp = new AIChatResponse();
                            reflectionResp.setSessionId(ctx.getSessionId());
                            reflectionResp.setModel(ctx.getActualModel());
                            reflectionResp.setDone(false);
                            reflectionResp.setType("reflection");
                            Map<String, Object> reflectionData = new HashMap<>();
                            reflectionData.put("needsCorrection", true);
                            reflectionData.put("correctionHint", reflection.getCorrectionHint());
                            reflectionData.put("confidenceScore", reflection.getConfidenceScore());
                            reflectionResp.setToolCalls(reflectionData);
                            onResponse.accept(reflectionResp);
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
                        }
                    }
                }

                sendDoneResponse(ctx, onResponse);
                return;
            }

            log.warn("模型既无工具调用也无内容，迭代 {}，连续错误次数: {}/{}",
                iteration + 1, ctx.getConsecutiveErrors() + 1, config.getMaxConsecutiveErrors());

            ctx.setConsecutiveErrors(ctx.getConsecutiveErrors() + 1);

            cleanIncompleteToolCalls(ctx.getMessages());
            log.info("【ReAct循环】已清理不完整的 tool_calls 消息（模型无响应时）");

            if (ctx.getConsecutiveErrors() >= config.getMaxConsecutiveErrors()) {
                sendErrorResponse(ctx, "AI 模型连续多次返回空响应 (" + ctx.getConsecutiveErrors() + "次)，可能是消息格式问题或模型理解困难", onResponse);
                return;
            }

            Map<String, Object> retryMsg = new HashMap<>();
            retryMsg.put("role", "user");
            retryMsg.put("content", "请继续完成任务。如果已完成，请直接给出最终答案。");
            ctx.getMessages().add(retryMsg);

            // DynamicConfigManager: 在每次迭代结束时统一进行推理链调整
            // 无论模型返回工具调用、内容还是空响应，都检查推理链状态
            if (ctx.getSessionConfig() != null && iteration > 0 && iteration % 5 == 0) {
                try {
                    com.superfriend.superfriend.agent.config.DynamicConfigManager.SessionConfig updatedConfig =
                        dynamicConfigManager.adjustForReasoningChain(ctx.getSessionId(), ctx.getReasoningChain());
                    ctx.setSessionConfig(updatedConfig);  // 同步 SessionConfig
                    ctx.setMaxConsecutiveErrors(updatedConfig.getMaxConsecutiveErrors());
                    config.setMaxConsecutiveErrors(updatedConfig.getMaxConsecutiveErrors());
                    log.info("[DynamicConfig] 迭代 {} 后推理链调整: maxErrors={}, successRate={}, actions={}",
                        iteration, updatedConfig.getMaxConsecutiveErrors(),
                        String.format("%.2f", ctx.getReasoningChain().getSuccessRate()),
                        ctx.getReasoningChain().getActionCount());
                } catch (Exception e) {
                    log.warn("[DynamicConfig] 推理链调整失败: {}", e.getMessage());
                }
            }
        }

        sendErrorResponse(ctx, "对话已达到最大轮次限制", onResponse);
    }

    private ToolCall parseToolCallFromNextAction(String nextAction, List<McpToolDefinition> availableTools, Map<String, Object> arguments) {
        if (nextAction == null || nextAction.isEmpty()) {
            return null;
        }

        if (availableTools == null || availableTools.isEmpty()) {
            log.warn("可用工具列表为空，无法解析 nextAction: {}", nextAction);
            return null;
        }

        for (McpToolDefinition tool : availableTools) {
            if (nextAction.equals(tool.getName())) {
                ToolCall toolCall = new ToolCall();
                toolCall.setId("tc_" + UUID.randomUUID().toString().substring(0, 8));
                toolCall.setServerName(tool.getServerName());
                String actualToolName = tool.getName();
                if (actualToolName.contains("__")) {
                    actualToolName = actualToolName.substring(actualToolName.indexOf("__") + 2);
                }
                toolCall.setToolName(actualToolName);
                toolCall.setArguments(arguments != null ? arguments : new HashMap<>());
                log.debug("匹配到工具: toolName={}, serverName={}, arguments={}", actualToolName, tool.getServerName(), toolCall.getArguments());
                return toolCall;
            }
        }

        if (nextAction.contains("__")) {
            String[] parts = nextAction.split("__", 2);
            if (parts.length == 2) {
                String serverName = parts[0];
                String originalToolName = parts[1];
                
                for (McpToolDefinition tool : availableTools) {
                    if (serverName.equals(tool.getServerName()) && 
                        tool.getName() != null && 
                        tool.getName().endsWith("__" + originalToolName)) {
                        ToolCall toolCall = new ToolCall();
                        toolCall.setId("tc_" + UUID.randomUUID().toString().substring(0, 8));
                        toolCall.setServerName(serverName);
                        toolCall.setToolName(originalToolName);
                        toolCall.setArguments(arguments != null ? arguments : new HashMap<>());
                        log.debug("匹配到工具(通过后缀匹配): toolName={}, serverName={}, arguments={}", originalToolName, serverName, toolCall.getArguments());
                        return toolCall;
                    }
                }
            }
        }

        log.warn("未找到匹配的工具: nextAction={}, 可用工具数量={}", nextAction, availableTools.size());
        for (McpToolDefinition tool : availableTools) {
            log.debug("可用工具: name={}, serverName={}", tool.getName(), tool.getServerName());
        }
        return null;
    }

    /**
     * 根据异常信息推断错误类型
     */
    private ErrorType inferErrorType(Exception e) {
        if (e == null) return ErrorType.UNKNOWN_ERROR;

        String message = e.getMessage();
        if (message == null) {
            message = e.getClass().getSimpleName();
        }
        String lowerMessage = message.toLowerCase();

        // 超时错误
        if (lowerMessage.contains("timeout") || lowerMessage.contains("timed out") ||
            lowerMessage.contains("超时")) {
            return ErrorType.TIMEOUT_ERROR;
        }

        // 速率限制
        if (lowerMessage.contains("rate limit") || lowerMessage.contains("429") ||
            lowerMessage.contains("too many") || lowerMessage.contains("频率限制")) {
            return ErrorType.RATE_LIMIT_ERROR;
        }

        // 网络错误
        if (lowerMessage.contains("connection") || lowerMessage.contains("network") ||
            lowerMessage.contains("socket") || lowerMessage.contains("connect") ||
            lowerMessage.contains("网络") || lowerMessage.contains("连接")) {
            return ErrorType.NETWORK_ERROR;
        }

        // 认证错误
        if (lowerMessage.contains("auth") || lowerMessage.contains("401") ||
            lowerMessage.contains("403") || lowerMessage.contains("forbidden")) {
            return ErrorType.AUTHENTICATION_ERROR;
        }

        // 服务器错误
        if (lowerMessage.contains("500") || lowerMessage.contains("503") ||
            lowerMessage.contains("server error") || lowerMessage.contains("服务器")) {
            return ErrorType.SERVER_ERROR;
        }

        return ErrorType.UNKNOWN_ERROR;
    }

    /**
     * 根据错误类型和执行上下文检测错误模式
     */
    private com.superfriend.superfriend.agent.config.DynamicConfigManager.ErrorPattern detectErrorPattern(
            ErrorType errorType, ExecutionContext ctx) {
        int recentFailures = ctx.getReasoningChain().getFailureCount();

        if (errorType == ErrorType.TIMEOUT_ERROR) {
            return com.superfriend.superfriend.agent.config.DynamicConfigManager.ErrorPattern.FREQUENT_TIMEOUTS;
        }
        if (errorType == ErrorType.RATE_LIMIT_ERROR) {
            return com.superfriend.superfriend.agent.config.DynamicConfigManager.ErrorPattern.RATE_LIMITING;
        }
        if (errorType == ErrorType.NETWORK_ERROR && recentFailures >= 2) {
            return com.superfriend.superfriend.agent.config.DynamicConfigManager.ErrorPattern.NETWORK_INSTABILITY;
        }
        if (recentFailures >= 3) {
            return com.superfriend.superfriend.agent.config.DynamicConfigManager.ErrorPattern.TOOL_ERRORS;
        }
        return null;
    }

    /**
     * 根据模型名称获取最大 Token 数（上下文长度）
     */
    private long getMaxTokensForModel(String modelName) {
        if (modelName == null) return 128000;  // 默认值

        String lowerModel = modelName.toLowerCase();

        // GPT-4 系列
        if (lowerModel.contains("gpt-4") || lowerModel.contains("gpt4")) {
            if (lowerModel.contains("turbo") || lowerModel.contains("1106") || lowerModel.contains("0125")) {
                return 128000;
            }
            return 8192;  // 标准 GPT-4
        }

        // GPT-3.5
        if (lowerModel.contains("gpt-3.5") || lowerModel.contains("gpt35")) {
            return 16385;
        }

        // Claude 系列
        if (lowerModel.contains("claude")) {
            if (lowerModel.contains("opus") || lowerModel.contains("sonnet")) {
                return 200000;
            }
            return 100000;
        }

        // DeepSeek 系列
        if (lowerModel.contains("deepseek")) {
            if (lowerModel.contains("v3") || lowerModel.contains("r1")) {
                return 128000;
            }
            return 64000;
        }

        // Qwen 系列
        if (lowerModel.contains("qwen")) {
            if (lowerModel.contains("max") || lowerModel.contains("2.5")) {
                return 128000;
            }
            return 8192;
        }

        // Gemini 系列
        if (lowerModel.contains("gemini") || lowerModel.contains("flash")) {
            return 1000000;  // Gemini 有很长的上下文
        }

        // 默认返回常见值
        return 128000;
    }

    private boolean checkCanContinue(
            ExecutionContext ctx,
            ExecutionConfig config,
            Consumer<AIChatResponse> onResponse) {

        if (ctx.isExecutionEnded()) {
            return false;
        }

        return true;
    }

    private void handleContextCompression(ExecutionContext ctx) {
        try {
            long currentTokens = smartCompressionStrategy.estimateTokens(ctx.getMessages());
            long maxTokens = 128000;
            
            SmartCompressionStrategy.CompressionContext compressionContext = new SmartCompressionStrategy.CompressionContext();
            compressionContext.setSessionId(ctx.getSessionId());
            compressionContext.setProgress(ctx.getProgress());
            compressionContext.setNearCompletion(ctx.getProgress() > 0.8);
            compressionContext.setIteration(ctx.getCurrentIteration());
            compressionContext.setMaxIterations(ctx.getMaxIterations());
            
            SmartCompressionStrategy.CompressionDecision decision = 
                smartCompressionStrategy.shouldCompress(ctx.getMessages(), currentTokens, maxTokens, compressionContext);
            
            if (decision.isNeeded()) {
                log.info("智能压缩决策: level={}, reason={}, usage={}%", 
                    decision.getLevel(), decision.getReason(), 
                    String.format("%.1f", decision.getUsageRatio() * 100));
                
                List<Map<String, Object>> originalMessages = new ArrayList<>(ctx.getMessages());
                
                List<Map<String, Object>> compressed = smartCompressionStrategy.executeCompression(
                    ctx.getMessages(), decision, compressionContext);
                
                if (compressed != ctx.getMessages() && compressed.size() < originalMessages.size()) {
                    SmartCompressionStrategy.CompressionSummary summary = 
                        smartCompressionStrategy.getCompressionSummary(originalMessages, compressed);
                    
                    ctx.getMessages().clear();
                    ctx.getMessages().addAll(compressed);
                    
                    log.info("智能压缩完成: {} -> {} messages, {} -> {} tokens, 节省 {}%",
                        summary.getOriginalMessages(), summary.getCompressedMessages(),
                        summary.getOriginalTokens(), summary.getCompressedTokens(),
                        String.format("%.1f", summary.getTokenReductionRatio() * 100));
                } else {
                    log.info("智能压缩未产生有效结果，使用规则压缩");
                    ContextCompressionService.CompressionResult result =
                            compressionService.compressIfNeeded(ctx.getMessages(), ctx.getSessionId(), ctx.getUserId(), ctx.getModel());

                    if (result.compressed) {
                        log.info("规则压缩: level={}, {} -> {} messages",
                                result.level, result.originalCount, result.compressedCount);
                    }
                }
            } else {
                log.debug("智能压缩决策: {}", decision.getReason());
            }
        } catch (Exception e) {
            log.warn("上下文压缩失败: {}", e.getMessage());
        }
    }

    private boolean handleInterrupt(ExecutionContext ctx, Consumer<AIChatResponse> onResponse) {
        if (!sessionManager.shouldInterrupt(ctx.getSessionId())) {
            return false;
        }

        AgentSessionManager.InterruptMode mode = sessionManager.getInterruptMode(ctx.getSessionId());

        if (mode == AgentSessionManager.InterruptMode.CANCEL) {
            log.info("用户取消了任务 (session={})", ctx.getSessionId());
            sendFinalResponse(ctx, "任务已被用户取消。", onResponse);
            return true;
        }

        if (mode == AgentSessionManager.InterruptMode.APPEND) {
            String appendContext = sessionManager.getAppendedContext(ctx.getSessionId());
            log.info("用户追加了指令 (session={}): {}", ctx.getSessionId(), appendContext);
            sessionManager.clearInterrupt(ctx.getSessionId());

            Map<String, Object> appendMsg = new HashMap<>();
            appendMsg.put("role", "user");
            appendMsg.put("content", "[用户追加指令] " + appendContext);
            ctx.getMessages().add(appendMsg);
        }

        return false;
    }

    private ModelResponse callModel(
            ExecutionContext ctx,
            ExecutionConfig config,
            Consumer<AIChatResponse> onResponse) {

        long startTime = System.currentTimeMillis();
        long[] inputTokensArr = new long[1];
        long[] outputTokensArr = new long[1];
        try {
            ModelResponse response = mcpHostService.callLLMWithTools(
                    ctx.getMessages(),
                    ctx.getActualModel(),
                    ctx.getAvailableTools(),
                    ctx.getApiUrl(),
                    ctx.getApiKey(),
                    ctx.getSessionId(),
                    onResponse,
                    inputTokensArr,
                    outputTokensArr
            );
            
            long inputTokens = inputTokensArr[0];
            long outputTokens = outputTokensArr[0];
            if (response != null) {
                long latencyMs = System.currentTimeMillis() - startTime;
                
                double cost = costTrackingService.calculateCost(ctx.getActualModel(), inputTokens, outputTokens);
                

                
                response.setCost(cost);
                response.setLatencyMs(latencyMs);
                
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("latencyMs", latencyMs);
                metadata.put("iteration", ctx.getCurrentIteration());
                costTrackingService.recordApiCall(
                    ctx.getSessionId(),
                    ctx.getUserId(),
                    ctx.getActualModel(),
                    inputTokens,
                    outputTokens,
                    metadata
                );

                // DynamicConfigManager: Token 使用率动态调整（使用累计 Token）
                if (ctx.getSessionConfig() != null) {
                    try {
                        // 累计 Token 使用
                        ctx.setCumulativeInputTokens(ctx.getCumulativeInputTokens() + inputTokens);
                        ctx.setCumulativeOutputTokens(ctx.getCumulativeOutputTokens() + outputTokens);
                        long cumulativeTokens = ctx.getCumulativeInputTokens() + ctx.getCumulativeOutputTokens();

                        // 根据模型获取最大 Token 数（常见模型上下文长度）
                        long maxTokens = getMaxTokensForModel(ctx.getActualModel());

                        // 使用累计 Token 进行调整（超过 50% 时触发）
                        if (cumulativeTokens > maxTokens * 0.5) {
                            com.superfriend.superfriend.agent.config.DynamicConfigManager.SessionConfig updatedConfig =
                                dynamicConfigManager.adjustForTokenUsage(ctx.getSessionId(), cumulativeTokens, maxTokens);
                            ctx.setSessionConfig(updatedConfig);
                            log.info("[DynamicConfig] Token 使用调整: cumulative={}, singleCall={}, max={}, ratio={}",
                                cumulativeTokens, inputTokens + outputTokens, maxTokens,
                                String.format("%.2f", (double) cumulativeTokens / maxTokens));
                        }
                    } catch (Exception e) {
                        log.warn("[DynamicConfig] Token 使用调整失败: {}", e.getMessage());
                    }
                }

                metricsService.recordModelCall(
                    ctx.getMetricsExecutionId(),
                    ctx.getActualModel(),
                    inputTokens,
                    outputTokens,
                    latencyMs
                );
                
                if (ctx.getObservabilityTraceId() != null) {
                    ObservabilityService.LLMEvent llmEvent = new ObservabilityService.LLMEvent();
                    llmEvent.setTraceId(ctx.getObservabilityTraceId());
                    llmEvent.setModel(ctx.getActualModel());
                    llmEvent.setPromptTokens(inputTokens);
                    llmEvent.setCompletionTokens(outputTokens);
                    llmEvent.setCost(cost);
                    llmEvent.setLatencyMs(latencyMs);
                    observabilityService.recordLLMCall(ctx.getObservabilityTraceId(), null, llmEvent);
                }
                
                if (ctx.getTraceId() != null) {
                    String promptSummary = extractPromptSummary(ctx.getMessages());
<<<<<<< HEAD
                    String responseSummary = response.getContent() != null
                            ? (response.getContent().length() > 5000 ? response.getContent().substring(0, 5000) + "..." : response.getContent())
=======
                    String responseSummary = response.getContent() != null 
                            ? (response.getContent().length() > 500 ? response.getContent().substring(0, 500) + "..." : response.getContent())
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
                            : "[工具调用模式]";
                    traceService.addLLMCallEvent(ctx.getTraceId(), promptSummary, responseSummary,
                            latencyMs, true, null);
                }
                
                sendCostUpdate(ctx, inputTokens, outputTokens, cost, onResponse);
                
                log.debug("LLM 调用成本: model={}, tokens={}/{}, cost=${}, latency={}ms",
                    ctx.getActualModel(), inputTokens, outputTokens, String.format("%.6f", cost), latencyMs);
            }
            
            return response;
        } catch (Exception e) {
            log.error("大模型调用失败: {}, 错误类型: {}, 堆栈: {}",
                e.getMessage(),
                e.getClass().getSimpleName(),
                e.getStackTrace()[0]);
            metricsService.recordModelError(ctx.getActualModel());
            
            long latencyMs = System.currentTimeMillis() - startTime;
            if (ctx.getTraceId() != null) {
                String promptSummary = extractPromptSummary(ctx.getMessages());
                traceService.addLLMCallEvent(ctx.getTraceId(), promptSummary, null,
                        latencyMs, false, e.getMessage());
            }

            ErrorType inferredErrorType = inferErrorType(e);
            ctx.setLastErrorType(inferredErrorType);
            ctx.setLastErrorMessage(e.getMessage());
            log.info("[ErrorDetection] 推断错误类型: {} -> {}", e.getClass().getSimpleName(), inferredErrorType);

            AIChatResponse errorResp = new AIChatResponse();
            errorResp.setSessionId(ctx.getSessionId());
            errorResp.setModel(ctx.getActualModel());
            errorResp.setDone(false);
            errorResp.setType("error");
            errorResp.setContent("[系统错误] 大模型调用失败: " + e.getMessage());
            onResponse.accept(errorResp);

            return null;
        }
    }

    private String extractPromptSummary(List<Map<String, Object>> messages) {
        if (messages == null || messages.isEmpty()) {
            return "[空消息列表]";
        }
        
        StringBuilder summary = new StringBuilder();
        int totalChars = 0;
        final int MAX_SUMMARY_LENGTH = 500;
        
        for (Map<String, Object> msg : messages) {
            String role = String.valueOf(msg.get("role"));
            Object contentObj = msg.get("content");
            String content;
            
            if (contentObj instanceof String) {
                content = (String) contentObj;
            } else if (contentObj instanceof List) {
                content = "[多模态内容]";
            } else {
                content = contentObj != null ? String.valueOf(contentObj) : "";
            }
            
            if (totalChars + content.length() > MAX_SUMMARY_LENGTH) {
                int remaining = MAX_SUMMARY_LENGTH - totalChars;
                if (remaining > 0) {
                    summary.append("[").append(role).append("] ")
                           .append(content.substring(0, remaining)).append("...");
                }
                break;
            }
            
            summary.append("[").append(role).append("] ").append(content).append("\n");
            totalChars += content.length();
        }
        
        return summary.toString();
    }

    private void sendCostUpdate(ExecutionContext ctx, long inputTokens, long outputTokens, 
                                 double cost, Consumer<AIChatResponse> onResponse) {
        try {
            CostTrackingService.SessionCost sessionCost = costTrackingService.getSessionCost(ctx.getSessionId());
            
            AIChatResponse costResponse = new AIChatResponse();
            costResponse.setSessionId(ctx.getSessionId());
            costResponse.setModel(ctx.getActualModel());
            costResponse.setType("cost");
            costResponse.setCost(cost);
            costResponse.setInputTokens(inputTokens);
            costResponse.setOutputTokens(outputTokens);
            
            if (sessionCost != null) {
                costResponse.setTotalCost(sessionCost.getTotalCost());
                costResponse.setTotalTokens(sessionCost.getTotalTokens());
            }
            
            onResponse.accept(costResponse);
            log.debug("发送成本更新: sessionId={}, cost=${}, tokens={}/{}", 
                ctx.getSessionId(), String.format("%.6f", cost), inputTokens, outputTokens);
        } catch (Exception e) {
            log.warn("发送成本更新失败: {}", e.getMessage());
        }
    }

    private void handleToolCalls(
            ExecutionContext ctx,
            ModelResponse modelResponse,
            ExecutionConfig config,
            Consumer<AIChatResponse> onResponse) {

        List<ToolCall> toolCalls = modelResponse.getToolCalls();
        log.info("【工具执行】开始处理 {} 个工具调用", toolCalls.size());

        Map<String, Object> assistantMessage = new HashMap<>();
        assistantMessage.put("role", "assistant");
        String content = modelResponse.getContent();
        assistantMessage.put("content", content != null ? content : "");

        List<Map<String, Object>> toolCallsArray = new ArrayList<>();
        for (ToolCall toolCall : toolCalls) {
            try {
                Map<String, Object> toolCallItem = new HashMap<>();
                toolCallItem.put("id", toolCall.getId());
                toolCallItem.put("type", "function");

                Map<String, Object> function = new HashMap<>();
                function.put("name", toolCall.getFullName());
                function.put("arguments", objectMapper.writeValueAsString(toolCall.getArguments()));
                toolCallItem.put("function", function);

                toolCallsArray.add(toolCallItem);
                log.info("【工具执行】准备执行: {}.{}, 参数: {}", 
                    toolCall.getServerName(), toolCall.getToolName(), toolCall.getArguments());
            } catch (Exception e) {
                log.error("【工具执行】序列化工具调用失败: {}", e.getMessage());
            }
        }
        assistantMessage.put("tool_calls", toolCallsArray);
        ctx.getMessages().add(assistantMessage);
        log.info("【工具执行】已将 assistant 消息（含 tool_calls）加入对话历史");

        if (config.isEnableParallelExecution() && toolCalls.size() > 1) {
            log.info("【工具执行】并行执行模式");
            executeToolsInParallel(ctx, toolCalls, config, onResponse);
        } else {
            log.info("【工具执行】顺序执行模式");
            executeToolsSequentially(ctx, toolCalls, config, onResponse);
        }
        
        log.info("【工具执行】所有工具执行完成，结果已加入对话历史");
    }

    private void executeToolsInParallel(
            ExecutionContext ctx,
            List<ToolCall> toolCalls,
            ExecutionConfig config,
            Consumer<AIChatResponse> onResponse) {

        log.info("并行执行 {} 个工具", toolCalls.size());

        List<CompletableFuture<ToolExecutionResult>> futures = new ArrayList<>();
        // 用于追踪是否有工具被强制终止
        final boolean[] hasForceTerminate = {false};

        for (ToolCall toolCall : toolCalls) {
            CompletableFuture<ToolExecutionResult> future = CompletableFuture.supplyAsync(() -> {
                        // 如果已有工具被强制终止，跳过后续工具
                        if (hasForceTerminate[0]) {
                            log.warn("【并行执行】跳过工具 {} (已有工具被强制终止)", toolCall.getToolName());
                            return new ToolExecutionResult(
                                    toolCall.getToolName(),
                                    toolCall.getServerName(),
                                    false,
                                    null,
                                    "【跳过】其他工具触发强制终止",
                                    0
                            );
                        }
                        return executeSingleTool(ctx, toolCall, config, onResponse);
                    }, parallelExecutor)
                    .exceptionally(e -> {
                        // 检查是否是强制终止异常
                        if (e.getCause() != null && e.getCause().getMessage() != null &&
                            e.getCause().getMessage().contains("URL重复访问超过阈值")) {
                            hasForceTerminate[0] = true;
                            log.warn("【并行执行】工具 {} 触发强制终止", toolCall.getToolName());
                            return new ToolExecutionResult(
                                    toolCall.getToolName(),
                                    toolCall.getServerName(),
                                    false,
                                    null,
                                    "【系统阻止】" + e.getCause().getMessage(),
                                    0
                            );
                        }
                        log.error("工具执行异常: {} - {}", toolCall.getToolName(), e.getMessage());
                        return new ToolExecutionResult(
                                toolCall.getToolName(),
                                toolCall.getServerName(),
                                false,
                                null,
                                "执行异常: " + e.getMessage(),
                                config.getToolTimeoutMs()
                        );
                    });
            futures.add(future);
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        for (int i = 0; i < futures.size(); i++) {
            try {
                ToolExecutionResult result = futures.get(i).get();
                addToolResultToMessages(ctx, toolCalls.get(i), result);
            } catch (Exception e) {
                log.error("获取工具执行结果失败: {}", e.getMessage());
            }
        }
    }

    private void executeToolsSequentially(
            ExecutionContext ctx,
            List<ToolCall> toolCalls,
            ExecutionConfig config,
            Consumer<AIChatResponse> onResponse) {

        log.info("【工具执行】顺序执行 {} 个工具", toolCalls.size());
        for (ToolCall toolCall : toolCalls) {
            log.info("【工具执行】开始执行工具: {}.{}", toolCall.getServerName(), toolCall.getToolName());
            try {
                ToolExecutionResult result = executeSingleTool(ctx, toolCall, config, onResponse);
                log.info("【工具执行】工具执行完成: {}.{}, 成功: {}",
                    toolCall.getServerName(), toolCall.getToolName(), result.isSuccess());
                addToolResultToMessages(ctx, toolCall, result);
            } catch (RuntimeException e) {
                // 处理强制终止异常
                if (e.getMessage() != null && e.getMessage().contains("URL重复访问超过阈值")) {
                    log.warn("【工具执行】工具被强制终止: {}", e.getMessage());
                    // 添加一个模拟的失败结果到对话历史
                    ToolExecutionResult blockedResult = new ToolExecutionResult(
                            toolCall.getToolName(),
                            toolCall.getServerName(),
                            false,
                            null,
                            "【系统阻止】" + e.getMessage(),
                            0
                    );
                    addToolResultToMessages(ctx, toolCall, blockedResult);
                } else {
                    // 其他异常：添加一个失败结果到对话历史，确保消息配对完整
                    log.error("【工具执行】工具执行异常: {}.{}, error={}",
                        toolCall.getServerName(), toolCall.getToolName(), e.getMessage());
                    ToolExecutionResult errorResult = new ToolExecutionResult(
                            toolCall.getToolName(),
                            toolCall.getServerName(),
                            false,
                            null,
                            "工具执行异常: " + e.getMessage(),
                            0
                    );
                    addToolResultToMessages(ctx, toolCall, errorResult);
                    // 不再抛出异常，继续执行后续工具或让 ReAct 循环处理
                }
            }
        }
    }

    private ToolExecutionResult executeSingleTool(
            ExecutionContext ctx,
            ToolCall toolCall,
            ExecutionConfig config,
            Consumer<AIChatResponse> onResponse) {

        long startTime = System.currentTimeMillis();
        String toolName = toolCall.getToolName();
        String serverName = toolCall.getServerName();

        log.info("执行工具：{}.{}", serverName, toolName);

        checkRepeatCall(ctx, toolCall, config);

        try {
            Map<String, Object> toolArgs = toolCall.getArguments() != null ? new HashMap<>(toolCall.getArguments()) : new HashMap<>();

<<<<<<< HEAD
            // 【新增】为沙箱工具自动注入已有的 sessionId
            injectSandboxSessionId(ctx, serverName, toolName, toolArgs);

            // 【新增】沙箱命令验证和自动包装
            if ("bash-sandbox".equals(serverName) && "execute".equals(toolName)) {
                String command = toolArgs.containsKey("command") ? String.valueOf(toolArgs.get("command")) : null;
                if (command != null) {
                    // 验证命令安全性
                    SandboxExecutionGuideService.ValidationResult validation =
                        sandboxExecutionGuideService.validateCommand(command);
                    if (!validation.isValid()) {
                        log.warn("【沙箱安全】命令被阻止: {} - {}", command, validation.getReason());
                        return new ToolExecutionResult(toolName, serverName, false, null,
                            "命令被安全策略阻止: " + validation.getReason() + "\n建议: " + validation.getSuggestion(),
                            System.currentTimeMillis() - startTime);
                    }

                    // 自动包装命令
                    String wrappedCommand = sandboxExecutionGuideService.wrapCommand(command, null);
                    if (!wrappedCommand.equals(command)) {
                        log.info("【沙箱引导】命令已自动包装: {} -> {}", command, wrappedCommand);
                        toolArgs.put("command", wrappedCommand);
                    }

                    // 如果有警告，记录日志
                    if (!validation.getWarnings().isEmpty()) {
                        log.info("【沙箱引导】命令建议: {}", String.join("; ", validation.getWarnings()));
                    }
                }
            }

=======
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
            // 为 run_skill_script 工具自动添加 session_id
            if ("run_skill_script".equals(toolName) && !toolArgs.containsKey("session_id")) {
                toolArgs.put("session_id", ctx.getSessionId());
                log.info("【工具执行】自动添加 session_id 到 run_skill_script 参数: {}", ctx.getSessionId());
            }

<<<<<<< HEAD
            // 为文件工具自动添加 session_id
            if (("list_files".equals(toolName) || "read_file".equals(toolName) ||
                 "search_file".equals(toolName) || "get_file_info".equals(toolName) ||
                 "get_file_path".equals(toolName))
                && !toolArgs.containsKey("session_id")) {
                toolArgs.put("session_id", ctx.getSessionId());
                log.info("【工具执行】自动添加 session_id 到 {} 参数: {}", toolName, ctx.getSessionId());
            }

=======
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
            // URL缓存检查 - 如果该URL已被访问过，直接返回缓存结果
            if (toolArgs.containsKey("url")) {
                String url = String.valueOf(toolArgs.get("url"));
                ToolExecutionResult cachedResult = ctx.getCachedUrlResult(url);
                if (cachedResult != null) {
                    log.info("【缓存命中】使用缓存的URL访问结果: {}, 跳过实际工具调用", url);
                    // 标记结果来自缓存（用于日志区分）
                    return new ToolExecutionResult(
                            toolName + "_cached",
                            serverName,
                            cachedResult.isSuccess(),
                            "[来自缓存] " + cachedResult.getResult(),
                            cachedResult.getError(),
                            0  // 缓存命中耗时为0
                    );
                }
            }

            PermissionCheckResult permResult = permissionService.checkPermission(ctx.getUserId(), toolName, toolArgs);

            if (permResult.isDenied()) {
                return new ToolExecutionResult(toolName, serverName, false, null,
                        "Permission denied: " + permResult.getReason(), System.currentTimeMillis() - startTime);
            }

            if (permResult.isRequiresApproval()) {
                boolean approved = waitForApproval(ctx.getSessionId(), ctx.getUserId(), toolCall, toolArgs);
                if (!approved) {
                    return new ToolExecutionResult(toolName, serverName, false, null,
                            "User denied approval", System.currentTimeMillis() - startTime);
                }
            }

            McpToolCallResponse toolResult = mcpHostService.callTool(serverName, toolName, toolArgs, onResponse);

            ValidationResult validation = validateToolResult(toolName, toolResult, ctx.getUserMessage());

            if (!validation.isValid() && validation.isShouldRetry()) {
                log.info("工具结果验证失败，尝试重试: {} - {}", toolName, validation.getReason());

                for (int retry = 0; retry < 2; retry++) {
                    sleep(1000 * (retry + 1));

                    toolResult = mcpHostService.callTool(serverName, toolName, toolArgs, onResponse);
                    validation = validateToolResult(toolName, toolResult, ctx.getUserMessage());

                    if (validation.isValid()) {
                        log.info("工具重试成功: {} (第{}次)", toolName, retry + 1);
                        break;
                    }
                }
            }

            if (!toolResult.isSuccess() || !validation.isValid()) {
                FallbackStrategyService.FallbackContext fallbackContext = new FallbackStrategyService.FallbackContext();
                fallbackContext.setOriginalToolName(toolName);
                fallbackContext.setOriginalServerName(serverName);
                fallbackContext.setOriginalParameters(toolArgs);
                fallbackContext.setFailureReason(toolResult.getError() != null ? toolResult.getError() : validation.getReason());
                fallbackContext.setUserRequest(ctx.getUserMessage());
                fallbackContext.setAvailableTools(ctx.getAvailableTools());
                fallbackContext.setAttemptCount(ctx.getCurrentIteration());

                FallbackStrategyService.FallbackResult fallbackResult = fallbackStrategyService.findFallback(fallbackContext);

                if (fallbackResult.isHasAlternative()) {
                    log.info("尝试回退策略: {} -> {} ({})", 
                            toolName, fallbackResult.getAlternativeToolName(), fallbackResult.getStrategy());

                    if (ctx.getTraceId() != null) {
                        traceService.addFallbackEvent(ctx.getTraceId(), toolName, 
                                fallbackResult.getAlternativeToolName(), fallbackResult.getStrategy());
                    }

                    updateProgress(ctx, "回退策略", 
                            "尝试替代工具: " + fallbackResult.getAlternativeToolName(), 
                            ctx.getProgress() + 0.05, onResponse);

                    McpToolCallResponse fallbackToolResult = mcpHostService.callTool(
                            fallbackResult.getAlternativeServerName(),
                            fallbackResult.getAlternativeToolName(),
                            fallbackResult.getAlternativeParameters(),
                            onResponse);

                    if (fallbackToolResult.isSuccess()) {
                        fallbackStrategyService.recordSuccess(
                                fallbackResult.getAlternativeToolName(), 
                                fallbackResult.getAlternativeServerName());

                        String compressedFallbackResult = mcpHostService.compressToolResult(fallbackToolResult);
                        long executionTime = System.currentTimeMillis() - startTime;

                        log.info("回退策略成功: {} -> {} ({}ms)", 
                                toolName, fallbackResult.getAlternativeToolName(), executionTime);

                        return new ToolExecutionResult(
                                fallbackResult.getAlternativeToolName(),
                                fallbackResult.getAlternativeServerName(),
                                true,
                                "[使用替代工具: " + fallbackResult.getStrategy() + "]\n" + compressedFallbackResult,
                                null,
                                executionTime);
                    } else {
                        log.warn("回退策略失败: {} -> {}", 
                                toolName, fallbackResult.getAlternativeToolName());
                    }
                }
            }

            String compressedResult = mcpHostService.compressToolResult(toolResult);

            if (!validation.isValid()) {
                compressedResult = "[验证警告: " + validation.getReason() + "]\n" + compressedResult;
                log.warn("工具结果验证失败: {} - {}", toolName, validation.getReason());
            }

            if (toolResult.isSuccess()) {
                fallbackStrategyService.recordSuccess(toolName, serverName);

<<<<<<< HEAD
                // 【新增】自动提取和保存沙箱会话信息
                if ("bash-sandbox".equals(serverName)) {
                    extractAndSaveSandboxSession(ctx, toolResult, toolName);
                }

=======
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
                // 检测文件导出操作并发送给前端
                checkAndSendExportedFile(toolResult, toolName, serverName, ctx, onResponse);
            }

            long executionTime = System.currentTimeMillis() - startTime;
            log.info("工具调用完成：{}.{} - {} ({}ms)", serverName, toolName,
                    toolResult.isSuccess() ? "成功" : "失败", executionTime);

            metricsService.recordToolCall(ctx.getMetricsExecutionId(), toolName, serverName,
                    executionTime, toolResult.isSuccess(), false);

            costTrackingService.recordToolCall(ctx.getSessionId(), toolName, serverName);

            if (ctx.getObservabilityTraceId() != null) {
                ObservabilityService.ToolEvent toolEvent = new ObservabilityService.ToolEvent();
                toolEvent.setTraceId(ctx.getObservabilityTraceId());
                toolEvent.setToolName(toolName);
                toolEvent.setServerName(serverName);
                toolEvent.setArguments(toolArgs);
                toolEvent.setResult(compressedResult);
                toolEvent.setSuccess(toolResult.isSuccess());
                toolEvent.setLatencyMs(executionTime);
                toolEvent.setErrorMessage(toolResult.getError());
                observabilityService.recordToolCall(ctx.getObservabilityTraceId(), null, toolEvent);
            }

            if (ctx.getTraceId() != null) {
                traceService.addToolCallEvent(ctx.getTraceId(), toolName, serverName,
                        toolArgs, compressedResult, executionTime, toolResult.isSuccess(),
                        toolResult.getError());
                semanticToolSelectionService.recordToolCall(toolName, serverName,
                        toolResult.isSuccess(), executionTime);
            }

            ToolExecutionResult result = new ToolExecutionResult(toolName, serverName, toolResult.isSuccess(),
                    compressedResult, null, executionTime);

            // 缓存URL访问结果
            if (toolArgs.containsKey("url") && toolResult.isSuccess()) {
                String url = String.valueOf(toolArgs.get("url"));
                ctx.cacheUrlResult(url, result);
                log.info("已缓存URL访问结果: {}", url);
            }

            return result;

        } catch (Exception e) {
            log.error("工具调用异常：{}.{} - {}", serverName, toolName, e.getMessage());
            long executionTime = System.currentTimeMillis() - startTime;

            metricsService.recordToolCall(ctx.getMetricsExecutionId(), toolName, serverName,
                    executionTime, false, false);
            
            if (ctx.getTraceId() != null) {
                traceService.addToolCallEvent(ctx.getTraceId(), toolName, serverName, 
                        null, null, executionTime, false, e.getMessage());
                semanticToolSelectionService.recordToolCall(toolName, serverName, false, executionTime);
                semanticToolSelectionService.recordToolError(toolName, serverName, e.getMessage());
            }
            
            return new ToolExecutionResult(toolName, serverName, false, null,
                    "工具调用异常: " + e.getMessage(), executionTime);
        }
    }

<<<<<<< HEAD
    /**
     * 【新增】从沙箱工具结果中提取并保存会话信息
     */
    private void extractAndSaveSandboxSession(ExecutionContext ctx, McpToolCallResponse toolResult, String toolName) {
        try {
            String resultText = extractResultText(toolResult);
            if (resultText == null) {
                return;
            }

            // 提取 sessionId
            String sessionIdPattern = "sessionId: `([^`]+)`";
            java.util.regex.Pattern sessionPattern = java.util.regex.Pattern.compile(sessionIdPattern);
            java.util.regex.Matcher sessionMatcher = sessionPattern.matcher(resultText);
            if (sessionMatcher.find()) {
                String sandboxSessionId = sessionMatcher.group(1);
                if (sandboxSessionId != null && !sandboxSessionId.isEmpty()) {
                    ctx.setBashSandboxSessionId(sandboxSessionId);
                    log.info("【沙箱会话】自动保存 sessionId: {}", sandboxSessionId);
                }
            }

            // 提取 workingDirectory
            String workingDirPattern = "workingDirectory: `([^`]+)`";
            java.util.regex.Pattern dirPattern = java.util.regex.Pattern.compile(workingDirPattern);
            java.util.regex.Matcher dirMatcher = dirPattern.matcher(resultText);
            if (dirMatcher.find()) {
                String workingDir = dirMatcher.group(1);
                if (workingDir != null && !workingDir.isEmpty()) {
                    ctx.setBashSandboxWorkingDir(workingDir);
                    log.info("【沙箱会话】自动保存 workingDirectory: {}", workingDir);
                }
            }

            // 如果是 execute 工具且没有 sessionId，尝试从参数中获取
            if ("execute".equals(toolName) && ctx.getBashSandboxSessionId() == null) {
                // 检查是否已有会话（从返回结果中判断）
                if (resultText.contains("(后续命令可复用此会话)")) {
                    log.info("【沙箱会话】检测到新创建的会话，已保存");
                }
            }

        } catch (Exception e) {
            log.warn("【沙箱会话】提取会话信息失败: {}", e.getMessage());
        }
    }

    /**
     * 【新增】为沙箱工具自动注入已有的 sessionId
     */
    private void injectSandboxSessionId(ExecutionContext ctx, String serverName, String toolName, Map<String, Object> toolArgs) {
        if (!"bash-sandbox".equals(serverName)) {
            return;
        }

        String existingSessionId = ctx.getBashSandboxSessionId();
        if (existingSessionId != null && !existingSessionId.isEmpty()) {
            // 对于需要 sessionId 的工具，自动注入
            if ("execute".equals(toolName) || "export_file".equals(toolName) ||
                "write_file".equals(toolName) || "read_file".equals(toolName)) {
                if (!toolArgs.containsKey("sessionId")) {
                    toolArgs.put("sessionId", existingSessionId);
                    log.info("【沙箱会话】自动注入已有 sessionId: {}", existingSessionId);
                }
            }
        }
    }

=======
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
    private ValidationResult validateToolResult(String toolName, McpToolCallResponse result, String userRequest) {
        if (!result.isSuccess()) {
            return ValidationResult.failure(result.getError(), true);
        }

        if (result.getContent() == null || result.getContent().isEmpty()) {
            return ValidationResult.failure("工具返回空结果", true);
        }

        String resultText = extractResultText(result);

        if (resultText == null || resultText.trim().isEmpty()) {
            return ValidationResult.failure("工具返回空文本", true);
        }

        if (resultText.contains("error") || resultText.contains("Error") || resultText.contains("ERROR")) {
            if (resultText.contains("not found") || resultText.contains("不存在") || resultText.contains("未找到")) {
                return ValidationResult.warning("结果包含未找到信息: " + resultText.substring(0, Math.min(100, resultText.length())));
            }
        }

        if (toolName.toLowerCase().contains("search") || toolName.toLowerCase().contains("find")) {
            if (resultText.length() < 10) {
                return ValidationResult.warning("搜索结果过短，可能未找到相关内容");
            }
        }

        if (toolName.toLowerCase().contains("read") || toolName.toLowerCase().contains("get")) {
            if (resultText.contains("permission denied") || resultText.contains("访问被拒绝")) {
                return ValidationResult.failure("权限不足，无法访问资源", false);
            }
        }

        return ValidationResult.success();
    }

    private String extractResultText(McpToolCallResponse result) {
        if (result.getContent() == null) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        for (McpToolCallResponse.ContentItem item : result.getContent()) {
            if ("text".equals(item.getType()) && item.getText() != null) {
                sb.append(item.getText());
            }
        }
        return sb.toString();
    }

    @Data
    public static class ValidationResult {
        private final boolean valid;
        private final String reason;
        private final boolean shouldRetry;

        public static ValidationResult success() {
            return new ValidationResult(true, null, false);
        }

        public static ValidationResult failure(String reason, boolean shouldRetry) {
            return new ValidationResult(false, reason, shouldRetry);
        }

        public static ValidationResult warning(String reason) {
            return new ValidationResult(true, reason, false);
        }
    }

    private void checkRepeatCall(ExecutionContext ctx, ToolCall toolCall, ExecutionConfig config) {
        String callSignature = toolCall.getToolName() + ":" + toolCall.getArguments();
        ctx.getRecentToolCallSignatures().add(callSignature);

        if (ctx.getRecentToolCallSignatures().size() > 10) {
            ctx.getRecentToolCallSignatures().remove(0);
        }

        int repeatCount = 0;
        for (String sig : ctx.getRecentToolCallSignatures()) {
            if (sig.equals(callSignature)) repeatCount++;
        }

<<<<<<< HEAD
        // 简化逻辑：只在重复调用次数非常高时（10次以上）才给出警告
        // 不再强制中断，让模型自己决定下一步
        // maxIterations 和 maxConsecutiveErrors 已经足够防止无限循环
        if (repeatCount >= 10) {
            log.warn("检测到大量重复工具调用 {} ({}次)，但不强制中断", callSignature, repeatCount);
=======
        if (repeatCount >= config.getRepeatCallThreshold()) {
            log.warn("检测到重复工具调用 {} ({}次)", callSignature, repeatCount);
            Map<String, Object> warnMsg = new HashMap<>();
            warnMsg.put("role", "user");
            warnMsg.put("content", "你已经连续 " + repeatCount + " 次调用相同的工具。请停止重复调用，直接给出答案。");
            ctx.getMessages().add(warnMsg);
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
        }

        // 跨工具URL重复检测
        Map<String, Object> args = toolCall.getArguments();
        if (args != null && args.containsKey("url")) {
            String url = String.valueOf(args.get("url"));
            if (ctx.hasVisitedUrl(url)) {
                log.warn("检测到跨工具重复访问URL: {} (已通过其他工具访问过)", url);
                String previousResult = ctx.getUrlAccessResult(url);
                int visitCount = ctx.getUrlVisitCount(url);

                // 如果同一URL被访问超过3次，强制要求直接回答
                if (visitCount >= 3) {
                    log.warn("URL {} 已被访问 {} 次，强制要求直接回答", url, visitCount);
                    Map<String, Object> forceAnswerMsg = new HashMap<>();
                    forceAnswerMsg.put("role", "user");
                    forceAnswerMsg.put("content", "【系统强制终止】该URL [" + url + "] 已被访问 " + visitCount + " 次。\n" +
                            "你必须立即停止重复访问，基于已有信息直接给出最终答案。\n" +
                            "之前的访问结果: " + (previousResult != null ? previousResult : "无") + "\n" +
                            "请现在直接回答用户的问题，不要再调用任何工具。");
                    ctx.getMessages().add(forceAnswerMsg);
                    // 设置标志，表示需要强制结束
                    ctx.addEvidence("force_terminate", true);
                    // 重要：抛出异常阻止工具执行
                    throw new RuntimeException("URL重复访问超过阈值，强制终止工具执行: " + url);
                } else {
                    Map<String, Object> warnMsg = new HashMap<>();
                    warnMsg.put("role", "user");
                    warnMsg.put("content", "该URL [" + url + "] 已经访问过。\n" +
                            "之前的访问结果摘要: " + (previousResult != null ? previousResult : "无") + "\n" +
                            "请勿重复访问相同URL，直接利用已有信息回答，或访问其他未探索的URL。");
                    ctx.getMessages().add(warnMsg);
                }
            }
        }
    }

    private boolean waitForApproval(String sessionId, Long userId, ToolCall toolCall, Map<String, Object> toolArgs) {
        try {
            ApprovalRequestDTO approvalRequest = approvalService.submitApproval(
                    sessionId, userId, toolCall.getToolName(),
                    toolCall.getServerName(), toolArgs,
                    permissionService.checkPermission(userId, toolCall.getToolName(), toolArgs).getPermissionLevel()
            );

            ApprovalService.ApprovalResult approvalResult = approvalService.waitForApproval(
                    approvalRequest.getRequestId(), 35000);

            return approvalResult.isApproved();
        } catch (Exception e) {
            log.error("审批流程异常: {}", e.getMessage());
            return false;
        }
    }

    private void addToolResultToMessages(ExecutionContext ctx, ToolCall toolCall, ToolExecutionResult result) {
        Map<String, Object> toolMessage = new HashMap<>();
        toolMessage.put("role", "tool");
        toolMessage.put("tool_call_id", toolCall.getId());

        // 确保 content 不为 null，API 要求 tool 消息必须有 content
        String content = result.isSuccess() ? result.getResult() : result.getError();
        if (content == null || content.isEmpty()) {
            content = result.isSuccess() ? "工具执行成功，无返回内容" : "工具执行失败，无错误信息";
        }
        toolMessage.put("content", content);

        ctx.getMessages().add(toolMessage);
        ctx.getToolResults().put(toolCall.getFullName(), result);

        // Record action and observation in ReasoningChain
        ctx.recordAction(toolCall.getFullName(), toolCall.getArguments(), content, result.isSuccess());

        // Add evidence to context
        String evidenceKey = "tool_" + toolCall.getFullName() + "_" + ctx.getCurrentIteration();
        ctx.addEvidence(evidenceKey, content);

        // 记录URL访问历史（跨工具共享）
        Map<String, Object> args = toolCall.getArguments();
        if (args != null && args.containsKey("url")) {
            String url = String.valueOf(args.get("url"));
            String resultSummary = content.length() > 200 ? content.substring(0, 200) + "..." : content;
            ctx.recordUrlAccess(url, resultSummary);
        }

        // Add reflection based on result quality
        double confidence = evaluateToolResultQuality(content, result.isSuccess());
        ctx.addReflection("工具 " + toolCall.getFullName() + " 执行" +
            (result.isSuccess() ? "成功" : "失败") + "，置信度: " + String.format("%.2f", confidence),
            confidence);

        log.info("【工具执行】已将工具结果加入对话历史: tool={}, success={}, resultLength={}",
            toolCall.getFullName(),
            result.isSuccess(),
            content.length());
    }

    /**
     * 评估工具执行结果的质量
     */
    private double evaluateToolResultQuality(String content, boolean success) {
        if (!success) return 0.2;

        double score = 0.5; // 基础分数

        if (content == null || content.isEmpty()) return 0.3;

        // 结果长度适中
        if (content.length() > 50 && content.length() < 2000) {
            score += 0.2;
        }

        // 包含有价值的信息
        String lower = content.toLowerCase();
        if (lower.contains("data") || lower.contains("result") ||
            lower.contains("结果") || lower.contains("信息")) {
            score += 0.15;
        }

        // 包含结构化数据
        if (content.contains("[") || content.contains("{")) {
            score += 0.15;
        }

        return Math.min(score, 1.0);
    }

    /**
<<<<<<< HEAD
     * 检查内容是否是实质性的任务完成
     * 【新增】防止模型敷衍回答
     *
     * @param ctx 执行上下文
     * @param content 模型返回的内容
     * @return 是否是实质性的完成
     */
    private boolean isSubstantiveCompletion(ExecutionContext ctx, String content) {
        if (content == null || content.trim().isEmpty()) {
            log.debug("实质性检查: 内容为空");
            return false;
        }

        String trimmed = content.trim();

        // 1. 检查内容长度（太短可能是敷衍）
        if (trimmed.length() < 30) {
            log.debug("实质性检查: 内容过短 ({} 字符)", trimmed.length());
            return false;
        }

        // 2. 检查是否包含敷衍回答模式
        String[] perfunctoryPatterns = {
            "好的，我来帮你", "好的，让我", "我来帮你", "让我来帮你",
            "我将", "我会帮你", "首先，我", "好的，我"
        };

        for (String pattern : perfunctoryPatterns) {
            if (trimmed.startsWith(pattern) && trimmed.length() < 150) {
                log.debug("实质性检查: 检测到敷衍回答模式 '{}'", pattern);
                return false;
            }
        }

        // 3. 检查是否有工具调用历史但没有实际结果
        int toolCallCount = ctx.getToolResults().size();
        if (toolCallCount == 0) {
            // 没有工具调用，检查内容是否足够详细
            if (trimmed.length() < 100) {
                log.debug("实质性检查: 无工具调用但内容较短 ({} 字符)", trimmed.length());
                // 不直接返回 false，因为有些简单问题不需要工具
            }
        } else {
            // 有工具调用，检查是否引用了工具结果
            boolean referencesToolResults = false;
            String lowerContent = trimmed.toLowerCase();

            // 检查是否引用了工具返回的数据
            String[] dataKeywords = {"结果", "数据", "信息", "找到", "发现", "获取", "显示", "表明",
                "result", "data", "found", "shows", "indicates"};
            for (String keyword : dataKeywords) {
                if (lowerContent.contains(keyword)) {
                    referencesToolResults = true;
                    break;
                }
            }

            if (!referencesToolResults && trimmed.length() < 200) {
                log.debug("实质性检查: 有工具调用但未引用结果且内容较短");
                return false;
            }
        }

        // 4. 检查是否以不完整的句子结尾
        if (trimmed.endsWith("，") || trimmed.endsWith(",") ||
            trimmed.endsWith("、") || trimmed.endsWith(":") || trimmed.endsWith("：")) {
            log.debug("实质性检查: 内容以不完整的句子结尾");
            return false;
        }

        // 5. 检查是否有"待续"、"未完"等标记
        if (trimmed.contains("待续") || trimmed.contains("未完") ||
            trimmed.contains("to be continued")) {
            log.debug("实质性检查: 内容包含未完成标记");
            return false;
        }

        // 6. 检查证据存储是否有足够的内容
        int evidenceSize = ctx.getEvidenceStore().size();
        if (toolCallCount > 2 && evidenceSize == 0) {
            log.debug("实质性检查: 有工具调用但无证据存储");
            // 这可能表示工具调用没有产生有效结果
        }

        log.debug("实质性检查: 通过 (长度: {}, 工具调用: {}, 证据: {})",
            trimmed.length(), toolCallCount, evidenceSize);
        return true;
    }

    /**
=======
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
     * 判断是否需要执行深度 Reflection
     * 优化策略：避免对简单任务执行不必要的深度反思
     *
     * @param ctx 执行上下文
     * @return 是否需要执行深度反思
     */
    private boolean shouldPerformDeepReflection(ExecutionContext ctx) {
        // 条件 1: 工具调用次数 > 2（表示任务有一定复杂度）
        int toolCallCount = ctx.getToolResults().size();
        if (toolCallCount > 2) {
            log.debug("[Reflection] 工具调用次数 {} > 2，执行深度反思", toolCallCount);
            return true;
        }

        // 条件 2: 迭代次数 >= 3（表示任务执行了多轮）
        if (ctx.getCurrentIteration() >= 3) {
            log.debug("[Reflection] 迭代次数 {} >= 3，执行深度反思", ctx.getCurrentIteration());
            return true;
        }

        // 条件 3: 推理链步骤数 > 5（表示有较多思考过程）
        if (ctx.getReasoningChain().getSteps().size() > 5) {
            log.debug("[Reflection] 推理链步骤数 {} > 5，执行深度反思",
                ctx.getReasoningChain().getSteps().size());
            return true;
        }

        // 条件 4: 有失败的工具调用（需要验证结果是否正确）
        boolean hasFailedTools = ctx.getToolResults().values().stream()
            .anyMatch(result -> !result.isSuccess());
        if (hasFailedTools) {
            log.debug("[Reflection] 存在失败的工具调用，执行深度反思");
            return true;
        }

        // 简单任务，跳过深度反思
        log.debug("[Reflection] 简单任务，跳过深度反思 (工具调用: {}, 迭代: {}, 推理步骤: {})",
            toolCallCount, ctx.getCurrentIteration(), ctx.getReasoningChain().getSteps().size());
        return false;
    }

    private ReflectionResult performReflection(ExecutionContext ctx, String response) {
        ReflectionResult result = new ReflectionResult();
        result.setConfidenceScore(1.0);
        result.setNeedsCorrection(false);
        result.setMissingInfo(new ArrayList<>());

        if (response == null || response.trim().isEmpty()) {
            result.setNeedsCorrection(true);
            result.setCorrectionHint("响应为空，请提供有意义的回答");
            result.setConfidenceScore(0.0);
            return result;
        }

        if (response.contains("我无法") || response.contains("我不能") || response.contains("无法完成")) {
            result.setNeedsCorrection(true);
            result.setCorrectionHint("检测到失败声明，请尝试其他方法或工具");
            result.setConfidenceScore(0.3);
            return result;
        }

        if (ctx.getToolResults().isEmpty() && response.length() < 50) {
            result.setNeedsCorrection(true);
            result.setCorrectionHint("回答过于简短，请提供更详细的信息");
            result.setConfidenceScore(0.5);
            return result;
        }

        if (ctx.getCurrentIteration() >= 3 || (ctx.getToolResults().size() > 0 && response.length() > 100)) {
            try {
                ReflectionService.ReflectionContext reflectionContext = new ReflectionService.ReflectionContext();
                reflectionContext.setUserRequest(ctx.getUserMessage());
                reflectionContext.setAssistantResponse(response);
                reflectionContext.setIterationCount(ctx.getCurrentIteration());

                List<ReflectionService.ToolExecutionRecord> toolRecords = new ArrayList<>();
                for (Map.Entry<String, ToolExecutionResult> entry : ctx.getToolResults().entrySet()) {
                    ReflectionService.ToolExecutionRecord record = new ReflectionService.ToolExecutionRecord();
                    record.setToolName(entry.getKey());
                    record.setSuccess(entry.getValue().isSuccess());
                    record.setResultSummary(entry.getValue().getResult());
                    record.setExecutionTimeMs(entry.getValue().getExecutionTimeMs());
                    toolRecords.add(record);
                }
                reflectionContext.setToolExecutions(toolRecords);

                ReflectionService.ReflectionResult deepResult = 
                        reflectionService.reflect(reflectionContext, ctx.getActualModel(), ctx.getUserId());

                if (deepResult != null) {
                    result.setConfidenceScore(deepResult.getConfidenceScore());
                    result.setNeedsCorrection(deepResult.isNeedsCorrection() || 
                            deepResult.getAction() == ReflectionService.ReflectionResult.ReflectionAction.CORRECT);
                    result.setMissingInfo(deepResult.getMissingInfo());

                    if (deepResult.isNeedsCorrection() && deepResult.getCorrectionHint() != null) {
                        result.setCorrectionHint(deepResult.getCorrectionHint());
                    } else if (deepResult.getSuggestedActions() != null && !deepResult.getSuggestedActions().isEmpty()) {
                        result.setCorrectionHint(String.join("; ", deepResult.getSuggestedActions()));
                    }

                    if (deepResult.getAction() == ReflectionService.ReflectionResult.ReflectionAction.REQUEST_INFO 
                            && deepResult.getMissingInfo() != null && !deepResult.getMissingInfo().isEmpty()) {
                        result.setNeedsCorrection(true);
                        result.setCorrectionHint("需要更多信息: " + String.join(", ", deepResult.getMissingInfo()));
                    }

                    if (ctx.getTraceId() != null) {
                        traceService.addReflectionEvent(ctx.getTraceId(), 
                                deepResult.getReasoning() != null ? deepResult.getReasoning() : result.getCorrectionHint(),
                                deepResult.isNeedsCorrection(),
                                deepResult.getConfidenceScore());
                    }

                    log.info("深度 Reflection 结果: confidence={}, needsCorrection={}, action={}",
                            deepResult.getConfidenceScore(), deepResult.isNeedsCorrection(), deepResult.getAction());
                }

            } catch (Exception e) {
                log.warn("深度 Reflection 失败，使用简单规则: {}", e.getMessage());
            }
        }

        return result;
    }

    private void updateProgress(
            ExecutionContext ctx,
            String phase,
            String description,
            double progress,
            Consumer<AIChatResponse> onResponse) {

        ctx.setCurrentPhase(phase);
        ctx.setProgress(progress);

        if (progress > 0) {
            AIChatResponse progressResp = new AIChatResponse();
            progressResp.setSessionId(ctx.getSessionId());
            progressResp.setModel(ctx.getActualModel());
            progressResp.setDone(false);
            progressResp.setType("progress");

            Map<String, Object> progressData = new HashMap<>();
            progressData.put("progress", progress);
            progressData.put("phase", phase);
            progressData.put("description", description);
            progressResp.setToolCalls(progressData);

            onResponse.accept(progressResp);
        }
    }

    private void sendFinalResponse(ExecutionContext ctx, String content, String reasoningContent, String thought, Consumer<AIChatResponse> onResponse) {
        log.info("执行完成，共 {} 轮迭代", ctx.getCurrentIteration() + 1);

        updateProgress(ctx, "完成", "任务执行完成", 1.0, onResponse);

        AIChatResponse finalResponse = new AIChatResponse();
        finalResponse.setContent(content);
        finalResponse.setReasoningContent(reasoningContent);
        finalResponse.setThought(thought);
        finalResponse.setSessionId(ctx.getSessionId());
        finalResponse.setModel(ctx.getActualModel());
        finalResponse.setDone(true);
        finalResponse.setType("result");
        onResponse.accept(finalResponse);

        ctx.setExecutionEnded(true);
    }
    
    private void sendFinalResponse(ExecutionContext ctx, String content, String reasoningContent, Consumer<AIChatResponse> onResponse) {
        sendFinalResponse(ctx, content, reasoningContent, null, onResponse);
    }
    
    private void sendFinalResponse(ExecutionContext ctx, String content, Consumer<AIChatResponse> onResponse) {
        sendFinalResponse(ctx, content, null, null, onResponse);
    }
    
    private void sendDoneResponse(ExecutionContext ctx, Consumer<AIChatResponse> onResponse) {
        AIChatResponse doneResponse = new AIChatResponse();
        doneResponse.setSessionId(ctx.getSessionId());
        doneResponse.setModel(ctx.getActualModel());
        doneResponse.setDone(true);
        doneResponse.setType("result");
        onResponse.accept(doneResponse);
        ctx.setExecutionEnded(true);
    }
    
    private void sendContentAndDone(ExecutionContext ctx, String content, Consumer<AIChatResponse> onResponse) {
        if (content != null && !content.isEmpty()) {
            AIChatResponse contentResp = new AIChatResponse();
            contentResp.setSessionId(ctx.getSessionId());
            contentResp.setModel(ctx.getActualModel());
            contentResp.setDone(false);
            contentResp.setType("result");
            contentResp.setContent(content);
            onResponse.accept(contentResp);
        }
        sendDoneResponse(ctx, onResponse);
    }
    
    private void sendThoughtAndDone(ExecutionContext ctx, String thought, Consumer<AIChatResponse> onResponse) {
        if (thought != null && !thought.isEmpty()) {
            AIChatResponse contentResp = new AIChatResponse();
            contentResp.setSessionId(ctx.getSessionId());
            contentResp.setModel(ctx.getActualModel());
            contentResp.setDone(false);
            contentResp.setType("result");
            contentResp.setContent(thought);
            onResponse.accept(contentResp);
        }
        sendDoneResponse(ctx, onResponse);
    }

<<<<<<< HEAD
    /**
     * 注册生成的文件到会话索引，让后续对话可以引用
     */
    private void registerGeneratedFile(String sessionId, String fileId, String fileName,
                                        String filePath, String fileType, long fileSize) {
        try {
            if (sessionId == null || filePath == null) {
                log.debug("跳过注册生成文件: sessionId={}, filePath={}", sessionId, filePath);
                return;
            }
            // 推断 MIME 类型
            String mimeType = inferMimeType(fileName, fileType);
            sessionFileIndexService.registerFile(
                sessionId,
                fileId != null ? fileId : "gen_" + System.currentTimeMillis(),
                fileName,
                mimeType,
                fileSize,
                filePath
            );
            log.info("注册生成文件到会话索引: sessionId={}, fileName={}, filePath={}", sessionId, fileName, filePath);
        } catch (Exception e) {
            log.warn("注册生成文件失败: {}", e.getMessage());
        }
    }

    /**
     * 根据文件名和类型推断 MIME 类型
     */
    private String inferMimeType(String fileName, String fileType) {
        if (fileName == null) return "application/octet-stream";
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".docx")) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        if (lower.endsWith(".xlsx")) return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        if (lower.endsWith(".pptx")) return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
        if (lower.endsWith(".pdf")) return "application/pdf";
        if (lower.endsWith(".md")) return "text/markdown";
        if (lower.endsWith(".txt")) return "text/plain";
        return "application/octet-stream";
    }

=======
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
    private void sendErrorResponse(ExecutionContext ctx, String error, Consumer<AIChatResponse> onResponse) {
        // 记录错误到 ReasoningChain
        ctx.addThought("执行失败: " + error);
        ctx.getReasoningChain().markFailed(error);

        AIChatResponse errorResponse = new AIChatResponse();
        errorResponse.setError(error);
        errorResponse.setContent(error);
        errorResponse.setSessionId(ctx.getSessionId());
        errorResponse.setModel(ctx.getActualModel() != null ? ctx.getActualModel() : ctx.getModel());
        errorResponse.setDone(true);
        errorResponse.setType("error");
        onResponse.accept(errorResponse);

        ctx.setExecutionEnded(true);
    }

    private void sendErrorResponse(ExecutionContext ctx, Exception e, Consumer<AIChatResponse> onResponse) {
        ChatErrorType errorType = ChatErrorType.fromException(e);

        ctx.addThought("执行失败: " + errorType.getTitle());
        ctx.getReasoningChain().markFailed(e.getMessage());

        AIChatResponse errorResponse = errorType.toResponse(ctx.getSessionId(),
            ctx.getActualModel() != null ? ctx.getActualModel() : ctx.getModel());
        errorResponse.setContent(e.getMessage());
        onResponse.accept(errorResponse);

        ctx.setExecutionEnded(true);
    }

    private void finalizeExecution(ExecutionContext ctx, Consumer<AIChatResponse> onResponse) {
        // DynamicConfigManager: 清理会话配置
        try {
            dynamicConfigManager.cleanupSession(ctx.getSessionId());
            log.info("[DynamicConfig] 会话配置已清理: sessionId={}", ctx.getSessionId());
        } catch (Exception e) {
            log.warn("[DynamicConfig] 清理会话配置失败: {}", e.getMessage());
        }

        // 清理 bash-sandbox 会话目录，避免临时文件堆积
        cleanupBashSandboxSessions(ctx);

        if (!ctx.isExecutionEnded()) {
            sessionManager.endExecution(ctx.getSessionId());
            ctx.setExecutionEnded(true);
        }

        costTrackingService.endSession(ctx.getSessionId());

        // 确保 ReasoningChain 被正确标记
        ReasoningChain chain = ctx.getReasoningChain();
        if (chain.getStatus() == ReasoningChain.ReasoningStatus.IN_PROGRESS) {
            chain.markCompleted();
        }

        // Log reasoning chain statistics
        log.info("[sessionId={}] 推理链统计: steps={}, actions={}, successRate={}%, status={}",
                ctx.getSessionId(),
                chain.getSteps().size(),
                chain.getActionCount(),
                String.format("%.1f", chain.getSuccessRate() * 100),
                chain.getStatus());

        if (ctx.getObservabilityTraceId() != null) {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("iterationCount", ctx.getCurrentIteration());
            metadata.put("toolCallCount", ctx.getToolResults().size());
            metadata.put("reasoningSteps", chain.getSteps().size());
            metadata.put("reasoningSuccessRate", chain.getSuccessRate());
            metadata.put("reasoningStatus", chain.getStatus().name());
            observabilityService.endTrace(ctx.getObservabilityTraceId(),
                    chain.getStatus() == ReasoningChain.ReasoningStatus.COMPLETED ? "completed" : "failed",
                    metadata);
        }

        saveContextAndMemory(ctx);
    }

    /**
<<<<<<< HEAD
     * 清理 bash-sandbox 会话：关闭进程但保留文件，延迟后再删除文件
     */
    private void cleanupBashSandboxSessions(ExecutionContext ctx) {
        try {
=======
     * 清理 bash-sandbox 会话目录，避免临时文件堆积
     */
    private void cleanupBashSandboxSessions(ExecutionContext ctx) {
        try {
            // 获取当前会话中使用的 bash-sandbox 会话 ID
            // 会话 ID 格式通常是 "sess_<timestamp>_<random>"
            // 我们需要清理与当前 session 相关的所有 bash-sandbox 会话

            // 调用 bash-sandbox 的 list_sessions 获取所有会话
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
            McpToolCallResponse listResult = mcpHostService.callTool("bash-sandbox", "list_sessions", new HashMap<>());
            if (listResult.isSuccess() && listResult.getContent() != null) {
                String content = listResult.getContent().stream()
                    .filter(item -> "text".equals(item.getType()))
                    .map(item -> item.getText())
                    .findFirst()
                    .orElse("");

<<<<<<< HEAD
                if (content.contains("sessions") || content.contains("[")) {
                    java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\"sessionId\"\\s*:\\s*\"(sess_[^\"]+)\"");
                    java.util.regex.Matcher matcher = pattern.matcher(content);

                    List<String> sessionsToSchedule = new ArrayList<>();
                    while (matcher.find()) {
                        String sandboxSessionId = matcher.group(1);
                        sessionsToSchedule.add(sandboxSessionId);
                    }

                    for (String sandboxSessionId : sessionsToSchedule) {
                        try {
                            // 关闭会话进程（释放资源），但保留文件目录（cleanup=false）
                            Map<String, Object> closeParams = new HashMap<>();
                            closeParams.put("sessionId", sandboxSessionId);
                            closeParams.put("cleanup", false);

                            mcpHostService.callTool("bash-sandbox", "close_session", closeParams);
                            log.debug("已关闭 bash-sandbox 会话进程（保留文件）: {}", sandboxSessionId);

                            // 将会话加入延迟清理列表，30 分钟后再清理文件
                            bashSandboxService.scheduleSessionCleanup(sandboxSessionId);
                        } catch (Exception e) {
                            log.warn("关闭 bash-sandbox 会话失败: {} - {}", sandboxSessionId, e.getMessage());
                        }
                    }

                    if (!sessionsToSchedule.isEmpty()) {
                        log.info("已关闭 {} 个 bash-sandbox 会话进程并加入延迟清理列表", sessionsToSchedule.size());
=======
                // 解析会话列表
                if (content.contains("sessions") || content.contains("[")) {
                    // 提取会话 ID 并关闭它们
                    java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\"sessionId\"\\s*:\\s*\"(sess_[^\"]+)\"");
                    java.util.regex.Matcher matcher = pattern.matcher(content);

                    List<String> sessionsToClean = new ArrayList<>();
                    while (matcher.find()) {
                        String sandboxSessionId = matcher.group(1);
                        sessionsToClean.add(sandboxSessionId);
                    }

                    // 关闭每个会话（带清理）
                    for (String sandboxSessionId : sessionsToClean) {
                        try {
                            Map<String, Object> closeParams = new HashMap<>();
                            closeParams.put("sessionId", sandboxSessionId);
                            closeParams.put("cleanup", true);

                            mcpHostService.callTool("bash-sandbox", "close_session", closeParams);
                            log.debug("已清理 bash-sandbox 会话: {}", sandboxSessionId);
                        } catch (Exception e) {
                            log.warn("清理 bash-sandbox 会话失败: {} - {}", sandboxSessionId, e.getMessage());
                        }
                    }

                    if (!sessionsToClean.isEmpty()) {
                        log.info("已清理 {} 个 bash-sandbox 会话目录", sessionsToClean.size());
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
                    }
                }
            }
        } catch (Exception e) {
            log.warn("清理 bash-sandbox 会话失败: {}", e.getMessage());
        }
    }

    private void saveContextAndMemory(ExecutionContext ctx) {
        if (ctx.getUserId() == null || ctx.getMessages().size() < 2) {
            return;
        }

        try {
            // 只有用户开启知识提取时才执行
            if (ctx.isEnableKnowledgeExtraction()) {
<<<<<<< HEAD
                // 获取最后一轮对话
                String lastUserMsg = null;
                String lastAssistantMsg = null;

                for (int i = ctx.getMessages().size() - 1; i >= 0; i--) {
                    Map<String, Object> msg = ctx.getMessages().get(i);
                    String role = (String) msg.get("role");

                    if ("user".equals(role) && lastUserMsg == null) {
                        lastUserMsg = (String) msg.get("content");
                    } else if ("assistant".equals(role) && lastAssistantMsg == null) {
                        lastAssistantMsg = (String) msg.get("content");
                    }

                    if (lastUserMsg != null && lastAssistantMsg != null) break;
                }

                // 使用记忆宫殿系统异步提取记忆
                if (lastUserMsg != null) {
                    memoryPalaceService.createFromConversationAsync(
                            ctx.getUserId(), ctx.getSessionId(), lastUserMsg, lastAssistantMsg);
                }
            }

        } catch (Exception e) {
            log.warn("自动提取记忆失败：{}", e.getMessage());
=======
                List<Map<String, Object>> nonSystemMessages = new ArrayList<>();
                for (Map<String, Object> msg : ctx.getMessages()) {
                    String role = (String) msg.get("role");
                    if (!"system".equals(role)) {
                        nonSystemMessages.add(msg);
                    }
                }

                // 异步提取知识图谱（合并了长期记忆功能）
                knowledgeExtractorService.extractAsync(
                        ctx.getUserId(), ctx.getSessionId(), nonSystemMessages, ctx.getModel());
            }

        } catch (Exception e) {
            log.warn("自动提取知识失败：{}", e.getMessage());
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
        }

        try {
            String lastUserMsg = null;
            String lastAssistantMsg = null;

            for (int i = ctx.getMessages().size() - 1; i >= 0; i--) {
                Map<String, Object> msg = ctx.getMessages().get(i);
                String role = (String) msg.get("role");

                if ("user".equals(role) && lastUserMsg == null) {
                    lastUserMsg = (String) msg.get("content");
                } else if ("assistant".equals(role) && lastAssistantMsg == null) {
                    lastAssistantMsg = (String) msg.get("content");
                }

                if (lastUserMsg != null && lastAssistantMsg != null) break;
            }

            if (lastUserMsg != null && lastAssistantMsg != null) {
                contextMangerService.saveTurn(ctx.getSessionId(), ctx.getUserId(), ctx.getModel(),
                        ChatMode.MEDIUM_TASK, lastUserMsg, lastAssistantMsg);
            }
        } catch (Exception e) {
            log.warn("保存上下文失败：{}", e.getMessage());
        }
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
<<<<<<< HEAD

=======
    
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
    private String sanitizeApiUrl(String apiUrl) {
        if (apiUrl == null || apiUrl.isEmpty()) {
            return "";
        }
        String sanitized = apiUrl.trim();
        while (sanitized.endsWith(",") || sanitized.endsWith("/")) {
            if (sanitized.endsWith(",")) {
                sanitized = sanitized.substring(0, sanitized.length() - 1).trim();
            } else if (sanitized.endsWith("/") && !sanitized.endsWith("//")) {
                break;
            } else {
                break;
            }
        }
        return sanitized;
    }
}
