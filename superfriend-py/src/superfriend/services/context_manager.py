from __future__ import annotations

import structlog
from typing import Any

from superfriend.models.agent_event import AgentEvent
from superfriend.graph.state import AgentState

logger = structlog.get_logger()


class ContextManager:
    """上下文管理器 - 同时维护内存缓存和数据库持久化"""

    def __init__(self):
        self._sessions: dict[str, dict[str, Any]] = {}
        self._messages: dict[str, list[dict[str, Any]]] = {}
        self._max_context_length: int = 100

    def create_session(self, session_id: str, user_id: int | None = None, model: str = "deepseek-chat") -> None:
        self._sessions[session_id] = {
            "user_id": user_id,
            "model": model,
            "created_at": __import__("time").time(),
            "message_count": 0,
        }
        self._messages[session_id] = []

    def add_message(self, session_id: str, role: str, content: str) -> None:
        if session_id not in self._messages:
            self._messages[session_id] = []

        self._messages[session_id].append({
            "role": role,
            "content": content,
            "timestamp": __import__("time").time(),
        })

        if session_id in self._sessions:
            self._sessions[session_id]["message_count"] = len(self._messages[session_id])

    def get_messages(self, session_id: str, limit: int | None = None) -> list[dict[str, Any]]:
        messages = self._messages.get(session_id, [])
        if limit:
            return messages[-limit:]
        return messages

    def get_history(self, session_id: str, max_messages: int = 20) -> list[dict[str, Any]]:
        messages = self.get_messages(session_id)
        return messages[-max_messages:]

    def compress_context(self, session_id: str) -> None:
        messages = self._messages.get(session_id, [])
        if len(messages) <= self._max_context_length:
            return

        system_messages = [m for m in messages if m["role"] == "system"]
        recent_messages = messages[-(self._max_context_length - len(system_messages)):]

        self._messages[session_id] = system_messages + recent_messages
        logger.info("context_compressed", session_id=session_id, kept=len(self._messages[session_id]))

    def clear_session(self, session_id: str) -> None:
        self._sessions.pop(session_id, None)
        self._messages.pop(session_id, None)

    def get_session(self, session_id: str) -> dict[str, Any] | None:
        return self._sessions.get(session_id)

    def build_agent_state(self, session_id: str, user_message: str, user_id: int | None = None) -> AgentState:
        session = self.get_session(session_id)
        if session is None:
            self.create_session(session_id, user_id)
            session = self._sessions[session_id]

        history = self.get_history(session_id)

        state = AgentState(
            session_id=session_id,
            user_id=user_id or session.get("user_id"),
            model=session.get("model", "deepseek-chat"),
            user_message=user_message,
        )

        return state

    def save_context(
        self,
        session_id: str,
        user_id: int | None,
        model: str,
        chat_mode: str,
        user_message: str,
        assistant_content: str,
    ) -> None:
        if session_id not in self._sessions:
            self.create_session(session_id, user_id, model)

        self.add_message(session_id, "user", user_message)
        self.add_message(session_id, "assistant", assistant_content)

    # ── 数据库持久化方法 ──

    async def save_message_to_db(self, session_id: str, role: str, content: str, user_id: int | None = None) -> None:
        """将消息持久化到数据库"""
        try:
            from superfriend.db.session import async_session_factory
            from superfriend.db.repository import ChatMessageRepository, ChatHistoryRepository

            async with async_session_factory() as session:
                # 确保 chat_history 存在
                history_repo = ChatHistoryRepository(session)
                history = await history_repo.get_by_session(session_id)
                if not history:
                    await history_repo.create_session(
                        session_id=session_id,
                        user_id=user_id,
                        model=self._sessions.get(session_id, {}).get("model", "deepseek-chat"),
                    )

                # 保存消息
                msg_repo = ChatMessageRepository(session)
                await msg_repo.add_message(session_id=session_id, role=role, content=content, history_id=history.id if history else None)

                # 更新消息计数
                await history_repo.update_message_count(session_id)
                await session.commit()
        except Exception as e:
            logger.warning("save_message_to_db_failed", error=str(e), session_id=session_id)

    async def load_messages_from_db(self, session_id: str, limit: int = 50) -> list[dict[str, Any]]:
        """从数据库加载历史消息"""
        try:
            from superfriend.db.session import async_session_factory
            from superfriend.db.repository import ChatMessageRepository

            async with async_session_factory() as session:
                repo = ChatMessageRepository(session)
                messages = await repo.get_by_session(session_id, limit=limit)
                return [
                    {"role": m.role, "content": m.content, "timestamp": m.created_time.timestamp() if m.created_time else 0}
                    for m in reversed(messages)  # 数据库返回倒序，需要反转
                ]
        except Exception as e:
            logger.warning("load_messages_from_db_failed", error=str(e), session_id=session_id)
            return []

    async def save_context_with_db(
        self,
        session_id: str,
        user_id: int | None,
        model: str,
        chat_mode: str,
        user_message: str,
        assistant_content: str,
    ) -> None:
        """同时保存到内存和数据库"""
        # 内存
        self.save_context(session_id, user_id, model, chat_mode, user_message, assistant_content)
        # 数据库
        await self.save_message_to_db(session_id, "user", user_message, user_id)
        await self.save_message_to_db(session_id, "assistant", assistant_content, user_id)


context_manager = ContextManager()
