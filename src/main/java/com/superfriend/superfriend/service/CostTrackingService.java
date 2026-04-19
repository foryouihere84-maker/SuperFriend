package com.superfriend.superfriend.service;

import com.superfriend.superfriend.event.CostUpdateEvent;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Slf4j
@Service
public class CostTrackingService {

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    private final Map<String, ModelPricing> modelPricingMap = new ConcurrentHashMap<>();
    private final Map<String, SessionCost> sessionCosts = new ConcurrentHashMap<>();
    private final Map<String, UserCost> userCosts = new ConcurrentHashMap<>();
    private final Map<LocalDate, DailyCost> dailyCosts = new ConcurrentHashMap<>();
    
    private final AtomicLong totalApiCalls = new AtomicLong(0);
    private final AtomicLong totalTokensUsed = new AtomicLong(0);
    private final AtomicLong totalCostCents = new AtomicLong(0);

    public CostTrackingService() {
        initDefaultPricing();
    }

    private void initDefaultPricing() {
        setModelPricing("gpt-4", 0.03, 0.06);
        setModelPricing("gpt-4-turbo", 0.01, 0.03);
        setModelPricing("gpt-4o", 0.005, 0.015);
        setModelPricing("gpt-4o-mini", 0.00015, 0.0006);
        setModelPricing("gpt-3.5-turbo", 0.0005, 0.0015);
        setModelPricing("claude-3-opus", 0.015, 0.075);
        setModelPricing("claude-3-sonnet", 0.003, 0.015);
        setModelPricing("claude-3-haiku", 0.00025, 0.00125);
        setModelPricing("deepseek-chat", 0.00014, 0.00028);
        setModelPricing("deepseek-reasoner", 0.00055, 0.00219);
        setModelPricing("gemini-pro", 0.00025, 0.0005);
        setModelPricing("gemini-1.5-pro", 0.0035, 0.0105);
        setModelPricing("llama-3-70b", 0.0007, 0.0009);
        setModelPricing("qwen-max", 0.0004, 0.0012);
        setModelPricing("default", 0.001, 0.002);
    }

    @Data
    public static class ModelPricing {
        private String modelId;
        private double inputPricePer1k;
        private double outputPricePer1k;
        private String currency;
        
        public double calculateCost(long inputTokens, long outputTokens) {
            double inputCost = (inputTokens / 1000.0) * inputPricePer1k;
            double outputCost = (outputTokens / 1000.0) * outputPricePer1k;
            return inputCost + outputCost;
        }
    }

    @Data
    public static class SessionCost {
        private String sessionId;
        private Long userId;
        private String model;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private long totalInputTokens;
        private long totalOutputTokens;
        private long totalTokens;
        private double totalCost;
        private int apiCallCount;
        private int toolCallCount;
        private List<CostEvent> events = new ArrayList<>();
    }

    @Data
    public static class CostEvent {
        private LocalDateTime timestamp;
        private String eventType;
        private String model;
        private long inputTokens;
        private long outputTokens;
        private double cost;
        private Map<String, Object> metadata;
    }

    @Data
    public static class UserCost {
        private Long userId;
        private AtomicLong totalTokens = new AtomicLong(0);
        private AtomicLong totalApiCalls = new AtomicLong(0);
        private AtomicLong totalCostCents = new AtomicLong(0);
        private Map<String, AtomicLong> modelUsage = new ConcurrentHashMap<>();
        private Map<LocalDate, AtomicLong> dailyCostCents = new ConcurrentHashMap<>();
    }

    @Data
    public static class DailyCost {
        private LocalDate date;
        private AtomicLong totalTokens = new AtomicLong(0);
        private AtomicLong totalApiCalls = new AtomicLong(0);
        private AtomicLong totalCostCents = new AtomicLong(0);
        private Map<String, AtomicLong> modelCosts = new ConcurrentHashMap<>();
    }

