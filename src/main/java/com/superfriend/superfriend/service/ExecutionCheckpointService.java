package com.superfriend.superfriend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.superfriend.superfriend.dto.TaskPlanDTO;
import com.superfriend.superfriend.dto.TaskStepDTO;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
public class ExecutionCheckpointService {

    @Autowired
    private ObjectMapper objectMapper;

    private static final String CHECKPOINT_DIR = "checkpoints";
    private static final int MAX_CHECKPOINTS_PER_SESSION = 10;
    private static final long CHECKPOINT_EXPIRY_HOURS = 24;

    private final Map<String, List<Checkpoint>> sessionCheckpoints = new ConcurrentHashMap<>();

    @Data
    public static class Checkpoint {
        private String checkpointId;
        private String sessionId;
        private String planId;
        private LocalDateTime createdAt;
        private int currentStep;
        private int totalSteps;
        private String phase;
        private CheckpointType type;
        private ExecutionState state;
        private String description;
        private boolean recoverable;
    }

    @Data
    public static class ExecutionState {
        private String userMessage;
        private String model;
        private Long userId;
        private List<Map<String, Object>> messages;
        private List<ToolExecutionState> toolExecutions;
        private Map<String, Object> variables;
        private TaskPlanDTO plan;
        private int currentStepIndex;
        private String lastResponse;
        private Map<String, Object> metadata;
    }

    @Data
    public static class ToolExecutionState {
        private String toolName;
        private String serverName;
        private Map<String, Object> arguments;
        private boolean success;
        private String result;
        private long executionTimeMs;
        private LocalDateTime executedAt;
    }

    public enum CheckpointType {
        STEP_START,
        STEP_COMPLETE,
        STEP_FAILED,
        PLAN_START,
        PLAN_COMPLETE,
        MANUAL,
        ERROR_RECOVERY
    }

    public Checkpoint createCheckpoint(
            String sessionId,
            String planId,
            int currentStep,
            int totalSteps,
            String phase,
            CheckpointType type,
            ExecutionState state,
            String description) {

        String checkpointId = generateCheckpointId();

        Checkpoint checkpoint = new Checkpoint();
        checkpoint.setCheckpointId(checkpointId);
        checkpoint.setSessionId(sessionId);
        checkpoint.setPlanId(planId);
        checkpoint.setCreatedAt(LocalDateTime.now());
        checkpoint.setCurrentStep(currentStep);
        checkpoint.setTotalSteps(totalSteps);
        checkpoint.setPhase(phase);
        checkpoint.setType(type);
        checkpoint.setState(state);
        checkpoint.setDescription(description);
        checkpoint.setRecoverable(true);

        sessionCheckpoints.computeIfAbsent(sessionId, k -> new ArrayList<>()).add(checkpoint);

        if (sessionCheckpoints.get(sessionId).size() > MAX_CHECKPOINTS_PER_SESSION) {
            Checkpoint removed = sessionCheckpoints.get(sessionId).remove(0);
            deleteCheckpointFile(removed);
            log.debug("移除旧检查点: {}", removed.getCheckpointId());
        }

        saveCheckpointToFile(checkpoint);

        log.info("创建检查点: sessionId={}, checkpointId={}, step={}/{}, type={}",
                sessionId, checkpointId, currentStep, totalSteps, type);

        return checkpoint;
    }

    public Checkpoint createStepCheckpoint(
            String sessionId,
            String planId,
            TaskStepDTO step,
            int stepIndex,
            int totalSteps,
            List<Map<String, Object>> messages,
            String model,
            Long userId,
            String userMessage) {

        ExecutionState state = new ExecutionState();
        state.setUserMessage(userMessage);
        state.setModel(model);
        state.setUserId(userId);
        state.setMessages(new ArrayList<>(messages));
        state.setCurrentStepIndex(stepIndex);
        state.setVariables(new HashMap<>());
        state.setMetadata(new HashMap<>());

        return createCheckpoint(
                sessionId,
                planId,
                stepIndex + 1,
                totalSteps,
                "step_" + (stepIndex + 1),
                CheckpointType.STEP_START,
                state,
                "步骤 " + (stepIndex + 1) + ": " + step.getDescription()
        );
    }

