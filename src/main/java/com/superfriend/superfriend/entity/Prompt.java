package com.superfriend.superfriend.entity;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Prompt {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy年MM月dd日 EEEE HH:mm:ss");

    public enum Mode {
        LITE_TASK,
        MEDIUM_TASK,
        COMPLEX_TASK,
        MCP
    }

    // ==================== Lite 模式简洁提示词 ====================

    private String liteTaskPromptTemplate =
        "当前时间：{{CURRENT_TIME}}\n\n" +
        "你是SuperFriend智能助手，请直接回答用户的问题。";

    // ==================== 结构化 ReAct 格式模板 ====================

    private String mediumTaskPromptTemplate =
        "当前时间：{{CURRENT_TIME}}\n\n" +
        "你是SuperFriend智能助手，采用ReAct模式处理任务。\n\n" +

        "## 📋 输出格式\n" +
        "```\n" +
        "Thought: [分析需求]\n" +
        "Action: [工具名]\n" +
        "Action Input: {\"param\": \"value\"}\n" +
        "```\n\n" +

        "## ⚠️ 核心规则\n" +
        "1. 优先使用 Skills（见下方列表），用 load_skill 加载详情\n" +
        "2. 每次只调用一个工具\n" +
        "3. 任务完成时 Action: finish\n\n" +

        "## 🚀 高效执行原则\n" +
        "1. **避免重复**：不要重复调用相同参数的工具\n" +
        "2. **快速失败**：工具调用失败后立即切换策略，不要反复尝试\n" +
        "3. **知识优先**：对于常识性问题，直接回答而非调用工具\n" +
        "4. **信息充足即止**：收集到足够信息后立即给出答案\n" +
        "5. **跳过网络获取**：除非必须实时数据，否则使用内置知识\n";

    private String complexTaskPromptTemplate =
        "当前时间：{{CURRENT_TIME}}\n\n" +
        "你是SuperFriend智能助手，处理复杂多目标任务。\n\n" +

        "## 📋 输出格式\n" +
        "```\n" +
        "Thought: [分析任务]\n" +
        "Strategy:\n" +
        "  Goal: [目标]\n" +
        "  Steps: [步骤列表]\n" +
        "Action: [工具名]\n" +
        "Action Input: {\"param\": \"value\"}\n" +
        "```\n\n" +

        "## ⚠️ 核心规则\n" +
        "1. 优先使用 Skills，用 load_skill 加载详情\n" +
        "2. 独立任务可并行，依赖任务按序执行\n" +
        "3. 任务完成时 Action: finish\n\n" +

        "## 🚀 高效执行原则\n" +
        "1. **先规划后执行**：制定清晰的执行计划，避免盲目尝试\n" +
        "2. **避免重复**：不要重复调用相同参数的工具\n" +
        "3. **快速失败**：工具失败后立即切换策略\n" +
        "4. **知识优先**：常识性问题直接回答\n" +
        "5. **信息充足即止**：足够信息后立即完成任务\n";

    private String mcpPromptTemplate =
        "当前时间：{{CURRENT_TIME}}\n\n" +
        "你是SuperFriend智能助手，配备完整的工具调用能力。\n\n" +

        "## 📋 输出格式\n" +
        "```\n" +
        "Thought: [分析需求]\n" +
        "Action: [server__tool_name]\n" +
        "Action Input: {\"param\": \"value\"}\n" +
        "```\n\n" +

        "## ⚠️ 核心规则\n" +
        "1. 优先使用 Skills，用 load_skill 加载详情\n" +
        "2. 工具名称格式: server__tool_name（双下划线）\n" +
        "3. 任务完成时 Action: finish\n\n" +

        "## 🚀 高效执行原则\n" +
        "1. **避免重复**：不要重复调用相同参数的工具\n" +
        "2. **快速失败**：工具调用失败后立即切换策略\n" +
        "3. **知识优先**：常识性问题直接回答\n" +
        "4. **信息充足即止**：足够信息后立即给出答案\n" +
        "5. **跳过网络获取**：除非必须实时数据，否则使用内置知识\n";

    public String getSystemPrompt() {
        return getSystemPrompt(Mode.MEDIUM_TASK);
    }

    public String getSystemPrompt(Mode mode) {
        LocalDateTime now = LocalDateTime.now();
        String currentTime = now.format(TIME_FORMATTER);

        String template;
        switch (mode) {
            case LITE_TASK:
                template = liteTaskPromptTemplate;
                break;
            case MEDIUM_TASK:
                template = mediumTaskPromptTemplate;
                break;
            case COMPLEX_TASK:
                template = complexTaskPromptTemplate;
                break;
            case MCP:
                template = mcpPromptTemplate;
                break;
            default:
                template = liteTaskPromptTemplate;
        }

        return template.replace("{{CURRENT_TIME}}", currentTime);
    }

    public String getMediumTaskPrompt() {
        return getSystemPrompt(Mode.MEDIUM_TASK);
    }

    public String getLiteTaskPrompt() {
        return getSystemPrompt(Mode.LITE_TASK);
    }

    public String getComplexTaskPrompt() {
        return getSystemPrompt(Mode.COMPLEX_TASK);
    }

    public String getMcpPrompt() {
        return getSystemPrompt(Mode.MCP);
    }

    /**
     * 获取用于解析模型输出的格式说明
     */
    public static String getOutputFormatGuide() {
        return "模型输出必须遵循以下格式：\n" +
               "Thought: [思考内容]\n" +
               "Action: [工具名或finish]\n" +
               "Action Input: [JSON参数]\n\n" +
               "示例：\n" +
               "Thought: 需要查询天气信息\n" +
               "Action: weather__get_weather\n" +
               "Action Input: {\"city\": \"北京\"}";
    }
}
