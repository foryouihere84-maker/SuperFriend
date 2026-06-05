from __future__ import annotations

import re
import structlog
from typing import Any

logger = structlog.get_logger()


class ProcessedResult:
    def __init__(self):
        self.content: str = ""
        self.needs_javascript: bool = False
        self.blocked_by_anti_crawl: bool = False
        self.content_empty: bool = False
        self.suggested_action: str = ""
        self.alternative_tool: str = ""
        self.fixed_parameters: dict[str, Any] = {}
        self.extracted_links: list[str] = []
        self.summary: str = ""
        self.success: bool = True

    def has_issues(self) -> bool:
        return self.needs_javascript or self.blocked_by_anti_crawl or self.content_empty


JS_RENDER_PATTERNS = [
    re.compile(r"<script[^>]*>.*?</script>", re.IGNORECASE | re.DOTALL),
    re.compile(r"ng-app|ng-controller|vue-app|react-root|data-reactroot", re.IGNORECASE),
    re.compile(r"""require\(['\"].*['\"]\)""", re.IGNORECASE),
    re.compile(r"window\.__INITIAL_STATE__|window\.__PRELOADED_STATE__", re.IGNORECASE),
]

ANTI_CRAWL_PATTERNS = [
    re.compile(r"验证码|captcha|verify", re.IGNORECASE),
    re.compile(r"访问频率|请求过于频繁|too many requests", re.IGNORECASE),
    re.compile(r"请登录|请先登录|please login", re.IGNORECASE),
    re.compile(r"访问被拒绝|access denied|forbidden", re.IGNORECASE),
    re.compile(r"安全验证|安全检查|security check", re.IGNORECASE),
]


