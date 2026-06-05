from __future__ import annotations

import time
import asyncio
import structlog
from dataclasses import dataclass, field
from typing import Any

from superfriend.services.permission_service import permission_service, PermissionLevel

logger = structlog.get_logger()

DEFAULT_TIMEOUT_MS = 30000


@dataclass
class ApprovalRequest:
    request_id: str = ""
    session_id: str = ""
    user_id: int | None = None
    tool_name: str = ""
    server_name: str = ""
    operation: str = ""
    arguments: str = "{}"
    permission_level: str = ""
    description: str = ""
    created_at: float = 0.0


@dataclass
class ApprovalResult:
    approved: bool = False
    reason: str = ""
    decided_by: str = "system"


@dataclass
class ApprovalDecision:
    decision: str = "denied"
    reason: str = ""
    always_allow: bool = False


@dataclass
class ApprovalLog:
    id: int | None = None
    user_id: int | None = None
    session_id: str = ""
    tool_name: str = ""
    operation: str = ""
    arguments: str = "{}"
    permission_level: str = ""
    decision: str = ""
    decided_by: str = ""
    reason: str = ""
    response_time_ms: int = 0


class PendingApproval:
    def __init__(self, request: ApprovalRequest):
        self.request = request
        self.future: asyncio.Future[ApprovalResult] | None = None
        self.created_at = time.time()
        self._timeout_handle: asyncio.TimerHandle | None = None

    def set_future(self, future: asyncio.Future[ApprovalResult]) -> None:
        self.future = future

    def set_timeout(self, handle: asyncio.TimerHandle) -> None:
        self._timeout_handle = handle

    def cancel_timeout(self) -> None:
        if self._timeout_handle:
            self._timeout_handle.cancel()


class ApprovalService:
    def __init__(self):
        self._pending: dict[str, PendingApproval] = {}
        self._logs: list[ApprovalLog] = []
        self._next_log_id: int = 1

    def submit_approval(self, session_id: str, user_id: int | None, tool_name: str,
                        server_name: str = "", arguments: dict[str, Any] | None = None,
                        permission_level: str = "") -> ApprovalRequest:
        request_id = f"apr_{int(time.time() * 1000)}_{id(tool_name) % 10000}"

        request = ApprovalRequest(
            request_id=request_id,
            session_id=session_id,
            user_id=user_id,
            tool_name=tool_name,
            server_name=server_name,
            operation=self._infer_operation(tool_name),
            arguments=str(arguments) if arguments else "{}",
            permission_level=permission_level,
            description=self._build_description(tool_name, arguments),
            created_at=time.time(),
        )

        pending = PendingApproval(request)
        self._pending[request_id] = pending
        logger.info("approval_submitted", request_id=request_id, tool=tool_name, session=session_id)
        return request

    async def wait_for_approval(self, request_id: str, timeout_ms: int = DEFAULT_TIMEOUT_MS) -> ApprovalResult:
        pending = self._pending.get(request_id)
        if not pending:
            return ApprovalResult(approved=False, reason="审批请求不存在", decided_by="system")

        loop = asyncio.get_event_loop()
        future: asyncio.Future[ApprovalResult] = loop.create_future()
        pending.set_future(future)

        def on_timeout():
            if not future.done():
                future.set_result(ApprovalResult(approved=False, reason="审批超时（30秒未响应）", decided_by="system"))
                self._pending.pop(request_id, None)
                self._log_approval(pending.request, "timed_out", "system", "审批超时", DEFAULT_TIMEOUT_MS)

        handle = loop.call_later(timeout_ms / 1000, on_timeout)
        pending.set_timeout(handle)

        try:
            return await future
        except asyncio.CancelledError:
            return ApprovalResult(approved=False, reason="等待审批被中断", decided_by="system")

    def decide(self, request_id: str, decision: ApprovalDecision) -> bool:
        pending = self._pending.get(request_id)
        if not pending:
            logger.warning("approval_decision_unknown", request_id=request_id)
            return False

        if pending.future and pending.future.done():
            logger.warning("approval_already_decided", request_id=request_id)
            return False

        response_time_ms = int((time.time() - pending.created_at) * 1000)
        request = pending.request

        if decision.decision == "approved":
            result = ApprovalResult(approved=True, reason=decision.reason, decided_by="user")
            self._log_approval(request, "approved", "user", decision.reason, response_time_ms)
            if decision.always_allow and request.user_id:
                permission_service.add_policy(
                    user_id=request.user_id, tool_name=request.tool_name,
                    operation_type=request.operation, permission_level=PermissionLevel.AUTO_ALLOW.value,
                    description=f"用户手动添加：总是允许 {request.tool_name}",
                )
        else:
            result = ApprovalResult(approved=False, reason=decision.reason, decided_by="user")
            self._log_approval(request, "denied", "user", decision.reason, response_time_ms)

        pending.cancel_timeout()
        if pending.future and not pending.future.done():
            pending.future.set_result(result)
        self._pending.pop(request_id, None)

        logger.info("approval_decided", request_id=request_id, decision=decision.decision, always_allow=decision.always_allow)
        return True

    def get_pending_approval(self, request_id: str) -> ApprovalRequest | None:
        pending = self._pending.get(request_id)
        return pending.request if pending else None

    def get_pending_approvals_for_session(self, session_id: str) -> list[ApprovalRequest]:
        return [
            p.request for p in self._pending.values()
            if p.request.session_id == session_id
        ]

    def get_approval_logs(self, user_id: int | None = None, session_id: str | None = None, limit: int = 50) -> list[ApprovalLog]:
        logs = self._logs
        if user_id:
            logs = [l for l in logs if l.user_id == user_id]
        if session_id:
            logs = [l for l in logs if l.session_id == session_id]
        return logs[-limit:]

    def _log_approval(self, request: ApprovalRequest, decision: str, decided_by: str, reason: str, response_time_ms: int) -> None:
        log_id = self._next_log_id
        self._next_log_id += 1
        self._logs.append(ApprovalLog(
            id=log_id, user_id=request.user_id, session_id=request.session_id,
            tool_name=request.tool_name, operation=request.operation,
            arguments=request.arguments, permission_level=request.permission_level,
            decision=decision, decided_by=decided_by, reason=reason,
            response_time_ms=response_time_ms,
        ))

    def _infer_operation(self, tool_name: str) -> str:
        return permission_service.infer_operation_type(tool_name)

    def _build_description(self, tool_name: str, arguments: dict[str, Any] | None) -> str:
        parts = [f"工具调用：{tool_name}"]
        if arguments:
            parts.append("参数：")
            for k, v in arguments.items():
                val = str(v)[:100]
                parts.append(f"  {k} = {val}")
        return "\n".join(parts)


approval_service = ApprovalService()