package com.superfriend.superfriend.service;

import com.superfriend.superfriend.dto.McpToolDefinition;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class FallbackStrategyService {

    @Autowired
    @Lazy
    private McpHostService mcpHostService;

    private final Map<String, List<ToolAlternative>> toolAlternatives = new ConcurrentHashMap<>();
    private final Map<String, Integer> failureCounts = new ConcurrentHashMap<>();
    private final Map<String, Long> lastFailureTime = new ConcurrentHashMap<>();

    private static final int MAX_FAILURES_BEFORE_FALLBACK = 2;
    private static final long FAILURE_MEMORY_DURATION_MS = 300000;

    @Data
    public static class ToolAlternative {
        private String toolName;
        private String serverName;
        private double priority;
        private String fallbackCondition;
        private Map<String, String> parameterMapping;
    }

    @Data
    public static class FallbackContext {
        private String originalToolName;
        private String originalServerName;
        private Map<String, Object> originalParameters;
        private String failureReason;
        private String userRequest;
        private List<McpToolDefinition> availableTools;
        private int attemptCount;
    }

    @Data
    public static class FallbackResult {
        private boolean hasAlternative;
        private String alternativeToolName;
        private String alternativeServerName;
        private Map<String, Object> alternativeParameters;
        private String strategy;
        private String reason;
    }

    public FallbackResult findFallback(FallbackContext context) {
        recordFailure(context.getOriginalToolName(), context.getOriginalServerName());

        FallbackResult result = tryDirectAlternative(context);
        if (result.isHasAlternative()) {
            return result;
        }

        result = tryFunctionalAlternative(context);
        if (result.isHasAlternative()) {
            return result;
        }

        result = tryDegradedStrategy(context);
        return result;
    }

    private FallbackResult tryDirectAlternative(FallbackContext context) {
        String toolKey = context.getOriginalServerName() + "__" + context.getOriginalToolName();

        List<ToolAlternative> alternatives = toolAlternatives.get(toolKey);
        if (alternatives != null && !alternatives.isEmpty()) {
            for (ToolAlternative alt : alternatives) {
                if (shouldUseAlternative(alt, context)) {
                    FallbackResult result = new FallbackResult();
                    result.setHasAlternative(true);
                    result.setAlternativeToolName(alt.getToolName());
                    result.setAlternativeServerName(alt.getServerName());
                    result.setAlternativeParameters(mapParameters(context.getOriginalParameters(), alt.getParameterMapping()));
                    result.setStrategy("direct_alternative");
                    result.setReason("使用预设的替代工具: " + alt.getToolName());
                    log.info("找到直接替代工具: {} -> {}", context.getOriginalToolName(), alt.getToolName());
                    return result;
                }
            }
        }

        for (McpToolDefinition tool : context.getAvailableTools()) {
            if (isDirectAlternative(context.getOriginalToolName(), tool.getName())) {
                if (!isToolInFailureState(tool.getName(), tool.getServerName())) {
                    FallbackResult result = new FallbackResult();
                    result.setHasAlternative(true);
                    result.setAlternativeToolName(tool.getName());
                    result.setAlternativeServerName(tool.getServerName());
                    result.setAlternativeParameters(context.getOriginalParameters());
                    result.setStrategy("similar_tool");
                    result.setReason("使用功能相似的工具: " + tool.getName());
                    log.info("找到相似工具: {} -> {}", context.getOriginalToolName(), tool.getName());
                    return result;
                }
            }
        }

        return createNoAlternativeResult();
    }

    private FallbackResult tryFunctionalAlternative(FallbackContext context) {
        String originalTool = context.getOriginalToolName().toLowerCase();
        String userRequest = context.getUserRequest() != null ? context.getUserRequest().toLowerCase() : "";

        if (originalTool.contains("read") || originalTool.contains("get")) {
            return tryReadAlternative(context, userRequest);
        }

        if (originalTool.contains("search") || originalTool.contains("find")) {
            return trySearchAlternative(context, userRequest);
        }

        if (originalTool.contains("write") || originalTool.contains("create")) {
            return tryWriteAlternative(context, userRequest);
        }

        if (originalTool.contains("execute") || originalTool.contains("run")) {
            return tryExecuteAlternative(context, userRequest);
        }

        return createNoAlternativeResult();
    }

    private FallbackResult tryReadAlternative(FallbackContext context, String userRequest) {
        for (McpToolDefinition tool : context.getAvailableTools()) {
            String toolName = tool.getName().toLowerCase();
            if ((toolName.contains("list") || toolName.contains("browse")) 
                    && !isToolInFailureState(tool.getName(), tool.getServerName())) {
                FallbackResult result = new FallbackResult();
                result.setHasAlternative(true);
                result.setAlternativeToolName(tool.getName());
                result.setAlternativeServerName(tool.getServerName());
                result.setAlternativeParameters(context.getOriginalParameters());
                result.setStrategy("list_instead_of_read");
                result.setReason("使用列表工具代替读取工具");
                return result;
            }
        }
        return createNoAlternativeResult();
    }

    private FallbackResult trySearchAlternative(FallbackContext context, String userRequest) {
        for (McpToolDefinition tool : context.getAvailableTools()) {
            String toolName = tool.getName().toLowerCase();
            if ((toolName.contains("read") || toolName.contains("get") || toolName.contains("list"))
                    && !isToolInFailureState(tool.getName(), tool.getServerName())) {
                FallbackResult result = new FallbackResult();
                result.setHasAlternative(true);
                result.setAlternativeToolName(tool.getName());
                result.setAlternativeServerName(tool.getServerName());
                result.setAlternativeParameters(context.getOriginalParameters());
                result.setStrategy("read_instead_of_search");
                result.setReason("使用读取工具代替搜索工具");
                return result;
            }
        }
        return createNoAlternativeResult();
    }

    private FallbackResult tryWriteAlternative(FallbackContext context, String userRequest) {
        for (McpToolDefinition tool : context.getAvailableTools()) {
            String toolName = tool.getName().toLowerCase();
            if (toolName.contains("edit") || toolName.contains("update")
                    && !isToolInFailureState(tool.getName(), tool.getServerName())) {
                FallbackResult result = new FallbackResult();
                result.setHasAlternative(true);
                result.setAlternativeToolName(tool.getName());
                result.setAlternativeServerName(tool.getServerName());
                result.setAlternativeParameters(context.getOriginalParameters());
                result.setStrategy("edit_instead_of_write");
                result.setReason("使用编辑工具代替写入工具");
                return result;
            }
        }
        return createNoAlternativeResult();
    }

    private FallbackResult tryExecuteAlternative(FallbackContext context, String userRequest) {
        for (McpToolDefinition tool : context.getAvailableTools()) {
            String toolName = tool.getName().toLowerCase();
            if (toolName.contains("shell") || toolName.contains("terminal") || toolName.contains("command")
                    && !isToolInFailureState(tool.getName(), tool.getServerName())) {
                FallbackResult result = new FallbackResult();
                result.setHasAlternative(true);
                result.setAlternativeToolName(tool.getName());
                result.setAlternativeServerName(tool.getServerName());
                result.setAlternativeParameters(context.getOriginalParameters());
                result.setStrategy("shell_instead_of_execute");
                result.setReason("使用 Shell 工具代替执行工具");
                return result;
            }
        }
        return createNoAlternativeResult();
    }

    private FallbackResult tryDegradedStrategy(FallbackContext context) {
        if (context.getAttemptCount() >= 3) {
            FallbackResult result = new FallbackResult();
            result.setHasAlternative(false);
            result.setStrategy("abort");
            result.setReason("尝试次数过多，建议放弃并告知用户");
            return result;
        }

        FallbackResult result = new FallbackResult();
        result.setHasAlternative(false);
        result.setStrategy("llm_fallback");
        result.setReason("无可用替代工具，建议让 LLM 直接回答或请求更多信息");
        return result;
    }

    private boolean isDirectAlternative(String originalTool, String candidateTool) {
        if (originalTool.equalsIgnoreCase(candidateTool)) {
            return false;
        }

        String original = originalTool.toLowerCase();
        String candidate = candidateTool.toLowerCase();

        String[] prefixes = {"read", "get", "fetch", "load"};
        if (anyMatch(original, prefixes) && anyMatch(candidate, prefixes)) {
            return true;
        }

        String[] searchPrefixes = {"search", "find", "query", "lookup"};
        if (anyMatch(original, searchPrefixes) && anyMatch(candidate, searchPrefixes)) {
            return true;
        }

        String[] writePrefixes = {"write", "create", "save", "store"};
        if (anyMatch(original, writePrefixes) && anyMatch(candidate, writePrefixes)) {
            return true;
        }

        return false;
    }

    private boolean anyMatch(String text, String[] prefixes) {
        for (String prefix : prefixes) {
            if (text.contains(prefix)) {
                return true;
            }
        }
        return false;
    }

    private boolean shouldUseAlternative(ToolAlternative alt, FallbackContext context) {
        if (alt.getFallbackCondition() == null || alt.getFallbackCondition().isEmpty()) {
            return true;
        }

        String condition = alt.getFallbackCondition().toLowerCase();
        String failureReason = context.getFailureReason() != null ? context.getFailureReason().toLowerCase() : "";

        if (condition.contains("timeout") && failureReason.contains("timeout")) {
            return true;
        }
        if (condition.contains("permission") && failureReason.contains("permission")) {
            return true;
        }
        if (condition.contains("not_found") && (failureReason.contains("not found") || failureReason.contains("不存在"))) {
            return true;
        }

        return false;
    }

    private Map<String, Object> mapParameters(Map<String, Object> originalParams, Map<String, String> mapping) {
        if (mapping == null || mapping.isEmpty()) {
            return originalParams;
        }

        Map<String, Object> mappedParams = new HashMap<>(originalParams);
        for (Map.Entry<String, String> entry : mapping.entrySet()) {
            String oldKey = entry.getKey();
            String newKey = entry.getValue();
            if (originalParams.containsKey(oldKey)) {
                mappedParams.put(newKey, originalParams.get(oldKey));
                if (!oldKey.equals(newKey)) {
                    mappedParams.remove(oldKey);
                }
            }
        }
        return mappedParams;
    }

    private boolean isToolInFailureState(String toolName, String serverName) {
        String key = serverName + "__" + toolName;
        Integer failures = failureCounts.get(key);
        Long lastFailure = lastFailureTime.get(key);

        if (failures == null || lastFailure == null) {
            return false;
        }

        if (System.currentTimeMillis() - lastFailure > FAILURE_MEMORY_DURATION_MS) {
            failureCounts.remove(key);
            lastFailureTime.remove(key);
            return false;
        }

        return failures >= MAX_FAILURES_BEFORE_FALLBACK;
    }

    private void recordFailure(String toolName, String serverName) {
        String key = serverName + "__" + toolName;
        failureCounts.merge(key, 1, Integer::sum);
        lastFailureTime.put(key, System.currentTimeMillis());
        log.debug("记录工具失败: {} (失败次数: {})", key, failureCounts.get(key));
    }

    public void recordSuccess(String toolName, String serverName) {
        String key = serverName + "__" + toolName;
        failureCounts.remove(key);
        lastFailureTime.remove(key);
        log.debug("清除工具失败记录: {}", key);
    }

    public void registerAlternative(String originalTool, String originalServer, ToolAlternative alternative) {
        String key = originalServer + "__" + originalTool;
        toolAlternatives.computeIfAbsent(key, k -> new ArrayList<>()).add(alternative);
        log.info("注册工具替代: {} -> {}", key, alternative.getToolName());
    }

    private FallbackResult createNoAlternativeResult() {
        FallbackResult result = new FallbackResult();
        result.setHasAlternative(false);
        result.setStrategy("none");
        result.setReason("未找到可用的替代方案");
        return result;
    }

    public List<String> getFailedTools() {
        List<String> failed = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : failureCounts.entrySet()) {
            if (entry.getValue() >= MAX_FAILURES_BEFORE_FALLBACK) {
                failed.add(entry.getKey());
            }
        }
        return failed;
    }

    public void clearFailureRecords() {
        failureCounts.clear();
        lastFailureTime.clear();
        log.info("已清除所有工具失败记录");
    }
}
