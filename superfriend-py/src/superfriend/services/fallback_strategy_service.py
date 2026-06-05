from __future__ import annotations

import time
import structlog
from dataclasses import dataclass, field
from typing import Any

logger = structlog.get_logger()

MAX_FAILURES_BEFORE_FALLBACK = 2
FAILURE_MEMORY_DURATION_MS = 300000


@dataclass
class ToolAlternative:
    tool_name: str = ""
    server_name: str = ""
    priority: float = 1.0
    fallback_condition: str = ""
    parameter_mapping: dict[str, str] = field(default_factory=dict)


@dataclass
class FallbackContext:
    original_tool_name: str = ""
    original_server_name: str = ""
    original_parameters: dict[str, Any] = field(default_factory=dict)
    failure_reason: str = ""
    user_request: str = ""
    available_tools: list[dict[str, Any]] = field(default_factory=list)
    attempt_count: int = 0


@dataclass
class FallbackResult:
    has_alternative: bool = False
    alternative_tool_name: str = ""
    alternative_server_name: str = ""
    alternative_parameters: dict[str, Any] = field(default_factory=dict)
    strategy: str = ""
    reason: str = ""


DEFAULT_ALTERNATIVES: dict[str, list[ToolAlternative]] = {
    "fetch": [
        ToolAlternative(tool_name="puppeteer__navigate", priority=0.8, fallback_condition="javascript_rendering"),
        ToolAlternative(tool_name="web_search", priority=0.6, fallback_condition="content_not_found"),
    ],
    "puppeteer__navigate": [
        ToolAlternative(tool_name="fetch", priority=0.7, fallback_condition="simple_page"),
    ],
    "web_search": [
        ToolAlternative(tool_name="fetch", priority=0.5, fallback_condition="direct_url"),
    ],
    "read_file": [
        ToolAlternative(tool_name="execute_code", priority=0.6, fallback_condition="special_format"),
    ],
    "code_execute": [
        ToolAlternative(tool_name="bash", priority=0.8, fallback_condition="shell_command"),
    ],
}


class FallbackStrategyService:
    def __init__(self):
        self._alternatives: dict[str, list[ToolAlternative]] = dict(DEFAULT_ALTERNATIVES)
        self._failure_counts: dict[str, int] = {}
        self._last_failure_time: dict[str, float] = {}

    def find_fallback(self, context: FallbackContext) -> FallbackResult:
        self._record_failure(context.original_tool_name, context.original_server_name)

        key = f"{context.original_server_name}__{context.original_tool_name}" if context.original_server_name else context.original_tool_name

        if self._failure_counts.get(key, 0) < MAX_FAILURES_BEFORE_FALLBACK:
            return FallbackResult(
                has_alternative=False,
                strategy="retry",
                reason=f"Failures ({self._failure_counts.get(key, 0)}) below threshold ({MAX_FAILURES_BEFORE_FALLBACK}), recommend retry",
            )

        alternatives = self._alternatives.get(context.original_tool_name, [])
        if not alternatives:
            return self._search_similar_tools(context)

        for alt in sorted(alternatives, key=lambda a: a.priority, reverse=True):
            if context.available_tools:
                available_names = {t.get("name", "") for t in context.available_tools}
                if alt.tool_name not in available_names:
                    continue

            mapped_params = self._map_parameters(context.original_parameters, alt.parameter_mapping)
            return FallbackResult(
                has_alternative=True,
                alternative_tool_name=alt.tool_name,
                alternative_server_name=alt.server_name,
                alternative_parameters=mapped_params,
                strategy="alternative_tool",
                reason=f"Switched from {context.original_tool_name} to {alt.tool_name} (condition: {alt.fallback_condition})",
            )

        return self._search_similar_tools(context)

    def _search_similar_tools(self, context: FallbackContext) -> FallbackResult:
        if not context.available_tools:
            return FallbackResult(has_alternative=False, strategy="none", reason="No available tools for fallback")

        original_lower = context.original_tool_name.lower()
        candidates = []
        for tool in context.available_tools:
            tool_name = tool.get("name", "")
            if tool_name == context.original_tool_name:
                continue
            score = 0.0
            if any(kw in tool_name.lower() for kw in original_lower.split("_")):
                score += 2.0
            desc = tool.get("description", "").lower()
            if any(kw in desc for kw in original_lower.split("_")):
                score += 1.0
            if score > 0:
                candidates.append((tool_name, score, tool.get("serverName", "")))

        if candidates:
            candidates.sort(key=lambda x: x[1], reverse=True)
            best_name, _, best_server = candidates[0]
            return FallbackResult(
                has_alternative=True,
                alternative_tool_name=best_name,
                alternative_server_name=best_server,
                strategy="semantic_search",
                reason=f"Found similar tool: {best_name}",
            )

        return FallbackResult(has_alternative=False, strategy="none", reason="No fallback available")

    def _record_failure(self, tool_name: str, server_name: str) -> None:
        key = f"{server_name}__{tool_name}" if server_name else tool_name
        self._failure_counts[key] = self._failure_counts.get(key, 0) + 1
        self._last_failure_time[key] = time.time()

        expired_keys = [
            k for k, t in self._last_failure_time.items()
            if (time.time() - t) * 1000 > FAILURE_MEMORY_DURATION_MS
        ]
        for k in expired_keys:
            self._failure_counts.pop(k, None)
            self._last_failure_time.pop(k, None)

    def reset_failures(self, tool_name: str, server_name: str = "") -> None:
        key = f"{server_name}__{tool_name}" if server_name else tool_name
        self._failure_counts.pop(key, None)
        self._last_failure_time.pop(key, None)

    def record_success(self, tool_name: str, server_name: str = "") -> None:
        self.reset_failures(tool_name, server_name)

    def add_alternative(self, tool_name: str, alternative: ToolAlternative) -> None:
        self._alternatives.setdefault(tool_name, []).append(alternative)

    def _map_parameters(self, original: dict[str, Any], mapping: dict[str, str]) -> dict[str, Any]:
        if not mapping:
            return dict(original)
        result = {}
        for orig_key, new_key in mapping.items():
            if orig_key in original:
                result[new_key] = original[orig_key]
        for k, v in original.items():
            if k not in mapping and k not in result:
                result[k] = v
        return result


fallback_strategy_service = FallbackStrategyService()