from __future__ import annotations

import time
import structlog
from enum import Enum
from dataclasses import dataclass, field
from typing import Any

logger = structlog.get_logger()


class SessionState(str, Enum):
    IDLE = "idle"
    EXECUTING = "executing"
    PAUSED = "paused"
    CANCELLED = "cancelled"


class InterruptMode(str, Enum):
    CANCEL = "cancel"
    APPEND = "append"


@dataclass
class AgentSessionState:
    state: SessionState = SessionState.IDLE
    interrupt_mode: InterruptMode | None = None
    appended_context: str | None = None
    start_time: float = 0.0


class AgentSessionManager:
    def __init__(self):
        self._active_sessions: dict[str, AgentSessionState] = {}

    def start_execution(self, session_id: str) -> None:
        state = self._active_sessions.setdefault(session_id, AgentSessionState())
        state.state = SessionState.EXECUTING
        state.interrupt_mode = None
        state.appended_context = None
        state.start_time = time.time()
        logger.info("agent_session_started", session_id=session_id)

    def end_execution(self, session_id: str) -> None:
        removed = self._active_sessions.pop(session_id, None)
        if removed:
            duration = time.time() - removed.start_time
            logger.info("agent_session_ended", session_id=session_id, duration_ms=int(duration * 1000))

    def should_interrupt(self, session_id: str) -> bool:
        state = self._active_sessions.get(session_id)
        return state is not None and state.interrupt_mode is not None

    def get_interrupt_mode(self, session_id: str) -> InterruptMode | None:
        state = self._active_sessions.get(session_id)
        return state.interrupt_mode if state else None

    def get_appended_context(self, session_id: str) -> str | None:
        state = self._active_sessions.get(session_id)
        return state.appended_context if state else None

    def interrupt(self, session_id: str, mode: InterruptMode, context: str | None = None) -> None:
        state = self._active_sessions.get(session_id)
        if state is None:
            logger.warning("cannot_interrupt_session_not_found", session_id=session_id)
            return
        if state.state != SessionState.EXECUTING:
            logger.warning("cannot_interrupt_wrong_state", session_id=session_id, state=state.state)
            return
        state.interrupt_mode = mode
        state.appended_context = context
        if mode == InterruptMode.CANCEL:
            state.state = SessionState.CANCELLED
        else:
            state.state = SessionState.PAUSED
        logger.info("session_interrupted", session_id=session_id, mode=mode.value)

    def pause(self, session_id: str) -> None:
        state = self._active_sessions.get(session_id)
        if state and state.state == SessionState.EXECUTING:
            state.state = SessionState.PAUSED
            logger.info("session_paused", session_id=session_id)

    def resume(self, session_id: str) -> None:
        state = self._active_sessions.get(session_id)
        if state and state.state == SessionState.PAUSED:
            state.state = SessionState.EXECUTING
            state.interrupt_mode = None
            state.appended_context = None
            logger.info("session_resumed", session_id=session_id)

    def get_session_state(self, session_id: str) -> SessionState | None:
        state = self._active_sessions.get(session_id)
        return state.state if state else None

    def is_executing(self, session_id: str) -> bool:
        state = self._active_sessions.get(session_id)
        return state is not None and state.state == SessionState.EXECUTING

    def get_active_sessions(self) -> list[str]:
        return [
            sid for sid, state in self._active_sessions.items()
            if state.state == SessionState.EXECUTING
        ]

    def get_all_sessions(self) -> dict[str, dict[str, Any]]:
        return {
            sid: {
                "state": state.state.value,
                "interrupt_mode": state.interrupt_mode.value if state.interrupt_mode else None,
                "start_time": state.start_time,
                "duration_ms": int((time.time() - state.start_time) * 1000) if state.start_time else 0,
            }
            for sid, state in self._active_sessions.items()
        }


agent_session_manager = AgentSessionManager()