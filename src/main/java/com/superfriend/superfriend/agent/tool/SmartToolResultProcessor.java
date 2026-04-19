package com.superfriend.superfriend.agent.tool;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 智能工具结果处理器
 * 解决常见问题：内容截断、JavaScript渲染、反爬机制等
 */
@Slf4j
@Component
public class SmartToolResultProcessor {

    // 检测 JavaScript 渲染需求的模式
    private static final List<Pattern> JS_RENDER_PATTERNS = Arrays.asList(
        Pattern.compile("<script[^>]*>.*?</script>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL),
        Pattern.compile("ng-app|ng-controller|vue-app|react-root|data-reactroot", Pattern.CASE_INSENSITIVE),
        Pattern.compile("require\\(['\"].*['\"]\\)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("window\\.__INITIAL_STATE__|window\\.__PRELOADED_STATE__", Pattern.CASE_INSENSITIVE)
    );

    // 检测反爬机制的模式
    private static final List<Pattern> ANTI_CRAWL_PATTERNS = Arrays.asList(
        Pattern.compile("验证码|captcha|verify", Pattern.CASE_INSENSITIVE),
        Pattern.compile("访问频率|请求过于频繁|too many requests", Pattern.CASE_INSENSITIVE),
        Pattern.compile("请登录|请先登录|please login", Pattern.CASE_INSENSITIVE),
        Pattern.compile("访问被拒绝|access denied|forbidden", Pattern.CASE_INSENSITIVE),
        Pattern.compile("安全验证|安全检查|security check", Pattern.CASE_INSENSITIVE)
    );

    // 检测内容为空的模式
    private static final List<Pattern> EMPTY_CONTENT_PATTERNS = Arrays.asList(
        Pattern.compile("^\\s*<!DOCTYPE html>\\s*<html[^>]*>\\s*<head[^>]*>\\s*<title>([^<]*)</title>",
            Pattern.CASE_INSENSITIVE),
        Pattern.compile("<body[^>]*>\\s*</body>", Pattern.CASE_INSENSITIVE),
        Pattern.compile("<div[^>]*>\\s*</div>", Pattern.CASE_INSENSITIVE)
    );

    @Data
    public static class ProcessedResult {
        private String content;
        private boolean needsJavaScript;
        private boolean blockedByAntiCrawl;
        private boolean contentEmpty;
        private String suggestedAction;
        private String alternativeTool;
        private Map<String, Object> fixedParameters;
        private List<String> extractedLinks;
        private String summary;
        private boolean success;

        public boolean hasIssues() {
            return needsJavaScript || blockedByAntiCrawl || contentEmpty;
        }
    }

    /**
     * 处理工具结果，检测问题并提供修复建议
     */
    public ProcessedResult process(String toolName, String result, Map<String, Object> parameters) {
        ProcessedResult processed = new ProcessedResult();
        processed.setSuccess(true);
        processed.setContent(result);

        if (result == null || result.trim().isEmpty()) {
            processed.setContentEmpty(true);
            processed.setSuggestedAction("结果为空，请检查参数或尝试其他工具");
            processed.setSuccess(false);
            return processed;
        }

        // 1. 检测 JavaScript 渲染需求
        if (isFetchTool(toolName)) {
            processed.setNeedsJavaScript(needsJavaScriptRendering(result));
            if (processed.isNeedsJavaScript()) {
                processed.setSuggestedAction("页面需要 JavaScript 渲染，建议使用 puppeteer 工具");
                processed.setAlternativeTool("puppeteer__navigate");
                if (parameters != null && parameters.containsKey("url")) {
                    processed.setFixedParameters(createPuppeteerParams(parameters.get("url").toString()));
                }
            }
        }

        // 2. 检测反爬机制
        processed.setBlockedByAntiCrawl(detectedAntiCrawl(result));
        if (processed.isBlockedByAntiCrawl()) {
            processed.setSuggestedAction("检测到反爬机制，建议：1) 使用 puppeteer 模拟浏览器 2) 添加延迟 3) 更换来源");
        }

        // 3. 检测内容是否有效
        if (isFetchTool(toolName)) {
            processed.setContentEmpty(isContentEmpty(result));
            if (processed.isContentEmpty() && !processed.isNeedsJavaScript()) {
                processed.setSuggestedAction("页面内容为空或需要登录，请尝试其他来源");
            }
        }

        // 4. 提取链接（用于后续爬取）
        if (isFetchTool(toolName) || toolName.contains("search")) {
            processed.setExtractedLinks(extractLinks(result));
        }

        // 5. 生成摘要（如果内容过长）
        if (result.length() > 5000) {
            processed.setSummary(generateSmartSummary(result, 1000));
        }

        return processed;
    }

    /**
     * 智能截断内容，保留关键信息
     */
    public String smartTruncate(String content, int maxLength) {
        if (content == null || content.length() <= maxLength) {
            return content;
        }

        // 1. 尝试提取主要内容区域
        String mainContent = extractMainContent(content);
        if (mainContent.length() <= maxLength) {
            return mainContent;
        }

        // 2. 按段落分割，保留重要段落
        List<String> paragraphs = splitIntoParagraphs(mainContent);
        StringBuilder result = new StringBuilder();
        int currentLength = 0;

        for (String para : paragraphs) {
            if (currentLength + para.length() + 2 > maxLength - 200) {
                break;
            }
            if (isImportantParagraph(para)) {
                result.append(para).append("\n\n");
                currentLength += para.length() + 2;
            }
        }

        // 3. 添加截断标记
        if (result.length() < content.length()) {
            result.append("\n[内容已智能截断，保留了关键信息，总长度：")
                  .append(content.length()).append(" 字符]");
        }

        return result.toString();
    }

    /**
     * 检测是否需要 JavaScript 渲染
     */
    private boolean needsJavaScriptRendering(String html) {
        if (html == null) return false;

        // 检查是否有大量 script 标签但内容很少
        int scriptCount = countPattern(html, Pattern.compile("<script", Pattern.CASE_INSENSITIVE));
        int textLength = html.replaceAll("<[^>]+>", "").trim().length();

        if (scriptCount > 5 && textLength < 500) {
            return true;
        }

        // 检查常见的 SPA 框架标记
        for (Pattern pattern : JS_RENDER_PATTERNS) {
            if (pattern.matcher(html).find()) {
                return true;
            }
        }

        // 检查是否有 "loading" 或 "请启用 JavaScript" 等提示
        String lower = html.toLowerCase();
        if (lower.contains("enable javascript") ||
            lower.contains("请启用javascript") ||
            lower.contains("loading...") && textLength < 200) {
            return true;
        }

        return false;
    }

    /**
     * 检测反爬机制
     */
    private boolean detectedAntiCrawl(String content) {
        if (content == null) return false;

        for (Pattern pattern : ANTI_CRAWL_PATTERNS) {
            if (pattern.matcher(content).find()) {
                return true;
            }
        }

        return false;
    }

    /**
     * 检测内容是否为空
     */
    private boolean isContentEmpty(String html) {
        if (html == null || html.trim().isEmpty()) {
            return true;
        }

        // 提取纯文本
        String text = html.replaceAll("<[^>]+>", " ")
                         .replaceAll("\\s+", " ")
                         .trim();

        // 如果纯文本少于 100 字符，可能内容为空
        if (text.length() < 100) {
            return true;
        }

        // 检查是否只有标题和导航
        int bodyStart = html.toLowerCase().indexOf("<body");
        int bodyEnd = html.toLowerCase().indexOf("</body>");
        if (bodyStart > 0 && bodyEnd > bodyStart) {
            String body = html.substring(bodyStart, bodyEnd);
            String bodyText = body.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
            if (bodyText.length() < 200) {
                return true;
            }
        }

        return false;
    }

    /**
     * 提取主要内容
     */
    private String extractMainContent(String html) {
        if (html == null) return "";

        // 移除脚本和样式
        String content = html;
        content = content.replaceAll("<script[^>]*>[\\s\\S]*?</script>", "");
        content = content.replaceAll("<style[^>]*>[\\s\\S]*?</style>", "");
        content = content.replaceAll("<!--[^>]*-->", "");

        // 尝试提取 article 或 main 标签
        Pattern articlePattern = Pattern.compile("<article[^>]*>([\\s\\S]*?)</article>", Pattern.CASE_INSENSITIVE);
        Matcher articleMatcher = articlePattern.matcher(content);
        if (articleMatcher.find()) {
            return articleMatcher.group(1);
        }

        Pattern mainPattern = Pattern.compile("<main[^>]*>([\\s\\S]*?)</main>", Pattern.CASE_INSENSITIVE);
        Matcher mainMatcher = mainPattern.matcher(content);
        if (mainMatcher.find()) {
            return mainMatcher.group(1);
        }

        // 尝试提取 body 内容
        Pattern bodyPattern = Pattern.compile("<body[^>]*>([\\s\\S]*?)</body>", Pattern.CASE_INSENSITIVE);
        Matcher bodyMatcher = bodyPattern.matcher(content);
        if (bodyMatcher.find()) {
            return bodyMatcher.group(1);
        }

        return content;
    }

    /**
     * 分割成段落
     */
    private List<String> splitIntoParagraphs(String content) {
        List<String> paragraphs = new ArrayList<>();

        // 移除 HTML 标签
        String text = content.replaceAll("<[^>]+>", "\n");
        text = text.replaceAll("&nbsp;", " ");
        text = text.replaceAll("&[a-z]+;", "");

        // 按换行分割
        String[] lines = text.split("\\n+");
        for (String line : lines) {
            line = line.trim();
            if (line.length() > 20) {  // 忽略太短的行
                paragraphs.add(line);
            }
        }

        return paragraphs;
    }

    /**
     * 判断段落是否重要
     */
    private boolean isImportantParagraph(String para) {
        if (para == null || para.length() < 30) {
            return false;
        }

        String lower = para.toLowerCase();

        // 排除导航、页脚等
        if (lower.contains("copyright") || lower.contains("版权所有") ||
            lower.contains("all rights reserved") || lower.contains("备案")) {
            return false;
        }

        // 包含关键信息的段落更重要
        String[] importantKeywords = {"招聘", "岗位", "职位", "薪资", "要求", "职责",
            "job", "position", "salary", "requirement", "responsibility"};
        for (String keyword : importantKeywords) {
            if (lower.contains(keyword)) {
                return true;
            }
        }

        // 数字较多的段落可能包含重要信息
        long digitCount = para.chars().filter(Character::isDigit).count();
        if (digitCount > 5) {
            return true;
        }

        return para.length() > 100;  // 较长的段落可能更有价值
    }

    /**
     * 提取链接
     */
    private List<String> extractLinks(String content) {
        List<String> links = new ArrayList<>();
        if (content == null) return links;

        Pattern linkPattern = Pattern.compile("href=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
        Matcher matcher = linkPattern.matcher(content);

        Set<String> seen = new HashSet<>();
        while (matcher.find()) {
            String url = matcher.group(1);
            if (!seen.contains(url) && isValidUrl(url)) {
                links.add(url);
                seen.add(url);
            }
        }

        // 限制链接数量
        if (links.size() > 20) {
            return links.subList(0, 20);
        }

        return links;
    }

    /**
     * 生成智能摘要
     */
    private String generateSmartSummary(String content, int maxLength) {
        if (content == null || content.length() <= maxLength) {
            return content;
        }

        List<String> paragraphs = splitIntoParagraphs(content);
        StringBuilder summary = new StringBuilder();
        int currentLength = 0;

        // 优先包含重要段落
        for (String para : paragraphs) {
            if (currentLength >= maxLength) break;
            if (isImportantParagraph(para)) {
                if (summary.length() > 0) summary.append("\n\n");
                summary.append(para);
                currentLength += para.length();
            }
        }

        // 如果还不够，添加其他段落
        if (currentLength < maxLength / 2) {
            for (String para : paragraphs) {
                if (currentLength >= maxLength) break;
                if (!summary.toString().contains(para)) {
                    if (summary.length() > 0) summary.append("\n\n");
                    summary.append(para);
                    currentLength += para.length();
                }
            }
        }

        return summary.toString();
    }

    private boolean isFetchTool(String toolName) {
        return toolName != null && (toolName.contains("fetch") || toolName.contains("puppeteer"));
    }

    private boolean isValidUrl(String url) {
        if (url == null || url.isEmpty()) return false;
        if (url.startsWith("javascript:")) return false;
        if (url.startsWith("#")) return false;
        if (url.startsWith("data:")) return false;
        return url.startsWith("http://") || url.startsWith("https://") || url.startsWith("/");
    }

    private Map<String, Object> createPuppeteerParams(String url) {
        Map<String, Object> params = new HashMap<>();
        params.put("url", url);
        return params;
    }

    private int countPattern(String text, Pattern pattern) {
        int count = 0;
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            count++;
        }
        return count;
    }
}
