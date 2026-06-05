package com.superfriend.superfriend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.superfriend.superfriend.dto.McpToolCallResponse;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 沙箱执行引导服务
 *
 * 核心目标：确保大模型能够正确使用沙箱环境
 *
 * 设计原则：
 * 1. 不依赖大模型阅读文档 - 通过代码强制正确流程
 * 2. 自动处理常见问题 - 减少大模型犯错的机会
 * 3. 提供清晰的错误反馈 - 让大模型知道如何修正
 */
@Slf4j
@Service
public class SandboxExecutionGuideService {

    @Autowired
    @Lazy
    private McpHostService mcpHostService;

    @Autowired
    private ObjectMapper objectMapper;

    // 沙箱会话缓存：sessionId -> 会话信息（带过期时间）
    private final Map<String, SandboxSession> sessionCache = new ConcurrentHashMap<>();
    private final Map<String, Long> sessionCreationTime = new ConcurrentHashMap<>();
    private static final long SESSION_TIMEOUT_MS = 30 * 60 * 1000; // 30分钟过期
    private static final int MAX_CACHE_SIZE = 100; // 最大缓存数量

    // 常见命令模板
    private static final Map<String, CommandTemplate> COMMAND_TEMPLATES = new HashMap<>();

    static {
        // Python 脚本执行
        COMMAND_TEMPLATES.put("python_script", new CommandTemplate(
            "python -u \"$SCRIPT_PATH\"",
            Arrays.asList("SCRIPT_PATH"),
            "执行 Python 脚本，-u 参数禁用缓冲确保实时输出"
        ));

        // 输出到指定目录
        COMMAND_TEMPLATES.put("output_file", new CommandTemplate(
            "echo \"$CONTENT\" > \"$SKILL_OUTPUT_DIR/$FILENAME\"",
            Arrays.asList("CONTENT", "FILENAME"),
            "将内容写入输出目录的文件"
        ));

        // 检查环境变量
        COMMAND_TEMPLATES.put("check_env", new CommandTemplate(
            "echo \"SKILL_DIR=$SKILL_DIR\" && echo \"SKILL_OUTPUT_DIR=$SKILL_OUTPUT_DIR\" && pwd",
            Collections.emptyList(),
            "检查当前环境变量和工作目录"
        ));

        // 列出输出目录文件
        COMMAND_TEMPLATES.put("list_output", new CommandTemplate(
            "ls -la \"$SKILL_OUTPUT_DIR\" 2>/dev/null || echo \"输出目录不存在或为空\"",
            Collections.emptyList(),
            "列出输出目录中的所有文件"
        ));
    }

    /**
     * 确保沙箱会话已创建并返回会话信息
     *
     * @param sessionId Agent 会话 ID
     * @param skillDir 技能目录（可选）
     * @return 沙箱会话信息
     */
    public SandboxSession ensureSession(String sessionId, String skillDir) {
        // 清理过期会话
        cleanupExpiredSessions();

        // 检查缓存
        SandboxSession cached = sessionCache.get(sessionId);
        if (cached != null && cached.isValid() && !isSessionExpired(sessionId)) {
            log.info("【沙箱引导】使用缓存的会话: sandboxSessionId={}, workingDir={}",
                cached.getSandboxSessionId(), cached.getWorkingDirectory());
            return cached;
        }

        // 创建新会话
        try {
            Map<String, Object> params = new HashMap<>();

            // 设置工作目录
            if (skillDir != null && !skillDir.isEmpty()) {
                params.put("workingDirectory", skillDir);
            }

            // 设置初始环境变量
            Map<String, String> env = new HashMap<>();
            env.put("SKILL_SESSION_ID", sessionId);
            params.put("environment", env);

            log.info("【沙箱引导】创建新会话: skillDir={}", skillDir);

            McpToolCallResponse response = mcpHostService.callTool(
                "bash-sandbox", "create_session", params, null);

            if (response.isSuccess()) {
                SandboxSession session = parseSessionFromResponse(response, sessionId);
                if (session != null) {
                    // 检查缓存大小
                    if (sessionCache.size() >= MAX_CACHE_SIZE) {
                        cleanupOldestSessions();
                    }
                    sessionCache.put(sessionId, session);
                    sessionCreationTime.put(sessionId, System.currentTimeMillis());
                    log.info("【沙箱引导】会话创建成功: sandboxSessionId={}, workingDir={}",
                        session.getSandboxSessionId(), session.getWorkingDirectory());
                    return session;
                }
            }

            log.warn("【沙箱引导】会话创建失败，将使用自动创建模式");

        } catch (Exception e) {
            log.warn("【沙箱引导】创建会话异常: {}", e.getMessage());
        }

        return null;
    }

