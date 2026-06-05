package com.superfriend.superfriend.service;

import com.superfriend.superfriend.entity.MemoryPalace;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;

/**
 * 记忆触发分析器
 * 智能判断对话是否值得记忆，避免频繁提取垃圾信息
 */
@Slf4j
@Component
public class MemoryTriggerAnalyzer {

    // ==================== 触发词配置 ====================

    /** 显式触发词 - 用户明确要求记住 */
    private static final Set<String> EXPLICIT_TRIGGERS = new HashSet<>(Arrays.asList(
        "记住", "别忘了", "记得", "记下来", "帮我记", "先记着",
        "以后记住", "以后记得", "要记住", "要记得",
        "这是我的", "我是", "我叫", "我来自"
    ));

    /** 偏好表达词 */
    private static final Set<String> PREFERENCE_TRIGGERS = new HashSet<>(Arrays.asList(
        "我喜欢", "我讨厌", "我爱", "我恨", "我偏好", "我倾向",
        "我习惯", "我更", "我比较喜欢", "我比较讨厌",
        "对我来说", "我的风格", "我的习惯", "我的偏好",
        "我更喜欢", "我更倾向", "我一般", "我通常",
        "我不喜欢", "我不爱", "我不习惯"
    ));

    /** 重要决策词 */
    private static final Set<String> DECISION_TRIGGERS = new HashSet<>(Arrays.asList(
        "我决定", "我选择", "我定了", "就这个了", "就用这个",
        "我打算", "我准备", "我计划", "我想要",
        "我确定了", "我选定", "我采纳"
    ));

    /** 技能表达词 */
    private static final Set<String> SKILL_TRIGGERS = new HashSet<>(Arrays.asList(
        "我会", "我能", "我擅长", "我精通", "我熟练",
        "我熟悉", "我了解", "我学过", "我做过",
        "我的强项", "我的专长", "我的特长"
    ));

    /** 知识询问词 */
    private static final Set<String> QUESTION_PATTERNS = new HashSet<>(Arrays.asList(
        "什么是", "是什么", "什么叫", "怎么理解",
        "怎么", "如何", "怎样", "为什么", "为啥",
        "哪个", "哪些", "有什么", "有哪些", "是谁", "谁"
    ));

    /** 临时性信息词 - 不应该记忆 */
    private static final Set<String> EPHEMERAL_PATTERNS = new HashSet<>(Arrays.asList(
        "现在", "今天", "刚才", "暂时", "临时",
        "一会儿", "马上", "正在", "目前"
    ));

    /** 通用常识词 - 不值得记忆 */
    private static final Set<String> COMMON_KNOWLEDGE = new HashSet<>(Arrays.asList(
        "你好", "谢谢", "再见", "好的", "是的", "对的",
        "可以", "没问题", "怎么样", "如何"
    ));

    /** 情感词 */
    private static final Map<String, String> EMOTION_WORDS = new HashMap<>();
    static {
        EMOTION_WORDS.put("开心", "positive");
        EMOTION_WORDS.put("高兴", "positive");
        EMOTION_WORDS.put("喜欢", "positive");
        EMOTION_WORDS.put("满意", "positive");
        EMOTION_WORDS.put("感谢", "positive");
        EMOTION_WORDS.put("讨厌", "negative");
        EMOTION_WORDS.put("烦", "negative");
        EMOTION_WORDS.put("累", "negative");
        EMOTION_WORDS.put("失望", "negative");
        EMOTION_WORDS.put("难过", "negative");
        EMOTION_WORDS.put("生气", "negative");
    }

    // ==================== 核心分析方法 ====================

