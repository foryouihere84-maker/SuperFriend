from __future__ import annotations

import structlog
from typing import Any

from superfriend.tools.anti_detection import anti_detection_service, CrawlConfig, RiskLevel

logger = structlog.get_logger()

SITE_CONFIGS: dict[str, dict[str, Any]] = {
    "jobs.mihoyo.com": {"wait_time": 3000, "needs_login": True, "site_name": "招聘信息"},
    "campus.mihoyo.com": {"wait_time": 3000, "needs_login": True, "site_name": "校招信息"},
    "join.qq.com": {"wait_time": 4000, "needs_login": True, "site_name": "腾讯招聘"},
    "talent.alibaba.com": {"wait_time": 4000, "needs_login": True, "site_name": "阿里招聘"},
    "jobs.bytedance.com": {"wait_time": 4000, "needs_login": True, "site_name": "字节招聘"},
    "career.huawei.com": {"wait_time": 4000, "needs_login": True, "site_name": "华为招聘"},
    "nowcoder.com": {"wait_time": 3000, "needs_login": True, "site_name": "牛客网"},
    "leetcode.cn": {"wait_time": 2000, "needs_login": True, "site_name": "力扣"},
    "zhihu.com": {"wait_time": 3000, "needs_login": True, "site_name": "知乎"},
    "weibo.com": {"wait_time": 3000, "needs_login": True, "site_name": "微博"},
    "bilibili.com": {"wait_time": 3000, "needs_login": True, "site_name": "B站"},
    "xiaohongshu.com": {"wait_time": 4000, "needs_login": True, "site_name": "小红书"},
    "taobao.com": {"wait_time": 5000, "needs_login": True, "site_name": "淘宝"},
    "jd.com": {"wait_time": 4000, "needs_login": True, "site_name": "京东"},
    "tmall.com": {"wait_time": 4000, "needs_login": True, "site_name": "天猫"},
}


class CrawlStrategy:
    def __init__(self):
        self.strategy: str = ""
        self.steps: list[str] = []
        self.params: dict[str, Any] = {}
        self.fallback_tool: str = ""
        self.tips: list[str] = []
        self.estimated_time: int = 0


class PuppeteerHelper:
    def generate_strategy(self, url: str, goal: str = "") -> CrawlStrategy:
        strategy = CrawlStrategy()
        site_config = self.get_site_config(url)
        lower_goal = goal.lower() if goal else ""

        if any(kw in lower_goal for kw in ["招聘", "岗位", "职位"]):
            strategy.strategy = "招聘信息爬取"
            strategy.steps = [
                "1. 使用 puppeteer 导航到目标页面",
                f"2. 等待 {site_config['wait_time']}ms 让页面加载",
                "3. 使用 get_content 获取页面内容",
                "4. 如果内容为空，尝试滚动加载更多",
            ]
            strategy.params = {"wait_time": site_config["wait_time"], "scroll": True}
            strategy.tips = [
                "招聘网站通常需要 JavaScript 渲染",
                "部分内容可能需要滚动才能加载",
            ]
            if site_config["needs_login"]:
                strategy.tips.append("⚠️ 该网站可能需要登录才能查看完整内容")

        elif any(kw in lower_goal for kw in ["搜索", "查找"]):
            strategy.strategy = "搜索结果爬取"
            strategy.steps = [
                "1. 使用 puppeteer 导航到搜索页面",
                "2. 等待搜索结果加载",
                "3. 获取搜索结果列表",
                "4. 可选：点击详情页获取更多信息",
            ]
            strategy.params = {"wait_time": site_config["wait_time"]}

        else:
            strategy.strategy = "通用页面爬取"
            strategy.steps = [
                "1. 使用 puppeteer 导航到目标页面",
                "2. 等待页面加载完成",
                "3. 获取页面内容",
            ]

        strategy.fallback_tool = "fetch__fetch"
        strategy.estimated_time = site_config["wait_time"] + 2000
        return strategy

    def get_site_config(self, url: str | None) -> dict[str, Any]:
        if not url:
            return {"wait_time": 2000, "needs_login": False, "site_name": "通用网站"}

        lower = url.lower()
        for domain, cfg in SITE_CONFIGS.items():
            if domain in lower:
                return cfg
        return {"wait_time": 2000, "needs_login": False, "site_name": "通用网站"}

    def generate_puppeteer_params(self, url: str, goal: str = "") -> dict[str, Any]:
        params: dict[str, Any] = {"url": url}
        site_config = self.get_site_config(url)
        params["wait_time"] = site_config["wait_time"]

        if goal:
            lower_goal = goal.lower()
            if any(kw in lower_goal for kw in ["列表", "所有", "更多"]):
                params["scroll"] = True
                params["scroll_times"] = 3
            if any(kw in lower_goal for kw in ["截图", "图片"]):
                params["screenshot"] = True

        return params

    def should_use_puppeteer(self, url: str, previous_result: str | None = None) -> bool:
        site_config = self.get_site_config(url)
        if site_config["wait_time"] > 2000:
            return True

        if previous_result:
            lower = previous_result.lower()
            if "javascript" in lower or "enable javascript" in lower:
                return True
            if len(previous_result) < 200:
                return True
            if any(kw in lower for kw in ["验证", "captcha", "blocked"]):
                return True

        if url:
            lower_url = url.lower()
            if "#/" in lower_url or "/#/" in lower_url:
                return True
            js_sites = ["zhihu", "bilibili", "weibo", "taobao", "jd", "tmall"]
            if any(s in lower_url for s in js_sites):
                return True

        return False

    def get_tool_sequence(self, url: str, goal: str = "") -> list[dict[str, Any]]:
        strategy = self.generate_strategy(url, goal)
        sequence = []

        sequence.append({
            "tool": "puppeteer__navigate",
            "params": {"url": url},
            "description": "导航到目标页面",
        })

        sequence.append({
            "tool": "puppeteer__wait",
            "params": {"time": strategy.params.get("wait_time", 2000)},
            "description": "等待页面加载",
        })

        if strategy.params.get("scroll"):
            sequence.append({
                "tool": "puppeteer__infinite_scroll",
                "params": {"times": strategy.params.get("scroll_times", 3)},
                "description": "滚动加载更多内容",
            })

        sequence.append({
            "tool": "puppeteer__get_content",
            "params": {},
            "description": "获取页面内容",
        })

        return sequence

    def generate_user_tip(self, url: str, goal: str = "") -> str:
        site_config = self.get_site_config(url)
        strategy = self.generate_strategy(url, goal)

        lines = [
            f"📌 网站信息：{site_config['site_name']}",
            f"⏱️ 预计加载时间：{site_config['wait_time']}ms",
            f"📋 策略：{strategy.strategy}",
        ]

        if strategy.tips:
            lines.append("")
            lines.append("💡 提示：")
            for tip in strategy.tips:
                lines.append(f"  {tip}")

        return "\n".join(lines)


puppeteer_helper = PuppeteerHelper()