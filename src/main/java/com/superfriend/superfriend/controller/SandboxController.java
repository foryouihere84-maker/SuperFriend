package com.superfriend.superfriend.controller;

import com.superfriend.superfriend.dto.ApiResponse;
import com.superfriend.superfriend.service.BashSandboxService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@RestController
@RequestMapping("/api/sandbox")
@CrossOrigin(origins = "*")
@Tag(name = "Bash 沙箱", description = "安全的命令执行环境管理")
@ConditionalOnProperty(prefix = "bash-sandbox", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SandboxController {

    @Autowired
    private BashSandboxService bashSandboxService;

    @PostMapping("/execute")
    @Operation(summary = "执行命令", description = "在沙箱环境中执行指定的命令")
    public CompletableFuture<ResponseEntity<ApiResponse<BashSandboxService.ExecuteResult>>> execute(
            @Parameter(description = "执行请求") @RequestBody ExecuteRequest request) {
        
        log.info("执行命令请求: {}", truncateCommand(request.getCommand()));
        
        return bashSandboxService.execute(
                request.getSessionId(),
                request.getCommand(),
                request.getWorkingDirectory(),
                request.getTimeout(),
                request.getEnvironment()
        ).thenApply(result -> ResponseEntity.ok(ApiResponse.success(result)));
    }

    @PostMapping("/sessions")
    @Operation(summary = "创建会话", description = "创建一个新的沙箱会话")
    public CompletableFuture<ResponseEntity<ApiResponse<BashSandboxService.SessionInfo>>> createSession(
            @Parameter(description = "会话配置") @RequestBody(required = false) CreateSessionRequest request) {
        
        log.info("创建会话请求");
        
        Map<String, String> env = null;
        String workDir = null;
        String name = null;
        
        if (request != null) {
            env = request.getEnvironment();
            workDir = request.getWorkingDirectory();
            name = request.getName();
        }
        
        return bashSandboxService.createSession(workDir, env, name)
                .thenApply(session -> ResponseEntity.ok(ApiResponse.success(session)));
    }

    @GetMapping("/sessions")
    @Operation(summary = "列出会话", description = "列出所有活跃的沙箱会话")
    public CompletableFuture<ResponseEntity<ApiResponse<List<BashSandboxService.SessionInfo>>>> listSessions() {
        log.info("列出会话请求");
        return bashSandboxService.listSessions()
                .thenApply(sessions -> ResponseEntity.ok(ApiResponse.success(sessions)));
    }

    @GetMapping("/sessions/{sessionId}")
    @Operation(summary = "获取会话信息", description = "获取指定会话的详细信息")
    public CompletableFuture<ResponseEntity<ApiResponse<BashSandboxService.SessionInfo>>> getSessionInfo(
            @Parameter(description = "会话 ID") @PathVariable String sessionId) {
        
        log.info("获取会话信息请求: {}", sessionId);
        return bashSandboxService.getSessionInfo(sessionId)
                .thenApply(session -> {
                    if (session == null) {
                        return ResponseEntity.ok(ApiResponse.<BashSandboxService.SessionInfo>error("会话不存在或已过期"));
                    }
                    return ResponseEntity.ok(ApiResponse.success(session));
                });
    }

    @DeleteMapping("/sessions/{sessionId}")
    @Operation(summary = "关闭会话", description = "关闭指定的沙箱会话")
    public CompletableFuture<ResponseEntity<ApiResponse<Map<String, Object>>>> closeSession(
            @Parameter(description = "会话 ID") @PathVariable String sessionId,
            @Parameter(description = "是否清理会话目录") @RequestParam(defaultValue = "true") boolean cleanup) {
        
        log.info("关闭会话请求: {}, cleanup: {}", sessionId, cleanup);
        
        return bashSandboxService.closeSession(sessionId, cleanup)
                .thenApply(success -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("sessionId", sessionId);
                    result.put("closed", success);
                    result.put("cleanup", cleanup);
                    return ResponseEntity.ok(ApiResponse.success(result));
                });
    }

    @GetMapping("/status")
    @Operation(summary = "获取沙箱状态", description = "获取沙箱服务的当前状态")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getStatus() {
        log.info("获取沙箱状态请求");
        
        Map<String, Object> status = new HashMap<>();
        status.put("available", bashSandboxService.isAvailable());
        status.put("timestamp", java.time.LocalDateTime.now().toString());
        
        String osName = System.getProperty("os.name", "");
        status.put("platform", osName);
        
        return ResponseEntity.ok(ApiResponse.success(status));
    }

    // ==================== Phase 4: 高级功能 API ====================

    @GetMapping("/templates")
    @Operation(summary = "列出命令模板", description = "列出可用的命令模板")
    public CompletableFuture<ResponseEntity<ApiResponse<BashSandboxService.TemplateListResult>>> listTemplates(
            @Parameter(description = "分类过滤") @RequestParam(required = false) String category) {
        log.info("列出命令模板请求, category: {}", category);
        return bashSandboxService.listTemplates(category)
                .thenApply(result -> ResponseEntity.ok(ApiResponse.success(result)));
    }

    @GetMapping("/templates/{templateId}")
    @Operation(summary = "获取模板详情", description = "获取指定命令模板的详细信息")
    public CompletableFuture<ResponseEntity<ApiResponse<BashSandboxService.TemplateInfo>>> getTemplate(
            @Parameter(description = "模板 ID") @PathVariable String templateId) {
        log.info("获取模板详情请求: {}", templateId);
        return bashSandboxService.getTemplate(templateId)
                .thenApply(result -> ResponseEntity.ok(ApiResponse.success(result)));
    }

    @PostMapping("/templates/render")
    @Operation(summary = "渲染命令模板", description = "使用参数渲染命令模板，生成可执行的命令")
    public CompletableFuture<ResponseEntity<ApiResponse<BashSandboxService.RenderedTemplate>>> renderTemplate(
            @Parameter(description = "渲染请求") @RequestBody RenderTemplateRequest request) {
        log.info("渲染模板请求: {}", request.getTemplateId());
        return bashSandboxService.renderTemplate(request.getTemplateId(), request.getParams())
                .thenApply(result -> ResponseEntity.ok(ApiResponse.success(result)));
    }

    @PostMapping("/parse")
    @Operation(summary = "解析命令输出", description = "将命令输出解析为结构化数据")
    public CompletableFuture<ResponseEntity<ApiResponse<BashSandboxService.ParseOutputResult>>> parseOutput(
            @Parameter(description = "解析请求") @RequestBody ParseOutputRequest request) {
        log.info("解析输出请求, format: {}", request.getFormat());
        return bashSandboxService.parseOutput(request.getOutput(), request.getFormat())
                .thenApply(result -> ResponseEntity.ok(ApiResponse.success(result)));
    }

    @PostMapping("/batch")
    @Operation(summary = "批量执行命令", description = "批量执行多个命令，支持顺序或并行执行")
    public CompletableFuture<ResponseEntity<ApiResponse<BashSandboxService.BatchExecutionReport>>> executeBatch(
            @Parameter(description = "批量执行请求") @RequestBody BatchExecuteRequest request) {
        log.info("批量执行请求, commands: {}, parallel: {}", 
                request.getCommands() != null ? request.getCommands().size() : 0, 
                request.isParallel());
        
        java.util.List<BashSandboxService.BatchCommand> commands = new java.util.ArrayList<>();
        if (request.getCommands() != null) {
            for (BatchCommandItem item : request.getCommands()) {
                commands.add(BashSandboxService.BatchCommand.builder()
                        .id(item.getId())
                        .command(item.getCommand())
                        .workingDirectory(item.getWorkingDirectory())
                        .timeout(item.getTimeout())
                        .build());
            }
        }
        
        return bashSandboxService.executeBatch(
                request.getSessionId(),
                commands,
                request.isParallel(),
                request.isStopOnFirstError()
        ).thenApply(result -> ResponseEntity.ok(ApiResponse.success(result)));
    }

    @GetMapping("/suggest")
    @Operation(summary = "获取命令建议", description = "根据上下文获取命令建议")
    public CompletableFuture<ResponseEntity<ApiResponse<BashSandboxService.SuggestionResult>>> suggestCommands(
            @Parameter(description = "用户意图") @RequestParam(required = false) String intent,
            @Parameter(description = "项目类型") @RequestParam(required = false) String projectType,
            @Parameter(description = "最后执行的命令") @RequestParam(required = false) String lastCommand,
            @Parameter(description = "最后命令的退出码") @RequestParam(required = false) Integer lastExitCode) {
        log.info("获取命令建议请求, intent: {}, projectType: {}", intent, projectType);
        return bashSandboxService.suggestCommands(intent, projectType, lastCommand, lastExitCode)
                .thenApply(result -> ResponseEntity.ok(ApiResponse.success(result)));
    }

    private String truncateCommand(String command) {
        if (command == null) return "";
        return command.length() > 100 ? command.substring(0, 100) + "..." : command;
    }

    @lombok.Data
    public static class ExecuteRequest {
        private String sessionId;
        private String command;
        private String workingDirectory;
        private Long timeout;
        private Map<String, String> environment;
    }

    @lombok.Data
    public static class CreateSessionRequest {
        private String workingDirectory;
        private Map<String, String> environment;
        private String name;
    }

    // Phase 4 请求类

    @lombok.Data
    public static class RenderTemplateRequest {
        private String templateId;
        private Map<String, Object> params;
    }

    @lombok.Data
    public static class ParseOutputRequest {
        private String output;
        private String format;
    }

    @lombok.Data
    public static class BatchExecuteRequest {
        private String sessionId;
        private java.util.List<BatchCommandItem> commands;
        private boolean parallel;
        private boolean stopOnFirstError;
    }

    @lombok.Data
    public static class BatchCommandItem {
        private String id;
        private String command;
        private String workingDirectory;
        private Long timeout;
    }
}
