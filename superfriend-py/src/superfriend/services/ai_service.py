from __future__ import annotations

import structlog
from typing import Any

from superfriend.services.llm_client import llm_client
from superfriend.services.context_manager import context_manager
from superfriend.constant.chat_mode import ChatMode
from superfriend.models.intent import UserIntent

logger = structlog.get_logger()


class AIService:
    async def chat_stream(
        self,
        message: str,
        session_id: str,
        model: str,
        history: list[dict[str, Any]],
        user_id: int | None,
        on_response: Any,
    ) -> None:
        logger.info("ai_chat_start", session_id=session_id, model=model)

        messages = [{"role": "system", "content": "You are a helpful AI assistant."}]
        if history:
            messages.extend(history[-20:])
        messages.append({"role": "user", "content": message})

        assistant_content = ""
        try:
            async for chunk in llm_client.chat_stream(messages=messages, model=model):
                assistant_content += chunk
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
                chat_mode=ChatMode.AI,
                user_message=message,
                assistant_content=assistant_content,
            )

        except Exception as e:
            logger.error("ai_chat_error", error=str(e))
            on_response({
                "type": "error",
                "content": str(e),
                "session_id": session_id,
                "model": model,
                "done": True,
                "error": str(e),
            })

    async def func_stream(
        self,
        message: str,
        session_id: str,
        model: str,
        user_id: int | None,
        on_response: Any,
    ) -> None:
        logger.info("ai_func_start", session_id=session_id, model=model)

        system_prompt = (
            "You are an AI function processor. Execute the requested function and return results. "
            "Be precise and structured in your output."
        )

        messages = [
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": message},
        ]

        try:
            async for chunk in llm_client.chat_stream(messages=messages, model=model):
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

        except Exception as e:
            logger.error("ai_func_error", error=str(e))
            on_response({
                "type": "error",
                "content": str(e),
                "session_id": session_id,
                "model": model,
                "done": True,
                "error": str(e),
            })


ai_service = AIService()