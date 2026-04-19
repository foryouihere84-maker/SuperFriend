package com.superfriend.superfriend.agent.skill;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.superfriend.superfriend.config.FilePathConfig;
import com.superfriend.superfriend.service.BashSandboxService;
import com.superfriend.superfriend.service.FileLifecycleManager;
import com.superfriend.superfriend.service.SkillSandboxSessionManager;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 技能脚本执行器
 * 通过 bash-sandbox 执行技能脚本，统一执行环境
 *
 * 替代原有的 ScriptExecutor，提供：
 * 1. 统一的沙箱执行环境
 * 2. 会话状态保持
 * 3. 文件生命周期管理
 */
@Slf4j
@Service
public class SkillScriptRunner {

    private final BashSandboxService bashSandboxService;
    private final SkillSandboxSessionManager sessionManager;
    private final SkillRegistry skillRegistry;
    private final FileLifecycleManager fileLifecycleManager;
    private final FilePathConfig filePathConfig;
    private final ObjectMapper objectMapper;

    /**
     * 默认脚本执行超时时间（毫秒）
     */
    private static final long DEFAULT_TIMEOUT_MS = 300000; // 5 分钟

    /**
     * 文件路径检测正则表达式
     */
    private static final Pattern FILE_PATH_PATTERN = Pattern.compile(
        "([a-zA-Z]:[\\\\/][^\\s]+|/[^\\s]+\\.(docx|pdf|xlsx|pptx|png|jpg|jpeg|gif|mp4|mp3|zip|json|txt|md))",
        Pattern.CASE_INSENSITIVE
    );

    @Autowired
    public SkillScriptRunner(BashSandboxService bashSandboxService,
                              SkillSandboxSessionManager sessionManager,
                              SkillRegistry skillRegistry,
                              FileLifecycleManager fileLifecycleManager,
                              FilePathConfig filePathConfig,
                              ObjectMapper objectMapper) {
        this.bashSandboxService = bashSandboxService;
        this.sessionManager = sessionManager;
        this.skillRegistry = skillRegistry;
        this.fileLifecycleManager = fileLifecycleManager;
        this.filePathConfig = filePathConfig;
        this.objectMapper = objectMapper;
    }

    /**
     * 执行技能脚本
     *
     * @param skillName   技能名称
     * @param scriptName  脚本名称（相对于 scripts 目录）
     * @param parameters  参数
     * @return 执行结果
     */
    public ScriptExecutionResult executeScript(String skillName,
                                                String scriptName,
                                                Map<String, Object> parameters) {
        return executeScript(skillName, scriptName, parameters, null);
    }