    /**
     * 分析对话是否值得记忆
     *
     * @param userMessage 用户消息
     * @param assistantReply 助手回复
     * @return 触发结果
     */
    public TriggerResult analyze(String userMessage, String assistantReply) {
        TriggerResult result = new TriggerResult();
        result.setShouldRemember(false);

        if (userMessage == null || userMessage.trim().isEmpty()) {
            return result;
        }

        String normalizedMsg = userMessage.trim();

        // 1. 检查是否为临时性信息（优先排除）
        if (isEphemeralInfo(normalizedMsg)) {
            result.setShouldRemember(false);
            result.setReason("临时性信息，不值得记忆");
            return result;
        }

        // 2. 检查是否为通用常识（排除）
        if (isCommonKnowledge(normalizedMsg)) {
            result.setShouldRemember(false);
            result.setReason("通用常识，无需记忆");
            return result;
        }

        // 3. 显式触发检测（最高优先级）
        if (containsExplicitTrigger(normalizedMsg)) {
            result.setShouldRemember(true);
            result.setMemoryType(MemoryPalace.TYPE_CORE);
            result.setCategory(detectCategory(normalizedMsg));
            result.setReason("用户显式要求记忆");
            result.setImportance(9);
            return result;
        }

        // 4. 偏好表达检测
        if (containsPreferenceExpression(normalizedMsg)) {
            result.setShouldRemember(true);
            result.setMemoryType(MemoryPalace.TYPE_IMPORTANT);
            result.setCategory(MemoryPalace.CATEGORY_PREFERENCE);
            result.setReason("用户表达偏好");
            result.setImportance(7);
            result.setEmotionTag(extractEmotionTag(normalizedMsg));
            return result;
        }

        // 5. 重要决策检测
        if (containsImportantDecision(normalizedMsg)) {
            result.setShouldRemember(true);
            result.setMemoryType(MemoryPalace.TYPE_IMPORTANT);
            result.setCategory(MemoryPalace.CATEGORY_DECISION);
            result.setReason("用户做出重要决策");
            result.setImportance(8);
            return result;
        }

        // 6. 技能表达检测
        if (containsSkillExpression(normalizedMsg)) {
            result.setShouldRemember(true);
            result.setMemoryType(MemoryPalace.TYPE_IMPORTANT);
            result.setCategory(MemoryPalace.CATEGORY_SKILL);
            result.setReason("用户表达技能水平");
            result.setImportance(7);
            return result;
        }

        // 7. 知识询问检测（需要判断答案价值）
        if (isKnowledgeQuestion(normalizedMsg)) {
            if (hasValuableAnswer(assistantReply)) {
                result.setShouldRemember(true);
                result.setMemoryType(MemoryPalace.TYPE_NORMAL);
                result.setCategory(MemoryPalace.CATEGORY_FACT);
                result.setReason("用户询问知识点并获得有价值答案");
                result.setImportance(6);
                return result;
            }
        }

        // 8. 情感强度检测
        double emotionIntensity = analyzeEmotionIntensity(normalizedMsg);
        if (emotionIntensity > 0.7) {
            result.setShouldRemember(true);
            result.setMemoryType(MemoryPalace.TYPE_NORMAL);
            result.setCategory(detectCategory(normalizedMsg));
            result.setReason("情感强度高");
            result.setImportance(6);
            result.setEmotionTag(extractEmotionTag(normalizedMsg));
            return result;
        }

        // 9. 实体重要性检测
        if (containsImportantEntity(normalizedMsg, assistantReply)) {
            result.setShouldRemember(true);
            result.setMemoryType(MemoryPalace.TYPE_NORMAL);
            result.setCategory(MemoryPalace.CATEGORY_FACT);
            result.setReason("包含重要实体信息");
            result.setImportance(5);
            return result;
        }

        // 默认不记忆
        result.setShouldRemember(false);
        result.setReason("未触发记忆条件");
        return result;
    }

    // ==================== 检测方法 ====================

    private boolean isEphemeralInfo(String message) {
        String lower = message.toLowerCase();
        // 短消息且包含临时词
        if (message.length() < 10) {
            for (String pattern : EPHEMERAL_PATTERNS) {
                if (lower.contains(pattern)) {
                    return true;
                }
            }
        }
        // 纯状态描述
        if (message.matches("^(我|在|正在).{0,20}$")) {
            return true;
        }
        return false;
    }

