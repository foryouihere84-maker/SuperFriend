package com.superfriend.superfriend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.superfriend.superfriend.dto.*;
import com.superfriend.superfriend.entity.AgentApprovalLog;
import com.superfriend.superfriend.entity.AgentPermissionPolicy;
import com.superfriend.superfriend.mapper.AgentApprovalLogMapper;
import com.superfriend.superfriend.mapper.AgentPermissionPolicyMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

@Slf4j
@Service
public class ApprovalService {

    @Autowired
    private AgentApprovalLogMapper approvalLogMapper;

    @Autowired
    private AgentPermissionPolicyMapper policyMapper;

    @Autowired
    private ObjectMapper objectMapper;

    private final ConcurrentHashMap<String, PendingApproval> pendingApprovals = new ConcurrentHashMap<>();
    private final ScheduledExecutorService timeoutExecutor = Executors.newSingleThreadScheduledExecutor();

    private static final long DEFAULT_TIMEOUT_MS = 30000;

    @Data
    public static class PendingApproval {
        private final ApprovalRequestDTO request;
        private final CompletableFuture<ApprovalResult> future;
        private final long createdAt;
        private ScheduledFuture<?> timeoutFuture;

        public PendingApproval(ApprovalRequestDTO request) {
            this.request = request;
            this.future = new CompletableFuture<>();
            this.createdAt = System.currentTimeMillis();
        }
    }

    @Data
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class ApprovalResult {
        private boolean approved;
        private String reason;
        private String decidedBy;
    }

    public ApprovalRequestDTO submitApproval(String sessionId, Long userId, String toolName,
                                              String serverName, Map<String, Object> arguments,
                                              String permissionLevel) {
        String requestId = "apr_" + System.currentTimeMillis() + "_" + (int)(Math.random() * 10000);

        ApprovalRequestDTO request = new ApprovalRequestDTO();
        request.setRequestId(requestId);
        request.setSessionId(sessionId);
        request.setToolName(toolName);
        request.setServerName(serverName);
        request.setOperation(inferOperationFromTool(toolName));
        request.setArguments(arguments != null ? arguments.toString() : "{}");
        request.setPermissionLevel(permissionLevel);
        request.setDescription(buildDescription(toolName, arguments));
        request.setUserId(userId);

        PendingApproval pending = new PendingApproval(request);

        ScheduledFuture<?> timeoutFuture = timeoutExecutor.schedule(() -> {
            if (!pending.getFuture().isDone()) {
                log.warn("Approval request timed out: requestId={}, tool={}", requestId, toolName);
                pending.getFuture().complete(new ApprovalResult(false, "审批超时（30秒未响应）", "system"));
                pendingApprovals.remove(requestId);
                logApproval(request, "timed_out", "system", "审批超时", DEFAULT_TIMEOUT_MS);
            }
        }, DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS);

        pending.setTimeoutFuture(timeoutFuture);
        pendingApprovals.put(requestId, pending);

        log.info("Approval request submitted: requestId={}, tool={}, session={}", requestId, toolName, sessionId);
        return request;
    }

    public ApprovalResult waitForApproval(String requestId, long timeoutMs) {
        PendingApproval pending = pendingApprovals.get(requestId);
        if (pending == null) {
            return new ApprovalResult(false, "审批请求不存在", "system");
        }

        try {
            return pending.getFuture().get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            return new ApprovalResult(false, "等待审批超时", "system");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new ApprovalResult(false, "等待审批被中断", "system");
        } catch (ExecutionException e) {
            return new ApprovalResult(false, "审批处理异常: " + e.getMessage(), "system");
        }
    }