    /**
     * 执行技能脚本
     *
     * @param skillName   技能名称
     * @param scriptName  脚本名称（相对于 scripts 目录）
     * @param parameters  参数
     * @param sessionId   会话 ID（可选，如果不提供则使用技能专用会话）
     * @return 执行结果
     */
    public ScriptExecutionResult executeScript(String skillName,
                                                String scriptName,
                                                Map<String, Object> parameters,
                                                String sessionId) {
        long startTime = System.currentTimeMillis();
        log.info("开始执行技能脚本: skill={}, script={}, sessionId={}", skillName, scriptName, sessionId);

        String paramFilePath = null;

        try {
            // 1. 获取技能路径
            String skillPath = skillRegistry.getSkillBasePath(skillName);
            if (skillPath == null) {
                skillPath = filePathConfig.getSkillsDir() + "/" + skillName;
            }

            // 2. 构建脚本完整路径
            String scriptPath = buildScriptPath(skillPath, scriptName);
            if (!Files.exists(Paths.get(scriptPath))) {
                return ScriptExecutionResult.failure("脚本文件不存在: " + scriptPath);
            }

            // 3. 获取或创建会话
            String effectiveSessionId = sessionId;
            if (effectiveSessionId == null || effectiveSessionId.isEmpty()) {
                effectiveSessionId = sessionManager.getOrCreateSession(skillName, skillPath);
            }

            // 4. 创建参数文件
            if (parameters != null && !parameters.isEmpty()) {
                paramFilePath = createParameterFile(parameters, skillPath);
            }

            // 5. 构建执行命令
            String command = buildCommand(scriptPath, paramFilePath, parameters);

            // 6. 通过 bash-sandbox 执行
            BashSandboxService.ExecuteResult executeResult = bashSandboxService.execute(
                effectiveSessionId,
                command,
                skillPath,  // 工作目录设为技能目录
                DEFAULT_TIMEOUT_MS,
                buildEnvironmentVariables(parameters)
            ).join();

            // 7. 清理参数文件
            if (paramFilePath != null) {
                cleanupParameterFile(paramFilePath);
                paramFilePath = null;  // 标记已清理
            }

            // 8. 转换结果
            ScriptExecutionResult result = convertResult(executeResult, scriptPath);
            result.setExecutionTime(System.currentTimeMillis() - startTime);

            // 9. 检测并注册输出文件
            if (result.isSuccess()) {
                List<String> outputFiles = detectOutputFiles(result.getOutput(), skillPath);
                result.setOutputFiles(outputFiles);

                for (String filePath : outputFiles) {
                    fileLifecycleManager.registerFile(filePath, "skill:" + skillName, effectiveSessionId);
                }
            }

            log.info("技能脚本执行完成: skill={}, script={}, success={}, time={}ms, outputFiles={}",
                skillName, scriptName, result.isSuccess(), result.getExecutionTime(),
                result.getOutputFiles() != null ? result.getOutputFiles().size() : 0);

            return result;

        } catch (Exception e) {
            log.error("技能脚本执行失败: skill={}, script={} - {}", skillName, scriptName, e.getMessage(), e);

            // 清理可能遗留的参数文件
            if (paramFilePath != null) {
                try {
                    cleanupParameterFile(paramFilePath);
                    log.info("已清理遗留的参数文件: {}", paramFilePath);
                } catch (Exception cleanupEx) {
                    log.warn("清理参数文件失败: {} - {}", paramFilePath, cleanupEx.getMessage());
                }
            }

            ScriptExecutionResult result = ScriptExecutionResult.failure("执行失败: " + e.getMessage());
            result.setExecutionTime(System.currentTimeMillis() - startTime);
            return result;
        }
    }

    /**
     * 构建脚本路径
     */
    private String buildScriptPath(String skillPath, String scriptName) {
        // 如果 scriptName 已包含 scripts/ 前缀，直接拼接
        if (scriptName.startsWith("scripts/") || scriptName.startsWith("scripts\\")) {
            return Paths.get(skillPath, scriptName).toString();
        }
        // 否则添加 scripts/ 前缀
        return Paths.get(skillPath, "scripts", scriptName).toString();
    }

    /**
     * 创建参数文件（在系统临时目录中创建，避免污染技能目录）
     */
    private String createParameterFile(Map<String, Object> parameters, String workingDir) throws IOException {
        // 在系统临时目录创建参数文件，避免污染技能目录
        Path tempDir = Paths.get(System.getProperty("java.io.tmpdir"), "superfriend_params");
        if (!Files.exists(tempDir)) {
            Files.createDirectories(tempDir);
        }
        Path paramFile = Files.createTempFile(tempDir, "skill_params_", ".json");
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(paramFile.toFile(), parameters);
        log.debug("创建参数文件: {}", paramFile);
        return paramFile.toString();
    }

    /**
     * 清理参数文件
     */
    private void cleanupParameterFile(String paramFilePath) {
        try {
            Files.deleteIfExists(Paths.get(paramFilePath));
            log.debug("清理参数文件: {}", paramFilePath);
        } catch (IOException e) {
            log.warn("清理参数文件失败: {} - {}", paramFilePath, e.getMessage());
        }
    }