    /**
     * 执行命令并自动处理会话管理
     *
     * @param sessionId Agent 会话 ID
     * @param command 要执行的命令
     * @param options 执行选项
     * @return 执行结果
     */
    public ExecutionResult executeWithGuide(String sessionId, String command, ExecutionOptions options) {
        ExecutionResult result = new ExecutionResult();
        result.setOriginalCommand(command);

        try {
            // 1. 确保有沙箱会话
            SandboxSession session = ensureSession(sessionId,
                options != null ? options.getSkillDir() : null);

            // 2. 构建执行参数
            Map<String, Object> params = new HashMap<>();
            params.put("command", command);

            if (session != null) {
                params.put("sessionId", session.getSandboxSessionId());
            }

            if (options != null) {
                if (options.getTimeout() > 0) {
                    params.put("timeout", options.getTimeout());
                }
                if (options.getEnvironment() != null && !options.getEnvironment().isEmpty()) {
                    params.put("environment", options.getEnvironment());
                }
            }

            // 3. 执行命令
            log.info("【沙箱引导】执行命令: {}", command);
            McpToolCallResponse response = mcpHostService.callTool(
                "bash-sandbox", "execute", params, null);

            // 4. 解析结果
            result.setSuccess(response.isSuccess());
            result.setOutput(extractOutput(response));
            result.setError(response.getError());

            // 5. 更新会话信息
            if (response.isSuccess()) {
                updateSessionFromResponse(sessionId, response);
            }

            // 6. 检测常见问题并提供建议
            if (!response.isSuccess() || containsError(result.getOutput())) {
                result.setSuggestion(diagnoseAndSuggest(command, result));
            }

        } catch (Exception e) {
            result.setSuccess(false);
            result.setError(e.getMessage());
            result.setSuggestion("执行失败: " + e.getMessage() + "。请检查命令格式是否正确。");
        }

        return result;
    }

    /**
     * 智能包装命令 - 自动处理路径和环境变量
     *
     * @param command 原始命令
     * @param context 执行上下文
     * @return 包装后的命令
     */
    public String wrapCommand(String command, CommandContext context) {
        String wrappedCommand = command;

        // 1. 处理输出路径 - 如果命令包含输出文件但没有指定路径
        if (containsOutputRedirect(command) && !command.contains("$SKILL_OUTPUT_DIR")
            && !command.contains("/") && !command.contains("\\")) {
            // 添加输出目录前缀
            wrappedCommand = wrappedCommand.replaceAll(">", "> \"$SKILL_OUTPUT_DIR/");
            wrappedCommand = wrappedCommand.replaceAll(">>", ">> \"$SKILL_OUTPUT_DIR/");
            log.info("【沙箱引导】自动添加输出目录前缀");
        }

        // 2. 处理 Python 脚本执行 - 添加 -u 参数确保实时输出
        if (command.startsWith("python ") && !command.contains(" -u ")) {
            wrappedCommand = command.replace("python ", "python -u ");
            log.info("【沙箱引导】自动添加 -u 参数");
        }

        // 3. 处理相对路径 - 转换为绝对路径
        if (context != null && context.getWorkingDir() != null) {
            // 这里可以添加更多路径处理逻辑
        }

        return wrappedCommand;
    }

    /**
     * 获取沙箱状态摘要 - 供大模型参考
     *
     * @param sessionId Agent 会话 ID
     * @return 状态摘要
     */
    public String getStatusSummary(String sessionId) {
        SandboxSession session = sessionCache.get(sessionId);

        StringBuilder summary = new StringBuilder();
        summary.append("=== 沙箱环境状态 ===\n");

        if (session != null) {
            summary.append("会话状态: 已创建\n");
            summary.append("沙箱会话ID: ").append(session.getSandboxSessionId()).append("\n");
            summary.append("工作目录: ").append(session.getWorkingDirectory()).append("\n");
            summary.append("\n可用环境变量:\n");
            summary.append("- $SKILL_OUTPUT_DIR: 建议的输出目录\n");
            summary.append("- $SKILL_SESSION_ID: ").append(sessionId).append("\n");

            // 尝试获取输出目录内容
            try {
                Map<String, Object> params = new HashMap<>();
                params.put("command", "ls -la \"$SKILL_OUTPUT_DIR\" 2>/dev/null || echo '(空)'");
                params.put("sessionId", session.getSandboxSessionId());

                McpToolCallResponse response = mcpHostService.callTool(
                    "bash-sandbox", "execute", params, null);

                if (response.isSuccess()) {
                    summary.append("\n输出目录内容:\n").append(extractOutput(response));
                }
            } catch (Exception e) {
                summary.append("\n(无法获取输出目录内容)\n");
            }
        } else {
            summary.append("会话状态: 未创建\n");
            summary.append("建议: 执行命令时会自动创建会话\n");
        }

        summary.append("\n=== 常用命令模板 ===\n");
        summary.append("1. 检查环境: echo $SKILL_OUTPUT_DIR && pwd\n");
        summary.append("2. 输出文件: echo '内容' > $SKILL_OUTPUT_DIR/filename.txt\n");
        summary.append("3. 执行脚本: python -u script.py\n");
        summary.append("4. 列出文件: ls -la $SKILL_OUTPUT_DIR\n");

        return summary.toString();
    }

