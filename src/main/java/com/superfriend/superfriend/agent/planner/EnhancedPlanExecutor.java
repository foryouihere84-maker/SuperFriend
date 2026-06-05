package com.superfriend.superfriend.agent.planner;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
public class EnhancedPlanExecutor {

    @Autowired
    @Lazy
    private TaskAnalyzer taskAnalyzer;

    @Autowired
    @Lazy
    private TaskDecomposer taskDecomposer;

    @Autowired
    @Lazy
    private DependencyManager dependencyManager;

    public PlanValidationResult validatePlan(ExecutionPlan plan) {
        List<PlanIssue> issues = new ArrayList<>();
        double confidence = 1.0;

        if (plan.getSteps() == null || plan.getSteps().isEmpty()) {
            issues.add(new PlanIssue(PlanIssueType.EMPTY_PLAN, "计划没有任何步骤", PlanIssueSeverity.CRITICAL));
            confidence = 0.0;
            return new PlanValidationResult(false, issues, confidence);
        }

        for (int i = 0; i < plan.getSteps().size(); i++) {
            PlanStep step = plan.getSteps().get(i);
            
            if (step.getDescription() == null || step.getDescription().trim().isEmpty()) {
                issues.add(new PlanIssue(PlanIssueType.MISSING_DESCRIPTION, 
                    "步骤 " + (i + 1) + " 缺少描述", PlanIssueSeverity.HIGH));
                confidence *= 0.8;
            }
            
            if (step.getToolName() != null && !step.getToolName().isEmpty()) {
                if (!isValidToolName(step.getToolName())) {
                    issues.add(new PlanIssue(PlanIssueType.INVALID_TOOL,
                        "步骤 " + (i + 1) + " 的工具名称格式无效: " + step.getToolName(), 
                        PlanIssueSeverity.MEDIUM));
                    confidence *= 0.9;
                }
            }
        }

        List<PlanStep> dependencyIssues = checkDependencyCycles(plan);
        if (!dependencyIssues.isEmpty()) {
            issues.add(new PlanIssue(PlanIssueType.CIRCULAR_DEPENDENCY,
                "存在循环依赖: " + dependencyIssues.stream()
                    .map(PlanStep::getStepId)
                    .collect(Collectors.joining(" -> ")),
                PlanIssueSeverity.CRITICAL));
            confidence = 0.0;
        }

        List<PlanStep> unreachableSteps = findUnreachableSteps(plan);
        if (!unreachableSteps.isEmpty()) {
            issues.add(new PlanIssue(PlanIssueType.UNREACHABLE_STEP,
                "存在不可达步骤: " + unreachableSteps.stream()
                    .map(PlanStep::getDescription)
                    .collect(Collectors.joining(", ")),
                PlanIssueSeverity.MEDIUM));
            confidence *= 0.85;
        }

        if (plan.getSteps().size() > 15) {
            issues.add(new PlanIssue(PlanIssueType.TOO_MANY_STEPS,
                "计划步骤过多 (" + plan.getSteps().size() + ")，建议分解为子计划",
                PlanIssueSeverity.LOW));
            confidence *= 0.95;
        }

        boolean valid = issues.stream()
            .noneMatch(issue -> issue.getSeverity() == PlanIssueSeverity.CRITICAL);
        
        log.info("[PlanValidator] 计划验证完成: valid={}, confidence={}, issues={}", 
                valid, confidence, issues.size());
        
        return new PlanValidationResult(valid, issues, confidence);
    }

