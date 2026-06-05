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
<<<<<<< HEAD
 * 提供功能：
 * 1. 统一的沙箱执行环境
 * 2. 会话状态保持
 * 3. 文件生命周期管理
 * 4. 多种执行器类型支持（script/command）
=======
 * 替代原有的 ScriptExecutor，提供：
 * 1. 统一的沙箱执行环境
 * 2. 会话状态保持
 * 3. 文件生命周期管理
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
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
<<<<<<< HEAD
     * 默认脚本执行超时时间（毫秒）- 可通过配置覆盖
     */
    @org.springframework.beans.factory.annotation.Value("${skill.executor.default-timeout-ms:300000}")
    private long defaultTimeoutMs;

    /**
     * 构建超时时间（毫秒）
     */
    @org.springframework.beans.factory.annotation.Value("${skill.executor.build-timeout-ms:180000}")
    private long buildTimeoutMs;

    /**
     * 文件路径检测正则表达式
     * 支持多种格式：
     * - Windows 绝对路径: C:\path\file.docx
     * - Unix 绝对路径: /path/file.docx
     * - 相对路径输出: ./output/file.docx, output/file.docx
     * - Created/Written 等提示后的路径
     */
    private static final Pattern FILE_PATH_PATTERN = Pattern.compile(
        "(?:created?|written|saved?|output|generated?|file)[:\\s]*([^\\s]+\\.(docx|pdf|xlsx|pptx|png|jpg|jpeg|gif|mp4|mp3|zip|json))|" +
        "([a-zA-Z]:[\\\\/][^\\s:*?\"<>|]+\\.(docx|pdf|xlsx|pptx|png|jpg|jpeg|gif|mp4|mp3|zip|json))|" +
        "(/[^\\s]+\\.(docx|pdf|xlsx|pptx|png|jpg|jpeg|gif|mp4|mp3|zip|json))|" +
        "(\\./[^\\s]+\\.(docx|pdf|xlsx|pptx|png|jpg|jpeg|gif|mp4|mp3|zip|json))",
        Pattern.CASE_INSENSITIVE
    );

    /**
     * 默认输出文件扩展名
     */
    private static final String[] DEFAULT_OUTPUT_EXTENSIONS = {
        ".docx", ".pdf", ".xlsx", ".pptx", ".png", ".jpg", ".jpeg", ".gif", ".mp4", ".mp3", ".zip", ".json"
    };

    /**
     * 默认脚本执行器配置
     */
    private static final Map<String, String> DEFAULT_SCRIPT_EXECUTORS = new HashMap<>();
    static {
        DEFAULT_SCRIPT_EXECUTORS.put("py", "python -u");
        DEFAULT_SCRIPT_EXECUTORS.put("sh", "bash");
        DEFAULT_SCRIPT_EXECUTORS.put("js", "node");
        DEFAULT_SCRIPT_EXECUTORS.put("ts", "node");
        DEFAULT_SCRIPT_EXECUTORS.put("csx", "dotnet script");
    }

=======
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

>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
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
<<<<<<< HEAD
     * @param scriptName  脚本名称（相对于 scripts 目录）或 CLI 子命令
