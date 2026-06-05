from __future__ import annotations

from fastapi import APIRouter, HTTPException
from pydantic import BaseModel
from typing import Any

from superfriend.services.permission_service import permission_service
from superfriend.services.approval_service import approval_service, ApprovalDecision

router = APIRouter()


# ── Permission Endpoints ──────────────────────────────────

class PolicyCreateRequest(BaseModel):
    user_id: int
    tool_name: str = "*"
    operation_type: str = "*"
    resource_pattern: str = ""
    permission_level: str = "auto_allow"
    description: str = ""


class PolicyUpdateRequest(BaseModel):
    tool_name: str | None = None
    operation_type: str | None = None
    resource_pattern: str | None = None
    permission_level: str | None = None
    description: str | None = None


class PermissionCheckRequest(BaseModel):
    user_id: int
    tool_name: str
    arguments: dict[str, Any] | None = None


@router.post("/permissions/check")
async def check_permission(request: PermissionCheckRequest):
    result = permission_service.check_permission(request.user_id, request.tool_name, request.arguments)
    return {
        "allowed": result.allowed,
        "level": result.level,
        "reason": result.reason,
        "needs_approval": result.needs_approval,
        "matched_policy_id": result.matched_policy_id,
    }


@router.get("/permissions/policies")
async def list_policies(user_id: int | None = None):
    if user_id:
        policies = permission_service.get_user_policies(user_id)
    else:
        policies = permission_service.get_all_policies()
    return {"policies": [
        {"id": p.id, "user_id": p.user_id, "tool_name": p.tool_name,
         "operation_type": p.operation_type, "resource_pattern": p.resource_pattern,
         "permission_level": p.permission_level, "description": p.description}
        for p in policies
    ]}


@router.post("/permissions/policies")
async def create_policy(request: PolicyCreateRequest):
    policy = permission_service.add_policy(
        user_id=request.user_id, tool_name=request.tool_name,
        operation_type=request.operation_type, resource_pattern=request.resource_pattern,
        permission_level=request.permission_level, description=request.description,
    )
    return {"success": True, "policy_id": policy.id}


@router.put("/permissions/policies/{policy_id}")
async def update_policy(policy_id: int, request: PolicyUpdateRequest):
    policy = permission_service.update_policy(policy_id, **request.model_dump(exclude_none=True))
    if not policy:
        raise HTTPException(404, "Policy not found")
    return {"success": True}


@router.delete("/permissions/policies/{policy_id}")
async def delete_policy(policy_id: int):
    deleted = permission_service.delete_policy(policy_id)
    return {"success": deleted}


# ── Approval Endpoints ────────────────────────────────────

class ApprovalSubmitRequest(BaseModel):
    session_id: str
    user_id: int | None = None
    tool_name: str
    server_name: str = ""
    arguments: dict[str, Any] | None = None
    permission_level: str = ""


class ApprovalDecideRequest(BaseModel):
    decision: str = "denied"
    reason: str = ""
    always_allow: bool = False


@router.post("/approvals/submit")
async def submit_approval(request: ApprovalSubmitRequest):
    approval_req = approval_service.submit_approval(
        session_id=request.session_id, user_id=request.user_id,
        tool_name=request.tool_name, server_name=request.server_name,
        arguments=request.arguments, permission_level=request.permission_level,
    )
    return {
        "request_id": approval_req.request_id,
        "session_id": approval_req.session_id,
        "tool_name": approval_req.tool_name,
        "description": approval_req.description,
    }


@router.post("/approvals/{request_id}/decide")
async def decide_approval(request_id: str, request: ApprovalDecideRequest):
    decision = ApprovalDecision(
        decision=request.decision, reason=request.reason,
        always_allow=request.always_allow,
    )
    success = approval_service.decide(request_id, decision)
    if not success:
        raise HTTPException(400, "Approval request not found or already decided")
    return {"success": True}


@router.get("/approvals/{request_id}")
async def get_approval(request_id: str):
    approval = approval_service.get_pending_approval(request_id)
    if not approval:
        raise HTTPException(404, "Approval request not found")
    return {
        "request_id": approval.request_id,
        "tool_name": approval.tool_name,
        "description": approval.description,
        "permission_level": approval.permission_level,
    }


@router.get("/approvals/session/{session_id}")
async def get_session_approvals(session_id: str):
    approvals = approval_service.get_pending_approvals_for_session(session_id)
    return {"approvals": [
        {"request_id": a.request_id, "tool_name": a.tool_name, "description": a.description}
        for a in approvals
    ]}


@router.get("/approvals/logs")
async def get_approval_logs(user_id: int | None = None, session_id: str | None = None, limit: int = 50):
    logs = approval_service.get_approval_logs(user_id=user_id, session_id=session_id, limit=limit)
    return {"logs": [
        {"id": l.id, "tool_name": l.tool_name, "decision": l.decision,
         "decided_by": l.decided_by, "reason": l.reason, "response_time_ms": l.response_time_ms}
        for l in logs
    ]}