    public ExecutionPlan adjustPlan(ExecutionPlan originalPlan, AdjustmentContext context) {
        log.info("[PlanAdjuster] 开始调整计划: reason={}, step={}", 
                context.getReason(), context.getFailedStepIndex());
        
        ExecutionPlan adjustedPlan = deepCopy(originalPlan);
        
        switch (context.getReason()) {
            case STEP_FAILED:
                adjustedPlan = handleStepFailure(adjustedPlan, context);
                break;
            case NEW_INFORMATION:
                adjustedPlan = incorporateNewInformation(adjustedPlan, context);
                break;
            case RESOURCE_UNAVAILABLE:
                adjustedPlan = handleResourceUnavailable(adjustedPlan, context);
                break;
            case USER_FEEDBACK:
                adjustedPlan = incorporateUserFeedback(adjustedPlan, context);
                break;
            case DEPENDENCY_CHANGED:
                adjustedPlan = updateDependencies(adjustedPlan, context);
                break;
        }
        
        adjustedPlan.setAdjustmentCount(adjustedPlan.getAdjustmentCount() + 1);
        adjustedPlan.setLastAdjustedAt(LocalDateTime.now());
        
        log.info("[PlanAdjuster] 计划调整完成: newSteps={}, adjustmentCount={}", 
                adjustedPlan.getSteps().size(), adjustedPlan.getAdjustmentCount());
        
        return adjustedPlan;
    }

    public ExecutionPlan replan(ExecutionPlan originalPlan, ReplanContext context) {
        log.info("[PlanReplanner] 开始重新规划: reason={}, remainingSteps={}", 
                context.getReason(), 
                originalPlan.getSteps().size() - context.getCompletedStepCount());
        
        ExecutionPlan newPlan = new ExecutionPlan();
        newPlan.setPlanId(originalPlan.getPlanId());
        newPlan.setSessionId(originalPlan.getSessionId());
        newPlan.setOriginalRequest(originalPlan.getOriginalRequest());
        newPlan.setCreatedAt(LocalDateTime.now());
        newPlan.setReplannedFrom(originalPlan.getVersion());
        newPlan.setVersion(originalPlan.getVersion() + 1);
        
        List<PlanStep> completedSteps = originalPlan.getSteps().stream()
            .filter(step -> step.getStatus() == StepStatus.COMPLETED)
            .collect(Collectors.toList());
        
        List<PlanStep> remainingSteps = new ArrayList<>();
        for (int i = context.getCompletedStepCount(); i < originalPlan.getSteps().size(); i++) {
            PlanStep originalStep = originalPlan.getSteps().get(i);
            
            PlanStep newStep = new PlanStep();
            newStep.setStepId(UUID.randomUUID().toString().substring(0, 8));
            newStep.setStepNumber(remainingSteps.size() + 1);
            newStep.setDescription(originalStep.getDescription());
            newStep.setToolName(originalStep.getToolName());
            newStep.setParameters(originalStep.getParameters());
            newStep.setStatus(StepStatus.PENDING);
            newStep.setDependencies(originalStep.getDependencies());
            
            remainingSteps.add(newStep);
        }
        
        if (context.getReason() == ReplanReason.PARTIAL_FAILURE) {
            remainingSteps = addRecoverySteps(remainingSteps, context);
        }
        
        List<PlanStep> allSteps = new ArrayList<>();
        allSteps.addAll(completedSteps);
        allSteps.addAll(remainingSteps);
        
        for (int i = 0; i < allSteps.size(); i++) {
            allSteps.get(i).setStepNumber(i + 1);
        }
        
        newPlan.setSteps(allSteps);
        newPlan.setTotalSteps(allSteps.size());
        newPlan.setCompletedSteps(completedSteps.size());
        
        log.info("[PlanReplanner] 重新规划完成: totalSteps={}, completedSteps={}", 
                newPlan.getTotalSteps(), newPlan.getCompletedSteps());
        
        return newPlan;
    }

