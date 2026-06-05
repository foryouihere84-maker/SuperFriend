package com.superfriend.superfriend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.superfriend.superfriend.dto.LLMCallRecord;
import com.superfriend.superfriend.dto.LLMChunk;
import com.superfriend.superfriend.dto.LLMCompleteResponse;
import com.superfriend.superfriend.dto.LLMRequest;
import com.superfriend.superfriend.dto.ToolDefinitionForLLM;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

@Slf4j
@Service
public class OpenAICompatibleLLMClient implements LLMClient {

    @Autowired
    private CostTrackingService costTrackingService;

    @Autowired
    private LLMCallMonitorService llmCallMonitorService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void streamChat(LLMRequest request, Consumer<LLMChunk> onChunk) {
        executeStreaming(request, null, onChunk);
    }

    @Override
    public void streamChatWithTools(LLMRequest request, List<ToolDefinitionForLLM> tools,
                                      Consumer<LLMChunk> onChunk) {
        executeStreaming(request, tools, onChunk);
    }

    @Override
    public LLMCompleteResponse chatComplete(LLMRequest request) {
        return executeNonStreaming(request, null);
    }

    @Override
    public LLMCompleteResponse chatCompleteWithTools(LLMRequest request, List<ToolDefinitionForLLM> tools) {
        return executeNonStreaming(request, tools);
    }