    /**
     * 构建执行命令
     */
    private String buildCommand(String scriptPath, String paramFilePath, Map<String, Object> parameters) {
        String extension = getFileExtension(scriptPath);
        StringBuilder command = new StringBuilder();

        switch (extension.toLowerCase()) {
            case "py":
                command.append("python -u \"").append(scriptPath).append("\"");
                if (paramFilePath != null) {
                    command.append(" --param-file \"").append(paramFilePath).append("\"");
                }
                break;

            case "sh":
                command.append("bash \"").append(scriptPath).append("\"");
                if (paramFilePath != null) {
                    command.append(" \"").append(paramFilePath).append("\"");
                }
                break;

            case "js":
            case "ts":
                command.append("node \"").append(scriptPath).append("\"");
                if (paramFilePath != null) {
                    command.append(" --param-file \"").append(paramFilePath).append("\"");
                }
                break;

            case "csx":
                command.append("dotnet script \"").append(scriptPath).append("\"");
                if (paramFilePath != null) {
                    command.append(" --param-file \"").append(paramFilePath).append("\"");
                }
                break;

            default:
                // 尝试直接执行
                command.append("\"").append(scriptPath).append("\"");
                if (paramFilePath != null) {
                    command.append(" \"").append(paramFilePath).append("\"");
                }
        }

        return command.toString();
    }

    /**
     * 构建环境变量
     */
    private Map<String, String> buildEnvironmentVariables(Map<String, Object> parameters) {
        Map<String, String> env = new HashMap<>();

        if (parameters != null) {
            parameters.forEach((key, value) -> {
                env.put("SKILL_PARAM_" + key.toUpperCase(), String.valueOf(value));
            });
        }

        return env;
    }

    /**
     * 转换执行结果
     */
    private ScriptExecutionResult convertResult(BashSandboxService.ExecuteResult executeResult, String scriptPath) {
        ScriptExecutionResult result = new ScriptExecutionResult();
        result.setScriptPath(scriptPath);
        result.setExitCode(executeResult.getExitCode());
        result.setOutput(executeResult.getStdout());
        result.setError(executeResult.getStderr());
        result.setSuccess(executeResult.isSuccess());

        // 尝试解析 JSON 输出
        if (executeResult.isSuccess() && executeResult.getStdout() != null) {
            try {
                Map<String, Object> parsedOutput = objectMapper.readValue(
                    executeResult.getStdout(),
                    Map.class
                );
                result.setParsedOutput(parsedOutput);
            } catch (Exception e) {
                log.debug("输出不是 JSON 格式，保持为字符串");
            }
        }

        return result;
    }

    /**
     * 检测输出文件
     */
    private List<String> detectOutputFiles(String output, String workingDir) {
        List<String> files = new ArrayList<>();

        if (output == null || output.isEmpty()) {
            return files;
        }

        // 1. 尝试从 JSON 输出中提取文件路径
        try {
            Map<String, Object> parsedOutput = objectMapper.readValue(output, Map.class);
            extractFilePathsFromMap(parsedOutput, files, workingDir);
        } catch (Exception e) {
            // 不是 JSON，使用正则表达式匹配
        }

        // 2. 使用正则表达式匹配文件路径
        if (files.isEmpty()) {
            Matcher matcher = FILE_PATH_PATTERN.matcher(output);
            while (matcher.find()) {
                String filePath = matcher.group(1);
                if (Files.exists(Paths.get(filePath))) {
                    files.add(filePath);
                }
            }
        }

        // 3. 检查工作目录中是否有新生成的常见输出文件
        if (files.isEmpty()) {
            detectNewOutputFiles(workingDir, files);
        }

        return files;
    }