    public PlanProgress evaluateProgress(ExecutionPlan plan) {
        PlanProgress progress = new PlanProgress();
        progress.setPlanId(plan.getPlanId());
        progress.setTotalSteps(plan.getTotalSteps());
        
        int completed = 0;
        int failed = 0;
        int skipped = 0;
        int pending = 0;
        
        for (PlanStep step : plan.getSteps()) {
            switch (step.getStatus()) {
                case COMPLETED: completed++; break;
                case FAILED: failed++; break;
                case SKIPPED: skipped++; break;
                case PENDING: pending++; break;
            }
        }
        
        progress.setCompletedSteps(completed);
        progress.setFailedSteps(failed);
        progress.setSkippedSteps(skipped);
        progress.setPendingSteps(pending);
        
        double completionRate = (double) completed / plan.getTotalSteps();
        progress.setCompletionRate(completionRate);
        
        double successRate = completed + failed > 0 
            ? (double) completed / (completed + failed) 
            : 1.0;
        progress.setSuccessRate(successRate);
        
        if (completionRate < 0.3) {
            progress.setPhase(ExecutionPhase.EARLY);
        } else if (completionRate < 0.7) {
            progress.setPhase(ExecutionPhase.MIDDLE);
        } else {
            progress.setPhase(ExecutionPhase.LATE);
        }
        
        progress.setEstimatedRemainingSteps(pending);
        progress.setHealthScore(calculateHealthScore(progress));
        
        return progress;
    }

    public List<PlanStep> getNextExecutableSteps(ExecutionPlan plan) {
        return plan.getSteps().stream()
            .filter(step -> step.getStatus() == StepStatus.PENDING)
            .filter(this::areDependenciesMet)
            .collect(Collectors.toList());
    }

    public boolean canContinueExecution(ExecutionPlan plan) {
        PlanProgress progress = evaluateProgress(plan);
        
        if (progress.getHealthScore() < 0.3) {
            log.warn("[PlanExecutor] 计划健康分数过低: {}", progress.getHealthScore());
            return false;
        }
        
        if (progress.getFailedSteps() > progress.getTotalSteps() * 0.5) {
            log.warn("[PlanExecutor] 失败步骤过多: {}/{}", 
                    progress.getFailedSteps(), progress.getTotalSteps());
            return false;
        }
        
        List<PlanStep> nextSteps = getNextExecutableSteps(plan);
        if (nextSteps.isEmpty() && progress.getPendingSteps() > 0) {
            log.warn("[PlanExecutor] 存在待执行步骤但无法继续（可能存在依赖问题）");
            return false;
        }
        
        return true;
    }

    private boolean isValidToolName(String toolName) {
        if (toolName == null || toolName.trim().isEmpty()) {
            return false;
        }
        return toolName.matches("^[a-zA-Z0-9_-]+(__[a-zA-Z0-9_-]+)?$");
    }

    private List<PlanStep> checkDependencyCycles(ExecutionPlan plan) {
        Set<String> visited = new HashSet<>();
        Set<String> recursionStack = new HashSet<>();
        List<PlanStep> cycleSteps = new ArrayList<>();
        
        for (PlanStep step : plan.getSteps()) {
            if (detectCycle(step, plan, visited, recursionStack, cycleSteps)) {
                return cycleSteps;
            }
        }
        
        return Collections.emptyList();
    }

    private boolean detectCycle(PlanStep step, ExecutionPlan plan, 
                                 Set<String> visited, Set<String> recursionStack,
                                 List<PlanStep> cycleSteps) {
        String stepId = step.getStepId();
        
        if (recursionStack.contains(stepId)) {
            cycleSteps.add(step);
            return true;
        }
        
        if (visited.contains(stepId)) {
            return false;
        }
        
        visited.add(stepId);
        recursionStack.add(stepId);
        
        if (step.getDependencies() != null) {
            for (String depId : step.getDependencies()) {
                PlanStep depStep = plan.getSteps().stream()
                    .filter(s -> s.getStepId().equals(depId))
                    .findFirst()
                    .orElse(null);
                
                if (depStep != null && detectCycle(depStep, plan, visited, recursionStack, cycleSteps)) {
                    cycleSteps.add(step);
                    return true;
                }
            }
        }
        
        recursionStack.remove(stepId);
        return false;
    }

    private List<PlanStep> findUnreachableSteps(ExecutionPlan plan) {
        Set<String> reachable = new HashSet<>();
        
        for (PlanStep step : plan.getSteps()) {
            if (step.getDependencies() == null || step.getDependencies().isEmpty()) {
                reachable.add(step.getStepId());
            }
        }
        
        boolean changed = true;
        while (changed) {
            changed = false;
            for (PlanStep step : plan.getSteps()) {
                if (!reachable.contains(step.getStepId()) && step.getDependencies() != null) {
                    if (reachable.containsAll(step.getDependencies())) {
                        reachable.add(step.getStepId());
                        changed = true;
                    }
                }
            }
        }
        
        return plan.getSteps().stream()
            .filter(step -> !reachable.contains(step.getStepId()))
            .collect(Collectors.toList());
    }

