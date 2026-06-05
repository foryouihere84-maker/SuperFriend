package com.superfriend.superfriend.agent.reasoning;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Data
public class ReasoningChain {

    private final String sessionId;
    private final List<ReasoningStep> steps;
    private final Map<String, Object> metadata;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private ReasoningStatus status;

<<<<<<< HEAD
    // 只保留连续失败的限制，移除重复工具调用的限制
    // 原因：模型可能需要多次调用同一工具（如分步生成），或成功后调用其他工具（如 send_file）
    // maxIterations 和 maxConsecutiveErrors 已经足够防止无限循环
    private static final int MAX_CONSECUTIVE_FAILURES = 5;
=======
    private static final int MAX_CONSECUTIVE_FAILURES = 3;
    private static final int MAX_SAME_TOOL_CALLS = 2;
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2

    private final Map<String, Integer> toolCallCounts = new LinkedHashMap<>();
    private final Map<String, Integer> toolFailureCounts = new LinkedHashMap<>();
    private int consecutiveFailures = 0;
    private TerminationReason terminationReason = null;

    public enum TerminationReason {
<<<<<<< HEAD
        CONSECUTIVE_FAILURES("连续失败次数过多");
=======
        CONSECUTIVE_FAILURES("连续失败次数过多"),
        REPEATED_TOOL_CALL("同一工具重复调用过多");
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2

        private final String description;
        TerminationReason(String description) { this.description = description; }
        public String getDescription() { return description; }
    }

    public ReasoningChain(String sessionId) {
        this.sessionId = sessionId;
        this.steps = new ArrayList<>();
        this.metadata = new HashMap<>();
        this.startTime = LocalDateTime.now();
        this.status = ReasoningStatus.IN_PROGRESS;
    }

    public boolean shouldTerminateEarly() {
        return terminationReason != null;
    }

    public TerminationReason getTerminationReason() {
        return terminationReason;
    }

    public String getTerminationMessage() {
        return terminationReason != null ? terminationReason.getDescription() : null;
    }

