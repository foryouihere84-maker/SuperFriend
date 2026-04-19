package com.superfriend.superfriend.agent.tool;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 反反爬策略服务
 * 提供多层降级的爬取策略，最大化成功率
 */
@Slf4j
@Component
public class AntiDetectionService {

    // 浏览器 User-Agent 池
    private static final List<String> USER_AGENTS = Arrays.asList(
        // Chrome on Windows
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36",
        // Chrome on Mac
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        // Firefox on Windows
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:121.0) Gecko/20100101 Firefox/121.0",
        // Edge
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36 Edg/120.0.0.0",
        // Safari on Mac
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.2 Safari/605.1.15"
    );

    // 高风险网站（已知有强反爬）
    private static final Set<String> HIGH_RISK_DOMAINS = new HashSet<>(Arrays.asList(
        "zhihu.com", "weibo.com", "taobao.com", "jd.com", "tmall.com",
        "xiaohongshu.com", "douyin.com", "bilibili.com",
        "linkedin.com", "twitter.com", "instagram.com",
        "cloudflare.com", "akamai.com"
    ));

    // 中等风险网站
    private static final Set<String> MEDIUM_RISK_DOMAINS = new HashSet<>(Arrays.asList(
        "mihoyo.com", "bytedance.com", "alibaba.com",
        "qq.com", "163.com", "sina.com.cn",
        "lagou.com", "zhipin.com", "51job.com"
    ));

    @Data
    public static class CrawlConfig {
        // 基础配置
        private String userAgent;
        private int waitTime;
        private int retryCount;
        private long retryDelay;

        // 行为模拟
        private boolean simulateHuman;
        private boolean randomDelay;
        private boolean scrollBeforeRead;
        private int scrollTimes;

        // 隐身配置
        private boolean disableWebdriverFlag;
        private boolean randomizeFingerprint;
        private boolean blockMedia;

        // 请求配置
        private Map<String, String> headers;
        private List<String> cookies;

        // 降级策略
        private List<String> fallbackStrategies;
    }

    @Data
    public static class CrawlResult {
        private boolean success;
        private String content;
        private String error;
        private String usedStrategy;
        private int attemptCount;
        private List<String> attemptedStrategies;
        private Map<String, String> diagnostics;
    }

    /**
     * 获取针对特定 URL 的爬取配置
     */
    public CrawlConfig getConfig(String url, String goal) {
        CrawlConfig config = new CrawlConfig();

        // 随机 User-Agent
        config.setUserAgent(getRandomUserAgent());

        // 根据风险等级设置配置
        String domain = extractDomain(url);
        RiskLevel risk = assessRisk(domain);

        switch (risk) {
            case HIGH:
                config.setWaitTime(5000);
                config.setRetryCount(3);
                config.setRetryDelay(3000);
                config.setSimulateHuman(true);
                config.setRandomDelay(true);
                config.setScrollBeforeRead(true);
                config.setScrollTimes(5);
                config.setDisableWebdriverFlag(true);
                config.setRandomizeFingerprint(true);
                config.setBlockMedia(true);
                break;

            case MEDIUM:
                config.setWaitTime(3000);
                config.setRetryCount(2);
                config.setRetryDelay(2000);
                config.setSimulateHuman(true);
                config.setRandomDelay(true);
                config.setScrollBeforeRead(true);
                config.setScrollTimes(3);
                config.setDisableWebdriverFlag(true);
                break;

            case LOW:
            default:
                config.setWaitTime(2000);
                config.setRetryCount(1);
                config.setRetryDelay(1000);
                config.setSimulateHuman(false);
                config.setRandomDelay(false);
                config.setScrollBeforeRead(false);
                break;
        }

        // 设置降级策略
        config.setFallbackStrategies(generateFallbackStrategies(risk, goal));

        // 设置请求头
        config.setHeaders(generateHeaders(config.getUserAgent(), url));

        return config;
    }

    /**
     * 生成 Puppeteer 启动参数
     */
    public Map<String, Object> generatePuppeteerLaunchArgs(CrawlConfig config) {
        Map<String, Object> args = new HashMap<>();

        // 基础参数
        List<String> chromeArgs = new ArrayList<>();
        chromeArgs.add("--no-sandbox");
        chromeArgs.add("--disable-setuid-sandbox");
        chromeArgs.add("--disable-dev-shm-usage");
        chromeArgs.add("--disable-accelerated-2d-canvas");
        chromeArgs.add("--no-first-run");
        chromeArgs.add("--no-zygote");
        chromeArgs.add("--disable-gpu");

        if (config.isBlockMedia()) {
            // 阻止图片、字体、样式表加载，加快速度
            // 这些需要在页面级别设置
        }

        args.put("args", chromeArgs);
        args.put("headless", true);
        args.put("ignoreHTTPSErrors", true);

        return args;
    }

    /**
     * 生成页面级别的反检测脚本
     */
    public String generateStealthScript(CrawlConfig config) {
        StringBuilder script = new StringBuilder();

        // 1. 隐藏 webdriver 标志
        if (config.isDisableWebdriverFlag()) {
            script.append(
                "// 删除 webdriver 标志\n" +
                "Object.defineProperty(navigator, 'webdriver', { get: () => undefined });\n\n" +
                "// 删除自动化标志\n" +
                "delete window.__webdriver_evaluate;\n" +
                "delete window.__selenium_evaluate;\n" +
                "delete window.__webdriver_script_function;\n" +
                "delete window.__webdriver_script_func;\n" +
                "delete window.__webdriver_script_fn;\n\n" +
                "// 修改 plugins\n" +
                "Object.defineProperty(navigator, 'plugins', {\n" +
                "    get: () => [1, 2, 3, 4, 5]\n" +
                "});\n\n" +
                "// 修改 languages\n" +
                "Object.defineProperty(navigator, 'languages', {\n" +
                "    get: () => ['zh-CN', 'zh', 'en']\n" +
                "});\n\n" +
                "// 隐藏 Chrome 自动化标志\n" +
                "window.chrome = { runtime: {} };\n"
            );
        }

        // 2. 随机化指纹
        if (config.isRandomizeFingerprint()) {
            script.append(
                "// 随机化屏幕信息\n" +
                "Object.defineProperty(screen, 'width', { get: () => 1920 });\n" +
                "Object.defineProperty(screen, 'height', { get: () => 1080 });\n" +
                "Object.defineProperty(screen, 'availWidth', { get: () => 1920 });\n" +
                "Object.defineProperty(screen, 'availHeight', { get: () => 1040 });\n\n" +
                "// 随机化时区\n" +
                "const originalDateTimezone = Date.prototype.getTimezoneOffset;\n" +
                "Date.prototype.getTimezoneOffset = function() {\n" +
                "    return -480; // 东八区\n" +
                "};\n\n" +
                "// WebGL 指纹随机化\n" +
                "const getParameter = WebGLRenderingContext.prototype.getParameter;\n" +
                "WebGLRenderingContext.prototype.getParameter = function(parameter) {\n" +
                "    if (parameter === 37445) return 'Intel Inc.';\n" +
                "    if (parameter === 37446) return 'Intel Iris OpenGL Engine';\n" +
                "    return getParameter.apply(this, arguments);\n" +
                "};\n"
            );
        }

        // 3. 模拟人类行为
        if (config.isSimulateHuman()) {
            script.append(
                "// 模拟鼠标移动轨迹\n" +
                "window.humanMouseMove = async function(targetX, targetY) {\n" +
                "    const steps = 20 + Math.floor(Math.random() * 10);\n" +
                "    const startX = Math.random() * window.innerWidth;\n" +
                "    const startY = Math.random() * window.innerHeight;\n\n" +
                "    for (let i = 0; i <= steps; i++) {\n" +
                "        const progress = i / steps;\n" +
                "        const x = startX + (targetX - startX) * progress + (Math.random() - 0.5) * 10;\n" +
                "        const y = startY + (targetY - startY) * progress + (Math.random() - 0.5) * 10;\n\n" +
                "        const event = new MouseEvent('mousemove', {\n" +
                "            clientX: x,\n" +
                "            clientY: y,\n" +
                "            bubbles: true\n" +
                "        });\n" +
                "        document.dispatchEvent(event);\n" +
                "        await new Promise(r => setTimeout(r, 10 + Math.random() * 20));\n" +
                "    }\n" +
                "};\n"
            );
        }

        return script.toString();
    }

    /**
     * 生成人类行为模拟脚本
     */
    public String generateHumanBehaviorScript() {
        return
            "// 随机滚动\n" +
            "async function humanScroll() {\n" +
            "    const scrollHeight = document.body.scrollHeight;\n" +
            "    const viewportHeight = window.innerHeight;\n" +
            "    const maxScroll = scrollHeight - viewportHeight;\n\n" +
            "    let currentScroll = 0;\n" +
            "    while (currentScroll < maxScroll * 0.8) {\n" +
            "        const scrollAmount = 100 + Math.random() * 200;\n" +
            "        currentScroll += scrollAmount;\n" +
            "        window.scrollTo({\n" +
            "            top: currentScroll,\n" +
            "            behavior: 'smooth'\n" +
            "        });\n" +
            "        await new Promise(r => setTimeout(r, 300 + Math.random() * 500));\n" +
            "    }\n" +
            "}\n\n" +
            "// 随机点击\n" +
            "function randomClick() {\n" +
            "    const elements = document.querySelectorAll('a, button, input');\n" +
            "    if (elements.length > 0) {\n" +
            "        const randomElement = elements[Math.floor(Math.random() * elements.length)];\n" +
            "        randomElement.dispatchEvent(new MouseEvent('mouseover', { bubbles: true }));\n" +
            "    }\n" +
            "}\n\n" +
            "// 执行\n" +
            "humanScroll();\n" +
            "setTimeout(randomClick, 1000);\n";
    }

    /**
     * 生成降级策略列表
     */
    private List<String> generateFallbackStrategies(RiskLevel risk, String goal) {
        List<String> strategies = new ArrayList<>();

        // 策略1: 标准爬取
        strategies.add("puppeteer_standard");

        // 策略2: 隐身模式
        if (risk != RiskLevel.LOW) {
            strategies.add("puppeteer_stealth");
        }

        // 策略3: 模拟人类行为
        if (risk == RiskLevel.HIGH) {
            strategies.add("puppeteer_human_simulation");
        }

        // 策略4: 简单 fetch（作为最后手段）
        strategies.add("fetch_simple");

        // 策略5: 搜索替代来源
        if (goal != null && (goal.contains("招聘") || goal.contains("岗位"))) {
            strategies.add("search_alternative_sources");
        }

        return strategies;
    }

    /**
     * 生成请求头
     */
    private Map<String, String> generateHeaders(String userAgent, String referer) {
        Map<String, String> headers = new LinkedHashMap<>();

        headers.put("User-Agent", userAgent);
        headers.put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8");
        headers.put("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
        headers.put("Accept-Encoding", "gzip, deflate, br");
        headers.put("Connection", "keep-alive");
        headers.put("Upgrade-Insecure-Requests", "1");
        headers.put("Sec-Fetch-Dest", "document");
        headers.put("Sec-Fetch-Mode", "navigate");
        headers.put("Sec-Fetch-Site", "none");
        headers.put("Sec-Fetch-User", "?1");
        headers.put("Cache-Control", "max-age=0");

        if (referer != null) {
            headers.put("Referer", referer);
        }

        return headers;
    }

    /**
     * 评估网站风险等级
     */
    public RiskLevel assessRisk(String domain) {
        if (domain == null) {
            return RiskLevel.LOW;
        }

        String lowerDomain = domain.toLowerCase();

        for (String highRisk : HIGH_RISK_DOMAINS) {
            if (lowerDomain.contains(highRisk)) {
                return RiskLevel.HIGH;
            }
        }

        for (String mediumRisk : MEDIUM_RISK_DOMAINS) {
            if (lowerDomain.contains(mediumRisk)) {
                return RiskLevel.MEDIUM;
            }
        }

        return RiskLevel.LOW;
    }

    /**
     * 获取随机 User-Agent
     */
    public String getRandomUserAgent() {
        return USER_AGENTS.get(new Random().nextInt(USER_AGENTS.size()));
    }

    /**
     * 提取域名
     */
    public String extractDomain(String url) {
        if (url == null) return null;
        try {
            String cleanUrl = url.replace("https://", "").replace("http://", "");
            int slashIndex = cleanUrl.indexOf('/');
            if (slashIndex > 0) {
                return cleanUrl.substring(0, slashIndex);
            }
            return cleanUrl;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 生成随机延迟
     */
    public long getRandomDelay(long baseDelay) {
        Random random = new Random();
        // 增加 0-50% 的随机延迟
        return baseDelay + random.nextInt((int) (baseDelay / 2));
    }

    /**
     * 获取替代数据源建议
     */
    public List<String> getAlternativeSources(String originalUrl, String goal) {
        List<String> alternatives = new ArrayList<>();

        String domain = extractDomain(originalUrl);
        String lowerGoal = goal != null ? goal.toLowerCase() : "";

        // 招聘类
        if (lowerGoal.contains("招聘") || lowerGoal.contains("岗位") || lowerGoal.contains("求职")) {
            if (!domain.contains("zhipin")) alternatives.add("https://www.zhipin.com");
            if (!domain.contains("lagou")) alternatives.add("https://www.lagou.com");
            if (!domain.contains("51job")) alternatives.add("https://www.51job.com");
            if (!domain.contains("liepin")) alternatives.add("https://www.liepin.com");
            if (!domain.contains("nowcoder")) alternatives.add("https://www.nowcoder.com/jobs");
        }

        // 产品/价格类
        if (lowerGoal.contains("价格") || lowerGoal.contains("产品")) {
            alternatives.add("使用搜索引擎查找相关信息");
            alternatives.add("查看第三方评测网站");
        }

        // 新闻类
        if (lowerGoal.contains("新闻") || lowerGoal.contains("资讯")) {
            alternatives.add("使用搜索引擎查找相关新闻");
            alternatives.add("查看官方公众号或微博");
        }

        return alternatives;
    }

    public enum RiskLevel {
        LOW,    // 低风险：普通网站
        MEDIUM, // 中风险：有一定反爬
        HIGH    // 高风险：强反爬机制
    }
}
