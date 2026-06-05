package com.superfriend.superfriend.service;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 上下文摘要服务
 *
 * 【新增】用于优化步骤间上下文传递，避免关键信息丢失
 */
@Slf4j
@Service
public class ContextSummarizerService {

    // 关键信息模式
    private static final String[] KEY_PATTERNS = {
        "结果", "答案", "数据", "信息", "找到", "发现", "获取", "成功", "完成",
        "result", "answer", "data", "found", "success", "completed"
    };

    // 错误信息模式
    private static final String[] ERROR_PATTERNS = {
        "错误", "失败", "异常", "无法", "未找到", "不存在",
        "error", "failed", "exception", "unable", "not found"
    };

    /**
     * 智能摘要步骤结果
     *
     * @param content 原始内容
     * @param maxLength 最大长度
     * @return 摘要后的内容
     */
    public String summarizeStepResult(String content, int maxLength) {
        if (content == null || content.isEmpty()) {
            return "";
        }

        if (content.length() <= maxLength) {
            return content;
        }

        // 1. 提取关键信息
        List<String> keySentences = extractKeySentences(content);

        // 2. 构建摘要
        StringBuilder summary = new StringBuilder();
        summary.append("【关键信息摘要】\n");

        for (String sentence : keySentences) {
            if (summary.length() + sentence.length() + 1 < maxLength * 0.7) {
                summary.append("- ").append(sentence).append("\n");
            }
        }

        // 3. 添加原始内容的前后部分
        int remaining = maxLength - summary.length() - 50;
        if (remaining > 100) {
            int headLength = remaining / 2;
            int tailLength = remaining / 2;

            summary.append("\n【开头部分】\n");
            summary.append(content.substring(0, Math.min(headLength, content.length()))).append("...\n");

            if (content.length() > headLength + tailLength) {
                summary.append("\n【结尾部分】\n");
                summary.append("...").append(content.substring(content.length() - tailLength));
            }
        }

        return summary.toString();
    }

    /**
     * 提取关键句子
     */
    private List<String> extractKeySentences(String content) {
        List<String> keySentences = new ArrayList<>();

        // 按句子分割
        String[] sentences = content.split("[。！？\n.!?]");

        for (String sentence : sentences) {
            String trimmed = sentence.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            // 检查是否包含关键信息
            for (String pattern : KEY_PATTERNS) {
                if (trimmed.toLowerCase().contains(pattern.toLowerCase())) {
                    // 避免重复添加
                    if (!containsSimilar(keySentences, trimmed)) {
                        keySentences.add(trimmed);
                    }
                    break;
                }
            }
        }

        // 限制数量
        if (keySentences.size() > 5) {
            return keySentences.subList(0, 5);
        }

        return keySentences;
    }