=======
     * @param scriptName  脚本名称（相对于 scripts 目录）
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
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
<<<<<<< HEAD
        String outputDir = null;

        try {
            // 1. 获取技能路径和配置
=======

        try {
            // 1. 获取技能路径
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
            String skillPath = skillRegistry.getSkillBasePath(skillName);
            if (skillPath == null) {
                skillPath = filePathConfig.getSkillsDir() + "/" + skillName;
            }

<<<<<<< HEAD
            // 获取技能配置
            Skill skill = skillRegistry.getSkill(skillName);
            SkillConfig.ExecutorConfig executorConfig = null;
            if (skill instanceof DynamicSkill) {
                executorConfig = ((DynamicSkill) skill).getConfig().getExecutor();
            }

            // 2. 创建唯一输出目录（每个会话独立）
            String effectiveSessionId = sessionId;
            if (effectiveSessionId == null || effectiveSessionId.isEmpty()) {
                effectiveSessionId = "skill-" + skillName + "-" + System.currentTimeMillis();
            }
            outputDir = createOutputDirectory(effectiveSessionId);
            log.info("创建输出目录: {}", outputDir);

            // 2.5 将输出参数重定向到 SKILL_OUTPUT_DIR
            // 脚本的 output/input 等参数如果是相对路径，自动转为 SKILL_OUTPUT_DIR 下的绝对路径
            if (parameters != null) {
                parameters = redirectOutputParameters(parameters, outputDir);
            }

            // 3. 根据执行器类型选择执行方式
            String command;
            String workingDir = skillPath;

            if (executorConfig != null && !"script".equals(executorConfig.getType())) {
                // 使用自定义执行器
                log.info("使用 {} 执行器: {}", executorConfig.getType(), skillName);

                String workingDirTemplate = executorConfig.getEffectiveWorkingDirTemplate();
                if (workingDirTemplate != null) {
                    workingDir = resolveTemplate(workingDirTemplate, skillPath, parameters);
                }

                // 如果需要构建，先执行构建命令
                if (executorConfig.isNeedsBuild()) {
                    String buildTemplate = executorConfig.getBuildTemplate();
                    SkillConfig.PlatformConfig pc = executorConfig.getPlatformConfig();
                    if (pc != null && pc.getBuildTemplate() != null) {
                        buildTemplate = pc.getBuildTemplate();
                    }

                    if (buildTemplate != null) {
                        String buildCommand = resolveTemplate(buildTemplate, skillPath, parameters);
                        log.info("执行构建命令: {}", buildCommand);

                        String buildSessionId = sessionManager.getOrCreateSession(skillName + "_build", skillPath);

                        BashSandboxService.ExecuteResult buildResult = bashSandboxService.execute(
                            buildSessionId,
                            buildCommand,
                            skillPath,
                            buildTimeoutMs,
                            executorConfig.getEffectiveEnvironment()
                        ).join();

                        if (!buildResult.isSuccess()) {
                            log.error("构建失败: {}", buildResult.getStderr());
                            cleanupOutputDirectory(outputDir);
                            ScriptExecutionResult result = ScriptExecutionResult.failure("构建失败: " + buildResult.getStderr());
                            result.setExecutionTime(System.currentTimeMillis() - startTime);
                            return result;
                        }
                        log.info("构建成功");
                    }
                }

                command = buildExecutorCommand(executorConfig, skillPath, scriptName, parameters);
            } else {
                // 传统脚本执行
                String scriptPath = buildScriptPath(skillPath, scriptName);
                if (!Files.exists(Paths.get(scriptPath))) {
                    cleanupOutputDirectory(outputDir);
                    return ScriptExecutionResult.failure("脚本文件不存在: " + scriptPath);
                }

                if (parameters != null && !parameters.isEmpty()) {
                    paramFilePath = createParameterFile(parameters, skillPath);
                }

                command = buildCommand(scriptPath, paramFilePath, parameters);
            }

            // 4. 获取或创建会话
            // 优先使用外部传入的 sessionId（来自 Agent ExecutionContext），确保会话一致性
            String sandboxSessionId;
            if (sessionId != null && !sessionId.isEmpty()) {
                // 外部传入 sessionId，优先复用已有会话
                sandboxSessionId = sessionManager.getSessionId(skillName);
                if (sandboxSessionId == null || !sessionManager.hasSession(skillName)) {
                    // 技能没有已有会话，创建新会话并关联
                    sandboxSessionId = sessionManager.getOrCreateSession(skillName, skillPath);
                    log.info("【会话关联】为技能 {} 创建新沙箱会话 {}，关联到 agentSessionId={}",
                        skillName, sandboxSessionId, sessionId);
                } else {
                    log.debug("【会话关联】复用技能 {} 已有沙箱会话 {}，agentSessionId={}",
                        skillName, sandboxSessionId, sessionId);
                }
            } else {
                // 无外部 sessionId，使用技能专用会话
                sandboxSessionId = sessionManager.getOrCreateSession(skillName, skillPath);
            }

            // 5. 构建环境变量（注入输出目录）
            Map<String, String> env = buildEnvironmentVariables(parameters, skillPath, outputDir, effectiveSessionId);
            if (executorConfig != null) {
                Map<String, String> executorEnv = executorConfig.getEffectiveEnvironment();
                if (executorEnv != null) {
                    env.putAll(executorEnv);
                }
            }

            // 6. 通过 bash-sandbox 执行
            long timeout = getTimeout(executorConfig);
            BashSandboxService.ExecuteResult executeResult = bashSandboxService.execute(
                sandboxSessionId,
                command,
                workingDir,
                timeout,
                env
=======
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
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
            ).join();

            // 7. 清理参数文件
            if (paramFilePath != null) {
                cleanupParameterFile(paramFilePath);
<<<<<<< HEAD
                paramFilePath = null;
            }

            // 8. 转换结果
            ScriptExecutionResult result = convertResult(executeResult, command);
            result.setExecutionTime(System.currentTimeMillis() - startTime);
            result.setOutputDirectory(outputDir);  // 记录输出目录

            // 9. 检测输出文件（多目录扫描，确保不遗漏）
            if (result.isSuccess()) {
                // 收集所有可能输出文件的目录
                List<String> searchDirs = new ArrayList<>();
                searchDirs.add(outputDir);  // SKILL_OUTPUT_DIR（优先）

                // 添加 input 参数所在目录（如果存在）
                if (parameters != null) {
                    String inputPath = getStringParam(parameters, "input", "inputFile", "input_path", "template");
                    if (inputPath != null && Paths.get(inputPath).isAbsolute()) {
                        String parentDir = Paths.get(inputPath).getParent() != null
                            ? Paths.get(inputPath).getParent().toString() : null;
                        if (parentDir != null && !searchDirs.contains(parentDir)) {
                            searchDirs.add(parentDir);
                        }
                    }
                    // 添加 output 参数所在目录（如果是绝对路径）
                    String outputPath = getStringParam(parameters, "output", "outputFile", "output_path", "out");
                    if (outputPath != null && Paths.get(outputPath).isAbsolute()) {
                        String parentDir = Paths.get(outputPath).getParent() != null
                            ? Paths.get(outputPath).getParent().toString() : null;
                        if (parentDir != null && !searchDirs.contains(parentDir)) {
                            searchDirs.add(parentDir);
                        }
                    }
                }

                // 添加 workingDir（skill 目录）
                if (workingDir != null && !searchDirs.contains(workingDir)) {
                    searchDirs.add(workingDir);
                }

                List<String> outputFiles = detectOutputFiles(result.getOutput(), searchDirs);
=======
                paramFilePath = null;  // 标记已清理
            }

            // 8. 转换结果
            ScriptExecutionResult result = convertResult(executeResult, scriptPath);
            result.setExecutionTime(System.currentTimeMillis() - startTime);

            // 9. 检测并注册输出文件
            if (result.isSuccess()) {
                List<String> outputFiles = detectOutputFiles(result.getOutput(), skillPath);
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
                result.setOutputFiles(outputFiles);

                for (String filePath : outputFiles) {
                    fileLifecycleManager.registerFile(filePath, "skill:" + skillName, effectiveSessionId);
                }
<<<<<<< HEAD

                log.info("检测到输出文件: files={}, searchDirs={}", outputFiles.size(), searchDirs);
=======
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
            }

            log.info("技能脚本执行完成: skill={}, script={}, success={}, time={}ms, outputFiles={}",
                skillName, scriptName, result.isSuccess(), result.getExecutionTime(),
                result.getOutputFiles() != null ? result.getOutputFiles().size() : 0);

            return result;

        } catch (Exception e) {
            log.error("技能脚本执行失败: skill={}, script={} - {}", skillName, scriptName, e.getMessage(), e);

<<<<<<< HEAD
            // 清理可能遗留的文件
=======
            // 清理可能遗留的参数文件
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
            if (paramFilePath != null) {
                try {
                    cleanupParameterFile(paramFilePath);
                    log.info("已清理遗留的参数文件: {}", paramFilePath);
                } catch (Exception cleanupEx) {
                    log.warn("清理参数文件失败: {} - {}", paramFilePath, cleanupEx.getMessage());
                }
            }
<<<<<<< HEAD
            if (outputDir != null) {
                try {
                    cleanupOutputDirectory(outputDir);
                    log.info("已清理输出目录: {}", outputDir);
                } catch (Exception cleanupEx) {
                    log.warn("清理输出目录失败: {} - {}", outputDir, cleanupEx.getMessage());
                }
            }
=======
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2

            ScriptExecutionResult result = ScriptExecutionResult.failure("执行失败: " + e.getMessage());
            result.setExecutionTime(System.currentTimeMillis() - startTime);
            return result;
        }
    }

    /**
<<<<<<< HEAD
     * 构建执行器命令
     */
    private String buildExecutorCommand(SkillConfig.ExecutorConfig executor, String skillPath,
                                         String scriptName, Map<String, Object> parameters) {
        String type = executor.getType();

        // 验证 script_name 不是脚本文件路径
        if (scriptName != null) {
            String lowerScriptName = scriptName.toLowerCase();
            if (lowerScriptName.endsWith(".sh") || lowerScriptName.endsWith(".py") ||
                lowerScriptName.endsWith(".js") || lowerScriptName.endsWith(".ts") ||
                lowerScriptName.endsWith(".bat") || lowerScriptName.endsWith(".cmd") ||
                lowerScriptName.contains("/") || lowerScriptName.contains("\\")) {
                log.error("【script_name 参数错误】传入的是脚本文件路径而非 CLI 子命令: {}", scriptName);
                log.error("正确的用法: script_name 应该是 CLI 子命令，如 'create', 'edit replace-text', 'apply-template', 'validate'");
                log.error("错误示例: script_name='scripts/docx_preview.sh' (这是文件路径，不是命令)");
                log.error("正确示例: script_name='create' (这是 CLI 子命令)");
                throw new IllegalArgumentException(
                    "script_name 参数错误: '" + scriptName + "' 是脚本文件路径，而非 CLI 子命令。\n" +
                    "正确的 script_name 值应该是 CLI 子命令，如: create, edit replace-text, apply-template, validate, analyze\n" +
                    "请检查 AI 是否误解了 SKILL.md 文档中的命令格式。"
                );
            }
        }

        if ("command".equals(type)) {
            return buildTemplateCommand(executor, skillPath, scriptName, parameters);
        } else {
            log.warn("未知的执行器类型: {}, 使用命令模板执行", type);
            return buildTemplateCommand(executor, skillPath, scriptName, parameters);
        }
    }

    /**
     * 构建模板命令
     * 支持变量替换：{skill_path}, {script_name}, {args}, {param_name} 等
     */
    private String buildTemplateCommand(SkillConfig.ExecutorConfig executor, String skillPath,
                                         String scriptName, Map<String, Object> parameters) {
        String template = executor.getEffectiveCommandTemplate();
        if (template == null || template.isEmpty()) {
            log.error("执行器命令模板为空");
            return "";
        }

        // 替换基础变量
        String result = template;
        result = result.replace("{skill_path}", skillPath);
        result = result.replace("{script_name}", scriptName != null ? scriptName : "");

        // 构建参数列表
        String args = buildArgsString(executor, parameters);
        result = result.replace("{args}", args);

        // 替换自定义参数
        if (parameters != null) {
            for (Map.Entry<String, Object> entry : parameters.entrySet()) {
                String placeholder = "{" + entry.getKey() + "}";
                String value = entry.getValue() != null ? String.valueOf(entry.getValue()) : "";
                result = result.replace(placeholder, value);
            }
        }

        // 替换默认参数
        if (executor.getDefaultArgs() != null) {
            for (Map.Entry<String, String> entry : executor.getDefaultArgs().entrySet()) {
                String placeholder = "{" + entry.getKey() + "}";
                if (result.contains(placeholder)) {
                    result = result.replace(placeholder, entry.getValue());
                }
            }
        }

        // 规范化路径分隔符（将 / 和 \ 都转换为当前系统的分隔符）
        // 但要保留引号内的路径
        result = normalizePathSeparators(result);

        log.debug("构建命令: {}", result);
        return result;
    }

    /**
     * 规范化路径分隔符
     * 将模板中的路径分隔符转换为当前系统适用的格式
     */
    private String normalizePathSeparators(String command) {
        if (command == null || command.isEmpty()) {
            return command;
        }

        // 检测当前系统
        boolean isWindows = File.separatorChar == '\\';

        // 处理引号内的路径
        // 在 Windows 上：将引号内的 / 转换为 \（但排除 URL 和特殊路径）
        // 在 Unix 上：将引号内的 \ 转换为 /

        StringBuilder result = new StringBuilder();
        boolean inQuote = false;
        StringBuilder quotedContent = new StringBuilder();

        for (int i = 0; i < command.length(); i++) {
            char c = command.charAt(i);

            if (c == '"') {
                if (inQuote) {
                    // 结束引号，处理引号内容
                    String content = quotedContent.toString();
                    if (isWindows) {
                        // Windows: 将 / 转换为 \，但排除 URL (http://, https://, file://)
                        if (!content.contains("://")) {
                            content = content.replace('/', '\\');
                        }
                    } else {
                        // Unix: 将 \ 转换为 /
                        content = content.replace('\\', '/');
                    }
                    result.append('"').append(content).append('"');
                    quotedContent = new StringBuilder();
                }
                inQuote = !inQuote;
            } else if (inQuote) {
                quotedContent.append(c);
            } else {
                result.append(c);
            }
        }

        return result.toString();
    }

    /**
     * 获取超时时间
     */
    private long getTimeout(SkillConfig.ExecutorConfig executorConfig) {
        if (executorConfig != null && executorConfig.getTimeout() > 0) {
            return executorConfig.getTimeout();
        }
        return defaultTimeoutMs;
    }

    /**
     * 转义参数值中的特殊字符
     */
    private String escapeValue(Object value) {
        if (value == null) {
            return "";
        }
        String str = String.valueOf(value);
        // 转义双引号和反斜杠
        str = str.replace("\\", "\\\\");
        str = str.replace("\"", "\\\"");
        return str;
    }

    /**
     * 构建参数字符串
     */
    private String buildArgsString(SkillConfig.ExecutorConfig executor, Map<String, Object> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            return "";
        }

        StringBuilder args = new StringBuilder();
        String format = executor.getArgsFormat() != null ? executor.getArgsFormat() : "kebab";
        String prefix = executor.getArgsPrefix() != null ? executor.getArgsPrefix() : "--";

        for (Map.Entry<String, Object> entry : parameters.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            // 转换参数名：camelCase -> kebab-case
            String argName = key.replaceAll("([a-z])([A-Z])", "$1-$2").toLowerCase();

            if (value instanceof Boolean && (Boolean) value) {
                // 布尔标志
                switch (format) {
                    case "kebab":
                        args.append(" ").append(prefix).append(argName);
                        break;
                    case "short":
                        args.append(" -").append(argName.charAt(0));
                        break;
                    // 其他格式不处理纯布尔值
                }
            } else if (value != null) {
                // 带值的参数 - 使用转义
                String escapedValue = escapeValue(value);
                switch (format) {
                    case "kebab":
                        args.append(" ").append(prefix).append(argName).append(" \"").append(escapedValue).append("\"");
                        break;
                    case "equals":
                        args.append(" ").append(prefix).append(argName).append("=\"").append(escapedValue).append("\"");
                        break;
                    case "short":
                        args.append(" -").append(argName.charAt(0)).append(" \"").append(escapedValue).append("\"");
                        break;
                    case "space":
                        args.append(" ").append(argName).append(" \"").append(escapedValue).append("\"");
                        break;
                    default:
                        args.append(" ").append(prefix).append(argName).append(" \"").append(escapedValue).append("\"");
                }
            }
        }

        return args.toString().trim();
    }

    /**
     * 解析模板变量
     * 支持：{skill_path}, {output}, {param_name} 等
     */
    private String resolveTemplate(String template, String skillPath, Map<String, Object> parameters) {
        if (template == null) {
            return "";
        }

        String result = template.replace("{skill_path}", skillPath);

        if (parameters != null) {
            for (Map.Entry<String, Object> entry : parameters.entrySet()) {
                String placeholder = "{" + entry.getKey() + "}";
                String value = entry.getValue() != null ? String.valueOf(entry.getValue()) : "";
                result = result.replace(placeholder, value);
            }
        }

        return result;
    }

    /**
=======
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
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
<<<<<<< HEAD
     * 构建执行命令（传统脚本方式）
=======
     * 构建执行命令
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
     */
    private String buildCommand(String scriptPath, String paramFilePath, Map<String, Object> parameters) {
        String extension = getFileExtension(scriptPath);
        StringBuilder command = new StringBuilder();

<<<<<<< HEAD
        // 使用配置的默认执行器，或回退到硬编码默认值
        String executor = DEFAULT_SCRIPT_EXECUTORS.getOrDefault(extension.toLowerCase(), "");

        if (!executor.isEmpty()) {
            command.append(executor).append(" \"").append(scriptPath).append("\"");
            if (paramFilePath != null) {
                command.append(" --param-file \"").append(paramFilePath).append("\"");
            }
        } else {
            // 未知扩展名，尝试直接执行
            command.append("\"").append(scriptPath).append("\"");
            if (paramFilePath != null) {
                command.append(" \"").append(paramFilePath).append("\"");
            }
=======
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
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
        }

        return command.toString();
    }

    /**
<<<<<<< HEAD
     * 创建唯一输出目录
     * 每个会话有独立的输出目录，避免文件冲突
     */
    private String createOutputDirectory(String sessionId) throws IOException {
        String baseOutputDir = filePathConfig.getSkillOutputDir();
        Path outputDir = Paths.get(baseOutputDir, sessionId);

        if (!Files.exists(outputDir)) {
            Files.createDirectories(outputDir);
        }

        return outputDir.toString();
    }

    /**
     * 将输出相关参数重定向到 SKILL_OUTPUT_DIR
     * 如果 output/input 等参数是相对路径，自动转为 SKILL_OUTPUT_DIR 下的绝对路径
     * 这确保所有技能生成的文件都统一存放在 SKILL_OUTPUT_DIR 中
     */
    private Map<String, Object> redirectOutputParameters(Map<String, Object> parameters, String outputDir) {
        // 需要重定向的参数名（常见的输出参数名）
        String[] outputParamNames = {"output", "outputFile", "outputPath", "output_path", "out"};

        Map<String, Object> redirected = new HashMap<>(parameters);

        for (String paramName : outputParamNames) {
            Object value = redirected.get(paramName);
            if (value == null) continue;

            String pathStr = String.valueOf(value);
            Path path = Paths.get(pathStr);

            // 只重定向相对路径（绝对路径保持不变，用户可能指定了特定位置）
            if (!path.isAbsolute()) {
                // 使用绝对路径，确保无论 workingDirectory 在哪里都能正确解析
                Path absolutePath = Paths.get(outputDir).toAbsolutePath().resolve(pathStr);
                redirected.put(paramName, absolutePath.toString());
                log.debug("重定向输出参数: {} = {} -> {}", paramName, pathStr, absolutePath);
            }
        }

        return redirected;
    }

    /**
     * 清理输出目录
     * 文件发送给前端后清理临时文件
     */
    public void cleanupOutputDirectory(String outputDir) {
        if (outputDir == null || outputDir.isEmpty()) {
            return;
        }

        try {
            Path dirPath = Paths.get(outputDir);
            if (Files.exists(dirPath)) {
                Files.walk(dirPath)
                    .sorted((a, b) -> -a.compareTo(b))  // 先删除文件，再删除目录
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            log.debug("删除文件失败: {} - {}", path, e.getMessage());
                        }
                    });
                log.info("已清理输出目录: {}", outputDir);
            }
        } catch (Exception e) {
            log.warn("清理输出目录失败: {} - {}", outputDir, e.getMessage());
        }
    }

    /**
     * 构建环境变量
     * 自动注入标准环境变量：
     * - SKILL_DIR: 技能目录
     * - SKILL_OUTPUT_DIR: 技能输出目录（固定位置）
     * - SKILL_SESSION_ID: 会话 ID
     * - SKILL_PARAM_*: 用户参数
     */
    private Map<String, String> buildEnvironmentVariables(Map<String, Object> parameters,
                                                           String skillPath,
                                                           String outputDir,
                                                           String sessionId) {
        Map<String, String> env = new HashMap<>();

        // 注入技能目录
        env.put("SKILL_DIR", skillPath);
        env.put("SKILL_PATH", skillPath);

        // 注入输出目录（所有技能生成的文件应存放于此）
        env.put("SKILL_OUTPUT_DIR", outputDir);
        env.put("OUTPUT_DIR", outputDir);  // 别名，更通用

        // 注入会话 ID
        env.put("SKILL_SESSION_ID", sessionId);
        env.put("SESSION_ID", sessionId);

        // 注入用户参数
=======
     * 构建环境变量
     */
    private Map<String, String> buildEnvironmentVariables(Map<String, Object> parameters) {
        Map<String, String> env = new HashMap<>();

>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
        if (parameters != null) {
            parameters.forEach((key, value) -> {
                env.put("SKILL_PARAM_" + key.toUpperCase(), String.valueOf(value));
            });
        }

        return env;
    }

    /**
<<<<<<< HEAD
     * 构建环境变量（兼容旧方法）
     */
    private Map<String, String> buildEnvironmentVariables(Map<String, Object> parameters) {
        return buildEnvironmentVariables(parameters, "", filePathConfig.getSkillOutputDir(), "");
    }

    /**
=======
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
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
<<<<<<< HEAD
     * 检测输出文件（多目录扫描版本）
     *
     * 优先级策略（从快到慢）：
     * 1. 脚本主动报告：从 JSON 输出中提取 outputFile/output_path/filePath
     * 2. 正则匹配：从输出文本中匹配文件路径
     * 3. 多目录扫描：扫描所有可能输出文件的目录
     *
     * @param output 脚本输出文本
     * @param searchDirs 要扫描的目录列表（按优先级排序）
     * @return 检测到的文件路径列表
     */
    private List<String> detectOutputFiles(String output, List<String> searchDirs) {
        List<String> files = new ArrayList<>();
        String firstDir = searchDirs != null && !searchDirs.isEmpty() ? searchDirs.get(0) : null;

        if (output != null && !output.isEmpty()) {
            // 1. 尝试从 JSON 输出中提取文件路径（最快，O(1)）
            try {
                Map<String, Object> parsedOutput = objectMapper.readValue(output, Map.class);
                extractFilePathsFromMap(parsedOutput, files, firstDir);
                if (!files.isEmpty()) {
                    log.info("从 JSON 输出中检测到文件: {}", files);
                    return files.stream().distinct().collect(java.util.stream.Collectors.toList());
                }
            } catch (Exception e) {
                // 不是 JSON，继续其他方法
            }

            // 2. 使用正则表达式匹配文件路径（较快）
            // 收集所有目录用于解析相对路径
            List<String> allDirs = searchDirs != null ? searchDirs : new ArrayList<>();
            Matcher matcher = FILE_PATH_PATTERN.matcher(output);
            while (matcher.find()) {
                for (int i = 1; i <= matcher.groupCount(); i++) {
                    String filePath = matcher.group(i);
                    if (filePath != null && !filePath.isEmpty()) {
                        Path resolvedPath = resolveFilePath(filePath, allDirs);
                        if (resolvedPath != null && Files.exists(resolvedPath)) {
                            files.add(resolvedPath.toString());
                            break;
                        }
                    }
                }
            }
            if (!files.isEmpty()) {
                log.info("从输出文本中匹配到文件: {}", files);
                return files.stream().distinct().collect(java.util.stream.Collectors.toList());
            }
        }

        // 3. 多目录扫描：按优先级扫描所有目录
        if (searchDirs != null) {
            for (String dir : searchDirs) {
                if (dir != null) {
                    detectOutputFilesInDirectory(dir, files);
=======
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
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
                }
            }
        }

<<<<<<< HEAD
        if (!files.isEmpty()) {
            log.info("从多个目录中检测到文件: {}", files);
        }

        return files.stream().distinct().collect(java.util.stream.Collectors.toList());
    }

    /**
     * 解析文件路径（支持相对路径和绝对路径）
     * 尝试在多个目录中查找文件
     */
    private Path resolveFilePath(String filePath, List<String> searchDirs) {
        Path path = Paths.get(filePath);

        // 绝对路径直接返回
        if (path.isAbsolute()) {
            return path;
        }

        // 相对路径：在所有搜索目录中尝试解析
        for (String dir : searchDirs) {
            if (dir != null) {
                Path resolved = Paths.get(dir, filePath);
                if (Files.exists(resolved)) {
                    return resolved;
                }
            }
        }

        // 都找不到，返回相对于第一个目录的路径（可能不存在）
        if (!searchDirs.isEmpty() && searchDirs.get(0) != null) {
            return Paths.get(searchDirs.get(0), filePath);
        }

        return path;
    }

    /**
     * 从参数 Map 中获取字符串值（支持多个可能的键名）
     */
    private String getStringParam(Map<String, Object> parameters, String... keys) {
        for (String key : keys) {
            Object value = parameters.get(key);
            if (value != null) {
                return String.valueOf(value);
            }
        }
        return null;
    }

    /**
     * 检测目录中的输出文件（不做时间过滤，只按扩展名过滤）
     */
    private void detectOutputFilesInDirectory(String directory, List<String> files) {
        try {
            Path dirPath = Paths.get(directory);
            if (!Files.exists(dirPath)) {
                return;
            }

            Files.walk(dirPath, 3)
                .filter(Files::isRegularFile)
                .filter(path -> {
                    String name = path.getFileName().toString().toLowerCase();
                    for (String ext : DEFAULT_OUTPUT_EXTENSIONS) {
=======
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
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
                        if (name.endsWith(ext)) {
                            return true;
                        }
                    }
                    return false;
                })
                .filter(path -> {
<<<<<<< HEAD
                    String name = path.getFileName().toString();
                    return !name.startsWith("skill_params_") && !name.startsWith("~$");
                })
                .filter(path -> {
                    String pathStr = path.toString().toLowerCase();
                    return !pathStr.contains("node_modules") &&
                           !pathStr.contains(File.separator + ".git" + File.separator) &&
                           !pathStr.contains(File.separator + "target" + File.separator) &&
                           !pathStr.contains(File.separator + "bin" + File.separator) &&
                           !pathStr.contains(File.separator + "obj" + File.separator);
                })
                .forEach(path -> files.add(path.toString()));

        } catch (Exception e) {
            log.debug("检测目录输出文件失败: {} - {}", directory, e.getMessage());
        }
    }

    /**
     * 获取目录快照（用于执行前后对比）
     * @return 目录中所有输出文件的路径集合
     */
    public Set<String> getDirectorySnapshot(String directory) {
        Set<String> snapshot = new HashSet<>();
        try {
            Path dirPath = Paths.get(directory);
            if (!Files.exists(dirPath)) {
                return snapshot;
            }

            Files.walk(dirPath, 3)
                .filter(Files::isRegularFile)
                .filter(path -> {
                    String name = path.getFileName().toString().toLowerCase();
                    for (String ext : DEFAULT_OUTPUT_EXTENSIONS) {
                        if (name.endsWith(ext)) {
                            return true;
                        }
                    }
                    return false;
                })
                .forEach(path -> snapshot.add(path.toString()));

        } catch (Exception e) {
            log.debug("获取目录快照失败: {} - {}", directory, e.getMessage());
        }
        return snapshot;
    }

    /**
     * 对比两个快照，找出新增的文件
     */
    public List<String> compareSnapshots(Set<String> before, Set<String> after) {
        List<String> newFiles = new ArrayList<>();
        for (String file : after) {
            if (!before.contains(file)) {
                newFiles.add(file);
            }
        }
        return newFiles;
=======
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
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
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
<<<<<<< HEAD
        /** 输出目录路径 */
        private String outputDirectory;
=======
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2

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
