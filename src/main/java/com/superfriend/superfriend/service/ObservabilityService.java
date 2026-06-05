package com.superfriend.superfriend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

@Slf4j
@Service
public class ObservabilityService {

    @Value("${observability.langfuse.enabled:false}")
    private boolean langfuseEnabled;

    @Value("${observability.langfuse.public-key:}")
    private String langfusePublicKey;

    @Value("${observability.langfuse.secret-key:}")
    private String langfuseSecretKey;

    @Value("${observability.langfuse.host:https://cloud.langfuse.com}")
    private String langfuseHost;

    @Value("${observability.arize.enabled:false}")
    private boolean arizeEnabled;

    @Value("${observability.arize.api-key:}")
    private String arizeApiKey;

    @Value("${observability.arize.space-id:}")
    private String arizeSpaceId;

    @Value("${observability.arize.model-id:superfriend-agent}")
    private String arizeModelId;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final Map<String, TraceContext> activeTraces = new ConcurrentHashMap<>();

    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

    @Data
    public static class TraceContext {
        private String traceId;
        private String sessionId;
        private String userId;
        private String model;
        private String userMessage;
        private LocalDateTime startTime;
        private List<SpanContext> spans = new ArrayList<>();
        private Map<String, Object> metadata = new HashMap<>();
        private double totalCost;
        private long totalTokens;
    }

    @Data
    public static class SpanContext {
        private String spanId;
        private String parentSpanId;
        private String name;
        private String type;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private Long durationMs;
        private Map<String, Object> input;
        private Map<String, Object> output;
        private Map<String, Object> metadata;
        private String status;
        private String errorMessage;
        private long inputTokens;
        private long outputTokens;
        private double cost;
    }

    @Data
    public static class LLMEvent {
        private String traceId;
        private String spanId;
        private String model;
        private String prompt;
        private String completion;
        private long promptTokens;
        private long completionTokens;
        private double cost;
        private long latencyMs;
        private Map<String, Object> metadata;
    }

    @Data
    public static class ToolEvent {
        private String traceId;
        private String spanId;
        private String toolName;
        private String serverName;
        private Map<String, Object> arguments;
        private String result;
        private boolean success;
        private long latencyMs;
        private String errorMessage;
    }

    @PostConstruct
    public void init() {
        if (langfuseEnabled) {
            log.info("Langfuse 可观测性已启用: {}", langfuseHost);
        }
        if (arizeEnabled) {
            log.info("Arize 可观测性已启用: spaceId={}, modelId={}", arizeSpaceId, arizeModelId);
        }
    }

    public boolean isObservabilityEnabled() {
        return langfuseEnabled || arizeEnabled;
    }

    public String startTrace(String sessionId, String userId, String model, String userMessage) {
        if (!isObservabilityEnabled()) {
            return null;
        }

        String traceId = UUID.randomUUID().toString();
        TraceContext ctx = new TraceContext();
        ctx.setTraceId(traceId);
        ctx.setSessionId(sessionId);
        ctx.setUserId(userId);
        ctx.setModel(model);
        ctx.setUserMessage(userMessage);
        ctx.setStartTime(LocalDateTime.now());

        activeTraces.put(traceId, ctx);

        if (langfuseEnabled) {
            sendLangfuseTraceEvent(ctx, "trace_create");
        }

        log.debug("开始可观测性追踪: traceId={}, sessionId={}", traceId, sessionId);
        return traceId;
    }

    public String startSpan(String traceId, String parentSpanId, String name, String type,
                            Map<String, Object> input) {
        if (!isObservabilityEnabled() || traceId == null) {
            return null;
        }

        TraceContext ctx = activeTraces.get(traceId);
        if (ctx == null) {
            return null;
        }

        String spanId = UUID.randomUUID().toString();
        SpanContext span = new SpanContext();
        span.setSpanId(spanId);
        span.setParentSpanId(parentSpanId);
        span.setName(name);
        span.setType(type);
        span.setStartTime(LocalDateTime.now());
        span.setInput(input);
        span.setStatus("running");

        ctx.getSpans().add(span);

        if (langfuseEnabled) {
            sendLangfuseSpanEvent(ctx, span, "span_start");
        }

        return spanId;
    }

