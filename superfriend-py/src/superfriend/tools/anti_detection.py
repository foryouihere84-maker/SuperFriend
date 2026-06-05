from __future__ import annotations

import random
import structlog
from typing import Any
from enum import Enum

logger = structlog.get_logger()

USER_AGENTS = [
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36",
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:121.0) Gecko/20100101 Firefox/121.0",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36 Edg/120.0.0.0",
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.2 Safari/605.1.15",
]

HIGH_RISK_DOMAINS = {
    "zhihu.com", "weibo.com", "taobao.com", "jd.com", "tmall.com",
    "xiaohongshu.com", "douyin.com", "bilibili.com",
    "linkedin.com", "twitter.com", "instagram.com",
    "cloudflare.com", "akamai.com",
}

MEDIUM_RISK_DOMAINS = {
    "mihoyo.com", "bytedance.com", "alibaba.com",
    "qq.com", "163.com", "sina.com.cn",
    "lagou.com", "zhipin.com", "51job.com",
}


class RiskLevel(str, Enum):
    LOW = "low"
    MEDIUM = "medium"
    HIGH = "high"


class CrawlConfig:
    def __init__(self):
        self.user_agent: str = ""
        self.wait_time: int = 2000
        self.retry_count: int = 1
        self.retry_delay: int = 1000
        self.simulate_human: bool = False
        self.random_delay: bool = False
        self.scroll_before_read: bool = False
        self.scroll_times: int = 3
        self.disable_webdriver_flag: bool = False
        self.randomize_fingerprint: bool = False
        self.block_media: bool = False
        self.headers: dict[str, str] = {}
        self.cookies: list[str] = []
        self.fallback_strategies: list[str] = []


class CrawlResult:
    def __init__(self):
        self.success: bool = False
        self.content: str = ""
        self.error: str = ""
        self.used_strategy: str = ""
        self.attempt_count: int = 0
        self.attempted_strategies: list[str] = []
        self.diagnostics: dict[str, str] = {}


