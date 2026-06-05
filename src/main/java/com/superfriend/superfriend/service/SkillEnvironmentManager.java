package com.superfriend.superfriend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Skill 环境管理服务
 * 负责检测、配置和缓存 skill 所需的运行环境
 *
 * 功能：
 * 1. 读取 skill 的 env-config.json 配置
 * 2. 检测环境依赖是否满足
 * 3. 自动安装缺失的依赖
 * 4. 持久化环境状态（永久保存）
 * 5. 提供环境状态查询
 */
@Slf4j
@Service
public class SkillEnvironmentManager {

    @Value("${skills.system.path:src/main/resources/skills/system}")
    private String skillsPath;

    @Value("${skills.env.cache.path:${user.home}/.superfriend/skill-env-cache}")
    private String envCachePath;

    @Autowired
    private McpHostService mcpHostService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // 环境状态缓存：skillName -> EnvironmentStatus
    private final ConcurrentHashMap<String, EnvironmentStatus> envStatusCache = new ConcurrentHashMap<>();

    // 持久化的环境状态
    private Path cacheFilePath;

    @Data
    public static class EnvironmentStatus {
        private String skillName;
        private boolean ready;
        private long lastChecked;
        private long lastSetup;
        private Map<String, RequirementStatus> requirements = new HashMap<>();
        private String errorMessage;
    }

    @Data
    public static class RequirementStatus {
        private String name;
        private boolean installed;
        private String version;
        private long checkedAt;
        private String error;
    }

    @Data
    public static class EnvConfig {
        private String name;
        private String version;
        private String description;
        private List<Requirement> requirements;
        private Map<String, String> setupScripts;
        private Map<String, String> environmentVariables;
    }

    @Data
    public static class Requirement {
        private String name;
        private String version;
        private String checkCommand;
        private Map<String, String> installCommand;
        private Map<String, String> setupCommand;
        private String workingDirectory;
        private boolean required;
        private String description;
    }

    @PostConstruct
    public void init() {
        // 初始化缓存目录
        cacheFilePath = Paths.get(envCachePath, "env-status.json");
        try {
            Files.createDirectories(cacheFilePath.getParent());
            loadCachedStatus();
        } catch (IOException e) {
            log.warn("无法创建环境缓存目录: {}", e.getMessage());
        }

        log.info("Skill 环境管理器初始化完成，缓存路径: {}", cacheFilePath);
    }

    /**
     * 检查 skill 环境是否就绪
     *
     * @param skillName skill 名称
     * @param autoSetup 如果环境不满足，是否自动配置
     * @return 环境状态
     */
    public EnvironmentStatus checkEnvironment(String skillName, boolean autoSetup) {
        // 先检查缓存（24小时内有效）
        EnvironmentStatus cached = envStatusCache.get(skillName);
        if (cached != null && cached.isReady() &&
            System.currentTimeMillis() - cached.getLastChecked() < 24 * 60 * 60 * 1000) {
            log.debug("使用缓存的环境状态: {} -> ready={}", skillName, cached.isReady());
            return cached;
        }

        // 读取配置
        EnvConfig config = loadEnvConfig(skillName);
        if (config == null) {
            log.info("Skill {} 没有 env-config.json，跳过环境检查", skillName);
            EnvironmentStatus status = new EnvironmentStatus();
            status.setSkillName(skillName);
            status.setReady(true);
            status.setLastChecked(System.currentTimeMillis());
            return status;
        }

        // 检查环境
        EnvironmentStatus status = checkRequirements(skillName, config);

        // 如果不满足且需要自动配置
        if (!status.isReady() && autoSetup) {
            log.info("Skill {} 环境不满足，开始自动配置...", skillName);
            status = setupEnvironment(skillName, config);
        }

        // 更新缓存
        envStatusCache.put(skillName, status);
        saveCachedStatus();

        return status;
    }