    public void endSpan(String traceId, String spanId, Map<String, Object> output,
                        String status, String errorMessage) {
        if (!isObservabilityEnabled() || traceId == null) {
            return;
        }

        TraceContext ctx = activeTraces.get(traceId);
        if (ctx == null) {
            return;
        }

        Optional<SpanContext> spanOpt = ctx.getSpans().stream()
            .filter(s -> s.getSpanId().equals(spanId))
            .findFirst();

        if (spanOpt.isPresent()) {
            SpanContext span = spanOpt.get();
            span.setEndTime(LocalDateTime.now());
            span.setDurationMs(java.time.Duration.between(span.getStartTime(), span.getEndTime()).toMillis());
            span.setOutput(output);
            span.setStatus(status != null ? status : "completed");
            span.setErrorMessage(errorMessage);

            if (langfuseEnabled) {
                sendLangfuseSpanEvent(ctx, span, "span_end");
            }
        }
    }

    public void recordLLMCall(String traceId, String spanId, LLMEvent event) {
        if (!isObservabilityEnabled() || traceId == null) {
            return;
        }

        TraceContext ctx = activeTraces.get(traceId);
        if (ctx == null) {
            return;
        }

        ctx.setTotalTokens(ctx.getTotalTokens() + event.getPromptTokens() + event.getCompletionTokens());
        ctx.setTotalCost(ctx.getTotalCost() + event.getCost());

        Optional<SpanContext> spanOpt = ctx.getSpans().stream()
            .filter(s -> s.getSpanId().equals(spanId))
            .findFirst();

        if (spanOpt.isPresent()) {
            SpanContext span = spanOpt.get();
            span.setInputTokens(event.getPromptTokens());
            span.setOutputTokens(event.getCompletionTokens());
            span.setCost(event.getCost());
        }

        if (langfuseEnabled) {
            sendLangfuseGenerationEvent(ctx, event);
        }

        if (arizeEnabled) {
            sendArizePredictionEvent(ctx, event);
        }

        log.debug("记录 LLM 调用: traceId={}, model={}, tokens={}/{}, cost={}",
            traceId, event.getModel(), event.getPromptTokens(), event.getCompletionTokens(), event.getCost());
    }

    public void recordToolCall(String traceId, String spanId, ToolEvent event) {
        if (!isObservabilityEnabled() || traceId == null) {
            return;
        }

        TraceContext ctx = activeTraces.get(traceId);
        if (ctx == null) {
            return;
        }

        if (langfuseEnabled) {
            sendLangfuseSpanEvent(ctx, event);
        }

        if (arizeEnabled) {
            sendArizeToolEvent(ctx, event);
        }

        log.debug("记录工具调用: traceId={}, tool={}, success={}", traceId, event.getToolName(), event.isSuccess());
    }

    public void endTrace(String traceId, String status, Map<String, Object> metadata) {
        if (!isObservabilityEnabled() || traceId == null) {
            return;
        }

        TraceContext ctx = activeTraces.get(traceId);
        if (ctx == null) {
            return;
        }

        if (metadata != null) {
            ctx.getMetadata().putAll(metadata);
        }

        if (langfuseEnabled) {
            sendLangfuseTraceEvent(ctx, "trace_end");
        }

        if (arizeEnabled) {
            sendArizeTraceEndEvent(ctx);
        }

        activeTraces.remove(traceId);
        log.debug("结束可观测性追踪: traceId={}, status={}", traceId, status);
    }

    public TraceContext getTraceContext(String traceId) {
        return activeTraces.get(traceId);
    }

    public int getActiveTracesCount() {
        return activeTraces.size();
    }

