package com.superfriend.superfriend.service;

import com.superfriend.superfriend.agent.skill.SkillRegistry;
import com.superfriend.superfriend.config.FilePathConfig;
import com.superfriend.superfriend.entity.Prompt;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 系统上下文构建器
 * 统一管理系统提示词的注入，避免重复和过长问题
 */
@Slf4j
@Service
public class SystemContextBuilder {

    @Autowired
    @Lazy
    private UserProfileService userProfileService;

    @Autowired
    @Lazy
    private KnowledgeGraphService knowledgeGraphService;

    @Autowired
    @Lazy
    private SkillRegistry skillRegistry;

    @Autowired
    @Lazy
    private SkillService skillService;

    @Autowired
    private FilePathConfig filePathConfig;

    /**
     * 最大系统提示词长度（字符数），约等于 8000 tokens
     */
    private static final int MAX_SYSTEM_PROMPT_LENGTH = 32000;

    /**
     * 各部分最大长度限制
     */
    private static final int MAX_USER_PROFILE_LENGTH = 500;
    private static final int MAX_KNOWLEDGE_GRAPH_LENGTH = 800;
    // 技能目录不限制长度，完整展示

    /**
     * 构建配置
     */
    @Data
    public static class BuildConfig {
        private Prompt.Mode promptMode = Prompt.Mode.MEDIUM_TASK;
        private Long userId;
        private String sessionId;
        private String userMessage;
        private boolean enableUserProfile = true;
        private boolean enableKnowledgeGraph = true;
        private boolean enableSkillCatalog = true;
        private boolean enableToolList = false;
        private String customBasePrompt;  // 自定义基础提示词

        public static BuildConfig create() {
            return new BuildConfig();
        }

        public BuildConfig promptMode(Prompt.Mode mode) {
            this.promptMode = mode;
            return this;
        }

        public BuildConfig userId(Long userId) {
            this.userId = userId;
            return this;
        }

        public BuildConfig sessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        public BuildConfig userMessage(String userMessage) {
            this.userMessage = userMessage;
            return this;
        }

        public BuildConfig enableUserProfile(boolean enable) {
            this.enableUserProfile = enable;
            return this;
        }

        public BuildConfig enableKnowledgeGraph(boolean enable) {
            this.enableKnowledgeGraph = enable;
            return this;
        }

        public BuildConfig enableSkillCatalog(boolean enable) {
            this.enableSkillCatalog = enable;
            return this;
        }

        public BuildConfig enableToolList(boolean enable) {
            this.enableToolList = enable;
            return this;
        }

        public BuildConfig customBasePrompt(String prompt) {
            this.customBasePrompt = prompt;
            return this;
        }
    }

    /**
     * 构建结果
     */
    @Data
    public static class BuildResult {
        private String systemPrompt;
        private int totalLength;
        private int userProfileLength;
        private int knowledgeGraphLength;
        private int skillCatalogLength;
        private boolean truncated;

        public String getSystemPrompt() {
            return systemPrompt;
        }
    }

    /**
     * 构建完整的系统提示词
     */
    public BuildResult build(BuildConfig config) {
        BuildResult result = new BuildResult();
        StringBuilder sb = new StringBuilder();

        // 1. 基础提示词
        String basePrompt = config.getCustomBasePrompt();
        if (basePrompt == null || basePrompt.isEmpty()) {
            Prompt prompt = new Prompt();
            basePrompt = prompt.getSystemPrompt(config.getPromptMode());
        }
        sb.append(basePrompt);

        // 2. 注入用户画像
        if (config.isEnableUserProfile() && config.getUserId() != null) {
            String profileContext = buildUserProfileContext(config);
            if (profileContext != null && !profileContext.isEmpty()) {
                sb.append("\n\n").append(profileContext);
                result.setUserProfileLength(profileContext.length());
                log.debug("[userId={}] 注入用户画像（{} 字符）", config.getUserId(), profileContext.length());
            }
        }

        // 3. 注入知识图谱
        if (config.isEnableKnowledgeGraph() && config.getUserId() != null && config.getSessionId() != null) {
            String graphContext = buildKnowledgeGraphContext(config);
            if (graphContext != null && !graphContext.isEmpty()) {
                sb.append("\n\n").append(graphContext);
                result.setKnowledgeGraphLength(graphContext.length());
                log.debug("[userId={}, sessionId={}] 注入知识图谱（{} 字符）",
                    config.getUserId(), config.getSessionId(), graphContext.length());
            }
        }

        // 4. 注入技能目录
        if (config.isEnableSkillCatalog()) {
            String skillCatalog = buildSkillCatalog(config);
            if (skillCatalog != null && !skillCatalog.isEmpty()) {
                sb.append("\n\n").append(skillCatalog);
                result.setSkillCatalogLength(skillCatalog.length());
                log.debug("[userId={}] 注入技能目录（{} 字符）", config.getUserId(), skillCatalog.length());
            }
        }

        // 5. 工具列表提示（仅MCP模式）
        if (config.isEnableToolList()) {
            sb.append("\n\n## 🔧 可用工具\n");
            sb.append("工具详细参数已通过 API 传递，按需调用即可。格式: `server__tool_name`\n");
        }

        // 6. 检查并截断
        String finalPrompt = sb.toString();
        result.setTotalLength(finalPrompt.length());

        if (finalPrompt.length() > MAX_SYSTEM_PROMPT_LENGTH) {
            log.warn("[userId={}] 系统提示词过长 ({} 字符)，正在截断",
                config.getUserId(), finalPrompt.length());
            finalPrompt = finalPrompt.substring(0, MAX_SYSTEM_PROMPT_LENGTH);
            finalPrompt += "\n\n[系统提示词已截断，请使用 load_skill 工具获取完整技能信息]";
            result.setTruncated(true);
            log.info("[userId={}] 系统提示词截断完成，新长度: {} 字符",
                config.getUserId(), finalPrompt.length());
        }

        result.setSystemPrompt(finalPrompt);
        return result;
    }

