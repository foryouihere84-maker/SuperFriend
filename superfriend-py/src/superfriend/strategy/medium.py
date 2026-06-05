from __future__ import annotations

import structlog
from typing import Any

from superfriend.constant.chat_mode import ChatMode
from superfriend.models.intent import UserIntent
from superfriend.services.llm_client import llm_client
from superfriend.services.context_manager import context_manager
from superfriend.tools.registry import default_registry
from superfriend.services.skill_registry import skill_registry
from superfriend.strategy.base import ChatModeStrategy, OnResponseFn
from superfriend.graph.main_graph import create_default_agent

logger = structlog.get_logger()


class MediumChatStrategy(ChatModeStrategy):
    def __init__(self):
        self._graph = create_default_agent(
            tool_registry=default_registry,
        )

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
        logger.info("medium_chat_start", session_id=session_id, model=model)

        skills = skill_registry.match_skills(message)
        if skills:
            skill_names = [s.name for s, _ in skills[:3]]
            logger.info("medium_skills_matched", skills=skill_names)

        full_content = ""
        try:
            from superfriend.graph.state import AgentState

            state = AgentState(
                session_id=session_id,
                user_id=user_id,
                model=model,
                user_message=message,
                enable_planning=True,
                enable_skills=bool(skills),
            )

            config = {"configurable": {"thread_id": session_id}}

            async for event in self._graph.astream(
                state.model_dump() if hasattr(state, "model_dump") else state.__dict__,
                config,
            ):
                if isinstance(event, dict):
                    for node_name, node_output in event.items():
                        if isinstance(node_output, dict):
                            content = node_output.get("content", "")
                            thought = node_output.get("current_thought", "")
                            observation = node_output.get("current_observation", "")

                            if content:
                                full_content += content
                                on_response({
                                    "type": "content",
                                    "content": content,
                                    "session_id": session_id,
                                    "model": model,
                                    "node": node_name,
                                    "done": False,
                                })
                            elif thought:
                                on_response({
                                    "type": "thinking",
                                    "content": thought,
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

            context_manager.save_context(
                session_id=session_id,
                user_id=user_id,
                model=model,
                chat_mode=ChatMode.MEDIUM_TASK,
                user_message=message,
                assistant_content=full_content or "Task completed",
            )

        except Exception as e:
            logger.error("medium_chat_error", error=str(e))
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
        logger.info("medium_multimodal_chat_start", session_id=session_id, model=multimodal_model)

        await self.execute_chat(
            session_id=session_id,
            message=message,
            model=multimodal_model,
            history=history,
            intent=intent,
            on_response=on_response,
            user_id=user_id,
        )

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
        await self.execute_chat(
            session_id=session_id,
            message=enhanced_message,
            model=model,
            history=history,
            intent=intent,
            on_response=on_response,
            user_id=user_id,
        )

    def get_chat_mode(self) -> str:
        return ChatMode.MEDIUM_TASK

    def _select_multimodal_model(self, model: str) -> str:
        if any(m in model.lower() for m in ["vision", "multimodal", "4o", "gpt-4"]):
            return model
        return "gpt-4o"