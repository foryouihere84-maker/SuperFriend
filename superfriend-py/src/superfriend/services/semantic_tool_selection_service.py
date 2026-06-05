from __future__ import annotations

import time
import structlog
from dataclasses import dataclass, field
from typing import Any
from datetime import datetime

logger = structlog.get_logger()


@dataclass
class ToolScore:
    tool_name: str = ""
    server_name: str = ""
    semantic_score: float = 0.0
    success_rate: float = 0.0
    final_score: float = 0.0
    reason: str = ""
    matched_keywords: list[str] = field(default_factory=list)


@dataclass
class ToolStatistics:
    tool_name: str = ""
    server_name: str = ""
    total_calls: int = 0
    successful_calls: int = 0
    total_duration_ms: int = 0
    average_duration_ms: int = 0
    common_errors: list[str] = field(default_factory=list)
    last_used: str = ""


class SemanticToolSelectionService:
    def __init__(self):
        self._tool_stats: dict[str, ToolStatistics] = {}

    def record_tool_call(self, tool_name: str, server_name: str, success: bool, duration_ms: int) -> None:
        key = f"{server_name}__{tool_name}"
        stats = self._tool_stats.get(key)
        if not stats:
            stats = ToolStatistics(tool_name=tool_name, server_name=server_name)
            self._tool_stats[key] = stats

        stats.total_calls += 1
        if success:
            stats.successful_calls += 1
        else:
            stats.common_errors = stats.common_errors[-5:]
        stats.total_duration_ms += duration_ms
        stats.average_duration_ms = stats.total_duration_ms // max(stats.total_calls, 1)
        stats.last_used = datetime.now().isoformat()

    def select_tools(self, user_request: str, available_tools: list[dict[str, Any]], top_k: int = 5) -> list[ToolScore]:
        if not available_tools:
            return []

        request_lower = user_request.lower()
        request_words = set(request_lower.split())

        scored: list[ToolScore] = []
        for tool in available_tools:
            tool_name = tool.get("name", "")
            server_name = tool.get("serverName", "")
            description = tool.get("description", "").lower()

            semantic_score = 0.0
            matched_keywords = []

            name_words = set(tool_name.lower().replace("_", " ").split())
            overlap = request_words & name_words
            if overlap:
                semantic_score += len(overlap) * 2.0
                matched_keywords.extend(overlap)

            for word in request_words:
                if word in description:
                    semantic_score += 1.5
                    matched_keywords.append(word)

            key = f"{server_name}__{tool_name}"
            stats = self._tool_stats.get(key)
            success_rate = 0.0
            if stats and stats.total_calls > 0:
                success_rate = stats.successful_calls / stats.total_calls
                semantic_score += success_rate * 1.0

            final_score = semantic_score * 0.7 + success_rate * 3.0

            scored.append(ToolScore(
                tool_name=tool_name, server_name=server_name,
                semantic_score=semantic_score, success_rate=success_rate,
                final_score=final_score,
                reason=f"semantic={semantic_score:.1f}, success_rate={success_rate:.2f}",
                matched_keywords=matched_keywords,
            ))

        scored.sort(key=lambda s: s.final_score, reverse=True)
        return scored[:top_k]

    def get_tool_statistics(self, tool_name: str | None = None) -> dict[str, Any]:
        if tool_name:
            stats = {k: v for k, v in self._tool_stats.items() if tool_name in k}
        else:
            stats = dict(self._tool_stats)
        return {
            k: {
                "tool_name": v.tool_name,
                "server_name": v.server_name,
                "total_calls": v.total_calls,
                "successful_calls": v.successful_calls,
                "success_rate": v.successful_calls / max(v.total_calls, 1),
                "average_duration_ms": v.average_duration_ms,
                "last_used": v.last_used,
            }
            for k, v in stats.items()
        }

    def get_recommendation(self, user_request: str, available_tools: list[dict[str, Any]]) -> dict[str, Any]:
        scores = self.select_tools(user_request, available_tools, top_k=3)
        if not scores:
            return {"recommendation": None, "reason": "No tools available"}

        best = scores[0]
        return {
            "recommendation": best.tool_name,
            "server": best.server_name,
            "confidence": best.final_score,
            "reason": best.reason,
            "alternatives": [
                {"tool": s.tool_name, "score": s.final_score}
                for s in scores[1:]
            ],
        }


semantic_tool_selection_service = SemanticToolSelectionService()