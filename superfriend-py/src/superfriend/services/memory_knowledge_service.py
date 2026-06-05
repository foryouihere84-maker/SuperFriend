from __future__ import annotations

import time
import structlog
from dataclasses import dataclass, field
from typing import Any
from enum import Enum

from superfriend.services.llm_client import llm_client

logger = structlog.get_logger()


class MemoryType(str, Enum):
    SHORT_TERM = "short_term"
    LONG_TERM = "long_term"
    EPISODIC = "episodic"
    SEMANTIC = "semantic"


class MemoryStatus(str, Enum):
    ACTIVE = "active"
    ARCHIVED = "archived"
    DECAYED = "decayed"


@dataclass
class MemoryLifecycleEvent:
    memory_id: int
    event_type: str
    old_status: str = ""
    new_status: str = ""
    reason: str = ""
    timestamp: float = 0.0


class MemoryLifecycleService:
    def __init__(self, decay_threshold_days: int = 30, archive_threshold_days: int = 90,
                 max_access_boost: float = 0.3, importance_decay_rate: float = 0.01):
        self._decay_threshold_days = decay_threshold_days
        self._archive_threshold_days = archive_threshold_days
        self._max_access_boost = max_access_boost
        self._importance_decay_rate = importance_decay_rate
        self._events: list[MemoryLifecycleEvent] = []

    def should_decay(self, memory: dict[str, Any]) -> tuple[bool, str]:
        created_at = memory.get("created_at")
        if not created_at:
            return False, ""

        if isinstance(created_at, (int, float)):
            age_days = (time.time() - created_at) / 86400
        else:
            age_days = 0

        access_count = memory.get("access_count", 0)
        importance = memory.get("importance", 0.5)

        effective_importance = importance + min(access_count * 0.05, self._max_access_boost)
        effective_importance -= age_days * self._importance_decay_rate

        if age_days > self._archive_threshold_days and effective_importance < 0.2:
            return True, "archived"
        if age_days > self._decay_threshold_days and effective_importance < 0.3:
            return True, "decayed"
        return False, ""

    def process_lifecycle(self, memories: list[dict[str, Any]]) -> list[dict[str, Any]]:
        updated = []
        for memory in memories:
            status = memory.get("status", MemoryStatus.ACTIVE.value)
            if status != MemoryStatus.ACTIVE.value:
                updated.append(memory)
                continue

            should_change, new_status = self.should_decay(memory)
            if should_change:
                event = MemoryLifecycleEvent(
                    memory_id=memory.get("id", 0), event_type="lifecycle_change",
                    old_status=status, new_status=new_status,
                    reason=f"Auto {new_status} due to age and low importance",
                    timestamp=time.time(),
                )
                self._events.append(event)
                memory = {**memory, "status": new_status}

            updated.append(memory)
        return updated

    def get_events(self, memory_id: int | None = None, limit: int = 50) -> list[dict[str, Any]]:
        events = self._events
        if memory_id:
            events = [e for e in events if e.memory_id == memory_id]
        return [
            {"memory_id": e.memory_id, "event_type": e.event_type,
             "old_status": e.old_status, "new_status": e.new_status,
             "reason": e.reason, "timestamp": e.timestamp}
            for e in events[-limit:]
        ]


class NodeEnrichmentService:
    async def enrich_node(self, node: dict[str, Any], model: str = "deepseek-chat") -> dict[str, Any]:
        content = node.get("content", "")
        name = node.get("name", "")
        node_type = node.get("node_type", "concept")

        if not content and not name:
            return node

        prompt = (
            f"分析以下知识节点，生成补充信息：\n\n"
            f"名称：{name}\n类型：{node_type}\n内容：{content[:500]}\n\n"
            f"请返回 JSON：\n"
            f'{{"summary": "简要摘要", "keywords": ["关键词1", "关键词2"], '
            f'"related_concepts": ["相关概念1", "相关概念2"], '
            f'"importance_score": 0.8}}'
        )

        try:
            response = await llm_client.chat(
                messages=[{"role": "user", "content": prompt}],
                model=model, temperature=0.3, max_tokens=500,
            )
            result_text = await llm_client.extract_content(response)
            if result_text:
                enrichment = self._parse_enrichment(result_text)
                enriched = {**node}
                if enrichment.get("summary"):
                    enriched["enriched_summary"] = enrichment["summary"]
                if enrichment.get("keywords"):
                    enriched["enriched_keywords"] = enrichment["keywords"]
                if enrichment.get("related_concepts"):
                    enriched["related_concepts"] = enrichment["related_concepts"]
                if enrichment.get("importance_score"):
                    enriched["importance"] = enrichment["importance_score"]
                return enriched
        except Exception as e:
            logger.warning("node_enrichment_error", error=str(e))

        return node

    def _parse_enrichment(self, text: str) -> dict[str, Any]:
        try:
            json_str = text
            if "```json" in text:
                json_str = text.split("```json")[1].split("```")[0]
            elif "```" in text:
                json_str = text.split("```")[1].split("```")[0]
            return json.loads(json_str.strip())
        except (json.JSONDecodeError, IndexError):
            return {}


memory_lifecycle_service = MemoryLifecycleService()
node_enrichment_service = NodeEnrichmentService()