    private void executeStreaming(LLMRequest request, List<ToolDefinitionForLLM> tools,
                                   Consumer<LLMChunk> onChunk) {
        final AtomicLong promptTokens = new AtomicLong(0);
        final AtomicLong completionTokens = new AtomicLong(0);
        final StringBuilder responseBuilder = new StringBuilder();
        final StringBuilder reasoningBuilder = new StringBuilder();
        final String[] finishReasonHolder = {null};
        final String[] errorHolder = {null};

        // 开始监控记录
        LLMCallRecord monitorRecord = null;
        if (llmCallMonitorService != null) {
            try {
                monitorRecord = llmCallMonitorService.startCall(
                        request.getSessionId(), request, tools);
            } catch (Exception e) {
                log.warn("[LLMClient] 启动监控记录失败: {}", e.getMessage(), e);
            }
        }

        HttpURLConnection connection = null;
        try {
            Map<String, Object> requestBody = buildRequestBody(request, tools, true);

            String apiUrl = sanitizeApiUrl(request.getApiUrl());
            URL url = new URL(apiUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept", "text/event-stream");
            if (request.getApiKey() != null && !request.getApiKey().isEmpty()) {
                connection.setRequestProperty("Authorization", "Bearer " + request.getApiKey());
            }
            connection.setConnectTimeout(30000);
            connection.setReadTimeout(180000);  // 增加到 3 分钟，给大模型更多时间生成

            log.info("[LLMClient] 流式调用 AI API: {}, model={}", apiUrl, request.getModel());

            String jsonRequest = objectMapper.writeValueAsString(requestBody);
            log.debug("[LLMClient] 请求体大小: {} 字节", jsonRequest.length());
            try (OutputStream os = connection.getOutputStream()) {
                os.write(jsonRequest.getBytes(StandardCharsets.UTF_8));
            }

            int httpCode = connection.getResponseCode();
            log.info("[LLMClient] API 响应码：{}", httpCode);

            if (httpCode == 200) {
                // 使用包装的 onChunk 收集响应内容
                final LLMCallRecord finalMonitorRecord = monitorRecord;
                Consumer<LLMChunk> monitoredOnChunk = chunk -> {
                    // 收集响应内容
                    if (chunk.hasContent()) {
                        responseBuilder.append(chunk.getContent());
                    }
                    if (chunk.hasReasoningContent()) {
                        reasoningBuilder.append(chunk.getReasoningContent());
                    }
                    // 收集完成原因
                    if (chunk.getFinishReason() != null) {
                        finishReasonHolder[0] = chunk.getFinishReason();
                    }
                    // 收集错误
                    if (chunk.isError()) {
                        errorHolder[0] = chunk.getError();
                    }
                    // 调用原始回调
                    onChunk.accept(chunk);
                };

                parseSSEResponse(connection, request.getModel(), request, monitoredOnChunk, promptTokens, completionTokens);
            } else {
                String errorDetail = readErrorStream(connection);
                log.error("[LLMClient] API 请求失败：HTTP {}，错误响应：{}", httpCode, errorDetail);
                errorHolder[0] = "API 请求失败：HTTP " + httpCode + " - " + errorDetail;
                onChunk.accept(LLMChunk.builder()
                    .done(true)
                    .finishReason("error")
                    .error(errorHolder[0])
                    .build());
            }
        } catch (Exception e) {
            log.error("[LLMClient] 流式调用异常：{}", e.getMessage(), e);
            errorHolder[0] = "流式调用异常：" + e.getMessage();
            onChunk.accept(LLMChunk.builder()
                .done(true)
                .finishReason("error")
                .error(errorHolder[0])
                .build());
        } finally {
            // 结束监控记录
            if (monitorRecord != null && llmCallMonitorService != null) {
                try {
                    llmCallMonitorService.endCall(
                            monitorRecord,
                            responseBuilder.toString(),
                            reasoningBuilder.toString(),
                            promptTokens.get(),
                            completionTokens.get(),
                            finishReasonHolder[0],
                            errorHolder[0]
                    );
                } catch (Exception e) {
                    log.warn("[LLMClient] 结束监控记录失败: {}", e.getMessage());
                }
            }

            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private LLMCompleteResponse executeNonStreaming(LLMRequest request, List<ToolDefinitionForLLM> tools) {
        final StringBuilder contentBuilder = new StringBuilder();
        final StringBuilder reasoningBuilder = new StringBuilder();
        final List<LLMCompleteResponse.ToolCall> toolCalls = new ArrayList<>();
        final String[] errorHolder = {null};
        final JsonNode[] usageHolder = {null};
        final String[] finishReasonHolder = {null};
        final AtomicLong promptTokens = new AtomicLong(0);
        final AtomicLong completionTokens = new AtomicLong(0);

        executeStreaming(request, tools, chunk -> {
            if (chunk.isError()) {
                errorHolder[0] = chunk.getError();
                return;
            }
            if (chunk.hasContent()) {
                contentBuilder.append(chunk.getContent());
            }
            if (chunk.hasReasoningContent()) {
                reasoningBuilder.append(chunk.getReasoningContent());
            }
            if (chunk.hasToolCalls()) {
                accumulateToolCalls(chunk.getToolCalls(), toolCalls);
            }
            // 【修复】处理累积的 tool_calls（MiniMax API 在流结束时才返回完整的 tool_calls）
            if (chunk.hasAccumulatedToolCalls()) {
                toolCalls.clear();
                toolCalls.addAll(chunk.getAccumulatedToolCalls());
            }
            if (chunk.getUsage() != null) {
                usageHolder[0] = chunk.getUsage();
                long prompt = chunk.getUsage().path("prompt_tokens").asLong(0);
                long completion = chunk.getUsage().path("completion_tokens").asLong(0);
                promptTokens.set(prompt);
                completionTokens.set(completion);
            }
            if (chunk.getFinishReason() != null) {
                finishReasonHolder[0] = chunk.getFinishReason();
            }
        });

        if (errorHolder[0] != null) {
            return LLMCompleteResponse.builder()
                .success(false)
                .error(errorHolder[0])
                .model(request.getModel())
                .build();
        }



        LLMCompleteResponse.LLMCompleteResponseBuilder builder = LLMCompleteResponse.builder()
            .model(request.getModel())
            .content(contentBuilder.length() > 0 ? contentBuilder.toString() : null)
            .reasoningContent(reasoningBuilder.length() > 0 ? reasoningBuilder.toString() : null);

        if (!toolCalls.isEmpty()) {
            builder.toolCalls(toolCalls);
        }
        if (usageHolder[0] != null) {
            Map<String, Object> usageMap = new HashMap<>();
            addIfNotNull(usageMap, "prompt_tokens", usageHolder[0].path("prompt_tokens"));
            addIfNotNull(usageMap, "completion_tokens", usageHolder[0].path("completion_tokens"));
            addIfNotNull(usageMap, "total_tokens", usageHolder[0].path("total_tokens"));
            builder.usage(usageMap);
        }

        return builder.build();
    }

    private void accumulateToolCalls(JsonNode deltaToolCalls, List<LLMCompleteResponse.ToolCall> accumulated) {
        if (!deltaToolCalls.isArray()) return;
        for (JsonNode dtc : deltaToolCalls) {
            int index = dtc.path("index").asInt(0);
            while (accumulated.size() <= index) {
                accumulated.add(new LLMCompleteResponse.ToolCall("", "", null, ""));
            }
            LLMCompleteResponse.ToolCall existing = accumulated.get(index);
            String tcId = existing.getId() != null ? existing.getId() : "";
            String tcName = existing.getName() != null ? existing.getName() : "";
            String tcArgsStr = existing.getArgumentsStr() != null ? existing.getArgumentsStr() : "";

            JsonNode idNode = dtc.path("id");
            if (!idNode.isMissingNode() && !idNode.asText().isEmpty()) {
                tcId = idNode.asText();
            }
            JsonNode funcNode = dtc.path("function");
            if (!funcNode.isMissingNode()) {
                JsonNode nameNode = funcNode.path("name");
                if (!nameNode.isMissingNode() && !nameNode.asText().isEmpty()) {
                    tcName = nameNode.asText();
                }
                JsonNode argsNode = funcNode.path("arguments");
                if (!argsNode.isMissingNode() && argsNode.isTextual()) {
                    tcArgsStr += argsNode.asText();
                }
            }
            accumulated.set(index, new LLMCompleteResponse.ToolCall(tcId, tcName, null, tcArgsStr));
        }
    }

    private Map<String, Object> buildRequestBody(LLMRequest request, List<ToolDefinitionForLLM> tools,
                                                  boolean isStream) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", request.getModel());
        body.put("messages", request.getMessages());
        body.put("stream", isStream);
        if (isStream) {
            Map<String, Object> streamOptions = new HashMap<>();
            streamOptions.put("include_usage", true);
            body.put("stream_options", streamOptions);
        }

        if (request.getTemperature() != null) {
            body.put("temperature", request.getTemperature());
        }
        if (request.getMaxTokens() != null) {
            body.put("max_tokens", request.getMaxTokens());
        }
        if (tools != null && !tools.isEmpty()) {
            List<Map<String, Object>> toolsDef = new ArrayList<>();
            for (ToolDefinitionForLLM tool : tools) {
                if (tool.getName() == null || tool.getName().isEmpty() || tool.getName().startsWith("[未启动]")) {
                    continue;
                }
                Map<String, Object> toolDef = new HashMap<>();
                toolDef.put("type", "function");

                Map<String, Object> function = new HashMap<>();
                function.put("name", tool.getName());
                function.put("description", tool.getDescription());

                if (tool.getParameters() != null) {
                    function.put("parameters", tool.getParameters());
                }

                toolDef.put("function", function);
                toolsDef.add(toolDef);
            }
            if (!toolsDef.isEmpty()) {
                body.put("tools", toolsDef);
                body.put("tool_choice", "auto");
                log.info("[LLMClient] 请求包含 {} 个工具定义", toolsDef.size());
            }
        }
        if (request.getExtraParams() != null && !request.getExtraParams().isEmpty()) {
            body.putAll(request.getExtraParams());
        }

        return body;
    }

    private void parseSSEResponse(HttpURLConnection connection, String model, LLMRequest request,
                                   Consumer<LLMChunk> onChunk, AtomicLong promptTokens, AtomicLong completionTokens) throws Exception {
        final List<LLMCompleteResponse.ToolCall> accumulatedToolCalls = new ArrayList<>();
        int lineCount = 0;
        int contentChunks = 0;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                lineCount++;

                // 每100行记录一次进度
                if (lineCount % 100 == 0) {
                    log.debug("[LLMClient] 已处理 {} 行 SSE 数据", lineCount);
                }

                if (line.startsWith("data: ")) {
                    String data = line.substring(6).trim();

                    if (data.equals("[DONE]")) {
                        log.info("[LLMClient] 收到 [DONE] 信号，共处理 {} 行，{} 个内容块", lineCount, contentChunks);
                        LLMChunk doneChunk = LLMChunk.builder()
                            .done(true)
                            .finishReason("stop")
                            .accumulatedToolCalls(new ArrayList<>(accumulatedToolCalls))
                            .build();
                        onChunk.accept(doneChunk);
                        return;
                    }

                    if (!data.isEmpty()) {
                        try {
                            JsonNode jsonNode = objectMapper.readTree(data);
                            JsonNode choices = jsonNode.path("choices");

                            if (choices.isArray() && choices.size() > 0) {
                                JsonNode delta = choices.get(0).path("delta");

                                String content = delta.path("content").asText(null);
                                String reasoningContent = delta.path("reasoning_content").asText(null);

                                if (content == null || content.isEmpty()) {
                                    content = delta.path("text").asText(null);
                                }

                                JsonNode toolCalls = delta.path("tool_calls");
                                JsonNode finishReason = choices.get(0).path("finish_reason");
                                JsonNode usage = jsonNode.path("usage");

                                boolean hasFinishReason = finishReason != null && !finishReason.isMissingNode() && !finishReason.isNull();

                                boolean hasReasoningContent = reasoningContent != null && !reasoningContent.isEmpty();
                                boolean hasContent = content != null && !content.isEmpty();
                                boolean hasToolCalls = toolCalls != null && !toolCalls.isMissingNode() && toolCalls.isArray() && toolCalls.size() > 0;

                                if (hasToolCalls) {
                                    accumulateToolCalls(toolCalls, accumulatedToolCalls);
                                }

                                if (!hasContent && !hasReasoningContent && !hasFinishReason) {

                                }

                                if (hasReasoningContent) {
                                    LLMChunk reasoningChunk = LLMChunk.builder()
                                        .reasoningContent(reasoningContent)
                                        .done(false)
                                        .build();
                                    onChunk.accept(reasoningChunk);
                                }

                                if (hasContent) {
                                    contentChunks++;
                                    LLMChunk contentChunk = LLMChunk.builder()
                                        .content(content)
                                        .done(false)
                                        .build();
                                    onChunk.accept(contentChunk);
                                }

                                if (hasFinishReason) {
                                    long prompt = 0;
                                    long completion = 0;

                                    if (!usage.isMissingNode()) {
                                        prompt = usage.path("prompt_tokens").asLong(0);
                                        completion = usage.path("completion_tokens").asLong(0);
                                        if (prompt == 0) prompt = usage.path("input_tokens").asLong(0);
                                        if (completion == 0) completion = usage.path("output_tokens").asLong(0);
                                        promptTokens.set(prompt);
                                        completionTokens.set(completion);
                                    }

                                    if (!accumulatedToolCalls.isEmpty()) {
                                        log.info("[LLMClient] 流式结束，累积 tool_calls 数量: {}", accumulatedToolCalls.size());
                                        for (LLMCompleteResponse.ToolCall tc : accumulatedToolCalls) {
                                            log.info("[LLMClient] tool_call: name={}, argumentsStr={}", tc.getName(), tc.getArgumentsStr());
                                        }
                                    }

                                    log.info("[LLMClient] 收到 finish_reason={}, 共处理 {} 行，{} 个内容块", finishReason.asText(null), lineCount, contentChunks);
                                    LLMChunk doneChunk = LLMChunk.builder()
                                        .done(true)
                                        .finishReason(finishReason.asText(null))
                                        .usage(!usage.isMissingNode() ? usage : null)
                                        .accumulatedToolCalls(new ArrayList<>(accumulatedToolCalls))
                                        .build();
                                    onChunk.accept(doneChunk);


                                }
                            }
                        } catch (Exception e) {
                            log.error("[LLMClient] 解析流数据失败：{}, 原始数据: {}", e.getMessage(), data.length() > 200 ? data.substring(0, 200) + "..." : data);
                        }
                    }
                }
            }

            // 如果循环正常结束但没有收到 [DONE] 或 finish_reason，手动发送完成信号
            log.warn("[LLMClient] 流正常结束但未收到 [DONE] 信号，共处理 {} 行，{} 个内容块，手动发送完成", lineCount, contentChunks);
            LLMChunk doneChunk = LLMChunk.builder()
                .done(true)
                .finishReason("stop")
                .accumulatedToolCalls(new ArrayList<>(accumulatedToolCalls))
                .build();
            onChunk.accept(doneChunk);
        }
    }

