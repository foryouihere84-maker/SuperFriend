package com.superfriend.superfriend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.superfriend.superfriend.constant.ChatMode;
import com.superfriend.superfriend.dto.*;
import com.superfriend.superfriend.service.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

@Slf4j
@RestController
@RequestMapping("/api/v16/chat")
@CrossOrigin(origins = "*")
@Tag(name = "对话管理", description = "智能对话接口，支持 MCP 工具调用和流式响应")
@Validated
public class ChatController {

    @Resource
    private ChatService chatService;

    @Resource
    @Lazy
    private ChatOrchestrator chatOrchestrator;

    @Resource
    @Lazy
    private ContextMangerService contextMangerService;

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    @Lazy
    private AgentSessionManager agentSessionManager;

    @Resource
    @Lazy
    private DelayedExtractionQueueService delayedExtractionQueueService;
    @Setter
    @Getter
    private HttpServletRequest httpRequest;

    /**
     * 线程安全的 SSE 上下文，用于管理连接状态和内容累积
     */
    private static class SSEContext {
        private final StringBuffer content = new StringBuffer();
        private final AtomicBoolean clientConnected = new AtomicBoolean(true);
        private final AtomicBoolean errorOccurred = new AtomicBoolean(false);
        @Setter
        @Getter
        private volatile long lastActivityTime = System.currentTimeMillis();

        public void appendContent(String text) {
            synchronized (content) {
                content.append(text);
            }
        }

        public String getContent() {
            synchronized (content) {
                return content.toString();
            }
        }

        public boolean isClientDisconnected() {
            return !clientConnected.get();
        }

        public void disconnect() {
            clientConnected.set(false);
        }

        public boolean hasError() {
            return errorOccurred.get();
        }

        public void setError() {
            errorOccurred.set(true);
        }

        public void updateActivity() {
            lastActivityTime = System.currentTimeMillis();
        }

    }

    /**
     * 配置 SSE 响应头
     */
    private void configureSSEResponse(HttpServletResponse response) {
        response.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Connection", "keep-alive");
        // Nginx header to disable response buffering for SSE streaming
        response.setHeader("X-Accel-Buffering", "no");
    }

    /**
     * 创建带客户端断开检测的 SSE 回调
     */
    private Consumer<AIChatResponse> createSafeSSECallback(
            HttpServletResponse response,
            SSEContext context) {

        return aiResponse -> {
            if (context.isClientDisconnected()) {
                log.debug("客户端已断开，跳过 SSE 发送");
                return;
            }

            try {
                String jsonData = objectMapper.writeValueAsString(aiResponse);
                response.getWriter().write("data: " + jsonData + "\n\n");
                response.getWriter().flush();
                context.updateActivity();

                if (aiResponse.getContent() != null) {
                    context.appendContent(aiResponse.getContent());
                }
            } catch (IOException e) {
                log.warn("SSE 发送失败，客户端可能已断开: {}", e.getMessage());
                context.disconnect();
                context.setError();
            }
        };
    }

    /**
     * 安全发送完成信号
     */
    private void safeSendDone(HttpServletResponse response, SSEContext context) {
        if (context.isClientDisconnected()) {
            log.info("客户端已断开，跳过 [DONE] 信号");
            return;
        }
        try {
            log.info("对话完成，发送 [DONE] 信号");
            response.getWriter().write("data: [DONE]\n\n");
            response.getWriter().flush();
        } catch (IOException e) {
            log.warn("发送 [DONE] 失败: {}", e.getMessage());
        }
    }

    /**
     * 安全发送错误响应
     */
    private void safeSendError(HttpServletResponse response, String sessionId, String model, String errorMessage, SSEContext context) {
        if (context.isClientDisconnected()) {
            return;
        }
        try {
            AIChatResponse errorResponse = new AIChatResponse();
            errorResponse.setError(errorMessage);
            errorResponse.setSessionId(sessionId);
            errorResponse.setModel(model != null ? model : "unknown");
            errorResponse.setDone(true);

            String errorJson = objectMapper.writeValueAsString(errorResponse);
            response.getWriter().write("data: " + errorJson + "\n\n");
            response.getWriter().flush();

            safeSendDone(response, context);
        } catch (IOException ex) {
            log.error("发送错误响应失败: {}", ex.getMessage());
        }
    }