    public Checkpoint createErrorCheckpoint(
            String sessionId,
            String planId,
            int currentStep,
            int totalSteps,
            String errorMessage,
            List<Map<String, Object>> messages,
            String model,
            Long userId,
            String userMessage) {

        ExecutionState state = new ExecutionState();
        state.setUserMessage(userMessage);
        state.setModel(model);
        state.setUserId(userId);
        state.setMessages(new ArrayList<>(messages));
        state.setCurrentStepIndex(currentStep);
        state.setVariables(new HashMap<>());
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("error", errorMessage);
        state.setMetadata(metadata);

        return createCheckpoint(
                sessionId,
                planId,
                currentStep,
                totalSteps,
                "error",
                CheckpointType.ERROR_RECOVERY,
                state,
                "错误恢复点: " + (errorMessage.length() > 100 ? errorMessage.substring(0, 100) : errorMessage)
        );
    }

    public Optional<Checkpoint> getLatestCheckpoint(String sessionId) {
        List<Checkpoint> checkpoints = sessionCheckpoints.get(sessionId);
        if (checkpoints == null || checkpoints.isEmpty()) {
            checkpoints = loadCheckpointsFromDisk(sessionId);
            if (checkpoints != null && !checkpoints.isEmpty()) {
                sessionCheckpoints.put(sessionId, checkpoints);
            }
        }

        if (checkpoints == null || checkpoints.isEmpty()) {
            return Optional.empty();
        }

        return checkpoints.stream()
                .filter(Checkpoint::isRecoverable)
                .max(Comparator.comparing(Checkpoint::getCreatedAt));
    }

    public Optional<Checkpoint> getCheckpoint(String sessionId, String checkpointId) {
        List<Checkpoint> checkpoints = sessionCheckpoints.get(sessionId);
        if (checkpoints == null) {
            return Optional.empty();
        }

        return checkpoints.stream()
                .filter(c -> c.getCheckpointId().equals(checkpointId))
                .findFirst();
    }

    public List<Checkpoint> getSessionCheckpoints(String sessionId) {
        List<Checkpoint> checkpoints = sessionCheckpoints.get(sessionId);
        if (checkpoints == null) {
            checkpoints = loadCheckpointsFromDisk(sessionId);
            if (checkpoints != null) {
                sessionCheckpoints.put(sessionId, checkpoints);
            }
        }
        return checkpoints != null ? new ArrayList<>(checkpoints) : new ArrayList<>();
    }

    public boolean canRecover(String sessionId) {
        Optional<Checkpoint> latest = getLatestCheckpoint(sessionId);
        return latest.isPresent() && latest.get().isRecoverable();
    }

    public ExecutionState recoverState(String sessionId, String checkpointId) {
        Optional<Checkpoint> checkpointOpt = getCheckpoint(sessionId, checkpointId);
        if (!checkpointOpt.isPresent()) {
            log.warn("检查点不存在: sessionId={}, checkpointId={}", sessionId, checkpointId);
            return null;
        }

        Checkpoint checkpoint = checkpointOpt.get();
        if (!checkpoint.isRecoverable()) {
            log.warn("检查点不可恢复: {}", checkpointId);
            return null;
        }

        log.info("从检查点恢复: sessionId={}, checkpointId={}, step={}/{}",
                sessionId, checkpointId, checkpoint.getCurrentStep(), checkpoint.getTotalSteps());

        return checkpoint.getState();
    }

    public ExecutionState recoverFromLatest(String sessionId) {
        Optional<Checkpoint> latest = getLatestCheckpoint(sessionId);
        return latest.map(c -> recoverState(sessionId, c.getCheckpointId())).orElse(null);
    }

    public void markCheckpointRecovered(String sessionId, String checkpointId) {
        Optional<Checkpoint> checkpointOpt = getCheckpoint(sessionId, checkpointId);
        checkpointOpt.ifPresent(checkpoint -> {
            checkpoint.setRecoverable(false);
            updateCheckpointFile(checkpoint);
            log.info("标记检查点为已恢复: {}", checkpointId);
        });
    }

    public void clearSessionCheckpoints(String sessionId) {
        List<Checkpoint> checkpoints = sessionCheckpoints.remove(sessionId);
        if (checkpoints != null) {
            for (Checkpoint checkpoint : checkpoints) {
                deleteCheckpointFile(checkpoint);
            }
        }
        log.info("清除会话检查点: sessionId={}", sessionId);
    }

