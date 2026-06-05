from __future__ import annotations

from abc import ABC, abstractmethod
from typing import Any, Awaitable, Callable

from superfriend.models.intent import UserIntent

OnResponseFn = Callable[[dict[str, Any]], Any]


class ChatModeStrategy(ABC):
    @abstractmethod
    async def execute_chat(
        self,
        session_id: str,
        message: str,
        model: str,
        history: list[dict[str, Any]],
        intent: UserIntent,
        on_response: OnResponseFn,
        user_id: int | None = None,
    ) -> None: ...

    @abstractmethod
    async def execute_multimodal_chat(
        self,
        session_id: str,
        message: str,
        model: str,
        history: list[dict[str, Any]],
        intent: UserIntent,
        on_response: OnResponseFn,
        user_id: int | None = None,
    ) -> None: ...

    @abstractmethod
    async def execute_file_parsed_chat(
        self,
        session_id: str,
        enhanced_message: str,
        model: str,
        history: list[dict[str, Any]],
        intent: UserIntent,
        on_response: OnResponseFn,
        user_id: int | None = None,
    ) -> None: ...

    @abstractmethod
    def get_chat_mode(self) -> str: ...