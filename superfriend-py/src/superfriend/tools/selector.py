from __future__ import annotations

from typing import Any

from superfriend.tools.registry import ToolRegistry, ToolDefinition
from superfriend.tools.models import TaskContext


class ToolSelector:
    def __init__(self, registry: ToolRegistry):
        self.registry = registry

    def select_tools(self, user_request: str, context: TaskContext | None = None) -> list[ToolDefinition]:
        all_tools = self.registry.get_all_tools()
        if not all_tools:
            return []

        lower_request = user_request.lower()

        keyword_map: dict[str, str] = {
            "搜索": "search",
            "查找": "search",
            "search": "search",
            "爬取": "fetch",
            "抓取": "fetch",
            "fetch": "fetch",
            "浏览": "browser",
            "浏览器": "browser",
            "puppeteer": "browser",
            "渲染": "browser",
            "文件": "file",
            "file": "file",
            "解析": "parse",
            "parse": "parse",
            "计算": "math",
            "calculate": "math",
            "翻译": "language",
            "翻译": "language",
            "translate": "language",
            "代码": "code",
            "code": "code",
            "运行": "code",
            "run": "code",
            "图片": "image",
            "image": "image",
            "生成": "generate",
            "generate": "generate",
        }

        matched_categories: set[str] = set()
        for kw, category in keyword_map.items():
            if kw in lower_request:
                matched_categories.add(category)

        if matched_categories:
            scored: list[tuple[ToolDefinition, float]] = []
            for tool in all_tools:
                score = 0.0
                if tool.category in matched_categories:
                    score += 5.0
                if any(kw in tool.name.lower() for kw in lower_request.split()):
                    score += 3.0
                if any(kw in tool.description.lower() for kw in lower_request.split()):
                    score += 2.0
                if context:
                    context_score = context.get_tool_score(tool.name)
                    score += context_score * 2.0
                score += tool.reliability * 1.0
                if score > 0:
                    scored.append((tool, score))

            scored.sort(key=lambda x: x[1], reverse=True)
            return [t for t, _ in scored[:10]]

        sorted_by_reliability = sorted(all_tools, key=lambda t: t.reliability, reverse=True)
        return sorted_by_reliability[:10]

    def select_best_tool(
        self, candidates: list[ToolDefinition], parameters: dict[str, Any] | None = None
    ) -> ToolDefinition | None:
        if not candidates:
            return None

        if len(candidates) == 1:
            return candidates[0]

        scored = []
        for tool in candidates:
            score = tool.reliability * 10.0 - tool.cost * 2.0
            if parameters:
                for key in parameters:
                    if key in tool.parameters:
                        score += 2.0
                    if key in tool.required:
                        score += 5.0
            scored.append((tool, score))

        scored.sort(key=lambda x: x[1], reverse=True)
        return scored[0][0]