    /**
     * 构建用户画像上下文
     */
    private String buildUserProfileContext(BuildConfig config) {
        try {
            String context = userProfileService.generateUserProfileContextForLLM(
                config.getUserId(), config.getUserMessage());

            if (context != null && context.length() > MAX_USER_PROFILE_LENGTH) {
                context = context.substring(0, MAX_USER_PROFILE_LENGTH) + "\n...(更多画像信息已省略)\n```\n";
                log.debug("[userId={}] 用户画像已截断至 {} 字符", config.getUserId(), MAX_USER_PROFILE_LENGTH);
            }

            return context;
        } catch (Exception e) {
            log.warn("[userId={}] 构建用户画像上下文失败：{}", config.getUserId(), e.getMessage());
            return null;
        }
    }

    /**
     * 构建知识图谱上下文
     */
    private String buildKnowledgeGraphContext(BuildConfig config) {
        try {
            String context = knowledgeGraphService.generateConversationGraphContextForLLM(
                config.getUserId(), config.getSessionId(), config.getUserMessage());

            if (context != null && context.length() > MAX_KNOWLEDGE_GRAPH_LENGTH) {
                context = context.substring(0, MAX_KNOWLEDGE_GRAPH_LENGTH) + "\n...(更多知识已省略)\n```\n";
                log.debug("[userId={}, sessionId={}] 知识图谱已截断至 {} 字符",
                    config.getUserId(), config.getSessionId(), MAX_KNOWLEDGE_GRAPH_LENGTH);
            }

            return context;
        } catch (Exception e) {
            log.warn("[userId={}, sessionId={}] 构建知识图谱上下文失败：{}",
                config.getUserId(), config.getSessionId(), e.getMessage());
            return null;
        }
    }

    /**
     * 构建技能目录（不限制长度，完整展示）
     */
    private String buildSkillCatalog(BuildConfig config) {
        try {
            String catalog = skillRegistry.generateSkillCatalogPromptForUser(
                config.getUserId(), skillService);

            // 添加工具选择指南
            String toolSelectionGuide = buildToolSelectionGuide();
            return catalog + "\n\n" + toolSelectionGuide;
        } catch (Exception e) {
            log.warn("[userId={}] 构建技能目录失败：{}", config.getUserId(), e.getMessage());
            return null;
        }
    }

    /**
     * 构建工具选择指南
     */
    private String buildToolSelectionGuide() {
        StringBuilder sb = new StringBuilder();
        sb.append("## 🔧 工具选择指南\n\n");
        sb.append("### Skills 工具（优先使用）\n");
        sb.append("- 任务涉及文档生成（Word、PDF、Excel）→ 使用 minimax-docx/minimax-pdf/minimax-xlsx\n");
        sb.append("- 任务涉及设计或前端开发 → 使用 canvas-design/frontend-design\n");
        sb.append("- 调用流程：load_skill → read_skill_resource(可选) → run_skill_script\n\n");
        sb.append("### bash-sandbox 工具\n");
        sb.append("- 需要执行通用 shell 命令\n");
        sb.append("- 需要会话状态保持（多个相关命令）\n");
        sb.append("- 工具名格式：bash-sandbox__execute\n\n");
        sb.append("### 重要提示\n");
        sb.append("- run_skill_script: 执行 Skills 脚本，输出文件自动发送给用户\n");
        sb.append("- bash-sandbox__execute: 执行通用命令，生成文件后需调用 send_file 发送\n");
        sb.append("- 所有脚本都在安全的沙箱环境中执行\n\n");

        // 添加沙箱环境信息
        sb.append(buildSandboxEnvironmentInfo());

        return sb.toString();
    }