    private ExecutionPlan deepCopy(ExecutionPlan original) {
        ExecutionPlan copy = new ExecutionPlan();
        copy.setPlanId(original.getPlanId());
        copy.setSessionId(original.getSessionId());
        copy.setOriginalRequest(original.getOriginalRequest());
        copy.setSummary(original.getSummary());
        copy.setVersion(original.getVersion());
        copy.setAdjustmentCount(original.getAdjustmentCount());
        copy.setCreatedAt(original.getCreatedAt());
        copy.setLastAdjustedAt(original.getLastAdjustedAt());
        
        List<PlanStep> copiedSteps = new ArrayList<>();
        for (PlanStep step : original.getSteps()) {
            PlanStep stepCopy = new PlanStep();
            stepCopy.setStepId(step.getStepId());
            stepCopy.setStepNumber(step.getStepNumber());
            stepCopy.setDescription(step.getDescription());
            stepCopy.setToolName(step.getToolName());
            stepCopy.setParameters(new HashMap<>(step.getParameters()));
            stepCopy.setStatus(step.getStatus());
            stepCopy.setDependencies(step.getDependencies() != null 
                ? new ArrayList<>(step.getDependencies()) : null);
            stepCopy.setResult(step.getResult());
            stepCopy.setError(step.getError());
            copiedSteps.add(stepCopy);
        }
        copy.setSteps(copiedSteps);
        copy.setTotalSteps(original.getTotalSteps());
        copy.setCompletedSteps(original.getCompletedSteps());
        
        return copy;
    }

    private ExecutionPlan handleStepFailure(ExecutionPlan plan, AdjustmentContext context) {
        int failedIndex = context.getFailedStepIndex();
        PlanStep failedStep = plan.getSteps().get(failedIndex);
        
        failedStep.setStatus(StepStatus.FAILED);
        failedStep.setError(context.getErrorMessage());
        
        if (context.getAlternativeTool() != null) {
            PlanStep retryStep = new PlanStep();
            retryStep.setStepId(UUID.randomUUID().toString().substring(0, 8));
            retryStep.setStepNumber(plan.getSteps().size() + 1);
            retryStep.setDescription("重试: " + failedStep.getDescription() + " (使用备选工具)");
            retryStep.setToolName(context.getAlternativeTool());
            retryStep.setParameters(failedStep.getParameters());
            retryStep.setStatus(StepStatus.PENDING);
            retryStep.setDependencies(Collections.singletonList(failedStep.getStepId()));
            
            plan.getSteps().add(retryStep);
            plan.setTotalSteps(plan.getTotalSteps() + 1);
        }
        
        return plan;
    }

    private ExecutionPlan incorporateNewInformation(ExecutionPlan plan, AdjustmentContext context) {
        PlanStep newStep = new PlanStep();
        newStep.setStepId(UUID.randomUUID().toString().substring(0, 8));
        newStep.setStepNumber(plan.getSteps().size() + 1);
        newStep.setDescription("处理新信息: " + context.getNewInformation());
        newStep.setToolName(context.getSuggestedTool());
        newStep.setParameters(context.getSuggestedParameters());
        newStep.setStatus(StepStatus.PENDING);
        
        plan.getSteps().add(newStep);
        plan.setTotalSteps(plan.getTotalSteps() + 1);
        
        return plan;
    }

    private ExecutionPlan handleResourceUnavailable(ExecutionPlan plan, AdjustmentContext context) {
        for (PlanStep step : plan.getSteps()) {
            if (step.getToolName() != null && 
                step.getToolName().equals(context.getUnavailableResource())) {
                
                if (context.getAlternativeResource() != null) {
                    step.setToolName(context.getAlternativeResource());
                    log.info("[PlanAdjuster] 替换不可用资源: {} -> {}", 
                            context.getUnavailableResource(), context.getAlternativeResource());
                } else {
                    step.setStatus(StepStatus.SKIPPED);
                    step.setError("资源不可用且无备选: " + context.getUnavailableResource());
                    log.warn("[PlanAdjuster] 跳过不可用资源步骤: {}", step.getDescription());
                }
            }
        }
        
        return plan;
    }