    @Data
    public static class CostReport {
        private LocalDateTime reportTime;
        private long totalApiCalls;
        private long totalTokens;
        private double totalCost;
        private double averageCostPerCall;
        private Map<String, ModelCostSummary> modelSummaries;
        private List<SessionCost> topExpensiveSessions;
        private DailyTrend dailyTrend;
    }

    @Data
    public static class ModelCostSummary {
        private String modelId;
        private long totalCalls;
        private long totalInputTokens;
        private long totalOutputTokens;
        private double totalCost;
        private double averageCostPerCall;
    }

    @Data
    public static class DailyTrend {
        private List<DailySummary> days;
    }

    @Data
    public static class DailySummary {
        private LocalDate date;
        private long apiCalls;
        private long tokens;
        private double cost;
    }

    @Data
    public static class BudgetStatus {
        private Long userId;
        private double dailyBudget;
        private double dailySpent;
        private double dailyRemaining;
        private double monthlyBudget;
        private double monthlySpent;
        private double monthlyRemaining;
        private double usagePercentage;
        private boolean overBudget;
    }

    public void setModelPricing(String modelId, double inputPricePer1k, double outputPricePer1k) {
        ModelPricing pricing = new ModelPricing();
        pricing.setModelId(modelId);
        pricing.setInputPricePer1k(inputPricePer1k);
        pricing.setOutputPricePer1k(outputPricePer1k);
        pricing.setCurrency("USD");
        modelPricingMap.put(modelId.toLowerCase(), pricing);
        log.debug("设置模型定价: {} -> input=${}/1k, output=${}/1k", 
            modelId, inputPricePer1k, outputPricePer1k);
    }

    public ModelPricing getModelPricing(String modelId) {
        if (modelId == null) {
            return modelPricingMap.get("default");
        }
        String normalizedModelId = modelId.toLowerCase();
        
        for (Map.Entry<String, ModelPricing> entry : modelPricingMap.entrySet()) {
            if (normalizedModelId.contains(entry.getKey()) || entry.getKey().equals("default")) {
                return entry.getValue();
            }
        }
        
        return modelPricingMap.get("default");
    }

    public double calculateCost(String modelId, long inputTokens, long outputTokens) {
        ModelPricing pricing = getModelPricing(modelId);
        return pricing.calculateCost(inputTokens, outputTokens);
    }

    public void recordApiCall(String sessionId, Long userId, String model,
                              long inputTokens, long outputTokens,
                              Map<String, Object> metadata) {
        double cost = calculateCost(model, inputTokens, outputTokens);
        long costCents = (long) (cost * 100);
        
        totalApiCalls.incrementAndGet();
        totalTokensUsed.addAndGet(inputTokens + outputTokens);
        totalCostCents.addAndGet(costCents);
        
        SessionCost sessionCost = sessionCosts.computeIfAbsent(sessionId, k -> {
            SessionCost sc = new SessionCost();
            sc.setSessionId(sessionId);
            sc.setUserId(userId);
            sc.setModel(model);
            sc.setStartTime(LocalDateTime.now());
            return sc;
        });
        sessionCost.setTotalInputTokens(sessionCost.getTotalInputTokens() + inputTokens);
        sessionCost.setTotalOutputTokens(sessionCost.getTotalOutputTokens() + outputTokens);
        sessionCost.setTotalTokens(sessionCost.getTotalTokens() + inputTokens + outputTokens);
        sessionCost.setTotalCost(sessionCost.getTotalCost() + cost);
        sessionCost.setApiCallCount(sessionCost.getApiCallCount() + 1);
        
        CostEvent event = new CostEvent();
        event.setTimestamp(LocalDateTime.now());
        event.setEventType("api_call");
        event.setModel(model);
        event.setInputTokens(inputTokens);
        event.setOutputTokens(outputTokens);
        event.setCost(cost);
        event.setMetadata(metadata != null ? metadata : new HashMap<>());
        sessionCost.getEvents().add(event);
        
        if (userId != null) {
            UserCost userCost = userCosts.computeIfAbsent(userId.toString(), k -> {
                UserCost uc = new UserCost();
                uc.setUserId(userId);
                return uc;
            });
            userCost.getTotalTokens().addAndGet(inputTokens + outputTokens);
            userCost.getTotalApiCalls().incrementAndGet();
            userCost.getTotalCostCents().addAndGet(costCents);
            userCost.getModelUsage().computeIfAbsent(model, k -> new AtomicLong(0))
                .addAndGet(inputTokens + outputTokens);
            userCost.getDailyCostCents().computeIfAbsent(LocalDate.now(), k -> new AtomicLong(0))
                .addAndGet(costCents);
        }
        
        LocalDate today = LocalDate.now();
        DailyCost dailyCost = dailyCosts.computeIfAbsent(today, k -> {
            DailyCost dc = new DailyCost();
            dc.setDate(today);
            return dc;
        });
        dailyCost.getTotalTokens().addAndGet(inputTokens + outputTokens);
        dailyCost.getTotalApiCalls().incrementAndGet();
        dailyCost.getTotalCostCents().addAndGet(costCents);
        dailyCost.getModelCosts().computeIfAbsent(model, k -> new AtomicLong(0))
            .addAndGet(costCents);
        
        log.info("记录 API 调用成本: session={}, model={}, tokens={}/{}, cost=${}", 
            sessionId, model, inputTokens, outputTokens, String.format("%.6f", cost));
        
        // 发布成本更新事件
        eventPublisher.publishEvent(new CostUpdateEvent(sessionId, userId));
    }

