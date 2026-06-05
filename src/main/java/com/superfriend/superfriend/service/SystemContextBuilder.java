package com.superfriend.superfriend.service;

import com.superfriend.superfriend.agent.skill.SkillRegistry;
import com.superfriend.superfriend.config.FilePathConfig;
import com.superfriend.superfriend.dto.ChatMessageContent;
import com.superfriend.superfriend.dto.FileIndexEntry;
import com.superfriend.superfriend.entity.Prompt;
import com.superfriend.superfriend.entity.ChatCompression;
import com.superfriend.superfriend.entity.AgentTaskPlan;
import com.superfriend.superfriend.mapper.ChatCompressionMapper;
import com.superfriend.superfriend.mapper.AgentTaskPlanMapper;
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
    private KnowledgeGraphService knowledgeGraphService;

    @Autowired
    @Lazy
    private SkillRegistry skillRegistry;

    @Autowired
    @Lazy
    private SkillService skillService;

    @Autowired
    @Lazy
    private MemoryRetrievalService memoryRetrievalService;

    @Autowired
    private FilePathConfig filePathConfig;

    @Autowired
    @Lazy
    private ChatCompressionMapper chatCompressionMapper;

    @Autowired
    @Lazy
    private AgentTaskPlanMapper agentTaskPlanMapper;

    @Autowired
    @Lazy
    private SessionFileIndexService sessionFileIndexService;

    /**
     * 最大系统提示词长度（字符数），约等于 8000 tokens
     */
    private static final int MAX_SYSTEM_PROMPT_LENGTH = 32000;

    /**
     * 各部分最大长度限制
     */
    private static final int MAX_KNOWLEDGE_GRAPH_LENGTH = 800;
    private static final int MAX_MEMORY_PALACE_LENGTH = 1000;
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
        private boolean enableMemoryPalace = true;
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

        public BuildConfig enableMemoryPalace(boolean enable) {
            this.enableMemoryPalace = enable;
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

        // 2. 注入对话摘要（新增）
        if (config.getSessionId() != null) {
            String summaryContext = buildConversationSummary(config);
            if (summaryContext != null && !summaryContext.isEmpty()) {
                sb.append("\n\n").append(summaryContext);
                log.debug("[sessionId={}] 注入对话摘要（{} 字符）", config.getSessionId(), summaryContext.length());
            }
        }

        // 3. 注入记忆宫殿（替代旧的用户画像）
        if (config.isEnableMemoryPalace() && config.getUserId() != null) {
            String memoryContext = buildMemoryPalaceContext(config);
            if (memoryContext != null && !memoryContext.isEmpty()) {
                sb.append("\n\n").append(memoryContext);
                log.debug("[userId={}] 注入记忆宫殿（{} 字符）", config.getUserId(), memoryContext.length());
            }
        }

        // 4. 注入知识图谱
        if (config.isEnableKnowledgeGraph() && config.getUserId() != null && config.getSessionId() != null) {
            String graphContext = buildKnowledgeGraphContext(config);
            if (graphContext != null && !graphContext.isEmpty()) {
                sb.append("\n\n").append(graphContext);
                result.setKnowledgeGraphLength(graphContext.length());
                log.debug("[userId={}, sessionId={}] 注入知识图谱（{} 字符）",
                    config.getUserId(), config.getSessionId(), graphContext.length());
            }
        }

        // 5. 注入技能目录
        if (config.isEnableSkillCatalog()) {
            String skillCatalog = buildSkillCatalog(config);
            if (skillCatalog != null && !skillCatalog.isEmpty()) {
                sb.append("\n\n").append(skillCatalog);
                result.setSkillCatalogLength(skillCatalog.length());
                log.debug("[userId={}] 注入技能目录（{} 字符）", config.getUserId(), skillCatalog.length());
            }
        }

        // 6. 注入任务状态（新增）
        if (config.getSessionId() != null) {
            String taskStatusContext = buildTaskStatus(config);
            if (taskStatusContext != null && !taskStatusContext.isEmpty()) {
                sb.append("\n\n").append(taskStatusContext);
                log.debug("[sessionId={}] 注入任务状态（{} 字符）", config.getSessionId(), taskStatusContext.length());
            }
        }

        // 7. 注入会话文件状态（新增）
        appendSessionFileContext(sb, config.getSessionId());

        // 8. 工具列表提示（仅MCP模式）
        if (config.isEnableToolList()) {
            sb.append("\n\n## 🔧 可用工具\n");
            sb.append("工具详细参数已通过 API 传递，按需调用即可。格式: `server__tool_name`\n");
        }

        // 9. 检查并截断
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
     * 构建对话摘要上下文
     */
    private String buildConversationSummary(BuildConfig config) {
        try {
            ChatCompression compression = chatCompressionMapper.findLatestBySessionId(config.getSessionId());
            if (compression == null || compression.getSummary() == null || compression.getSummary().isEmpty()) {
                return null;
            }

            StringBuilder sb = new StringBuilder();
            sb.append("## 📝 对话摘要\n\n");
            sb.append("以下是之前对话的压缩摘要，可帮助理解上下文：\n\n");
            sb.append(compression.getSummary());

            if (compression.getOriginalMessageCount() != null && compression.getCompressedMessageCount() != null) {
                sb.append("\n\n_（已压缩 ").append(compression.getOriginalMessageCount())
                  .append(" 条消息为 ").append(compression.getCompressedMessageCount())
                  .append(" 条摘要）_\n");
            }

            return sb.toString();
        } catch (Exception e) {
            log.warn("[sessionId={}] 构建对话摘要失败：{}", config.getSessionId(), e.getMessage());
            return null;
        }
    }

    /**
     * 构建任务状态上下文
     */
    private String buildTaskStatus(BuildConfig config) {
        try {
            List<AgentTaskPlan> plans = agentTaskPlanMapper.findBySessionId(config.getSessionId());
            if (plans == null || plans.isEmpty()) {
                return null;
            }

            // 获取最近的任务计划
            AgentTaskPlan activePlan = null;
            for (AgentTaskPlan plan : plans) {
                if ("RUNNING".equals(plan.getStatus()) || "PENDING".equals(plan.getStatus())) {
                    activePlan = plan;
                    break;
                }
            }
            if (activePlan == null) {
                activePlan = plans.get(0);
            }

            // 只显示进行中的任务
            if (!"RUNNING".equals(activePlan.getStatus())) {
                return null;
            }

            StringBuilder sb = new StringBuilder();
            sb.append("## 📊 当前任务状态\n\n");
            sb.append("**任务**: ").append(activePlan.getPlanSummary() != null
                ? activePlan.getPlanSummary()
                : activePlan.getOriginalRequest()).append("\n\n");

            int completed = activePlan.getCompletedSteps() != null ? activePlan.getCompletedSteps() : 0;
            int total = activePlan.getTotalSteps() != null ? activePlan.getTotalSteps() : 0;
            int progress = total > 0 ? (completed * 100 / total) : 0;

            sb.append("**进度**: ").append(completed).append("/").append(total)
              .append(" 步骤 (").append(progress).append("%)\n\n");

            sb.append("**状态**: ");
            switch (activePlan.getStatus()) {
                case "RUNNING": sb.append("🔄 执行中"); break;
                case "PENDING": sb.append("⏳ 等待中"); break;
                case "COMPLETED": sb.append("✅ 已完成"); break;
                case "FAILED": sb.append("❌ 失败"); break;
                default: sb.append(activePlan.getStatus());
            }
            sb.append("\n");

            return sb.toString();
        } catch (Exception e) {
            log.warn("[sessionId={}] 构建任务状态失败：{}", config.getSessionId(), e.getMessage());
            return null;
        }
    }

    /**
     * 构建记忆宫殿上下文（替代旧的用户画像）
     */
    private String buildMemoryPalaceContext(BuildConfig config) {
        try {
            String context = memoryRetrievalService.generateMemoryContext(
                config.getUserId(), config.getUserMessage());

            if (context != null && context.length() > MAX_MEMORY_PALACE_LENGTH) {
                context = context.substring(0, MAX_MEMORY_PALACE_LENGTH) + "\n...(更多记忆已省略)\n```\n";
                log.debug("[userId={}] 记忆宫殿已截断至 {} 字符", config.getUserId(), MAX_MEMORY_PALACE_LENGTH);
            }

            return context;
        } catch (Exception e) {
            log.warn("[userId={}] 构建记忆宫殿上下文失败：{}", config.getUserId(), e.getMessage());
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
     * 构建工具选择指南（精简版）
     */
    private String buildToolSelectionGuide() {
        StringBuilder sb = new StringBuilder();
        sb.append("## 🔧 工具快速选择\n\n");

        sb.append("### ⚡ Skills 工作流（文档生成必用）\n");
        sb.append("当用户需要生成 Word/PDF/Excel 文档时，必须使用 Skills 工作流：\n");
        sb.append("```\n");
        sb.append("步骤1: load_skill(skill_name=\"minimax-docx\")  # 获取技能详情和脚本列表\n");
        sb.append("步骤2: run_skill_script(skill_name=\"minimax-docx\", script_name=\"scripts/create.py\", parameters={...})  # 执行脚本生成文档\n");
        sb.append("```\n");
        sb.append("⚠️ 重要：不要直接调用 bash-sandbox__execute 来执行技能脚本，必须使用 run_skill_script！\n");
        sb.append("(run_skill_script 内部会自动调用 bash-sandbox 并处理环境配置和文件发送)\n\n");

        sb.append("### 按场景选择\n");
        sb.append("| 用户需求 | 推荐工具 | 说明 |\n");
        sb.append("|----------|----------|------|\n");
        sb.append("| 生成Word/PDF/Excel | `load_skill` → `run_skill_script` | minimax-docx/pdf/xlsx |\n");
        sb.append("| 搜索网络信息 | `web_search` | 返回搜索结果和AI答案 |\n");
        sb.append("| 抓取网页内容 | `web_extract` | 自动处理反爬虫 |\n");
        sb.append("| 执行代码/命令 | `bash-sandbox__execute` | 沙箱环境执行 |\n");
        sb.append("| 读取用户文件 | `read_user_file` | 读取之前上传的文件 |\n");
        sb.append("| 发送文件给用户 | `send_file` | 将生成的文件发送 |\n");
        sb.append("| 设计/前端 | `load_skill` | canvas-design/frontend-design |\n");
        sb.append("|\n\n");

        // 【新增】沙箱环境详细说明
        sb.append("### 📦 沙箱环境说明\n");
        sb.append("沙箱是一个隔离的命令执行环境，用于安全地运行代码和脚本。\n\n");
        sb.append("**核心概念**：\n");
        sb.append("- **sessionId**: 会话标识，用于保持命令间的状态（环境变量、工作目录等）\n");
        sb.append("- **workingDirectory**: 当前工作目录，所有相对路径的文件操作都在此目录下进行\n");
        sb.append("- **自动会话管理**: 首次执行命令时自动创建会话，后续可复用 sessionId\n\n");
        sb.append("**环境变量**（执行脚本时自动注入）：\n");
        sb.append("- `$SKILL_DIR`: 当前技能的目录路径\n");
        sb.append("- `$SKILL_OUTPUT_DIR`: 建议的输出目录，生成的文件应放在此目录\n");
        sb.append("- `$SKILL_SESSION_ID`: 当前会话ID\n");
        sb.append("- `$SKILL_PARAM_*`: 用户传入的参数（如 `$SKILL_PARAM_title`）\n\n");
        sb.append("**最佳实践**：\n");
        sb.append("```\n");
        sb.append("# 1. 首次执行会返回 sessionId 和 workingDirectory\n");
        sb.append("bash-sandbox__execute(command=\"pwd\")  \n");
        sb.append("# 返回: sessionId='sess_xxx', workingDirectory='/path/to/workspace'\n\n");
        sb.append("# 2. 后续命令复用 sessionId 保持状态\n");
        sb.append("bash-sandbox__execute(sessionId=\"sess_xxx\", command=\"python script.py\")\n\n");
        sb.append("# 3. 生成文件时使用环境变量指定路径\n");
        sb.append("bash-sandbox__execute(command=\"python -c \\\"import os; print(os.environ.get('SKILL_OUTPUT_DIR'))\\\"\")\n\n");
        sb.append("# 4. 输出文件建议使用 $SKILL_OUTPUT_DIR 作为前缀\n");
        sb.append("bash-sandbox__execute(command=\"echo 'content' > $SKILL_OUTPUT_DIR/output.txt\")\n");
        sb.append("```\n\n");
        sb.append("**注意事项**：\n");
        sb.append("- Windows 环境优先使用 Git Bash，支持完整 bash 语法\n");
        sb.append("- 如无 Git Bash 则使用 PowerShell，语法有所不同\n");
        sb.append("- 危险命令（如 `rm -rf /`）会被阻止\n");
        sb.append("- 会话默认 30 分钟无活动后自动清理\n\n");

        sb.append("### 调用流程\n");
        sb.append("```\n");
        sb.append("# Skills 流程（文档生成推荐）\n");
        sb.append("load_skill(skill_name=\"minimax-docx\")  # 获取脚本路径\n");
        sb.append("run_skill_script(skill_name=\"minimax-docx\", script_name=\"scripts/create.py\", parameters={...})  # 生成文档\n\n");

        sb.append("# 通用命令流程（非技能任务）\n");
        sb.append("bash-sandbox__execute(command=\"python script.py\")  # 执行命令\n");
        sb.append("bash-sandbox__export_file(sourcePath=\"output.pdf\")  # 导出到共享目录\n");
        sb.append("send_file(file_path=\"共享目录/output.pdf\")  # 发送给用户\n");
        sb.append("```\n\n");

        sb.append("### 重要提示\n");
        sb.append("- **run_skill_script**: 用于执行技能脚本，自动处理环境配置和文件发送\n");
        sb.append("- **bash-sandbox__execute**: 用于通用命令，需手动导出和发送文件\n");
        sb.append("- **工具名格式**: MCP工具使用 `server__tool_name`（双下划线）\n");

        return sb.toString();
    }

    /**
     * 追加会话文件状态到系统提示
     * 让 AI 知道当前会话中有哪些文件可用（包括上传的和生成的）
     */
    private void appendSessionFileContext(StringBuilder sb, String sessionId) {
        if (sessionId == null) return;

        try {
            List<FileIndexEntry> files = sessionFileIndexService.getFileIndex(sessionId);
            if (files == null || files.isEmpty()) return;

            sb.append("\n\n【当前会话文件】\n");
            sb.append("会话中已有以下文件，你可以通过文件路径操作它们：\n\n");

            for (int i = 0; i < files.size(); i++) {
                FileIndexEntry entry = files.get(i);
                sb.append(i + 1).append(". ");
                sb.append(entry.getFileName() != null ? entry.getFileName() : "未知文件");
                if (entry.getMimeType() != null) {
                    sb.append(" (").append(entry.getMimeType()).append(")");
                }
                if (entry.getFileSize() > 0) {
                    sb.append(", 大小: ").append(formatFileSize(entry.getFileSize()));
                }
                if (entry.getTempUrl() != null) {
                    sb.append(", 路径: ").append(entry.getTempUrl());
                }
                sb.append("\n");
            }

            sb.append("\n提示：对已生成的文件，可使用 edit fill-placeholders 或 edit replace-text 追加内容，无需重新创建。\n");

            // 添加分批生成策略引导
            sb.append("\n【长文档生成策略】\n");
            sb.append("当需要生成超过 2000 字的文档时，必须分批进行：\n");
            sb.append("1. 第一步：使用 create 命令创建文档骨架（标题 + 目录结构）\n");
            sb.append("2. 第二步：使用 edit fill-placeholders 或 edit replace-text 逐章填充内容\n");
            sb.append("3. 每次填充一个章节（约 1000-2000 字），避免单次输出超出 token 限制\n");
            sb.append("4. 填充完成后，使用 validate 验证文档完整性\n\n");
            sb.append("示例流程：\n");
            sb.append("```\n");
            sb.append("# Step 1: 创建骨架\n");
            sb.append("run_skill_script(skill_name=\"minimax-docx\", script_name=\"create\", parameters={\"output\": \"论文.docx\", \"title\": \"xxx\", \"toc\": true})\n\n");
            sb.append("# Step 2: 逐章填充\n");
            sb.append("run_skill_script(skill_name=\"minimax-docx\", script_name=\"edit fill-placeholders\", parameters={\"input\": \"论文.docx\", \"data\": \"{\\\"第一章\\\": \\\"内容...\\\"}\"})\n\n");
            sb.append("# Step 3: 继续填充下一章\n");
            sb.append("run_skill_script(skill_name=\"minimax-docx\", script_name=\"edit fill-placeholders\", parameters={\"input\": \"论文.docx\", \"data\": \"{\\\"第二章\\\": \\\"内容...\\\"}\"})\n");
            sb.append("```\n");
        } catch (Exception e) {
            log.debug("构建会话文件上下文失败: {}", e.getMessage());
        }
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + "B";
        if (bytes < 1024 * 1024) return String.format("%.1fKB", bytes / 1024.0);
        return String.format("%.1fMB", bytes / (1024.0 * 1024.0));
    }

    /**
     * 构建沙箱环境信息（已精简，保留核心内容）
     */
    private String buildSandboxEnvironmentInfo() {
        return "";  // 已整合到 buildToolSelectionGuide 中
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
