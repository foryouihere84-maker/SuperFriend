from __future__ import annotations

import json
import time
import structlog
from dataclasses import dataclass, field
from typing import Any
from enum import Enum

from superfriend.services.llm_client import llm_client

logger = structlog.get_logger()


class IntentCategory(str, Enum):
    QUESTION = "question"
    COMMAND = "command"
    CREATION = "creation"
    ANALYSIS = "analysis"
    CONVERSATION = "conversation"
    TOOL_REQUEST = "tool_request"
    KNOWLEDGE_QUERY = "knowledge_query"
    FILE_OPERATION = "file_operation"
    UNCLEAR = "unclear"


@dataclass
class IntentResult:
    category: str = IntentCategory.UNCLEAR.value
    confidence: float = 0.0
    sub_intents: list[str] = field(default_factory=list)
    entities: dict[str, Any] = field(default_factory=dict)
    requires_tools: bool = False
    suggested_tools: list[str] = field(default_factory=list)
    complexity: str = "simple"


class LLMIntentClassifier:
    def __init__(self, model: str = "deepseek-chat", use_cache: bool = True, cache_ttl: int = 300):
        self._model = model
        self._use_cache = use_cache
        self._cache_ttl = cache_ttl
        self._cache: dict[str, tuple[float, IntentResult]] = {}

    async def classify(self, user_message: str, context: dict[str, Any] | None = None) -> IntentResult:
        if self._use_cache:
            cached = self._cache.get(user_message[:200])
            if cached and time.time() - cached[0] < self._cache_ttl:
                return cached[1]

        heuristic = self._heuristic_classify(user_message)
        if heuristic.confidence > 0.8:
            self._cache_result(user_message, heuristic)
            return heuristic

        llm_result = await self._llm_classify(user_message, context)
        if llm_result.confidence > heuristic.confidence:
            self._cache_result(user_message, llm_result)
            return llm_result

        self._cache_result(user_message, heuristic)
        return heuristic

    def _heuristic_classify(self, message: str) -> IntentResult:
        msg_lower = message.lower().strip()
        result = IntentResult()

        if any(kw in msg_lower for kw in ["搜索", "查找", "搜索一下", "查一下", "search", "find", "百度", "google"]):
            result.category = IntentCategory.TOOL_REQUEST.value
            result.suggested_tools = ["web_search"]
            result.requires_tools = True
            result.confidence = 0.85
        elif any(kw in msg_lower for kw in ["打开", "启动", "运行", "执行", "run", "execute", "open"]):
            result.category = IntentCategory.COMMAND.value
            result.requires_tools = True
            result.confidence = 0.75
        elif any(kw in msg_lower for kw in ["创建", "生成", "写", "制作", "create", "generate", "write", "make"]):
            result.category = IntentCategory.CREATION.value
            result.confidence = 0.7
        elif any(kw in msg_lower for kw in ["分析", "对比", "评估", "analyze", "compare", "evaluate"]):
            result.category = IntentCategory.ANALYSIS.value
            result.confidence = 0.7
        elif any(kw in msg_lower for kw in ["知识", "概念", "关系", "knowledge", "concept"]):
            result.category = IntentCategory.KNOWLEDGE_QUERY.value
            result.suggested_tools = ["knowledge_graph"]
            result.requires_tools = True
            result.confidence = 0.8
        elif any(kw in msg_lower for kw in ["上传", "下载", "文件", "file", "upload", "download"]):
            result.category = IntentCategory.FILE_OPERATION.value
            result.requires_tools = True
            result.confidence = 0.8
        elif any(kw in msg_lower for kw in ["？", "？", "吗", "呢", "what", "how", "why", "when", "where", "?"]):
            result.category = IntentCategory.QUESTION.value
            result.confidence = 0.6
        else:
            result.category = IntentCategory.CONVERSATION.value
            result.confidence = 0.4

        if len(message) > 200:
            result.complexity = "complex"
        elif len(message) > 50:
            result.complexity = "moderate"

        return result

    async def _llm_classify(self, user_message: str, context: dict[str, Any] | None = None) -> IntentResult:
        prompt = (
            f"分析以下用户消息的意图，返回 JSON：\n\n"
            f"用户消息：{user_message}\n\n"
            f'返回格式：{{"category": "question|command|creation|analysis|conversation|tool_request|knowledge_query|file_operation|unclear", '
            f'"confidence": 0.9, "sub_intents": [], "entities": {{}}, '
            f'"requires_tools": true/false, "suggested_tools": [], "complexity": "simple|moderate|complex"}}'
        )

        try:
            response = await llm_client.chat(
                messages=[{"role": "user", "content": prompt}],
                model=self._model, temperature=0.1, max_tokens=300,
            )
            text = await llm_client.extract_content(response)
            if text:
                return self._parse_result(text)
        except Exception as e:
            logger.warning("llm_intent_classify_error", error=str(e))

        return IntentResult()

    def _parse_result(self, text: str) -> IntentResult:
        try:
            json_str = text
            if "```json" in text:
                json_str = text.split("```json")[1].split("```")[0]
            elif "```" in text:
                json_str = text.split("```")[1].split("```")[0]
            data = json.loads(json_str.strip())
            return IntentResult(
                category=data.get("category", IntentCategory.UNCLEAR.value),
                confidence=data.get("confidence", 0.0),
                sub_intents=data.get("sub_intents", []),
                entities=data.get("entities", {}),
                requires_tools=data.get("requires_tools", False),
                suggested_tools=data.get("suggested_tools", []),
                complexity=data.get("complexity", "simple"),
            )
        except (json.JSONDecodeError, IndexError):
            return IntentResult()

    def _cache_result(self, message: str, result: IntentResult) -> None:
        if self._use_cache:
            self._cache[message[:200]] = (time.time(), result)
            if len(self._cache) > 1000:
                oldest = sorted(self._cache.items(), key=lambda x: x[1][0])[:500]
                for k, _ in oldest:
                    del self._cache[k]