    /**
     * 检测工作目录中新生成的输出文件
     */
    private void detectNewOutputFiles(String workingDir, List<String> files) {
        try {
            Path workPath = Paths.get(workingDir);
            if (!Files.exists(workPath)) {
                return;
            }

            // 常见输出文件扩展名
            String[] outputExtensions = {".docx", ".pdf", ".xlsx", ".pptx", ".png", ".jpg", ".jpeg", ".gif", ".mp4", ".mp3", ".zip", ".json"};

            Files.walk(workPath, 2)  // 最多深度2层
                .filter(Files::isRegularFile)
                .filter(path -> {
                    String name = path.getFileName().toString().toLowerCase();
                    for (String ext : outputExtensions) {
                        if (name.endsWith(ext)) {
                            return true;
                        }
                    }
                    return false;
                })
                .filter(path -> {
                    // 排除临时文件和参数文件
                    String name = path.getFileName().toString();
                    return !name.startsWith("skill_params_") && !name.startsWith("~$");
                })
                .forEach(path -> files.add(path.toString()));

            if (!files.isEmpty()) {
                log.info("检测到工作目录中的输出文件: {}", files);
            }
        } catch (Exception e) {
            log.debug("检测工作目录输出文件失败: {}", e.getMessage());
        }
    }

    /**
     * 从 Map 中提取文件路径（支持数组和嵌套结构）
     */
    private void extractFilePathsFromMap(Map<String, Object> map, List<String> files, String workingDir) {
        String[] fileKeys = {"outputFile", "output_path", "filePath", "file_path", "output", "outputFiles", "files"};

        for (String key : fileKeys) {
            Object value = map.get(key);
            if (value == null) {
                continue;
            }

            if (value instanceof String) {
                // 单个文件路径
                addFilePath((String) value, files, workingDir);
            } else if (value instanceof List) {
                // 文件路径数组
                for (Object item : (List<?>) value) {
                    if (item instanceof String) {
                        addFilePath((String) item, files, workingDir);
                    } else if (item instanceof Map) {
                        // 嵌套结构，递归提取
                        extractFilePathsFromMap((Map<String, Object>) item, files, workingDir);
                    }
                }
            } else if (value instanceof Map) {
                // 嵌套 Map，递归提取
                extractFilePathsFromMap((Map<String, Object>) value, files, workingDir);
            }
        }
    }

    /**
     * 添加文件路径（处理相对路径转换）
     */
    private void addFilePath(String filePath, List<String> files, String workingDir) {
        if (filePath == null || filePath.isEmpty()) {
            return;
        }

        // 如果是相对路径，转换为绝对路径
        if (!Paths.get(filePath).isAbsolute()) {
            filePath = Paths.get(workingDir, filePath).toString();
        }

        if (Files.exists(Paths.get(filePath))) {
            files.add(filePath);
        }
    }

    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String path) {
        int lastDot = path.lastIndexOf('.');
        if (lastDot > 0 && lastDot < path.length() - 1) {
            return path.substring(lastDot + 1);
        }
        return "";
    }

    /**
     * 脚本执行结果
     * 与 ScriptExecutor.ScriptExecutionResult 兼容
     */
    @Data
    public static class ScriptExecutionResult {
        private boolean success;
        private String scriptPath;
        private int exitCode;
        private String output;
        private String error;
        private long executionTime;
        private Map<String, Object> parsedOutput;
        /** 输出文件列表 */
        private List<String> outputFiles;

        public static ScriptExecutionResult success(String output) {
            ScriptExecutionResult result = new ScriptExecutionResult();
            result.setSuccess(true);
            result.setOutput(output);
            result.setExitCode(0);
            return result;
        }

        public static ScriptExecutionResult failure(String error) {
            ScriptExecutionResult result = new ScriptExecutionResult();
            result.setSuccess(false);
            result.setError(error);
            result.setExitCode(-1);
            return result;
        }

        public boolean hasParsedOutput() {
            return parsedOutput != null && !parsedOutput.isEmpty();
        }

        public boolean hasOutputFiles() {
            return outputFiles != null && !outputFiles.isEmpty();
        }
    }
}