    private ExecutionPlan incorporateUserFeedback(ExecutionPlan plan, AdjustmentContext context) {
        PlanStep feedbackStep = new PlanStep();
        feedbackStep.setStepId(UUID.randomUUID().toString().substring(0, 8));
        feedbackStep.setStepNumber(plan.getSteps().size() + 1);
        feedbackStep.setDescription("响应用户反馈: " + context.getUserFeedback());
        feedbackStep.setStatus(StepStatus.PENDING);
        
        plan.getSteps().add(feedbackStep);
        plan.setTotalSteps(plan.getTotalSteps() + 1);
        
        return plan;
    }

    private ExecutionPlan updateDependencies(ExecutionPlan plan, AdjustmentContext context) {
        for (PlanStep step : plan.getSteps()) {
            if (step.getDependencies() != null) {
                step.getDependencies().removeAll(context.getRemovedDependencies());
                step.getDependencies().addAll(context.getAddedDependencies());
            }
        }
        
        return plan;
    }

    private List<PlanStep> addRecoverySteps(List<PlanStep> steps, ReplanContext context) {
        PlanStep recoveryStep = new PlanStep();
        recoveryStep.setStepId(UUID.randomUUID().toString().substring(0, 8));
        recoveryStep.setStepNumber(steps.size() + 1);
        recoveryStep.setDescription("恢复步骤: 处理之前的失败");
        recoveryStep.setStatus(StepStatus.PENDING);
        steps.add(recoveryStep);
        
        return steps;
    }

    private boolean areDependenciesMet(PlanStep step) {
        if (step.getDependencies() == null || step.getDependencies().isEmpty()) {
            return true;
        }
        
        return step.getDependencies().stream()
            .allMatch(depId -> {
                PlanStep depStep = step.getParentPlan() != null 
                    ? step.getParentPlan().getSteps().stream()
                        .filter(s -> s.getStepId().equals(depId))
                        .findFirst()
                        .orElse(null)
                    : null;
                return depStep != null && depStep.getStatus() == StepStatus.COMPLETED;
            });
    }

    private double calculateHealthScore(PlanProgress progress) {
        double score = 1.0;
        
        score *= progress.getSuccessRate();
        
        if (progress.getFailedSteps() > 0) {
            score *= Math.max(0.3, 1.0 - (progress.getFailedSteps() * 0.1));
        }
        
        if (progress.getSkippedSteps() > 0) {
            score *= Math.max(0.5, 1.0 - (progress.getSkippedSteps() * 0.05));
        }
        
        return Math.max(0, Math.min(1, score));
    }

    @Data
    public static class ExecutionPlan {
        private String planId;
        private String sessionId;
        private String originalRequest;
        private String summary;
        private int version = 1;
        private int adjustmentCount = 0;
        private LocalDateTime createdAt;
        private LocalDateTime lastAdjustedAt;
        private List<PlanStep> steps;
        private int totalSteps;
        private int completedSteps;
        private int replannedFrom;
    }

    @Data
    public static class PlanStep {
        private String stepId;
        private int stepNumber;
        private String description;
        private String toolName;
        private Map<String, Object> parameters = new HashMap<>();
        private StepStatus status = StepStatus.PENDING;
        private List<String> dependencies;
        private Object result;
        private String error;
        private ExecutionPlan parentPlan;
    }

    public enum StepStatus {
        PENDING("待执行"),
        WAITING_FOR_RESOURCE("等待资源"),
        WAITING_FOR_USER_INPUT("等待用户输入"),
        RUNNING("执行中"),
        RETRYING("重试中"),
        COMPLETED("已完成"),
        FAILED("失败"),
        SKIPPED("已跳过"),
        CANCELLED("已取消");

