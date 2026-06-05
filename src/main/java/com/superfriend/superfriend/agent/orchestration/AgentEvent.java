package com.superfriend.superfriend.agent.orchestration;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentEvent {

    public enum EventType {
        SKILL_DETECTED("skill_detected"),
        REACT_THOUGHT("react_thought"),
        REACT_ACTION("react_action"),
        TOOL_CALL_START("tool_call_start"),
        TOOL_CALL_RESULT("tool_call_result"),
        SKILL_EXECUTION_START("skill_execution_start"),
        SKILL_EXECUTION_PROGRESS("skill_execution_progress"),
        SKILL_EXECUTION_RESULT("skill_execution_result"),
        PROGRESSIVE_DISCLOSURE("progressive_disclosure"),
        CONTEXT_UPDATE("context_update"),
        STREAMING_CHUNK("streaming_chunk"),
        STEP_COMPLETED("step_completed"),
        ERROR("error"),
        COMPLETED("completed"),
        INTERRUPTED("interrupted");

        private final String value;

        EventType(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    private String eventId;
    private EventType type;
    private long timestamp;
    private int stepIndex;
    private String sessionId;
    private String content;
    private Map<String, Object> data;
    private Map<String, Object> metadata;

    public static AgentEvent thought(String sessionId, int stepIndex, String thought) {
        return AgentEvent.builder()
                .eventId(genId())
                .type(EventType.REACT_THOUGHT)
                .timestamp(System.currentTimeMillis())
                .stepIndex(stepIndex)
                .sessionId(sessionId)
                .content(thought)
                .build();
    }

    public static AgentEvent action(String sessionId, int stepIndex, String toolName, Map<String, Object> params) {
        return AgentEvent.builder()
                .eventId(genId())
                .type(EventType.REACT_ACTION)
                .timestamp(System.currentTimeMillis())
                .stepIndex(stepIndex)
                .sessionId(sessionId)
                .content(toolName)
                .data(params)
                .build();
    }

    public static AgentEvent toolCallStart(String sessionId, int stepIndex, String toolName, Map<String, Object> params) {
        return AgentEvent.builder()
                .eventId(genId())
                .type(EventType.TOOL_CALL_START)
                .timestamp(System.currentTimeMillis())
                .stepIndex(stepIndex)
                .sessionId(sessionId)
                .content(toolName)
                .data(params)
                .build();
    }

    public static AgentEvent toolCallResult(String sessionId, int stepIndex, String toolName, Object result) {
        Map<String, Object> data = new HashMap<>();
        data.put("result", result);
        return AgentEvent.builder()
                .eventId(genId())
                .type(EventType.TOOL_CALL_RESULT)
                .timestamp(System.currentTimeMillis())
                .stepIndex(stepIndex)
                .sessionId(sessionId)
                .content(toolName)
                .data(data)
                .build();
    }

    public static AgentEvent skillDetected(String sessionId, String skillName, double confidence) {
        Map<String, Object> data = new HashMap<>();
        data.put("confidence", confidence);
        return AgentEvent.builder()
                .eventId(genId())
                .type(EventType.SKILL_DETECTED)
                .timestamp(System.currentTimeMillis())
                .sessionId(sessionId)
                .content(skillName)
                .data(data)
                .build();
    }

    public static AgentEvent skillExecutionStart(String sessionId, int stepIndex, String skillName) {
        return AgentEvent.builder()
                .eventId(genId())
                .type(EventType.SKILL_EXECUTION_START)
                .timestamp(System.currentTimeMillis())
                .stepIndex(stepIndex)
                .sessionId(sessionId)
                .content(skillName)
                .build();
    }

    public static AgentEvent skillExecutionResult(String sessionId, int stepIndex, String skillName, boolean success, String message) {
        Map<String, Object> data = new HashMap<>();
        data.put("success", success);
        data.put("message", message);
        return AgentEvent.builder()
                .eventId(genId())
                .type(EventType.SKILL_EXECUTION_RESULT)
                .timestamp(System.currentTimeMillis())
                .stepIndex(stepIndex)
                .sessionId(sessionId)
                .content(skillName)
                .data(data)
                .build();
    }

    public static AgentEvent progressiveDisclosure(String sessionId, int level, String description, Map<String, Object> disclosed) {
        Map<String, Object> data = new HashMap<>();
        data.put("level", level);
        data.put("disclosed", disclosed);
        return AgentEvent.builder()
                .eventId(genId())
                .type(EventType.PROGRESSIVE_DISCLOSURE)
                .timestamp(System.currentTimeMillis())
                .sessionId(sessionId)
                .content(description)
                .data(data)
                .build();
    }

    public static AgentEvent streamingChunk(String sessionId, String chunk) {
        return AgentEvent.builder()
                .eventId(genId())
                .type(EventType.STREAMING_CHUNK)
                .timestamp(System.currentTimeMillis())
                .sessionId(sessionId)
                .content(chunk)
                .build();
    }

    public static AgentEvent stepCompleted(String sessionId, int stepIndex, String description) {
        return AgentEvent.builder()
                .eventId(genId())
                .type(EventType.STEP_COMPLETED)
                .timestamp(System.currentTimeMillis())
                .stepIndex(stepIndex)
                .sessionId(sessionId)
                .content(description)
                .build();
    }

    public static AgentEvent error(String sessionId, String errorType, String message) {
        Map<String, Object> data = new HashMap<>();
        data.put("errorType", errorType);
        return AgentEvent.builder()
                .eventId(genId())
                .type(EventType.ERROR)
                .timestamp(System.currentTimeMillis())
                .sessionId(sessionId)
                .content(message)
                .data(data)
                .build();
    }

    public static AgentEvent completed(String sessionId, String finalAnswer) {
        return AgentEvent.builder()
                .eventId(genId())
                .type(EventType.COMPLETED)
                .timestamp(System.currentTimeMillis())
                .sessionId(sessionId)
                .content(finalAnswer)
                .build();
    }

    public static AgentEvent interrupted(String sessionId, String reason) {
        return AgentEvent.builder()
                .eventId(genId())
                .type(EventType.INTERRUPTED)
                .timestamp(System.currentTimeMillis())
                .sessionId(sessionId)
                .content(reason)
                .build();
    }

    private static String genId() {
        return "evt_" + System.currentTimeMillis() + "_" + Integer.toHexString((int)(Math.random() * 0xFFFF));
    }
}