    private boolean isCommonKnowledge(String message) {
        String lower = message.toLowerCase().trim();
        // 纯问候语
        if (COMMON_KNOWLEDGE.contains(lower)) {
            return true;
        }
        // 太短的消息
        if (message.length() < 5) {
            return true;
        }
        return false;
    }

    private boolean containsExplicitTrigger(String message) {
        for (String trigger : EXPLICIT_TRIGGERS) {
            if (message.contains(trigger)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsPreferenceExpression(String message) {
        for (String trigger : PREFERENCE_TRIGGERS) {
            if (message.contains(trigger)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsImportantDecision(String message) {
        for (String trigger : DECISION_TRIGGERS) {
            if (message.contains(trigger)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsSkillExpression(String message) {
        for (String trigger : SKILL_TRIGGERS) {
            if (message.contains(trigger)) {
                return true;
            }
        }
        return false;
    }

    private boolean isKnowledgeQuestion(String message) {
        for (String pattern : QUESTION_PATTERNS) {
            if (message.contains(pattern)) {
                return true;
            }
        }
        // 问号结尾
        if (message.endsWith("?") || message.endsWith("？")) {
            return true;
        }
        return false;
    }

    private boolean hasValuableAnswer(String reply) {
        if (reply == null || reply.trim().isEmpty()) {
            return false;
        }
        // 答案足够长，说明有实质内容
        if (reply.length() > 100) {
            return true;
        }
        // 包含解释性内容
        if (reply.contains("是") || reply.contains("因为") || reply.contains("可以") ||
            reply.contains("方法") || reply.contains("步骤")) {
            return true;
        }
        return false;
    }

    private double analyzeEmotionIntensity(String message) {
        int emotionCount = 0;
        for (String word : EMOTION_WORDS.keySet()) {
            if (message.contains(word)) {
                emotionCount++;
            }
        }
        // 感叹号增强情感
        int exclamationCount = 0;
        for (char c : message.toCharArray()) {
            if (c == '!' || c == '！') {
                exclamationCount++;
            }
        }
        return Math.min(1.0, emotionCount * 0.3 + exclamationCount * 0.2);
    }

    private String extractEmotionTag(String message) {
        for (Map.Entry<String, String> entry : EMOTION_WORDS.entrySet()) {
            if (message.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return "neutral";
    }

    private String detectCategory(String message) {
        if (containsPreferenceExpression(message)) {
            return MemoryPalace.CATEGORY_PREFERENCE;
        }
        if (containsSkillExpression(message)) {
            return MemoryPalace.CATEGORY_SKILL;
        }
        if (containsImportantDecision(message)) {
            return MemoryPalace.CATEGORY_DECISION;
        }
        if (isKnowledgeQuestion(message)) {
            return MemoryPalace.CATEGORY_FACT;
        }
        return MemoryPalace.CATEGORY_EXPERIENCE;
    }

    private boolean containsImportantEntity(String userMessage, String assistantReply) {
        // 检查是否包含专有名词（大写字母开头、书名号、引号等）
        Pattern properNoun = Pattern.compile("[《「『\"].+[》」』\"]");
        if (properNoun.matcher(userMessage).find()) {
            return true;
        }
        if (assistantReply != null && assistantReply.length() > 500) {
            return true;
        }
        return false;
    }

    // ==================== 结果类 ====================

    @Data
    public static class TriggerResult {
        /** 是否值得记忆 */
        private boolean shouldRemember;
        /** 记忆类型 */
        private String memoryType;
        /** 分类 */
        private String category;
        /** 触发原因 */
        private String reason;
        /** 重要性 1-10 */
        private int importance;
        /** 情感标签 */
        private String emotionTag;
        /** 提取的标题 */
        private String title;
        /** 提取的内容 */
        private String content;
        /** 关键词 */
        private List<String> keywords;
    }
}
