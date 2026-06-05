package com.superfriend.superfriend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.superfriend.superfriend.agent.skill.ScriptExecutor;
import com.superfriend.superfriend.dto.*;
import com.superfriend.superfriend.entity.AIModelConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@Slf4j
@Service
public class McpHostService {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private McpServiceLauncher mcpServiceLauncher;

    @Autowired
    private ContextCompressionService compressionService;

    @Autowired
    @Lazy
    private TaskPlannerService taskPlannerService;

    @Autowired
    @Lazy
    private AIModelConfigService modelConfigService;

    @Autowired
    @Lazy
    private LLMClient llmClient;

    @Autowired
    @Lazy
    private AgentSessionManager sessionManager;

    @Autowired
    @Lazy
    private PermissionService permissionService;

    @Autowired
    @Lazy
    private ApprovalService approvalService;

    @Autowired
    @Lazy
    private KnowledgeGraphService knowledgeGraphService;

    @Autowired
    @Lazy
    private SkillRecommendationService skillRecommendationService;

    @Autowired
    @Lazy
    private com.superfriend.superfriend.agent.skill.SkillRegistry skillRegistry;

    @Autowired
    @Lazy
    private SkillEnvironmentManager skillEnvironmentManager;

    @Autowired
    @Lazy
    private SkillService skillService;

    @Autowired
    @Lazy
    private ContextMangerService contextMangerService;

    @Autowired
    @Lazy
    private AgentExecutionService agentExecutionService;

    @Autowired
    @Lazy
    private CostTrackingService costTrackingService;

    @Autowired
    @Lazy
    private ObservabilityService observabilityService;

    @Autowired
    @Lazy
<<<<<<< HEAD
=======
    private UserProfileService userProfileService;

    @Autowired
    @Lazy
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
    private OrioSearchService orioSearchService;

    @Autowired
    @Lazy
    private MessageContentBuilder messageContentBuilder;

    @Autowired
    @Lazy
    private ModelCapabilityService modelCapabilityService;

    @Autowired
    @Lazy
    private SystemContextBuilder systemContextBuilder;

<<<<<<< HEAD
    @Autowired
    @Lazy
    private SessionFileIndexService sessionFileIndexService;

=======
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
    private final ConcurrentHashMap<String, McpServerProcess> runningServers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> serverLastUsedTime = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<McpToolDefinition>> toolsCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ServerCapabilities> serverCapabilities = new ConcurrentHashMap<>();
<<<<<<< HEAD
    private final ConcurrentHashMap<String, CompletableFuture<List<McpToolDefinition>>> toolLoadingFutures = new ConcurrentHashMap<>();
=======
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
    private final ConcurrentHashMap<String, Boolean> selectedServers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ToolStats> toolStats = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ErrorStats> errorStats = new ConcurrentHashMap<>();
    
    private static final String CONFIG_LOCATION = "mcpserverconfig/mcp-servers-config.json";
    private static final String SMALL_CONFIG_LOCATION = "mcpserverconfig/mcp-servers-small-config.json";
    @Value("${mcp.react.max-iterations:80}")
    private int maxIterations;

    @Value("${mcp.react.max-consecutive-errors:8}")
    private int maxConsecutiveErrors;

    @Value("${mcp.react.step-timeout-ms:120000}")
    private long stepTimeoutMs;

    @Value("${mcp.react.total-timeout-ms:600000}")
    private long totalTimeoutMs;

    @Value("${mcp.react.default-model:deepseek-chat}")
    private String defaultModel;
    
    @Value("${mcp.host.idle-timeout-ms:3000000}")
    private long mcpIdleTimeoutMs;

    @Value("${mcp.host.enable-idle-check:true}")
    private boolean mcpEnableIdleCheck;

    @Value("${mcp.config-path:}")
    private String mcpConfigPath;

<<<<<<< HEAD
    /**
     * MCP 配置加载优先级（从高到低）：
     * 1. 环境变量 MCP_CONFIG_PATH 指定的配置文件
     * 2. 项目目录下的 mcp-servers-small-config.json（开发模式）
     * 3. 用户目录 ~/.superfriend/mcp-servers-config.json
     * 4. 部署目录 /opt/superfriend/config/mcp-servers-config.json
     * 5. classpath 内置配置 mcp-servers-config.json
     */
    public McpServersFile loadConfig() throws IOException {
        // 1. 最高优先级：环境变量指定的配置文件路径
        if (mcpConfigPath != null && !mcpConfigPath.isEmpty()) {
            File envConfigFile = new File(mcpConfigPath);
            if (envConfigFile.exists()) {
                log.info("【MCP配置】优先级1 - 从环境变量加载: {}", envConfigFile.getAbsolutePath());
                String content = readFileToString(envConfigFile);
                return objectMapper.readValue(replacePathPlaceholders(content), McpServersFile.class);
            }
            log.warn("【MCP配置】环境变量指定的配置文件不存在: {}", mcpConfigPath);
        }

        // 2. 开发模式：项目目录下的精简配置文件
        File smallConfigFile = new File("src/main/resources/mcpserverconfig/mcp-servers-small-config.json");
        if (smallConfigFile.exists()) {
            log.info("【MCP配置】优先级2 - 从项目目录加载: {}", smallConfigFile.getAbsolutePath());
            String content = readFileToString(smallConfigFile);
            return objectMapper.readValue(replacePathPlaceholders(content), McpServersFile.class);
        }

        // 3. 用户目录下的配置文件
        String userHome = System.getProperty("user.home");
        File userConfigFile = new File(userHome, ".superfriend/mcp-servers-config.json");
        if (userConfigFile.exists()) {
            log.info("【MCP配置】优先级3 - 从用户目录加载: {}", userConfigFile.getAbsolutePath());
            String content = readFileToString(userConfigFile);
            return objectMapper.readValue(replacePathPlaceholders(content), McpServersFile.class);
        }

        // 4. 部署模式：默认部署目录下的配置文件
        File deployConfigFile = new File("/opt/superfriend/config/mcp-servers-config.json");
        if (deployConfigFile.exists()) {
            log.info("【MCP配置】优先级4 - 从部署目录加载: {}", deployConfigFile.getAbsolutePath());
            String content = readFileToString(deployConfigFile);
            return objectMapper.readValue(replacePathPlaceholders(content), McpServersFile.class);
        }

        // 5. classpath 内置配置（兜底）
        log.info("【MCP配置】优先级5 - 从 classpath 加载: {}", CONFIG_LOCATION);
        Resource resource = new ClassPathResource(CONFIG_LOCATION);

        if (!resource.exists()) {
            log.warn("【MCP配置】所有路径均未找到配置文件，使用默认配置");
=======
    public McpServersFile loadConfig() throws IOException {
        // 1. 开发模式：项目目录下的配置文件
        File smallConfigFile = new File("src/main/resources/mcpserverconfig/mcp-servers-small-config.json");

        if (smallConfigFile.exists()) {
            log.info("从项目目录加载精简 MCP 配置：{}", smallConfigFile.getAbsolutePath());
            String content = readFileToString(smallConfigFile);
            McpServersFile config = objectMapper.readValue(replacePathPlaceholders(content), McpServersFile.class);
            return config;
        }

        // 2. 部署模式：环境变量指定的配置文件路径
        if (mcpConfigPath != null && !mcpConfigPath.isEmpty()) {
            File envConfigFile = new File(mcpConfigPath);
            if (envConfigFile.exists()) {
                log.info("从环境变量指定路径加载 MCP 配置：{}", envConfigFile.getAbsolutePath());
                String content = readFileToString(envConfigFile);
                McpServersFile config = objectMapper.readValue(replacePathPlaceholders(content), McpServersFile.class);
                return config;
            }
        }

        // 3. 部署模式：默认部署目录下的配置文件
        File deployConfigFile = new File("/opt/superfriend/config/mcp-servers-config.json");
        if (deployConfigFile.exists()) {
            log.info("从部署目录加载 MCP 配置：{}", deployConfigFile.getAbsolutePath());
            String content = readFileToString(deployConfigFile);
            McpServersFile config = objectMapper.readValue(replacePathPlaceholders(content), McpServersFile.class);
            return config;
        }

        // 4. 用户目录下的配置文件
        String userHome = System.getProperty("user.home");
        File externalConfigFile = new File(userHome, ".superfriend/mcp-servers-config.json");

        if (externalConfigFile.exists()) {
            log.info("从外部文件加载 MCP 配置：{}", externalConfigFile.getAbsolutePath());
            String content = readFileToString(externalConfigFile);
            McpServersFile config = objectMapper.readValue(replacePathPlaceholders(content), McpServersFile.class);
            return config;
        }

        // 5. classpath 内置配置
        log.info("从 classpath 加载 MCP 配置文件：{}", CONFIG_LOCATION);
        Resource resource = new ClassPathResource(CONFIG_LOCATION);

        if (!resource.exists()) {
            log.warn("MCP 配置文件不存在：{}", CONFIG_LOCATION);
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
            return createDefaultConfig();
        }

        try (InputStream inputStream = resource.getInputStream()) {
            String content = readInputStreamToString(inputStream);
            McpServersFile config = objectMapper.readValue(replacePathPlaceholders(content), McpServersFile.class);
<<<<<<< HEAD
            log.info("【MCP配置】加载成功");
=======
            log.info("MCP 配置文件加载成功");
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
            return config;
        }
    }

    /**
     * Java 8 兼容：读取文件内容为字符串
     */
    private String readFileToString(File file) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(file), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            return sb.toString();
        }
    }

