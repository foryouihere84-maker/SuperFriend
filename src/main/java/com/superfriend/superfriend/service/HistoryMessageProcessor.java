package com.superfriend.superfriend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 历史消息处理器
 * 统一处理历史消息的转换和过滤，确保tool_calls和tool消息正确配对
 */
@Slf4j
@Component
public class HistoryMessageProcessor {

    private final ObjectMapper objectMapper;

    public HistoryMessageProcessor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 处理历史消息，确保tool_calls和tool消息配对正确
     *
     * @param history 原始历史消息列表 (Map<String, String> 格式)
     * @return 处理后的消息列表 (Map<String, Object> 格式)
     */
    public List<Map<String, Object>> processHistory(List<Map<String, String>> history) {
        if (history == null || history.isEmpty()) {
            return new ArrayList<>();
        }

        // 转换为Object格式
        List<Map<String, Object>> messages = convertToObjects(history);

        // 清理不完整的tool调用链
        cleanIncompleteToolCalls(messages);

        return messages;
    }

    /**
     * 将 Map<String, String> 格式的消息转换为 Map<String, Object> 格式
     * 同时保留 tool_calls 和 tool_call_id 等字段
     */
    private List<Map<String, Object>> convertToObjects(List<Map<String, String>> history) {
        List<Map<String, Object>> messages = new ArrayList<>();

        for (Map<String, String> histMsg : history) {
            Map<String, Object> msg = new LinkedHashMap<>();
            String role = histMsg.get("role");

            if (role == null) {
                log.warn("历史消息缺少 role 字段，跳过");
                continue;
            }

            msg.put("role", role);
            msg.put("content", histMsg.get("content"));

            // 处理 assistant 消息中的 tool_calls
            if ("assistant".equals(role) && histMsg.containsKey("tool_calls")) {
                try {
                    String toolCallsStr = histMsg.get("tool_calls");
                    if (toolCallsStr != null && !toolCallsStr.isEmpty()) {
                        // 尝试解析为JSON
                        List<Map<String, Object>> toolCalls = objectMapper.readValue(
                            toolCallsStr,
                            new TypeReference<List<Map<String, Object>>>() {}
                        );
                        msg.put("tool_calls", toolCalls);
                    }
                } catch (Exception e) {
                    log.warn("解析 tool_calls 失败: {}", e.getMessage());
                }
            }

            // 处理 tool 消息中的 tool_call_id
            if ("tool".equals(role)) {
                if (histMsg.containsKey("tool_call_id")) {
                    msg.put("tool_call_id", histMsg.get("tool_call_id"));
                }
                if (histMsg.containsKey("name")) {
                    msg.put("name", histMsg.get("name"));
                }
            }

            messages.add(msg);
        }

        return messages;
    }

    /**
     * 清理不完整的 tool_calls 消息
     * 当 assistant 消息包含 tool_calls 时，必须有对应的 tool 响应消息
     * 否则会导致 API 调用失败
     * 
     * OpenAI API 要求：
     * 1. assistant 消息中的每个 tool_call_id 必须有对应的 tool 消息
     * 2. 这些 tool 消息必须紧随 assistant 消息之后（中间不能有其他消息）
     */
    @SuppressWarnings("unchecked")
    private void cleanIncompleteToolCalls(List<Map<String, Object>> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }

        List<Map<String, Object>> toRemove = new ArrayList<>();
        Set<String> processedToolCallIds = new HashSet<>();