    /**
     * 构建沙箱环境信息
     * 让大模型知道文件在哪里、如何与其他工具协作
     */
    private String buildSandboxEnvironmentInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("## 📂 沙箱环境\n\n");

        sb.append("### 核心概念\n");
        sb.append("1. **会话 (Session)**: 每个会话有独立的工作目录，命令在会话中执行\n");
        sb.append("2. **共享目录**: 所有会话都可以访问，用于跨工具文件共享\n");
        sb.append("3. **导出文件**: 将沙箱内文件导出到共享目录，供其他工具访问\n\n");

        sb.append("### 工具使用模式\n");
        sb.append("```\n");
        sb.append("# 模式1: Skills 脚本（推荐）\n");
        sb.append("load_skill(skill_name=\"minimax-docx\")  # 获取脚本路径和参数说明\n");
        sb.append("run_skill_script(skill_name=\"minimax-docx\", script_name=\"scripts/create.py\", parameters={...})\n");
        sb.append("# 输出文件自动发送给用户\n\n");

        sb.append("# 模式2: 通用命令 + 手动发送文件\n");
        sb.append("result = bash-sandbox__execute(command=\"python generate_image.py\")\n");
        sb.append("# result 包含: sessionId, workingDirectory, stdout, stderr\n");
        sb.append("# 步骤1: 导出文件到共享目录\n");
        sb.append("bash-sandbox__export_file(sourcePath=\"output.png\")\n");
        sb.append("# 步骤2: 发送文件给用户\n");
        sb.append("send_file(file_path=\"共享目录路径/output.png\")\n\n");

        sb.append("# 模式3: 文件导出（跨工具协作）\n");
        sb.append("bash-sandbox__execute(command=\"echo hello > output.txt\")\n");
        sb.append("bash-sandbox__export_file(sourcePath=\"output.txt\")  # 导出到共享目录\n");
        sb.append("# 然后其他工具可以访问共享目录中的文件\n");
        sb.append("```\n\n");

        sb.append("### 重要提示\n");
        sb.append("- **run_skill_script**: 自动处理文件发送，适合文档生成等任务\n");
        sb.append("- **bash-sandbox__execute**: 适合通用命令，需要手动导出文件\n");
        sb.append("- **会话复用**: 使用返回的 sessionId 保持命令间的状态\n");
        sb.append("- **路径处理**: 使用相对路径，或使用返回的 workingDirectory 作为基准\n");

        return sb.toString();
    }

    /**
     * 快捷方法：构建 Lite 模式系统提示词
     */
    public String buildLitePrompt(Long userId, String sessionId, String userMessage) {
        BuildResult result = build(BuildConfig.create()
            .promptMode(Prompt.Mode.LITE_TASK)
            .userId(userId)
            .sessionId(sessionId)
            .userMessage(userMessage)
            .enableSkillCatalog(false)
            .enableToolList(false));

        return result.getSystemPrompt();
    }

    /**
     * 快捷方法：构建 MCP 模式系统提示词（包含工具列表）
     */
    public String buildMcpPrompt(Long userId, String sessionId, String userMessage) {
        BuildResult result = build(BuildConfig.create()
            .promptMode(Prompt.Mode.MCP)
            .userId(userId)
            .sessionId(sessionId)
            .userMessage(userMessage)
            .enableSkillCatalog(true)
            .enableToolList(true));

        return result.getSystemPrompt();
    }

    /**
     * 快捷方法：构建 Agent 模式系统提示词
     */
    public String buildAgentPrompt(Long userId, String sessionId, String userMessage, Prompt.Mode mode) {
        BuildResult result = build(BuildConfig.create()
            .promptMode(mode)
            .userId(userId)
            .sessionId(sessionId)
            .userMessage(userMessage)
            .enableSkillCatalog(true)
            .enableToolList(true));

        return result.getSystemPrompt();
    }

    /**
     * 快捷方法：构建普通对话系统提示词（AIService 使用）
     */
    public String buildChatPrompt(Long userId, String sessionId, String userMessage) {
        BuildResult result = build(BuildConfig.create()
            .promptMode(Prompt.Mode.MEDIUM_TASK)
            .userId(userId)
            .sessionId(sessionId)
            .userMessage(userMessage)
            .enableSkillCatalog(true)
            .enableToolList(false));

        return result.getSystemPrompt();
    }
}