    /**
     * Java 8 兼容：读取 InputStream 内容为字符串
     */
    private String readInputStreamToString(InputStream inputStream) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            return sb.toString();
        }
    }

    /**
     * 替换配置文件中的路径占位符
     */
    private String replacePathPlaceholders(String content) {
        String userHome = System.getProperty("user.home");
        return content.replace("${user.home}", userHome);
    }

    public void saveConfig(McpServersFile config) throws IOException {
        String userHome = System.getProperty("user.home");
        File configDir = new File(userHome, ".harmonynotes");
        if (!configDir.exists()) {
            configDir.mkdirs();
        }
        
        File configFile = new File(configDir, "mcp-servers-config.json");
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(configFile, config);
        log.info("MCP 配置文件已保存到：{}", configFile.getAbsolutePath());
    }
    
    public void initializeServers() {
        try {
            loadSelectedServersFromConfig();
            log.info("MCP Host 初始化完成。请在页面中选择要使用的服务器，当前已选中：{}", selectedServers.keySet());
        } catch (Exception e) {
            log.error("MCP Host 初始化失败", e);
        }
    }

    /**
     * MCP 服务启动由 McpServiceLauncher 处理
     * 包括：解析配置、检查本地资源、下载依赖、准备启动命令
     */

    public void startServer(String serverName, McpServerConfig config) {
        Process process = null;
        McpServerProcess serverProcess = null;

        try {
            log.info("[MCP] 正在启动 MCP Server: {}", serverName);

            // 使用 McpServiceLauncher 准备配置（检查本地资源、下载依赖等）
            McpServerConfig resolvedConfig = mcpServiceLauncher.prepareConfig(serverName, config);

            // 确保沙箱目录存在（filesystem 和 bash-sandbox 需要）
            ensureSandboxDirectories(resolvedConfig);

            ProcessBuilder processBuilder = new ProcessBuilder();
            List<String> command = new ArrayList<>();

            String osName = System.getProperty("os.name").toLowerCase();
            if (osName.contains("win")) {
                command.add("cmd");
                command.add("/c");
                command.add(resolvedConfig.getCommand());
                if (resolvedConfig.getArgs() != null) {
                    for (String arg : resolvedConfig.getArgs()) {
                        command.add(arg);
                    }
                }
            } else {
                command.add(resolvedConfig.getCommand());
                if (resolvedConfig.getArgs() != null) {
                    command.addAll(Arrays.asList(resolvedConfig.getArgs()));
                }
            }

            // 打印完整命令用于调试
            log.info("[MCP] [{}] 执行命令: {}", serverName, String.join(" ", command));
            log.info("[MCP] [{}] 工作目录: {}", serverName, System.getProperty("user.dir"));

            processBuilder.command(command);

            Map<String, String> env = processBuilder.environment();
            if (resolvedConfig.getEnv() != null) {
                env.putAll(resolvedConfig.getEnv());
            }

            processBuilder.directory(new File(System.getProperty("user.dir")));
            processBuilder.redirectErrorStream(true);

            process = processBuilder.start();


            int timeout = resolvedConfig.getTimeout() > 0 ? resolvedConfig.getTimeout() : 60000;
            serverProcess = new McpServerProcess(
                serverName,
                process,
                timeout,
                objectMapper
            );

            serverProcess.setNotificationHandler((method, params) -> {
                handleServerNotification(serverName, method, params);
            });

            // 先注册到 runningServers，确保进程可追踪
            runningServers.put(serverName, serverProcess);
            updateLastUsedTime(serverName);

            serverProcess.waitForInitialization();

            ServerCapabilities caps = serverProcess.getCapabilities();
            if (caps != null) {
                serverCapabilities.put(serverName, caps);
                log.info("[MCP] [{}] 服务器能力已缓存", serverName);
            }

            log.info("[MCP] Server '{}' 启动成功", serverName);

            preloadTools(serverName, serverProcess);

        } catch (Exception e) {
            log.error("[MCP] 启动 MCP Server '{}' 失败：{}", serverName, e.getMessage(), e);

            // 清理失败的进程
            if (serverProcess != null) {
                try {
                    serverProcess.stop();
                } catch (Exception stopEx) {
                    log.warn("[MCP] 停止失败的 serverProcess 时出错: {}", stopEx.getMessage());
                }
            } else if (process != null) {
                // 如果 serverProcess 未创建，直接销毁进程
                try {
                    process.destroyForcibly();
                    if (!process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) {
                        log.warn("[MCP] 进程未在5秒内终止: {}", serverName);
                    }
                } catch (Exception destroyEx) {
                    log.warn("[MCP] 销毁进程失败: {}", destroyEx.getMessage());
                }
            }

            runningServers.remove(serverName);
            serverLastUsedTime.remove(serverName);
        }
    }

    /**
     * 确保沙箱目录存在
     * 为 filesystem 和 bash-sandbox 服务创建必要的工作目录
     */
    private void ensureSandboxDirectories(McpServerConfig config) {
        String tmpDir = System.getProperty("java.io.tmpdir");
        File sandboxDir = new File(tmpDir, "superfriend_sandbox");
        File sharedDir = new File(sandboxDir, "shared");

        if (!sharedDir.exists()) {
            boolean created = sharedDir.mkdirs();
            if (created) {
                log.info("[MCP] 创建沙箱共享目录: {}", sharedDir.getAbsolutePath());
            } else {
                log.warn("[MCP] 无法创建沙箱共享目录: {}", sharedDir.getAbsolutePath());
            }
        }

        // 检查环境变量中的沙箱目录配置
        Map<String, String> env = config.getEnv();
        if (env != null) {
            String sandboxRoot = env.get("SANDBOX_ROOT_DIR");
            String sandboxRootWin = env.get("SANDBOX_ROOT_DIR_WIN");

            // 根据操作系统创建对应的沙箱目录
            String osName = System.getProperty("os.name").toLowerCase();
            String dirToCreate = osName.contains("win") ? sandboxRootWin : sandboxRoot;

            if (dirToCreate != null && !dirToCreate.contains("${")) {
                File customSandbox = new File(dirToCreate);
                if (!customSandbox.exists()) {
                    boolean created = customSandbox.mkdirs();
                    if (created) {
                        log.info("[MCP] 创建沙箱目录: {}", customSandbox.getAbsolutePath());
                    }
                }
            }
        }
    }

    private void handleServerNotification(String serverName, String method, JsonNode params) {
        log.info("[{}] 收到通知：{}", serverName, method);
        
        switch (method) {
            case "notifications/tools/list_changed":
                refreshToolsCache(serverName);
                break;
            case "notifications/resources/list_changed":
                log.info("[{}] 资源列表已变化", serverName);
                break;
            case "notifications/prompts/list_changed":
                log.info("[{}] 提示词列表已变化", serverName);
                break;
            case "notifications/resources/updated":
                log.info("[{}] 资源已更新：{}", serverName, params.path("uri").asText());
                break;
            case "notifications/message":
                log.debug("[{}] 日志消息：{}", serverName, params.path("data").asText());
                break;
            default:
                log.debug("[{}] 未知通知类型：{}", serverName, method);
        }
    }
    
    private void refreshToolsCache(String serverName) {
        McpServerProcess server = runningServers.get(serverName);
        if (server != null && server.isAlive()) {
<<<<<<< HEAD
            // 复用 preloadTools 的去重逻辑，避免与预加载竞争
            preloadTools(serverName, server);
=======
            new Thread(() -> {
                try {
                    List<McpToolDefinition> tools = server.listTools();
                    if (tools != null && !tools.isEmpty()) {
                        for (McpToolDefinition tool : tools) {
                            tool.setName(serverName + "__" + tool.getName());
                        }
                        toolsCache.put(serverName, tools);
                        log.info("[{}] 工具缓存已刷新，共 {} 个工具", serverName, tools.size());
                    }
                } catch (Exception e) {
                    log.error("[{}] 刷新工具缓存失败：{}", serverName, e.getMessage());
                }
            }).start();
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
        }
    }
    
    private void preloadTools(String serverName, McpServerProcess serverProcess) {
<<<<<<< HEAD
        // 如果已有加载任务在运行，不重复启动
        CompletableFuture<List<McpToolDefinition>> existing = toolLoadingFutures.get(serverName);
        if (existing != null && !existing.isDone()) {
            log.debug("[{}] 工具加载任务已在运行，跳过", serverName);
            return;
        }

        CompletableFuture<List<McpToolDefinition>> future = CompletableFuture.supplyAsync(() -> {
=======
        new Thread(() -> {
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
            try {
                Thread.sleep(500);
                List<McpToolDefinition> tools = serverProcess.listTools();
                if (tools != null && !tools.isEmpty()) {
                    for (McpToolDefinition tool : tools) {
                        tool.setName(serverName + "__" + tool.getName());
                    }
                    toolsCache.put(serverName, tools);
                    log.info("[{}] 预加载工具列表完成，共 {} 个工具", serverName, tools.size());
<<<<<<< HEAD
                    return tools;
                }
                return Collections.emptyList();
            } catch (Exception e) {
                log.error("[{}] 预加载工具列表失败：{}", serverName, e.getMessage());
                return Collections.emptyList();
            }
        });

        toolLoadingFutures.put(serverName, future);
=======
                }
            } catch (Exception e) {
                log.error("[{}] 预加载工具列表失败：{}", serverName, e.getMessage());
            }
        }).start();
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
    }
    
    public McpServerProcess getOrCreateServer(String serverName) {
        McpServerProcess server = runningServers.get(serverName);
        if (server != null && server.isAlive()) {
            updateLastUsedTime(serverName);
            return server;
        }
        
        try {
            log.info("MCP Server '{}' 未运行，正在按需启动...", serverName);
            
            McpServersFile config = loadConfig();
            McpServerConfig serverConfig = config.getMcpServers().get(serverName);
            
            if (serverConfig == null) {
                log.error("配置文件中未找到 MCP Server: {}", serverName);
                return null;
            }
            
            if (serverConfig.isDisabled()) {
                log.warn("MCP Server '{}' 已被禁用", serverName);
                return null;
            }
            
            startServer(serverName, serverConfig);
            return runningServers.get(serverName);
            
        } catch (IOException e) {
            log.error("启动 MCP Server '{}' 失败：{}", serverName, e.getMessage(), e);
            return null;
        }
    }
    
    private void updateLastUsedTime(String serverName) {
        serverLastUsedTime.put(serverName, System.currentTimeMillis());
    }
    
    public void checkAndCloseIdleServers() {
        if (!mcpEnableIdleCheck) {
            log.debug("空闲检测已禁用，跳过服务器回收");
            return;
        }
        
        long now = System.currentTimeMillis();
        
        for (String serverName : new ArrayList<>(runningServers.keySet())) {
            Long lastUsed = serverLastUsedTime.get(serverName);
            if (lastUsed == null) {
                continue;
            }
            
            long idleTime = now - lastUsed;
            if (idleTime > mcpIdleTimeoutMs) {
                log.info("MCP Server '{}' 已空闲 {} 毫秒，正在关闭...", serverName, idleTime);
                stopServer(serverName);
                serverLastUsedTime.remove(serverName);
            }
        }
    }
    
    public void stopServer(String serverName) {
        McpServerProcess process = runningServers.remove(serverName);
        if (process != null) {
            process.stop();
            log.info("MCP Server '{}' 已停止", serverName);
        }
        
        toolsCache.remove(serverName);
        toolsCacheTimestamp.remove(serverName);
        serverCapabilities.remove(serverName);
<<<<<<< HEAD

        // 取消正在进行的加载任务
        CompletableFuture<List<McpToolDefinition>> loadingFuture = toolLoadingFutures.remove(serverName);
        if (loadingFuture != null && !loadingFuture.isDone()) {
            loadingFuture.cancel(true);
        }
=======
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
    }
    
    public void stopAllServers() {
        log.info("[MCP] 正在停止所有 MCP Servers，当前运行数量: {}", runningServers.size());
        for (String serverName : new ArrayList<>(runningServers.keySet())) {
            stopServer(serverName);
        }
        toolsCache.clear();
<<<<<<< HEAD
        toolsCacheTimestamp.clear();
        serverCapabilities.clear();
        // 取消所有加载任务
        for (CompletableFuture<List<McpToolDefinition>> future : toolLoadingFutures.values()) {
            if (!future.isDone()) {
                future.cancel(true);
            }
        }
        toolLoadingFutures.clear();
=======
        serverCapabilities.clear();
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
        log.info("[MCP] 所有 MCP Servers 已停止");
    }

    public String startSelectedServers() {
        try {
            File smallConfigFile = new File("src/main/resources/mcpserverconfig/mcp-servers-small-config.json");
            
            if (!smallConfigFile.exists()) {
                Resource smallConfigResource = new ClassPathResource(SMALL_CONFIG_LOCATION);
                if (!smallConfigResource.exists()) {
                    return "精简配置文件不存在，请先在管理页面选择并保存服务器";
                }
                try (InputStream inputStream = smallConfigResource.getInputStream()) {
                    McpServersFile selectedConfig = objectMapper.readValue(inputStream, McpServersFile.class);
                    return doStartServers(selectedConfig);
                }
            }
            
            McpServersFile selectedConfig = objectMapper.readValue(smallConfigFile, McpServersFile.class);
            return doStartServers(selectedConfig);
            
        } catch (Exception e) {
            log.error("启动选中服务器失败：{}", e.getMessage(), e);
            return "启动失败：" + e.getMessage();
        }
    }
    
    private String doStartServers(McpServersFile selectedConfig) {
        if (selectedConfig == null || selectedConfig.getMcpServers() == null || selectedConfig.getMcpServers().isEmpty()) {
            return "精简配置中没有选中任何服务器，请先在管理页面选择并保存服务器";
        }

        loadSelectedServersFromConfig();

        Set<String> alreadyRunning = new HashSet<>(runningServers.keySet());
        List<String> startedNames = new ArrayList<>();
        List<String> skippedNames = new ArrayList<>();
        List<String> failedNames = new ArrayList<>();

        for (Map.Entry<String, McpServerConfig> entry : selectedConfig.getMcpServers().entrySet()) {
            String serverName = entry.getKey();
            McpServerConfig serverConfig = entry.getValue();

            if (serverConfig.isDisabled()) {
                skippedNames.add(serverName + "(已禁用)");
                continue;
            }

            if (alreadyRunning.contains(serverName)) {
                skippedNames.add(serverName);
                continue;
            }

            try {
                startServer(serverName, serverConfig);
                startedNames.add(serverName);
            } catch (Exception e) {
                log.error("启动选中服务器 '{}' 失败：{}", serverName, e.getMessage());
                failedNames.add(serverName + "(" + e.getMessage() + ")");
            }
        }

        StringBuilder result = new StringBuilder();
        if (!startedNames.isEmpty()) {
            result.append("已启动 ").append(startedNames.size()).append(" 个服务器：").append(String.join(", ", startedNames));
        }
        if (!skippedNames.isEmpty()) {
            if (result.length() > 0) result.append("\n");
            result.append("跳过 ").append(skippedNames.size()).append(" 个（已在运行）：").append(String.join(", ", skippedNames));
        }
        if (!failedNames.isEmpty()) {
            if (result.length() > 0) result.append("\n");
            result.append("失败 ").append(failedNames.size()).append(" 个：").append(String.join(", ", failedNames));
        }

        if (result.length() == 0) {
            return "没有需要启动的服务器";
        }

        return result.toString();
    }

    @PreDestroy
    public void destroy() {
        log.info("应用关闭，正在清理 MCP 资源...");
        stopAllServers();
        log.info("MCP 资源清理完成");
    }
    
    public List<McpToolDefinition> listTools() {
        return listToolsInternal(runningServers.keySet());
    }
    
    public List<McpToolDefinition> listSelectedTools() {
        Set<String> activeServerNames = new HashSet<>();
        for (String serverName : selectedServers.keySet()) {
            if (runningServers.containsKey(serverName)) {
                activeServerNames.add(serverName);
            }
        }

<<<<<<< HEAD
        // Skills tools FIRST - load_skill and run_skill_script MUST be together at the top
        List<McpToolDefinition> skillsTools = new ArrayList<>();
        skillsTools.add(createGetSkillDetailsTool());      // 1. load_skill
        skillsTools.add(createRunSkillScriptTool());       // 2. run_skill_script (MUST follow load_skill)
        skillsTools.add(createReadSkillResourceTool());    // 3. read_skill_resource
        skillsTools.add(createSetupSkillEnvironmentTool());
        skillsTools.add(createSendFileTool());

        // Web tools
        skillsTools.add(createWebExtractTool());
        skillsTools.add(createWebSearchTool());

        // File tools
        skillsTools.add(createListFilesTool());
        skillsTools.add(createReadFileTool());
        skillsTools.add(createSearchFileTool());
        skillsTools.add(createGetFileInfoTool());
        skillsTools.add(createGetFilePathTool());

=======
        // Skills tools FIRST - these are prioritized for the model
        List<McpToolDefinition> skillsTools = new ArrayList<>();
        skillsTools.add(createGetSkillDetailsTool());
        skillsTools.add(createReadSkillResourceTool());
        skillsTools.add(createRunSkillScriptTool());
        skillsTools.add(createSetupSkillEnvironmentTool());
        skillsTools.add(createSendFileTool());

        // Web tools (OrioSearch) - HIGHEST PRIORITY for content extraction
        skillsTools.add(0, createWebExtractTool());  // 最优先
        skillsTools.add(createWebSearchTool());

>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
        // Then MCP tools
        List<McpToolDefinition> mcpTools = listToolsInternal(activeServerNames);

        // Combine: Skills tools first, then MCP tools
        List<McpToolDefinition> allTools = new ArrayList<>(skillsTools);
        allTools.addAll(mcpTools);

        return allTools;
    }

    public List<McpToolDefinition> listSelectedToolsWithSkills(Long userId) {
        Set<String> activeServerNames = new HashSet<>();
        for (String serverName : selectedServers.keySet()) {
            if (runningServers.containsKey(serverName)) {
                activeServerNames.add(serverName);
            }
        }

<<<<<<< HEAD
        // Skills tools FIRST - load_skill and run_skill_script MUST be together at the top
        List<McpToolDefinition> skillsTools = new ArrayList<>();
        skillsTools.add(createGetSkillDetailsTool());      // 1. load_skill
        skillsTools.add(createRunSkillScriptTool());       // 2. run_skill_script (MUST follow load_skill)
        skillsTools.add(createReadSkillResourceTool());    // 3. read_skill_resource
        skillsTools.add(createSetupSkillEnvironmentTool());
        skillsTools.add(createSendFileTool());

        // Web tools
        skillsTools.add(createWebExtractTool());
        skillsTools.add(createWebSearchTool());

        // File tools
        skillsTools.add(createListFilesTool());
        skillsTools.add(createReadFileTool());
        skillsTools.add(createSearchFileTool());
        skillsTools.add(createGetFileInfoTool());
        skillsTools.add(createGetFilePathTool());

=======
        // Skills tools FIRST - these are prioritized for the model
        List<McpToolDefinition> skillsTools = new ArrayList<>();
        skillsTools.add(createGetSkillDetailsTool());
        skillsTools.add(createReadSkillResourceTool());
        skillsTools.add(createRunSkillScriptTool());
        skillsTools.add(createSetupSkillEnvironmentTool());
        skillsTools.add(createSendFileTool());

        // Web tools (OrioSearch) - HIGHEST PRIORITY for content extraction
        skillsTools.add(0, createWebExtractTool());  // 最优先
        skillsTools.add(createWebSearchTool());

>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
        // Then MCP tools
        List<McpToolDefinition> mcpTools = listToolsInternal(activeServerNames);

        // Combine: Skills tools first, then MCP tools
        List<McpToolDefinition> allTools = new ArrayList<>(skillsTools);
        allTools.addAll(mcpTools);

        log.info("加载 {} 个 Skills 工具 (优先) + {} 个 MCP 工具 (userId={})", skillsTools.size(), mcpTools.size(), userId);

        return allTools;
    }

    private McpToolDefinition createGetSkillDetailsTool() {
        McpToolDefinition tool = new McpToolDefinition();
        tool.setName("load_skill");
        tool.setDescription(
            "Load the full SKILL.md body with detailed instructions for a specific skill. " +
            "使用场景：当系统提示词中的 Skills 列表有匹配的技能时调用。 " +
            "返回内容：技能描述、环境状态、可用脚本列表、参考文件列表、详细指令。 " +
<<<<<<< HEAD
            "⚡ CRITICAL: After calling load_skill, you MUST immediately call run_skill_script to execute the actual task. " +
            "DO NOT use bash-sandbox or write_file for document generation.");
=======
            "后续步骤：使用 read_skill_resource 读取参考文档，或 run_skill_script 执行脚本。");
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2

        McpToolDefinition.JsonSchema schema = new McpToolDefinition.JsonSchema();
        schema.setType("object");

        Map<String, Object> properties = new HashMap<>();
        Map<String, Object> skillNameProp = new HashMap<>();
        skillNameProp.put("type", "string");
        skillNameProp.put("description", "The name of the skill to load (e.g., 'minimax-docx')");
        properties.put("skill_name", skillNameProp);

        schema.setProperties(properties);
        schema.setRequired(new String[]{"skill_name"});
        tool.setInputSchema(schema);

        return tool;
    }

    private McpToolDefinition createReadSkillResourceTool() {
        McpToolDefinition tool = new McpToolDefinition();
        tool.setName("read_skill_resource");
        tool.setDescription(
            "Read supplementary files from a skill (reference docs, templates, examples). " +
            "使用场景：当 SKILL.md 引用 'references/' 或 'assets/' 目录下的文件时调用。 " +
            "常见路径示例：'references/design-system.md', 'references/scenario_a_create.md', 'assets/template.json'");

        McpToolDefinition.JsonSchema schema = new McpToolDefinition.JsonSchema();
        schema.setType("object");

        Map<String, Object> properties = new HashMap<>();
        Map<String, Object> skillNameProp = new HashMap<>();
        skillNameProp.put("type", "string");
        skillNameProp.put("description", "The name of the skill");
        properties.put("skill_name", skillNameProp);
        Map<String, Object> resourcePathProp = new HashMap<>();
        resourcePathProp.put("type", "string");
        resourcePathProp.put("description", "The path to the resource file (e.g., 'references/example.md')");
        properties.put("resource_path", resourcePathProp);

        schema.setProperties(properties);
        schema.setRequired(new String[]{"skill_name", "resource_path"});
        tool.setInputSchema(schema);

        return tool;
    }
    
    private McpToolDefinition createRunSkillScriptTool() {
        McpToolDefinition tool = new McpToolDefinition();
        tool.setName("run_skill_script");
        tool.setDescription(
<<<<<<< HEAD
            "⚡ CRITICAL: Execute a skill script to perform the actual task. " +
            "This is the SECOND step after load_skill. " +
            "You MUST call this tool after loading a skill to generate files or perform actions. " +
            "DO NOT directly call bash-sandbox__execute or bash-sandbox__write_file for skill tasks - use run_skill_script instead. " +
            "(run_skill_script internally uses bash-sandbox with proper environment setup)");
=======
            "Execute a script within a skill. CRITICAL: 必须在 load_skill 之后调用。 " +
            "脚本在沙箱中执行，输出文件自动发送给用户。");
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2

        McpToolDefinition.JsonSchema schema = new McpToolDefinition.JsonSchema();
        schema.setType("object");

        Map<String, Object> properties = new HashMap<>();
        Map<String, Object> skillNameProp = new HashMap<>();
        skillNameProp.put("type", "string");
        skillNameProp.put("description", "技能名称，与 load_skill 使用相同值 (e.g., 'minimax-docx')");
        properties.put("skill_name", skillNameProp);
        Map<String, Object> scriptNameProp = new HashMap<>();
        scriptNameProp.put("description",
            "脚本路径，使用 load_skill 返回的 Available Scripts 中的格式。 " +
            "格式: 'scripts/xxx.py' 或 'scripts/xxx.sh'。 " +
            "示例: 'scripts/fill_write.py' (来自 load_skill 返回的 Available Scripts 列表)");
        scriptNameProp.put("type", "string");
        properties.put("script_name", scriptNameProp);

        Map<String, Object> parametersProp = new HashMap<>();
        parametersProp.put("type", "object");
        parametersProp.put("description",
            "传递给脚本的参数，会以 JSON 文件形式传递。 " +
            "脚本通过 --param-file 参数接收。 " +
            "示例: {\"input\": \"form.pdf\", \"output\": \"result.pdf\"}");
        properties.put("parameters", parametersProp);

        Map<String, Object> sessionIdProp = new HashMap<>();
        sessionIdProp.put("type", "string");
        sessionIdProp.put("description", "会话 ID (可选，自动填充)");
        properties.put("session_id", sessionIdProp);

        schema.setProperties(properties);
        schema.setRequired(new String[]{"skill_name", "script_name"});
        tool.setInputSchema(schema);

        return tool;
    }

    private McpToolDefinition createSetupSkillEnvironmentTool() {
        McpToolDefinition tool = new McpToolDefinition();
        tool.setName("setup_skill_environment");
        tool.setDescription("Setup and verify the environment for a skill. " +
            "This checks if required dependencies (dotnet, python packages, etc.) are installed and installs them if missing. " +
            "CRITICAL: Call this BEFORE attempting to run any skill scripts, especially for the first time. " +
            "Returns the environment status showing which requirements are satisfied.");

        McpToolDefinition.JsonSchema schema = new McpToolDefinition.JsonSchema();
        schema.setType("object");

        Map<String, Object> properties = new HashMap<>();
        Map<String, Object> skillNameProp = new HashMap<>();
        skillNameProp.put("type", "string");
        skillNameProp.put("description", "The name of the skill to setup environment for (e.g., 'minimax-docx')");
        properties.put("skill_name", skillNameProp);

        schema.setProperties(properties);
        schema.setRequired(new String[]{"skill_name"});
        tool.setInputSchema(schema);

        return tool;
    }

    private McpToolDefinition createSendFileTool() {
        McpToolDefinition tool = new McpToolDefinition();
        tool.setName("send_file");
        tool.setDescription("发送文件给用户。用于将沙箱中生成的文件（如图片、PDF、文档）发送给前端显示。" +
            "使用场景：bash-sandbox 执行命令生成文件后，调用此工具发送文件。" +
            "注意：文件路径必须是共享目录中的绝对路径，或使用 bash-sandbox__export_file 导出后的路径。");

        McpToolDefinition.JsonSchema schema = new McpToolDefinition.JsonSchema();
        schema.setType("object");

        Map<String, Object> properties = new HashMap<>();

        Map<String, Object> filePathProp = new HashMap<>();
        filePathProp.put("type", "string");
        filePathProp.put("description", "文件路径（共享目录中的绝对路径）");
        properties.put("file_path", filePathProp);

        Map<String, Object> sessionIdProp = new HashMap<>();
        sessionIdProp.put("type", "string");
        sessionIdProp.put("description", "会话 ID（可选）");
        properties.put("session_id", sessionIdProp);

        schema.setProperties(properties);
        schema.setRequired(new String[]{"file_path"});
        tool.setInputSchema(schema);

        return tool;
    }

    private McpToolDefinition createWebSearchTool() {
        McpToolDefinition tool = new McpToolDefinition();
        tool.setName("web_search");
        tool.setDescription("搜索网络信息。返回搜索结果（标题、URL、摘要）和AI答案。适用于查找信息、获取新闻、了解话题。【重要】搜索时请参考系统提示词中的当前时间，使用当前年份（如2026年），不要使用过时的年份。");

        McpToolDefinition.JsonSchema schema = new McpToolDefinition.JsonSchema();
        schema.setType("object");

        Map<String, Object> properties = new HashMap<>();

        // query 参数
        Map<String, Object> queryProp = new HashMap<>();
        queryProp.put("type", "string");
        queryProp.put("description", "搜索关键词或问题。请使用当前年份，例如\"2026年招聘信息\"而非\"2024年\"");
        properties.put("query", queryProp);

        // max_results 参数（可选）
        Map<String, Object> maxResultsProp = new HashMap<>();
        maxResultsProp.put("type", "integer");
        maxResultsProp.put("description", "最大结果数，默认5");
        maxResultsProp.put("default", 5);
        properties.put("max_results", maxResultsProp);

        schema.setProperties(properties);
        schema.setRequired(new String[]{"query"});
        tool.setInputSchema(schema);

        return tool;
    }

    private McpToolDefinition createWebExtractTool() {
        McpToolDefinition tool = new McpToolDefinition();
        tool.setName("web_extract");
        tool.setDescription("【首选网页抓取工具】从URL提取网页内容。比 fetch/puppeteer 更强大：自动处理反爬虫、JS渲染、验证码，返回干净文本。支持单个url或批量urls。当需要获取网页内容时，请优先使用此工具而非 fetch。【重要】支持并行抓取多个URL，速度更快。当有多个URL需要抓取时，请一次性传入urls数组批量处理，比逐个调用快数倍。");

        McpToolDefinition.JsonSchema schema = new McpToolDefinition.JsonSchema();
        schema.setType("object");

        Map<String, Object> properties = new HashMap<>();

        // url 参数（单个URL，优先推荐）
        Map<String, Object> urlProp = new HashMap<>();
        urlProp.put("type", "string");
        urlProp.put("description", "单个URL地址。推荐使用此参数，简单直接。");
        properties.put("url", urlProp);

        // urls 参数（批量，推荐用于多URL场景）
        Map<String, Object> urlsProp = new HashMap<>();
        urlsProp.put("type", "array");
        urlsProp.put("description", "URL列表，支持批量并行提取多个页面。推荐：当有多个URL时使用此参数，并行抓取速度比逐个调用快数倍。");
        Map<String, Object> itemsProp = new HashMap<>();
        itemsProp.put("type", "string");
        urlsProp.put("items", itemsProp);
        properties.put("urls", urlsProp);

        // max_length 参数（可选）
        Map<String, Object> maxLengthProp = new HashMap<>();
        maxLengthProp.put("type", "integer");
        maxLengthProp.put("description", "每个URL内容的最大字符数，默认10000。设为0表示不限制。");
        maxLengthProp.put("default", 10000);
        properties.put("max_length", maxLengthProp);

        schema.setProperties(properties);
        // url 和 urls 二选一，不设为必填，由逻辑校验
        tool.setInputSchema(schema);

        return tool;
    }
<<<<<<< HEAD

    // ==================== 文件工具定义 ====================

    private McpToolDefinition createListFilesTool() {
        McpToolDefinition tool = new McpToolDefinition();
        tool.setName("list_files");
        tool.setDescription("列出当前会话中用户上传的所有文件。返回文件名、类型、大小、摘要等信息。使用场景：需要了解用户上传了哪些文件时调用。");

        McpToolDefinition.JsonSchema schema = new McpToolDefinition.JsonSchema();
        schema.setType("object");

        Map<String, Object> properties = new HashMap<>();

        Map<String, Object> sessionIdProp = new HashMap<>();
        sessionIdProp.put("type", "string");
        sessionIdProp.put("description", "会话 ID（可选，系统会自动注入）");
        properties.put("session_id", sessionIdProp);

        schema.setProperties(properties);
        schema.setRequired(new String[]{});

        tool.setInputSchema(schema);
        return tool;
    }

    private McpToolDefinition createReadFileTool() {
        McpToolDefinition tool = new McpToolDefinition();
        tool.setName("read_file");
        tool.setDescription("读取指定文件的内容。支持分段读取大文件，可指定起始行和结束行。使用场景：需要查看文件具体内容时调用。");

        McpToolDefinition.JsonSchema schema = new McpToolDefinition.JsonSchema();
        schema.setType("object");

        Map<String, Object> properties = new HashMap<>();

        Map<String, Object> fileIdProp = new HashMap<>();
        fileIdProp.put("type", "string");
        fileIdProp.put("description", "文件 ID（从 list_files 获取）");
        properties.put("file_id", fileIdProp);

        Map<String, Object> sessionIdProp = new HashMap<>();
        sessionIdProp.put("type", "string");
        sessionIdProp.put("description", "会话 ID（可选，系统会自动注入）");
        properties.put("session_id", sessionIdProp);

        Map<String, Object> startLineProp = new HashMap<>();
        startLineProp.put("type", "integer");
        startLineProp.put("description", "起始行号（可选，从 1 开始）");
        properties.put("start_line", startLineProp);

        Map<String, Object> endLineProp = new HashMap<>();
        endLineProp.put("type", "integer");
        endLineProp.put("description", "结束行号（可选）");
        properties.put("end_line", endLineProp);

        schema.setProperties(properties);
        schema.setRequired(new String[]{"file_id"});

        tool.setInputSchema(schema);
        return tool;
    }

    private McpToolDefinition createSearchFileTool() {
        McpToolDefinition tool = new McpToolDefinition();
        tool.setName("search_file");
        tool.setDescription("在文件中搜索关键词，返回匹配的行。使用场景：需要在文件中查找特定内容时调用。");

        McpToolDefinition.JsonSchema schema = new McpToolDefinition.JsonSchema();
        schema.setType("object");

        Map<String, Object> properties = new HashMap<>();

        Map<String, Object> keywordProp = new HashMap<>();
        keywordProp.put("type", "string");
        keywordProp.put("description", "搜索关键词");
        properties.put("keyword", keywordProp);

        Map<String, Object> fileIdProp = new HashMap<>();
        fileIdProp.put("type", "string");
        fileIdProp.put("description", "文件 ID（可选，不指定则搜索所有文件）");
        properties.put("file_id", fileIdProp);

        Map<String, Object> sessionIdProp = new HashMap<>();
        sessionIdProp.put("type", "string");
        sessionIdProp.put("description", "会话 ID（可选，系统会自动注入）");
        properties.put("session_id", sessionIdProp);

        schema.setProperties(properties);
        schema.setRequired(new String[]{"keyword"});

        tool.setInputSchema(schema);
        return tool;
    }

    private McpToolDefinition createGetFileInfoTool() {
        McpToolDefinition tool = new McpToolDefinition();
        tool.setName("get_file_info");
        tool.setDescription("获取指定文件的详细信息，包括文件名、类型、大小、行数/页数、摘要等。使用场景：需要了解文件详情时调用。");

        McpToolDefinition.JsonSchema schema = new McpToolDefinition.JsonSchema();
        schema.setType("object");

        Map<String, Object> properties = new HashMap<>();

        Map<String, Object> fileIdProp = new HashMap<>();
        fileIdProp.put("type", "string");
        fileIdProp.put("description", "文件 ID");
        properties.put("file_id", fileIdProp);

        Map<String, Object> sessionIdProp = new HashMap<>();
        sessionIdProp.put("type", "string");
        sessionIdProp.put("description", "会话 ID（可选，系统会自动注入）");
        properties.put("session_id", sessionIdProp);

        schema.setProperties(properties);
        schema.setRequired(new String[]{"file_id"});

        tool.setInputSchema(schema);
        return tool;
    }

    private McpToolDefinition createGetFilePathTool() {
        McpToolDefinition tool = new McpToolDefinition();
        tool.setName("get_file_path");
        tool.setDescription("获取用户上传文件的本地绝对路径。当你需要将用户上传的文件传给 run_skill_script 的 input 参数时，必须先调用此工具获取文件的本地路径。例如：使用 minimax-docx skill 处理用户上传的 docx 文件前，需要先获取其本地路径。");

        McpToolDefinition.JsonSchema schema = new McpToolDefinition.JsonSchema();
        schema.setType("object");

        Map<String, Object> properties = new HashMap<>();

        Map<String, Object> fileIdProp = new HashMap<>();
        fileIdProp.put("type", "string");
        fileIdProp.put("description", "文件 ID（从 list_files 获取）");
        properties.put("file_id", fileIdProp);

        Map<String, Object> sessionIdProp = new HashMap<>();
        sessionIdProp.put("type", "string");
        sessionIdProp.put("description", "会话 ID（可选，系统会自动注入）");
        properties.put("session_id", sessionIdProp);

        schema.setProperties(properties);
        schema.setRequired(new String[]{"file_id"});

        tool.setInputSchema(schema);
        return tool;
    }