    /**
     * 强制重新配置环境
     */
    public EnvironmentStatus forceSetup(String skillName) {
        EnvConfig config = loadEnvConfig(skillName);
        if (config == null) {
            EnvironmentStatus status = new EnvironmentStatus();
            status.setSkillName(skillName);
            status.setReady(true);
            return status;
        }

        EnvironmentStatus status = setupEnvironment(skillName, config);
        envStatusCache.put(skillName, status);
        saveCachedStatus();

        return status;
    }

    /**
     * 获取所有 skill 的环境状态
     */
    public Map<String, EnvironmentStatus> getAllEnvironmentStatus() {
        Map<String, EnvironmentStatus> result = new HashMap<>();

        // 扫描所有 skill 目录
        File skillsDir = new File(skillsPath);
        if (skillsDir.exists() && skillsDir.isDirectory()) {
            for (File skillDir : skillsDir.listFiles(File::isDirectory)) {
                String skillName = skillDir.getName();
                result.put(skillName, checkEnvironment(skillName, false));
            }
        }

        return result;
    }

    /**
     * 加载 skill 的环境配置
     * 支持两种格式：
     * 1. 扁平格式：{"requirements": [...], "setupScripts": {...}}
     * 2. 嵌套格式：{"environment": {"requirements": [...], "setupScripts": {...}}}
     */
    private EnvConfig loadEnvConfig(String skillName) {
        Path configPath = Paths.get(skillsPath, skillName, "env-config.json");
        if (!Files.exists(configPath)) {
            return null;
        }

        try {
            JsonNode root = objectMapper.readTree(configPath.toFile());
            EnvConfig config = new EnvConfig();

            // 提取基本字段
            config.setName(root.has("name") ? root.get("name").asText() : skillName);
            config.setVersion(root.has("version") ? root.get("version").asText() : "1.0.0");
            config.setDescription(root.has("description") ? root.get("description").asText() : "");

            // 检查是否是嵌套格式（environment 字段）
            JsonNode envNode = root.has("environment") ? root.get("environment") : root;

            // 解析 requirements
            if (envNode.has("requirements")) {
                JsonNode reqsNode = envNode.get("requirements");
                List<Requirement> requirements = objectMapper.readValue(
                    reqsNode.toString(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Requirement.class)
                );
                config.setRequirements(requirements);
            }

            // 解析 setupScripts
            if (envNode.has("setupScripts")) {
                JsonNode scriptsNode = envNode.get("setupScripts");
                Map<String, String> setupScripts = objectMapper.readValue(
                    scriptsNode.toString(),
                    objectMapper.getTypeFactory().constructMapType(Map.class, String.class, String.class)
                );
                config.setSetupScripts(setupScripts);
            }

            // 解析 environmentVariables
            if (envNode.has("environmentVariables")) {
                JsonNode varsNode = envNode.get("environmentVariables");
                Map<String, String> envVars = objectMapper.readValue(
                    varsNode.toString(),
                    objectMapper.getTypeFactory().constructMapType(Map.class, String.class, String.class)
                );
                config.setEnvironmentVariables(envVars);
            }

            log.debug("成功加载 skill {} 的环境配置，requirements: {}", skillName,
                config.getRequirements() != null ? config.getRequirements().size() : 0);
            return config;

        } catch (IOException e) {
            log.error("读取 skill {} 的 env-config.json 失败: {}", skillName, e.getMessage());
            return null;
        }
    }

    /**
     * 检查所有依赖项
     */
    private EnvironmentStatus checkRequirements(String skillName, EnvConfig config) {
        EnvironmentStatus status = new EnvironmentStatus();
        status.setSkillName(skillName);
        status.setLastChecked(System.currentTimeMillis());

        boolean allReady = true;
        StringBuilder errorMsg = new StringBuilder();

        if (config.getRequirements() != null) {
            for (Requirement req : config.getRequirements()) {
                RequirementStatus reqStatus = checkRequirement(req);
                status.getRequirements().put(req.getName(), reqStatus);

                if (req.isRequired() && !reqStatus.isInstalled()) {
                    allReady = false;
                    errorMsg.append(req.getName()).append(": ").append(reqStatus.getError()).append("; ");
                }
            }
        }

        status.setReady(allReady);
        if (!allReady) {
            status.setErrorMessage(errorMsg.toString());
        }

        return status;
    }

