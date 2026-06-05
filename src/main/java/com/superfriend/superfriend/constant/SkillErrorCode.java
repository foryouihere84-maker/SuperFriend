package com.superfriend.superfriend.constant;

import lombok.Getter;

@Getter
public enum SkillErrorCode {
    SKILL_NOT_FOUND("SKILL_001", "技能不存在", "请检查技能名称或使用 /api/v16/skills 查看可用技能"),
    SKILL_NAME_DUPLICATE("SKILL_002", "技能名称已存在", "请使用其他名称或修改现有技能"),
    SKILL_NAME_EMPTY("SKILL_003", "技能名称不能为空", "请提供有效的技能名称"),
    PERMISSION_DENIED("SKILL_004", "权限不足", "您只能修改自己创建的技能"),
    SYSTEM_SKILL_IMMUTABLE("SKILL_005", "系统技能不可修改", "系统技能只能查看和执行，不能修改或删除"),
    EXECUTION_TIMEOUT("SKILL_006", "执行超时", "技能执行时间过长，请稍后重试或优化技能脚本"),
    EXECUTION_FAILED("SKILL_007", "执行失败", "技能执行过程中发生错误，请查看详细信息"),
    SCRIPT_NOT_FOUND("SKILL_008", "脚本不存在", "技能未配置执行脚本，请联系技能创建者"),
    INVALID_PARAMETERS("SKILL_009", "参数无效", "请检查参数格式和必填项"),
    CONTEXT_INVALID("SKILL_010", "上下文无效", "技能上下文验证失败，请提供必要的上下文信息"),
    USER_ID_REQUIRED("SKILL_011", "用户ID必填", "此操作需要提供用户ID，请先登录"),
    SKILL_REGISTRY_ERROR("SKILL_012", "技能注册失败", "技能注册时发生错误，请联系管理员"),
    DATABASE_ERROR("SKILL_013", "数据库操作失败", "数据库操作失败，请稍后重试"),
    FILE_SYSTEM_ERROR("SKILL_014", "文件系统错误", "技能文件读取失败，请检查文件路径和权限"),
    DEPENDENCY_ERROR("SKILL_015", "依赖缺失", "技能执行所需依赖未安装，请联系管理员");
    
    private final String code;
    private final String message;
    private final String suggestion;
    
    SkillErrorCode(String code, String message, String suggestion) {
        this.code = code;
        this.message = message;
        this.suggestion = suggestion;
    }
}
