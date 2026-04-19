package com.superfriend.superfriend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.superfriend.superfriend.config.SandboxConfig;
import com.superfriend.superfriend.dto.McpToolCallResponse;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Bash 沙箱服务
 * 提供安全的命令执行环境
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "bash-sandbox", name = "enabled", havingValue = "true", matchIfMissing = true)
public class BashSandboxService {

    private final McpHostService mcpHostService;
    private final SandboxConfig config;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    private Counter executionCounter;
    private Counter errorCounter;
    private Counter blockedCounter;
    private Timer executionTimer;
    private Counter sessionCreatedCounter;
    private Counter sessionClosedCounter;

    @Autowired
    public BashSandboxService(McpHostService mcpHostService,
                              SandboxConfig config,
                              ObjectMapper objectMapper,
                              MeterRegistry meterRegistry) {
        this.mcpHostService = mcpHostService;
        this.config = config;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
    }

    @PostConstruct
    public void init() {
        initMetrics();
        
        String osName = System.getProperty("os.name", "").toLowerCase();
        boolean isWindows = osName.contains("win");
        
        if (isWindows && config.isDisableOnWindows()) {
            log.info("Bash 沙箱在 Windows 上已禁用");
            return;
        }
        
        log.info("Bash 沙箱服务初始化完成 - 平台: {}, 根目录: {}, 最大并发会话: {}",
                isWindows ? "Windows" : "Linux",
                config.getPlatformRootDir(),
                config.getMaxConcurrentSessions());
    }

    private void initMetrics() {
        executionCounter = Counter.builder("bash_sandbox_executions_total")
                .description("Total number of command executions")
                .register(meterRegistry);

        errorCounter = Counter.builder("bash_sandbox_errors_total")
                .description("Total number of execution errors")
                .register(meterRegistry);

        blockedCounter = Counter.builder("bash_sandbox_blocked_total")
                .description("Total number of blocked commands")
                .register(meterRegistry);

        executionTimer = Timer.builder("bash_sandbox_execution_duration")
                .description("Command execution duration")
                .register(meterRegistry);

        sessionCreatedCounter = Counter.builder("bash_sandbox_sessions_created_total")
                .description("Total number of sessions created")
                .register(meterRegistry);

        sessionClosedCounter = Counter.builder("bash_sandbox_sessions_closed_total")
                .description("Total number of sessions closed")
                .register(meterRegistry);
    }

    /**
     * 在沙箱中执行命令
     *
     * @param command 要执行的命令
     * @return 执行结果
     */
    public CompletableFuture<ExecuteResult> execute(String command) {
        return execute(null, command, null, null, null);
    }

    /**
     * 在沙箱中执行命令
     *
     * @param sessionId 会话 ID（可选）
     * @param command   要执行的命令
     * @return 执行结果
     */
    public CompletableFuture<ExecuteResult> execute(String sessionId, String command) {
        return execute(sessionId, command, null, null, null);
    }

    /**
     * 在沙箱中执行命令
     *
     * @param sessionId        会话 ID（可选）
     * @param command          要执行的命令
     * @param workingDirectory 工作目录（可选）
     * @param timeout          超时时间（毫秒，可选）
     * @param environment      环境变量（可选）
     * @return 执行结果
     */
    public CompletableFuture<ExecuteResult> execute(String sessionId,
                                                     String command,
                                                     String workingDirectory,
                                                     Long timeout,
                                                     Map<String, String> environment) {
        executionCounter.increment();
        long startTime = System.currentTimeMillis();

        Map<String, Object> params = new HashMap<>();
        params.put("command", command);

        if (sessionId != null && !sessionId.isEmpty()) {
            params.put("sessionId", sessionId);
        }
        if (workingDirectory != null && !workingDirectory.isEmpty()) {
            params.put("workingDirectory", workingDirectory);
        }
        if (timeout != null && timeout > 0) {
            params.put("timeout", timeout);
        }
        if (environment != null && !environment.isEmpty()) {
            params.put("environment", environment);
        }

        String toolName = config.getMcpServerName() + "__execute";

        return mcpHostService.callToolAsync(toolName, params)
                .thenApply(result -> {
                    long duration = System.currentTimeMillis() - startTime;
                    executionTimer.record(duration, TimeUnit.MILLISECONDS);

                    ExecuteResult execResult = parseExecuteResult(result);

                    if (!execResult.isSuccess()) {
                        errorCounter.increment();
                    }
                    if (execResult.isBlocked()) {
                        blockedCounter.increment();
                    }

                    log.debug("命令执行完成 - 命令: {}, 耗时: {}ms, 成功: {}, 被阻止: {}",
                            truncateCommand(command), duration, execResult.isSuccess(), execResult.isBlocked());

                    return execResult;
                })
                .exceptionally(ex -> {
                    long duration = System.currentTimeMillis() - startTime;
                    errorCounter.increment();

                    log.error("命令执行失败 - 命令: {}, 错误: {}",
                            truncateCommand(command), ex.getMessage());

                    return ExecuteResult.builder()
                            .success(false)
                            .exitCode(-3)
                            .error(ex.getMessage())
                            .executionTimeMs(duration)
                            .build();
                });
    }