    public boolean decide(String requestId, ApprovalDecisionDTO decision) {
        PendingApproval pending = pendingApprovals.get(requestId);
        if (pending == null) {
            log.warn("Approval decision for unknown request: {}", requestId);
            return false;
        }

        if (pending.getFuture().isDone()) {
            log.warn("Approval already decided: {}", requestId);
            return false;
        }

        long responseTimeMs = System.currentTimeMillis() - pending.getCreatedAt();
        ApprovalRequestDTO request = pending.getRequest();

        if ("approved".equals(decision.getDecision())) {
            pending.getFuture().complete(new ApprovalResult(true, decision.getReason(), "user"));
            logApproval(request, "approved", "user", decision.getReason(), (int) responseTimeMs);

            if (decision.isAlwaysAllow()) {
                addAutoAllowPolicy(request);
            }
        } else {
            pending.getFuture().complete(new ApprovalResult(false, decision.getReason(), "user"));
            logApproval(request, "denied", "user", decision.getReason(), (int) responseTimeMs);
        }

        if (pending.getTimeoutFuture() != null) {
            pending.getTimeoutFuture().cancel(false);
        }
        pendingApprovals.remove(requestId);

        log.info("Approval decided: requestId={}, decision={}, alwaysAllow={}",
                requestId, decision.getDecision(), decision.isAlwaysAllow());
        return true;
    }

    public ApprovalRequestDTO getPendingApproval(String requestId) {
        PendingApproval pending = pendingApprovals.get(requestId);
        return pending != null ? pending.getRequest() : null;
    }

    public List<ApprovalRequestDTO> getPendingApprovalsForSession(String sessionId) {
        return pendingApprovals.values().stream()
                .filter(p -> sessionId.equals(p.getRequest().getSessionId()))
                .map(PendingApproval::getRequest)
                .collect(java.util.stream.Collectors.toList());
    }

    private void addAutoAllowPolicy(ApprovalRequestDTO request) {
        try {
            Long userId = request.getUserId();
            if (userId == null) {
                log.warn("Cannot add auto_allow policy: userId is null for tool {}", request.getToolName());
                return;
            }
            AgentPermissionPolicy policy = new AgentPermissionPolicy();
            policy.setUserId(userId);
            policy.setToolName(request.getToolName());
            policy.setOperationType(request.getOperation());
            policy.setPermissionLevel("auto_allow");
            policy.setDescription("用户手动添加：总是允许 " + request.getToolName());
            policyMapper.insert(policy);
            log.info("Added auto_allow policy for tool: {}", request.getToolName());
        } catch (Exception e) {
            log.error("Failed to add auto_allow policy: {}", e.getMessage(), e);
        }
    }

    private void logApproval(ApprovalRequestDTO request, String decision, String decidedBy,
                              String reason, long responseTimeMs) {
        try {
            if (request.getUserId() == null) {
                log.warn("Skipping approval log: userId is null for tool {}", request.getToolName());
                return;
            }
            AgentApprovalLog logEntry = new AgentApprovalLog();
            logEntry.setUserId(request.getUserId());
            logEntry.setSessionId(request.getSessionId());
            logEntry.setToolName(request.getToolName());
            logEntry.setOperation(request.getOperation());
            logEntry.setArguments(request.getArguments());
            logEntry.setPermissionLevel(request.getPermissionLevel());
            logEntry.setDecision(decision);
            logEntry.setDecidedBy(decidedBy);
            logEntry.setReason(reason);
            logEntry.setResponseTimeMs((int) responseTimeMs);
            approvalLogMapper.insert(logEntry);
        } catch (Exception e) {
            log.error("Failed to log approval: {}", e.getMessage(), e);
        }
    }

    private String inferOperationFromTool(String toolName) {
        if (toolName == null) return "execute";
        String lower = toolName.toLowerCase();
        if (lower.contains("delete") || lower.contains("remove") || lower.contains("rm_")) return "delete";
        if (lower.contains("write") || lower.contains("create") || lower.contains("save") || lower.contains("upload")) return "write";
        if (lower.contains("read") || lower.contains("list") || lower.contains("get") || lower.contains("search") || lower.contains("query")) return "read";
        return "execute";
    }

    private String buildDescription(String toolName, Map<String, Object> arguments) {
        StringBuilder sb = new StringBuilder();
        sb.append("工具调用：").append(toolName);
        if (arguments != null && !arguments.isEmpty()) {
            sb.append("\n参数：");
            for (Map.Entry<String, Object> entry : arguments.entrySet()) {
                String value = entry.getValue() != null ? entry.getValue().toString() : "null";
                if (value.length() > 100) {
                    value = value.substring(0, 100) + "...";
                }
                sb.append("\n  ").append(entry.getKey()).append(" = ").append(value);
            }
        }
        return sb.toString();
    }
}
