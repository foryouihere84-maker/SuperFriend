package com.superfriend.superfriend.agent.tool;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Puppeteer 辅助服务
 * 提供智能的浏览器自动化策略，处理 JavaScript 渲染、反爬机制等
 */
@Slf4j
@Component
public class PuppeteerHelper {

    // 常见网站的特殊处理配置
    private static final Map<String, SiteConfig> SITE_CONFIGS = new HashMap<>();

    static {
        // 招聘网站
        SITE_CONFIGS.put("jobs.mihoyo.com", new SiteConfig(3000, true, "招聘信息"));
        SITE_CONFIGS.put("campus.mihoyo.com", new SiteConfig(3000, true, "校招信息"));
        SITE_CONFIGS.put("join.qq.com", new SiteConfig(4000, true, "腾讯招聘"));
        SITE_CONFIGS.put("talent.alibaba.com", new SiteConfig(4000, true, "阿里招聘"));
        SITE_CONFIGS.put("jobs.bytedance.com", new SiteConfig(4000, true, "字节招聘"));
        SITE_CONFIGS.put("career.huawei.com", new SiteConfig(4000, true, "华为招聘"));
        SITE_CONFIGS.put("nowcoder.com", new SiteConfig(3000, true, "牛客网"));
        SITE_CONFIGS.put("leetcode.cn", new SiteConfig(2000, true, "力扣"));

        // 社交/内容平台
        SITE_CONFIGS.put("zhihu.com", new SiteConfig(3000, true, "知乎"));
        SITE_CONFIGS.put("weibo.com", new SiteConfig(3000, true, "微博"));
        SITE_CONFIGS.put("bilibili.com", new SiteConfig(3000, true, "B站"));
        SITE_CONFIGS.put("xiaohongshu.com", new SiteConfig(4000, true, "小红书"));

        // 电商
        SITE_CONFIGS.put("taobao.com", new SiteConfig(5000, true, "淘宝"));
        SITE_CONFIGS.put("jd.com", new SiteConfig(4000, true, "京东"));
        SITE_CONFIGS.put("tmall.com", new SiteConfig(4000, true, "天猫"));
    }

    @Data
    public static class SiteConfig {
        private int waitTime;          // 等待时间（毫秒）
        private boolean needsLogin;    // 是否需要登录
        private String siteName;       // 网站名称

        public SiteConfig(int waitTime, boolean needsLogin, String siteName) {
            this.waitTime = waitTime;
            this.needsLogin = needsLogin;
            this.siteName = siteName;
        }
    }

    @Data
    public static class CrawlStrategy {
        private String strategy;           // 策略名称
        private List<String> steps;        // 执行步骤
        private Map<String, Object> params; // 推荐参数
        private String fallbackTool;       // 失败后的备选工具
        private List<String> tips;         // 提示信息
        private int estimatedTime;         // 预估时间（毫秒）
    }

    /**
     * 根据URL生成爬取策略
     */
    public CrawlStrategy generateStrategy(String url, String goal) {
        CrawlStrategy strategy = new CrawlStrategy();
        strategy.setSteps(new ArrayList<>());
        strategy.setParams(new HashMap<>());
        strategy.setTips(new ArrayList<>());

        // 获取网站配置
        SiteConfig config = getSiteConfig(url);

        // 根据目标选择策略
        String lowerGoal = goal != null ? goal.toLowerCase() : "";

        if (lowerGoal.contains("招聘") || lowerGoal.contains("岗位") || lowerGoal.contains("职位")) {
            strategy.setStrategy("招聘信息爬取");
            strategy.getSteps().add("1. 使用 puppeteer 导航到目标页面");
            strategy.getSteps().add("2. 等待 " + config.getWaitTime() + "ms 让页面加载");
            strategy.getSteps().add("3. 使用 get_content 获取页面内容");
            strategy.getSteps().add("4. 如果内容为空，尝试滚动加载更多");

            strategy.getParams().put("wait_time", config.getWaitTime());
            strategy.getParams().put("scroll", true);

            strategy.getTips().add("招聘网站通常需要 JavaScript 渲染");
            strategy.getTips().add("部分内容可能需要滚动才能加载");

            if (config.isNeedsLogin()) {
                strategy.getTips().add("⚠️ 该网站可能需要登录才能查看完整内容");
            }

        } else if (lowerGoal.contains("搜索") || lowerGoal.contains("查找")) {
            strategy.setStrategy("搜索结果爬取");
            strategy.getSteps().add("1. 使用 puppeteer 导航到搜索页面");
            strategy.getSteps().add("2. 等待搜索结果加载");
            strategy.getSteps().add("3. 获取搜索结果列表");
            strategy.getSteps().add("4. 可选：点击详情页获取更多信息");

            strategy.getParams().put("wait_time", config.getWaitTime());

        } else {
            strategy.setStrategy("通用页面爬取");
            strategy.getSteps().add("1. 使用 puppeteer 导航到目标页面");
            strategy.getSteps().add("2. 等待页面加载完成");
            strategy.getSteps().add("3. 获取页面内容");
        }

        // 设置备选方案
        strategy.setFallbackTool("fetch__fetch");
        strategy.setEstimatedTime(config.getWaitTime() + 2000);

        return strategy;
    }