    /**
     * 创建新会话
     *
     * @return 会话信息
     */
    public CompletableFuture<SessionInfo> createSession() {
        return createSession(null, null, null);
    }

    /**
     * 创建新会话
     *
     * @param workingDirectory 初始工作目录
     * @param environment      初始环境变量
     * @param name             会话名称
     * @return 会话信息
     */
    public CompletableFuture<SessionInfo> createSession(String workingDirectory,
                                                         Map<String, String> environment,
                                                         String name) {
        Map<String, Object> params = new HashMap<>();

        if (workingDirectory != null && !workingDirectory.isEmpty()) {
            params.put("workingDirectory", workingDirectory);
        }
        if (environment != null && !environment.isEmpty()) {
            params.put("environment", environment);
        }
        if (name != null && !name.isEmpty()) {
            params.put("name", name);
        }

        String toolName = config.getMcpServerName() + "__create_session";

        return mcpHostService.callToolAsync(toolName, params)
                .thenApply(result -> {
                    sessionCreatedCounter.increment();
                    SessionInfo sessionInfo = parseSessionInfo(result);
                    log.info("会话创建成功 - ID: {}, 工作目录: {}",
                            sessionInfo.getSessionId(), sessionInfo.getWorkingDirectory());
                    return sessionInfo;
                })
                .exceptionally(ex -> {
                    log.error("创建会话失败: {}", ex.getMessage());
                    throw new RuntimeException("创建会话失败: " + ex.getMessage(), ex);
                });
    }

    /**
     * 关闭会话
     *
     * @param sessionId 会话 ID
     * @return 是否成功
     */
    public CompletableFuture<Boolean> closeSession(String sessionId) {
        return closeSession(sessionId, true);
    }

    /**
     * 关闭会话
     *
     * @param sessionId 会话 ID
     * @param cleanup   是否清理会话目录
     * @return 是否成功
     */
    public CompletableFuture<Boolean> closeSession(String sessionId, boolean cleanup) {
        Map<String, Object> params = new HashMap<>();
        params.put("sessionId", sessionId);
        params.put("cleanup", cleanup);

        String toolName = config.getMcpServerName() + "__close_session";

        return mcpHostService.callToolAsync(toolName, params)
                .thenApply(result -> {
                    sessionClosedCounter.increment();
                    boolean success = parseBooleanResult(result);
                    log.info("会话关闭 - ID: {}, 成功: {}", sessionId, success);
                    return success;
                })
                .exceptionally(ex -> {
                    log.error("关闭会话失败 - ID: {}, 错误: {}", sessionId, ex.getMessage());
                    return false;
                });
    }

    /**
     * 获取会话信息
     *
     * @param sessionId 会话 ID
     * @return 会话信息
     */
    public CompletableFuture<SessionInfo> getSessionInfo(String sessionId) {
        Map<String, Object> params = new HashMap<>();
        params.put("sessionId", sessionId);

        String toolName = config.getMcpServerName() + "__get_session_info";

        return mcpHostService.callToolAsync(toolName, params)
                .thenApply(this::parseSessionInfo)
                .exceptionally(ex -> {
                    log.error("获取会话信息失败 - ID: {}, 错误: {}", sessionId, ex.getMessage());
                    return null;
                });
    }