    /**
     * 检查单个依赖项
     */
    private RequirementStatus checkRequirement(Requirement req) {
        RequirementStatus status = new RequirementStatus();
        status.setName(req.getName());
        status.setCheckedAt(System.currentTimeMillis());

        if (req.getCheckCommand() == null || req.getCheckCommand().isEmpty()) {
            status.setInstalled(true);
            return status;
        }

        try {
            // 通过 bash-sandbox 执行检查命令
            String result = executeCommand(req.getCheckCommand(), null, 30000);
            status.setInstalled(result != null && !result.contains("not found") && !result.contains("not recognized"));

            // 解析版本号
            if (status.isInstalled() && req.getVersion() != null) {
                status.setVersion(parseVersion(result));
            }

            log.debug("检查依赖 {}: installed={}, version={}", req.getName(), status.isInstalled(), status.getVersion());

        } catch (Exception e) {
            status.setInstalled(false);
            status.setError(e.getMessage());
            log.debug("检查依赖 {} 失败: {}", req.getName(), e.getMessage());
        }

        return status;
    }

    /**
     * 配置环境
     */
    private EnvironmentStatus setupEnvironment(String skillName, EnvConfig config) {
        EnvironmentStatus status = new EnvironmentStatus();
        status.setSkillName(skillName);
        status.setLastSetup(System.currentTimeMillis());
        status.setLastChecked(System.currentTimeMillis());

        boolean success = true;
        StringBuilder errorMsg = new StringBuilder();

        // 1. 安装缺失的依赖
        if (config.getRequirements() != null) {
            for (Requirement req : config.getRequirements()) {
                if (!req.isRequired()) continue;

                RequirementStatus reqStatus = checkRequirement(req);
                if (!reqStatus.isInstalled()) {
                    log.info("安装依赖: {}", req.getName());
                    boolean installed = installRequirement(req);
                    reqStatus.setInstalled(installed);
                    reqStatus.setCheckedAt(System.currentTimeMillis());

                    if (!installed) {
                        success = false;
                        errorMsg.append(req.getName()).append(": 安装失败; ");
                    }
                }
                status.getRequirements().put(req.getName(), reqStatus);
            }
        }

        // 2. 运行 setup 脚本
        if (success && config.getSetupScripts() != null) {
            String setupScript = getPlatformCommand(config.getSetupScripts());
            if (setupScript != null) {
                log.info("运行 setup 脚本: {}", setupScript);
                try {
                    String workingDir = Paths.get(skillsPath, skillName).toString();
                    String result = executeCommand(setupScript, workingDir, 300000); // 5分钟超时
                    log.info("Setup 脚本输出: {}", result);
                } catch (Exception e) {
                    success = false;
                    errorMsg.append("setup脚本失败: ").append(e.getMessage());
                    log.error("运行 setup 脚本失败: {}", e.getMessage());
                }
            }
        }

        status.setReady(success);
        if (!success) {
            status.setErrorMessage(errorMsg.toString());
        }

        return status;
    }

    /**
     * 安装单个依赖
     */
    private boolean installRequirement(Requirement req) {
        if (req.getInstallCommand() == null) {
            log.warn("依赖 {} 没有配置安装命令", req.getName());
            return false;
        }

        String installCmd = getPlatformCommand(req.getInstallCommand());
        if (installCmd == null) {
            log.warn("依赖 {} 没有配置当前平台的安装命令", req.getName());
            return false;
        }

        try {
            log.info("执行安装命令: {}", installCmd);
            String result = executeCommand(installCmd, null, 600000); // 10分钟超时
            log.info("安装结果: {}", result);

            // 重新检查
            RequirementStatus status = checkRequirement(req);
            return status.isInstalled();

        } catch (Exception e) {
            log.error("安装依赖 {} 失败: {}", req.getName(), e.getMessage());
            return false;
        }
    }