    /**
     * 验证命令是否安全可执行
     *
     * @param command 要验证的命令
     * @return 验证结果
     */
    public ValidationResult validateCommand(String command) {
        ValidationResult result = new ValidationResult();
        result.setValid(true);

        // 检查危险命令
        String[] dangerousPatterns = {
            "rm -rf /", "rm -rf /*", "mkfs", "dd of=/dev/",
            ":(){ :|:& };:",  // fork bomb
            "> /dev/sda", "> /dev/hda"
        };

        for (String pattern : dangerousPatterns) {
            if (command.contains(pattern)) {
                result.setValid(false);
                result.setReason("命令包含危险操作: " + pattern);
                result.setSuggestion("请移除危险操作，使用更安全的方式");
                return result;
            }
        }

        // 检查常见问题
        if (command.contains("python ") && !command.contains(" -u ")) {
            result.addWarning("建议添加 -u 参数以获得实时输出");
        }

        if (command.contains("> ") && !command.contains("$SKILL_OUTPUT_DIR")
            && !command.contains("/") && !command.contains("\\")) {
            result.addWarning("输出文件未指定目录，建议使用 $SKILL_OUTPUT_DIR");
        }

        return result;
    }

    // ==================== 辅助方法 ====================

    private SandboxSession parseSessionFromResponse(McpToolCallResponse response, String agentSessionId) {
        try {
            String output = extractOutput(response);
            if (output == null) return null;

            SandboxSession session = new SandboxSession();
            session.setAgentSessionId(agentSessionId);

            // 提取 sessionId
            Pattern sessionPattern = Pattern.compile("sessionId:\\s*`([^`]+)`");
            Matcher sessionMatcher = sessionPattern.matcher(output);
            if (sessionMatcher.find()) {
                session.setSandboxSessionId(sessionMatcher.group(1));
            }

            // 提取 workingDirectory
            Pattern dirPattern = Pattern.compile("workingDirectory:\\s*`([^`]+)`");
            Matcher dirMatcher = dirPattern.matcher(output);
            if (dirMatcher.find()) {
                session.setWorkingDirectory(dirMatcher.group(1));
            }

            session.setCreatedAt(System.currentTimeMillis());

            return session.getSandboxSessionId() != null ? session : null;

        } catch (Exception e) {
            log.warn("解析会话响应失败: {}", e.getMessage());
            return null;
        }
    }

    private void updateSessionFromResponse(String sessionId, McpToolCallResponse response) {
        SandboxSession session = sessionCache.get(sessionId);
        if (session == null) {
            // 尝试从响应中创建会话
            SandboxSession newSession = parseSessionFromResponse(response, sessionId);
            if (newSession != null) {
                sessionCache.put(sessionId, newSession);
            }
        }
    }

    private String extractOutput(McpToolCallResponse response) {
        if (response.getContent() == null || response.getContent().isEmpty()) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        for (McpToolCallResponse.ContentItem item : response.getContent()) {
            if ("text".equals(item.getType()) && item.getText() != null) {
                sb.append(item.getText());
            }
        }

        return sb.toString();
    }

    private boolean containsOutputRedirect(String command) {
        return command.contains(">") || command.contains(">>");
    }

    private boolean containsError(String output) {
        if (output == null) return false;

        String[] errorPatterns = {
            "error:", "Error:", "ERROR:",
            "failed:", "Failed:", "FAILED:",
            "exception:", "Exception:",
            "不存在", "未找到", "无法", "失败"
        };

        for (String pattern : errorPatterns) {
            if (output.contains(pattern)) {
                return true;
            }
        }

        return false;
    }