    /**
     * 列出所有活跃会话
     *
     * @return 会话列表
     */
    public CompletableFuture<List<SessionInfo>> listSessions() {
        String toolName = config.getMcpServerName() + "__list_sessions";

        return mcpHostService.callToolAsync(toolName, new HashMap<>())
                .thenApply(this::parseSessionList)
                .exceptionally(ex -> {
                    log.error("列出会话失败: {}", ex.getMessage());
                    return java.util.Collections.emptyList();
                });
    }

    /**
     * 检查沙箱服务是否可用
     */
    public boolean isAvailable() {
        String osName = System.getProperty("os.name", "").toLowerCase();
        if (osName.contains("win") && config.isDisableOnWindows()) {
            return false;
        }
        return config.isEnabled();
    }

    // ==================== Phase 4: 高级功能 ====================

    /**
     * 列出可用的命令模板
     *
     * @param category 可选的分类过滤
     * @return 模板列表
     */
    public CompletableFuture<TemplateListResult> listTemplates(String category) {
        Map<String, Object> params = new HashMap<>();
        if (category != null && !category.isEmpty()) {
            params.put("category", category);
        }

        String toolName = config.getMcpServerName() + "__list_templates";

        return mcpHostService.callToolAsync(toolName, params)
                .thenApply(this::parseTemplateList)
                .exceptionally(ex -> {
                    log.error("列出模板失败: {}", ex.getMessage());
                    return TemplateListResult.builder()
                            .success(false)
                            .error(ex.getMessage())
                            .build();
                });
    }

    /**
     * 获取指定模板的详细信息
     *
     * @param templateId 模板 ID
     * @return 模板详情
     */
    public CompletableFuture<TemplateInfo> getTemplate(String templateId) {
        Map<String, Object> params = new HashMap<>();
        params.put("templateId", templateId);

        String toolName = config.getMcpServerName() + "__get_template";

        return mcpHostService.callToolAsync(toolName, params)
                .thenApply(this::parseTemplateInfo)
                .exceptionally(ex -> {
                    log.error("获取模板失败 - ID: {}, 错误: {}", templateId, ex.getMessage());
                    return TemplateInfo.builder()
                            .success(false)
                            .error(ex.getMessage())
                            .build();
                });
    }

    /**
     * 渲染命令模板
     *
     * @param templateId 模板 ID
     * @param params     模板参数
     * @return 渲染后的命令
     */
    public CompletableFuture<RenderedTemplate> renderTemplate(String templateId, Map<String, Object> params) {
        Map<String, Object> toolParams = new HashMap<>();
        toolParams.put("templateId", templateId);
        toolParams.put("params", params);

        String toolName = config.getMcpServerName() + "__render_template";

        return mcpHostService.callToolAsync(toolName, toolParams)
                .thenApply(this::parseRenderedTemplate)
                .exceptionally(ex -> {
                    log.error("渲染模板失败 - ID: {}, 错误: {}", templateId, ex.getMessage());
                    return RenderedTemplate.builder()
                            .success(false)
                            .error(ex.getMessage())
                            .build();
                });
    }

    /**
     * 解析命令输出
     *
     * @param output 命令输出
     * @param format 可选的格式指定 (json, csv, table, list, keyvalue, xml, yaml)
     * @return 解析结果
     */
    public CompletableFuture<ParseOutputResult> parseOutput(String output, String format) {
        Map<String, Object> params = new HashMap<>();
        params.put("output", output);
        if (format != null && !format.isEmpty()) {
            params.put("format", format);
        }

        String toolName = config.getMcpServerName() + "__parse_output";

        return mcpHostService.callToolAsync(toolName, params)
                .thenApply(this::parseParseOutputResult)
                .exceptionally(ex -> {
                    log.error("解析输出失败: {}", ex.getMessage());
                    return ParseOutputResult.builder()
                            .success(false)
                            .error(ex.getMessage())
                            .build();
                });
    }