    /**
     * 执行命令（通过 bash-sandbox）
     */
    private String executeCommand(String command, String workingDir, long timeoutMs) {
        try {
            // 使用 PowerShell 或 bash 执行
            String shell = isWindows() ? "powershell.exe" : "bash";
            String shellArg = isWindows() ? "-Command" : "-c";

            ProcessBuilder pb = new ProcessBuilder(shell, shellArg, command);

            if (workingDir != null) {
                pb.directory(new File(workingDir));
            }

            pb.redirectErrorStream(true);

            Process process = pb.start();
            boolean finished = process.waitFor(timeoutMs / 1000, java.util.concurrent.TimeUnit.SECONDS);

            if (!finished) {
                process.destroyForcibly();
                throw new RuntimeException("命令执行超时");
            }

            // Java 8 兼容：使用 ByteArrayOutputStream 代替 readAllBytes()
            java.io.InputStream inputStream = process.getInputStream();
            java.io.ByteArrayOutputStream outputStream = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int len;
            while ((len = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, len);
            }
            String output = outputStream.toString("UTF-8");
            return output;

        } catch (Exception e) {
            log.error("执行命令失败: {} - {}", command, e.getMessage());
            throw new RuntimeException("命令执行失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取当前平台的命令
     */
    private String getPlatformCommand(Map<String, String> commands) {
        if (commands == null) return null;

        if (isWindows()) {
            return commands.getOrDefault("windows", commands.get("default"));
        } else if (isMac()) {
            return commands.getOrDefault("macos", commands.get("linux"));
        } else {
            return commands.getOrDefault("linux", commands.get("default"));
        }
    }

    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("windows");
    }

    private boolean isMac() {
        return System.getProperty("os.name").toLowerCase().contains("mac");
    }

    /**
     * 从输出中解析版本号
     */
    private String parseVersion(String output) {
        if (output == null) return null;

        // 匹配常见的版本格式：x.y.z 或 x.y
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(\\d+\\.\\d+(?:\\.\\d+)?)");
        java.util.regex.Matcher matcher = pattern.matcher(output);

        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    /**
     * 加载缓存的状态
     */
    @SuppressWarnings("unchecked")
    private void loadCachedStatus() {
        if (!Files.exists(cacheFilePath)) {
            return;
        }

        try {
            // Java 8 兼容：使用 Files.readAllBytes 代替 Files.readString
            byte[] bytes = Files.readAllBytes(cacheFilePath);
            String content = new String(bytes, "UTF-8");
            JsonNode root = objectMapper.readTree(content);

            root.fields().forEachRemaining(entry -> {
                try {
                    EnvironmentStatus status = objectMapper.treeToValue(entry.getValue(), EnvironmentStatus.class);
                    envStatusCache.put(entry.getKey(), status);
                } catch (Exception e) {
                    log.warn("解析缓存状态失败: {}", entry.getKey());
                }
            });

            log.info("加载了 {} 个 skill 的环境状态缓存", envStatusCache.size());

        } catch (IOException e) {
            log.warn("加载环境状态缓存失败: {}", e.getMessage());
        }
    }

    /**
     * 保存缓存状态到文件
     */
    private void saveCachedStatus() {
        try {
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(envStatusCache);
            // Java 8 兼容：使用 Files.write 代替 Files.writeString
            Files.write(cacheFilePath, json.getBytes("UTF-8"));
            log.debug("保存环境状态缓存到: {}", cacheFilePath);
        } catch (IOException e) {
            log.warn("保存环境状态缓存失败: {}", e.getMessage());
        }
    }

    /**
     * 清除缓存
     */
    public void clearCache() {
        envStatusCache.clear();
        try {
            Files.deleteIfExists(cacheFilePath);
            log.info("已清除环境状态缓存");
        } catch (IOException e) {
            log.warn("清除缓存文件失败: {}", e.getMessage());
        }
    }
}