    private void sendLangfuseTraceEvent(TraceContext ctx, String eventType) {
        executor.submit(() -> {
            try {
                Map<String, Object> event = new LinkedHashMap<>();
                event.put("id", ctx.getTraceId());
                event.put("type", eventType);
                event.put("timestamp", ctx.getStartTime().format(ISO_FORMATTER));

                if ("trace_create".equals(eventType)) {
                    Map<String, Object> data = new HashMap<>();
                    data.put("name", "Agent Execution");
                    data.put("sessionId", ctx.getSessionId());
                    data.put("userId", ctx.getUserId());
                    data.put("input", ctx.getUserMessage());
                    Map<String, Object> metadata1 = new HashMap<>();
                    metadata1.put("model", ctx.getModel() != null ? ctx.getModel() : "unknown");
                    data.put("metadata", metadata1);
                    event.put("body", data);
                } else if ("trace_end".equals(eventType)) {
                    Map<String, Object> data = new HashMap<>();
                    data.put("output", ctx.getMetadata().get("finalResponse"));
                    Map<String, Object> metadata2 = new HashMap<>();
                    metadata2.put("totalCost", ctx.getTotalCost());
                    metadata2.put("totalTokens", ctx.getTotalTokens());
                    metadata2.put("spanCount", ctx.getSpans().size());
                    data.put("metadata", metadata2);
                    event.put("body", data);
                }

                sendLangfuseIngestion(Collections.singletonList(event));
            } catch (Exception e) {
                log.warn("发送 Langfuse trace 事件失败: {}", e.getMessage());
            }
        });
    }

    private void sendLangfuseSpanEvent(TraceContext ctx, SpanContext span, String eventType) {
        executor.submit(() -> {
            try {
                Map<String, Object> event = new LinkedHashMap<>();
                event.put("id", span.getSpanId());
                event.put("type", "span");
                event.put("timestamp", span.getStartTime().format(ISO_FORMATTER));

                Map<String, Object> body = new HashMap<>();
                body.put("traceId", ctx.getTraceId());
                body.put("parentObservationId", span.getParentSpanId());
                body.put("name", span.getName());
                body.put("type", "span");
                body.put("startTime", span.getStartTime().format(ISO_FORMATTER));

                if (span.getEndTime() != null) {
                    body.put("endTime", span.getEndTime().format(ISO_FORMATTER));
                }
                if (span.getInput() != null) {
                    body.put("input", span.getInput());
                }
                if (span.getOutput() != null) {
                    body.put("output", span.getOutput());
                }
                if (span.getMetadata() != null) {
                    body.put("metadata", span.getMetadata());
                }

                Map<String, Object> level = new HashMap<>();
                level.put("value", "completed".equals(span.getStatus()) ? "DEFAULT" : "WARNING");
                body.put("level", level);

                event.put("body", body);
                sendLangfuseIngestion(Collections.singletonList(event));
            } catch (Exception e) {
                log.warn("发送 Langfuse span 事件失败: {}", e.getMessage());
            }
        });
    }

    private void sendLangfuseSpanEvent(TraceContext ctx, ToolEvent toolEvent) {
        executor.submit(() -> {
            try {
                Map<String, Object> event = new LinkedHashMap<>();
                event.put("id", toolEvent.getSpanId());
                event.put("type", "span");
                event.put("timestamp", LocalDateTime.now().format(ISO_FORMATTER));

                Map<String, Object> body = new HashMap<>();
                body.put("traceId", ctx.getTraceId());
                body.put("name", "tool_call:" + toolEvent.getToolName());
                body.put("type", "span");
                body.put("startTime", LocalDateTime.now().minusNanos(toolEvent.getLatencyMs() * 1_000_000).format(ISO_FORMATTER));
                body.put("endTime", LocalDateTime.now().format(ISO_FORMATTER));
                body.put("input", toolEvent.getArguments());
                body.put("output", toolEvent.getResult());
                Map<String, Object> metadata3 = new HashMap<>();
                metadata3.put("serverName", toolEvent.getServerName());
                metadata3.put("success", toolEvent.isSuccess());
                metadata3.put("errorMessage", toolEvent.getErrorMessage() != null ? toolEvent.getErrorMessage() : "");
                body.put("metadata", metadata3);

                Map<String, Object> level = new HashMap<>();
                level.put("value", toolEvent.isSuccess() ? "DEFAULT" : "ERROR");
                body.put("level", level);

                event.put("body", body);
                sendLangfuseIngestion(Collections.singletonList(event));
            } catch (Exception e) {
                log.warn("发送 Langfuse tool 事件失败: {}", e.getMessage());
            }
        });
    }

