from __future__ import annotations

import json
import time
import structlog
from dataclasses import dataclass, field
from typing import Any
from datetime import datetime

logger = structlog.get_logger()


@dataclass
class LLMCallRecord:
    request_id: str = ""
    session_id: str = ""
    model: str = ""
    input_tokens: int = 0
    output_tokens: int = 0
    duration_ms: float = 0.0
    success: bool = True
    error: str = ""
    timestamp: float = 0.0
    request_summary: str = ""
    response_summary: str = ""


@dataclass
class TraceEvent:
    event_id: str = ""
    timestamp: float = 0.0
    event_type: str = ""
    name: str = ""
    description: str = ""
    duration_ms: float = 0.0
    input_data: dict[str, Any] = field(default_factory=dict)
    output_data: dict[str, Any] = field(default_factory=dict)


@dataclass
class ExecutionTrace:
    trace_id: str = ""
    session_id: str = ""
    start_time: float = 0.0
    end_time: float = 0.0
    duration_ms: float = 0.0
    status: str = "running"
    user_message: str = ""
    model: str = ""
    user_id: int | None = None
    events: list[TraceEvent] = field(default_factory=list)
    error: str = ""


class LLMCallMonitorService:
    def __init__(self):
        self._session_records: dict[str, list[LLMCallRecord]] = {}
        self._max_records_per_session: int = 50

    def record_call(self, record: LLMCallRecord) -> None:
        records = self._session_records.setdefault(record.session_id, [])
        records.append(record)
        if len(records) > self._max_records_per_session:
            self._session_records[record.session_id] = records[-self._max_records_per_session:]

    def get_session_records(self, session_id: str) -> list[dict[str, Any]]:
        records = self._session_records.get(session_id, [])
        return [
            {
                "request_id": r.request_id,
                "model": r.model,
                "input_tokens": r.input_tokens,
                "output_tokens": r.output_tokens,
                "duration_ms": r.duration_ms,
                "success": r.success,
                "error": r.error,
                "timestamp": r.timestamp,
            }
            for r in records
        ]

    def get_session_stats(self, session_id: str) -> dict[str, Any]:
        records = self._session_records.get(session_id, [])
        if not records:
            return {"session_id": session_id, "total_calls": 0}

        total_input = sum(r.input_tokens for r in records)
        total_output = sum(r.output_tokens for r in records)
        success_count = sum(1 for r in records if r.success)

        return {
            "session_id": session_id,
            "total_calls": len(records),
            "successful_calls": success_count,
            "failed_calls": len(records) - success_count,
            "total_input_tokens": total_input,
            "total_output_tokens": total_output,
            "avg_duration_ms": sum(r.duration_ms for r in records) / len(records),
        }


class ExecutionTraceService:
    def __init__(self):
        self._active_traces: dict[str, ExecutionTrace] = {}
        self._session_traces: dict[str, list[ExecutionTrace]] = {}
        self._trace_counter: int = 0

    def start_trace(self, session_id: str, user_message: str = "", model: str = "", user_id: int | None = None) -> str:
        self._trace_counter += 1
        trace_id = f"trace_{self._trace_counter}_{int(time.time())}"

        trace = ExecutionTrace(
            trace_id=trace_id,
            session_id=session_id,
            start_time=time.time(),
            status="running",
            user_message=user_message,
            model=model,
            user_id=user_id,
        )
        self._active_traces[trace_id] = trace
        self._session_traces.setdefault(session_id, []).append(trace)
        return trace_id

    def add_event(self, trace_id: str, event: TraceEvent) -> None:
        trace = self._active_traces.get(trace_id)
        if trace:
            event.timestamp = time.time()
            trace.events.append(event)

    def end_trace(self, trace_id: str, status: str = "completed", error: str = "") -> None:
        trace = self._active_traces.get(trace_id)
        if trace:
            trace.end_time = time.time()
            trace.duration_ms = (trace.end_time - trace.start_time) * 1000
            trace.status = status
            trace.error = error

    def get_session_traces(self, session_id: str) -> list[dict[str, Any]]:
        traces = self._session_traces.get(session_id, [])
        return [
            {
                "trace_id": t.trace_id,
                "status": t.status,
                "duration_ms": t.duration_ms,
                "event_count": len(t.events),
                "user_message": t.user_message[:200],
                "model": t.model,
                "error": t.error,
            }
            for t in traces
        ]

    def get_trace_detail(self, trace_id: str) -> dict[str, Any] | None:
        trace = self._active_traces.get(trace_id)
        if not trace:
            for traces in self._session_traces.values():
                for t in traces:
                    if t.trace_id == trace_id:
                        trace = t
                        break
        if not trace:
            return None

        return {
            "trace_id": trace.trace_id,
            "session_id": trace.session_id,
            "status": trace.status,
            "duration_ms": trace.duration_ms,
            "user_message": trace.user_message,
            "model": trace.model,
            "events": [
                {
                    "event_id": e.event_id,
                    "type": e.event_type,
                    "name": e.name,
                    "description": e.description,
                    "duration_ms": e.duration_ms,
                }
                for e in trace.events
            ],
            "error": trace.error,
        }


llm_call_monitor = LLMCallMonitorService()
execution_trace_service = ExecutionTraceService()