    public void recordToolCall(String sessionId, String toolName, String serverName) {
        SessionCost sessionCost = sessionCosts.get(sessionId);
        if (sessionCost != null) {
            sessionCost.setToolCallCount(sessionCost.getToolCallCount() + 1);
            
            CostEvent event = new CostEvent();
            event.setTimestamp(LocalDateTime.now());
            event.setEventType("tool_call");
            Map<String, Object> meta = new HashMap<>();
            meta.put("toolName", toolName);
            meta.put("serverName", serverName);
            event.setMetadata(meta);
            sessionCost.getEvents().add(event);
        }
    }

    public void endSession(String sessionId) {
        SessionCost sessionCost = sessionCosts.get(sessionId);
        if (sessionCost != null) {
            sessionCost.setEndTime(LocalDateTime.now());
            log.info("会话成本统计: session={}, totalCost=${}, tokens={}, apiCalls={}", 
                sessionId, String.format("%.4f", sessionCost.getTotalCost()), 
                sessionCost.getTotalTokens(), sessionCost.getApiCallCount());
        }
    }

    public SessionCost getSessionCost(String sessionId) {
        return sessionCosts.get(sessionId);
    }

    public UserCost getUserCost(Long userId) {
        return userCosts.get(userId.toString());
    }

    public DailyCost getDailyCost(LocalDate date) {
        return dailyCosts.get(date);
    }

