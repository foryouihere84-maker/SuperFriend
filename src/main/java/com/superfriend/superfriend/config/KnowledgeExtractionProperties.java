package com.superfriend.superfriend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 知识提取配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "knowledge.extraction")
public class KnowledgeExtractionProperties {

    /**
     * 触发知识提取的最小消息数量
     */
    private int minMessages = 4;

    /**
     * 分析的最大消息数量（从最近的开始）
     */
    private int maxMessagesToAnalyze = 15;

    /**
     * 是否在对话结束时自动提取（推荐 true）
     */
    private boolean autoExtractOnFinalize = true;

    /**
     * 是否在每次保存时提取（不推荐，会产生重复）
     */
    private boolean autoExtractOnSave = false;

    /**
     * 触发知识提取的最小文本长度（字符数，包含用户输入和AI回复）
     */
    private int minTextLength = 2000;

    /**
     * 是否启用文本长度触发（与消息数量触发是"或"的关系）
     */
    private boolean enableTextLengthTrigger = true;

    /**
     * 是否启用知识提取功能
     */
    private boolean enabled = true;

    /**
     * 延迟提取的等待时间（秒）
     * 当用户关闭/切换对话时，会延迟一段时间再执行知识提取
     * 给用户返回对话的机会
     */
    private int delaySeconds = 60;
}