    public void cleanupExpiredCheckpoints() {
        LocalDateTime expiryTime = LocalDateTime.now().minusHours(CHECKPOINT_EXPIRY_HOURS);
        AtomicInteger removed = new AtomicInteger();

        for (List<Checkpoint> checkpoints : sessionCheckpoints.values()) {
            checkpoints.removeIf(checkpoint -> {
                if (checkpoint.getCreatedAt().isBefore(expiryTime)) {
                    deleteCheckpointFile(checkpoint);
                    removed.getAndIncrement();
                    return true;
                }
                return false;
            });
        }

        if (removed.get() > 0) {
            log.info("清理过期检查点: {} 个", removed);
        }
    }

    private String generateCheckpointId() {
        return "ckpt_" + System.currentTimeMillis() + "_" + 
                UUID.randomUUID().toString().substring(0, 8);
    }

    private void saveCheckpointToFile(Checkpoint checkpoint) {
        try {
            Path dirPath = Paths.get(CHECKPOINT_DIR);
            if (!Files.exists(dirPath)) {
                Files.createDirectories(dirPath);
            }

            String filename = String.format("%s_%s.json",
                    checkpoint.getSessionId(),
                    checkpoint.getCheckpointId());

            Path filePath = dirPath.resolve(filename);

            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(checkpoint);
            Files.write(filePath, json.getBytes(java.nio.charset.StandardCharsets.UTF_8), 
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            log.debug("保存检查点到文件: {}", filename);

        } catch (Exception e) {
            log.error("保存检查点失败: {}", e.getMessage());
        }
    }

    private void updateCheckpointFile(Checkpoint checkpoint) {
        saveCheckpointToFile(checkpoint);
    }

    private void deleteCheckpointFile(Checkpoint checkpoint) {
        try {
            String filename = String.format("%s_%s.json",
                    checkpoint.getSessionId(),
                    checkpoint.getCheckpointId());

            Path filePath = Paths.get(CHECKPOINT_DIR, filename);
            Files.deleteIfExists(filePath);

        } catch (Exception e) {
            log.warn("删除检查点文件失败: {}", e.getMessage());
        }
    }

    private List<Checkpoint> loadCheckpointsFromDisk(String sessionId) {
        try {
            Path dirPath = Paths.get(CHECKPOINT_DIR);
            if (!Files.exists(dirPath)) {
                return null;
            }

            List<Checkpoint> checkpoints = new ArrayList<>();

            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dirPath, sessionId + "_ckpt_*.json")) {
                for (Path filePath : stream) {
                    try {
                        String content = new String(Files.readAllBytes(filePath), java.nio.charset.StandardCharsets.UTF_8);
                        Checkpoint checkpoint = objectMapper.readValue(content, Checkpoint.class);
                        checkpoints.add(checkpoint);
                    } catch (Exception e) {
                        log.warn("加载检查点失败: {}", filePath);
                    }
                }
            }

            checkpoints.sort(Comparator.comparing(Checkpoint::getCreatedAt));

            return checkpoints;

        } catch (Exception e) {
            log.error("加载检查点目录失败: {}", e.getMessage());
            return null;
        }
    }

    public CheckpointSummary getCheckpointSummary(String sessionId) {
        List<Checkpoint> checkpoints = getSessionCheckpoints(sessionId);

        CheckpointSummary summary = new CheckpointSummary();
        summary.setSessionId(sessionId);
        summary.setTotalCheckpoints(checkpoints.size());
        summary.setRecoverableCheckpoints((int) checkpoints.stream().filter(Checkpoint::isRecoverable).count());

        if (!checkpoints.isEmpty()) {
            Checkpoint latest = checkpoints.get(checkpoints.size() - 1);
            summary.setLatestStep(latest.getCurrentStep());
            summary.setTotalSteps(latest.getTotalSteps());
            summary.setLatestPhase(latest.getPhase());
            summary.setLatestType(latest.getType().name());
            summary.setCanRecover(latest.isRecoverable());
        }

        return summary;
    }

    @Data
    public static class CheckpointSummary {
        private String sessionId;
        private int totalCheckpoints;
        private int recoverableCheckpoints;
        private int latestStep;
        private int totalSteps;
        private String latestPhase;
        private String latestType;
        private boolean canRecover;
    }
}
