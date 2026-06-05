from __future__ import annotations

from fastapi import APIRouter, HTTPException
from pydantic import BaseModel
from typing import Any

from superfriend.services.bash_sandbox_service import bash_sandbox_service
from superfriend.services.task_planner_service import task_planner_service
from superfriend.services.fallback_strategy_service import fallback_strategy_service, FallbackContext
from superfriend.services.semantic_tool_selection_service import semantic_tool_selection_service
from superfriend.services.observability_service import agent_metrics_service, execution_checkpoint_service, observability_service

router = APIRouter()


# ── Sandbox Endpoints ─────────────────────────────────────

class SandboxExecuteRequest(BaseModel):
    command: str
    session_id: str = ""
    working_dir: str = ""
    timeout: float | None = None


@router.post("/sandbox/execute")
async def execute_command(request: SandboxExecuteRequest):
    result = await bash_sandbox_service.execute(
        command=request.command, session_id=request.session_id,
        working_dir=request.working_dir, timeout=request.timeout,
    )
    return {
        "success": result.success,
        "output": result.output,
        "error": result.error,
        "exit_code": result.exit_code,
        "execution_time_ms": result.execution_time_ms,
        "blocked": result.blocked,
        "block_reason": result.block_reason,
    }


@router.get("/sandbox/sessions")
async def list_sandbox_sessions():
    return {"sessions": bash_sandbox_service.list_sessions()}


@router.delete("/sandbox/sessions/{session_id}")
async def close_sandbox_session(session_id: str):
    bash_sandbox_service.close_session(session_id)
    return {"success": True}


# ── Task Planner Endpoints ────────────────────────────────

class TaskDecomposeRequest(BaseModel):
    user_message: str
    model: str = "deepseek-chat"


@router.post("/task-planner/decompose")
async def decompose_task(request: TaskDecomposeRequest):
    plan = await task_planner_service.decompose_task(request.user_message, request.model)
    return {
        "plan_id": plan.plan_id,
        "complexity": plan.complexity.value,
        "status": plan.status,
        "subtasks": [
            {"id": st.id, "title": st.title, "description": st.description,
             "tool_name": st.tool_name, "dependencies": st.dependencies, "order": st.order}
            for st in plan.subtasks
        ],
        "error": plan.error,
    }


@router.get("/task-planner/plans/{plan_id}")
async def get_plan(plan_id: str):
    plan = task_planner_service.get_plan(plan_id)
    if not plan:
        raise HTTPException(404, "Plan not found")
    return {
        "plan_id": plan.plan_id,
        "complexity": plan.complexity.value,
        "status": plan.status,
        "subtasks": [
            {"id": st.id, "title": st.title, "status": st.status, "result": st.result}
            for st in plan.subtasks
        ],
    }


@router.post("/task-planner/check-complexity")
async def check_complexity(request: TaskDecomposeRequest):
    is_complex = await task_planner_service.is_complex_task_async(request.user_message, model=request.model)
    return {"is_complex": is_complex}


# ── Fallback Strategy Endpoints ───────────────────────────

class FallbackRequest(BaseModel):
    tool_name: str
    server_name: str = ""
    parameters: dict[str, Any] | None = None
    failure_reason: str = ""
    available_tools: list[dict[str, Any]] | None = None


@router.post("/fallback/find")
async def find_fallback(request: FallbackRequest):
    context = FallbackContext(
        original_tool_name=request.tool_name,
        original_server_name=request.server_name,
        original_parameters=request.parameters or {},
        failure_reason=request.failure_reason,
        available_tools=request.available_tools or [],
    )
    result = fallback_strategy_service.find_fallback(context)
    return {
        "has_alternative": result.has_alternative,
        "alternative_tool_name": result.alternative_tool_name,
        "alternative_server_name": result.alternative_server_name,
        "alternative_parameters": result.alternative_parameters,
        "strategy": result.strategy,
        "reason": result.reason,
    }


# ── Semantic Tool Selection Endpoints ─────────────────────

class ToolSelectRequest(BaseModel):
    user_request: str
    available_tools: list[dict[str, Any]]
    top_k: int = 5


@router.post("/tool-selection/select")
async def select_tools(request: ToolSelectRequest):
    scores = semantic_tool_selection_service.select_tools(
        request.user_request, request.available_tools, request.top_k,
    )
    return {"selections": [
        {"tool_name": s.tool_name, "server_name": s.server_name,
         "final_score": s.final_score, "reason": s.reason}
        for s in scores
    ]}


@router.get("/tool-selection/statistics")
async def get_tool_statistics(tool_name: str | None = None):
    return {"statistics": semantic_tool_selection_service.get_tool_statistics(tool_name)}


# ── Metrics Endpoints ─────────────────────────────────────

@router.get("/metrics/summary")
async def get_metrics_summary():
    return agent_metrics_service.get_summary()


@router.get("/metrics/agent")
async def get_agent_metrics(agent_name: str | None = None):
    return agent_metrics_service.get_agent_metrics(agent_name)


@router.get("/metrics/points")
async def get_metric_points(metric_type: str | None = None, session_id: str | None = None, limit: int = 100):
    points = agent_metrics_service.get_metrics(metric_type=metric_type, session_id=session_id, limit=limit)
    return {"points": [
        {"type": p.metric_type, "name": p.name, "value": p.value,
         "timestamp": p.timestamp, "session_id": p.session_id}
        for p in points
    ]}


# ── Checkpoint Endpoints ──────────────────────────────────

@router.get("/checkpoints/{session_id}")
async def get_checkpoints(session_id: str):
    checkpoints = execution_checkpoint_service.get_checkpoints(session_id)
    return {"checkpoints": [
        {"id": cp.id, "plan_id": cp.plan_id, "step_index": cp.step_index,
         "step_name": cp.step_name, "state": cp.state, "created_at": cp.created_at}
        for cp in checkpoints
    ]}


@router.post("/checkpoints/{session_id}/restore")
async def restore_checkpoint(session_id: str):
    state = execution_checkpoint_service.restore_from_checkpoint(session_id)
    if not state:
        raise HTTPException(404, "No checkpoint found")
    return {"restored": state}


@router.delete("/checkpoints/{session_id}")
async def delete_checkpoints(session_id: str):
    count = execution_checkpoint_service.delete_checkpoints(session_id)
    return {"deleted": count}


# ── Observability Endpoints ───────────────────────────────

@router.get("/observability/traces")
async def get_traces(limit: int = 50):
    return {"traces": observability_service.get_all_traces(limit)}


@router.get("/observability/traces/{trace_id}")
async def get_trace(trace_id: str):
    spans = observability_service.get_trace(trace_id)
    return {"trace_id": trace_id, "spans": spans}