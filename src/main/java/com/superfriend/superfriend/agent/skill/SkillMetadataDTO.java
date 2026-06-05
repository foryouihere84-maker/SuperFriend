package com.superfriend.superfriend.agent.skill;

import lombok.Data;
import java.util.List;

@Data
public class SkillMetadataDTO {
    private String name;
    private String description;
    private String category;
    private List<String> tags;
    private List<String> allowedTools;
    private String priority;
    private String scope;
    private String triggerHint;

    public SkillMetadataDTO() {}

    public SkillMetadataDTO(Skill skill) {
        this.name = skill.getName();
        this.description = skill.getDescription();
        this.category = skill.getCategory();
        this.tags = skill.getTags();
        this.allowedTools = skill.getAllowedTools();
        
        SkillMetadata metadata = skill.getMetadata();
        if (metadata != null) {
            this.priority = metadata.getPriority() != null ? metadata.getPriority().name() : "MEDIUM";
            this.scope = metadata.getScope() != null ? metadata.getScope().name() : "PROJECT";
        } else {
            this.priority = "MEDIUM";
            this.scope = "PROJECT";
        }
        
        this.triggerHint = generateTriggerHint(skill);
    }

    private String generateTriggerHint(Skill skill) {
        StringBuilder hint = new StringBuilder();
        
        if (getName() != null) {
            hint.append("当用户提到 \"").append(getName()).append("\"");
        }
        
        if (getTags() != null && !getTags().isEmpty()) {
            if (hint.length() > 0) {
                hint.append(" 或 ");
            }
            hint.append("涉及关键词: ").append(String.join(", ", getTags()));
        }
        
        if (getCategory() != null) {
            if (hint.length() > 0) {
                hint.append("，或需要");
            }
            hint.append(getCategory()).append("相关操作");
        }
        
        return hint.length() > 0 ? hint.toString() : "根据描述判断使用场景";
    }

    public String toSystemPromptFormat() {
        StringBuilder sb = new StringBuilder();
        sb.append("### ").append(name).append("\n");
        sb.append("- **Description**: ").append(description).append("\n");
        sb.append("- **Category**: ").append(category).append("\n");
        if (tags != null && !tags.isEmpty()) {
            sb.append("- **Tags**: ").append(String.join(", ", tags)).append("\n");
        }
        if (allowedTools != null && !allowedTools.isEmpty()) {
            sb.append("- **Allowed Tools**: ").append(String.join(", ", allowedTools)).append("\n");
        }
        sb.append("- **Trigger**: ").append(triggerHint).append("\n");
        sb.append("- **How to use**: Call `load_skill` tool to get detailed instructions\n");
        return sb.toString();
    }

    /**
     * 紧凑格式：只返回名称和简短描述，用于减少 token 消耗
     * 详细信息可通过 load_skill 按需获取
     */
    public String toCompactSystemPromptFormat() {
        String shortDesc = description;
        if (shortDesc != null && shortDesc.length() > 60) {
            shortDesc = shortDesc.substring(0, 57) + "...";
        }
        return String.format("- **%s**: %s", name, shortDesc != null ? shortDesc : "");
    }
}
