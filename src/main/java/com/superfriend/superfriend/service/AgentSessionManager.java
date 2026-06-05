package com.superfriend.superfriend.service;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

//会话状态管理
@Slf4j
@Service
public class AgentSessionManager {

    private final ConcurrentHashMap<String, AgentSessionState> activeSessions = new ConcurrentHashMap<>();

    public enum SessionState {
        IDLE,
        EXECUTING,
        PAUSED,
        CANCELLED
    }

    public enum InterruptMode {
        CANCEL,
        APPEND
    }

    @Data
    public static class AgentSessionState {
        private SessionState state = SessionState.IDLE;
        private InterruptMode interruptMode;
        private String appendedContext;
        private long startTime;
    }

    public void startExecution(String sessionId) {
        AgentSessionState state = activeSessions.computeIfAbsent(sessionId, k -> new AgentSessionState());
        state.setState(SessionState.EXECUTING);
        state.setInterruptMode(null);
        state.setAppendedContext(null);
        state.setStartTime(System.currentTimeMillis());
        log.info("Agent session started: {}", sessionId);
    }

    public void endExecution(String sessionId) {
        AgentSessionState removed = activeSessions.remove(sessionId);
        if (removed != null) {
            long duration = System.currentTimeMillis() - removed.getStartTime();
            log.info("Agent session ended: {}, duration: {}ms", sessionId, duration);
        }
    }

    public boolean shouldInterrupt(String sessionId) {
        AgentSessionState state = activeSessions.get(sessionId);
        return state != null && state.getInterruptMode() != null;
    }

    public InterruptMode getInterruptMode(String sessionId) {
        AgentSessionState state = activeSessions.get(sessionId);
        return state != null ? state.getInterruptMode() : null;
    }

    public String getAppendedContext(String sessionId) {
        AgentSessionState state = activeSessions.get(sessionId);
        return state != null ? state.getAppendedContext() : null;
    }

    public void interrupt(String sessionId, InterruptMode mode, String context) {
        AgentSessionState state = activeSessions.get(sessionId);
        if (state == null) {
            log.warn("Cannot interrupt session {}: session not found", sessionId);
            return;
        }
        if (state.getState() != SessionState.EXECUTING) {
            log.warn("Cannot interrupt session {}: current state is {}", sessionId, state.getState());
            return;
        }
        state.setInterruptMode(mode);
        state.setAppendedContext(context);
        log.info("Agent session interrupt requested: {}, mode={}, context={}", sessionId, mode, context);
    }

    public void clearInterrupt(String sessionId) {
        AgentSessionState state = activeSessions.get(sessionId);
        if (state != null) {
            state.setInterruptMode(null);
            state.setAppendedContext(null);
        }
    }

    public SessionState getSessionState(String sessionId) {
        AgentSessionState state = activeSessions.get(sessionId);
        return state != null ? state.getState() : SessionState.IDLE;
    }

    public boolean isExecuting(String sessionId) {
        return getSessionState(sessionId) == SessionState.EXECUTING;
    }

    /**
     * 取消正在执行的会话
     * @param sessionId 会话ID
     * @return 是否成功取消
     */
    public boolean cancelSession(String sessionId) {
        AgentSessionState state = activeSessions.get(sessionId);
        if (state == null) {
            log.warn("Cannot cancel session {}: session not found", sessionId);
            return false;
        }
        if (state.getState() != SessionState.EXECUTING) {
            log.warn("Cannot cancel session {}: current state is {}", sessionId, state.getState());
            return false;
        }
        state.setState(SessionState.CANCELLED);
        state.setInterruptMode(InterruptMode.CANCEL);
        log.info("Agent session cancelled: {}", sessionId);
        return true;
    }

    /**
     * 检查会话是否已被取消
     * @param sessionId 会话ID
     * @return 是否已取消
     */
    public boolean isCancelled(String sessionId) {
        AgentSessionState state = activeSessions.get(sessionId);
        return state != null &&
               (state.getState() == SessionState.CANCELLED ||
                state.getInterruptMode() == InterruptMode.CANCEL);
    }

    /**
     * 获取所有活跃会话数量
     */
    public int getActiveSessionCount() {
        return (int) activeSessions.values().stream()
            .filter(s -> s.getState() == SessionState.EXECUTING)
            .count();
    }

    /**
     * 清理超时的会话（超过指定毫秒数）
     */
    public int cleanupStaleSessions(long timeoutMs) {
        long now = System.currentTimeMillis();
        int cleaned = 0;
        for (Map.Entry<String, AgentSessionState> entry : activeSessions.entrySet()) {
            if (entry.getValue().getState() == SessionState.EXECUTING &&
                now - entry.getValue().getStartTime() > timeoutMs) {
                activeSessions.remove(entry.getKey());
                cleaned++;
                log.warn("Cleaned up stale session: {}, age: {}ms",
                    entry.getKey(), now - entry.getValue().getStartTime());
            }
        }
        return cleaned;
    }
}