    /**
     * 统一保存上下文的辅助方法
     */
    private void saveContextIfNeeded(AIChatRequest request, String mode,
                                      SSEContext context, MultimodalContext multimodalCtx) {
        if (!context.hasError()) {
            String content = context.getContent();

            // 添加生成的资源引用（图片/音频/视频）
            if (multimodalCtx != null && multimodalCtx.getGeneratedResourceId() != null) {
                String resourceId = multimodalCtx.getGeneratedResourceId();
                String resourceType = multimodalCtx.getGeneratedResourceType();
                if ("image".equals(resourceType)) {
                    content = content + "\n\n![生成的图片](image:" + resourceId + ")\n\n";
                } else if ("audio".equals(resourceType)) {
                    content = content + "\n\n[audio:" + resourceId + "]\n\n";
                } else if ("video".equals(resourceType)) {
                    content = content + "\n\n[video:" + resourceId + "]\n\n";
                } else if ("file".equals(resourceType)) {
                    content = content + "\n\n[file:" + resourceId + "]\n\n";
                }
            }

            boolean enableExtraction = Boolean.TRUE.equals(request.getEnableKnowledgeExtraction());
            contextMangerService.saveContext(
                request.getSessionId(),
                request.getUserId(),
                request.getModel(),
                mode,
                request.getEffectiveText(),
                request.getHistory(),
                content,
                enableExtraction
            );
        }
    }

    @PostMapping(value = "/lite-task", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "轻量对话", description = "轻量模式 - 支持多模态消息、文件解析、图片生成。自动识别用户意图并选择合适的处理方式")
    public void liteTask(@Valid @RequestBody AIChatRequest request,
                         HttpServletRequest httpRequest,
                         HttpServletResponse response) {
        this.httpRequest = httpRequest;
        log.info("收到轻量对话请求：sessionId={}, model={}, multimodal={}",
            request.getSessionId(), request.getModel(), request.isMultimodal());

        configureSSEResponse(response);
        SSEContext context = new SSEContext();

        try {
            Consumer<AIChatResponse> sseCallback = createSafeSSECallback(response, context);
            MultimodalContext multimodalCtx = chatOrchestrator.dispatch(request, sseCallback, ChatMode.LITE_TASK);

            saveContextIfNeeded(request, ChatMode.LITE_TASK, context, multimodalCtx);
            safeSendDone(response, context);

        } catch (Exception e) {
            log.error("轻量对话失败：{}", e.getMessage(), e);
            safeSendError(response, request.getSessionId(), request.getModel(), "轻量对话失败：" + e.getMessage(), context);
        }
    }

    @PostMapping(value = "/lite-task-multimodal", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "轻量对话（多模态）", description = "轻量模式多模态接口 - 支持文本和图片输入，返回流式响应")
    public void liteTaskMultimodal(@Valid @RequestBody AIChatRequest request,
                                   HttpServletRequest httpRequest,
                                   HttpServletResponse response) {
        this.httpRequest = httpRequest;
        log.info("收到多模态轻量对话请求：sessionId={}, model={}, hasImages={}",
            request.getSessionId(), request.getModel(),
            request.getImages() != null ? request.getImages().size() : 0);

        configureSSEResponse(response);
        SSEContext context = new SSEContext();

        try {
            Consumer<AIChatResponse> sseCallback = createSafeSSECallback(response, context);
            MultimodalContext multimodalCtx = chatOrchestrator.dispatch(request, sseCallback, ChatMode.LITE_TASK);

            saveContextIfNeeded(request, ChatMode.LITE_TASK, context, multimodalCtx);
            safeSendDone(response, context);

        } catch (Exception e) {
            log.error("多模态轻量对话失败：{}", e.getMessage(), e);
            safeSendError(response, request.getSessionId(), request.getModel(), "多模态轻量对话失败：" + e.getMessage(), context);
        }
    }

    @PostMapping(value = "/medium-task", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "中等任务", description = "中等任务模式 - 支持多模态消息、文件解析、图片生成。自动识别用户意图并选择合适的处理方式")
    public void mediumTask(@Valid @RequestBody AIChatRequest request,
                           HttpServletRequest httpRequest,
                           HttpServletResponse response) {
        this.httpRequest = httpRequest;
        log.info("收到中等任务请求：sessionId={}, model={}, multimodal={}",
            request.getSessionId(), request.getModel(), request.isMultimodal());

        configureSSEResponse(response);
        SSEContext context = new SSEContext();

        try {
            Consumer<AIChatResponse> sseCallback = createSafeSSECallback(response, context);
            MultimodalContext multimodalCtx = chatOrchestrator.dispatch(request, sseCallback, ChatMode.MEDIUM_TASK);

            saveContextIfNeeded(request, ChatMode.MEDIUM_TASK, context, multimodalCtx);
            safeSendDone(response, context);

        } catch (Exception e) {
            log.error("中等任务失败：{}", e.getMessage(), e);
            safeSendError(response, request.getSessionId(), request.getModel(), "中等任务失败：" + e.getMessage(), context);
        }
    }

