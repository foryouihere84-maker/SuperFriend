from __future__ import annotations

import time
import structlog
from dataclasses import dataclass, field
from typing import Any
from enum import Enum

logger = structlog.get_logger()


class MetricType(str, Enum):
    TOOL_CALL = "tool_call"
    LLM_CALL = "llm_call"
    AGENT_EXECUTION = "agent_execution"
    TASK_PLAN = "task_plan"
    APPROVAL = "approval"


@dataclass
class MetricPoint:
    metric_type: str = ""
    name: str = ""
    value: float = 0.0
    timestamp: float = 0.0
    tags: dict[str, str] = field(default_factory=dict)
    session_id: str = ""
    user_id: int | None = None


@dataclass
class AgentMetrics:
    total_executions: int = 0
    successful_executions: int = 0
    failed_executions: int = 0
    average_duration_ms: float = 0.0
    total_tool_calls: int = 0
    total_llm_calls: int = 0
    total_tokens: int = 0
    total_cost_usd: float = 0.0
    last_execution_time: float = 0.0


class AgentMetricsService:
    def __init__(self, max_points: int = 10000):
        self._metrics: list[MetricPoint] = []
        self._max_points = max_points
        self._agent_metrics: dict[str, AgentMetrics] = {}

    def record(self, metric_type: str, name: str, value: float, session_id: str = "",
               user_id: int | None = None, tags: dict[str, str] | None = None) -> None:
        point = MetricPoint(
            metric_type=metric_type, name=name, value=value,
            timestamp=time.time(), session_id=session_id,
            user_id=user_id, tags=tags or {},
        )
        self._metrics.append(point)
        if len(self._metrics) > self._max_points:
            self._metrics = self._metrics[-self._max_points:]

    def record_tool_call(self, tool_name: str, success: bool, duration_ms: float, session_id: str = "") -> None:
        self.record(MetricType.TOOL_CALL, tool_name, duration_ms, session_id=session_id, tags={"success": str(success)})

    def record_llm_call(self, model: str, tokens: int, duration_ms: float, session_id: str = "") -> None:
        self.record(MetricType.LLM_CALL, model, duration_ms, session_id=session_id, tags={"tokens": str(tokens)})

    def record_agent_execution(self, agent_name: str, success: bool, duration_ms: float, session_id: str = "") -> None:
        metrics = self._agent_metrics.setdefault(agent_name, AgentMetrics())
        metrics.total_executions += 1
        if success:
            metrics.successful_executions += 1
        else:
            metrics.failed_executions += 1
        metrics.last_execution_time = time.time()
        self.record(MetricType.AGENT_EXECUTION, agent_name, duration_ms, session_id=session_id, tags={"success": str(success)})

    def get_metrics(self, metric_type: str | None = None, session_id: str | None = None,
                    since: float | None = None, limit: int = 100) -> list[MetricPoint]:
        result = self._metrics
        if metric_type:
            result = [m for m in result if m.metric_type == metric_type]
        if session_id:
            result = [m for m in result if m.session_id == session_id]
        if since:
            result = [m for m in result if m.timestamp >= since]
        return result[-limit:]

    def get_agent_metrics(self, agent_name: str | None = None) -> dict[str, Any]:
        if agent_name:
            m = self._agent_metrics.get(agent_name)
            return self._metrics_to_dict(agent_name, m) if m else {}
        return {name: self._metrics_to_dict(name, m) for name, m in self._agent_metrics.items()}

    def _metrics_to_dict(self, name: str, m: AgentMetrics) -> dict[str, Any]:
        return {
            "agent": name,
            "total_executions": m.total_executions,
            "successful_executions": m.successful_executions,
            "failed_executions": m.failed_executions,
            "success_rate": m.successful_executions / max(m.total_executions, 1),
            "average_duration_ms": m.average_duration_ms,
        }

    def get_summary(self) -> dict[str, Any]:
        total = len(self._metrics)
        by_type: dict[str, int] = {}
        for m in self._metrics:
            by_type[m.metric_type] = by_type.get(m.metric_type, 0) + 1
        return {"total_points": total, "by_type": by_type, "agents_tracked": len(self._agent_metrics)}


agent_metrics_service = AgentMetricsService()


# ── Execution Checkpoint Service ──────────────────────────

@dataclass
class ExecutionCheckpoint:
    id: int | None = None
    session_id: str = ""
    plan_id: str = ""
    step_index: int = 0
    step_name: str = ""
    state: dict[str, Any] = field(default_factory=dict)
    created_at: float = 0.0


