package com.superfriend.superfriend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.superfriend.superfriend.dto.McpToolCallResponse;
import com.superfriend.superfriend.dto.McpToolDefinition;
import com.superfriend.superfriend.dto.ServerCapabilities;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;

@Slf4j
public class McpServerProcess {
    
    private final String serverName;
    private final Process process;
    private final int timeout;
    private final ObjectMapper objectMapper;
    
    private final BufferedReader stdOutReader;
    private final BufferedWriter stdInWriter;
    
    private final AtomicLong requestId = new AtomicLong(0);
    private final ConcurrentHashMap<Long, CompletableFuture<JsonNode>> pendingRequests = new ConcurrentHashMap<>();
    private final ExecutorService executor;
    
    private volatile boolean initialized = false;
    private volatile ServerCapabilities capabilities;
    private BiConsumer<String, JsonNode> notificationHandler;
    
    private final List<Map<String, String>> allowedRoots = new ArrayList<>();
    
    public McpServerProcess(String serverName, 
                           Process process, 
                           int timeout,
                           ObjectMapper objectMapper) throws IOException {
        this.serverName = serverName;
        this.process = process;
        this.timeout = timeout;
        this.objectMapper = objectMapper;
        
        this.stdOutReader = new BufferedReader(
            new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)
        );
        this.stdInWriter = new BufferedWriter(
            new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8)
        );
        
        initializeAllowedRoots();
        
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "mcp-reader-" + serverName);
            t.setDaemon(true);
            return t;
        });
        this.executor.submit(this::readResponses);
    }
    
    private void initializeAllowedRoots() {
        allowedRoots.clear();
        
        String userDir = System.getProperty("user.dir").replace("\\", "/");
        String userHome = System.getProperty("user.home").replace("\\", "/");
        
        Map<String, String> root1 = new HashMap<>();
        root1.put("uri", "file://" + userDir);
        root1.put("name", "Working Directory");
        allowedRoots.add(root1);
        
        Map<String, String> root2 = new HashMap<>();
        root2.put("uri", "file://" + userHome);
        root2.put("name", "User Home");
        allowedRoots.add(root2);
        
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            Map<String, String> root3 = new HashMap<>();
            root3.put("uri", "file:///C:/");
            root3.put("name", "C Drive");
            allowedRoots.add(root3);
        }
        
        log.info("[{}] 初始化根目录列表：{}", serverName, allowedRoots);
    }
    
    public void setNotificationHandler(BiConsumer<String, JsonNode> handler) {
        this.notificationHandler = handler;
    }
    
    private void readResponses() {
        String line;
        try {
            log.info("[{}] 开始读取进程输出...", serverName);
            while ((line = stdOutReader.readLine()) != null) {
                log.debug("[{}] 收到原始行：{}", serverName, line);
                if (line.trim().isEmpty()) {
                    continue;
                }

                // 检查是否是非 JSON 的日志输出（如 "Secure MCP Filesystem Server running on stdio"）
                if (!line.startsWith("{")) {
                    log.info("[{}] 进程输出：{}", serverName, line);
                    continue;
                }

                try {
                    JsonNode jsonNode = objectMapper.readTree(line);
                    
                    if (jsonNode.has("method") && "roots/list".equals(jsonNode.get("method").asText())) {
                        handleRootsListRequest(jsonNode);
                        continue;
                    }
                    
                    if (jsonNode.has("method") && !jsonNode.has("id")) {
                        String method = jsonNode.get("method").asText();
                        log.debug("[{}] 收到通知：{}", serverName, method);
                        if (notificationHandler != null) {
                            notificationHandler.accept(method, jsonNode.path("params"));
                        }
                        continue;
                    }
                    
                    if (jsonNode.has("id")) {
                        long id = jsonNode.get("id").asLong();
                        CompletableFuture<JsonNode> future = pendingRequests.remove(id);
                        if (future != null) {
                            future.complete(jsonNode);
                        } else {
                            log.warn("[{}] 收到未知请求ID的响应：{}", serverName, id);
                        }
                    }
                    
                } catch (Exception e) {
                    log.debug("[{}] 跳过非 JSON 输出：{}", serverName, line);
                }
            }
        } catch (IOException e) {
            if (process.isAlive()) {
                log.error("[{}] 读取响应失败：{}", serverName, e.getMessage());
            } else {
                log.info("[{}] MCP Server 进程已终止", serverName);
            }
        }
    }
    
    private void handleRootsListRequest(JsonNode request) throws IOException {
        Map<String, Object> rootsResult = new HashMap<>();
        rootsResult.put("roots", allowedRoots);
        
        Map<String, Object> rootsResponse = new HashMap<>();
        rootsResponse.put("jsonrpc", "2.0");
        rootsResponse.put("id", request.get("id").asLong());
        rootsResponse.put("result", rootsResult);
        
        String jsonResponse = objectMapper.writeValueAsString(rootsResponse);
        log.debug("[{}] 发送 roots/list 响应", serverName);
        
        synchronized (stdInWriter) {
            stdInWriter.write(jsonResponse + "\n");
            stdInWriter.flush();
        }
    }
    
    public void waitForInitialization() throws InterruptedException, TimeoutException {
        try {
            Map<String, Object> params = new HashMap<>();
            
            Map<String, Object> clientInfo = new HashMap<>();
            clientInfo.put("name", "SuperFriend MCP Host");
            clientInfo.put("version", "1.0.0");
            params.put("clientInfo", clientInfo);
            
            Map<String, Object> clientCapabilities = new HashMap<>();
            clientCapabilities.put("roots", new HashMap<>());
            clientCapabilities.put("sampling", new HashMap<>());
            params.put("capabilities", clientCapabilities);
            
            params.put("protocolVersion", "2024-11-05");
            
            log.info("[{}] 发送 initialize 请求...", serverName);
            JsonNode initResponse = sendRequest("initialize", params).get(timeout, TimeUnit.MILLISECONDS);
            
            if (initResponse.has("error")) {
                log.error("[{}] initialize 失败：{}", serverName, initResponse.get("error"));
                throw new RuntimeException("MCP Server initialize 失败: " + initResponse.get("error"));
            }
            
            this.capabilities = ServerCapabilities.fromInitializeResponse(initResponse);
            log.info("[{}] 服务器能力：tools={}, resources={}, prompts={}", 
                serverName, capabilities.isHasTools(), capabilities.isHasResources(), capabilities.isHasPrompts());
            
            sendNotification("notifications/initialized", new HashMap<>());
            log.info("[{}] 发送 initialized 通知", serverName);
            
            initialized = true;
            log.info("[{}] MCP Server 初始化完成", serverName);
            
        } catch (ExecutionException e) {
            throw new RuntimeException("初始化请求失败：" + e.getMessage(), e);
        } catch (TimeoutException e) {
            throw new TimeoutException("MCP Server '" + serverName + "' 初始化超时");
        } catch (IOException e) {
            throw new RuntimeException("发送初始化请求失败：" + e.getMessage(), e);
        }
    }
    
    public CompletableFuture<JsonNode> sendRequest(String method, Map<String, Object> params) throws IOException {
        long id = requestId.incrementAndGet();
        
        Map<String, Object> request = new HashMap<>();
        request.put("jsonrpc", "2.0");
        request.put("id", id);
        request.put("method", method);
        if (params != null && !params.isEmpty()) {
            request.put("params", params);
        }
        
        String jsonRequest = objectMapper.writeValueAsString(request);
        log.debug("[{}] 发送请求：{} (id={})", serverName, method, id);
        
        CompletableFuture<JsonNode> future = new CompletableFuture<>();
        pendingRequests.put(id, future);
        
        synchronized (stdInWriter) {
            stdInWriter.write(jsonRequest + "\n");
            stdInWriter.flush();
        }
        
        return future;
    }
    
    public void sendNotification(String method, Map<String, Object> params) throws IOException {
        Map<String, Object> notification = new HashMap<>();
        notification.put("jsonrpc", "2.0");
        notification.put("method", method);
        if (params != null && !params.isEmpty()) {
            notification.put("params", params);
        }
        
        String jsonNotification = objectMapper.writeValueAsString(notification);
        log.debug("[{}] 发送通知：{}", serverName, method);
        
        synchronized (stdInWriter) {
            stdInWriter.write(jsonNotification + "\n");
            stdInWriter.flush();
        }
    }
    
    public ServerCapabilities getCapabilities() {
        return capabilities;
    }
    
    public boolean isInitialized() {
        return initialized;
    }
    
    public boolean isAlive() {
        return process != null && process.isAlive();
    }
    
    public String getServerName() {
        return serverName;
    }
    
    public List<McpToolDefinition> listTools() {
        if (capabilities != null && !capabilities.isHasTools()) {
            log.warn("[{}] 服务器不支持 tools", serverName);
            return new ArrayList<>();
        }
        
        try {
            JsonNode response = sendRequest("tools/list", new HashMap<>())
                .get(timeout, TimeUnit.MILLISECONDS);
            
            if (response.has("error")) {
                log.error("[{}] tools/list 失败：{}", serverName, response.get("error"));
                return new ArrayList<>();
            }
            
            List<McpToolDefinition> tools = new ArrayList<>();
            JsonNode toolsNode = response.path("result").path("tools");
            
            if (toolsNode.isArray()) {
                for (JsonNode toolNode : toolsNode) {
                    McpToolDefinition tool = parseTool(toolNode);
                    if (tool != null) {
                        tool.setServerName(serverName);
                        tools.add(tool);
                    }
                }
            }
            
            log.info("[{}] 获取到 {} 个工具", serverName, tools.size());
            return tools;
            
        } catch (Exception e) {
            log.error("[{}] 获取工具列表失败：{}", serverName, e.getMessage(), e);
            return new ArrayList<>();
        }
    }
    
    private McpToolDefinition parseTool(JsonNode toolNode) {
        String toolName = toolNode.path("name").asText("");
        if (toolName.isEmpty()) {
            return null;
        }
        
        McpToolDefinition tool = new McpToolDefinition();
        tool.setName(toolName);
        tool.setDescription(toolNode.path("description").asText(""));
        
        JsonNode schemaNode = toolNode.path("inputSchema");
        if (!schemaNode.isMissingNode()) {
            McpToolDefinition.JsonSchema schema = new McpToolDefinition.JsonSchema();
            schema.setType(schemaNode.path("type").asText("object"));
            
            if (schemaNode.has("properties")) {
                schema.setProperties(objectMapper.convertValue(
                    schemaNode.get("properties"), Map.class
                ));
            }
            
            JsonNode requiredNode = schemaNode.path("required");
            if (requiredNode.isArray()) {
                List<String> required = new ArrayList<>();
                for (JsonNode req : requiredNode) {
                    required.add(req.asText());
                }
                schema.setRequired(required.toArray(new String[0]));
            }
            
            tool.setInputSchema(schema);
        }
        
        return tool;
    }
    
    public McpToolCallResponse callTool(String toolName, Map<String, Object> arguments) {
        McpToolCallResponse result = new McpToolCallResponse();
        result.setServerName(serverName);
        result.setToolName(toolName);
        
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("name", toolName);
            params.put("arguments", arguments != null ? arguments : new HashMap<>());
            
            JsonNode response = sendRequest("tools/call", params)
                .get(timeout, TimeUnit.MILLISECONDS);
            
            if (response.has("error")) {
                result.setSuccess(false);
                result.setError(response.path("error").path("message").asText("未知错误"));
                return result;
            }
            
            JsonNode resultNode = response.path("result");
            JsonNode contentNode = resultNode.path("content");
            
            if (contentNode.isMissingNode() || contentNode.isNull()) {
                result.setSuccess(false);
                result.setError("工具调用返回空内容");
                return result;
            }
            
            result.setSuccess(true);
            List<McpToolCallResponse.ContentItem> content = new ArrayList<>();
            
            if (contentNode.isArray()) {
                for (JsonNode itemNode : contentNode) {
                    McpToolCallResponse.ContentItem item = new McpToolCallResponse.ContentItem();
                    String type = itemNode.path("type").asText("text");
                    item.setType(type);
                    
                    if ("text".equals(type)) {
                        item.setText(itemNode.path("text").asText());
                    } else {
                        item.setData(itemNode);
                    }
                    content.add(item);
                }
            }
            
            result.setContent(content);
            log.debug("[{}] 工具调用成功：{}", serverName, toolName);
            return result;
            
        } catch (Exception e) {
            log.error("[{}] 调用工具 {} 失败：{}", serverName, toolName, e.getMessage(), e);
            result.setSuccess(false);
            result.setError(e.getMessage());
            return result;
        }
    }
    
    public void stop() {
        log.info("[{}] 正在停止 MCP Server...", serverName);
        
        try {
            if (stdInWriter != null) {
                stdInWriter.close();
            }
        } catch (Exception e) {
            log.debug("[{}] 关闭 stdin 失败：{}", serverName, e.getMessage());
        }
        
        if (process != null && process.isAlive()) {
            process.destroyForcibly();
            try {
                if (!process.waitFor(5, TimeUnit.SECONDS)) {
                    log.warn("[{}] 进程未能在5秒内终止", serverName);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        if (executor != null) {
            executor.shutdownNow();
        }
        
        pendingRequests.clear();
        log.info("[{}] MCP Server 已停止", serverName);
    }
}