    @PostMapping(value = "/complex-task", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "复杂任务", description = "复杂任务模式 - 支持多模态消息、文件解析、图片生成。自动识别用户意图并选择合适的处理方式")
    public void complexTask(@Valid @RequestBody AIChatRequest request,
                            HttpServletRequest httpRequest,
                            HttpServletResponse response) {
        this.httpRequest = httpRequest;
        log.info("收到复杂任务请求：sessionId={}, model={}, multimodal={}",
            request.getSessionId(), request.getModel(), request.isMultimodal());

        configureSSEResponse(response);
        SSEContext context = new SSEContext();

        try {
            Consumer<AIChatResponse> sseCallback = createSafeSSECallback(response, context);
            MultimodalContext multimodalCtx = chatOrchestrator.dispatch(request, sseCallback, ChatMode.COMPLEX_TASK);

            saveContextIfNeeded(request, ChatMode.COMPLEX_TASK, context, multimodalCtx);
            safeSendDone(response, context);

        } catch (Exception e) {
            log.error("复杂任务失败：{}", e.getMessage(), e);
            safeSendError(response, request.getSessionId(), request.getModel(), "复杂任务失败：" + e.getMessage(), context);
        }
    }

    @PostMapping(value = "/mcp", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "MCP 智能对话", description = "与 AI 进行 MCP 对话，支持工具调用和流式响应")
    public void chatStream(@Valid @RequestBody AIChatRequest request,
                           HttpServletRequest httpRequest,
                           HttpServletResponse response) {
        this.httpRequest = httpRequest;
        log.info("收到 MCP 对话请求：sessionId={}, model={}",
            request.getSessionId(), request.getModel());

        configureSSEResponse(response);
        SSEContext context = new SSEContext();

        try {
            Consumer<AIChatResponse> sseCallback = createSafeSSECallback(response, context);
            chatService.chat(request, sseCallback);
            safeSendDone(response, context);

        } catch (Exception e) {
            log.error("MCP 对话失败：{}", e.getMessage(), e);
            safeSendError(response, request.getSessionId(), request.getModel(), "MCP 对话失败：" + e.getMessage(), context);
        }
    }

    @PostMapping("/cancel/{sessionId}")
    @Operation(summary = "取消对话", description = "取消正在执行的对话任务")
    public Map<String, Object> cancelChat(@PathVariable String sessionId) {
        log.info("[sessionId={}] 收到取消请求", sessionId);

        Map<String, Object> result = new HashMap<>();
        boolean cancelled = agentSessionManager.cancelSession(sessionId);

        if (cancelled) {
            result.put("success", true);
            result.put("message", "会话已取消");
            log.info("[sessionId={}] 会话取消成功", sessionId);
        } else {
            result.put("success", false);
            result.put("message", "会话不存在或已结束");
            log.warn("[sessionId={}] 会话取消失败：会话不存在或已结束", sessionId);
        }

        return result;
    }

    @GetMapping("/status/{sessionId}")
    @Operation(summary = "查询会话状态", description = "查询指定会话的当前状态")
    public Map<String, Object> getSessionStatus(@PathVariable String sessionId) {
        Map<String, Object> result = new HashMap<>();
        AgentSessionManager.SessionState state = agentSessionManager.getSessionState(sessionId);
        boolean isCancelled = agentSessionManager.isCancelled(sessionId);

        result.put("sessionId", sessionId);
        result.put("state", state.name());
        result.put("isCancelled", isCancelled);
        result.put("isExecuting", state == AgentSessionManager.SessionState.EXECUTING);

        return result;
    }

