package com.superfriend.superfriend.agent.skill;

import lombok.Data;
import java.util.Map;
import java.util.List;

@Data
public class SkillHook {
    private String id;
    private HookEvent event;
    private HookTrigger trigger;
    private HookAction action;
    private boolean enabled;
    private int priority;

    public enum HookEvent {
        SESSION_START("SessionStart"),
        PRE_TOOL_USE("PreToolUse"),
        POST_TOOL_USE("PostToolUse"),
        PRE_SKILL_EXECUTE("PreSkillExecute"),
        POST_SKILL_EXECUTE("PostSkillExecute"),
        PRE_COMMIT("PreCommit"),
        POST_COMMIT("PostCommit"),
        ERROR_OCCURRED("ErrorOccurred");

        private final String eventName;

        HookEvent(String eventName) {
            this.eventName = eventName;
        }

        public String getEventName() {
            return eventName;
        }
    }

    @Data
    public static class HookTrigger {
        private String toolName;
        private String skillName;
        private String pattern;
        private Map<String, Object> conditions;
    }

    @Data
    public static class HookAction {
        private ActionType type;
        private String script;
        private String command;
        private Map<String, Object> parameters;
        private boolean silent;
        private boolean stopOnFailure;

        public enum ActionType {
            EXECUTE_SCRIPT,
            RUN_COMMAND,
            CALL_SKILL,
            LOG_MESSAGE,
            SEND_NOTIFICATION
        }
    }
}