    public ReasoningStep addThought(String thought) {
        ReasoningStep step = new ReasoningStep();
        step.setStepNumber(steps.size() + 1);
        step.setType(StepType.THOUGHT);
        step.setContent(thought);
        step.setTimestamp(LocalDateTime.now());
        steps.add(step);
<<<<<<< HEAD
        log.debug("[ReasoningChain-{}] Thought #{}: {}", sessionId, step.getStepNumber(),
=======
        log.debug("[ReasoningChain-{}] Thought #{}: {}", sessionId, step.getStepNumber(), 
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
                truncate(thought, 100));
        return step;
    }

    public ReasoningStep addAction(String toolName, Map<String, Object> params) {
        String toolSignature = toolName + "_" + (params != null ? params.hashCode() : 0);
        int callCount = toolCallCounts.getOrDefault(toolSignature, 0) + 1;
        toolCallCounts.put(toolSignature, callCount);
<<<<<<< HEAD

        // 移除重复工具调用的早期终止逻辑
        // 只记录调用次数用于统计，不触发终止
        log.debug("[ReasoningChain-{}] 工具 {} 调用次数: {}", sessionId, toolName, callCount);

=======
        
        if (callCount > MAX_SAME_TOOL_CALLS) {
            terminationReason = TerminationReason.REPEATED_TOOL_CALL;
            log.warn("[ReasoningChain-{}] 工具 {} 重复调用 {} 次，建议终止", 
                    sessionId, toolName, callCount);
        }
        
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
        ReasoningStep step = new ReasoningStep();
        step.setStepNumber(steps.size() + 1);
        step.setType(StepType.ACTION);
        step.setToolName(toolName);
        step.setParameters(params);
        step.setTimestamp(LocalDateTime.now());
        steps.add(step);
<<<<<<< HEAD
        log.debug("[ReasoningChain-{}] Action #{}: {} with params {}", sessionId,
=======
        log.debug("[ReasoningChain-{}] Action #{}: {} with params {}", sessionId, 
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
                step.getStepNumber(), toolName, params != null ? params.keySet() : "none");
        return step;
    }

    public ReasoningStep addObservation(String observation, boolean success) {
        if (!success) {
            consecutiveFailures++;
            ReasoningStep lastAction = getLastAction().orElse(null);
            if (lastAction != null) {
                String toolName = lastAction.getToolName();
                toolFailureCounts.merge(toolName, 1, Integer::sum);
<<<<<<< HEAD

                // 更新工具签名的失败计数
                String toolSignature = toolName + "_" + (lastAction.getParameters() != null ? lastAction.getParameters().hashCode() : 0);
                toolFailureCounts.merge(toolSignature, 1, Integer::sum);
            }

=======
            }
            
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
            if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                terminationReason = TerminationReason.CONSECUTIVE_FAILURES;
                log.warn("[ReasoningChain-{}] 连续失败 {} 次，建议终止", sessionId, consecutiveFailures);
            }
        } else {
            consecutiveFailures = 0;
        }
<<<<<<< HEAD

=======
        
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
        ReasoningStep step = new ReasoningStep();
        step.setStepNumber(steps.size() + 1);
        step.setType(StepType.OBSERVATION);
        step.setContent(observation);
        step.setSuccess(success);
        step.setTimestamp(LocalDateTime.now());
        steps.add(step);
<<<<<<< HEAD
        log.debug("[ReasoningChain-{}] Observation #{}: {} [{}]", sessionId,
=======
        log.debug("[ReasoningChain-{}] Observation #{}: {} [{}]", sessionId, 
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
                step.getStepNumber(), truncate(observation, 50), success ? "SUCCESS" : "FAILED");
        return step;
    }

    public ReasoningStep addReflection(String reflection, double confidence) {
        ReasoningStep step = new ReasoningStep();
        step.setStepNumber(steps.size() + 1);
        step.setType(StepType.REFLECTION);
        step.setContent(reflection);
        step.setConfidence(confidence);
        step.setTimestamp(LocalDateTime.now());
        steps.add(step);
        log.debug("[ReasoningChain-{}] Reflection #{}: confidence={}", sessionId, 
                step.getStepNumber(), confidence);
        return step;
    }

    public ReasoningStep addDecision(String decision, String rationale) {
        ReasoningStep step = new ReasoningStep();
        step.setStepNumber(steps.size() + 1);
        step.setType(StepType.DECISION);
        step.setContent(decision);
        step.setRationale(rationale);
        step.setTimestamp(LocalDateTime.now());
        steps.add(step);
        log.debug("[ReasoningChain-{}] Decision #{}: {}", sessionId, step.getStepNumber(), decision);
        return step;
    }

    public void markCompleted() {
        this.endTime = LocalDateTime.now();
        this.status = ReasoningStatus.COMPLETED;
    }

    public void markFailed(String error) {
        this.endTime = LocalDateTime.now();
        this.status = ReasoningStatus.FAILED;
        metadata.put("error", error);
    }

    public List<ReasoningStep> getThoughts() {
        return steps.stream()
                .filter(s -> s.getType() == StepType.THOUGHT)
                .collect(Collectors.toList());
    }

    public List<ReasoningStep> getActions() {
        return steps.stream()
                .filter(s -> s.getType() == StepType.ACTION)
                .collect(Collectors.toList());
    }

    public List<ReasoningStep> getObservations() {
        return steps.stream()
                .filter(s -> s.getType() == StepType.OBSERVATION)
                .collect(Collectors.toList());
    }

    public List<ReasoningStep> getReflections() {
        return steps.stream()
                .filter(s -> s.getType() == StepType.REFLECTION)
                .collect(Collectors.toList());
    }

    public List<ReasoningStep> getFailedActions() {
        return steps.stream()
                .filter(s -> s.getType() == StepType.OBSERVATION && !s.isSuccess())
                .collect(Collectors.toList());
    }

    public int getActionCount() {
        return (int) steps.stream()
                .filter(s -> s.getType() == StepType.ACTION)
                .count();
    }

    public int getSuccessCount() {
        return (int) steps.stream()
                .filter(s -> s.getType() == StepType.OBSERVATION && s.isSuccess())
                .count();
    }

    public int getFailureCount() {
        return (int) steps.stream()
                .filter(s -> s.getType() == StepType.OBSERVATION && !s.isSuccess())
                .count();
    }

    public double getSuccessRate() {
        int total = getSuccessCount() + getFailureCount();
        if (total == 0) return 1.0;
        return (double) getSuccessCount() / total;
    }

    public Optional<ReasoningStep> getLastThought() {
        for (int i = steps.size() - 1; i >= 0; i--) {
            if (steps.get(i).getType() == StepType.THOUGHT) {
                return Optional.of(steps.get(i));
            }
        }
        return Optional.empty();
    }

    public Optional<ReasoningStep> getLastAction() {
        for (int i = steps.size() - 1; i >= 0; i--) {
            if (steps.get(i).getType() == StepType.ACTION) {
                return Optional.of(steps.get(i));
            }
        }
        return Optional.empty();
    }

    public Optional<ReasoningStep> getLastObservation() {
        for (int i = steps.size() - 1; i >= 0; i--) {
            if (steps.get(i).getType() == StepType.OBSERVATION) {
                return Optional.of(steps.get(i));
            }
        }
        return Optional.empty();
    }

    public String buildPromptContext(int maxSteps) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 推理历史\n\n");
        
        int startIdx = Math.max(0, steps.size() - maxSteps);
        List<ReasoningStep> recentSteps = steps.subList(startIdx, steps.size());
        
        for (ReasoningStep step : recentSteps) {
            switch (step.getType()) {
                case THOUGHT:
                    sb.append("Thought: ").append(step.getContent()).append("\n");
                    break;
                case ACTION:
                    sb.append("Action: ").append(step.getToolName());
                    if (step.getParameters() != null && !step.getParameters().isEmpty()) {
                        sb.append(" with ").append(step.getParameters());
                    }
                    sb.append("\n");
                    break;
                case OBSERVATION:
                    sb.append("Observation: ").append(truncate(step.getContent(), 500))
                      .append(" [").append(step.isSuccess() ? "SUCCESS" : "FAILED").append("]\n");
                    break;
                case REFLECTION:
                    sb.append("Reflection: ").append(step.getContent())
                      .append(" (confidence: ").append(String.format("%.2f", step.getConfidence())).append(")\n");
                    break;
                case DECISION:
                    sb.append("Decision: ").append(step.getContent());
                    if (step.getRationale() != null) {
                        sb.append(" - ").append(step.getRationale());
                    }
                    sb.append("\n");
                    break;
            }
        }
        
        return sb.toString();
    }

    public String buildSummary() {
        return String.format(
            "ReasoningChain[sessionId=%s, steps=%d, actions=%d, successRate=%.2f%%, status=%s]",
            sessionId, steps.size(), getActionCount(), getSuccessRate() * 100, status
        );
    }

    private String truncate(String s, int maxLength) {
        if (s == null) return "";
        if (s.length() <= maxLength) return s;
        return s.substring(0, maxLength) + "...";
    }

    public enum ReasoningStatus {
        IN_PROGRESS,
        COMPLETED,
        FAILED,
        INTERRUPTED
    }

    @Data
    public static class ReasoningStep {
        private int stepNumber;
        private StepType type;
        private String content;
        private String toolName;
        private Map<String, Object> parameters;
        private boolean success;
        private double confidence;
        private String rationale;
        private LocalDateTime timestamp;
        private Map<String, Object> metadata;

        public ReasoningStep() {
            this.metadata = new HashMap<>();
        }

        public void addMetadata(String key, Object value) {
            metadata.put(key, value);
        }
    }

    public enum StepType {
        THOUGHT,
        ACTION,
        OBSERVATION,
        REFLECTION,
        DECISION
    }
}