    public CostReport generateReport(LocalDate startDate, LocalDate endDate) {
        CostReport report = new CostReport();
        report.setReportTime(LocalDateTime.now());
        report.setTotalApiCalls(totalApiCalls.get());
        report.setTotalTokens(totalTokensUsed.get());
        report.setTotalCost(totalCostCents.get() / 100.0);
        report.setAverageCostPerCall(
            totalApiCalls.get() > 0 ? 
            (totalCostCents.get() / 100.0) / totalApiCalls.get() : 0.0
        );
        
        Map<String, ModelCostSummary> modelSummaries = new HashMap<>();
        for (DailyCost dailyCost : dailyCosts.values()) {
            if ((startDate == null || !dailyCost.getDate().isBefore(startDate)) &&
                (endDate == null || !dailyCost.getDate().isAfter(endDate))) {
                for (Map.Entry<String, AtomicLong> entry : dailyCost.getModelCosts().entrySet()) {
                    String model = entry.getKey();
                    ModelCostSummary summary = modelSummaries.computeIfAbsent(model, k -> {
                        ModelCostSummary mcs = new ModelCostSummary();
                        mcs.setModelId(model);
                        return mcs;
                    });
                    summary.setTotalCalls(summary.getTotalCalls() + dailyCost.getTotalApiCalls().get());
                    summary.setTotalCost(summary.getTotalCost() + entry.getValue().get() / 100.0);
                }
            }
        }
        report.setModelSummaries(modelSummaries);
        
        List<SessionCost> sortedSessions = sessionCosts.values().stream()
            .sorted((a, b) -> Double.compare(b.getTotalCost(), a.getTotalCost()))
            .limit(10)
            .collect(Collectors.toList());
        report.setTopExpensiveSessions(sortedSessions);
        
        DailyTrend trend = new DailyTrend();
        List<DailySummary> days = dailyCosts.entrySet().stream()
            .filter(e -> (startDate == null || !e.getKey().isBefore(startDate)) &&
                        (endDate == null || !e.getKey().isAfter(endDate)))
            .sorted(Map.Entry.comparingByKey())
            .map(e -> {
                DailySummary ds = new DailySummary();
                ds.setDate(e.getKey());
                ds.setApiCalls(e.getValue().getTotalApiCalls().get());
                ds.setTokens(e.getValue().getTotalTokens().get());
                ds.setCost(e.getValue().getTotalCostCents().get() / 100.0);
                return ds;
            })
            .collect(Collectors.toList());
        trend.setDays(days);
        report.setDailyTrend(trend);
        
        return report;
    }

    public BudgetStatus checkBudget(Long userId, double dailyBudget, double monthlyBudget) {
        BudgetStatus status = new BudgetStatus();
        status.setUserId(userId);
        status.setDailyBudget(dailyBudget);
        status.setMonthlyBudget(monthlyBudget);
        
        LocalDate today = LocalDate.now();
        DailyCost todayCost = dailyCosts.get(today);
        status.setDailySpent(todayCost != null ? todayCost.getTotalCostCents().get() / 100.0 : 0.0);
        status.setDailyRemaining(dailyBudget - status.getDailySpent());
        
        LocalDate monthStart = today.withDayOfMonth(1);
        double monthlySpent = 0.0;
        for (LocalDate date = monthStart; !date.isAfter(today); date = date.plusDays(1)) {
            DailyCost dc = dailyCosts.get(date);
            if (dc != null) {
                monthlySpent += dc.getTotalCostCents().get() / 100.0;
            }
        }
        status.setMonthlySpent(monthlySpent);
        status.setMonthlyRemaining(monthlyBudget - monthlySpent);
        
        status.setUsagePercentage(monthlyBudget > 0 ? (monthlySpent / monthlyBudget) * 100 : 0.0);
        status.setOverBudget(monthlySpent > monthlyBudget);
        
        return status;
    }

    public Map<String, Object> getCostMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("totalApiCalls", totalApiCalls.get());
        metrics.put("totalTokens", totalTokensUsed.get());
        metrics.put("totalCost", totalCostCents.get() / 100.0);
        metrics.put("activeSessions", sessionCosts.size());
        metrics.put("trackedUsers", userCosts.size());
        metrics.put("trackedDays", dailyCosts.size());
        return metrics;
    }

    public void cleanupOldData(int maxAgeDays) {
        LocalDate cutoff = LocalDate.now().minusDays(maxAgeDays);
        
        dailyCosts.entrySet().removeIf(entry -> entry.getKey().isBefore(cutoff));
        
        sessionCosts.entrySet().removeIf(entry -> {
            SessionCost sc = entry.getValue();
            return sc.getEndTime() != null && 
                   sc.getEndTime().toLocalDate().isBefore(cutoff);
        });
        
        log.info("清理过期成本数据: 保留 {} 天内的记录", maxAgeDays);
    }

    public void resetCounters() {
        totalApiCalls.set(0);
        totalTokensUsed.set(0);
        totalCostCents.set(0);
        log.warn("成本计数器已重置");
    }
}