    private void sendLangfuseGenerationEvent(TraceContext ctx, LLMEvent llmEvent) {
        executor.submit(() -> {
            try {
                Map<String, Object> event = new LinkedHashMap<>();
                event.put("id", llmEvent.getSpanId() != null ? llmEvent.getSpanId() : UUID.randomUUID().toString());
                event.put("type", "generation");
                event.put("timestamp", LocalDateTime.now().format(ISO_FORMATTER));

                Map<String, Object> body = new HashMap<>();
                body.put("traceId", ctx.getTraceId());
                body.put("name", "llm_call:" + llmEvent.getModel());
                body.put("type", "generation");
                body.put("startTime", LocalDateTime.now().minusNanos(llmEvent.getLatencyMs() * 1_000_000).format(ISO_FORMATTER));
                body.put("endTime", LocalDateTime.now().format(ISO_FORMATTER));
                body.put("model", llmEvent.getModel());
                body.put("input", llmEvent.getPrompt());
                body.put("output", llmEvent.getCompletion());

                Map<String, Object> usage = new HashMap<>();
                usage.put("input", llmEvent.getPromptTokens());
                usage.put("output", llmEvent.getCompletionTokens());
                usage.put("total", llmEvent.getPromptTokens() + llmEvent.getCompletionTokens());
                body.put("usage", usage);

                Map<String, Object> metadata4 = new HashMap<>();
                metadata4.put("cost", llmEvent.getCost());
                metadata4.put("latencyMs", llmEvent.getLatencyMs());
                body.put("metadata", metadata4);

                event.put("body", body);
                sendLangfuseIngestion(Collections.singletonList(event));
            } catch (Exception e) {
                log.warn("发送 Langfuse generation 事件失败: {}", e.getMessage());
            }
        });
    }

    private void sendLangfuseIngestion(List<Map<String, Object>> events) {
        try {
            String url = langfuseHost + "/api/public/ingestion";
            String auth = langfusePublicKey + ":" + langfuseSecretKey;
            String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));

            Map<String, Object> payload = new HashMap<>();
            payload.put("batch", events);
            Map<String, Object> metadata5 = new HashMap<>();
            metadata5.put("sdk_version", "custom-java-1.0.0");
            payload.put("metadata", metadata5);