@dataclass
class ProcessHistoryEntry:
    session_id: str = ""
    step: str = ""
    input_data: str = ""
    output_data: str = ""
    model: str = ""
    token_usage: int = 0
    duration_ms: int = 0
    timestamp: float = 0.0
    metadata: dict[str, Any] = field(default_factory=dict)


class AIProcessHistoryService:
    def __init__(self, max_entries_per_session: int = 1000, retention_days: int = 30):
        self._max_entries = max_entries_per_session
        self._retention_days = retention_days
        self._history: dict[str, list[ProcessHistoryEntry]] = {}

    def record(self, session_id: str, step: str, input_data: str = "",
               output_data: str = "", model: str = "", token_usage: int = 0,
               duration_ms: int = 0, **metadata: Any) -> None:
        entry = ProcessHistoryEntry(
            session_id=session_id, step=step, input_data=input_data[:2000],
            output_data=output_data[:2000], model=model, token_usage=token_usage,
            duration_ms=duration_ms, timestamp=time.time(), metadata=metadata,
        )
        self._history.setdefault(session_id, []).append(entry)

        if len(self._history[session_id]) > self._max_entries:
            self._history[session_id] = self._history[session_id][-self._max_entries:]

    def get_history(self, session_id: str, step: str | None = None, limit: int = 50) -> list[dict[str, Any]]:
        entries = self._history.get(session_id, [])
        if step:
            entries = [e for e in entries if e.step == step]
        return [
            {"step": e.step, "input": e.input_data[:200], "output": e.output_data[:200],
             "model": e.model, "tokens": e.token_usage, "duration_ms": e.duration_ms,
             "timestamp": e.timestamp, "metadata": e.metadata}
            for e in entries[-limit:]
        ]

    def get_session_summary(self, session_id: str) -> dict[str, Any]:
        entries = self._history.get(session_id, [])
        if not entries:
            return {"session_id": session_id, "total_steps": 0}

        total_tokens = sum(e.token_usage for e in entries)
        total_duration = sum(e.duration_ms for e in entries)
        step_counts: dict[str, int] = {}
        for e in entries:
            step_counts[e.step] = step_counts.get(e.step, 0) + 1

        return {
            "session_id": session_id,
            "total_steps": len(entries),
            "total_tokens": total_tokens,
            "total_duration_ms": total_duration,
            "step_counts": step_counts,
            "first_step": entries[0].timestamp,
            "last_step": entries[-1].timestamp,
        }

    def cleanup_old_sessions(self) -> int:
        cutoff = time.time() - self._retention_days * 86400
        removed = 0
        for sid in list(self._history.keys()):
            entries = self._history[sid]
            if entries and entries[-1].timestamp < cutoff:
                del self._history[sid]
                removed += 1
        return removed


llm_intent_classifier = LLMIntentClassifier()
ai_process_history_service = AIProcessHistoryService()