    private LLMCompleteResponse parseJSONResponse(String responseBody, String model) throws Exception {
        JsonNode jsonResponse = objectMapper.readTree(responseBody);
        LLMCompleteResponse.LLMCompleteResponseBuilder builder = LLMCompleteResponse.builder()
            .model(model);

        JsonNode choices = jsonResponse.path("choices");
        if (choices.isArray() && choices.size() > 0) {
            JsonNode messageNode = choices.get(0).path("message");
            String content = messageNode.path("content").asText(null);
            builder.content(content);

            JsonNode toolCallsNode = messageNode.path("tool_calls");
            if (toolCallsNode.isArray() && toolCallsNode.size() > 0) {
                List<LLMCompleteResponse.ToolCall> toolCalls = new ArrayList<>();
                for (JsonNode tcNode : toolCallsNode) {
                    LLMCompleteResponse.ToolCall tc = LLMCompleteResponse.ToolCall.builder()
                        .id(tcNode.path("id").asText(""))
                        .name(tcNode.path("function").path("name").asText(""))
                        .arguments(parseArguments(tcNode.path("function").path("arguments")))
                        .build();
                    toolCalls.add(tc);
                }
                builder.toolCalls(toolCalls);
            }
        }

        JsonNode usageNode = jsonResponse.path("usage");
        if (!usageNode.isMissingNode()) {
            Map<String, Object> usageMap = new HashMap<>();
            addIfNotNull(usageMap, "prompt_tokens", usageNode.path("prompt_tokens"));
            addIfNotNull(usageMap, "completion_tokens", usageNode.path("completion_tokens"));
            addIfNotNull(usageMap, "total_tokens", usageNode.path("total_tokens"));
            builder.usage(usageMap);
        }

        return builder.build();
    }

    private Map<String, Object> parseArguments(JsonNode argsNode) {
        if (argsNode == null || argsNode.isMissingNode()) return new HashMap<>();
        try {
            return objectMapper.convertValue(argsNode, Map.class);
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    private void addIfNotNull(Map<String, Object> map, String key, JsonNode node) {
        if (!node.isNull() && !node.isMissingNode()) {
            map.put(key, node.asInt());
        }
    }

    private String readErrorStream(HttpURLConnection connection) {
        try (InputStream es = connection.getErrorStream();
             BufferedReader reader = es != null ? new BufferedReader(new InputStreamReader(es, StandardCharsets.UTF_8)) : null) {
            if (reader == null) return "";
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private String readFullResponseBody(HttpURLConnection connection) throws Exception {
        try (InputStream is = connection.getInputStream()) {
            byte[] bytes = readAllBytes(is);
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    private static byte[] readAllBytes(InputStream inputStream) throws Exception {
        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        byte[] data = new byte[4096];
        int nRead;
        while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }
        buffer.flush();
        return buffer.toByteArray();
    }
    
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