    /**
     * 获取网站配置
     */
    public SiteConfig getSiteConfig(String url) {
        if (url == null) {
            return new SiteConfig(2000, false, "通用网站");
        }

        String lowerUrl = url.toLowerCase();
        for (Map.Entry<String, SiteConfig> entry : SITE_CONFIGS.entrySet()) {
            if (lowerUrl.contains(entry.getKey())) {
                return entry.getValue();
            }
        }

        // 默认配置
        return new SiteConfig(2000, false, "通用网站");
    }

    /**
     * 生成 Puppeteer 工具调用参数
     */
    public Map<String, Object> generatePuppeteerParams(String url, String goal) {
        Map<String, Object> params = new HashMap<>();
        params.put("url", url);

        SiteConfig config = getSiteConfig(url);
        params.put("wait_time", config.getWaitTime());

        // 根据目标调整参数
        if (goal != null) {
            String lowerGoal = goal.toLowerCase();

            // 如果需要滚动加载
            if (lowerGoal.contains("列表") || lowerGoal.contains("所有") || lowerGoal.contains("更多")) {
                params.put("scroll", true);
                params.put("scroll_times", 3);
            }

            // 如果需要截图
            if (lowerGoal.contains("截图") || lowerGoal.contains("图片")) {
                params.put("screenshot", true);
            }
        }

        return params;
    }

    /**
     * 判断是否应该使用 Puppeteer 而不是 fetch
     */
    public boolean shouldUsePuppeteer(String url, String previousResult) {
        // 1. 检查网站配置
        SiteConfig config = getSiteConfig(url);
        if (config.getWaitTime() > 2000) {
            return true;
        }

        // 2. 检查之前的结果
        if (previousResult != null) {
            String lower = previousResult.toLowerCase();

            // 如果之前的结果包含 JavaScript 提示
            if (lower.contains("javascript") || lower.contains("enable javascript")) {
                return true;
            }

            // 如果内容为空或太短
            if (previousResult.length() < 200) {
                return true;
            }

            // 如果检测到反爬
            if (lower.contains("验证") || lower.contains("captcha") || lower.contains("blocked")) {
                return true;
            }
        }

        // 3. 检查 URL 特征
        if (url != null) {
            String lowerUrl = url.toLowerCase();

            // SPA 应用
            if (lowerUrl.contains("#/") || lowerUrl.contains("/#/")) {
                return true;
            }

            // 已知需要 JS 的网站
            String[] jsSites = {"zhihu", "bilibili", "weibo", "taobao", "jd", "tmall"};
            for (String site : jsSites) {
                if (lowerUrl.contains(site)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * 获取 Puppeteer 工具调用序列
     */
    public List<Map<String, Object>> getToolSequence(String url, String goal) {
        List<Map<String, Object>> sequence = new ArrayList<>();

        CrawlStrategy strategy = generateStrategy(url, goal);

        // 1. 导航
        Map<String, Object> navigateParams = new HashMap<>();
        navigateParams.put("url", url);
        Map<String, Object> navigateStep = new HashMap<>();
        navigateStep.put("tool", "puppeteer__navigate");
        navigateStep.put("params", navigateParams);
        navigateStep.put("description", "导航到目标页面");
        sequence.add(navigateStep);

        // 2. 等待
        Map<String, Object> waitParams = new HashMap<>();
        waitParams.put("time", strategy.getParams().getOrDefault("wait_time", 2000));
        Map<String, Object> waitStep = new HashMap<>();
        waitStep.put("tool", "puppeteer__wait");
        waitStep.put("params", waitParams);
        waitStep.put("description", "等待页面加载");
        sequence.add(waitStep);

        // 3. 滚动（如果需要）
        if (Boolean.TRUE.equals(strategy.getParams().get("scroll"))) {
            Map<String, Object> scrollParams = new HashMap<>();
            scrollParams.put("times", strategy.getParams().getOrDefault("scroll_times", 3));
            Map<String, Object> scrollStep = new HashMap<>();
            scrollStep.put("tool", "puppeteer__infinite_scroll");
            scrollStep.put("params", scrollParams);
            scrollStep.put("description", "滚动加载更多内容");
            sequence.add(scrollStep);
        }

        // 4. 获取内容
        Map<String, Object> contentStep = new HashMap<>();
        contentStep.put("tool", "puppeteer__get_content");
        contentStep.put("params", new HashMap<>());
        contentStep.put("description", "获取页面内容");
        sequence.add(contentStep);

        return sequence;
    }

    /**
     * 生成用户友好的提示
     */
    public String generateUserTip(String url, String goal) {
        StringBuilder tip = new StringBuilder();

        SiteConfig config = getSiteConfig(url);
        CrawlStrategy strategy = generateStrategy(url, goal);

        tip.append("📌 网站信息：").append(config.getSiteName()).append("\n");
        tip.append("⏱️ 预计加载时间：").append(config.getWaitTime()).append("ms\n");
        tip.append("📋 策略：").append(strategy.getStrategy()).append("\n");

        if (!strategy.getTips().isEmpty()) {
            tip.append("\n💡 提示：\n");
            for (String t : strategy.getTips()) {
                tip.append("  ").append(t).append("\n");
            }
        }

        return tip.toString();
    }
}
