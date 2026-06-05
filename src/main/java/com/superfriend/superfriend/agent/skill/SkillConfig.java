package com.superfriend.superfriend.agent.skill;

import lombok.Data;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.regex.Pattern;

@Data
public class SkillConfig {
    // ==================== Agent Skills 标准字段 ====================

    /**
     * 技能名称（必需）
     * 协议要求：1-64字符，仅小写字母、数字、连字符，不能以连字符开头/结尾，不能有连续连字符
     */
    private String name;

    /**
     * 技能描述（必需）
     * 协议要求：最大 1024 字符，描述技能功能和何时使用
     */
    private String description;

    /**
     * 许可证（可选）
     */
    private String license;

    /**
     * 兼容性要求（可选）
     * 协议要求：最大 500 字符
     */
    private String compatibility;

    /**
     * 元数据（可选）
     * 协议标准字段，用于存储任意键值对
     */
    private Map<String, Object> metadata = new HashMap<>();

    /**
     * 允许的工具列表（可选）
     * 协议要求：空格分隔的工具名称列表
     */
    private List<String> allowedTools = new ArrayList<>();

    /**
     * 禁用模型自动调用（可选）
     * 协议要求：当为 true 时，技能隐藏在系统提示中，用户必须使用 /skill:name
     */
    private boolean disableModelInvocation = false;

    // ==================== SuperFriend 扩展字段（建议放入 metadata）====================

    /**
     * 执行器配置（SuperFriend 扩展）
     * 支持类型：script, command
     */
    private ExecutorConfig executor;

    /**
     * 技能指令内容（从 Markdown body 解析）
     */
    private String instructions;

    // 以下字段建议迁移到 metadata 中，但保留向后兼容

    public SkillConfig() {
    }

    public SkillConfig(String name, String description) {
        this.name = name;
        this.description = description;
    }

    // ==================== 协议验证方法 ====================

    private static final Pattern NAME_PATTERN = Pattern.compile("^[a-z0-9]([a-z0-9-]*[a-z0-9])?$");

    /**
     * 验证技能名称是否符合 Agent Skills 协议
     * @return 验证结果，null 表示通过，否则返回错误信息
     */
    public String validateName() {
        if (name == null || name.isEmpty()) {
            return "技能名称不能为空";
        }
        if (name.length() > 64) {
            return "技能名称不能超过 64 字符: " + name.length();
        }
        if (!NAME_PATTERN.matcher(name).matches()) {
            return "技能名称格式无效，仅允许小写字母、数字、连字符，不能以连字符开头/结尾或包含连续连字符: " + name;
        }
        return null;
    }

    /**
     * 验证描述是否符合 Agent Skills 协议
     * @return 验证结果，null 表示通过，否则返回错误信息
     */
    public String validateDescription() {
        if (description == null || description.isEmpty()) {
            return "技能描述不能为空";
        }
        if (description.length() > 1024) {
            return "技能描述不能超过 1024 字符: " + description.length();
        }
        return null;
    }

    /**
     * 验证兼容性字段是否符合 Agent Skills 协议
     * @return 验证结果，null 表示通过，否则返回错误信息
     */
    public String validateCompatibility() {
        if (compatibility != null && compatibility.length() > 500) {
            return "兼容性描述不能超过 500 字符: " + compatibility.length();
        }
        return null;
    }

    /**
     * 执行完整协议验证
     * @return 验证错误列表，空列表表示通过
     */
    public List<String> validate() {
        List<String> errors = new ArrayList<>();

        String nameError = validateName();
        if (nameError != null) errors.add(nameError);

        String descError = validateDescription();
        if (descError != null) errors.add(descError);

        String compatError = validateCompatibility();
        if (compatError != null) errors.add(compatError);

        return errors;
    }

    /**
     * 检查是否符合 Agent Skills 协议
     */
    public boolean isProtocolCompliant() {
        return validate().isEmpty();
    }

    // ==================== 辅助方法 ====================

    public String getMetadataString(String key) {
        Object value = metadata.get(key);
        return value != null ? String.valueOf(value) : null;
    }

    public void setMetadataString(String key, String value) {
        metadata.put(key, value);
    }

    @SuppressWarnings("unchecked")
    public List<String> getMetadataList(String key) {
        Object value = metadata.get(key);
        if (value instanceof List) {
            return (List<String>) value;
        }
        return new ArrayList<>();
    }

    public boolean hasAllowedTools() {
        return allowedTools != null && !allowedTools.isEmpty();
    }