    /**
     * 批量执行命令
     *
     * @param sessionId       会话 ID
     * @param commands        命令列表
     * @param parallel        是否并行执行
     * @param stopOnFirstError 是否在第一个错误时停止
     * @return 批量执行报告
     */
    public CompletableFuture<BatchExecutionReport> executeBatch(
            String sessionId,
            List<BatchCommand> commands,
            boolean parallel,
            boolean stopOnFirstError) {
        
        Map<String, Object> params = new HashMap<>();
        params.put("sessionId", sessionId);
        params.put("commands", commands);
        params.put("parallel", parallel);
        params.put("stopOnFirstError", stopOnFirstError);

        String toolName = config.getMcpServerName() + "__execute_batch";

        return mcpHostService.callToolAsync(toolName, params)
                .thenApply(this::parseBatchReport)
                .exceptionally(ex -> {
                    log.error("批量执行失败: {}", ex.getMessage());
                    return BatchExecutionReport.builder()
                            .success(false)
                            .error(ex.getMessage())
                            .build();
                });
    }

    /**
     * 获取命令建议
     *
     * @param intent       可选的用户意图
     * @param projectType  可选的项目类型
     * @param lastCommand  可选的最后执行的命令
     * @param lastExitCode 可选的最后命令的退出码
     * @return 命令建议列表
     */
    public CompletableFuture<SuggestionResult> suggestCommands(
            String intent,
            String projectType,
            String lastCommand,
            Integer lastExitCode) {
        
        Map<String, Object> params = new HashMap<>();
        if (intent != null && !intent.isEmpty()) {
            params.put("intent", intent);
        }
        if (projectType != null && !projectType.isEmpty()) {
            params.put("projectType", projectType);
        }
        if (lastCommand != null && !lastCommand.isEmpty()) {
            params.put("lastCommand", lastCommand);
        }
        if (lastExitCode != null) {
            params.put("lastExitCode", lastExitCode);
        }

        String toolName = config.getMcpServerName() + "__suggest_commands";

        return mcpHostService.callToolAsync(toolName, params)
                .thenApply(this::parseSuggestionResult)
                .exceptionally(ex -> {
                    log.error("获取命令建议失败: {}", ex.getMessage());
                    return SuggestionResult.builder()
                            .success(false)
                            .error(ex.getMessage())
                            .build();
                });
    }

    // ==================== 解析方法 ====================

    private TemplateListResult parseTemplateList(McpToolCallResponse response) {
        try {
            String content = response.getContent() != null && !response.getContent().isEmpty()
                    ? response.getContent().get(0).getText()
                    : "{}";
            JsonNode data = objectMapper.readTree(content);

            java.util.List<TemplateSummary> templates = new java.util.ArrayList<>();
            JsonNode templatesNode = data.path("templates");
            if (templatesNode.isArray()) {
                for (JsonNode t : templatesNode) {
                    templates.add(TemplateSummary.builder()
                            .id(t.path("id").asText())
                            .name(t.path("name").asText())
                            .description(t.path("description").asText())
                            .category(t.path("category").asText())
                            .command(t.path("command").asText())
                            .build());
                }
            }

            java.util.List<String> categories = new java.util.ArrayList<>();
            JsonNode categoriesNode = data.path("categories");
            if (categoriesNode.isArray()) {
                for (JsonNode c : categoriesNode) {
                    categories.add(c.asText());
                }
            }

            return TemplateListResult.builder()
                    .success(true)
                    .templates(templates)
                    .categories(categories)
                    .totalCount(data.path("totalCount").asInt())
                    .build();
        } catch (Exception e) {
            log.error("解析模板列表失败: {}", e.getMessage());
            return TemplateListResult.builder()
                    .success(false)
                    .error("解析失败: " + e.getMessage())
                    .build();
        }
    }

    private TemplateInfo parseTemplateInfo(McpToolCallResponse response) {
        try {
            String content = response.getContent() != null && !response.getContent().isEmpty()
                    ? response.getContent().get(0).getText()
                    : "{}";
            JsonNode data = objectMapper.readTree(content);

            if (!data.path("success").asBoolean(true)) {
                return TemplateInfo.builder()
                        .success(false)
                        .error(data.path("message").asText("模板不存在"))
                        .build();
            }

            JsonNode template = data.path("template");
            return TemplateInfo.builder()
                    .success(true)
                    .id(template.path("id").asText())
                    .name(template.path("name").asText())
                    .description(template.path("description").asText())
                    .category(template.path("category").asText())
                    .command(template.path("command").asText())
                    .platform(template.path("platform").asText(null))
                    .build();
        } catch (Exception e) {
            log.error("解析模板信息失败: {}", e.getMessage());
            return TemplateInfo.builder()
                    .success(false)
                    .error("解析失败: " + e.getMessage())
                    .build();
        }
    }