        for (int i = 0; i < messages.size(); i++) {
            Map<String, Object> msg = messages.get(i);
            String role = (String) msg.get("role");

            if ("assistant".equals(role) && msg.containsKey("tool_calls")) {
                Object toolCallsObj = msg.get("tool_calls");
                if (toolCallsObj instanceof List) {
                    List<Map<String, Object>> toolCalls = (List<Map<String, Object>>) toolCallsObj;
                    Set<String> requiredIds = new HashSet<>();
                    for (Map<String, Object> tc : toolCalls) {
                        if (tc.containsKey("id")) {
                            requiredIds.add((String) tc.get("id"));
                        }
                    }

                    if (requiredIds.isEmpty()) {
                        continue;
                    }

                    Set<String> foundIds = new HashSet<>();
                    boolean hasBlockingMessage = false;

                    for (int j = i + 1; j < messages.size(); j++) {
                        Map<String, Object> nextMsg = messages.get(j);
                        String nextRole = (String) nextMsg.get("role");

                        if ("tool".equals(nextRole) && nextMsg.containsKey("tool_call_id")) {
                            String toolCallId = (String) nextMsg.get("tool_call_id");
                            if (requiredIds.contains(toolCallId)) {
                                foundIds.add(toolCallId);
                            }
                        } else if (!"tool".equals(nextRole)) {
                            if (!foundIds.equals(requiredIds)) {
                                hasBlockingMessage = true;
                            }
                            break;
                        }
                    }

                    if (!foundIds.equals(requiredIds) || hasBlockingMessage) {
                        Set<String> missingIds = new HashSet<>(requiredIds);
                        missingIds.removeAll(foundIds);
                        if (!missingIds.isEmpty()) {
                            log.warn("发现不完整的 tool_calls，缺失的 tool_call_id: {}", missingIds);
                        }
                        if (hasBlockingMessage) {
                            log.warn("发现 tool_calls 消息顺序错误，assistant 消息后存在非 tool 消息");
                        }
                        toRemove.add(msg);
                        log.info("移除不完整的 assistant 消息（含 tool_calls）");
                    } else {
                        processedToolCallIds.addAll(requiredIds);
                    }
                }
            }
        }

        List<Map<String, Object>> orphanedToolMessages = new ArrayList<>();
        for (Map<String, Object> msg : messages) {
            String role = (String) msg.get("role");
            if ("tool".equals(role) && msg.containsKey("tool_call_id")) {
                String toolCallId = (String) msg.get("tool_call_id");
                if (!processedToolCallIds.contains(toolCallId) && !isToolCallIdInRemainingMessages(messages, toRemove, toolCallId)) {
                    orphanedToolMessages.add(msg);
                    log.info("移除孤立的 tool 消息，tool_call_id: {}", toolCallId);
                }
            }
        }

        messages.removeAll(toRemove);
        messages.removeAll(orphanedToolMessages);
    }

    @SuppressWarnings("unchecked")
    private boolean isToolCallIdInRemainingMessages(List<Map<String, Object>> messages, 
                                                     List<Map<String, Object>> toRemove, 
                                                     String toolCallId) {
        for (Map<String, Object> msg : messages) {
            if (toRemove.contains(msg)) {
                continue;
            }
            String role = (String) msg.get("role");
            if ("assistant".equals(role) && msg.containsKey("tool_calls")) {
                Object toolCallsObj = msg.get("tool_calls");
                if (toolCallsObj instanceof List) {
                    List<Map<String, Object>> toolCalls = (List<Map<String, Object>>) toolCallsObj;
                    for (Map<String, Object> tc : toolCalls) {
                        if (toolCallId.equals(tc.get("id"))) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     * 过滤历史消息中的特定角色
     * 用于某些场景下只保留 user 和 assistant 消息
     */
    public List<Map<String, Object>> filterByRoles(List<Map<String, Object>> messages, Set<String> allowedRoles) {
        if (messages == null || allowedRoles == null) {
            return messages;
        }

        messages.removeIf(msg -> {
            String role = (String) msg.get("role");
            return role == null || !allowedRoles.contains(role);
        });

        return messages;
    }

    /**
     * 截断历史消息到指定长度
     * 保留最近的 N 条消息
     */
    public List<Map<String, Object>> truncateHistory(List<Map<String, Object>> messages, int maxMessages) {
        if (messages == null || messages.size() <= maxMessages) {
            return messages;
        }

        // 保留最后 maxMessages 条消息
        return new ArrayList<>(messages.subList(messages.size() - maxMessages, messages.size()));
    }
}
