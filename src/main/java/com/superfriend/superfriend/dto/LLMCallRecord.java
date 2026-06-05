package com.superfriend.superfriend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * LLM 调用记录，用于监控和调试
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LLMCallRecord {
    /**
     * 调用唯一 ID
     */
    private String callId;

    /**
     * 会话 ID
     */
    private String sessionId;

    /**
     * 使用的模型
     */
    private String model;

    /**
     * API 地址（脱敏）
     */
    private String apiUrl;

    // ========== 请求信息 ==========

    /**
     * 请求时间
     */
    private LocalDateTime requestTime;

    /**
     * 完整消息列表
     */
    private List<Map<String, Object>> messages;

    /**
     * 工具定义
     */
    private List<Map<String, Object>> tools;

    /**
     * 温度参数
     */
    private Double temperature;

    /**
     * 最大 Token
     */
    private Integer maxTokens;

    // ========== 响应信息 ==========

    /**
     * 响应完成时间
     */
    private LocalDateTime responseTime;

    /**
     * 耗时（毫秒）
     */
    private Long durationMs;

    /**
     * 完成原因
     */
    private String finishReason;

    /**
     * 输入 Token 数
     */
    private Long promptTokens;

    /**
     * 输出 Token 数
     */
    private Long completionTokens;

    /**
     * 总 Token 数
     */
    private Long totalTokens;

    /**
     * 完整响应内容
     */
    private String responseContent;

    /**
     * 推理内容（DeepSeek 等）
     */
    private String reasoningContent;

    // ========== 状态信息 ==========

    /**
     * 是否成功
     */
    private Boolean success;

    /**
     * 错误信息
     */
    private String errorMessage;
}