    private RenderedTemplate parseRenderedTemplate(McpToolCallResponse response) {
        try {
            String content = response.getContent() != null && !response.getContent().isEmpty()
                    ? response.getContent().get(0).getText()
                    : "{}";
            JsonNode data = objectMapper.readTree(content);

            if (!data.path("success").asBoolean(true)) {
                return RenderedTemplate.builder()
                        .success(false)
                        .error(data.path("error").asText("渲染失败"))
                        .build();
            }

            java.util.List<String> warnings = new java.util.ArrayList<>();
            JsonNode warningsNode = data.path("warnings");
            if (warningsNode.isArray()) {
                for (JsonNode w : warningsNode) {
                    warnings.add(w.asText());
                }
            }

            return RenderedTemplate.builder()
                    .success(true)
                    .templateId(data.path("templateId").asText())
                    .command(data.path("command").asText())
                    .description(data.path("description").asText(null))
                    .warnings(warnings)
                    .build();
        } catch (Exception e) {
            log.error("解析渲染模板失败: {}", e.getMessage());
            return RenderedTemplate.builder()
                    .success(false)
                    .error("解析失败: " + e.getMessage())
                    .build();
        }
    }

    private ParseOutputResult parseParseOutputResult(McpToolCallResponse response) {
        try {
            String content = response.getContent() != null && !response.getContent().isEmpty()
                    ? response.getContent().get(0).getText()
                    : "{}";
            JsonNode data = objectMapper.readTree(content);

            JsonNode parseResult = data.path("parseResult");
            return ParseOutputResult.builder()
                    .success(true)
                    .format(parseResult.path("format").asText())
                    .structured(parseResult.path("structured").asBoolean())
                    .lineCount(parseResult.path("lineCount").asInt())
                    .parsed(objectMapper.convertValue(parseResult.path("parsed"), Object.class))
                    .error(parseResult.path("error").asText(null))
                    .build();
        } catch (Exception e) {
            log.error("解析输出结果失败: {}", e.getMessage());
            return ParseOutputResult.builder()
                    .success(false)
                    .error("解析失败: " + e.getMessage())
                    .build();
        }
    }

    private BatchExecutionReport parseBatchReport(McpToolCallResponse response) {
        try {
            String content = response.getContent() != null && !response.getContent().isEmpty()
                    ? response.getContent().get(0).getText()
                    : "{}";
            JsonNode data = objectMapper.readTree(content);

            if (!data.path("success").asBoolean(true)) {
                return BatchExecutionReport.builder()
                        .success(false)
                        .error(data.path("error").asText("批量执行失败"))
                        .build();
            }

            JsonNode report = data.path("report");
            java.util.List<BatchResult> results = new java.util.ArrayList<>();
            JsonNode resultsNode = report.path("results");
            if (resultsNode.isArray()) {
                for (JsonNode r : resultsNode) {
                    results.add(BatchResult.builder()
                            .id(r.path("id").asText())
                            .success(r.path("success").asBoolean())
                            .stdout(r.path("result").path("stdout").asText(null))
                            .stderr(r.path("result").path("stderr").asText(null))
                            .exitCode(r.path("result").path("exitCode").asInt(-1))
                            .error(r.path("error").asText(null))
                            .skipped(r.path("skipped").asBoolean(false))
                            .build());
                }
            }

            return BatchExecutionReport.builder()
                    .success(true)
                    .totalCommands(report.path("totalCommands").asInt())
                    .successfulCommands(report.path("successfulCommands").asInt())
                    .failedCommands(report.path("failedCommands").asInt())
                    .skippedCommands(report.path("skippedCommands").asInt())
                    .totalTimeMs(report.path("totalTimeMs").asLong())
                    .parallel(report.path("parallel").asBoolean())
                    .results(results)
                    .build();
        } catch (Exception e) {
            log.error("解析批量执行报告失败: {}", e.getMessage());
            return BatchExecutionReport.builder()
                    .success(false)
                    .error("解析失败: " + e.getMessage())
                    .build();
        }
    }