class ExecutionCheckpointService:
    def __init__(self):
        self._checkpoints: dict[int, ExecutionCheckpoint] = {}
        self._next_id: int = 1

    def save_checkpoint(self, session_id: str, plan_id: str, step_index: int,
                        step_name: str, state: dict[str, Any]) -> ExecutionCheckpoint:
        cp_id = self._next_id
        self._next_id += 1
        cp = ExecutionCheckpoint(
            id=cp_id, session_id=session_id, plan_id=plan_id,
            step_index=step_index, step_name=step_name,
            state=state, created_at=time.time(),
        )
        self._checkpoints[cp_id] = cp
        logger.info("checkpoint_saved", session_id=session_id, step=step_name)
        return cp

    def get_latest_checkpoint(self, session_id: str) -> ExecutionCheckpoint | None:
        session_cps = [c for c in self._checkpoints.values() if c.session_id == session_id]
        if not session_cps:
            return None
        return max(session_cps, key=lambda c: c.step_index)

    def get_checkpoints(self, session_id: str) -> list[ExecutionCheckpoint]:
        return sorted(
            [c for c in self._checkpoints.values() if c.session_id == session_id],
            key=lambda c: c.step_index,
        )

    def restore_from_checkpoint(self, session_id: str) -> dict[str, Any] | None:
        cp = self.get_latest_checkpoint(session_id)
        if not cp:
            return None
        return {"plan_id": cp.plan_id, "step_index": cp.step_index, "step_name": cp.step_name, "state": cp.state}

    def delete_checkpoints(self, session_id: str) -> int:
        to_delete = [cid for cid, c in self._checkpoints.items() if c.session_id == session_id]
        for cid in to_delete:
            del self._checkpoints[cid]
        return len(to_delete)


execution_checkpoint_service = ExecutionCheckpointService()


# ── Observability Service (Langfuse) ──────────────────────

@dataclass
class TraceSpan:
    trace_id: str = ""
    span_id: str = ""
    parent_span_id: str = ""
    name: str = ""
    start_time: float = 0.0
    end_time: float = 0.0
    status: str = "ok"
    metadata: dict[str, Any] = field(default_factory=dict)
    input_data: str = ""
    output_data: str = ""


class ObservabilityService:
    def __init__(self, enabled: bool = False, public_key: str = "", secret_key: str = "", host: str = "https://cloud.langfuse.com"):
        self.enabled = enabled
        self.public_key = public_key
        self.secret_key = secret_key
        self.host = host
        self._traces: dict[str, list[TraceSpan]] = {}
        self._next_span_id: int = 1

    def start_trace(self, trace_id: str, name: str, metadata: dict[str, Any] | None = None) -> str:
        if not self.enabled:
            return trace_id
        span = TraceSpan(
            trace_id=trace_id, span_id=f"span_{self._next_span_id}",
            name=name, start_time=time.time(), metadata=metadata or {},
        )
        self._next_span_id += 1
        self._traces.setdefault(trace_id, []).append(span)
        return span.span_id

    def end_trace(self, trace_id: str, span_id: str, output_data: str = "", status: str = "ok") -> None:
        if not self.enabled:
            return
        spans = self._traces.get(trace_id, [])
        for span in spans:
            if span.span_id == span_id:
                span.end_time = time.time()
                span.output_data = output_data[:5000]
                span.status = status
                break

    def add_span(self, trace_id: str, name: str, parent_span_id: str = "",
                 input_data: str = "", metadata: dict[str, Any] | None = None) -> str:
        if not self.enabled:
            return ""
        span = TraceSpan(
            trace_id=trace_id, span_id=f"span_{self._next_span_id}",
            parent_span_id=parent_span_id, name=name,
            start_time=time.time(), input_data=input_data[:5000],
            metadata=metadata or {},
        )
        self._next_span_id += 1
        self._traces.setdefault(trace_id, []).append(span)
        return span.span_id

    def get_trace(self, trace_id: str) -> list[dict[str, Any]]:
        spans = self._traces.get(trace_id, [])
        return [
            {
                "span_id": s.span_id, "parent_span_id": s.parent_span_id,
                "name": s.name, "start_time": s.start_time, "end_time": s.end_time,
                "duration_ms": int((s.end_time - s.start_time) * 1000) if s.end_time else 0,
                "status": s.status,
            }
            for s in spans
        ]

    def get_all_traces(self, limit: int = 50) -> dict[str, Any]:
        traces = {}
        for tid, spans in list(self._traces.items())[-limit:]:
            completed = [s for s in spans if s.end_time > 0]
            traces[tid] = {
                "span_count": len(spans),
                "completed_spans": len(completed),
                "total_duration_ms": sum(int((s.end_time - s.start_time) * 1000) for s in completed),
            }
        return traces


observability_service = ObservabilityService()