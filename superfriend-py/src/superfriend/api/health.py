from __future__ import annotations

import time
import platform
import structlog
from fastapi import APIRouter

from superfriend.services.agent_session_manager import agent_session_manager
from superfriend.services.cost_tracking_service import cost_tracking_service

router = APIRouter(tags=["health"])
logger = structlog.get_logger()


@router.get("/health")
async def health_check():
    return {
        "status": "UP",
        "timestamp": int(time.time() * 1000),
        "python": platform.python_version(),
        "platform": platform.system(),
    }


@router.get("/info")
async def app_info():
    cost_summary = cost_tracking_service.get_summary()
    sessions = agent_session_manager.get_all_sessions()
    return {
        "name": "SuperFriend",
        "version": "2.0.0-py",
        "framework": "FastAPI + LangGraph",
        "active_sessions": len(sessions),
        "cost_summary": cost_summary,
    }