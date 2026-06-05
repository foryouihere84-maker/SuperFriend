from __future__ import annotations

import json
import structlog
from typing import Any

from superfriend.constant.chat_mode import ChatMode
from superfriend.models.intent import UserIntent
from superfriend.services.llm_client import llm_client
from superfriend.services.context_manager import context_manager
from superfriend.strategy.base import ChatModeStrategy, OnResponseFn

logger = structlog.get_logger()


class LiteChatStrategy(ChatModeStrategy):
    async def execute_chat(
        self,
        session_id: str,
        message: str,
        model: str,
        history: list[dict[str, Any]],
        intent: UserIntent,
        on_response: OnResponseFn,
        user_id: int | None = None,
    ) -> None:
        logger.info("lite_chat_start", session_id=session_id, model=model)

        messages = self._build_messages(message, history)

        full_content = ""
        try:
            async for chunk in llm_client.chat_stream(
                messages=messages,
                model=model,
                temperature=0.7,
            ):
                full_content += chunk
                on_response({
                    "type": "content",
                    "content": chunk,
                    "session_id": session_id,
                    "model": model,
                    "done": False,
                })

            on_response({
                "type": "done",
                "content": "",
                "session_id": session_id,
                "model": model,
                "done": True,
            })

            await context_manager.save_context_with_db(
                session_id=session_id,
                user_id=user_id,
                model=model,
                chat_mode=ChatMode.LITE_TASK,
                user_message=message,
                assistant_content=full_content,
            )

        except Exception as e:
            logger.error("lite_chat_error", error=str(e))
            on_response({
                "type": "error",
                "content": str(e),
                "session_id": session_id,
                "model": model,
                "done": True,
                "error": str(e),
            })

    async def execute_multimodal_chat(
        self,
        session_id: str,
        message: str,
        model: str,
        history: list[dict[str, Any]],
        intent: UserIntent,
        on_response: OnResponseFn,
        user_id: int | None = None,
    ) -> None:
        multimodal_model = self._select_multimodal_model(model)
        logger.info("lite_multimodal_chat_start", session_id=session_id, model=multimodal_model)

        messages = self._build_messages(message, history)

        full_content = ""
        try:
            async for chunk in llm_client.chat_stream(
                messages=messages,
                model=multimodal_model,
                temperature=0.7,
            ):
                full_content += chunk
                on_response({
                    "type": "content",
                    "content": chunk,
                    "session_id": session_id,
                    "model": multimodal_model,
                    "done": False,
                })

            on_response({
                "type": "done",
                "content": "",
                "session_id": session_id,
                "model": multimodal_model,
                "done": True,
            })

        except Exception as e:
            logger.error("lite_multimodal_chat_error", error=str(e))
            on_response({
                "type": "error",
                "content": str(e),
                "session_id": session_id,
                "model": multimodal_model,
                "done": True,
                "error": str(e),
            })

    async def execute_file_parsed_chat(
        self,
        session_id: str,
        enhanced_message: str,
        model: str,
        history: list[dict[str, Any]],
        intent: UserIntent,
        on_response: OnResponseFn,
        user_id: int | None = None,
    ) -> None:
        await self.execute_chat(session_id, enhanced_message, model, history, intent, on_response, user_id)

    def get_chat_mode(self) -> str:
        return ChatMode.LITE_TASK

    def _build_messages(self, message: str, history: list[dict[str, Any]]) -> list[dict[str, Any]]:
        messages: list[dict[str, Any]] = [
            {"role": "system", "content": "You are a helpful AI assistant. Respond concisely and accurately."}
        ]
        if history:
            messages.extend(history[-20:])
        messages.append({"role": "user", "content": message})
        return messages

    def _select_multimodal_model(self, model: str) -> str:
        multimodal_models = ["gpt-4o", "gpt-4-vision-preview", "claude-3-opus", "gemini-pro-vision"]
        if any(m in model.lower() for m in ["vision", "multimodal", "4o", "gpt-4"]):
            return model
        return "gpt-4o"