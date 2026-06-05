import structlog
from typing import Any, AsyncIterator

from superfriend.graph.main_graph import create_default_agent
from superfriend.graph.state import AgentState
from superfriend.models.agent_event import AgentEvent
from superfriend.services.llm_client import llm_client
from superfriend.services.context_manager import context_manager
from superfriend.tools.registry import default_registry
from superfriend.tools.mcp_tools import mcp_tool_manager

logger = structlog.get_logger()


class AgentOrchestrator:
    def __init__(self):
        self._graph = create_default_agent(
            tool_registry=default_registry,
        )

    async def run(
        self,
        user_message: str,
        session_id: str,
        model: str = "deepseek-chat",
        history: list[dict[str, Any]] | None = None,
        user_id: int | None = None,
    ) -> AsyncIterator[AgentEvent]:
        context_manager.create_session(session_id, user_id, model)
        if history:
            for msg in history:
                role = msg.get("role", "user")
                content = msg.get("content", "")
                context_manager.add_message(session_id, role, content)

        state = AgentState(
            session_id=session_id,
            user_id=user_id,
            model=model,
            user_message=user_message,
        )

        config = {"configurable": {"thread_id": session_id}}

        async for event in self._graph.astream(
            state.model_dump() if hasattr(state, "model_dump") else state.__dict__,
            config,
        ):
            if isinstance(event, dict):
                for node_name, node_output in event.items():
                    if isinstance(node_output, dict):
                        agent_events = node_output.get("events", [])
                        event_queue = node_output.get("event_queue", [])
                        for evt in agent_events + event_queue:
                            if isinstance(evt, AgentEvent):
                                yield evt

    async def run_stream(
        self,
        user_message: str,
        session_id: str,
        model: str = "deepseek-chat",
        history: list[dict[str, Any]] | None = None,
        user_id: int | None = None,
    ) -> AsyncIterator[str]:
        context_manager.create_session(session_id, user_id, model)

        if history:
            for msg in history:
                context_manager.add_message(session_id, msg.get("role", "user"), msg.get("content", ""))

        system_prompt = (
            "You are an AI assistant with tool-calling capabilities. "
            "Analyze the user's request and respond helpfully."
        )

        messages = [{"role": "system", "content": system_prompt}]
        if history:
            messages.extend(history[-20:])
        messages.append({"role": "user", "content": user_message})

        async for chunk in llm_client.chat_stream(messages, model=model):
            yield chunk

        context_manager.add_message(session_id, "user", user_message)


agent_orchestrator = AgentOrchestrator()