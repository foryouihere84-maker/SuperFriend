from __future__ import annotations

import structlog
from fastapi import APIRouter, Query
from datetime import date

from superfriend.services.cost_tracking_service import cost_tracking_service
from superfriend.services.monitor_service import llm_call_monitor, execution_trace_service

router = APIRouter(prefix="/cost", tags=["cost-tracking"])
logger = structlog.get_logger()


@router.get("/metrics")
async def get_metrics():
    return cost_tracking_service.get_summary()


@router.get("/session/{session_id}")
async def get_session_cost(session_id: str):
    return cost_tracking_service.get_session_cost(session_id)


@router.get("/user/{user_id}")
async def get_user_cost(user_id: int):
    return cost_tracking_service.get_user_cost(user_id)


@router.get("/daily/{target_date}")
async def get_daily_cost(target_date: str):
    try:
        d = date.fromisoformat(target_date)
    except ValueError:
        d = date.today()
    return cost_tracking_service.get_daily_cost(d)


@router.get("/llm-monitor/{session_id}")
async def get_llm_monitor(session_id: str):
    return {
        "records": llm_call_monitor.get_session_records(session_id),
        "stats": llm_call_monitor.get_session_stats(session_id),
    }


@router.get("/traces/{session_id}")
async def get_traces(session_id: str):
    return {"traces": execution_trace_service.get_session_traces(session_id)}


@router.get("/trace/{trace_id}")
async def get_trace_detail(trace_id: str):
    detail = execution_trace_service.get_trace_detail(trace_id)
    if not detail:
        return {"error": "Trace not found"}
    return detail