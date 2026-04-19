package com.superfriend.superfriend.agent.skill;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.*;
import java.util.concurrent.*;

@Slf4j
@Service
public class SkillHookManager {
    
    private final Map<SkillHook.HookEvent, List<SkillHook>> hooksByEvent = new ConcurrentHashMap<>();
    private final ScriptExecutor scriptExecutor;
    private final SkillExecutor skillExecutor;

    @Autowired
    public SkillHookManager(ScriptExecutor scriptExecutor, @Lazy SkillExecutor skillExecutor) {
        this.scriptExecutor = scriptExecutor;
        this.skillExecutor = skillExecutor;
    }

    public void registerHook(SkillHook hook) {
        if (!hook.isEnabled()) {
            return;
        }

        SkillHook.HookEvent event = hook.getEvent();
        List<SkillHook> hooks = hooksByEvent.computeIfAbsent(event, k -> new CopyOnWriteArrayList<>());
        hooks.add(hook);
        
        hooks.sort((h1, h2) -> 
            Integer.compare(h2.getPriority(), h1.getPriority()));

        log.info("Registered hook: {} for event: {}", hook.getId(), event);
    }

    public void unregisterHook(String hookId) {
        hooksByEvent.values().forEach(hooks -> 
            hooks.removeIf(hook -> hookId.equals(hook.getId()))
        );
        log.info("Unregistered hook: {}", hookId);
    }

    public HookExecutionResult executeHooks(
        SkillHook.HookEvent event,
        HookExecutionContext context
    ) {
        List<SkillHook> hooks = hooksByEvent.get(event);
        if (hooks == null || hooks.isEmpty()) {
            return HookExecutionResult.success();
        }

        log.debug("Executing {} hooks for event: {}", hooks.size(), event);

        List<HookExecutionResult.HookResult> results = new ArrayList<>();
        boolean allSuccess = true;

        for (SkillHook hook : hooks) {
            if (!shouldTrigger(hook, context)) {
                continue;
            }

            HookExecutionResult.HookResult result = executeHook(hook, context);
            results.add(result);

            if (!result.isSuccess() && hook.getAction().isStopOnFailure()) {
                allSuccess = false;
                break;
            }

            if (!result.isSuccess()) {
                allSuccess = false;
            }
        }

        HookExecutionResult finalResult = new HookExecutionResult();
        finalResult.setSuccess(allSuccess);
        finalResult.setResults(results);
        return finalResult;
    }

    private boolean shouldTrigger(SkillHook hook, HookExecutionContext context) {
        SkillHook.HookTrigger trigger = hook.getTrigger();
        if (trigger == null) {
            return true;
        }

        if (trigger.getToolName() != null && 
            !trigger.getToolName().equals(context.getToolName())) {
            return false;
        }

        if (trigger.getSkillName() != null && 
            !trigger.getSkillName().equals(context.getSkillName())) {
            return false;
        }

        if (trigger.getPattern() != null && context.getData() != null) {
            String dataStr = context.getData().toString();
            if (!dataStr.matches(trigger.getPattern())) {
                return false;
            }
        }

        if (trigger.getConditions() != null) {
            for (Map.Entry<String, Object> entry : trigger.getConditions().entrySet()) {
                Object contextValue = context.getParameter(entry.getKey());
                if (!entry.getValue().equals(contextValue)) {
                    return false;
                }
            }
        }

        return true;
    }

