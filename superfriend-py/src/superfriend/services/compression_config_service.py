from __future__ import annotations

import json
import time
import structlog
from dataclasses import dataclass, field
from typing import Any
from enum import Enum

logger = structlog.get_logger()


class CompressionStrategy(str, Enum):
    TRUNCATE = "truncate"
    SUMMARIZE = "summarize"
    SLIDING_WINDOW = "sliding_window"
    IMPORTANCE_BASED = "importance_based"
    HYBRID = "hybrid"


@dataclass
class CompressionResult:
    original_token_count: int = 0
    compressed_token_count: int = 0
    compression_ratio: float = 0.0
    strategy_used: str = ""
    messages_removed: int = 0
    messages_summarized: int = 0


@dataclass
class MessageScore:
    index: int = 0
    importance: float = 0.0
    reason: str = ""
    token_count: int = 0


class SmartCompressionStrategy:
    def __init__(self, max_context_tokens: int = 8000, reserve_tokens: int = 2000,
                 summary_threshold: float = 0.3, importance_weights: dict[str, float] | None = None):
        self.max_context_tokens = max_context_tokens
        self.reserve_tokens = reserve_tokens
        self.summary_threshold = summary_threshold
        self.importance_weights = importance_weights or {
            "system": 1.0,
            "tool_call": 0.8,
            "tool_result": 0.6,
            "user_recent": 0.9,
            "user_old": 0.3,
            "assistant_recent": 0.7,
            "assistant_old": 0.2,
        }

    def estimate_tokens(self, text: str) -> int:
        return max(1, len(text) // 4)

    def score_messages(self, messages: list[dict[str, Any]]) -> list[MessageScore]:
        total = len(messages)
        scores = []
        for i, msg in enumerate(messages):
            role = msg.get("role", "")
            content = msg.get("content", "")
            if isinstance(content, list):
                content = " ".join(p.get("text", "") for p in content if isinstance(p, dict) and p.get("type") == "text")

            token_count = self.estimate_tokens(str(content))
            recency = (i + 1) / total
            importance = 0.5

            if role == "system":
                importance = self.importance_weights["system"]
            elif role == "tool":
                importance = self.importance_weights["tool_result"] * (0.5 + 0.5 * recency)
            elif role == "assistant":
                has_tool_calls = bool(msg.get("tool_calls"))
                base = self.importance_weights["assistant_recent"] if recency > 0.5 else self.importance_weights["assistant_old"]
                importance = base * (1.2 if has_tool_calls else 1.0)
            elif role == "user":
                importance = self.importance_weights["user_recent"] if recency > 0.5 else self.importance_weights["user_old"]

            reason = f"role={role}, recency={recency:.2f}"
            scores.append(MessageScore(index=i, importance=importance, reason=reason, token_count=token_count))

        return scores

    def compress(self, messages: list[dict[str, Any]], strategy: CompressionStrategy = CompressionStrategy.HYBRID) -> tuple[list[dict[str, Any]], CompressionResult]:
        total_tokens = sum(self.estimate_tokens(str(m.get("content", ""))) for m in messages)
        target_tokens = self.max_context_tokens - self.reserve_tokens

        if total_tokens <= target_tokens:
            return messages, CompressionResult(
                original_token_count=total_tokens, compressed_token_count=total_tokens,
                compression_ratio=1.0, strategy_used="none",
            )

        if strategy == CompressionStrategy.TRUNCATE:
            return self._truncate(messages, target_tokens)
        elif strategy == CompressionStrategy.SLIDING_WINDOW:
            return self._sliding_window(messages, target_tokens)
        elif strategy == CompressionStrategy.IMPORTANCE_BASED:
            return self._importance_based(messages, target_tokens)
        elif strategy == CompressionStrategy.HYBRID:
            return self._hybrid(messages, target_tokens)
        else:
            return self._truncate(messages, target_tokens)

    def _truncate(self, messages: list[dict[str, Any]], target: int) -> tuple[list[dict[str, Any]], CompressionResult]:
        system_msgs = [m for m in messages if m.get("role") == "system"]
        non_system = [m for m in messages if m.get("role") != "system"]

        result = list(system_msgs)
        current_tokens = sum(self.estimate_tokens(str(m.get("content", ""))) for m in result)
        removed = 0

        for msg in reversed(non_system):
            msg_tokens = self.estimate_tokens(str(msg.get("content", "")))
            if current_tokens + msg_tokens <= target:
                result.append(msg)
                current_tokens += msg_tokens
            else:
                removed += 1

        result_sorted = sorted(result, key=lambda m: messages.index(m))
        original = sum(self.estimate_tokens(str(m.get("content", ""))) for m in messages)
        return result_sorted, CompressionResult(
            original_token_count=original, compressed_token_count=current_tokens,
            compression_ratio=current_tokens / max(original, 1), strategy_used="truncate",
            messages_removed=removed,
        )

    def _sliding_window(self, messages: list[dict[str, Any]], target: int) -> tuple[list[dict[str, Any]], CompressionResult]:
        system_msgs = [m for m in messages if m.get("role") == "system"]
        non_system = [m for m in messages if m.get("role") != "system"]

        system_tokens = sum(self.estimate_tokens(str(m.get("content", ""))) for m in system_msgs)
        available = target - system_tokens

        window: list[dict[str, Any]] = []
        window_tokens = 0
        for msg in reversed(non_system):
            msg_tokens = self.estimate_tokens(str(msg.get("content", "")))
            if window_tokens + msg_tokens <= available:
                window.insert(0, msg)
                window_tokens += msg_tokens
            else:
                break

        result = system_msgs + window
        original = sum(self.estimate_tokens(str(m.get("content", ""))) for m in messages)
        compressed = sum(self.estimate_tokens(str(m.get("content", ""))) for m in result)
        return result, CompressionResult(
            original_token_count=original, compressed_token_count=compressed,
            compression_ratio=compressed / max(original, 1), strategy_used="sliding_window",
            messages_removed=len(messages) - len(result),
        )

    def _importance_based(self, messages: list[dict[str, Any]], target: int) -> tuple[list[dict[str, Any]], CompressionResult]:
        scores = self.score_messages(messages)
        sorted_scores = sorted(scores, key=lambda s: s.importance, reverse=True)

        selected_indices: set[int] = set()
        current_tokens = 0

        for score in sorted_scores:
            if current_tokens + score.token_count <= target:
                selected_indices.add(score.index)
                current_tokens += score.token_count

        result = [m for i, m in enumerate(messages) if i in selected_indices]
        original = sum(s.token_count for s in scores)
        return result, CompressionResult(
            original_token_count=original, compressed_token_count=current_tokens,
            compression_ratio=current_tokens / max(original, 1), strategy_used="importance_based",
            messages_removed=len(messages) - len(result),
        )

    def _hybrid(self, messages: list[dict[str, Any]], target: int) -> tuple[list[dict[str, Any]], CompressionResult]:
        scores = self.score_messages(messages)
        low_importance = [(i, s) for i, s in enumerate(scores) if s.importance < self.summary_threshold and messages[i].get("role") != "system"]

        if not low_importance:
            return self._sliding_window(messages, target)

        result_messages = list(messages)
        removed = 0
        summarized = 0

        for idx, score in reversed(low_importance):
            total = sum(self.estimate_tokens(str(m.get("content", ""))) for m in result_messages)
            if total <= target:
                break

            msg = result_messages[idx]
            if msg.get("role") in ("user", "assistant"):
                content = str(msg.get("content", ""))
                if len(content) > 200:
                    result_messages[idx] = {**msg, "content": f"[摘要] {content[:150]}..."}
                    summarized += 1
                else:
                    result_messages.pop(idx)
                    removed += 1
            else:
                result_messages.pop(idx)
                removed += 1

        original = sum(self.estimate_tokens(str(m.get("content", ""))) for m in messages)
        compressed = sum(self.estimate_tokens(str(m.get("content", ""))) for m in result_messages)
        return result_messages, CompressionResult(
            original_token_count=original, compressed_token_count=compressed,
            compression_ratio=compressed / max(original, 1), strategy_used="hybrid",
            messages_removed=removed, messages_summarized=summarized,
        )


class DynamicConfigManager:
    def __init__(self):
        self._configs: dict[str, dict[str, Any]] = {}
        self._observers: dict[str, list[Any]] = {}
        self._version: dict[str, int] = {}

    def get(self, key: str, default: Any = None) -> Any:
        parts = key.split(".")
        current = self._configs
        for part in parts:
            if isinstance(current, dict) and part in current:
                current = current[part]
            else:
                return default
        return current

    def set(self, key: str, value: Any) -> None:
        parts = key.split(".")
        current = self._configs
        for part in parts[:-1]:
            if part not in current:
                current[part] = {}
            current = current[part]
        current[parts[-1]] = value
        self._version[key] = self._version.get(key, 0) + 1
        self._notify_observers(key, value)

    def delete(self, key: str) -> bool:
        parts = key.split(".")
        current = self._configs
        for part in parts[:-1]:
            if isinstance(current, dict) and part in current:
                current = current[part]
            else:
                return False
        if isinstance(current, dict) and parts[-1] in current:
            del current[parts[-1]]
            self._version[key] = self._version.get(key, 0) + 1
            self._notify_observers(key, None)
            return True
        return False

    def observe(self, key_pattern: str, callback: Any) -> None:
        self._observers.setdefault(key_pattern, []).append(callback)

    def _notify_observers(self, key: str, value: Any) -> None:
        for pattern, callbacks in self._observers.items():
            if pattern == "*" or key.startswith(pattern):
                for cb in callbacks:
                    try:
                        cb(key, value)
                    except Exception as e:
                        logger.warning("config_observer_error", key=key, error=str(e))

    def get_version(self, key: str) -> int:
        return self._version.get(key, 0)

    def export_config(self) -> dict[str, Any]:
        return dict(self._configs)

    def import_config(self, config: dict[str, Any]) -> None:
        self._configs.update(config)

    def list_keys(self, prefix: str = "") -> list[str]:
        keys = []
        self._flatten_keys(self._configs, prefix, keys)
        return keys

    def _flatten_keys(self, obj: Any, prefix: str, keys: list[str]) -> None:
        if isinstance(obj, dict):
            for k, v in obj.items():
                full_key = f"{prefix}.{k}" if prefix else k
                keys.append(full_key)
                self._flatten_keys(v, full_key, keys)


smart_compression_strategy = SmartCompressionStrategy()
dynamic_config_manager = DynamicConfigManager()