=======
    
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
    private final ConcurrentHashMap<String, Long> toolsCacheTimestamp = new ConcurrentHashMap<>();
    
    private List<McpToolDefinition> listToolsInternal(Set<String> targetServers) {
        List<McpToolDefinition> allTools = new ArrayList<>();
        
        McpServersFile configFile = null;
        try {
            configFile = loadConfig();
        } catch (IOException e) {
            log.warn("加载配置文件失败：{}", e.getMessage());
        }
        
        for (Map.Entry<String, McpServerProcess> entry : runningServers.entrySet()) {
            String serverName = entry.getKey();
            McpServerProcess server = entry.getValue();
            
            if (!targetServers.contains(serverName)) {
                continue;
            }
            
            if (server != null && server.isAlive()) {
                List<McpToolDefinition> tools = toolsCache.get(serverName);
<<<<<<< HEAD

                if (tools == null) {
                    // 先检查是否有正在进行的加载任务
                    CompletableFuture<List<McpToolDefinition>> loadingFuture = toolLoadingFutures.get(serverName);
                    if (loadingFuture != null && !loadingFuture.isDone()) {
                        try {
                            tools = loadingFuture.get(10, java.util.concurrent.TimeUnit.SECONDS);
                        } catch (Exception e) {
                            log.warn("[{}] 等待工具加载超时，尝试直接获取: {}", serverName, e.getMessage());
                        }
                    }

                    // 仍然为空则直接请求
                    if (tools == null || tools.isEmpty()) {
                        tools = server.listTools();
                        if (tools != null && !tools.isEmpty()) {
                            for (McpToolDefinition tool : tools) {
                                tool.setServerName(serverName);
                                tool.setName(serverName + "__" + tool.getName());
                            }
                            toolsCache.put(serverName, tools);
                        }
                    }
                }

=======
                
                if (tools == null) {
                    tools = server.listTools();
                    if (tools != null && !tools.isEmpty()) {
                        for (McpToolDefinition tool : tools) {
                            tool.setServerName(serverName);
                            tool.setName(serverName + "__" + tool.getName());
                        }
                        toolsCache.put(serverName, tools);
                    }
                }
                
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
                if (tools != null) {
                    allTools.addAll(tools);
                }
            }
        }
        
        log.info("可用工具总数：{}", allTools.size());
        return allTools;
    }

    /**
     * 调用工具（支持 serverName__toolName 格式的工具名）- 异步版本
     */
    public CompletableFuture<McpToolCallResponse> callToolAsync(String toolName, Map<String, Object> arguments) {
        return CompletableFuture.supplyAsync(() -> callToolWithRetry(null, toolName, arguments, 0));
    }

    /**
     * 调用工具（支持 serverName__toolName 格式的工具名）
     */
    public McpToolCallResponse callTool(String toolName, Map<String, Object> arguments) {
        return callToolWithRetry(null, toolName, arguments, 0);
    }

    public McpToolCallResponse callTool(String serverName, String toolName, Map<String, Object> arguments) {
        return callToolWithRetry(serverName, toolName, arguments, 0);
    }

    public McpToolCallResponse callTool(String serverName, String toolName, Map<String, Object> arguments,
                                          Consumer<AIChatResponse> onResponse) {
        if ("run_skill_script".equals(toolName)) {
            return handleRunSkillScript(arguments, onResponse);
        }

        if ("send_file".equals(toolName)) {
            return handleSendFile(arguments, onResponse);
        }

        // 处理 bash-sandbox execute，自动检测并发送文件
        // 支持两种格式: toolName="execute" + serverName="bash-sandbox" 或 toolName="bash-sandbox__execute"
        String actualServerName = serverName;
        String actualToolName = toolName;
        if (toolName != null && toolName.contains("__")) {
            String[] parts = toolName.split("__", 2);
            actualServerName = parts[0];
            actualToolName = parts[1];
        }

        if ("execute".equals(actualToolName) && "bash-sandbox".equals(actualServerName)) {
            return handleBashSandboxExecute(arguments, onResponse);
        }

        return callToolWithRetry(serverName, toolName, arguments, 0);
    }
    
