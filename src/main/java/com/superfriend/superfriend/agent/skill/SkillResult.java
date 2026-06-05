package com.superfriend.superfriend.agent.skill;

import lombok.Data;
import java.util.Map;
import java.util.HashMap;

@Data
public class SkillResult {
    private boolean success;
    private Object data;
    private String error;
    private long executionTime;
    private Map<String, Object> metadata;
    private SkillStatus status;

    public enum SkillStatus {
        SUCCESS,
        FAILED,
        PARTIAL,
        INTERRUPTED,
        TIMEOUT
    }

    public SkillResult() {
        this.metadata = new HashMap<>();
    }

    public static SkillResult success(Object data) {
        SkillResult result = new SkillResult();
        result.setSuccess(true);
        result.setData(data);
        result.setStatus(SkillStatus.SUCCESS);
        return result;
    }

    public static SkillResult failure(String error) {
        SkillResult result = new SkillResult();
        result.setSuccess(false);
        result.setError(error);
        result.setStatus(SkillStatus.FAILED);
        return result;
    }

    public static SkillResult partial(Object data, String error) {
        SkillResult result = new SkillResult();
        result.setSuccess(false);
        result.setData(data);
        result.setError(error);
        result.setStatus(SkillStatus.PARTIAL);
        return result;
    }
}