            String jsonPayload = objectMapper.writeValueAsString(payload);

            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Basic " + encodedAuth);
            conn.setDoOutput(true);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(10000);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(jsonPayload.getBytes(StandardCharsets.UTF_8));
            }

            int responseCode = conn.getResponseCode();
            if (responseCode >= 200 && responseCode < 300) {
                log.debug("Langfuse ingestion 成功: {} 个事件", events.size());
            } else {
                log.warn("Langfuse ingestion 失败: HTTP {}", responseCode);
            }
            conn.disconnect();
        } catch (Exception e) {
            log.warn("Langfuse ingestion 异常: {}", e.getMessage());
        }
    }

    private void sendArizePredictionEvent(TraceContext ctx, LLMEvent event) {
        executor.submit(() -> {
            try {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("prediction_id", event.getSpanId() != null ? event.getSpanId() : UUID.randomUUID().toString());
                payload.put("timestamp", System.currentTimeMillis());

                Map<String, Object> features = new HashMap<>();
                features.put("model", event.getModel());
                features.put("prompt_length", event.getPrompt() != null ? event.getPrompt().length() : 0);
                features.put("prompt_tokens", event.getPromptTokens());
                features.put("completion_tokens", event.getCompletionTokens());
                features.put("latency_ms", event.getLatencyMs());
                features.put("cost", event.getCost());
                payload.put("features", features);

                Map<String, Object> predictionLabel = new HashMap<>();
                predictionLabel.put("completion", event.getCompletion());
                payload.put("prediction_label", predictionLabel);

                payload.put("model_id", arizeModelId);

                Map<String, Object> embeddingFeatures = new HashMap<>();
                if (event.getPrompt() != null && !event.getPrompt().isEmpty()) {
                    Map<String, Object> promptEmbedding = new HashMap<>();
                    promptEmbedding.put("vector", Collections.emptyList());
                    promptEmbedding.put("raw_data", event.getPrompt().substring(0, Math.min(1000, event.getPrompt().length())));
                    embeddingFeatures.put("prompt_embedding", promptEmbedding);
                }
                payload.put("embedding_features", embeddingFeatures);

                sendArizeLog(payload, "log_prediction");
            } catch (Exception e) {
                log.warn("发送 Arize prediction 事件失败: {}", e.getMessage());
            }
        });
    }

    private void sendArizeToolEvent(TraceContext ctx, ToolEvent event) {
        executor.submit(() -> {
            try {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("prediction_id", event.getSpanId() != null ? event.getSpanId() : UUID.randomUUID().toString());
                payload.put("timestamp", System.currentTimeMillis());

                Map<String, Object> features = new HashMap<>();
                features.put("tool_name", event.getToolName());
                features.put("server_name", event.getServerName());
                features.put("latency_ms", event.getLatencyMs());
                features.put("success", event.isSuccess());
                features.put("argument_count", event.getArguments() != null ? event.getArguments().size() : 0);
                payload.put("features", features);

                Map<String, Object> predictionLabel = new HashMap<>();
                predictionLabel.put("result", event.getResult() != null ? 
                    event.getResult().substring(0, Math.min(500, event.getResult().length())) : "");
                predictionLabel.put("success", event.isSuccess());
                payload.put("prediction_label", predictionLabel);

                payload.put("model_id", arizeModelId + "-tools");

                sendArizeLog(payload, "log_prediction");
            } catch (Exception e) {
                log.warn("发送 Arize tool 事件失败: {}", e.getMessage());
            }
        });
    }

    private void sendArizeTraceEndEvent(TraceContext ctx) {
        executor.submit(() -> {
            try {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("prediction_id", ctx.getTraceId());
                payload.put("timestamp", System.currentTimeMillis());

                Map<String, Object> features = new HashMap<>();
                features.put("total_tokens", ctx.getTotalTokens());
                features.put("total_cost", ctx.getTotalCost());
                features.put("span_count", ctx.getSpans().size());
                features.put("model", ctx.getModel());
                payload.put("features", features);

                Map<String, Object> actualLabel = new HashMap<>();
                actualLabel.put("success", ctx.getMetadata().get("success"));
                actualLabel.put("response", ctx.getMetadata().get("finalResponse"));
                payload.put("actual_label", actualLabel);

                payload.put("model_id", arizeModelId);

                sendArizeLog(payload, "log_actuals");
            } catch (Exception e) {
                log.warn("发送 Arize trace end 事件失败: {}", e.getMessage());
            }
        });
    }

    private void sendArizeLog(Map<String, Object> payload, String endpoint) {
        try {
            String url = "https://api.arize.com/v1/" + endpoint;
            String jsonPayload = objectMapper.writeValueAsString(payload);

            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + arizeApiKey);
            conn.setRequestProperty("space-id", arizeSpaceId);
            conn.setDoOutput(true);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(10000);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(jsonPayload.getBytes(StandardCharsets.UTF_8));
            }

            int responseCode = conn.getResponseCode();
            if (responseCode >= 200 && responseCode < 300) {
                log.debug("Arize {} 成功", endpoint);
            } else {
                log.warn("Arize {} 失败: HTTP {}", endpoint, responseCode);
            }
            conn.disconnect();
        } catch (Exception e) {
            log.warn("Arize {} 异常: {}", endpoint, e.getMessage());
        }
    }

    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
    }
}