        private final String description;

        StepStatus(String description) {
            this.description = description;
        }

        public String getDescription() { return description; }

        /**
         * 检查是否可以转换到新状态
         */
        public boolean canTransitionTo(StepStatus newStatus) {
            switch (this) {
                case PENDING:
                    return newStatus == WAITING_FOR_RESOURCE ||
                           newStatus == WAITING_FOR_USER_INPUT ||
                           newStatus == RUNNING ||
                           newStatus == SKIPPED ||
                           newStatus == CANCELLED;
                case WAITING_FOR_RESOURCE:
                    return newStatus == RUNNING ||
                           newStatus == FAILED ||
                           newStatus == SKIPPED ||
                           newStatus == CANCELLED;
                case WAITING_FOR_USER_INPUT:
                    return newStatus == RUNNING ||
                           newStatus == FAILED ||
                           newStatus == CANCELLED;
                case RUNNING:
                    return newStatus == COMPLETED ||
                           newStatus == FAILED ||
                           newStatus == RETRYING ||
                           newStatus == WAITING_FOR_RESOURCE ||
                           newStatus == CANCELLED;
                case RETRYING:
                    return newStatus == RUNNING ||
                           newStatus == FAILED ||
                           newStatus == CANCELLED;
                case FAILED:
                    return newStatus == RETRYING ||
                           newStatus == SKIPPED ||
                           newStatus == CANCELLED;
                case COMPLETED:
                case SKIPPED:
                case CANCELLED:
                    return false;  // 终态，不可转换
                default:
                    return false;
            }
        }

        /**
         * 是否是终态
         */
        public boolean isTerminal() {
            return this == COMPLETED || this == SKIPPED || this == CANCELLED;
        }

        /**
         * 是否是活跃状态（正在执行）
         */
        public boolean isActive() {
            return this == RUNNING || this == RETRYING ||
                   this == WAITING_FOR_RESOURCE || this == WAITING_FOR_USER_INPUT;
        }
    }

    @Data
    public static class PlanValidationResult {
        private final boolean valid;
        private final List<PlanIssue> issues;
        private final double confidence;
    }

    @Data
    public static class PlanIssue {
        private final PlanIssueType type;
        private final String message;
        private final PlanIssueSeverity severity;
    }

    public enum PlanIssueType {
        EMPTY_PLAN,
        MISSING_DESCRIPTION,
        INVALID_TOOL,
        CIRCULAR_DEPENDENCY,
        UNREACHABLE_STEP,
        TOO_MANY_STEPS,
        MISSING_DEPENDENCY
    }

    public enum PlanIssueSeverity {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }

    @Data
    public static class AdjustmentContext {
        private AdjustmentReason reason;
        private int failedStepIndex;
        private String errorMessage;
        private String alternativeTool;
        private String newInformation;
        private String suggestedTool;
        private Map<String, Object> suggestedParameters;
        private String unavailableResource;
        private String alternativeResource;
        private String userFeedback;
        private List<String> removedDependencies = new ArrayList<>();
        private List<String> addedDependencies = new ArrayList<>();
    }

    public enum AdjustmentReason {
        STEP_FAILED,
        NEW_INFORMATION,
        RESOURCE_UNAVAILABLE,
        USER_FEEDBACK,
        DEPENDENCY_CHANGED
    }

    @Data
    public static class ReplanContext {
        private ReplanReason reason;
        private int completedStepCount;
        private String errorMessage;
    }

    public enum ReplanReason {
        COMPLETE_FAILURE,
        PARTIAL_FAILURE,
        USER_REQUEST,
        EXTERNAL_CHANGE
    }

    @Data
    public static class PlanProgress {
        private String planId;
        private int totalSteps;
        private int completedSteps;
        private int failedSteps;
        private int skippedSteps;
        private int pendingSteps;
        private double completionRate;
        private double successRate;
        private ExecutionPhase phase;
        private int estimatedRemainingSteps;
        private double healthScore;
    }

    public enum ExecutionPhase {
        EARLY,
        MIDDLE,
        LATE
    }
}
