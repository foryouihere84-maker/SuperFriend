from __future__ import annotations

import structlog
from fastapi import APIRouter, HTTPException, Query
from pydantic import BaseModel

from superfriend.services.agent_session_manager import (
    agent_session_manager, InterruptMode,
)

router = APIRouter(prefix="/agent", tags=["agent"])
logger = structlog.get_logger()


class InterruptRequest(BaseModel):
    mode: str = "cancel"
    context: str | None = None


@router.post("/sessions/{session_id}/interrupt")
async def interrupt_session(session_id: str, request: InterruptRequest):
    mode = InterruptMode.CANCEL if request.mode == "cancel" else InterruptMode.APPEND
    agent_session_manager.interrupt(session_id, mode, request.context)
    return {"success": True, "message": "中断请求已发送"}


@router.get("/sessions/{session_id}/status")
async def get_session_status(session_id: str):
    state = agent_session_manager.get_session_state(session_id)
    return {
        "sessionId": session_id,
        "isExecuting": agent_session_manager.is_executing(session_id),
        "state": state.value if state else "none",
    }


@router.get("/sessions")
async def list_active_sessions():
    return {
        "active_sessions": agent_session_manager.get_active_sessions(),
        "all_sessions": agent_session_manager.get_all_sessions(),
    }


@router.post("/sessions/{session_id}/pause")
async def pause_session(session_id: str):
    agent_session_manager.pause(session_id)
    return {"success": True, "sessionId": session_id, "state": "paused"}


@router.post("/sessions/{session_id}/resume")
async def resume_session(session_id: str):
    agent_session_manager.resume(session_id)
    return {"success": True, "sessionId": session_id, "state": "executing"}