class AntiDetectionService:
    def get_config(self, url: str, goal: str = "") -> CrawlConfig:
        config = CrawlConfig()
        config.user_agent = self.get_random_user_agent()
        domain = self.extract_domain(url)
        risk = self.assess_risk(domain)

        if risk == RiskLevel.HIGH:
            config.wait_time = 5000
            config.retry_count = 3
            config.retry_delay = 3000
            config.simulate_human = True
            config.random_delay = True
            config.scroll_before_read = True
            config.scroll_times = 5
            config.disable_webdriver_flag = True
            config.randomize_fingerprint = True
            config.block_media = True
        elif risk == RiskLevel.MEDIUM:
            config.wait_time = 3000
            config.retry_count = 2
            config.retry_delay = 2000
            config.simulate_human = True
            config.random_delay = True
            config.scroll_before_read = True
            config.scroll_times = 3
            config.disable_webdriver_flag = True
        else:
            config.wait_time = 2000
            config.retry_count = 1
            config.retry_delay = 1000

        config.fallback_strategies = self._generate_fallback_strategies(risk, goal)
        config.headers = self._generate_headers(config.user_agent, url)
        return config

    def generate_puppeteer_launch_args(self, config: CrawlConfig) -> dict[str, Any]:
        chrome_args = [
            "--no-sandbox",
            "--disable-setuid-sandbox",
            "--disable-dev-shm-usage",
            "--disable-accelerated-2d-canvas",
            "--no-first-run",
            "--no-zygote",
            "--disable-gpu",
        ]
        return {
            "args": chrome_args,
            "headless": True,
            "ignoreHTTPSErrors": True,
        }

    def generate_stealth_script(self, config: CrawlConfig) -> str:
        parts = []

        if config.disable_webdriver_flag:
            parts.append("""
Object.defineProperty(navigator, 'webdriver', { get: () => undefined });
delete window.__webdriver_evaluate;
delete window.__selenium_evaluate;
delete window.__webdriver_script_function;
delete window.__webdriver_script_func;
delete window.__webdriver_script_fn;

Object.defineProperty(navigator, 'plugins', { get: () => [1, 2, 3, 4, 5] });
Object.defineProperty(navigator, 'languages', { get: () => ['zh-CN', 'zh', 'en'] });
window.chrome = { runtime: {} };
""")

        if config.randomize_fingerprint:
            parts.append("""
Object.defineProperty(screen, 'width', { get: () => 1920 });
Object.defineProperty(screen, 'height', { get: () => 1080 });
Object.defineProperty(screen, 'availWidth', { get: () => 1920 });
Object.defineProperty(screen, 'availHeight', { get: () => 1040 });

const getParameter = WebGLRenderingContext.prototype.getParameter;
WebGLRenderingContext.prototype.getParameter = function(parameter) {
    if (parameter === 37445) return 'Intel Inc.';
    if (parameter === 37446) return 'Intel Iris OpenGL Engine';
    return getParameter.apply(this, arguments);
};
""")

        if config.simulate_human:
            parts.append("""
window.humanMouseMove = async function(targetX, targetY) {
    const steps = 20 + Math.floor(Math.random() * 10);
    const startX = Math.random() * window.innerWidth;
    const startY = Math.random() * window.innerHeight;
    for (let i = 0; i <= steps; i++) {
        const progress = i / steps;
        const x = startX + (targetX - startX) * progress + (Math.random() - 0.5) * 10;
        const y = startY + (targetY - startY) * progress + (Math.random() - 0.5) * 10;
        const event = new MouseEvent('mousemove', {
            clientX: x, clientY: y, bubbles: true
        });
        document.dispatchEvent(event);
        await new Promise(r => setTimeout(r, 10 + Math.random() * 20));
    }
};
""")

        return "\n".join(parts)

    def generate_human_behavior_script(self) -> str:
        return """
async function humanScroll() {
    const scrollHeight = document.body.scrollHeight;
    const viewportHeight = window.innerHeight;
    const maxScroll = scrollHeight - viewportHeight;
    let currentScroll = 0;
    while (currentScroll < maxScroll * 0.8) {
        const scrollAmount = 100 + Math.random() * 200;
        currentScroll += scrollAmount;
        window.scrollTo({ top: currentScroll, behavior: 'smooth' });
        await new Promise(r => setTimeout(r, 300 + Math.random() * 500));
    }
}

function randomClick() {
    const elements = document.querySelectorAll('a, button, input');
    if (elements.length > 0) {
        const randomElement = elements[Math.floor(Math.random() * elements.length)];
        randomElement.dispatchEvent(new MouseEvent('mouseover', { bubbles: true }));
    }
}

humanScroll();
setTimeout(randomClick, 1000);
"""

    def assess_risk(self, domain: str | None) -> RiskLevel:
        if not domain:
            return RiskLevel.LOW

        lower = domain.lower()
        for d in HIGH_RISK_DOMAINS:
            if d in lower:
                return RiskLevel.HIGH
        for d in MEDIUM_RISK_DOMAINS:
            if d in lower:
                return RiskLevel.MEDIUM
        return RiskLevel.LOW

    def get_random_user_agent(self) -> str:
        return random.choice(USER_AGENTS)

    def extract_domain(self, url: str | None) -> str | None:
        if not url:
            return None
        try:
            clean = url.replace("https://", "").replace("http://", "")
            slash_idx = clean.find("/")
            if slash_idx > 0:
                return clean[:slash_idx]
            return clean
        except Exception:
            return None

    def get_random_delay(self, base_delay: int) -> int:
        return base_delay + random.randint(0, base_delay // 2)

    def get_alternative_sources(self, original_url: str, goal: str = "") -> list[str]:
        domain = self.extract_domain(original_url)
        lower_goal = goal.lower() if goal else ""
        alternatives = []

        if any(kw in lower_goal for kw in ["招聘", "岗位", "求职"]):
            if not domain or "zhipin" not in domain:
                alternatives.append("https://www.zhipin.com")
            if not domain or "lagou" not in domain:
                alternatives.append("https://www.lagou.com")
            if not domain or "51job" not in domain:
                alternatives.append("https://www.51job.com")
            if not domain or "liepin" not in domain:
                alternatives.append("https://www.liepin.com")

        if any(kw in lower_goal for kw in ["价格", "产品"]):
            alternatives.append("使用搜索引擎查找相关信息")
            alternatives.append("查看第三方评测网站")

        if any(kw in lower_goal for kw in ["新闻", "资讯"]):
            alternatives.append("使用搜索引擎查找相关新闻")
            alternatives.append("查看官方公众号或微博")

        return alternatives

    def _generate_fallback_strategies(self, risk: RiskLevel, goal: str) -> list[str]:
        strategies = ["puppeteer_standard"]
        if risk != RiskLevel.LOW:
            strategies.append("puppeteer_stealth")
        if risk == RiskLevel.HIGH:
            strategies.append("puppeteer_human_simulation")
        strategies.append("fetch_simple")
        if goal and any(kw in goal for kw in ["招聘", "岗位"]):
            strategies.append("search_alternative_sources")
        return strategies

    def _generate_headers(self, user_agent: str, referer: str) -> dict[str, str]:
        headers = {
            "User-Agent": user_agent,
            "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
            "Accept-Language": "zh-CN,zh;q=0.9,en;q=0.8",
            "Accept-Encoding": "gzip, deflate, br",
            "Connection": "keep-alive",
            "Upgrade-Insecure-Requests": "1",
            "Sec-Fetch-Dest": "document",
            "Sec-Fetch-Mode": "navigate",
            "Sec-Fetch-Site": "none",
            "Sec-Fetch-User": "?1",
            "Cache-Control": "max-age=0",
        }
        if referer:
            headers["Referer"] = referer
        return headers


anti_detection_service = AntiDetectionService()