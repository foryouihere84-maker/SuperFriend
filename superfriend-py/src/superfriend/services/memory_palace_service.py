from __future__ import annotations

import structlog
from dataclasses import dataclass, field
from typing import Any
from enum import Enum

logger = structlog.get_logger()


class MemoryType(str, Enum):
    FACT = "fact"
    PREFERENCE = "preference"
    EXPERIENCE = "experience"
    SKILL = "skill"
    CONTEXT = "context"


class MemoryStrength(str, Enum):
    WEAK = "weak"
    MODERATE = "moderate"
    STRONG = "strong"


@dataclass
class MemoryPalace:
    id: int | None = None
    user_id: int | None = None
    session_id: str = ""
    title: str = ""
    content: str = ""
    memory_type: MemoryType = MemoryType.FACT
    strength: MemoryStrength = MemoryStrength.MODERATE
    tags: list[str] = field(default_factory=list)
    trigger_keywords: list[str] = field(default_factory=list)
    access_count: int = 0
    importance: float = 0.5
    created_at: str = ""
    last_accessed: str = ""


@dataclass
class MemoryConnection:
    id: int | None = None
    source_id: int | None = None
    target_id: int | None = None
    connection_type: str = "associated"
    strength: float = 0.5


@dataclass
class TriggerResult:
    should_remember: bool = False
    memory_type: MemoryType = MemoryType.FACT
    importance: float = 0.5
    title: str = ""
    trigger_keywords: list[str] = field(default_factory=list)


MEMORY_TRIGGER_PATTERNS = [
            ("记住", "记住", MemoryType.FACT, 0.8),
            ("我喜欢", "我喜欢", MemoryType.PREFERENCE, 0.7),
            ("我偏好", "我偏好", MemoryType.PREFERENCE, 0.7),
            ("我习惯", "我习惯", MemoryType.EXPERIENCE, 0.6),
            ("我的名字", "我的名字", MemoryType.FACT, 0.9),
            ("我是", "我是", MemoryType.FACT, 0.7),
            ("经验", "经验", MemoryType.EXPERIENCE, 0.5),
            ("技巧", "技巧", MemoryType.SKILL, 0.6),
            ("方法", "方法", MemoryType.SKILL, 0.5),
        ]


class MemoryTriggerAnalyzer:
    def analyze(self, user_message: str, assistant_reply: str = "") -> TriggerResult:
        result = TriggerResult()
        combined = user_message + " " + assistant_reply

        for keyword, pattern, mem_type, importance in MEMORY_TRIGGER_PATTERNS:
            if pattern in combined:
                result.should_remember = True
                result.memory_type = mem_type
                result.importance = max(result.importance, importance)
                result.trigger_keywords.append(keyword)

        if result.should_remember:
            result.title = user_message[:50]
            if not result.trigger_keywords:
                result.trigger_keywords = user_message.split()[:5]

        return result


class MemoryPalaceService:
    def __init__(self):
        self._memories: dict[int, MemoryPalace] = {}
        self._connections: dict[int, MemoryConnection] = {}
        self._user_memories: dict[int, list[int]] = {}
        self._trigger_analyzer = MemoryTriggerAnalyzer()
        self._next_id: int = 1
        self._next_conn_id: int = 1

    def create_memory(self, user_id: int, title: str, content: str,
                      memory_type: MemoryType = MemoryType.FACT,
                      session_id: str = "", tags: list[str] | None = None,
                      trigger_keywords: list[str] | None = None,
                      importance: float = 0.5) -> MemoryPalace:
        mem_id = self._next_id
        self._next_id += 1

        memory = MemoryPalace(
            id=mem_id, user_id=user_id, session_id=session_id,
            title=title, content=content, memory_type=memory_type,
            tags=tags or [], trigger_keywords=trigger_keywords or [],
            importance=importance,
        )
        self._memories[mem_id] = memory
        self._user_memories.setdefault(user_id, []).append(mem_id)
        return memory

    def create_from_conversation(self, user_id: int, session_id: str,
                                  user_message: str, assistant_reply: str) -> MemoryPalace | None:
        trigger = self._trigger_analyzer.analyze(user_message, assistant_reply)
        if not trigger.should_remember:
            return None

        return self.create_memory(
            user_id=user_id, session_id=session_id,
            title=trigger.title, content=user_message,
            memory_type=trigger.memory_type,
            trigger_keywords=trigger.trigger_keywords,
            importance=trigger.importance,
        )

    def get_memory(self, memory_id: int) -> MemoryPalace | None:
        return self._memories.get(memory_id)

    def get_user_memories(self, user_id: int, memory_type: MemoryType | None = None) -> list[MemoryPalace]:
        mem_ids = self._user_memories.get(user_id, [])
        memories = [self._memories[mid] for mid in mem_ids if mid in self._memories]
        if memory_type:
            memories = [m for m in memories if m.memory_type == memory_type]
        return sorted(memories, key=lambda m: m.importance, reverse=True)

    def search_memories(self, user_id: int, query: str, limit: int = 10) -> list[MemoryPalace]:
        all_memories = self.get_user_memories(user_id)
        query_lower = query.lower()
        scored = []
        for mem in all_memories:
            score = 0.0
            if query_lower in mem.title.lower():
                score += 3.0
            if query_lower in mem.content.lower():
                score += 2.0
            if any(query_lower in kw.lower() for kw in mem.trigger_keywords):
                score += 2.5
            if any(query_lower in tag.lower() for tag in mem.tags):
                score += 1.5
            score += mem.importance * 0.5
            if score > 0:
                scored.append((mem, score))
        scored.sort(key=lambda x: x[1], reverse=True)
        return [m for m, _ in scored[:limit]]

    def update_memory(self, memory_id: int, **kwargs: Any) -> MemoryPalace | None:
        memory = self._memories.get(memory_id)
        if not memory:
            return None
        for k, v in kwargs.items():
            if hasattr(memory, k):
                setattr(memory, k, v)
        return memory

    def delete_memory(self, memory_id: int) -> bool:
        if memory_id not in self._memories:
            return False
        mem = self._memories.pop(memory_id)
        if mem.user_id and mem.user_id in self._user_memories:
            self._user_memories[mem.user_id] = [
                mid for mid in self._user_memories[mem.user_id] if mid != memory_id
            ]
        return True

    def add_connection(self, source_id: int, target_id: int,
                       connection_type: str = "associated", strength: float = 0.5) -> MemoryConnection | None:
        if source_id not in self._memories or target_id not in self._memories:
            return None
        conn_id = self._next_conn_id
        self._next_conn_id += 1
        conn = MemoryConnection(
            id=conn_id, source_id=source_id, target_id=target_id,
            connection_type=connection_type, strength=strength,
        )
        self._connections[conn_id] = conn
        return conn

    def get_related_memories(self, memory_id: int) -> list[MemoryPalace]:
        related_ids = set()
        for conn in self._connections.values():
            if conn.source_id == memory_id:
                related_ids.add(conn.target_id)
            elif conn.target_id == memory_id:
                related_ids.add(conn.source_id)
        return [self._memories[mid] for mid in related_ids if mid in self._memories]


memory_palace_service = MemoryPalaceService()