    /**
     * 检查是否已包含相似内容
     */
    private boolean containsSimilar(List<String> sentences, String newSentence) {
        for (String existing : sentences) {
            // 简单的相似度检查
            if (existing.contains(newSentence) || newSentence.contains(existing)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 构建步骤上下文
     *
     * @param originalRequest 原始请求
     * @param previousSteps 前序步骤结果
     * @param currentStep 当前步骤描述
     * @param maxContextLength 最大上下文长度
     * @return 构建的上下文
     */
    public String buildStepContext(String originalRequest,
                                    Map<Integer, String> previousSteps,
                                    String currentStep,
                                    int maxContextLength) {
        StringBuilder context = new StringBuilder();

        // 1. 添加原始任务
        context.append("=== 原始任务 ===\n");
        context.append(originalRequest).append("\n\n");

        // 2. 添加前序步骤摘要
        if (previousSteps != null && !previousSteps.isEmpty()) {
            context.append("=== 已完成步骤摘要 ===\n");

            for (Map.Entry<Integer, String> entry : previousSteps.entrySet()) {
                int stepNum = entry.getKey();
                String stepResult = entry.getValue();

                // 智能摘要每个步骤
                String summary = summarizeStepResult(stepResult, 500);
                context.append("步骤 ").append(stepNum).append(": ").append(summary).append("\n\n");

                // 检查长度限制
                if (context.length() > maxContextLength * 0.7) {
                    context.append("... (更多步骤已省略)\n");
                    break;
                }
            }
        }

        // 3. 添加当前步骤
        context.append("=== 当前步骤 ===\n");
        context.append(currentStep).append("\n");

        return context.toString();
    }

    /**
     * 提取关键数据
     *
     * @param content 内容
     * @return 关键数据映射
     */
    public Map<String, Object> extractKeyData(String content) {
        Map<String, Object> keyData = new LinkedHashMap<>();

        if (content == null || content.isEmpty()) {
            return keyData;
        }

        // 1. 提取 URL
        List<String> urls = extractUrls(content);
        if (!urls.isEmpty()) {
            keyData.put("urls", urls);
        }

        // 2. 提取数字
        List<String> numbers = extractNumbers(content);
        if (!numbers.isEmpty()) {
            keyData.put("numbers", numbers);
        }

        // 3. 提取日期
        List<String> dates = extractDates(content);
        if (!dates.isEmpty()) {
            keyData.put("dates", dates);
        }

        // 4. 提取错误信息
        List<String> errors = extractErrors(content);
        if (!errors.isEmpty()) {
            keyData.put("errors", errors);
        }

        return keyData;
    }

    private List<String> extractUrls(String content) {
        List<String> urls = new ArrayList<>();
        Pattern pattern = Pattern.compile("https?://[\\w\\-._~:/?#[\\]@!$&'()*+,;=%]+");
        Matcher matcher = pattern.matcher(content);
        while (matcher.find()) {
            urls.add(matcher.group());
        }
        return urls;
    }

    private List<String> extractNumbers(String content) {
        List<String> numbers = new ArrayList<>();
        Pattern pattern = Pattern.compile("\\d+(?:\\.\\d+)?");
        Matcher matcher = pattern.matcher(content);
        int count = 0;
        while (matcher.find() && count < 10) {
            numbers.add(matcher.group());
            count++;
        }
        return numbers;
    }

    private List<String> extractDates(String content) {
        List<String> dates = new ArrayList<>();
        Pattern pattern = Pattern.compile("\\d{4}[-/]\\d{1,2}[-/]\\d{1,2}");
        Matcher matcher = pattern.matcher(content);
        while (matcher.find()) {
            dates.add(matcher.group());
        }
        return dates;
    }

    private List<String> extractErrors(String content) {
        List<String> errors = new ArrayList<>();
        String lowerContent = content.toLowerCase();

        for (String pattern : ERROR_PATTERNS) {
            int index = lowerContent.indexOf(pattern.toLowerCase());
            if (index >= 0) {
                // 提取错误上下文
                int start = Math.max(0, index - 20);
                int end = Math.min(content.length(), index + pattern.length() + 50);
                String errorContext = content.substring(start, end);
                if (!errors.contains(errorContext)) {
                    errors.add(errorContext);
                }
            }
        }

        return errors;
    }

    /**
     * 合并多个步骤结果
     *
     * @param stepResults 步骤结果列表
     * @param maxLength 最大长度
     * @return 合并后的结果
     */
    public String mergeStepResults(List<String> stepResults, int maxLength) {
        if (stepResults == null || stepResults.isEmpty()) {
            return "";
        }

        StringBuilder merged = new StringBuilder();
        int totalLength = stepResults.stream().mapToInt(String::length).sum();

        if (totalLength <= maxLength) {
            // 直接合并
            for (int i = 0; i < stepResults.size(); i++) {
                merged.append("【步骤 ").append(i + 1).append("】\n");
                merged.append(stepResults.get(i)).append("\n\n");
            }
        } else {
            // 需要压缩
            int eachMaxLength = maxLength / stepResults.size();

            for (int i = 0; i < stepResults.size(); i++) {
                String result = stepResults.get(i);
                merged.append("【步骤 ").append(i + 1).append(" 摘要】\n");
                merged.append(summarizeStepResult(result, eachMaxLength)).append("\n\n");
            }
        }

        return merged.toString();
    }

    /**
     * 上下文摘要结果
     */
    @Data
    public static class SummaryResult {
        private String summary;
        private List<String> keyPoints;
        private Map<String, Object> keyData;
        private int originalLength;
        private int summaryLength;
        private double compressionRatio;

        public SummaryResult(String summary, int originalLength) {
            this.summary = summary;
            this.originalLength = originalLength;
            this.summaryLength = summary.length();
            this.compressionRatio = (double) summaryLength / originalLength;
            this.keyPoints = new ArrayList<>();
            this.keyData = new HashMap<>();
        }
    }
}