    @PostMapping("/finalize/{sessionId}")
    @Operation(summary = "结束对话", description = "结束对话并安排延迟知识提取，应在用户关闭/切换对话时调用")
    public Map<String, Object> finalizeConversation(
            @PathVariable String sessionId,
            @RequestBody(required = false) Map<String, Object> body) {
        Long userId = body != null && body.get("userId") != null
            ? Long.valueOf(body.get("userId").toString()) : null;
        String model = body != null ? (String) body.get("model") : null;

        log.info("收到结束对话请求（延迟提取）：sessionId={}, userId={}, model={}", sessionId, userId, model);

        Map<String, Object> result = new HashMap<>();

        try {
            delayedExtractionQueueService.scheduleDelayedExtraction(sessionId, userId, model);
            result.put("success", true);
            result.put("message", "已安排延迟知识提取");
            result.put("sessionId", sessionId);
        } catch (Exception e) {
            log.error("结束对话失败：{}", e.getMessage(), e);
            result.put("success", false);
            result.put("message", "结束对话失败：" + e.getMessage());
        }

        return result;
    }

    @PostMapping("/extract/{sessionId}")
    @Operation(summary = "立即提取知识", description = "立即执行知识提取，前端传入对话内容，无条件执行")
    public Map<String, Object> extractNow(
            @PathVariable String sessionId,
            @RequestBody(required = false) Map<String, Object> body) {
        Long userId = body != null && body.get("userId") != null
            ? Long.valueOf(body.get("userId").toString()) : null;
        String model = body != null ? (String) body.get("model") : null;
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> messages = body != null
            ? (List<Map<String, Object>>) body.get("messages") : null;

        log.info("[手动提取] 收到请求: sessionId={}, userId={}, model={}, messageCount={}",
            sessionId, userId, model, messages != null ? messages.size() : 0);

        Map<String, Object> result = new HashMap<>();

        try {
            if (messages == null || messages.isEmpty()) {
                log.warn("[手动提取] 消息列表为空: sessionId={}", sessionId);
                result.put("success", false);
                result.put("status", "NO_MESSAGES");
                result.put("message", "对话内容为空，无法提取知识");
                result.put("sessionId", sessionId);
                return result;
            }

            DelayedExtractionQueueService.ExtractionResult extractionResult =
                delayedExtractionQueueService.executeImmediate(sessionId, userId, model, messages);

            switch (extractionResult) {
                case SUCCESS:
                    result.put("success", true);
                    result.put("status", "SUCCESS");
                    result.put("message", "知识提取已触发");
                    break;
                case EXTRACTING:
                    result.put("success", false);
                    result.put("status", "EXTRACTING");
                    result.put("message", "知识提取正在进行中，请稍后再试");
                    break;
                case FAILED:
                    result.put("success", false);
                    result.put("status", "FAILED");
                    result.put("message", "知识提取失败");
                    break;
            }
            result.put("sessionId", sessionId);
        } catch (Exception e) {
            log.error("[手动提取] 失败: sessionId={}, error={}", sessionId, e.getMessage(), e);
            result.put("success", false);
            result.put("status", "ERROR");
            result.put("message", "知识提取失败：" + e.getMessage());
        }

        return result;
    }

    @GetMapping("/extraction-status/{sessionId}")
    @Operation(summary = "查询提取状态", description = "查询指定会话的知识提取状态")
    public Map<String, Object> getExtractionStatus(@PathVariable String sessionId) {
        Map<String, Object> result = new HashMap<>();

        DelayedExtractionQueueService.ExtractionStatus status =
            delayedExtractionQueueService.getExtractionStatus(sessionId);

        result.put("sessionId", sessionId);
        result.put("status", status.getStatus());
        result.put("message", status.getMessage());
        result.put("inProgress", status.isInProgress());
        if (status.getRemainingSeconds() != null) {
            result.put("remainingSeconds", status.getRemainingSeconds());
        }

        return result;
    }

    @PostMapping("/finalize/{sessionId}/force-extraction")
    @Operation(summary = "强制重新提取", description = "强制重新进行知识提取，忽略已完成标记")
    public Map<String, Object> forceFinalizeConversation(
            @PathVariable String sessionId,
            @RequestBody(required = false) Map<String, Object> body) {
        Long userId = body != null && body.get("userId") != null
            ? Long.valueOf(body.get("userId").toString()) : null;
        String model = body != null ? (String) body.get("model") : null;

        log.info("收到强制重新提取请求：sessionId={}, userId={}, model={}", sessionId, userId, model);

        Map<String, Object> result = new HashMap<>();

        try {
            contextMangerService.forceFinalizeConversation(sessionId, userId, model);
            result.put("success", true);
            result.put("message", "强制重新提取已触发");
            result.put("sessionId", sessionId);
        } catch (Exception e) {
            log.error("强制重新提取失败：{}", e.getMessage(), e);
            result.put("success", false);
            result.put("message", "强制重新提取失败：" + e.getMessage());
        }

        return result;
    }
}