    public boolean isToolAllowed(String toolName) {
        if (!hasAllowedTools()) {
            return true;
        }
        for (String allowed : allowedTools) {
            if (allowed.equals(toolName) || allowed.startsWith(toolName + "(") || allowed.equals("*")) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查是否有执行器配置
     */
    public boolean hasExecutor() {
        return executor != null && executor.getType() != null;
    }

    // ==================== 向后兼容的便捷方法 ====================

    /**
     * 获取版本（从 metadata 中获取）
     */
    public String getVersion() {
        return getMetadataString("version");
    }

    public void setVersion(String version) {
        metadata.put("version", version);
    }

    /**
     * 获取分类（从 metadata 中获取）
     */
    public String getCategory() {
        return getMetadataString("category");
    }

    public void setCategory(String category) {
        metadata.put("category", category);
    }

    /**
     * 获取作者（从 metadata 中获取）
     */
    public String getAuthor() {
        return getMetadataString("author");
    }

    public void setAuthor(String author) {
        metadata.put("author", author);
    }

    /**
     * 获取标签（从 metadata 中获取）
     */
    @SuppressWarnings("unchecked")
    public List<String> getTags() {
        Object value = metadata.get("tags");
        if (value instanceof List) {
            return (List<String>) value;
        }
        return new ArrayList<>();
    }

    public void setTags(List<String> tags) {
        metadata.put("tags", tags);
    }

    /**
     * 获取参数定义（从 metadata 中获取）
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getParameters() {
        Object value = metadata.get("parameters");
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return new HashMap<>();
    }

    public void setParameters(Map<String, Object> parameters) {
        metadata.put("parameters", parameters);
    }

    /**
     * 获取优先级（从 metadata 中获取）
     */
    public SkillMetadata.SkillPriority getPriority() {
        String value = getMetadataString("priority");
        if (value != null) {
            try {
                return SkillMetadata.SkillPriority.valueOf(value.toUpperCase());
            } catch (Exception e) {
                return SkillMetadata.SkillPriority.MEDIUM;
            }
        }
        return SkillMetadata.SkillPriority.MEDIUM;
    }

    public void setPriority(SkillMetadata.SkillPriority priority) {
        metadata.put("priority", priority.name());
    }

    /**
     * 获取超时时间（从 metadata 中获取）
     */
    public long getTimeout() {
        String value = getMetadataString("timeout");
        if (value != null) {
            try {
                return Long.parseLong(value);
            } catch (Exception e) {
                return 60000L;
            }
        }
        return 60000L;
    }

    public void setTimeout(long timeout) {
        metadata.put("timeout", String.valueOf(timeout));
    }

    /**
     * 是否有脚本（从 metadata 中获取）
     */
    public boolean isHasScripts() {
        String value = getMetadataString("hasScripts");
        return "true".equals(value);
    }

    public void setHasScripts(boolean hasScripts) {
        metadata.put("hasScripts", String.valueOf(hasScripts));
    }

    /**
     * 获取主脚本（从 metadata 中获取）
     */
    public String getMainScript() {
        return getMetadataString("mainScript");
    }

    public void setMainScript(String mainScript) {
        metadata.put("mainScript", mainScript);
    }

    /**
     * 获取脚本列表（从 metadata 中获取）
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getScripts() {
        Object value = metadata.get("scripts");
        if (value instanceof List) {
            return (List<Map<String, Object>>) value;
        }
        return new ArrayList<>();
    }

    public void setScripts(List<Map<String, Object>> scripts) {
        metadata.put("scripts", scripts);
    }

    /**
     * 获取资源列表（从 metadata 中获取）
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getResources() {
        Object value = metadata.get("resources");
        if (value instanceof List) {
            return (List<Map<String, Object>>) value;
        }
        return new ArrayList<>();
    }

    public void setResources(List<Map<String, Object>> resources) {
        metadata.put("resources", resources);
    }

    /**
     * 执行器配置类
     */
    @Data
    public static class ExecutorConfig {
        /**
         * 执行器类型：
         * - script: 传统脚本执行（默认，按扩展名判断）
         * - command: 自定义命令模板（最灵活，推荐）
         */
        private String type = "script";

        /**
         * 命令模板，支持变量替换：
         * {skill_path} - 技能目录路径
         * {script_name} - 脚本名称/子命令
         * {output} - 输出文件路径
         * {param_file} - 参数文件路径
         * {args} - 自动生成的参数列表
         * 其他参数通过 {param_name} 引用
         */
        private String commandTemplate;

        /**
         * 工作目录模板，支持变量替换
         */
        private String workingDirTemplate;

        /**
         * 参数格式：kebab, equals, short, space
         */
        private String argsFormat = "kebab";

        /**
         * 参数前缀（默认 --）
         */
        private String argsPrefix = "--";

        /**
         * 执行超时时间（毫秒）
         */
        private long timeout = 0;

        /**
         * 默认参数
         */
        private Map<String, String> defaultArgs = new HashMap<>();

        /**
         * 环境变量
         */
        private Map<String, String> environment = new HashMap<>();

        /**
         * 是否需要先构建/编译
         */
        private boolean needsBuild = false;

        /**
         * 构建命令模板
         */
        private String buildTemplate;

        /**
         * 平台特定配置
         */
        private Map<String, PlatformConfig> platforms = new HashMap<>();

        public PlatformConfig getPlatformConfig() {
            String osName = System.getProperty("os.name", "").toLowerCase();
            String platform;
            if (osName.contains("win")) {
                platform = "windows";
            } else if (osName.contains("mac") || osName.contains("darwin")) {
                platform = "macos";
            } else {
                platform = "linux";
            }
            return platforms.get(platform);
        }

        public String getEffectiveCommandTemplate() {
            PlatformConfig pc = getPlatformConfig();
            if (pc != null && pc.getCommandTemplate() != null) {
                return pc.getCommandTemplate();
            }
            return commandTemplate;
        }

        public String getEffectiveWorkingDirTemplate() {
            PlatformConfig pc = getPlatformConfig();
            if (pc != null && pc.getWorkingDirTemplate() != null) {
                return pc.getWorkingDirTemplate();
            }
            return workingDirTemplate;
        }

        public Map<String, String> getEffectiveEnvironment() {
            Map<String, String> result = new HashMap<>(environment);
            PlatformConfig pc = getPlatformConfig();
            if (pc != null && pc.getEnvironment() != null) {
                result.putAll(pc.getEnvironment());
            }
            return result;
        }
    }

    /**
     * 平台特定配置
     */
    @Data
    public static class PlatformConfig {
        private String commandTemplate;
        private String workingDirTemplate;
        private Map<String, String> environment = new HashMap<>();
        private String buildTemplate;
    }
}