class SmartToolResultProcessor:
    def process(self, tool_name: str, result: str, parameters: dict[str, Any] | None = None) -> ProcessedResult:
        processed = ProcessedResult()
        processed.success = True
        processed.content = result

        if not result or not result.strip():
            processed.content_empty = True
            processed.suggested_action = "结果为空，请检查参数或尝试其他工具"
            processed.success = False
            return processed

        if self._is_fetch_tool(tool_name):
            processed.needs_javascript = self._needs_javascript_rendering(result)
            if processed.needs_javascript:
                processed.suggested_action = "页面需要 JavaScript 渲染，建议使用 puppeteer 工具"
                processed.alternative_tool = "puppeteer__navigate"
                if parameters and "url" in parameters:
                    processed.fixed_parameters = {"url": parameters["url"]}

        processed.blocked_by_anti_crawl = self._detected_anti_crawl(result)
        if processed.blocked_by_anti_crawl:
            processed.suggested_action = (
                "检测到反爬机制，建议：1) 使用 puppeteer 模拟浏览器 2) 添加延迟 3) 更换来源"
            )

        if self._is_fetch_tool(tool_name):
            processed.content_empty = self._is_content_empty(result)
            if processed.content_empty and not processed.needs_javascript:
                processed.suggested_action = "页面内容为空或需要登录，请尝试其他来源"

        if self._is_fetch_tool(tool_name) or "search" in tool_name:
            processed.extracted_links = self._extract_links(result)

        if len(result) > 5000:
            processed.summary = self._generate_smart_summary(result, 1000)

        return processed

    def smart_truncate(self, content: str, max_length: int) -> str:
        if not content or len(content) <= max_length:
            return content

        main_content = self._extract_main_content(content)
        if len(main_content) <= max_length:
            return main_content

        paragraphs = self._split_into_paragraphs(main_content)
        result = []
        current_length = 0

        for para in paragraphs:
            if current_length + len(para) + 2 > max_length - 200:
                break
            if self._is_important_paragraph(para):
                result.append(para)
                current_length += len(para) + 2

        if result:
            truncated = "\n\n".join(result)
            return f"{truncated}\n\n[内容已智能截断，总长度：{len(content)} 字符]"
        return content[:max_length] + f"\n\n[内容已截断，总长度：{len(content)} 字符]"

    def _needs_javascript_rendering(self, html: str) -> bool:
        if not html:
            return False

        script_count = len(re.findall(r"<script", html, re.IGNORECASE))
        text_content = re.sub(r"<[^>]+>", "", html).strip()
        text_length = len(text_content)

        if script_count > 5 and text_length < 500:
            return True

        for pattern in JS_RENDER_PATTERNS:
            if pattern.search(html):
                return True

        lower = html.lower()
        if ("enable javascript" in lower
                or "请启用javascript" in lower
                or ("loading..." in lower and text_length < 200)):
            return True

        return False

    def _detected_anti_crawl(self, content: str) -> bool:
        if not content:
            return False
        for pattern in ANTI_CRAWL_PATTERNS:
            if pattern.search(content):
                return True
        return False

    def _is_content_empty(self, html: str) -> bool:
        if not html or not html.strip():
            return True

        text = re.sub(r"<[^>]+>", " ", html)
        text = re.sub(r"\s+", " ", text).strip()

        if len(text) < 100:
            return True

        body_start = html.lower().find("<body")
        body_end = html.lower().find("</body>")
        if body_start > 0 and body_end > body_start:
            body = html[body_start:body_end]
            body_text = re.sub(r"<[^>]+>", " ", body)
            body_text = re.sub(r"\s+", " ", body_text).strip()
            if len(body_text) < 200:
                return True

        return False

    def _extract_main_content(self, html: str) -> str:
        if not html:
            return ""

        content = html
        content = re.sub(r"<script[^>]*>[\s\S]*?</script>", "", content)
        content = re.sub(r"<style[^>]*>[\s\S]*?</style>", "", content)
        content = re.sub(r"<!--[^>]*-->", "", content)

        article_match = re.search(r"<article[^>]*>([\s\S]*?)</article>", content, re.IGNORECASE)
        if article_match:
            return article_match.group(1)

        main_match = re.search(r"<main[^>]*>([\s\S]*?)</main>", content, re.IGNORECASE)
        if main_match:
            return main_match.group(1)

        body_match = re.search(r"<body[^>]*>([\s\S]*?)</body>", content, re.IGNORECASE)
        if body_match:
            return body_match.group(1)

        return content

    def _split_into_paragraphs(self, content: str) -> list[str]:
        text = re.sub(r"<[^>]+>", "\n", content)
        text = text.replace("&nbsp;", " ")
        text = re.sub(r"&[a-z]+;", "", text)

        paragraphs = []
        for line in text.split("\n"):
            line = line.strip()
            if len(line) > 20:
                paragraphs.append(line)
        return paragraphs

    def _is_important_paragraph(self, para: str) -> bool:
        if not para or len(para) < 30:
            return False

        lower = para.lower()
        if any(kw in lower for kw in ["copyright", "版权所有", "all rights reserved", "备案"]):
            return False

        important_keywords = [
            "招聘", "岗位", "职位", "薪资", "要求", "职责",
            "job", "position", "salary", "requirement", "responsibility",
        ]
        for kw in important_keywords:
            if kw in lower:
                return True

        digit_count = sum(1 for c in para if c.isdigit())
        if digit_count > 5:
            return True

        return len(para) > 100

    def _extract_links(self, content: str) -> list[str]:
        links = []
        if not content:
            return links

        seen = set()
        for match in re.finditer(r"""href=["']([^"']+)["']""", content, re.IGNORECASE):
            url = match.group(1)
            if url not in seen and self._is_valid_url(url):
                links.append(url)
                seen.add(url)

        return links[:20]

    def _generate_smart_summary(self, content: str, max_length: int) -> str:
        if not content or len(content) <= max_length:
            return content

        paragraphs = self._split_into_paragraphs(content)
        result = []
        current_length = 0

        for para in paragraphs:
            if current_length >= max_length:
                break
            if self._is_important_paragraph(para):
                result.append(para)
                current_length += len(para)

        if current_length < max_length // 2:
            for para in paragraphs:
                if current_length >= max_length:
                    break
                if para not in result:
                    result.append(para)
                    current_length += len(para)

        return "\n\n".join(result)

    def _is_fetch_tool(self, tool_name: str) -> bool:
        return bool(tool_name and ("fetch" in tool_name or "puppeteer" in tool_name))

    def _is_valid_url(self, url: str) -> bool:
        if not url:
            return False
        if url.startswith("javascript:"):
            return False
        if url.startswith("#"):
            return False
        if url.startswith("data:"):
            return False
        return url.startswith("http://") or url.startswith("https://") or url.startswith("/")


smart_processor = SmartToolResultProcessor()