    private SuggestionResult parseSuggestionResult(McpToolCallResponse response) {
        try {
            String content = response.getContent() != null && !response.getContent().isEmpty()
                    ? response.getContent().get(0).getText()
                    : "{}";
            JsonNode data = objectMapper.readTree(content);

            java.util.List<CommandSuggestion> suggestions = new java.util.ArrayList<>();
            JsonNode suggestionsNode = data.path("suggestions");
            if (suggestionsNode.isArray()) {
                for (JsonNode s : suggestionsNode) {
                    suggestions.add(CommandSuggestion.builder()
                            .templateId(s.path("templateId").asText())
                            .name(s.path("name").asText())
                            .description(s.path("description").asText())
                            .command(s.path("command").asText())
                            .category(s.path("category").asText())
                            .relevance(s.path("relevance").asDouble())
                            .build());
                }
            }

            return SuggestionResult.builder()
                    .success(true)
                    .suggestions(suggestions)
                    .count(data.path("count").asInt())
                    .build();
        } catch (Exception e) {
            log.error("解析命令建议失败: {}", e.getMessage());
            return SuggestionResult.builder()
                    .success(false)
                    .error("解析失败: " + e.getMessage())
                    .build();
        }
    }

    private ExecuteResult parseExecuteResult(McpToolCallResponse response) {
        try {
            String content = response.getContent() != null && !response.getContent().isEmpty()
                    ? response.getContent().get(0).getText()
                    : "{}";
            JsonNode data = objectMapper.readTree(content);

            return ExecuteResult.builder()
                    .success(data.path("success").asBoolean())
                    .stdout(data.path("stdout").asText())
                    .stderr(data.path("stderr").asText())
                    .exitCode(data.path("exitCode").asInt())
                    .executionTimeMs(data.path("executionTimeMs").asLong())
                    .sessionId(data.path("sessionId").asText())
                    .workingDirectory(data.path("workingDirectory").asText())
                    .blocked(data.path("blocked").asBoolean())
                    .blockedReason(data.path("blockedReason").asText(null))
                    .error(data.path("error").asText(null))
                    .timedOut(data.path("timedOut").asBoolean(false))
                    .build();
        } catch (Exception e) {
            log.error("解析执行结果失败: {}", e.getMessage());
            return ExecuteResult.builder()
                    .success(false)
                    .error("解析结果失败: " + e.getMessage())
                    .build();
        }
    }

    private SessionInfo parseSessionInfo(McpToolCallResponse response) {
        try {
            String content = response.getContent() != null && !response.getContent().isEmpty()
                    ? response.getContent().get(0).getText()
                    : "{}";
            JsonNode data = objectMapper.readTree(content);

            if (data.has("session")) {
                JsonNode session = data.path("session");
                return SessionInfo.builder()
                        .sessionId(session.path("sessionId").asText())
                        .workingDirectory(session.path("workingDirectory").asText())
                        .createdAt(session.path("createdAt").asText())
                        .lastActivityAt(session.path("lastActivityAt").asText())
                        .name(session.path("name").asText(null))
                        .commandCount(session.path("commandCount").asInt(0))
                        .totalExecutionTimeMs(session.path("totalExecutionTimeMs").asLong(0))
                        .build();
            }

            return SessionInfo.builder()
                    .sessionId(data.path("sessionId").asText())
                    .workingDirectory(data.path("workingDirectory").asText())
                    .createdAt(data.path("createdAt").asText())
                    .build();
        } catch (Exception e) {
            log.error("解析会话信息失败: {}", e.getMessage());
            return null;
        }
    }

    private List<SessionInfo> parseSessionList(McpToolCallResponse response) {
        try {
            String content = response.getContent() != null && !response.getContent().isEmpty()
                    ? response.getContent().get(0).getText()
                    : "{}";
            JsonNode data = objectMapper.readTree(content);
            JsonNode sessions = data.path("sessions");

            java.util.List<SessionInfo> list = new java.util.ArrayList<>();
            if (sessions.isArray()) {
                for (JsonNode session : sessions) {
                    list.add(SessionInfo.builder()
                            .sessionId(session.path("sessionId").asText())
                            .workingDirectory(session.path("workingDirectory").asText())
                            .createdAt(session.path("createdAt").asText())
                            .lastActivityAt(session.path("lastActivityAt").asText())
                            .name(session.path("name").asText(null))
                            .build());
                }
            }
            return list;
        } catch (Exception e) {
            log.error("解析会话列表失败: {}", e.getMessage());
            return java.util.Collections.emptyList();
        }
    }

