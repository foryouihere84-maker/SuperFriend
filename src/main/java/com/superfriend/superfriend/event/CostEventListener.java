package com.superfriend.superfriend.event;

import com.superfriend.superfriend.service.CostTrackingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class CostEventListener {

    @Autowired
    private CostTrackingService costTrackingService;

    private final Map<String, CostUpdateCallback> sessionCallbacks = new ConcurrentHashMap<>();

    public interface CostUpdateCallback {
        void onCostUpdate(String sessionId, CostTrackingService.SessionCost cost);
    }

    public void registerCallback(String sessionId, CostUpdateCallback callback) {
        sessionCallbacks.put(sessionId, callback);
        log.debug("注册成本回调: sessionId={}", sessionId);
    }

    public void unregisterCallback(String sessionId) {
        sessionCallbacks.remove(sessionId);
        log.debug("注销成本回调: sessionId={}", sessionId);
    }

    @Async
    @EventListener
    public void onCostUpdate(CostUpdateEvent event) {
        String sessionId = event.getSessionId();
        Long userId = event.getUserId();

        log.debug("收到成本更新事件: sessionId={}, userId={}", sessionId, userId);

        CostTrackingService.SessionCost sessionCost = costTrackingService.getSessionCost(sessionId);
        if (sessionCost == null) {
            log.warn("未找到会话成本: sessionId={}", sessionId);
            return;
        }

        CostUpdateCallback callback = sessionCallbacks.get(sessionId);
        if (callback != null) {
            try {
                callback.onCostUpdate(sessionId, sessionCost);
                log.debug("成本回调执行成功: sessionId={}, totalCost=${}", 
                    sessionId, String.format("%.4f", sessionCost.getTotalCost()));
            } catch (Exception e) {
                log.error("成本回调执行失败: sessionId={}, error={}", sessionId, e.getMessage());
            }
        }

        if (sessionCost.getTotalCost() > 1.0) {
            log.warn("会话成本超过 $1: sessionId={}, totalCost=${}", 
                sessionId, String.format("%.4f", sessionCost.getTotalCost()));
        }

        if (userId != null) {
            CostTrackingService.UserCost userCost = costTrackingService.getUserCost(userId);
            if (userCost != null) {
                double userTotalCost = userCost.getTotalCostCents().get() / 100.0;
                if (userTotalCost > 10.0) {
                    log.warn("用户累计成本超过 $10: userId={}, totalCost=${}", 
                        userId, String.format("%.4f", userTotalCost));
                }
            }
        }
    }
}