<<<<<<< HEAD
    private McpToolCallResponse callToolWithRetry(String serverName, String toolName,
                                                   Map<String, Object> arguments, int attempt) {
        // 【修复】限制最大重试次数为1，避免与上层 ErrorRecoveryManager 的重试叠加
        // 策略性重试（换工具、换参数等）由 ErrorRecoveryManager 统一管理
        final int MAX_RETRIES = 1;
=======
    private McpToolCallResponse callToolWithRetry(String serverName, String toolName, 
                                                  Map<String, Object> arguments, int attempt) {
        final int MAX_RETRIES = 3;
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
        long startTime = System.currentTimeMillis();
        
        if ("load_skill".equals(toolName)) {
            return handleGetSkillDetails(arguments);
        }
        
        if ("read_skill_resource".equals(toolName)) {
            return handleReadSkillResource(arguments);
        }
        
        if ("run_skill_script".equals(toolName)) {
            return handleRunSkillScript(arguments, null);  // null for onResponse in retry path
        }

        if ("setup_skill_environment".equals(toolName)) {
            return handleSetupSkillEnvironment(arguments);
        }

        if ("send_file".equals(toolName)) {
            return handleSendFile(arguments, null);
        }

        if ("web_search".equals(toolName)) {
            return handleWebSearch(arguments);
        }

        if ("web_extract".equals(toolName)) {
            return handleWebExtract(arguments);
        }
<<<<<<< HEAD

        // File tools
        if ("list_files".equals(toolName)) {
            return handleListFiles(arguments);
        }

        if ("read_file".equals(toolName)) {
            return handleReadFile(arguments);
        }

        if ("search_file".equals(toolName)) {
            return handleSearchFile(arguments);
        }

        if ("get_file_info".equals(toolName)) {
            return handleGetFileInfo(arguments);
        }

        if ("get_file_path".equals(toolName)) {
            return handleGetFilePath(arguments);
        }

=======
        
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
        if (toolName.contains("__")) {
            String[] parts = toolName.split("__", 2);
            if (parts.length == 2) {
                serverName = parts[0];
                toolName = parts[1];
            }
        } else if (toolName.contains(".")) {
            String[] parts = toolName.split("\\.", 2);
            if (parts.length == 2) {
                serverName = parts[0];
                toolName = parts[1];
            }
        } else {
            boolean found = false;
            for (String server : runningServers.keySet()) {
                List<McpToolDefinition> tools = toolsCache.get(server);
<<<<<<< HEAD

                // 缓存为空时，等待加载完成
                if (tools == null) {
                    CompletableFuture<List<McpToolDefinition>> loadingFuture = toolLoadingFutures.get(server);
                    if (loadingFuture != null && !loadingFuture.isDone()) {
                        try {
                            tools = loadingFuture.get(10, java.util.concurrent.TimeUnit.SECONDS);
                        } catch (Exception e) {
                            log.debug("[{}] 等待工具加载超时: {}", server, e.getMessage());
                        }
                    }
                }

=======
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
                if (tools != null) {
                    for (McpToolDefinition tool : tools) {
                        String fullName = tool.getName();
                        if (fullName.endsWith("__" + toolName)) {
                            serverName = server;
                            found = true;
                            break;
                        }
                    }
                }
                if (found) break;
            }
        }
        
        McpServerProcess server = getOrCreateServer(serverName);
        if (server == null) {
            McpToolCallResponse error = new McpToolCallResponse();
            error.setSuccess(false);
            error.setError("MCP Server '" + serverName + "' 无法启动");
            return error;
        }
        
        if (!toolExists(serverName, toolName)) {
            McpToolCallResponse error = new McpToolCallResponse();
            error.setSuccess(false);
            error.setError("工具 '" + serverName + "__" + toolName + "' 不存在");
            return error;
        }
        
        updateLastUsedTime(serverName);

        // 智能参数增强：针对爬取工具自动优化参数
        arguments = enhanceCrawlParameters(serverName, toolName, arguments);

        McpToolCallResponse result = server.callTool(toolName, arguments);
        long executionTime = System.currentTimeMillis() - startTime;
        
        String fullToolName = serverName + "__" + toolName;
        ToolStats stats = toolStats.computeIfAbsent(fullToolName, k -> new ToolStats());
        stats.recordCall(result.isSuccess(), executionTime);
        
        if (!result.isSuccess() && attempt < MAX_RETRIES) {
            String errorType = classifyError(result.getError());
            ErrorStats errorStats = this.errorStats.computeIfAbsent(fullToolName, k -> new ErrorStats());
            errorStats.recordError(errorType);
            errorStats.recordRetry();
            
            if (shouldRetry(errorType)) {
                long delay = calculateRetryDelay(errorType, attempt);
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                
                McpToolCallResponse retryResult = callToolWithRetry(serverName, toolName, arguments, attempt + 1);
                if (retryResult.isSuccess()) {
                    errorStats.recordRecovery();
                }
                return retryResult;
            }
        }
        
        return result;
    }
    
    private boolean toolExists(String serverName, String toolName) {
        List<McpToolDefinition> tools = toolsCache.get(serverName);
<<<<<<< HEAD

        // 缓存为空时，等待正在进行的加载任务完成
        if (tools == null || tools.isEmpty()) {
            CompletableFuture<List<McpToolDefinition>> loadingFuture = toolLoadingFutures.get(serverName);
            if (loadingFuture != null && !loadingFuture.isDone()) {
                try {
                    log.debug("[{}] 工具正在加载中，等待完成...", serverName);
                    tools = loadingFuture.get(10, java.util.concurrent.TimeUnit.SECONDS);
                    if (tools != null && !tools.isEmpty()) {
                        toolsCache.put(serverName, tools);
                    }
                } catch (Exception e) {
                    log.warn("[{}] 等待工具加载超时或失败: {}", serverName, e.getMessage());
                }
            }
        }

        if (tools == null || tools.isEmpty()) {
            return false;
        }

        String expectedFullName = serverName + "__" + toolName;
        return tools.stream().anyMatch(tool ->
            tool.getName().equals(expectedFullName) ||
=======
        if (tools == null || tools.isEmpty()) {
            return false;
        }
        
        String expectedFullName = serverName + "__" + toolName;
        return tools.stream().anyMatch(tool -> 
            tool.getName().equals(expectedFullName) || 
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
            tool.getName().endsWith("__" + toolName)
        );
    }
    
    private String classifyError(String errorMessage) {
        if (errorMessage == null) return "UNKNOWN";
        String lowerMessage = errorMessage.toLowerCase();

        // 超时错误 - 支持中英文
        if (lowerMessage.contains("timeout") || lowerMessage.contains("超时") ||
            lowerMessage.contains("timed out") || lowerMessage.contains("time out")) {
            return "TIMEOUT";
        }

        // 网络错误 - 支持中英文
        if (lowerMessage.contains("network") || lowerMessage.contains("connection") ||
            lowerMessage.contains("网络") || lowerMessage.contains("连接") ||
            lowerMessage.contains("connect") || lowerMessage.contains("socket") ||
            lowerMessage.contains("eof") || lowerMessage.contains("reset")) {
            return "NETWORK";
        }

        // 认证错误 - 支持中英文
        if (lowerMessage.contains("auth") || lowerMessage.contains("unauthorized") ||
            lowerMessage.contains("认证") || lowerMessage.contains("授权") ||
            lowerMessage.contains("forbidden") || lowerMessage.contains("permission") ||
            lowerMessage.contains("401") || lowerMessage.contains("403")) {
            return "AUTHENTICATION";
        }

        // 资源未找到
        if (lowerMessage.contains("not found") || lowerMessage.contains("404") ||
            lowerMessage.contains("未找到") || lowerMessage.contains("不存在") ||
            lowerMessage.contains("no such") || lowerMessage.contains("does not exist")) {
            return "NOT_FOUND";
        }

        // 速率限制 - 支持中英文
        if (lowerMessage.contains("rate limit") || lowerMessage.contains("429") ||
            lowerMessage.contains("too many") || lowerMessage.contains("频率") ||
            lowerMessage.contains("限制") || lowerMessage.contains("throttl")) {
            return "RATE_LIMIT";
        }

        // 服务器错误
        if (lowerMessage.contains("500") || lowerMessage.contains("503") ||
            lowerMessage.contains("502") || lowerMessage.contains("server error") ||
            lowerMessage.contains("服务器错误") || lowerMessage.contains("internal error")) {
            return "SERVER_ERROR";
        }

        // 参数错误
        if (lowerMessage.contains("invalid") || lowerMessage.contains("参数") ||
            lowerMessage.contains("argument") || lowerMessage.contains("param") ||
            lowerMessage.contains("bad request") || lowerMessage.contains("400")) {
            return "INVALID_REQUEST";
        }

        // 资源耗尽
        if (lowerMessage.contains("out of memory") || lowerMessage.contains("内存") ||
            lowerMessage.contains("资源") || lowerMessage.contains("capacity") ||
            lowerMessage.contains("limit exceeded")) {
            return "RESOURCE_EXHAUSTED";
        }

        return "UNKNOWN";
    }

    private boolean shouldRetry(String errorType) {
        return "TIMEOUT".equals(errorType) || "NETWORK".equals(errorType) ||
               "SERVER_ERROR".equals(errorType) || "RATE_LIMIT".equals(errorType) ||
               "RESOURCE_EXHAUSTED".equals(errorType);
    }

    private long calculateRetryDelay(String errorType, int attempt) {
        long baseDelay = 1000;
        if ("RATE_LIMIT".equals(errorType)) {
            baseDelay = 5000; // 速率限制使用更长的基础延迟
        } else if ("RESOURCE_EXHAUSTED".equals(errorType)) {
            baseDelay = 3000; // 资源耗尽使用中等延迟
        }
        // 指数退避，最大延迟 30 秒
        return Math.min(baseDelay * (long) Math.pow(2, attempt), 30000);
    }
    
    private McpToolCallResponse handleGetSkillDetails(Map<String, Object> arguments) {
        McpToolCallResponse response = new McpToolCallResponse();
        
        String skillName = arguments != null ? (String) arguments.get("skill_name") : null;
        if (skillName == null) skillName = (String) arguments.get("skillName");
        
        if (skillName == null || skillName.isEmpty()) {
            response.setSuccess(false);
            response.setError("缺少参数：skill_name");
            return response;
        }
        
        String skillContent = skillRegistry.getSkillFullContent(skillName);
        
        if (skillContent == null) {
            response.setSuccess(false);
            response.setError("未找到技能: " + skillName);
            return response;
        }
        
        response.setSuccess(true);
        McpToolCallResponse.ContentItem contentItem = new McpToolCallResponse.ContentItem();
        contentItem.setType("text");
        contentItem.setText(skillContent);
        response.setContent(Collections.singletonList(contentItem));
        
        return response;
    }
    
    private McpToolCallResponse handleReadSkillResource(Map<String, Object> arguments) {
        McpToolCallResponse response = new McpToolCallResponse();
        
        String skillName = arguments != null ? (String) arguments.get("skill_name") : null;
        String resourcePath = arguments != null ? (String) arguments.get("resource_path") : null;
        Long userId = null;
        if (arguments != null && arguments.get("user_id") != null) {
            Object userIdObj = arguments.get("user_id");
            if (userIdObj instanceof Number) {
                userId = ((Number) userIdObj).longValue();
            } else if (userIdObj instanceof String) {
                userId = Long.parseLong((String) userIdObj);
            }
        }
        
        if (skillName == null || skillName.isEmpty()) {
            response.setSuccess(false);
            response.setError("缺少参数：skill_name");
            return response;
        }
        
        if (resourcePath == null || resourcePath.isEmpty()) {
            response.setSuccess(false);
            response.setError("缺少参数：resource_path");
            return response;
        }
        
        String resourceContent = skillRegistry.getSkillResource(skillName, resourcePath, userId);
        
        if (resourceContent == null) {
            response.setSuccess(false);
            response.setError("未找到资源文件: " + skillName + " / " + resourcePath);
            return response;
        }
        
        if (resourceContent.startsWith("[Error]")) {
            response.setSuccess(false);
            response.setError(resourceContent);
            return response;
        }
        
        response.setSuccess(true);
        McpToolCallResponse.ContentItem contentItem = new McpToolCallResponse.ContentItem();
        contentItem.setType("text");
        contentItem.setText(resourceContent);
        response.setContent(Collections.singletonList(contentItem));
        
        return response;
    }
    
    private McpToolCallResponse handleRunSkillScript(Map<String, Object> arguments, Consumer<AIChatResponse> onResponse) {
        McpToolCallResponse response = new McpToolCallResponse();

        String skillName = arguments != null ? (String) arguments.get("skill_name") : null;
        String scriptName = arguments != null ? (String) arguments.get("script_name") : null;
<<<<<<< HEAD
        Map<String, Object> parameters = null;
        if (arguments != null && arguments.get("parameters") != null) {
            Object paramsObj = arguments.get("parameters");
            if (paramsObj instanceof Map) {
                parameters = (Map<String, Object>) paramsObj;
            } else if (paramsObj instanceof String) {
                try {
                    parameters = objectMapper.readValue((String) paramsObj, Map.class);
                    log.info("handleRunSkillScript: 将字符串参数解析为Map成功");
                } catch (Exception e) {
                    log.warn("handleRunSkillScript: 参数字符串解析为Map失败: {}", e.getMessage());
                    response.setSuccess(false);
                    response.setError("parameters 参数格式错误：无法将字符串解析为JSON对象。请传递JSON对象而非字符串，例如：{\"type\": \"academic\"} 而非 \"{\\\"type\\\": \\\"academic\\\"}\"");
                    return response;
                }
            } else {
                log.warn("handleRunSkillScript: parameters 类型不支持: {}", paramsObj.getClass().getName());
            }
        }
=======
        Map<String, Object> parameters = arguments != null ? (Map<String, Object>) arguments.get("parameters") : null;
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
        String sessionId = arguments != null ? (String) arguments.get("session_id") : null;

        log.info("handleRunSkillScript: skillName={}, scriptName={}, onResponse={}, sessionId={}",
            skillName, scriptName, onResponse != null, sessionId);

        if (skillName == null || skillName.isEmpty()) {
            response.setSuccess(false);
            response.setError("缺少参数：skill_name");
            return response;
        }

        if (scriptName == null || scriptName.isEmpty()) {
            response.setSuccess(false);
            response.setError("缺少参数：script_name");
            return response;
        }

        try {
<<<<<<< HEAD
            // 传入 sessionId 确保会话一致性（Agent ExecutionContext 与沙箱会话关联）
            ScriptExecutor.ScriptExecutionResult result = skillRegistry.executeSkillScript(
                skillName, scriptName, parameters != null ? parameters : new HashMap<>(), sessionId);
=======
            ScriptExecutor.ScriptExecutionResult result = skillRegistry.executeSkillScript(
                skillName, scriptName, parameters != null ? parameters : new HashMap<>());
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2

            if (result.isSuccess()) {
                response.setSuccess(true);

<<<<<<< HEAD
                String outputStr = result.getOutput();
                log.info("handleRunSkillScript: 脚本执行成功, output长度={}, outputFiles={}",
                    outputStr != null ? outputStr.length() : 0,
                    result.getOutputFiles() != null ? result.getOutputFiles().size() : 0);

                // 优先使用 SkillScriptRunner 检测到的输出文件列表
                List<String> detectedFiles = result.getOutputFiles();

                // 如果检测到的文件列表为空，尝试从 output JSON 中解析
                if ((detectedFiles == null || detectedFiles.isEmpty()) && outputStr != null && onResponse != null) {
                    try {
=======
                // 检查是否有文件输出
                String outputStr = result.getOutput();
                log.info("handleRunSkillScript: 脚本执行成功, output长度={}, output前200字符={}",
                    outputStr != null ? outputStr.length() : 0,
                    outputStr != null && outputStr.length() > 200 ? outputStr.substring(0, 200) : outputStr);

                if (outputStr != null && onResponse != null) {
                    try {
                        // 尝试解析 output 中的文件路径
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
                        Map<String, Object> outputMap = objectMapper.readValue(outputStr, Map.class);
                        String filePath = null;

                        if (outputMap.containsKey("outputFile") || outputMap.containsKey("output_path") || outputMap.containsKey("filePath")) {
                            filePath = (String) (outputMap.get("outputFile") != null ? outputMap.get("outputFile") :
                                        outputMap.get("output_path") != null ? outputMap.get("output_path") :
                                        outputMap.get("filePath"));
                        }

<<<<<<< HEAD
                        if (filePath != null) {
                            detectedFiles = new ArrayList<>();
                            detectedFiles.add(filePath);
                        }
                    } catch (Exception parseEx) {
                        log.debug("handleRunSkillScript: output不是JSON格式: {}", parseEx.getMessage());
                    }
                }

                // 发送所有检测到的文件
                if (detectedFiles != null && !detectedFiles.isEmpty() && onResponse != null) {
                    for (String filePath : detectedFiles) {
                        sendFileToUser(filePath, skillName, scriptName, sessionId, onResponse);
                    }

                    // 注意：不立即清理输出目录，文件已注册到 FileLifecycleManager
                    // FileLifecycleManager 会在 24 小时后自动清理文件
                    // 原始输出目录中的文件已被 sendFileToUser 复制到新路径
                    log.info("文件已发送并注册到生命周期管理器，将在 24 小时后自动清理");
=======
                        log.info("handleRunSkillScript: 解析JSON成功, outputFile={}, output_path={}, filePath={}, 最终filePath={}",
                            outputMap.get("outputFile"), outputMap.get("output_path"), outputMap.get("filePath"), filePath);

                        // 如果 output 本身是文件路径（检测常见文件扩展名）
                        if (filePath == null && outputStr.contains(".") && !outputStr.contains("\n") && outputStr.length() < 500) {
                            // 简单判断是否可能是文件路径
                            if (outputStr.matches(".*\\.[a-zA-Z0-9]{1,10}$")) {
                                filePath = outputStr.trim();
                            }
                        }

                        // 如果检测到文件，发送 SSE 并注册文件生命周期
                        if (filePath != null) {
                            File file = new File(filePath);
                            log.info("handleRunSkillScript: 检测到文件路径={}, 文件存在={}, 是文件={}",
                                filePath, file.exists(), file.isFile());

                            if (file.exists() && file.isFile()) {
                                // 重命名文件，添加时间戳后缀避免冲突
                                File renamedFile = copyFileWithTimestamp(file, sessionId);
                                String filePathToSend = renamedFile.getAbsolutePath();
                                String fileName = renamedFile.getName();
                                String fileType = getFileType(fileName);
                                String mimeType = detectMimeType(fileName);

                                // 先注册文件到生命周期管理器
                                fileLifecycleManager.registerFile(filePathToSend, "skill:" + skillName, sessionId);

                                try {
                                    String base64Content = Base64.getEncoder().encodeToString(
                                        java.nio.file.Files.readAllBytes(renamedFile.toPath()));
                                    String fileId = "skill-" + skillName + "-" + scriptName + "-" + System.currentTimeMillis();

                                    AIChatResponse fileResponse = new AIChatResponse();
                                    fileResponse.setType("file");
                                    fileResponse.setSessionId(sessionId);
                                    fileResponse.setResourceId(fileId);
                                    fileResponse.setFileName(fileName);
                                    fileResponse.setFileType(fileType);
                                    fileResponse.setFileSize(renamedFile.length());
                                    fileResponse.setContent("data:" + mimeType + ";base64," + base64Content);

                                    // 发送 SSE
                                    onResponse.accept(fileResponse);

                                    // 发送成功，标记文件为已发送
                                    fileLifecycleManager.markAsSent(filePathToSend);

                                    log.info("Skill {} 发送文件 SSE 给前端: {} ({} bytes, type={}, mime={})",
                                        skillName, fileName, renamedFile.length(), fileType, mimeType);

                                } catch (Exception sendEx) {
                                    // SSE 发送失败，记录错误但保留文件注册（等待后续清理）
                                    log.error("Skill {} 发送文件 SSE 失败: {} - {}", skillName, fileName, sendEx.getMessage());
                                    // 文件已注册但未标记，将在 TTL 后被清理
                                }
                            }
                        }
                    } catch (Exception parseEx) {
                        log.warn("handleRunSkillScript: 解析output失败: {}", parseEx.getMessage());
                        // output 不是 JSON，忽略
                    }
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
                } else if (onResponse == null) {
                    log.warn("handleRunSkillScript: onResponse 为 null，无法发送文件 SSE");
                }

<<<<<<< HEAD
                // 构建返回信息
=======
                // 构建更清晰的返回信息
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
                StringBuilder resultText = new StringBuilder();
                resultText.append("✅ 脚本执行成功\n");
                resultText.append("- 技能: ").append(skillName).append("\n");
                resultText.append("- 脚本: ").append(scriptName).append("\n");
                resultText.append("- 执行时间: ").append(result.getExecutionTime()).append("ms\n");

<<<<<<< HEAD
                if (detectedFiles != null && !detectedFiles.isEmpty()) {
                    resultText.append("- 输出文件:\n");
                    for (String filePath : detectedFiles) {
                        resultText.append("  - ").append(filePath).append("\n");
=======
                // 添加输出文件信息
                if (result.getOutputFiles() != null && !result.getOutputFiles().isEmpty()) {
                    resultText.append("- 输出文件:\n");
                    for (String filePath : result.getOutputFiles()) {
                        resultText.append("  - ").append(filePath).append(" (已发送给用户)\n");
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
                    }
                }

                resultText.append("\n--- 脚本输出 ---\n");
                resultText.append(result.getOutput() != null ? result.getOutput() : "(无输出)");

                McpToolCallResponse.ContentItem contentItem = new McpToolCallResponse.ContentItem();
                contentItem.setType("text");
                contentItem.setText(resultText.toString());
                response.setContent(Collections.singletonList(contentItem));
            } else {
                response.setSuccess(false);
                response.setError("脚本执行失败: " + result.getError());
            }
        } catch (Exception e) {
            response.setSuccess(false);
            response.setError("脚本执行异常: " + e.getMessage());
        }

        return response;
    }

    /**
<<<<<<< HEAD
     * 发送文件给用户
     */
    private void sendFileToUser(String filePath, String skillName, String scriptName,
                                String sessionId, Consumer<AIChatResponse> onResponse) {
        File file = new File(filePath);
        log.info("sendFileToUser: filePath={}, exists={}, isFile={}",
            filePath, file.exists(), file.isFile());

        if (!file.exists() || !file.isFile()) {
            log.warn("sendFileToUser: 文件不存在或不是文件: {}", filePath);
            return;
        }

        try {
            // 复制文件并添加时间戳
            File renamedFile = copyFileWithTimestamp(file, sessionId);
            String filePathToSend = renamedFile.getAbsolutePath();
            String fileName = renamedFile.getName();
            String fileType = getFileType(fileName);
            String mimeType = detectMimeType(fileName);

            // 注册文件生命周期
            fileLifecycleManager.registerFile(filePathToSend, "skill:" + skillName, sessionId);

            String base64Content = Base64.getEncoder().encodeToString(
                java.nio.file.Files.readAllBytes(renamedFile.toPath()));
            String fileId = "skill-" + skillName + "-" + scriptName + "-" + System.currentTimeMillis();

            AIChatResponse fileResponse = new AIChatResponse();
            fileResponse.setType("file");
            fileResponse.setSessionId(sessionId);
            fileResponse.setResourceId(fileId);
            fileResponse.setFileName(fileName);
            fileResponse.setFileType(fileType);
            fileResponse.setFileSize(renamedFile.length());
            fileResponse.setContent("data:" + mimeType + ";base64," + base64Content);

            // 调试日志：检查发送的内容
            log.info("sendFileToUser: 准备发送 SSE 消息, type={}, fileName={}, fileSize={}, contentLength={}, contentPreview={}",
                fileResponse.getType(), fileName, renamedFile.length(),
                fileResponse.getContent() != null ? fileResponse.getContent().length() : 0,
                fileResponse.getContent() != null && fileResponse.getContent().length() > 50
                    ? fileResponse.getContent().substring(0, 50) + "..."
                    : fileResponse.getContent());

            onResponse.accept(fileResponse);
            fileLifecycleManager.markAsSent(filePathToSend);

            log.info("Skill {} 发送文件成功: {} ({} bytes)", skillName, fileName, renamedFile.length());

        } catch (Exception e) {
            log.error("sendFileToUser 发送文件失败: {} - {}", filePath, e.getMessage());
        }
    }

    /**
=======
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
     * 处理 bash-sandbox execute 工具调用
     *
     * 注意：bash-sandbox 在独立进程中运行，Java 后端无法直接访问沙箱内的文件。
     * 文件发送机制：
     * 1. 大模型执行命令后，使用 export_file 将文件导出到共享目录
     * 2. 然后调用 send_file 发送文件给前端
     *
     * 此方法仅处理命令执行结果，不自动检测文件。
     */
    private McpToolCallResponse handleBashSandboxExecute(Map<String, Object> arguments,
                                                          Consumer<AIChatResponse> onResponse) {
        // 直接执行命令，不做文件检测
        return callToolWithRetry("bash-sandbox", "execute", arguments, 0);
    }

    /**
     * 处理 send_file 工具调用
     * 发送共享目录中的文件给前端
     */
    private McpToolCallResponse handleSendFile(Map<String, Object> arguments,
                                                Consumer<AIChatResponse> onResponse) {
        McpToolCallResponse response = new McpToolCallResponse();

        String filePath = arguments != null ? (String) arguments.get("file_path") : null;
        String sessionId = arguments != null ? (String) arguments.get("session_id") : null;

        if (filePath == null || filePath.isEmpty()) {
            response.setSuccess(false);
            response.setError("缺少参数：file_path");
            return response;
        }

        if (onResponse == null) {
            response.setSuccess(false);
            response.setError("无法发送文件：缺少 SSE 连接");
            return response;
        }

        try {
            java.io.File file = new java.io.File(filePath);
            if (!file.exists()) {
                response.setSuccess(false);
                response.setError("文件不存在: " + filePath);
                return response;
            }

            if (!file.isFile()) {
                response.setSuccess(false);
                response.setError("路径不是文件: " + filePath);
                return response;
            }

            // 复制文件，添加时间戳后缀避免冲突
            File renamedFile = copyFileWithTimestamp(file, sessionId);
            String filePathToSend = renamedFile.getAbsolutePath();
            String fileName = renamedFile.getName();
            String fileType = getFileType(fileName);
            String mimeType = detectMimeType(fileName);

            // 注册文件到生命周期管理器
            fileLifecycleManager.registerFile(filePathToSend, "send_file", sessionId);

            // 读取文件内容
            String base64Content = Base64.getEncoder().encodeToString(
                java.nio.file.Files.readAllBytes(renamedFile.toPath()));
<<<<<<< HEAD
            // fileId 使用纯 ASCII 字符，避免中文在 SSE 传输/IndexedDB 存取中出问题
            String fileId = "send_file-" + System.currentTimeMillis() + "-" + renamedFile.length();
=======
            String fileId = "send_file-" + System.currentTimeMillis() + "-" + fileName;
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2

            // 构建 SSE 响应
            AIChatResponse fileResponse = new AIChatResponse();
            fileResponse.setType("file");
            fileResponse.setSessionId(sessionId);
            fileResponse.setResourceId(fileId);
            fileResponse.setFileName(fileName);
            fileResponse.setFileType(fileType);
            fileResponse.setFileSize(renamedFile.length());
            fileResponse.setContent("data:" + mimeType + ";base64," + base64Content);

            // 发送 SSE
            onResponse.accept(fileResponse);

<<<<<<< HEAD
            // 标记文件为已发送（使用复制后的路径，与 registerFile 一致）
            fileLifecycleManager.markAsSent(filePathToSend);
=======
            // 标记文件为已发送
            fileLifecycleManager.markAsSent(filePath);
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2

            log.info("发送文件给前端: {} ({} bytes, type={})", fileName, file.length(), fileType);

            response.setSuccess(true);
            McpToolCallResponse.ContentItem contentItem = new McpToolCallResponse.ContentItem();
            contentItem.setType("text");
            contentItem.setText("✅ 文件已发送: " + fileName + " (" + renamedFile.length() + " bytes)");
            response.setContent(Collections.singletonList(contentItem));

        } catch (Exception e) {
            log.error("发送文件失败: {} - {}", filePath, e.getMessage());
            response.setSuccess(false);
            response.setError("发送文件失败: " + e.getMessage());
        }

        return response;
    }

    /**
     * 处理 setup_skill_environment 工具调用
     */
    private McpToolCallResponse handleSetupSkillEnvironment(Map<String, Object> arguments) {
        McpToolCallResponse response = new McpToolCallResponse();

        String skillName = arguments != null ? (String) arguments.get("skill_name") : null;

        if (skillName == null || skillName.isEmpty()) {
            response.setSuccess(false);
            response.setError("缺少参数：skill_name");
            return response;
        }

        try {
            log.info("开始配置 skill 环境: {}", skillName);

            // 使用 SkillEnvironmentManager 配置环境
            SkillEnvironmentManager.EnvironmentStatus status = skillEnvironmentManager.forceSetup(skillName);

            StringBuilder result = new StringBuilder();
            result.append("Skill: ").append(skillName).append("\n");
            result.append("Environment Status: ").append(status.isReady() ? "✅ Ready" : "❌ Not Ready").append("\n\n");

            if (status.getRequirements() != null && !status.getRequirements().isEmpty()) {
                result.append("Dependencies:\n");
                for (Map.Entry<String, SkillEnvironmentManager.RequirementStatus> entry : status.getRequirements().entrySet()) {
                    String name = entry.getKey();
                    SkillEnvironmentManager.RequirementStatus reqStatus = entry.getValue();
                    String icon = reqStatus.isInstalled() ? "✅" : "❌";
                    String version = reqStatus.getVersion() != null ? " (v" + reqStatus.getVersion() + ")" : "";
                    result.append("  ").append(icon).append(" ").append(name).append(version).append("\n");
                    if (reqStatus.getError() != null) {
                        result.append("      Error: ").append(reqStatus.getError()).append("\n");
                    }
                }
            }

            if (status.getErrorMessage() != null) {
                result.append("\n⚠️ Issues: ").append(status.getErrorMessage()).append("\n");
            }

            response.setSuccess(status.isReady());
            McpToolCallResponse.ContentItem contentItem = new McpToolCallResponse.ContentItem();
            contentItem.setType("text");
            contentItem.setText(result.toString());
            response.setContent(Collections.singletonList(contentItem));

            log.info("Skill {} 环境配置完成: ready={}", skillName, status.isReady());

        } catch (Exception e) {
            log.error("配置 skill {} 环境失败: {}", skillName, e.getMessage(), e);
            response.setSuccess(false);
            response.setError("环境配置失败: " + e.getMessage());
        }

        return response;
    }

    /**
     * 根据文件扩展名获取文件类型（用于前端分类显示）
     */
    /**
     * 复制文件并添加时间戳后缀，避免多进程冲突
     * 格式: 原文件名_时间戳后6位_用户ID.扩展名
     * 例如: output.png → output_123456_1.png
     *
     * 注意：使用复制而非重命名，保留原始文件不变
     */
    private File copyFileWithTimestamp(File originalFile, String sessionId) {
        try {
            String originalName = originalFile.getName();
            String parentDir = originalFile.getParent();

            // 分离文件名和扩展名
            int dotIndex = originalName.lastIndexOf('.');
            String baseName;
            String extension;
            if (dotIndex > 0) {
                baseName = originalName.substring(0, dotIndex);
                extension = originalName.substring(dotIndex);
            } else {
                baseName = originalName;
                extension = "";
            }

            // 生成时间戳后6位
            String timestampSuffix = String.valueOf(System.currentTimeMillis() % 1000000);

            // 从 sessionId 提取用户标识（如果没有则使用 "0"）
            String userSuffix = "0";
            if (sessionId != null && !sessionId.isEmpty()) {
                // sessionId 可能包含 userId，简单取后几位
                userSuffix = sessionId.length() > 6 ? sessionId.substring(sessionId.length() - 6) : sessionId;
            }

            // 构建新文件名
            String newFileName = baseName + "_" + timestampSuffix + "_" + userSuffix + extension;
            File newFile = new File(parentDir, newFileName);

            // 复制文件（而非重命名）
            java.nio.file.Files.copy(originalFile.toPath(), newFile.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);

            log.info("文件复制: {} → {}", originalName, newFileName);
            return newFile;

        } catch (Exception e) {
            log.warn("文件复制失败: {} - {}, 使用原文件", originalFile.getName(), e.getMessage());
            return originalFile;
        }
    }

    private String getFileType(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "other";
        }
        String ext = fileName.substring(fileName.lastIndexOf(".")).toLowerCase();

        // Office文档
        if (ext.equals(".pdf")) return "pdf";
        if (ext.equals(".docx") || ext.equals(".doc")) return "docx";
        if (ext.equals(".xlsx") || ext.equals(".xls")) return "xlsx";
        if (ext.equals(".pptx") || ext.equals(".ppt")) return "pptx";

        // 图片
        if (ext.equals(".png") || ext.equals(".jpg") || ext.equals(".jpeg") ||
            ext.equals(".gif") || ext.equals(".bmp") || ext.equals(".webp") ||
            ext.equals(".svg") || ext.equals(".ico") || ext.equals(".tiff") || ext.equals(".tif")) {
            return "image";
        }

        // 音频
        if (ext.equals(".mp3") || ext.equals(".wav") || ext.equals(".ogg") ||
            ext.equals(".m4a") || ext.equals(".flac") || ext.equals(".aac")) {
            return "audio";
        }

        // 视频
        if (ext.equals(".mp4") || ext.equals(".webm") || ext.equals(".avi") ||
            ext.equals(".mov") || ext.equals(".mkv") || ext.equals(".flv") || ext.equals(".wmv")) {
            return "video";
        }

        // 压缩文件
        if (ext.equals(".zip") || ext.equals(".rar") || ext.equals(".7z") ||
            ext.equals(".tar") || ext.equals(".gz")) {
            return "archive";
        }

        // 代码/文本
        if (ext.equals(".txt") || ext.equals(".html") || ext.equals(".htm") ||
            ext.equals(".css") || ext.equals(".js") || ext.equals(".json") ||
            ext.equals(".xml") || ext.equals(".csv") || ext.equals(".md") ||
            ext.equals(".yaml") || ext.equals(".yml")) {
            return "text";
        }

        return "other";
    }

    /**
     * 根据文件扩展名检测MIME类型
     */
    private String detectMimeType(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "application/octet-stream";
        }
        String ext = fileName.substring(fileName.lastIndexOf(".")).toLowerCase();

        switch (ext) {
            // Office文档
            case ".pdf": return "application/pdf";
            case ".docx": return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case ".xlsx": return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case ".pptx": return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            case ".doc": return "application/msword";
            case ".xls": return "application/vnd.ms-excel";
            case ".ppt": return "application/vnd.ms-powerpoint";
            // 图片
            case ".png": return "image/png";
            case ".jpg":
            case ".jpeg": return "image/jpeg";
            case ".gif": return "image/gif";
            case ".bmp": return "image/bmp";
            case ".webp": return "image/webp";
            case ".svg": return "image/svg+xml";
            case ".ico": return "image/x-icon";
            case ".tiff":
            case ".tif": return "image/tiff";
            // 音频
            case ".mp3": return "audio/mpeg";
            case ".wav": return "audio/wav";
            case ".ogg": return "audio/ogg";
            case ".m4a": return "audio/mp4";
            case ".flac": return "audio/flac";
            case ".aac": return "audio/aac";
            // 视频
            case ".mp4": return "video/mp4";
            case ".webm": return "video/webm";
            case ".avi": return "video/x-msvideo";
            case ".mov": return "video/quicktime";
            case ".mkv": return "video/x-matroska";
            case ".flv": return "video/x-flv";
            case ".wmv": return "video/x-ms-wmv";
            // 压缩文件
            case ".zip": return "application/zip";
            case ".rar": return "application/vnd.rar";
            case ".7z": return "application/x-7z-compressed";
            case ".tar": return "application/x-tar";
            case ".gz": return "application/gzip";
            // 文本/代码
            case ".txt": return "text/plain";
            case ".html":
            case ".htm": return "text/html";
            case ".css": return "text/css";
            case ".js": return "application/javascript";
            case ".json": return "application/json";
            case ".xml": return "application/xml";
            case ".csv": return "text/csv";
            case ".md": return "text/markdown";
            case ".yaml":
            case ".yml": return "application/x-yaml";
            // 字体
            case ".ttf": return "font/ttf";
            case ".otf": return "font/otf";
            case ".woff": return "font/woff";
            case ".woff2": return "font/woff2";
            // 其他常见格式
            case ".epub": return "application/epub+zip";
            case ".mobi": return "application/x-mobipocket-ebook";
            case ".rtf": return "application/rtf";
            case ".odt": return "application/vnd.oasis.opendocument.text";
            case ".ods": return "application/vnd.oasis.opendocument.spreadsheet";
            case ".odp": return "application/vnd.oasis.opendocument.presentation";
            case ".sqlite":
            case ".db": return "application/x-sqlite3";
            case ".exe": return "application/vnd.microsoft.portable-executable";
            case ".dmg": return "application/x-apple-diskimage";
            case ".iso": return "application/x-iso9660-image";
            default: return "application/octet-stream";
        }
    }

    private McpToolCallResponse handleWebSearch(Map<String, Object> arguments) {
        McpToolCallResponse response = new McpToolCallResponse();

        String query = arguments != null ? (String) arguments.get("query") : null;

        if (query == null || query.isEmpty()) {
            response.setSuccess(false);
            response.setError("缺少参数：query");
            return response;
        }

        try {
            long startTime = System.currentTimeMillis();
            OrioSearchService.SearchResult result = orioSearchService.search(query);
            long elapsed = System.currentTimeMillis() - startTime;

            if (result == null) {
                response.setSuccess(false);
                response.setError("搜索服务不可用或返回空结果");
                return response;
            }

            StringBuilder sb = new StringBuilder();
            sb.append("## 搜索: ").append(query).append("\n");
            sb.append("耗时: ").append(elapsed).append("ms\n\n");

            if (result.getAnswer() != null && !result.getAnswer().isEmpty()) {
                sb.append("### AI 答案\n").append(result.getAnswer()).append("\n\n");
            }

            sb.append("### 搜索结果\n");
            if (result.getResults() != null && !result.getResults().isEmpty()) {
                int index = 1;
                for (OrioSearchService.SearchResultItem item : result.getResults()) {
                    sb.append(index++).append(". **").append(item.getTitle() != null ? item.getTitle() : "无标题").append("**\n");
                    if (item.getContent() != null) {
                        sb.append("   ").append(item.getContent()).append("\n");
                    }
                    if (item.getUrl() != null) {
                        sb.append("   URL: ").append(item.getUrl()).append("\n");
                    }
                    sb.append("\n");
                }
                sb.append("---\n💡 提示：使用 web_extract 工具获取完整文章内容。支持批量并行抓取多个URL，速度更快！建议一次性传入多个URL的 urls 数组。");
            } else {
                sb.append("未找到相关结果\n");
            }

            response.setSuccess(true);
            McpToolCallResponse.ContentItem contentItem = new McpToolCallResponse.ContentItem();
            contentItem.setType("text");
            contentItem.setText(sb.toString());
            response.setContent(Collections.singletonList(contentItem));

        } catch (Exception e) {
            log.error("Web 搜索失败: {}", e.getMessage(), e);
            response.setSuccess(false);
            response.setError("搜索失败: " + e.getMessage());
        }

        return response;
    }

    private McpToolCallResponse handleWebExtract(Map<String, Object> arguments) {
        McpToolCallResponse response = new McpToolCallResponse();

        // 解析 URLs - 支持 url (单个) 和 urls (批量) 两种参数
        List<String> urls = new ArrayList<>();

        if (arguments != null) {
            // 优先处理单个 url 参数
            if (arguments.get("url") != null) {
                String singleUrl = arguments.get("url").toString();
                if (singleUrl != null && !singleUrl.isEmpty()) {
                    urls.add(singleUrl);
                }
            }

            // 处理批量 urls 参数
            if (arguments.get("urls") != null) {
                Object urlsObj = arguments.get("urls");
                if (urlsObj instanceof List) {
                    for (Object url : (List<?>) urlsObj) {
                        String urlStr = url.toString();
                        if (urlStr != null && !urlStr.isEmpty() && !urls.contains(urlStr)) {
                            urls.add(urlStr);
                        }
                    }
                }
            }
        }

        if (urls.isEmpty()) {
            response.setSuccess(false);
            response.setError("缺少参数：请提供 url (单个URL) 或 urls (URL列表)");
            return response;
        }

        // 解析 max_length 参数
        int maxLength = 10000;
        if (arguments != null && arguments.get("max_length") != null) {
            try {
                maxLength = ((Number) arguments.get("max_length")).intValue();
            } catch (Exception e) {
                // 使用默认值
            }
        }

        // 验证 URL 数量
        if (urls.size() > 10) {
            log.warn("批量提取 URL 数量过多: {}, 将只提取前10个", urls.size());
            urls = urls.subList(0, 10);
        }

        try {
            long startTime = System.currentTimeMillis();
            OrioSearchService.ExtractResult result = orioSearchService.extract(urls);
            long elapsed = System.currentTimeMillis() - startTime;

            if (result == null || result.getResults() == null || result.getResults().isEmpty()) {
                response.setSuccess(false);
                response.setError("内容提取失败：服务不可用或无结果");
                return response;
            }

            StringBuilder sb = new StringBuilder();
            sb.append("## 提取结果\n");
            sb.append("成功提取 ").append(result.getResults().size()).append(" 个页面，耗时 ").append(elapsed).append("ms\n\n");

            int successCount = 0;
            for (OrioSearchService.ExtractResultItem item : result.getResults()) {
                if (item.getUrl() != null) {
                    sb.append("### ").append(item.getUrl()).append("\n\n");
                }
                if (item.getRawContent() != null && !item.getRawContent().isEmpty()) {
                    String content = item.getRawContent();
                    // 应用长度限制
                    if (maxLength > 0 && content.length() > maxLength) {
                        content = content.substring(0, maxLength) + "\n... [内容已截断，共 " + item.getRawContent().length() + " 字符]";
                    }
                    sb.append(content).append("\n\n");
                    successCount++;
                } else {
                    sb.append("[该页面内容为空或提取失败]\n\n");
                }
                sb.append("---\n\n");
            }

            sb.append("提取完成：成功 ").append(successCount).append("/").append(urls.size());

            response.setSuccess(true);
            McpToolCallResponse.ContentItem contentItem = new McpToolCallResponse.ContentItem();
            contentItem.setType("text");
            contentItem.setText(sb.toString());
            response.setContent(Collections.singletonList(contentItem));

        } catch (Exception e) {
            log.error("Web 内容提取失败: {}", e.getMessage(), e);
            response.setSuccess(false);
            response.setError("内容提取失败: " + e.getMessage());
        }

        return response;
    }

<<<<<<< HEAD
    // ==================== 文件工具处理方法 ====================

    private McpToolCallResponse handleListFiles(Map<String, Object> arguments) {
        McpToolCallResponse response = new McpToolCallResponse();

        // 从 arguments 获取 sessionId
        String sessionId = arguments != null ? (String) arguments.get("session_id") : null;

        if (sessionId == null || sessionId.isEmpty()) {
            response.setSuccess(false);
            response.setError("缺少参数：session_id（请确保在会话上下文中调用）");
            return response;
        }

        try {
            List<FileIndexEntry> files = sessionFileIndexService.getFileIndex(sessionId);

            StringBuilder sb = new StringBuilder();
            if (files.isEmpty()) {
                sb.append("当前会话没有上传任何文件。");
            } else {
                sb.append("## 当前会话文件列表\n\n");
                sb.append("共 ").append(files.size()).append(" 个文件：\n\n");

                for (FileIndexEntry file : files) {
                    sb.append("### ").append(file.getFileName()).append("\n");
                    sb.append("- **文件 ID**: `").append(file.getFileId()).append("`\n");
                    sb.append("- **类型**: ").append(file.getFileTypeDisplayName()).append("\n");
                    sb.append("- **大小**: ").append(file.getFormattedSize()).append("\n");

                    if (file.getSummary() != null && !file.getSummary().isEmpty()) {
                        sb.append("- **摘要**: ").append(file.getSummary()).append("\n");
                    }
                    if (file.getLineCount() != null) {
                        sb.append("- **行数**: ").append(file.getLineCount()).append("\n");
                    }
                    if (file.getPageCount() != null) {
                        sb.append("- **页数**: ").append(file.getPageCount()).append("\n");
                    }
                    sb.append("\n");
                }

                sb.append("---\n");
                sb.append("💡 **提示**：\n");
                sb.append("- 使用 `read_file` 工具读取文件内容\n");
                sb.append("- 使用 `search_file` 工具搜索文件内容\n");
                sb.append("- 如果需要将文件传给 `run_skill_script` 处理（如 minimax-docx），请先使用 `get_file_path` 获取文件的本地路径\n");
            }

            response.setSuccess(true);
            McpToolCallResponse.ContentItem contentItem = new McpToolCallResponse.ContentItem();
            contentItem.setType("text");
            contentItem.setText(sb.toString());
            response.setContent(Collections.singletonList(contentItem));

        } catch (Exception e) {
            log.error("列出文件失败: {}", e.getMessage(), e);
            response.setSuccess(false);
            response.setError("列出文件失败: " + e.getMessage());
        }

        return response;
    }

    private McpToolCallResponse handleReadFile(Map<String, Object> arguments) {
        McpToolCallResponse response = new McpToolCallResponse();

        String fileId = arguments != null ? (String) arguments.get("file_id") : null;
        String sessionId = arguments != null ? (String) arguments.get("session_id") : null;
        Integer startLine = null;
        Integer endLine = null;

        if (arguments != null) {
            if (arguments.get("start_line") != null) {
                try {
                    startLine = ((Number) arguments.get("start_line")).intValue();
                } catch (Exception ignored) {}
            }
            if (arguments.get("end_line") != null) {
                try {
                    endLine = ((Number) arguments.get("end_line")).intValue();
                } catch (Exception ignored) {}
            }
        }

        if (fileId == null || fileId.isEmpty()) {
            response.setSuccess(false);
            response.setError("缺少参数：file_id");
            return response;
        }

        if (sessionId == null || sessionId.isEmpty()) {
            response.setSuccess(false);
            response.setError("缺少参数：session_id");
            return response;
        }

        try {
            FileIndexEntry fileInfo = sessionFileIndexService.getFileInfo(fileId, sessionId);
            if (fileInfo == null) {
                response.setSuccess(false);
                response.setError("文件不存在或无权访问: " + fileId);
                return response;
            }

            String content = sessionFileIndexService.getFileContentRange(fileId, sessionId, startLine, endLine);

            if (content == null) {
                response.setSuccess(false);
                response.setError("读取文件内容失败");
                return response;
            }

            StringBuilder sb = new StringBuilder();
            sb.append("## 文件内容: ").append(fileInfo.getFileName()).append("\n\n");

            if (startLine != null || endLine != null) {
                sb.append("**读取范围**: 行 ");
                sb.append(startLine != null ? startLine : 1);
                sb.append(" - ");
                sb.append(endLine != null ? endLine : "末尾");
                sb.append("\n\n");
            }

            sb.append("```\n");
            sb.append(content);
            sb.append("\n```\n");

            response.setSuccess(true);
            McpToolCallResponse.ContentItem contentItem = new McpToolCallResponse.ContentItem();
            contentItem.setType("text");
            contentItem.setText(sb.toString());
            response.setContent(Collections.singletonList(contentItem));

        } catch (Exception e) {
            log.error("读取文件失败: {}", e.getMessage(), e);
            response.setSuccess(false);
            response.setError("读取文件失败: " + e.getMessage());
        }

        return response;
    }

    private McpToolCallResponse handleSearchFile(Map<String, Object> arguments) {
        McpToolCallResponse response = new McpToolCallResponse();

        String keyword = arguments != null ? (String) arguments.get("keyword") : null;
        String fileId = arguments != null ? (String) arguments.get("file_id") : null;
        String sessionId = arguments != null ? (String) arguments.get("session_id") : null;

        if (keyword == null || keyword.isEmpty()) {
            response.setSuccess(false);
            response.setError("缺少参数：keyword");
            return response;
        }

        if (sessionId == null || sessionId.isEmpty()) {
            response.setSuccess(false);
            response.setError("缺少参数：session_id");
            return response;
        }

        try {
            List<SessionFileIndexService.FileSearchResult> results =
                    sessionFileIndexService.searchInFiles(sessionId, keyword, fileId);

            StringBuilder sb = new StringBuilder();
            sb.append("## 搜索结果: \"").append(keyword).append("\"\n\n");

            if (results.isEmpty()) {
                sb.append("未找到匹配内容。");
            } else {
                int totalMatches = 0;
                for (SessionFileIndexService.FileSearchResult result : results) {
                    totalMatches += result.getMatches().size();
                }

                sb.append("在 ").append(results.size()).append(" 个文件中找到 ")
                  .append(totalMatches).append(" 处匹配：\n\n");

                for (SessionFileIndexService.FileSearchResult result : results) {
                    sb.append("### ").append(result.getFileName()).append("\n");
                    sb.append("(文件 ID: `").append(result.getFileId()).append("`)\n\n");

                    for (SessionFileIndexService.SearchMatch match : result.getMatches()) {
                        sb.append("- **行 ").append(match.getLineNumber()).append("**: ");
                        // 高亮关键词
                        String line = match.getLineContent();
                        String lowerLine = line.toLowerCase();
                        String lowerKeyword = keyword.toLowerCase();
                        int idx = lowerLine.indexOf(lowerKeyword);
                        if (idx >= 0) {
                            sb.append(line.substring(0, idx))
                              .append("**").append(line.substring(idx, idx + keyword.length())).append("**")
                              .append(line.substring(idx + keyword.length()));
                        } else {
                            sb.append(line);
                        }
                        sb.append("\n");
                    }
                    sb.append("\n");
                }
            }

            response.setSuccess(true);
            McpToolCallResponse.ContentItem contentItem = new McpToolCallResponse.ContentItem();
            contentItem.setType("text");
            contentItem.setText(sb.toString());
            response.setContent(Collections.singletonList(contentItem));

        } catch (Exception e) {
            log.error("搜索文件失败: {}", e.getMessage(), e);
            response.setSuccess(false);
            response.setError("搜索文件失败: " + e.getMessage());
        }

        return response;
    }

    private McpToolCallResponse handleGetFileInfo(Map<String, Object> arguments) {
        McpToolCallResponse response = new McpToolCallResponse();

        String fileId = arguments != null ? (String) arguments.get("file_id") : null;
        String sessionId = arguments != null ? (String) arguments.get("session_id") : null;

        if (fileId == null || fileId.isEmpty()) {
            response.setSuccess(false);
            response.setError("缺少参数：file_id");
            return response;
        }

        if (sessionId == null || sessionId.isEmpty()) {
            response.setSuccess(false);
            response.setError("缺少参数：session_id");
            return response;
        }

        try {
            FileIndexEntry file = sessionFileIndexService.getFileInfo(fileId, sessionId);

            if (file == null) {
                response.setSuccess(false);
                response.setError("文件不存在或无权访问: " + fileId);
                return response;
            }

            StringBuilder sb = new StringBuilder();
            sb.append("## 文件详细信息\n\n");
            sb.append("- **文件名**: ").append(file.getFileName()).append("\n");
            sb.append("- **文件 ID**: `").append(file.getFileId()).append("`\n");
            sb.append("- **MIME 类型**: ").append(file.getMimeType()).append("\n");
            sb.append("- **文件类型**: ").append(file.getFileTypeDisplayName()).append("\n");
            sb.append("- **文件大小**: ").append(file.getFormattedSize()).append("\n");

            if (file.getSummary() != null && !file.getSummary().isEmpty()) {
                sb.append("- **摘要**: ").append(file.getSummary()).append("\n");
            }
            if (file.getLineCount() != null) {
                sb.append("- **行数**: ").append(file.getLineCount()).append("\n");
            }
            if (file.getPageCount() != null) {
                sb.append("- **页数**: ").append(file.getPageCount()).append("\n");
            }
            if (file.getUploadTime() != null) {
                sb.append("- **上传时间**: ").append(file.getUploadTime()).append("\n");
            }
            sb.append("- **是否预解析**: ").append(file.getPreParsed() ? "是" : "否").append("\n");

            // 提供文件路径（供 skill 使用）
            String localPath = resolveTempUrlToLocalPath(file.getTempUrl());
            if (localPath != null) {
                java.io.File localFile = new java.io.File(localPath);
                sb.append("- **本地路径**: `").append(localPath).append("`");
                sb.append(localFile.exists() ? " (可用)" : " (文件不存在)").append("\n");
            }

            response.setSuccess(true);
            McpToolCallResponse.ContentItem contentItem = new McpToolCallResponse.ContentItem();
            contentItem.setType("text");
            contentItem.setText(sb.toString());
            response.setContent(Collections.singletonList(contentItem));

        } catch (Exception e) {
            log.error("获取文件信息失败: {}", e.getMessage(), e);
            response.setSuccess(false);
            response.setError("获取文件信息失败: " + e.getMessage());
        }

        return response;
    }

    private McpToolCallResponse handleGetFilePath(Map<String, Object> arguments) {
        McpToolCallResponse response = new McpToolCallResponse();

        String fileId = arguments != null ? (String) arguments.get("file_id") : null;
        String sessionId = arguments != null ? (String) arguments.get("session_id") : null;

        if (fileId == null || fileId.isEmpty()) {
            response.setSuccess(false);
            response.setError("缺少参数：file_id");
            return response;
        }

        if (sessionId == null || sessionId.isEmpty()) {
            response.setSuccess(false);
            response.setError("缺少参数：session_id");
            return response;
        }

        try {
            FileIndexEntry file = sessionFileIndexService.getFileInfo(fileId, sessionId);

            if (file == null) {
                response.setSuccess(false);
                response.setError("文件不存在或无权访问: " + fileId);
                return response;
            }

            String tempUrl = file.getTempUrl();
            if (tempUrl == null || tempUrl.isEmpty()) {
                response.setSuccess(false);
                response.setError("文件没有可用的本地路径");
                return response;
            }

            // 将 temp:// URL 转换为本地绝对路径
            String localPath = resolveTempUrlToLocalPath(tempUrl);
            if (localPath == null) {
                response.setSuccess(false);
                response.setError("无法解析文件路径: " + tempUrl);
                return response;
            }

            // 验证文件是否存在
            java.io.File localFile = new java.io.File(localPath);
            if (!localFile.exists()) {
                response.setSuccess(false);
                response.setError("文件在本地不存在（可能已被清理）: " + localPath);
                return response;
            }

            StringBuilder sb = new StringBuilder();
            sb.append("## 文件路径\n\n");
            sb.append("- **文件名**: ").append(file.getFileName()).append("\n");
            sb.append("- **本地绝对路径**: `").append(localPath).append("`\n");
            sb.append("\n请将此路径作为 `run_skill_script` 的 `input` 参数使用。");

            response.setSuccess(true);
            McpToolCallResponse.ContentItem contentItem = new McpToolCallResponse.ContentItem();
            contentItem.setType("text");
            contentItem.setText(sb.toString());
            response.setContent(Collections.singletonList(contentItem));

        } catch (Exception e) {
            log.error("获取文件路径失败: {}", e.getMessage(), e);
            response.setSuccess(false);
            response.setError("获取文件路径失败: " + e.getMessage());
        }

        return response;
    }

    /**
     * 将 temp:// URL 转换为本地绝对路径
     * temp://type/fileName -> uploads/temp/type/fileName (绝对路径)
     */
    private String resolveTempUrlToLocalPath(String tempUrl) {
        if (tempUrl == null || !tempUrl.startsWith("temp://")) {
            return null;
        }

        try {
            String path = tempUrl.substring("temp://".length());
            String[] parts = path.split("/", 2);
            if (parts.length != 2 || parts[1].isEmpty()) {
                log.warn("无效的 temp:// 文件路径: {}", tempUrl);
                return null;
            }

            java.nio.file.Path localPath = java.nio.file.Paths.get("uploads", "temp", parts[0], parts[1]).toAbsolutePath();
            return localPath.toString();
        } catch (Exception e) {
            log.error("解析 temp:// URL 失败: {}", tempUrl, e);
            return null;
        }
    }

=======
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
    public Set<String> getRunningServers() {
        return runningServers.keySet();
    }
    
    public Map<String, McpServerConfig> getAllAvailableServers() throws IOException {
        File fullConfigFile = new File("src/main/resources/mcpserverconfig/mcp-servers-config.json");
        
        McpServersFile config;
        if (fullConfigFile.exists()) {
            config = objectMapper.readValue(fullConfigFile, McpServersFile.class);
        } else {
            Resource resource = new ClassPathResource(CONFIG_LOCATION);
            if (!resource.exists()) {
                return new HashMap<>();
            }
            try (InputStream inputStream = resource.getInputStream()) {
                config = objectMapper.readValue(inputStream, McpServersFile.class);
            }
        }
        
        if (config.getMcpServers() == null) {
            return new HashMap<>();
        }
        
        Map<String, McpServerConfig> availableServers = new HashMap<>();
        for (Map.Entry<String, McpServerConfig> entry : config.getMcpServers().entrySet()) {
            if (!entry.getValue().isDisabled()) {
                availableServers.put(entry.getKey(), entry.getValue());
            }
        }
        return availableServers;
    }
    
    public List<String> getSelectedServers() {
        return new ArrayList<>(selectedServers.keySet());
    }
    
    public void updateSelectedServers(List<String> serverNames) {
        selectedServers.clear();
        if (serverNames != null) {
            for (String serverName : serverNames) {
                selectedServers.put(serverName, true);
            }
        }
        log.info("用户选中的 MCP 服务器已更新：{}", selectedServers.keySet());
        
        try {
            saveSelectedConfig();
        } catch (IOException e) {
            log.error("保存精简配置文件失败：{}", e.getMessage(), e);
        }
    }
    
    public void selectServer(String serverName) {
        selectedServers.put(serverName, true);
        log.info("用户选择了 MCP 服务器：{}", serverName);
    }
    
    public void deselectServer(String serverName) {
        selectedServers.remove(serverName);
        log.info("用户取消了 MCP 服务器：{}", serverName);
    }
    
    public McpServersFile getSelectedServersConfig() throws IOException {
        File fullConfigFile = new File("src/main/resources/mcpserverconfig/mcp-servers-config.json");
        McpServersFile fullConfig;
        
        if (fullConfigFile.exists()) {
            fullConfig = objectMapper.readValue(fullConfigFile, McpServersFile.class);
        } else {
            Resource resource = new ClassPathResource(CONFIG_LOCATION);
            try (InputStream inputStream = resource.getInputStream()) {
                fullConfig = objectMapper.readValue(inputStream, McpServersFile.class);
            }
        }
        
        McpServersFile selectedConfig = new McpServersFile();
        Map<String, McpServerConfig> selectedServersMap = new HashMap<>();
        
        if (fullConfig.getMcpServers() != null) {
            for (String serverName : selectedServers.keySet()) {
                McpServerConfig config = fullConfig.getMcpServers().get(serverName);
                if (config != null && !config.isDisabled()) {
                    selectedServersMap.put(serverName, config);
                }
            }
        }
        
        selectedConfig.setMcpServers(selectedServersMap);
        return selectedConfig;
    }
    
    public void loadSelectedServersFromConfig() {
        try {
            File smallConfigFile = new File("src/main/resources/mcpserverconfig/mcp-servers-small-config.json");
            
            if (!smallConfigFile.exists()) {
                log.info("精简配置文件不存在，不加载任何服务器选择");
                return;
            }
            
            McpServersFile selectedConfig = objectMapper.readValue(smallConfigFile, McpServersFile.class);
            
            if (selectedConfig.getMcpServers() != null) {
                selectedServers.clear();
                for (String serverName : selectedConfig.getMcpServers().keySet()) {
                    selectedServers.put(serverName, true);
                }
                log.info("从精简配置加载了 {} 个选中的服务器", selectedServers.size());
            }
            
        } catch (IOException e) {
            log.error("从精简配置加载服务器选择失败：{}", e.getMessage(), e);
        }
    }
    
    private void saveSelectedConfig() throws IOException {
        McpServersFile selectedConfig = getSelectedServersConfig();
        
        File configFile = new File("src/main/resources/mcpserverconfig/mcp-servers-small-config.json");
        
        File parentDir = configFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }
        
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(configFile, selectedConfig);
        log.info("精简版 MCP 配置文件已保存到：{}", configFile.getAbsolutePath());
    }

    /**
     * 会话模式配置枚举
     * 统一管理所有对话模式的配置
     *
     * 模式说明：
     * - LITE_TASK: 轻量模式，纯对话，不加载工具，适用于简单问答
     * - MEDIUM_TASK: 中等任务模式，使用 ReAct 循环，适用于需要多步推理但结构相对清晰的任务
     * - COMPLEX_TASK: 复杂任务模式，使用 Plan-Execute 架构，适用于需要复杂规划和多阶段执行的任务
     * - MCP: MCP模式，智能判断任务复杂度，自动选择ReAct或Plan-Execute
     */
    public enum ChatModeConfig {
        LITE_TASK(
            "轻量任务",
            "轻量模式 - 纯对话，不加载工具和技能，保留知识图谱和用户画像",
            1,       // maxIterations - 单次调用
            3,       // maxConsecutiveErrors
            3,       // repeatCallThreshold
            60000,   // toolTimeoutMs
            true,    // forceReactMode
            false,   // forcePlanExecuteMode
            com.superfriend.superfriend.constant.ChatMode.LITE_TASK,
            com.superfriend.superfriend.entity.Prompt.Mode.LITE_TASK
        ),
        MEDIUM_TASK(
            "中等任务",
            "中等任务模式 - 智能选择 ReAct 或 Plan-Execute，支持工具调用和技能执行",
            50,      // maxIterations（增加以支持 Plan-Execute）
            6,       // maxConsecutiveErrors
            3,       // repeatCallThreshold
            180000,  // toolTimeoutMs
            false,   // forceReactMode - 改为 false，支持智能切换
            false,   // forcePlanExecuteMode - 自动判断
            com.superfriend.superfriend.constant.ChatMode.MEDIUM_TASK,
            com.superfriend.superfriend.entity.Prompt.Mode.MEDIUM_TASK
        ),
        COMPLEX_TASK(
            "复杂任务",
            "复杂任务模式 - 强制使用 Plan-Execute 架构，支持任务分解、计划验证和动态调整",
            80,      // maxIterations
            8,       // maxConsecutiveErrors
            3,       // repeatCallThreshold
            300000,  // toolTimeoutMs
            false,   // forceReactMode
            true,    // forcePlanExecuteMode
            com.superfriend.superfriend.constant.ChatMode.COMPLEX_TASK,
            com.superfriend.superfriend.entity.Prompt.Mode.COMPLEX_TASK
        ),
        MCP(
            "MCP对话",
            "MCP模式 - 智能判断任务复杂度，自动选择ReAct或Plan-Execute",
            80,      // maxIterations
            8,       // maxConsecutiveErrors
            3,       // repeatCallThreshold
            120000,  // toolTimeoutMs
            false,   // forceReactMode
            false,   // forcePlanExecuteMode (自动判断)
            com.superfriend.superfriend.constant.ChatMode.MCP,
            com.superfriend.superfriend.entity.Prompt.Mode.MCP
        );

        private final String displayName;
        private final String description;
        private final int maxIterations;
        private final int maxConsecutiveErrors;
        private final int repeatCallThreshold;
        private final long toolTimeoutMs;
        private final boolean forceReactMode;
        private final boolean forcePlanExecuteMode;
        private final String chatMode;
        private final com.superfriend.superfriend.entity.Prompt.Mode promptMode;

        ChatModeConfig(String displayName, String description, int maxIterations,
                       int maxConsecutiveErrors, int repeatCallThreshold, long toolTimeoutMs,
                       boolean forceReactMode, boolean forcePlanExecuteMode,
                       String chatMode,
                       com.superfriend.superfriend.entity.Prompt.Mode promptMode) {
            this.displayName = displayName;
            this.description = description;
            this.maxIterations = maxIterations;
            this.maxConsecutiveErrors = maxConsecutiveErrors;
            this.repeatCallThreshold = repeatCallThreshold;
            this.toolTimeoutMs = toolTimeoutMs;
            this.forceReactMode = forceReactMode;
            this.forcePlanExecuteMode = forcePlanExecuteMode;
            this.chatMode = chatMode;
            this.promptMode = promptMode;
        }

        public AgentExecutionService.ExecutionConfig createExecutionConfig() {
            AgentExecutionService.ExecutionConfig config = new AgentExecutionService.ExecutionConfig();

            // 根据模式设置不同的配置
            boolean isLiteMode = (this == LITE_TASK);
            config.setEnableSkills(!isLiteMode);
            config.setEnableReflection(!isLiteMode);
            config.setEnableParallelExecution(!isLiteMode);
            config.setEnableProgressTracking(!isLiteMode);
            config.setEnableIntelligentToolSelection(!isLiteMode);
            config.setEnableWebSearchPreload(true); // 所有模式都启用预搜索

            config.setMaxIterations(maxIterations);
            config.setMaxConsecutiveErrors(maxConsecutiveErrors);
            config.setRepeatCallThreshold(repeatCallThreshold);
            config.setToolTimeoutMs(toolTimeoutMs);
            config.setForceReactMode(forceReactMode);
            config.setForcePlanExecuteMode(forcePlanExecuteMode);
            config.setChatMode(chatMode);
            config.setPromptMode(promptMode);
            return config;
        }

        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
        
        public String getLogPrefix() {
            return "[" + displayName + "]";
        }
    }

    /**
     * Lite 模式对话 - 纯对话，不加载 MCP 和 Skills
     * 保留用户画像和知识图谱上下文注入
     */
    public void chatLite(String message,
                         String sessionId,
                         String model,
                         List<Map<String, String>> history,
                         Long userId,
                         Consumer<AIChatResponse> onResponse) {
        log.info("开始 Lite 模式对话，Session ID: {}, Model: {}", sessionId, model);

        sessionManager.startExecution(sessionId);

        String observabilityTraceId = observabilityService.startTrace(
            sessionId,
            userId != null ? userId.toString() : null,
            model,
            message
        );

        final long[] totalInputTokens = {0};
        final long[] totalOutputTokens = {0};
        final double[] totalCost = {0.0};
        final long startTime = System.currentTimeMillis();
        final String[] actualModelRef = {model != null ? model : defaultModel};

        try {
            // 1. 解析模型配置
            AIModelConfig resolvedConfig = modelConfigService.resolveModelConfig(model, userId);
            if (resolvedConfig == null) {
                AIChatResponse errResp = new AIChatResponse();
                errResp.setType("error");
                errResp.setContent("未找到可用的模型配置，请先在模型配置页面添加模型");
                errResp.setSessionId(sessionId);
                errResp.setModel(model != null ? model : defaultModel);
                onResponse.accept(errResp);
                sessionManager.endExecution(sessionId);
                return;
            }

            String actualModel = resolvedConfig.getModelId();
            if (actualModel == null || actualModel.isEmpty()) {
                actualModel = defaultModel;
            }
            actualModelRef[0] = actualModel;
            String effectiveApiUrl = resolvedConfig.getApiUrl();
            String effectiveApiKey = resolvedConfig.getApiKey();

            // 2. 使用 SystemContextBuilder 构建系统提示词
            String systemPrompt = systemContextBuilder.buildLitePrompt(userId, sessionId, message);

            // 3. 搜索预加载（使用 OrioSearch）
            String searchContext = null;
            if (shouldPerformSearch(message)) {
                try {
                    AIChatResponse searchStatusResp = new AIChatResponse();
                    searchStatusResp.setType("thinking");
                    searchStatusResp.setContent("正在搜索相关信息...");
                    searchStatusResp.setSessionId(sessionId);
                    searchStatusResp.setModel(actualModelRef[0]);
                    onResponse.accept(searchStatusResp);

                    searchContext = orioSearchService.getSearchContextForLLM(message);
                    if (searchContext != null && searchContext.length() > 50) {
                        log.info("[sessionId={}] OrioSearch 搜索完成，获取 {} 字符", sessionId, searchContext.length());
                    } else {
                        searchContext = null;
                    }
                } catch (Exception e) {
                    log.warn("[sessionId={}] OrioSearch 搜索失败: {}", sessionId, e.getMessage());
                }
            }

            // 5. 构建消息列表
            List<Map<String, Object>> messages = new ArrayList<>();

            Map<String, Object> systemMessage = new HashMap<>();
            systemMessage.put("role", "system");
            systemMessage.put("content", systemPrompt);
            messages.add(systemMessage);

            // 添加历史消息
            if (history != null && !history.isEmpty()) {
                for (Map<String, String> histMsg : history) {
                    Map<String, Object> msg = new HashMap<>();
                    msg.put("role", histMsg.get("role"));
                    msg.put("content", histMsg.get("content"));
                    messages.add(msg);
                }
            }

            // 添加用户消息
            Map<String, Object> userMessage = new HashMap<>();
            if (searchContext != null && searchContext.length() > 50) {
                userMessage.put("role", "user");
                userMessage.put("content", "[搜索结果]\n" + searchContext + "\n\n[请基于以上信息回答]\n" + message);
            } else {
                userMessage.put("role", "user");
                userMessage.put("content", message);
            }
            messages.add(userMessage);

            // 6. 直接调用 LLM（无工具）
            LLMRequest llmRequest = LLMRequest.builder()
                .model(actualModel)
                .apiUrl(effectiveApiUrl)
                .apiKey(effectiveApiKey)
                .messages(messages)
                .stream(true)
                .sessionId(sessionId)
                .userId(userId)
                .build();

            final StringBuilder contentBuilder = new StringBuilder();

            llmClient.streamChat(llmRequest, chunk -> {
                if (chunk.isError()) {
                    AIChatResponse errResp = new AIChatResponse();
                    errResp.setType("error");
                    errResp.setContent(chunk.getError());
                    errResp.setSessionId(sessionId);
                    errResp.setModel(actualModelRef[0]);
                    errResp.setDone(true);
                    onResponse.accept(errResp);
                    return;
                }

                if (chunk.hasContent()) {
                    contentBuilder.append(chunk.getContent());

                    AIChatResponse resp = new AIChatResponse();
                    resp.setType("result");
                    resp.setContent(chunk.getContent());
                    resp.setSessionId(sessionId);
                    resp.setModel(actualModelRef[0]);
                    resp.setDone(false);
                    onResponse.accept(resp);
                }

                if (chunk.hasReasoningContent()) {
                    AIChatResponse resp = new AIChatResponse();
                    resp.setType("thinking");
                    resp.setReasoningContent(chunk.getReasoningContent());
                    resp.setSessionId(sessionId);
                    resp.setModel(actualModelRef[0]);
                    resp.setDone(false);
                    onResponse.accept(resp);
                }

                if (chunk.getUsage() != null) {
                    JsonNode usage = chunk.getUsage();
                    totalInputTokens[0] = usage.path("prompt_tokens").asLong(0);
                    totalOutputTokens[0] = usage.path("completion_tokens").asLong(0);
<<<<<<< HEAD
                    if (totalInputTokens[0] == 0) totalInputTokens[0] = usage.path("input_tokens").asLong(0);
                    if (totalOutputTokens[0] == 0) totalOutputTokens[0] = usage.path("output_tokens").asLong(0);
=======
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
                }

                if (chunk.isDone()) {
                    AIChatResponse doneResp = new AIChatResponse();
                    doneResp.setType("result");
                    doneResp.setContent("");
                    doneResp.setSessionId(sessionId);
                    doneResp.setModel(actualModelRef[0]);
                    doneResp.setDone(true);
                    onResponse.accept(doneResp);

                    // 记录成本
                    try {
                        double cost = costTrackingService.calculateCost(
                            actualModelRef[0],
                            totalInputTokens[0],
                            totalOutputTokens[0]
                        );
                        costTrackingService.recordApiCall(
                            sessionId,
                            userId,
                            actualModelRef[0],
                            totalInputTokens[0],
                            totalOutputTokens[0],
                            null
                        );
                        // 发送 SSE cost 事件
                        sendCostUpdate(sessionId, actualModelRef[0], totalInputTokens[0], totalOutputTokens[0], cost, onResponse);
                    } catch (Exception e) {
                        log.warn("记录成本失败：{}", e.getMessage());
                    }

                    observabilityService.endTrace(observabilityTraceId, "completed", null);
                }
            });

        } catch (Exception e) {
            log.error("Lite 模式对话失败：{}", e.getMessage(), e);
            AIChatResponse errResp = new AIChatResponse();
            errResp.setType("error");
            errResp.setContent("Lite 模式对话失败：" + e.getMessage());
            errResp.setSessionId(sessionId);
            errResp.setModel(actualModelRef[0]);
            errResp.setDone(true);
            onResponse.accept(errResp);
            observabilityService.endTrace(observabilityTraceId, "failed", null);
        } finally {
            sessionManager.endExecution(sessionId);
        }
    }

    /**
     * Lite 模式对话 - 多模态版本
     * 自动检测所需模态并选择支持该模态的模型
     *
     * @param request AI 对话请求（支持多模态）
     * @param onResponse 响应回调
     */
    public void chatLiteMultimodal(AIChatRequest request,
                                   Consumer<AIChatResponse> onResponse) {
        chatLiteMultimodal(request, null, onResponse);
    }

    public void chatLiteMultimodal(AIChatRequest request,
                                   UserIntent intent,
                                   Consumer<AIChatResponse> onResponse) {
        String sessionId = request.getSessionId();
        String model = request.getModel();
        Long userId = request.getUserId();

        log.info("开始 Lite 模式对话（多模态+模型选择），Session ID: {}, Model: {}, Multimodal: {}",
            sessionId, model, request.isMultimodal());

        // 自动选择支持所需模态的模型
        ModelCapabilityService.ModelSelectionResult selectionResult = modelCapabilityService.autoSelectModel(
            userId,
            request.getContent(),
            request.getImages(),
            model
        );

        if (!selectionResult.isSuccessful()) {
            AIChatResponse errorResponse = new AIChatResponse();
            errorResponse.setError("未找到支持所需模态的模型。所需模态: " + selectionResult.getRequiredModalities());
            errorResponse.setSessionId(sessionId);
            errorResponse.setModel(model);
            errorResponse.setDone(true);
            onResponse.accept(errorResponse);
            return;
        }

        // 如果模型被切换，更新请求中的模型
        if (selectionResult.isModelSwitched()) {
            AIModelConfig selectedModel = selectionResult.getSelectedModel();
            request.setModel(selectedModel.getConfigId());

            log.info("模型自动切换: {} -> {}，原因: 支持模态 {}",
                model, selectedModel.getModelId(), selectionResult.getRequiredModalities());

            // 通知前端模型切换
            AIChatResponse switchResponse = new AIChatResponse();
            switchResponse.setType("model_switch");
            switchResponse.setContent("已自动切换到支持多模态的模型: " + selectedModel.getName());
            switchResponse.setSessionId(sessionId);
            switchResponse.setModel(selectedModel.getConfigId());
            onResponse.accept(switchResponse);
        }

        // 调用原有的 chatLite 方法
        chatLite(request, intent, onResponse);
    }

    /**
     * Lite 模式对话 - 支持多模态消息
     * 保留用户画像和知识图谱上下文注入
     *
     * @param request AI 对话请求（支持多模态）
     * @param onResponse 响应回调
     */
    public void chatLite(AIChatRequest request,
                         Consumer<AIChatResponse> onResponse) {
        chatLite(request, null, onResponse);
    }

    public void chatLite(AIChatRequest request,
                         UserIntent intent,
                         Consumer<AIChatResponse> onResponse) {
        String sessionId = request.getSessionId();
        String model = request.getModel();
        Long userId = request.getUserId();

        log.info("开始 Lite 模式对话（多模态），Session ID: {}, Model: {}, Multimodal: {}",
            sessionId, model, request.isMultimodal());

        sessionManager.startExecution(sessionId);

        String observabilityTraceId = observabilityService.startTrace(
            sessionId,
            userId != null ? userId.toString() : null,
            model,
            request.getMessage()
        );

        final long[] totalInputTokens = {0};
        final long[] totalOutputTokens = {0};
        final double[] totalCost = {0.0};
        final long startTime = System.currentTimeMillis();
        final String[] actualModelRef = {model != null ? model : defaultModel};

        try {
            // 1. 解析模型配置
            AIModelConfig resolvedConfig = modelConfigService.resolveModelConfig(model, userId);
            if (resolvedConfig == null) {
                AIChatResponse errResp = new AIChatResponse();
                errResp.setType("error");
                errResp.setContent("未找到可用的模型配置，请先在模型配置页面添加模型");
                errResp.setSessionId(sessionId);
                errResp.setModel(model != null ? model : defaultModel);
                onResponse.accept(errResp);
                sessionManager.endExecution(sessionId);
                return;
            }

            String actualModel = resolvedConfig.getModelId();
            if (actualModel == null || actualModel.isEmpty()) {
                actualModel = defaultModel;
            }
            actualModelRef[0] = actualModel;
            String effectiveApiUrl = resolvedConfig.getApiUrl();
            String effectiveApiKey = resolvedConfig.getApiKey();

            // 2. 使用 SystemContextBuilder 构建系统提示词
            String systemPrompt = systemContextBuilder.buildLitePrompt(userId, sessionId, request.getEffectiveText());

            // 3. 搜索预加载（使用 OrioSearch）
            String searchContext = null;
            String effectiveText = request.getEffectiveText();
            boolean doSearch = (intent != null && intent.isNeedsSearch()) || shouldPerformSearch(effectiveText);
            if (doSearch) {
                try {
                    AIChatResponse searchStatusResp = new AIChatResponse();
                    searchStatusResp.setType("thinking");
                    searchStatusResp.setContent("正在搜索相关信息...");
                    searchStatusResp.setSessionId(sessionId);
                    searchStatusResp.setModel(actualModelRef[0]);
                    onResponse.accept(searchStatusResp);

                    searchContext = orioSearchService.getSearchContextForLLM(effectiveText);
                    if (searchContext != null && searchContext.length() > 50) {
                        log.info("[sessionId={}] OrioSearch 搜索完成，获取 {} 字符", sessionId, searchContext.length());
                    } else {
                        searchContext = null;
                    }
                } catch (Exception e) {
                    log.warn("[sessionId={}] OrioSearch 搜索失败: {}", sessionId, e.getMessage());
                }
            }

            // 5. 构建消息列表（使用 MessageContentBuilder 支持多模态）
            List<Map<String, Object>> messages = new ArrayList<>();

            Map<String, Object> systemMessage = new HashMap<>();
            systemMessage.put("role", "system");
            systemMessage.put("content", systemPrompt);
            messages.add(systemMessage);

            // 添加历史消息（支持多模态）
            messages.addAll(messageContentBuilder.buildHistoryMessages(request));

            // 添加用户消息（支持多模态）
            Map<String, Object> userMessage = new HashMap<>();
            userMessage.put("role", "user");

            // 构建用户消息内容
            Object userContent;
            if (searchContext != null && searchContext.length() > 50) {
                // 有搜索结果时，将搜索结果注入到文本前面
                String textWithSearch = "[搜索结果]\n" + searchContext + "\n\n[请基于以上信息回答]\n" +
                    (effectiveText != null ? effectiveText : "");

                if (request.isMultimodal() && request.getImages() != null && !request.getImages().isEmpty()) {
                    // 多模态 + 搜索结果：构建新的内容列表
                    List<ChatMessageContent> contents = new ArrayList<>();
                    contents.add(ChatMessageContent.text(textWithSearch));
                    for (String imageUrl : request.getImages()) {
                        contents.add(ChatMessageContent.imageUrl(imageUrl));
                    }
                    userContent = contents;
                } else {
                    userContent = textWithSearch;
                }
            } else {
                // 无搜索结果，使用 MessageContentBuilder 构建内容
                userContent = messageContentBuilder.buildUserMessageContent(request);
            }

            userMessage.put("content", userContent);
            messages.add(userMessage);

            log.debug("[sessionId={}] 构建消息完成，共 {} 条消息，用户消息类型: {}",
                sessionId, messages.size(), userContent.getClass().getSimpleName());

            // 6. 直接调用 LLM（无工具）
            LLMRequest llmRequest = LLMRequest.builder()
                .model(actualModel)
                .apiUrl(effectiveApiUrl)
                .apiKey(effectiveApiKey)
                .messages(messages)
                .stream(true)
                .sessionId(sessionId)
                .userId(userId)
                .build();

            final StringBuilder contentBuilder = new StringBuilder();

            llmClient.streamChat(llmRequest, chunk -> {
                if (chunk.isError()) {
                    AIChatResponse errResp = new AIChatResponse();
                    errResp.setType("error");
                    errResp.setContent(chunk.getError());
                    errResp.setSessionId(sessionId);
                    errResp.setModel(actualModelRef[0]);
                    errResp.setDone(true);
                    onResponse.accept(errResp);
                    return;
                }

                if (chunk.hasContent()) {
                    contentBuilder.append(chunk.getContent());

                    AIChatResponse resp = new AIChatResponse();
                    resp.setType("result");
                    resp.setContent(chunk.getContent());
                    resp.setSessionId(sessionId);
                    resp.setModel(actualModelRef[0]);
                    resp.setDone(false);
                    onResponse.accept(resp);
                }

                if (chunk.hasReasoningContent()) {
                    AIChatResponse resp = new AIChatResponse();
                    resp.setType("thinking");
                    resp.setReasoningContent(chunk.getReasoningContent());
                    resp.setSessionId(sessionId);
                    resp.setModel(actualModelRef[0]);
                    resp.setDone(false);
                    onResponse.accept(resp);
                }

                if (chunk.getUsage() != null) {
                    JsonNode usage = chunk.getUsage();
                    totalInputTokens[0] = usage.path("prompt_tokens").asLong(0);
                    totalOutputTokens[0] = usage.path("completion_tokens").asLong(0);
<<<<<<< HEAD
                    if (totalInputTokens[0] == 0) totalInputTokens[0] = usage.path("input_tokens").asLong(0);
                    if (totalOutputTokens[0] == 0) totalOutputTokens[0] = usage.path("output_tokens").asLong(0);
=======
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
                }

                if (chunk.isDone()) {
                    AIChatResponse doneResp = new AIChatResponse();
                    doneResp.setType("result");
                    doneResp.setContent("");
                    doneResp.setSessionId(sessionId);
                    doneResp.setModel(actualModelRef[0]);
                    doneResp.setDone(true);
                    onResponse.accept(doneResp);

                    // 计算成本并记录
                    try {
                        double cost = costTrackingService.calculateCost(
                            actualModelRef[0],
                            totalInputTokens[0],
                            totalOutputTokens[0]
                        );
                        totalCost[0] = cost;
                        costTrackingService.recordApiCall(
                            sessionId,
                            userId,
                            actualModelRef[0],
                            totalInputTokens[0],
                            totalOutputTokens[0],
                            null
                        );
                        // 发送 SSE cost 事件
                        sendCostUpdate(sessionId, actualModelRef[0], totalInputTokens[0], totalOutputTokens[0], cost, onResponse);
                    } catch (Exception e) {
                        log.warn("记录成本失败：{}", e.getMessage());
                    }

                    // 记录观测数据
                    observabilityService.endTrace(observabilityTraceId, "completed", null);

<<<<<<< HEAD
                    // 保存对话历史（Lite 模式也需要保存，以便后续对话引用）
                    try {
                        String userText = request.getEffectiveText();
                        String assistantText = contentBuilder.toString();
                        if (userText != null && !userText.isEmpty() &&
                            assistantText != null && !assistantText.isEmpty()) {
                            contextMangerService.saveTurn(
                                sessionId,
                                userId,
                                actualModelRef[0],
                                com.superfriend.superfriend.constant.ChatMode.LITE_TASK,
                                userText,
                                assistantText
                            );
                            log.debug("[sessionId={}] Lite 模式对话历史已保存", sessionId);
                        }
                    } catch (Exception e) {
                        log.warn("[sessionId={}] 保存 Lite 模式对话历史失败: {}", sessionId, e.getMessage());
                    }

=======
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
                    log.info("[sessionId={}] Lite 多模态对话完成，tokens: input={}, output={}, cost={}",
                        sessionId, totalInputTokens[0], totalOutputTokens[0], totalCost[0]);
                }
            });

        } catch (Exception e) {
            log.error("Lite 多模态对话失败：{}", e.getMessage(), e);
            AIChatResponse errResp = new AIChatResponse();
            errResp.setType("error");
            errResp.setContent("Lite 多模态对话失败：" + e.getMessage());
            errResp.setSessionId(sessionId);
            errResp.setModel(actualModelRef[0]);
            errResp.setDone(true);
            onResponse.accept(errResp);
            observabilityService.endTrace(observabilityTraceId, "failed", null);
        } finally {
            sessionManager.endExecution(sessionId);
        }
    }

    /**
     * 构建 Lite 模式系统提示词（简洁版）
     */
    public String buildLiteSystemPrompt(Long userId) {
        com.superfriend.superfriend.entity.Prompt prompt = new com.superfriend.superfriend.entity.Prompt();
        return prompt.getSystemPrompt(com.superfriend.superfriend.entity.Prompt.Mode.LITE_TASK);
    }

    /**
     * 判断是否需要执行搜索预加载
     */
    private boolean shouldPerformSearch(String message) {
        if (message == null || message.isEmpty()) {
            return false;
        }

        String lower = message.toLowerCase();

        // 操作性查询不需要搜索
        String[] operationalPatterns = {
            "帮我写", "写一个", "写个", "实现", "创建", "生成代码",
            "帮我改", "修改", "重构", "优化代码",
            "执行", "运行", "调用", "操作"
        };

        for (String pattern : operationalPatterns) {
            if (lower.contains(pattern)) {
                return false;
            }
        }

        // 包含时间关键词需要搜索
        String[] timeKeywords = {"最新", "最近", "今天", "昨天", "今年", "2025", "2024", "新闻", "动态", "招聘", "校招"};
        for (String keyword : timeKeywords) {
            if (lower.contains(keyword)) {
                return true;
            }
        }

        // 疑问句通常需要搜索
        if (lower.contains("？") || lower.contains("?") ||
            lower.contains("是什么") || lower.contains("怎么样") ||
            lower.contains("如何") || lower.contains("为什么") ||
            lower.contains("哪些") || lower.contains("多少")) {
            return true;
        }

        return false;
    }

    public void chatWithMCPWithSkills(String message,
                                      String sessionId,
                                      String model,
                                      List<Map<String, String>> history,
                                      Long userId,
                                      boolean enableSkills,
                                      Consumer<AIChatResponse> onResponse) {
        chatWithMCPWithSkills(message, sessionId, model, history, userId, enableSkills, false, onResponse);
    }

    public void chatWithMCPWithSkills(String message,
                                      String sessionId,
                                      String model,
                                      List<Map<String, String>> history,
                                      Long userId,
                                      boolean enableSkills,
                                      boolean enableKnowledgeExtraction,
                                      Consumer<AIChatResponse> onResponse) {
        log.info("{} 开始中等任务对话，Session ID: {}, Model: {}",
            ChatModeConfig.MEDIUM_TASK.getLogPrefix(), sessionId, model);

        AgentExecutionService.ExecutionConfig config = ChatModeConfig.MEDIUM_TASK.createExecutionConfig();
        config.setEnableKnowledgeExtraction(enableKnowledgeExtraction);

        agentExecutionService.executeWithOptimizations(
            message,
            sessionId,
            model,
            history,
            userId,
            config,
            onResponse
        );
    }

    /**
     * 中等任务对话 - 统一接口版本
     * 与 Lite 模式接口风格一致，接受 AIChatRequest 和 UserIntent
     * 支持多模态消息的模型自动选择
     */
    public void chatWithMCPWithSkills(AIChatRequest request,
                                      UserIntent intent,
                                      Consumer<AIChatResponse> onResponse) {
        log.info("{} 开始中等任务对话，Session ID: {}, Model: {}, Multimodal: {}",
            ChatModeConfig.MEDIUM_TASK.getLogPrefix(), request.getSessionId(), request.getModel(), request.isMultimodal());

        // 【改进】多模态请求自动选择支持所需模态的模型
        if (request.isMultimodal() || (request.getImages() != null && !request.getImages().isEmpty())) {
            ModelCapabilityService.ModelSelectionResult selectionResult = modelCapabilityService.autoSelectModel(
                request.getUserId(),
                request.getContent(),
                request.getImages(),
                request.getModel()
            );

            if (!selectionResult.isSuccessful()) {
                AIChatResponse errorResponse = new AIChatResponse();
                errorResponse.setError("未找到支持所需模态的模型。所需模态: " + selectionResult.getRequiredModalities());
                errorResponse.setSessionId(request.getSessionId());
                errorResponse.setModel(request.getModel());
                errorResponse.setDone(true);
                onResponse.accept(errorResponse);
                return;
            }

            if (selectionResult.isModelSwitched()) {
                AIModelConfig selectedModel = selectionResult.getSelectedModel();
                request.setModel(selectedModel.getConfigId());
                log.info("模型自动切换: {} -> {}，原因: 支持模态 {}",
                    request.getModel(), selectedModel.getModelId(), selectionResult.getRequiredModalities());
            }
        }

        AgentExecutionService.ExecutionConfig config = ChatModeConfig.MEDIUM_TASK.createExecutionConfig();
        if (request.getEnableKnowledgeExtraction() != null) {
            config.setEnableKnowledgeExtraction(request.getEnableKnowledgeExtraction());
        }
        // 如果 intent 指定需要搜索，确保启用预搜索
        if (intent != null && intent.isNeedsSearch()) {
            config.setEnableWebSearchPreload(true);
        }

        // 【改进】传递 intent 到执行服务
        agentExecutionService.executeWithOptimizationsMultimodal(
            request,
            intent,
            config,
            onResponse
        );
    }

    public List<Map<String, Object>> recommendTools(String userRequest) {
        List<Map<String, Object>> recommendations = new ArrayList<>();
        
        try {
            List<McpToolDefinition> allTools = listSelectedTools();
            
            if (allTools.isEmpty()) {
                return recommendations;
            }
            
            String lowerRequest = userRequest.toLowerCase();
            
            for (McpToolDefinition tool : allTools) {
                Map<String, Object> rec = new HashMap<>();
                rec.put("toolName", tool.getName());
                rec.put("description", tool.getDescription());
                rec.put("relevanceScore", calculateRelevanceScore(lowerRequest, tool));
                recommendations.add(rec);
            }
            
            recommendations.sort((a, b) -> {
                double scoreA = (Double) a.get("relevanceScore");
                double scoreB = (Double) b.get("relevanceScore");
                return Double.compare(scoreB, scoreA);
            });
            
            if (recommendations.size() > 10) {
                recommendations = recommendations.subList(0, 10);
            }
            
        } catch (Exception e) {
            log.error("工具推荐失败：{}", e.getMessage(), e);
        }
        
        return recommendations;
    }
    
    private double calculateRelevanceScore(String userRequest, McpToolDefinition tool) {
        double score = 0.0;
        
        String toolName = tool.getName() != null ? tool.getName().toLowerCase() : "";
        String description = tool.getDescription() != null ? tool.getDescription().toLowerCase() : "";
        
        String[] requestWords = userRequest.split("\\s+");
        for (String word : requestWords) {
            if (word.length() < 3) continue;
            
            if (toolName.contains(word)) {
                score += 0.3;
            }
            if (description.contains(word)) {
                score += 0.1;
            }
        }
        
        return Math.min(score, 1.0);
    }

    public void chatWithMCP(String message,
                           String sessionId,
                           String model,
                           Consumer<AIChatResponse> onResponse) {
        chatWithMCP(message, sessionId, model, null, null, onResponse);
    }

    public void chatWithMCP(String message,
                           String sessionId,
                           String model,
                           List<Map<String, String>> history,
                           Consumer<AIChatResponse> onResponse) {
        chatWithMCP(message, sessionId, model, history, null, onResponse);
    }

    /**
     * MCP 模式对话 - 智能判断任务复杂度，自动选择 ReAct 或 Plan-Execute 模式
     * 统一通过 AgentExecutionService 执行
     */
    public void chatWithMCP(String message,
                           String sessionId,
                           String model,
                           List<Map<String, String>> history,
                           Long userId,
                           Consumer<AIChatResponse> onResponse) {

        log.info("{} 开始 MCP 对话，Session ID: {}, Model: {}",
            ChatModeConfig.MCP.getLogPrefix(), sessionId, model);

        AgentExecutionService.ExecutionConfig config = ChatModeConfig.MCP.createExecutionConfig();

        agentExecutionService.executeWithOptimizations(
            message,
            sessionId,
            model,
            history,
            userId,
            config,
            onResponse
        );
    }

    public void chatWithMCPComplex(String message,
                                   String sessionId,
                                   String model,
                                   List<Map<String, String>> history,
                                   Long userId,
                                   Consumer<AIChatResponse> onResponse) {

        log.info("{} 开始复杂任务对话，Session ID: {}, Model: {}",
            ChatModeConfig.COMPLEX_TASK.getLogPrefix(), sessionId, model);

        AgentExecutionService.ExecutionConfig config = ChatModeConfig.COMPLEX_TASK.createExecutionConfig();

        agentExecutionService.executeWithOptimizations(
            message,
            sessionId,
            model,
            history,
            userId,
            config,
            onResponse
        );
    }

    /**
     * 复杂任务对话 - 统一接口版本
     * 与 Lite 模式接口风格一致，接受 AIChatRequest 和 UserIntent
     * 支持多模态消息的模型自动选择
     */
    public void chatWithMCPComplex(AIChatRequest request,
                                   UserIntent intent,
                                   Consumer<AIChatResponse> onResponse) {
        log.info("{} 开始复杂任务对话，Session ID: {}, Model: {}, Multimodal: {}",
            ChatModeConfig.COMPLEX_TASK.getLogPrefix(), request.getSessionId(), request.getModel(), request.isMultimodal());

        // 【改进】多模态请求自动选择支持所需模态的模型
        if (request.isMultimodal() || (request.getImages() != null && !request.getImages().isEmpty())) {
            ModelCapabilityService.ModelSelectionResult selectionResult = modelCapabilityService.autoSelectModel(
                request.getUserId(),
                request.getContent(),
                request.getImages(),
                request.getModel()
            );

            if (!selectionResult.isSuccessful()) {
                AIChatResponse errorResponse = new AIChatResponse();
                errorResponse.setError("未找到支持所需模态的模型。所需模态: " + selectionResult.getRequiredModalities());
                errorResponse.setSessionId(request.getSessionId());
                errorResponse.setModel(request.getModel());
                errorResponse.setDone(true);
                onResponse.accept(errorResponse);
                return;
            }

            if (selectionResult.isModelSwitched()) {
                AIModelConfig selectedModel = selectionResult.getSelectedModel();
                request.setModel(selectedModel.getConfigId());
                log.info("模型自动切换: {} -> {}，原因: 支持模态 {}",
                    request.getModel(), selectedModel.getModelId(), selectionResult.getRequiredModalities());
            }
        }

        AgentExecutionService.ExecutionConfig config = ChatModeConfig.COMPLEX_TASK.createExecutionConfig();
        if (request.getEnableKnowledgeExtraction() != null) {
            config.setEnableKnowledgeExtraction(request.getEnableKnowledgeExtraction());
        }
        // 如果 intent 指定需要搜索，确保启用预搜索
        if (intent != null && intent.isNeedsSearch()) {
            config.setEnableWebSearchPreload(true);
        }

        // 【改进】传递 intent 到执行服务
        agentExecutionService.executeWithOptimizationsMultimodal(
            request,
            intent,
            config,
            onResponse
        );
    }

    // ============ 多模态版本的方法 ============

    /**
     * 中等任务对话 - 多模态版本
     * 支持文本+图片等多模态消息
     * 自动选择支持所需模态的模型
     *
     * @param request AI 对话请求（支持多模态）
     * @param onResponse 响应回调
     */
    public void chatWithMCPWithSkillsMultimodal(AIChatRequest request,
                                                 Consumer<AIChatResponse> onResponse) {
        chatWithMCPWithSkillsMultimodal(request, null, onResponse);
    }

    /**
     * 中等任务对话 - 多模态版本（带意图）
     * 与 Lite 模式接口风格一致
     *
     * @param request AI 对话请求（支持多模态）
     * @param intent 用户意图（可选）
     * @param onResponse 响应回调
     */
    public void chatWithMCPWithSkillsMultimodal(AIChatRequest request,
                                                 UserIntent intent,
                                                 Consumer<AIChatResponse> onResponse) {
        String sessionId = request.getSessionId();
        String model = request.getModel();
        Long userId = request.getUserId();

        log.info("{} 开始中等任务对话（多模态），Session ID: {}, Model: {}, Multimodal: {}",
            ChatModeConfig.MEDIUM_TASK.getLogPrefix(), sessionId, model, request.isMultimodal());

        // 自动选择支持所需模态的模型
        ModelCapabilityService.ModelSelectionResult selectionResult = modelCapabilityService.autoSelectModel(
            userId,
            request.getContent(),
            request.getImages(),
            model
        );

        if (!selectionResult.isSuccessful()) {
            AIChatResponse errorResponse = new AIChatResponse();
            errorResponse.setError("未找到支持所需模态的模型。所需模态: " + selectionResult.getRequiredModalities());
            errorResponse.setSessionId(sessionId);
            errorResponse.setModel(model);
            errorResponse.setDone(true);
            onResponse.accept(errorResponse);
            return;
        }

        // 如果模型被切换，更新请求中的模型
        if (selectionResult.isModelSwitched()) {
            AIModelConfig selectedModel = selectionResult.getSelectedModel();
            request.setModel(selectedModel.getConfigId());

            log.info("模型自动切换: {} -> {}，原因: 支持模态 {}",
                model, selectedModel.getModelId(), selectionResult.getRequiredModalities());
        }

        AgentExecutionService.ExecutionConfig config = ChatModeConfig.MEDIUM_TASK.createExecutionConfig();
        if (request.getEnableKnowledgeExtraction() != null) {
            config.setEnableKnowledgeExtraction(request.getEnableKnowledgeExtraction());
        }
        // 如果 intent 指定需要搜索，确保启用预搜索
        if (intent != null && intent.isNeedsSearch()) {
            config.setEnableWebSearchPreload(true);
        }

        // 【改进】传递 intent 到执行服务
        agentExecutionService.executeWithOptimizationsMultimodal(
            request,
            intent,
            config,
            onResponse
        );
    }

    /**
     * 复杂任务对话 - 多模态版本
     * 支持文本+图片等多模态消息
     *
     * @param request AI 对话请求（支持多模态）
     * @param onResponse 响应回调
     */
    public void chatWithMCPComplexMultimodal(AIChatRequest request,
                                              Consumer<AIChatResponse> onResponse) {
        chatWithMCPComplexMultimodal(request, null, onResponse);
    }

    /**
     * 复杂任务对话 - 多模态版本（带意图）
     * 与 Lite 模式接口风格一致
     *
     * @param request AI 对话请求（支持多模态）
     * @param intent 用户意图（可选）
     * @param onResponse 响应回调
     */
    public void chatWithMCPComplexMultimodal(AIChatRequest request,
                                              UserIntent intent,
                                              Consumer<AIChatResponse> onResponse) {
        String sessionId = request.getSessionId();
        String model = request.getModel();
        Long userId = request.getUserId();

        log.info("{} 开始复杂任务对话（多模态），Session ID: {}, Model: {}, Multimodal: {}",
            ChatModeConfig.COMPLEX_TASK.getLogPrefix(), sessionId, model, request.isMultimodal());

        // 自动选择支持所需模态的模型
        ModelCapabilityService.ModelSelectionResult selectionResult = modelCapabilityService.autoSelectModel(
            userId,
            request.getContent(),
            request.getImages(),
            model
        );

        if (!selectionResult.isSuccessful()) {
            AIChatResponse errorResponse = new AIChatResponse();
            errorResponse.setError("未找到支持所需模态的模型。所需模态: " + selectionResult.getRequiredModalities());
            errorResponse.setSessionId(sessionId);
            errorResponse.setModel(model);
            errorResponse.setDone(true);
            onResponse.accept(errorResponse);
            return;
        }

        // 如果模型被切换，更新请求中的模型
        if (selectionResult.isModelSwitched()) {
            AIModelConfig selectedModel = selectionResult.getSelectedModel();
            request.setModel(selectedModel.getConfigId());

            log.info("模型自动切换: {} -> {}，原因: 支持模态 {}",
                model, selectedModel.getModelId(), selectionResult.getRequiredModalities());
        }

        AgentExecutionService.ExecutionConfig config = ChatModeConfig.COMPLEX_TASK.createExecutionConfig();
        if (request.getEnableKnowledgeExtraction() != null) {
            config.setEnableKnowledgeExtraction(request.getEnableKnowledgeExtraction());
        }
        // 如果 intent 指定需要搜索，确保启用预搜索
        if (intent != null && intent.isNeedsSearch()) {
            config.setEnableWebSearchPreload(true);
        }

        // 【改进】传递 intent 到执行服务
        agentExecutionService.executeWithOptimizationsMultimodal(
            request,
            intent,
            config,
            onResponse
        );
    }

    public String buildSystemPrompt(List<McpToolDefinition> tools) {
        return buildSystemPrompt(tools, null, com.superfriend.superfriend.entity.Prompt.Mode.MCP);
    }

    public String buildSystemPrompt(List<McpToolDefinition> tools, Long userId) {
        return buildSystemPrompt(tools, userId, com.superfriend.superfriend.entity.Prompt.Mode.MCP);
    }

    // 系统提示词最大长度（字符数），约等于 8000 tokens
    private static final int MAX_SYSTEM_PROMPT_LENGTH = 32000;

    public String buildSystemPrompt(List<McpToolDefinition> tools, Long userId, com.superfriend.superfriend.entity.Prompt.Mode mode) {
        com.superfriend.superfriend.entity.Prompt prompt = new com.superfriend.superfriend.entity.Prompt();
        StringBuilder sb = new StringBuilder();

        sb.append(prompt.getSystemPrompt(mode));
        sb.append("\n\n---\n\n");

        // 注入 Skills 目录（已精简）
        try {
            String skillCatalog = skillRegistry.generateSkillCatalogPromptForUser(userId, skillService);
            if (skillCatalog != null && !skillCatalog.isEmpty()) {
                sb.append(skillCatalog);
                log.debug("[userId={}] 已注入技能目录到系统提示（{} 字符）", userId, skillCatalog.length());
            }
        } catch (Exception e) {
            log.warn("[userId={}] 注入技能目录失败：{}", userId, e.getMessage());
        }

        // 工具列表（精简版：只列出名称，详细定义通过 function calling 传递）
        sb.append("\n\n## 🔧 可用工具\n");
        sb.append("工具详细参数已通过 API 传递，按需调用即可。格式: `server__tool_name`\n");

        // 检查并截断过长的系统提示词
        String result = sb.toString();
        if (result.length() > MAX_SYSTEM_PROMPT_LENGTH) {
            log.warn("[userId={}] 系统提示词过长 ({} 字符)，正在截断", userId, result.length());
            result = result.substring(0, MAX_SYSTEM_PROMPT_LENGTH);
            result += "\n\n[系统提示词已截断，请使用 load_skill 工具获取完整技能信息]";
            log.info("[userId={}] 系统提示词截断完成，新长度: {} 字符", userId, result.length());
        }

        return result;
    }

    /**
     * 生成工具描述提示（精简版）
     * 注意：完整的工具定义通过 OpenAI function calling 格式传递，这里不再重复
     */
    private String generateToolDescriptionPrompt(List<McpToolDefinition> tools) {
        if (tools == null || tools.isEmpty()) {
            return "";
        }
        // 工具定义已通过 ToolDefinitionForLLM 传递给 LLM，这里不需要重复
        return "";
    }

    public ModelResponse callLLMWithTools(List<Map<String, Object>> messages,
                                         String model,
                                         List<McpToolDefinition> tools,
                                         String apiUrl,
                                         String apiKey,
                                         String sessionId,
                                         Consumer<AIChatResponse> onResponse,
                                         long[] totalInputTokens,
                                         long[] totalOutputTokens) throws IOException {

        log.info("[McpHostService] callLLMWithTools 开始，工具数量: {}", tools != null ? tools.size() : 0);

        // 在调用 API 之前清理不完整的 tool_calls 消息
        cleanIncompleteToolCalls(messages);

        List<ToolDefinitionForLLM> toolDefs = new ArrayList<>();
        for (McpToolDefinition tool : tools) {
            if (tool.getName() == null || tool.getName().isEmpty()) {
                continue;
            }
            ToolDefinitionForLLM def = ToolDefinitionForLLM.fromMcpTool(tool);
            if (def != null) {
                toolDefs.add(def);
            }
        }
        
        log.info("[McpHostService] 转换后 toolDefs 数量: {}", toolDefs.size());
        
        LLMRequest request = LLMRequest.fromConfig(model, apiUrl, apiKey)
            .toBuilder()
            .messages(messages)
            .stream(true)
<<<<<<< HEAD
            .maxTokens(50000)
            .sessionId(sessionId)
=======
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
            .build();

        final StringBuilder contentBuilder = new StringBuilder();
        final StringBuilder reasoningBuilder = new StringBuilder();
        final List<LLMCompleteResponse.ToolCall> rawToolCalls = new ArrayList<>();
        final String[] errorHolder = {null};
        final JsonNode[] usageHolder = {null};
        final boolean[] hasError = {false};
<<<<<<< HEAD
        final String[] finishReasonHolder = {null};  // 新增：捕获 finish_reason
=======
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
        final String finalModel = model;

        if (!toolDefs.isEmpty()) {
            llmClient.streamChatWithTools(request, toolDefs, chunk -> {
                if (chunk.isError()) {
                    errorHolder[0] = chunk.getError();
                    hasError[0] = true;
                    return;
                }
                if (chunk.hasReasoningContent()) {
                    reasoningBuilder.append(chunk.getReasoningContent());
                    AIChatResponse thinkingResp = new AIChatResponse();
                    thinkingResp.setSessionId(sessionId);
                    thinkingResp.setModel(finalModel);
                    thinkingResp.setDone(false);
                    thinkingResp.setType("thinking");
                    thinkingResp.setReasoningContent(chunk.getReasoningContent());
                    onResponse.accept(thinkingResp);
                }
                if (chunk.hasContent()) {
                    contentBuilder.append(chunk.getContent());
                    AIChatResponse contentResp = new AIChatResponse();
                    contentResp.setSessionId(sessionId);
                    contentResp.setModel(finalModel);
                    contentResp.setDone(false);
                    contentResp.setType("result");
                    contentResp.setContent(chunk.getContent());
                    onResponse.accept(contentResp);
                }
                if (chunk.hasAccumulatedToolCalls()) {
                    log.info("[McpHostService] 流式结束，获取累积的 tool_calls: {} 个", chunk.getAccumulatedToolCalls().size());
                    rawToolCalls.clear();
                    rawToolCalls.addAll(chunk.getAccumulatedToolCalls());
                }
                if (chunk.getUsage() != null) {
                    usageHolder[0] = chunk.getUsage();
                }
<<<<<<< HEAD
                // 新增：捕获 finish_reason
                if (chunk.getFinishReason() != null) {
                    finishReasonHolder[0] = chunk.getFinishReason();
                    log.info("[McpHostService] 收到 finish_reason: {}", chunk.getFinishReason());
                }
=======
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
            });
        } else {
            llmClient.streamChat(request, chunk -> {
                if (chunk.isError()) {
                    errorHolder[0] = chunk.getError();
                    hasError[0] = true;
                    return;
                }
                if (chunk.hasReasoningContent()) {
                    reasoningBuilder.append(chunk.getReasoningContent());
                    AIChatResponse thinkingResp = new AIChatResponse();
                    thinkingResp.setSessionId(sessionId);
                    thinkingResp.setModel(finalModel);
                    thinkingResp.setDone(false);
                    thinkingResp.setType("thinking");
                    thinkingResp.setReasoningContent(chunk.getReasoningContent());
                    onResponse.accept(thinkingResp);
                }
                if (chunk.hasContent()) {
                    contentBuilder.append(chunk.getContent());
                    AIChatResponse contentResp = new AIChatResponse();
                    contentResp.setSessionId(sessionId);
                    contentResp.setModel(finalModel);
                    contentResp.setDone(false);
                    contentResp.setType("result");
                    contentResp.setContent(chunk.getContent());
                    onResponse.accept(contentResp);
                }
                if (chunk.getUsage() != null) {
                    usageHolder[0] = chunk.getUsage();
                }
<<<<<<< HEAD
                // 新增：捕获 finish_reason
                if (chunk.getFinishReason() != null) {
                    finishReasonHolder[0] = chunk.getFinishReason();
                    log.info("[McpHostService] 收到 finish_reason: {}", chunk.getFinishReason());
                }
=======
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
            });
        }

        if (hasError[0]) {
            throw new RuntimeException("大模型 API 请求失败：" + errorHolder[0]);
        }

        if (usageHolder[0] != null) {
            JsonNode usage = usageHolder[0];
            if (usage.has("prompt_tokens")) {
                totalInputTokens[0] += usage.get("prompt_tokens").asLong();
<<<<<<< HEAD
            } else if (usage.has("input_tokens")) {
                totalInputTokens[0] += usage.get("input_tokens").asLong();
            }
            if (usage.has("completion_tokens")) {
                totalOutputTokens[0] += usage.get("completion_tokens").asLong();
            } else if (usage.has("output_tokens")) {
                totalOutputTokens[0] += usage.get("output_tokens").asLong();
=======
            }
            if (usage.has("completion_tokens")) {
                totalOutputTokens[0] += usage.get("completion_tokens").asLong();
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
            }
        }

        ModelResponse response = new ModelResponse();
        String content = contentBuilder.toString();
        if (!content.trim().isEmpty()) {
            response.setContent(content);
        }
<<<<<<< HEAD

=======
        
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
        String reasoningContent = reasoningBuilder.toString();
        if (!reasoningContent.trim().isEmpty()) {
            response.setReasoningContent(reasoningContent);
        }

<<<<<<< HEAD
        // 设置 finishReason
        if (finishReasonHolder[0] != null) {
            response.setFinishReason(finishReasonHolder[0]);
            if ("length".equals(finishReasonHolder[0])) {
                log.warn("[McpHostService] LLM 输出被截断 (finish_reason=length)，可能需要续写");
            }
        }

=======
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
        if (rawToolCalls != null && !rawToolCalls.isEmpty()) {
            log.info("[McpHostService] 累积完成，共 {} 个工具调用，开始转换为 ToolCall 对象", rawToolCalls.size());
            List<ToolCall> toolCalls = new ArrayList<>();
            
            for (LLMCompleteResponse.ToolCall rawTC : rawToolCalls) {
                log.info("[McpHostService] 处理工具调用: id={}, name={}, argumentsStr={}", 
                    rawTC.getId(), rawTC.getName(), rawTC.getArgumentsStr());
                ToolCall toolCall = new ToolCall();
                toolCall.setId(rawTC.getId());
                String functionName = rawTC.getName();
                
                if ("load_skill".equals(functionName) || "read_skill_resource".equals(functionName) || "run_skill_script".equals(functionName)) {
                    toolCall.setServerName("skill");
                    toolCall.setToolName(functionName);
                    toolCall.setArguments(rawTC.getArguments() != null ? rawTC.getArguments() : new HashMap<>());
                    toolCalls.add(toolCall);
                    continue;
                }
                
                if (functionName.contains("__")) {
                    String[] parts = functionName.split("__", 2);
                    if (parts.length == 2) {
                        toolCall.setServerName(parts[0]);
                        toolCall.setToolName(parts[1]);
                    }
                } else if (functionName.contains(".")) {
                    String[] parts = functionName.split("\\.", 2);
                    if (parts.length == 2) {
                        toolCall.setServerName(parts[0]);
                        toolCall.setToolName(parts[1]);
                    }
                } else {
                    if (!runningServers.isEmpty()) {
                        toolCall.setServerName(runningServers.keySet().iterator().next());
                        toolCall.setToolName(functionName);
                    } else {
                        continue;
                    }
                }
                
                toolCall.setArguments(rawTC.getArguments() != null ? rawTC.getArguments() : new HashMap<>());
                toolCalls.add(toolCall);
                log.info("[McpHostService] 转换完成: serverName={}, toolName={}, arguments={}", 
                    toolCall.getServerName(), toolCall.getToolName(), toolCall.getArguments());
            }
            
            response.setToolCalls(toolCalls);
            log.info("[McpHostService] ModelResponse 设置完成，toolCalls 数量: {}", toolCalls.size());
        }
        
        log.info("[McpHostService] callLLMWithTools 返回: hasToolCalls={}, hasContent={}, hasReasoningContent={}", 
            response.hasToolCalls(), response.hasContent(), response.hasReasoningContent());
        
        return response;
    }
    
    @Autowired
    @Lazy
    private com.superfriend.superfriend.agent.tool.SmartToolResultProcessor smartToolResultProcessor;

    @Autowired
    @Lazy
    private com.superfriend.superfriend.agent.tool.AntiDetectionService antiDetectionService;

    @Autowired
    @Lazy
    private com.superfriend.superfriend.agent.tool.PuppeteerHelper puppeteerHelper;

    @Autowired
    @Lazy
    private FileLifecycleManager fileLifecycleManager;

    public String compressToolResult(McpToolCallResponse result) {
        return compressToolResult(result, null, null);
    }

    public String compressToolResult(McpToolCallResponse result, String toolName, Map<String, Object> parameters) {
        if (!result.isSuccess()) {
            return "❌ 工具调用失败：" + result.getError() + "\n\n💡 建议：检查参数是否正确，或尝试使用其他工具。";
        }

        StringBuilder sb = new StringBuilder();
        if (result.getContent() != null) {
            for (McpToolCallResponse.ContentItem item : result.getContent()) {
                if ("text".equals(item.getType()) && item.getText() != null) {
                    String text = item.getText();

                    // 使用智能结果处理器
                    if (toolName != null) {
                        com.superfriend.superfriend.agent.tool.SmartToolResultProcessor.ProcessedResult processed =
                            smartToolResultProcessor.process(toolName, text, parameters);

                        // 如果检测到问题，添加提示
                        if (processed.hasIssues()) {
                            sb.append("⚠️ 检测到问题：\n");

                            if (processed.isNeedsJavaScript()) {
                                sb.append("- 页面需要 JavaScript 渲染\n");
                                if (processed.getAlternativeTool() != null) {
                                    sb.append("- 💡 建议使用：").append(processed.getAlternativeTool()).append("\n");
                                }
                            }

                            if (processed.isBlockedByAntiCrawl()) {
                                sb.append("- 检测到反爬机制\n");
                                sb.append("- 💡 建议：使用 puppeteer 模拟浏览器访问\n");
                            }

                            if (processed.isContentEmpty()) {
                                sb.append("- 页面内容为空或需要登录\n");
                            }

                            sb.append("\n");
                        }

                        // 使用智能截断
                        if (text.length() > 15000) {
                            text = smartToolResultProcessor.smartTruncate(text, 15000);
                        }

                        // 如果有摘要，使用摘要
                        if (processed.getSummary() != null && !processed.getSummary().isEmpty()) {
                            sb.append("📄 内容摘要：\n").append(processed.getSummary()).append("\n\n");
                        } else {
                            sb.append(text);
                        }

                        // 如果提取到了链接，显示部分链接
                        if (processed.getExtractedLinks() != null && !processed.getExtractedLinks().isEmpty()) {
                            sb.append("\n\n🔗 相关链接（前5个）：\n");
                            int linkCount = 0;
                            for (String link : processed.getExtractedLinks()) {
                                if (linkCount++ >= 5) break;
                                sb.append("- ").append(link).append("\n");
                            }
                        }
                    } else {
                        // 回退到简单截断
                        int maxLength = 15000;
                        if (text.length() > maxLength) {
                            sb.append(text.substring(0, maxLength));
                            sb.append("\n\n[内容过长已截断，总长度：").append(text.length()).append(" 字符]");
                        } else {
                            sb.append(text);
                        }
                    }
                }
            }
        }
        return sb.toString();
    }

    /**
     * 智能参数增强：针对爬取工具自动优化参数
     * 根据目标网站的风险等级自动调整等待时间、添加反检测脚本等
     */
    private Map<String, Object> enhanceCrawlParameters(String serverName, String toolName, Map<String, Object> arguments) {
        if (arguments == null) {
            arguments = new HashMap<>();
        }

        String fullToolName = serverName + "__" + toolName;

        // 只处理爬取相关的工具
        if (!fullToolName.contains("fetch") && !fullToolName.contains("puppeteer")) {
            return arguments;
        }

        String url = (String) arguments.get("url");
        if (url == null || url.isEmpty()) {
            return arguments;
        }

        // 创建增强后的参数副本
        Map<String, Object> enhancedArgs = new HashMap<>(arguments);

        // 获取网站配置
        com.superfriend.superfriend.agent.tool.AntiDetectionService.CrawlConfig config =
            antiDetectionService.getConfig(url, null);
        com.superfriend.superfriend.agent.tool.PuppeteerHelper.SiteConfig siteConfig =
            puppeteerHelper.getSiteConfig(url);

        log.info("[智能爬取] URL: {}, 网站类型: {}, 推荐等待时间: {}ms",
            url, siteConfig.getSiteName(), siteConfig.getWaitTime());

        // 针对 Puppeteer 工具的增强
        if (fullToolName.contains("puppeteer")) {
            // 自动添加等待时间
            if (!enhancedArgs.containsKey("wait_time") && !enhancedArgs.containsKey("waitTime")) {
                enhancedArgs.put("wait_time", siteConfig.getWaitTime());
                log.info("[智能爬取] 自动设置等待时间: {}ms", siteConfig.getWaitTime());
            }

            // 高风险网站自动启用隐身模式
            if (antiDetectionService.assessRisk(antiDetectionService.extractDomain(url)) ==
                com.superfriend.superfriend.agent.tool.AntiDetectionService.RiskLevel.HIGH) {
                enhancedArgs.put("stealth_mode", true);
                log.info("[智能爬取] 高风险网站，自动启用隐身模式");
            }

            // 招聘网站自动启用滚动
            if (url.contains("jobs") || url.contains("career") || url.contains("zhipin") ||
                url.contains("lagou") || url.contains("nowcoder")) {
                enhancedArgs.put("scroll", true);
                enhancedArgs.put("scroll_times", 3);
                log.info("[智能爬取] 招聘网站，自动启用滚动加载");
            }
        }

        // 针对 Fetch 工具的增强
        if (fullToolName.contains("fetch")) {
            // 自动添加优化的请求头
            if (!enhancedArgs.containsKey("headers")) {
                Map<String, String> headers = config.getHeaders();
                enhancedArgs.put("headers", headers);
                log.info("[智能爬取] 自动添加优化的请求头");
            }
        }

        return enhancedArgs;
    }

    /**
     * 发送 SSE cost 事件到前端
     */
    private void sendCostUpdate(String sessionId, String model, long inputTokens,
                                long outputTokens, double cost,
                                Consumer<AIChatResponse> onResponse) {
        try {
            CostTrackingService.SessionCost sessionCost = costTrackingService.getSessionCost(sessionId);

            AIChatResponse costResponse = new AIChatResponse();
            costResponse.setSessionId(sessionId);
            costResponse.setModel(model);
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
                sessionId, String.format("%.6f", cost), inputTokens, outputTokens);
        } catch (Exception e) {
            log.warn("发送成本更新失败: {}", e.getMessage());
        }
    }

    /**
     * 清理不完整的 tool_calls 消息
     * 当 LLM 调用失败时，消息列表中可能存在没有对应 tool 响应的 assistant 消息
     * 这会导致 API 报错：assistant message with 'tool_calls' must be followed by tool messages
     *
     * OpenAI API 要求：
     * 1. assistant 消息中的每个 tool_call_id 必须有对应的 tool 消息
     * 2. 这些 tool 消息必须紧随 assistant 消息之后（中间不能有其他消息）
     */
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
                            log.warn("[McpHostService] 发现不完整的 tool_calls，缺失的 tool_call_id: {}", missingIds);
                        }
                        if (hasBlockingMessage) {
                            log.warn("[McpHostService] 发现 tool_calls 消息顺序错误，assistant 消息后存在非 tool 消息");
                        }
                        toRemove.add(msg);
                        log.info("[McpHostService] 移除不完整的 assistant 消息（含 tool_calls）");
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
                    log.info("[McpHostService] 移除孤立的 tool 消息，tool_call_id: {}", toolCallId);
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

    /**
     * 压缩上下文消息，防止无限增长
     * 当消息数量超过阈值时，保留系统消息和最近的对话
     */
    private void compressMessagesIfNeeded(List<Map<String, Object>> messages, int maxMessages) {
        if (messages.size() <= maxMessages) {
            return;
        }

        log.info("[上下文压缩] 消息数量 {} 超过阈值 {}，开始压缩", messages.size(), maxMessages);

        // 保留系统消息（第一条）
        Map<String, Object> systemMessage = messages.get(0);

        // 保留最近的对话
        int keepRecent = maxMessages - 2; // 减去系统消息和摘要消息
        int startIndex = messages.size() - keepRecent;

        // 创建摘要消息
        StringBuilder summary = new StringBuilder();
        summary.append("[历史对话摘要]\n");
        int summarizedCount = 0;
        for (int i = 1; i < startIndex; i++) {
            Map<String, Object> msg = messages.get(i);
            String role = (String) msg.get("role");
            String content = msg.get("content") != null ? msg.get("content").toString() : "";
            if (content.length() > 200) {
                content = content.substring(0, 200) + "...";
            }
            if ("user".equals(role) || "assistant".equals(role)) {
                summary.append(role).append(": ").append(content).append("\n");
                summarizedCount++;
            }
            if (summarizedCount >= 10) {
                summary.append("... (更多历史已省略)\n");
                break;
            }
        }

        // 重建消息列表
        List<Map<String, Object>> compressedMessages = new ArrayList<>();
        compressedMessages.add(systemMessage);

        // 添加摘要消息
        Map<String, Object> summaryMsg = new HashMap<>();
        summaryMsg.put("role", "system");
        summaryMsg.put("content", summary.toString());
        compressedMessages.add(summaryMsg);

        // 添加最近的对话
        for (int i = startIndex; i < messages.size(); i++) {
            compressedMessages.add(messages.get(i));
        }

        messages.clear();
        messages.addAll(compressedMessages);

        log.info("[上下文压缩] 压缩完成，消息数量从 {} 减少到 {}", messages.size() + (startIndex - 2), messages.size());
    }

    /**
     * 生成工具调用的标准化签名
     * 对参数进行排序，确保相同参数不同顺序生成相同的签名
     */
    private String generateToolCallSignature(String toolName, Map<String, Object> arguments) {
        StringBuilder sb = new StringBuilder();
        sb.append(toolName).append(":");

        if (arguments != null && !arguments.isEmpty()) {
            // 按 key 排序
            List<String> sortedKeys = new ArrayList<>(arguments.keySet());
            Collections.sort(sortedKeys);

            for (String key : sortedKeys) {
                Object value = arguments.get(key);
                sb.append(key).append("=");
                if (value != null) {
                    String valueStr = value.toString();
                    // 截断过长的值
                    if (valueStr.length() > 100) {
                        valueStr = valueStr.substring(0, 100) + "...";
                    }
                    sb.append(valueStr);
                }
                sb.append(";");
            }
        }

        return sb.toString();
    }

    private McpServersFile createDefaultConfig() {
        McpServersFile file = new McpServersFile();
        Map<String, McpServerConfig> servers = new HashMap<>();
        
        McpServerConfig fsConfig = new McpServerConfig();
        fsConfig.setCommand("npx");
        fsConfig.setArgs(new String[]{"-y", "@modelcontextprotocol/server-filesystem", "."});
        fsConfig.setEnv(new HashMap<>());
        fsConfig.setDisabled(false);
        fsConfig.setTimeout(60000);
        fsConfig.setDescription("文件系统操作");
        
        servers.put("filesystem", fsConfig);
        file.setMcpServers(servers);
        
        return file;
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
    
    public Map<String, Object> getToolUsageStats() {
        Map<String, Object> stats = new HashMap<>();
        
        List<Map<String, Object>> toolStatsList = new ArrayList<>();
        for (Map.Entry<String, ToolStats> entry : toolStats.entrySet()) {
            Map<String, Object> toolStat = new HashMap<>();
            toolStat.put("toolName", entry.getKey());
            toolStat.put("totalCalls", entry.getValue().getTotalCalls());
            toolStat.put("successRate", String.format("%.2f%%", entry.getValue().getSuccessRate() * 100));
            toolStat.put("avgExecutionTime", String.format("%.2fms", entry.getValue().getAverageExecutionTime()));
            toolStatsList.add(toolStat);
        }
        
        stats.put("tools", toolStatsList);
        return stats;
    }
    
    public Map<String, Object> getErrorStats() {
        Map<String, Object> stats = new HashMap<>();
        
        List<Map<String, Object>> errorStatsList = new ArrayList<>();
        for (Map.Entry<String, ErrorStats> entry : errorStats.entrySet()) {
            Map<String, Object> errorStat = new HashMap<>();
            errorStat.put("toolName", entry.getKey());
            errorStat.put("errorCount", entry.getValue().getErrorCount());
            errorStat.put("retryCount", entry.getValue().getRetryCount());
            errorStat.put("recoveryCount", entry.getValue().getRecoveryCount());
            errorStatsList.add(errorStat);
        }
        
        stats.put("errors", errorStatsList);
        return stats;
    }
    
    private static class ToolStats {
        private int totalCalls = 0;
        private int successCalls = 0;
        private long totalExecutionTime = 0;
        
        public void recordCall(boolean success, long executionTime) {
            totalCalls++;
            if (success) successCalls++;
            totalExecutionTime += executionTime;
        }
        
        public double getSuccessRate() {
            return totalCalls == 0 ? 0.5 : (double) successCalls / totalCalls;
        }
        
        public double getAverageExecutionTime() {
            return totalCalls == 0 ? 0 : (double) totalExecutionTime / totalCalls;
        }
        
        public int getTotalCalls() {
            return totalCalls;
        }
    }
    
    private static class ErrorStats {
        private int errorCount = 0;
        private int retryCount = 0;
        private int recoveryCount = 0;
        
        public void recordError(String errorType) {
            errorCount++;
        }
        
        public void recordRetry() {
            retryCount++;
        }
        
        public void recordRecovery() {
            recoveryCount++;
        }
        
        public int getErrorCount() {
            return errorCount;
        }
        
        public int getRetryCount() {
            return retryCount;
        }
        
        public int getRecoveryCount() {
            return recoveryCount;
        }
    }
}