    private boolean parseBooleanResult(McpToolCallResponse response) {
        try {
            String content = response.getContent() != null && !response.getContent().isEmpty()
                    ? response.getContent().get(0).getText()
                    : "{}";
            JsonNode data = objectMapper.readTree(content);
            return data.path("success").asBoolean();
        } catch (Exception e) {
            return false;
        }
    }

    private String truncateCommand(String command) {
        if (command == null) return "";
        return command.length() > 100 ? command.substring(0, 100) + "..." : command;
    }

    /**
     * 执行结果
     */
    @lombok.Data
    @lombok.Builder
    public static class ExecuteResult {
        private boolean success;
        private String stdout;
        private String stderr;
        private int exitCode;
        private long executionTimeMs;
        private String sessionId;
        private String workingDirectory;
        private boolean blocked;
        private String blockedReason;
        private String error;
        private boolean timedOut;
    }

    /**
     * 会话信息
     */
    @lombok.Data
    @lombok.Builder
    public static class SessionInfo {
        private String sessionId;
        private String workingDirectory;
        private String createdAt;
        private String lastActivityAt;
        private String name;
        private int commandCount;
        private long totalExecutionTimeMs;
    }

    // ==================== Phase 4 数据类 ====================

    /**
     * 模板列表结果
     */
    @lombok.Data
    @lombok.Builder
    public static class TemplateListResult {
        private boolean success;
        private String error;
        private java.util.List<TemplateSummary> templates;
        private java.util.List<String> categories;
        private int totalCount;
    }

    /**
     * 模板摘要
     */
    @lombok.Data
    @lombok.Builder
    public static class TemplateSummary {
        private String id;
        private String name;
        private String description;
        private String category;
        private String command;
    }

    /**
     * 模板详情
     */
    @lombok.Data
    @lombok.Builder
    public static class TemplateInfo {
        private boolean success;
        private String error;
        private String id;
        private String name;
        private String description;
        private String category;
        private String command;
        private String platform;
    }

    /**
     * 渲染后的模板
     */
    @lombok.Data
    @lombok.Builder
    public static class RenderedTemplate {
        private boolean success;
        private String error;
        private String templateId;
        private String command;
        private String description;
        private java.util.List<String> warnings;
    }

    /**
     * 输出解析结果
     */
    @lombok.Data
    @lombok.Builder
    public static class ParseOutputResult {
        private boolean success;
        private String error;
        private String format;
        private boolean structured;
        private int lineCount;
        private Object parsed;
    }

    /**
     * 批量执行报告
     */
    @lombok.Data
    @lombok.Builder
    public static class BatchExecutionReport {
        private boolean success;
        private String error;
        private int totalCommands;
        private int successfulCommands;
        private int failedCommands;
        private int skippedCommands;
        private long totalTimeMs;
        private boolean parallel;
        private java.util.List<BatchResult> results;
    }

    /**
     * 批量命令
     */
    @lombok.Data
    @lombok.Builder
    public static class BatchCommand {
        private String id;
        private String command;
        private String workingDirectory;
        private Long timeout;
    }

    /**
     * 批量执行结果
     */
    @lombok.Data
    @lombok.Builder
    public static class BatchResult {
        private String id;
        private boolean success;
        private String stdout;
        private String stderr;
        private int exitCode;
        private String error;
        private boolean skipped;
    }

    /**
     * 命令建议结果
     */
    @lombok.Data
    @lombok.Builder
    public static class SuggestionResult {
        private boolean success;
        private String error;
        private java.util.List<CommandSuggestion> suggestions;
        private int count;
    }

    /**
     * 命令建议
     */
    @lombok.Data
    @lombok.Builder
    public static class CommandSuggestion {
        private String templateId;
        private String name;
        private String description;
        private String command;
        private String category;
        private double relevance;
    }
}