    private HookExecutionResult.HookResult executeHook(
        SkillHook hook,
        HookExecutionContext context
    ) {
        long startTime = System.currentTimeMillis();
        HookExecutionResult.HookResult result = new HookExecutionResult.HookResult();
        result.setHookId(hook.getId());

        try {
            SkillHook.HookAction action = hook.getAction();

            switch (action.getType()) {
                case EXECUTE_SCRIPT:
                    ScriptExecutor.ScriptExecutionResult scriptResult = 
                        scriptExecutor.executeScript(
                            action.getScript(),
                            action.getParameters(),
                            context.getWorkingDirectory()
                        );
                    result.setSuccess(scriptResult.isSuccess());
                    result.setOutput(scriptResult.getOutput());
                    result.setError(scriptResult.getError());
                    break;

                case RUN_COMMAND:
                    ProcessBuilder pb = new ProcessBuilder(action.getCommand().split(" "));
                    Process process = pb.start();
                    int exitCode = process.waitFor();
                    result.setSuccess(exitCode == 0);
                    break;

                case CALL_SKILL:
                    String calledSkillName = (String) action.getParameters().get("skillName");
                    if (calledSkillName != null && !calledSkillName.isEmpty()) {
                        try {
                            SkillContext hookContext = new SkillContext();
                            hookContext.setSessionId(context.getSessionId());
                            hookContext.setUserId(context.getUserId());
                            hookContext.setUserRequest("Hook triggered: " + context.getSkillName());
                            hookContext.setParameters(action.getParameters());
                            
                            SkillResult skillResult = skillExecutor.execute(calledSkillName, hookContext);
                            result.setSuccess(skillResult.isSuccess());
                            if (skillResult.isSuccess()) {
                                result.setOutput(String.valueOf(skillResult.getData()));
                            } else {
                                result.setError(skillResult.getError());
                            }
                        } catch (Exception e) {
                            log.error("Failed to execute called skill: {}", calledSkillName, e);
                            result.setSuccess(false);
                            result.setError("Skill execution failed: " + e.getMessage());
                        }
                    } else {
                        log.warn("CALL_SKILL action missing skillName parameter");
                        result.setSuccess(false);
                        result.setError("Missing skillName parameter");
                    }
                    break;

                case LOG_MESSAGE:
                    String message = (String) action.getParameters().get("message");
                    if (message != null) {
                        log.info("Hook {}: {}", hook.getId(), message);
                    } else {
                        log.info("Hook {}: Executed (no message)", hook.getId());
                    }
                    result.setSuccess(true);
                    break;

                case SEND_NOTIFICATION:
                    String notificationType = (String) action.getParameters().get("type");
                    String notificationMessage = (String) action.getParameters().get("message");
                    
                    if (notificationType != null && notificationMessage != null) {
                        log.info("Hook {}: Sending {} notification: {}", 
                            hook.getId(), notificationType, notificationMessage);
                        
                        switch (notificationType.toLowerCase()) {
                            case "log":
                                log.info("[NOTIFICATION] {}", notificationMessage);
                                break;
                            case "debug":
                                log.debug("[NOTIFICATION] {}", notificationMessage);
                                break;
                            case "error":
                                log.error("[NOTIFICATION] {}", notificationMessage);
                                break;
                            default:
                                log.info("[NOTIFICATION] {}: {}", notificationType, notificationMessage);
                        }
                        result.setSuccess(true);
                    } else {
                        log.warn("SEND_NOTIFICATION action missing parameters");
                        result.setSuccess(false);
                        result.setError("Missing notification parameters");
                    }
                    break;
            }

            result.setExecutionTime(System.currentTimeMillis() - startTime);

            if (!action.isSilent()) {
                log.info("Hook {} executed: success={}, time={}ms", 
                    hook.getId(), result.isSuccess(), result.getExecutionTime());
            }

        } catch (Exception e) {
            log.error("Hook execution failed: {} - {}", hook.getId(), e.getMessage(), e);
            result.setSuccess(false);
            result.setError(e.getMessage());
            result.setExecutionTime(System.currentTimeMillis() - startTime);
        }

        return result;
    }

    public List<SkillHook> getHooksForEvent(SkillHook.HookEvent event) {
        return hooksByEvent.getOrDefault(event, Collections.emptyList());
    }

    public void clearAllHooks() {
        hooksByEvent.clear();
        log.info("Cleared all hooks");
    }

    @Data
    public static class HookExecutionContext {
        private String sessionId;
        private String userId;
        private String toolName;
        private String skillName;
        private Object data;
        private Map<String, Object> parameters;
        private File workingDirectory;

        public Object getParameter(String key) {
            return parameters != null ? parameters.get(key) : null;
        }
    }

    @Data
    public static class HookExecutionResult {
        private boolean success;
        private List<HookResult> results;

        public static HookExecutionResult success() {
            HookExecutionResult result = new HookExecutionResult();
            result.setSuccess(true);
            result.setResults(Collections.emptyList());
            return result;
        }

        @Data
        public static class HookResult {
            private String hookId;
            private boolean success;
            private String output;
            private String error;
            private long executionTime;
        }
    }
}
