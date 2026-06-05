from __future__ import annotations

import re
import structlog
from dataclasses import dataclass, field
from typing import Any

logger = structlog.get_logger()

LEVEL1_TOKEN_THRESHOLD = 10000
LEVEL2_TOKEN_THRESHOLD = 12000
MAX_TOOL_RESULT_LENGTH = 5000
RECENT_TOKENS_TO_KEEP = 9000
MIN_MESSAGES = 5
SEARCH_RESULT_MAX_LENGTH = 5000
FILE_CONTENT_MAX_LENGTH = 15000
ERROR_MESSAGE_MAX_LENGTH = 5000
DEFAULT_RESULT_MAX_LENGTH = 5000

SEARCH_TOOLS = {"search", "find", "query", "lookup", "retrieve", "web_search", "google", "bing"}
FILE_TOOLS = {"read", "write", "list", "directory", "file", "folder", "path"}
ERROR_PATTERN = re.compile(r"(?i)(error|exception|failed|failure|not found|不存在|失败|错误)")


@dataclass
class MessageImportance:
    index: int = 0
    score: float = 0.0
    role: str = ""
    content: str = ""
    has_tool_call: bool = False
    has_error: bool = False


@dataclass
class ToolResultConfig:
    tool_name: str = ""
    max_length: int = DEFAULT_RESULT_MAX_LENGTH
    preserve_structure: bool = True
    truncation_suffix: str = "...[truncated]"


def _estimate_tokens(text: str) -> int:
    return len(text) // 4


def _truncate(text: str, max_length: int, suffix: str = "...[truncated]") -> str:
    if len(text) <= max_length:
        return text
    return text[: max_length - len(suffix)] + suffix


class ContextCompressionService:
    def compress_messages(self, messages: list[dict[str, Any]], max_tokens: int = LEVEL1_TOKEN_THRESHOLD) -> list[dict[str, Any]]:
        if not messages:
            return messages

        total_tokens = sum(_estimate_tokens(m.get("content", "") or "") for m in messages)
        if total_tokens <= max_tokens:
            return messages

        scored = self._score_messages(messages)
        keep_indices = self._select_messages_to_keep(scored, len(messages), max_tokens)

        compressed = []
        for i, msg in enumerate(messages):
            if i in keep_indices:
                content = msg.get("content", "") or ""
                if msg.get("role") == "tool":
                    tool_name = msg.get("name", "")
                    config = self._get_tool_result_config(tool_name)
                    content = _truncate(content, config.max_length, config.truncation_suffix)
                compressed.append({**msg, "content": content})
            elif msg.get("role") == "system":
                compressed.append(msg)

        return compressed

    def compress_tool_result(self, tool_name: str, result: str) -> str:
        config = self._get_tool_result_config(tool_name)
        return _truncate(result, config.max_length, config.truncation_suffix)

    def _score_messages(self, messages: list[dict[str, Any]]) -> list[MessageImportance]:
        scored = []
        for i, msg in enumerate(messages):
            content = msg.get("content", "") or ""
            role = msg.get("role", "")
            has_tool_call = bool(msg.get("tool_calls"))
            has_error = bool(ERROR_PATTERN.search(content))

            score = 0.0
            if role == "system":
                score += 10.0
            elif role == "user":
                score += 5.0
            elif role == "assistant":
                score += 3.0
                if has_tool_call:
                    score += 2.0
            elif role == "tool":
                score += 1.0

            recency = i / max(len(messages), 1)
            score += recency * 4.0

            if has_error:
                score += 1.0

            scored.append(MessageImportance(
                index=i, score=score, role=role,
                content=content, has_tool_call=has_tool_call, has_error=has_error,
            ))
        return scored

    def _select_messages_to_keep(self, scored: list[MessageImportance], total: int, max_tokens: int) -> set[int]:
        keep = set()
        current_tokens = 0

        for mi in sorted(scored, key=lambda x: x.score, reverse=True):
            tokens = _estimate_tokens(mi.content)
            if current_tokens + tokens <= max_tokens or mi.role == "system":
                keep.add(mi.index)
                current_tokens += tokens

        recent_count = max(MIN_MESSAGES, int(total * 0.3))
        for i in range(max(0, total - recent_count), total):
            if i not in keep:
                tokens = _estimate_tokens(scored[i].content)
                keep.add(i)
                current_tokens += tokens

        return keep

    def _get_tool_result_config(self, tool_name: str) -> ToolResultConfig:
        lower = tool_name.lower()
        if any(t in lower for t in SEARCH_TOOLS):
            return ToolResultConfig(tool_name=tool_name, max_length=SEARCH_RESULT_MAX_LENGTH)
        if any(t in lower for t in FILE_TOOLS):
            return ToolResultConfig(tool_name=tool_name, max_length=FILE_CONTENT_MAX_LENGTH)
        if ERROR_PATTERN.search(tool_name):
            return ToolResultConfig(tool_name=tool_name, max_length=ERROR_MESSAGE_MAX_LENGTH)
        return ToolResultConfig(tool_name=tool_name, max_length=DEFAULT_RESULT_MAX_LENGTH)


context_compression_service = ContextCompressionService()