    private String diagnoseAndSuggest(String command, ExecutionResult result) {
        StringBuilder suggestion = new StringBuilder();

        String output = result.getOutput() != null ? result.getOutput() : "";
        String error = result.getError() != null ? result.getError() : "";

        // 分析错误类型并提供建议
        if (error.contains("not found") || error.contains("未找到") || error.contains("不存在")) {
            suggestion.append("可能原因: 文件或命令不存在\n");
            suggestion.append("建议: 检查路径是否正确，使用 'ls' 或 'pwd' 查看当前目录\n");
        }

        if (error.contains("permission") || error.contains("权限")) {
            suggestion.append("可能原因: 权限不足\n");
            suggestion.append("建议: 检查文件权限，或尝试其他方法\n");
        }

        if (command.contains("python") && (error.contains("ModuleNotFoundError") || error.contains("ImportError"))) {
            suggestion.append("可能原因: Python 模块未安装\n");
            suggestion.append("建议: 使用 'pip install 模块名' 安装所需模块\n");
        }

        if (output.contains("SKILL_OUTPUT_DIR") && output.contains("not set")) {
            suggestion.append("可能原因: 环境变量未设置\n");
            suggestion.append("建议: 使用绝对路径或确保在正确的会话中执行\n");
        }

        if (suggestion.length() == 0) {
            suggestion.append("执行遇到问题，建议:\n");
            suggestion.append("1. 使用 'pwd' 检查当前目录\n");
            suggestion.append("2. 使用 'echo $SKILL_OUTPUT_DIR' 检查环境变量\n");
            suggestion.append("3. 使用 'ls -la' 查看目录内容\n");
        }

        return suggestion.toString();
    }

    // ==================== 数据类 ====================

    @Data
    public static class SandboxSession {
        private String agentSessionId;
        private String sandboxSessionId;
        private String workingDirectory;
        private long createdAt;
        private Map<String, String> environment;

        public boolean isValid() {
            return sandboxSessionId != null && !sandboxSessionId.isEmpty();
        }

        public boolean isExpired(long timeoutMs) {
            return System.currentTimeMillis() - createdAt > timeoutMs;
        }
    }

    @Data
    public static class ExecutionResult {
        private boolean success;
        private String originalCommand;
        private String output;
        private String error;
        private String suggestion;
        private String sandboxSessionId;
        private String workingDirectory;
    }

    @Data
    public static class ExecutionOptions {
        private String skillDir;
        private int timeout;
        private Map<String, String> environment;
    }

    @Data
    public static class CommandContext {
        private String workingDir;
        private String skillDir;
        private Map<String, String> environment;
    }

    @Data
    public static class ValidationResult {
        private boolean valid;
        private String reason;
        private String suggestion;
        private List<String> warnings = new ArrayList<>();

        public void addWarning(String warning) {
            warnings.add(warning);
        }
    }

    @Data
    public static class CommandTemplate {
        private final String template;
        private final List<String> parameters;
        private final String description;
    }

    // ==================== 缓存管理方法 ====================

    /**
     * 检查会话是否过期
     */
    private boolean isSessionExpired(String sessionId) {
        Long creationTime = sessionCreationTime.get(sessionId);
        if (creationTime == null) {
            return true;
        }
        return System.currentTimeMillis() - creationTime > SESSION_TIMEOUT_MS;
    }

    /**
     * 清理过期会话
     */
    private void cleanupExpiredSessions() {
        long now = System.currentTimeMillis();
        List<String> expiredSessions = new ArrayList<>();

        for (Map.Entry<String, Long> entry : sessionCreationTime.entrySet()) {
            if (now - entry.getValue() > SESSION_TIMEOUT_MS) {
                expiredSessions.add(entry.getKey());
            }
        }

        for (String sessionId : expiredSessions) {
            sessionCache.remove(sessionId);
            sessionCreationTime.remove(sessionId);
            log.debug("【沙箱缓存】清理过期会话: {}", sessionId);
        }

        if (!expiredSessions.isEmpty()) {
            log.info("【沙箱缓存】清理了 {} 个过期会话", expiredSessions.size());
        }
    }

    /**
     * 清理最旧的会话（当缓存满时）
     */
    private void cleanupOldestSessions() {
        // 找到最旧的会话并移除
        String oldestSessionId = null;
        long oldestTime = Long.MAX_VALUE;

        for (Map.Entry<String, Long> entry : sessionCreationTime.entrySet()) {
            if (entry.getValue() < oldestTime) {
                oldestTime = entry.getValue();
                oldestSessionId = entry.getKey();
            }
        }

        if (oldestSessionId != null) {
            sessionCache.remove(oldestSessionId);
            sessionCreationTime.remove(oldestSessionId);
            log.info("【沙箱缓存】缓存已满，移除最旧会话: {}", oldestSessionId);
        }
    }

    /**
     * 获取缓存统计信息
     */
    public int getCacheSize() {
        return sessionCache.size();
    }

    /**
     * 清理所有缓存
     */
    public void clearCache() {
        sessionCache.clear();
        sessionCreationTime.clear();
        log.info("【沙箱缓存】已清理所有缓存");
    }
}
