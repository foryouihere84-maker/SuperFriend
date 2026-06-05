from __future__ import annotations

import re
import time
import structlog
from dataclasses import dataclass, field
from typing import Any
from enum import Enum

logger = structlog.get_logger()


class PermissionLevel(str, Enum):
    AUTO_ALLOW = "auto_allow"
    REQUIRE_APPROVAL = "require_approval"
    DENY = "deny"


class OperationType(str, Enum):
    READ = "read"
    WRITE = "write"
    DELETE = "delete"
    EXECUTE = "execute"


READ_TOOL_PATTERNS = {"read_file", "list_directory", "get_", "search_", "query_", "get_system_info", "list_files", "read_graph", "search_nodes"}
WRITE_TOOL_PATTERNS = {"write_file", "create_", "save_", "upload_", "send_", "create_entities", "create_relations"}
DELETE_TOOL_PATTERNS = {"delete_", "remove_", "batch_delete", "rm_"}
EXECUTE_TOOL_PATTERNS = {"run_command", "execute_", "shell_", "navigate_", "click_", "install_", "kill_", "browser"}


@dataclass
class PermissionPolicy:
    id: int | None = None
    user_id: int = 0
    tool_name: str = ""
    operation_type: str = "execute"
    resource_pattern: str = ""
    permission_level: str = PermissionLevel.AUTO_ALLOW.value
    description: str = ""


@dataclass
class PermissionCheckResult:
    allowed: bool = True
    level: str = PermissionLevel.AUTO_ALLOW.value
    reason: str = ""
    matched_policy_id: int | None = None
    needs_approval: bool = False

    @classmethod
    def auto_allow(cls, reason: str = "") -> PermissionCheckResult:
        return cls(allowed=True, level=PermissionLevel.AUTO_ALLOW.value, reason=reason)

    @classmethod
    def require_approval(cls, reason: str = "") -> PermissionCheckResult:
        return cls(allowed=False, level=PermissionLevel.REQUIRE_APPROVAL.value, reason=reason, needs_approval=True)

    @classmethod
    def denied(cls, reason: str = "") -> PermissionCheckResult:
        return cls(allowed=False, level=PermissionLevel.DENY.value, reason=reason)


class PermissionService:
    def __init__(self):
        self._policies: dict[int, PermissionPolicy] = {}
        self._next_id: int = 1

    def check_permission(self, user_id: int, tool_name: str, arguments: dict[str, Any] | None = None) -> PermissionCheckResult:
        operation_type = self.infer_operation_type(tool_name)
        effective_user_id = user_id or 0

        matching = [
            p for p in self._policies.values()
            if p.user_id == effective_user_id
            and (p.tool_name == "*" or p.tool_name == tool_name or tool_name.startswith(p.tool_name))
            and (p.operation_type == "*" or p.operation_type == operation_type)
        ]

        if not matching:
            return PermissionCheckResult.auto_allow("默认允许所有操作")

        for policy in matching:
            if policy.permission_level == PermissionLevel.AUTO_ALLOW.value:
                if self._matches_resource_pattern(policy, arguments):
                    return PermissionCheckResult.auto_allow(
                        policy.description or "策略自动允许",
                        matched_policy_id=policy.id,
                    )
            elif policy.permission_level == PermissionLevel.DENY.value:
                if self._matches_resource_pattern(policy, arguments):
                    return PermissionCheckResult.denied(policy.description or "策略拒绝")
            elif policy.permission_level == PermissionLevel.REQUIRE_APPROVAL.value:
                if self._matches_resource_pattern(policy, arguments):
                    return PermissionCheckResult.require_approval(policy.description or "需要审批")

        return PermissionCheckResult.auto_allow("默认允许所有操作")

    def infer_operation_type(self, tool_name: str) -> str:
        if not tool_name:
            return OperationType.EXECUTE.value
        lower = tool_name.lower()
        for pattern in DELETE_TOOL_PATTERNS:
            if pattern in lower:
                return OperationType.DELETE.value
        for pattern in WRITE_TOOL_PATTERNS:
            if pattern in lower:
                return OperationType.WRITE.value
        for pattern in READ_TOOL_PATTERNS:
            if pattern in lower:
                return OperationType.READ.value
        for pattern in EXECUTE_TOOL_PATTERNS:
            if pattern in lower:
                return OperationType.EXECUTE.value
        return OperationType.EXECUTE.value

    def _matches_resource_pattern(self, policy: PermissionPolicy, arguments: dict[str, Any] | None) -> bool:
        pattern = policy.resource_pattern
        if not pattern or not pattern.strip():
            return True
        if not arguments:
            return True
        for value in arguments.values():
            if value is not None and self._matches_glob(pattern, str(value)):
                return True
        return False

    def _matches_glob(self, pattern: str, text: str) -> bool:
        regex = pattern.replace(".", r"\.").replace("*", ".*").replace("?", ".")
        try:
            return bool(re.fullmatch(regex, text))
        except re.error:
            return pattern.replace("*", "").replace("?", "") in text

    def add_policy(self, user_id: int, tool_name: str = "*", operation_type: str = "*",
                   resource_pattern: str = "", permission_level: str = PermissionLevel.AUTO_ALLOW.value,
                   description: str = "") -> PermissionPolicy:
        policy_id = self._next_id
        self._next_id += 1
        policy = PermissionPolicy(
            id=policy_id, user_id=user_id, tool_name=tool_name,
            operation_type=operation_type, resource_pattern=resource_pattern,
            permission_level=permission_level, description=description,
        )
        self._policies[policy_id] = policy
        logger.info("permission_policy_added", policy_id=policy_id, tool=tool_name, level=permission_level)
        return policy

    def update_policy(self, policy_id: int, **kwargs: Any) -> PermissionPolicy | None:
        policy = self._policies.get(policy_id)
        if not policy:
            return None
        for k, v in kwargs.items():
            if hasattr(policy, k) and v is not None:
                setattr(policy, k, v)
        return policy

    def delete_policy(self, policy_id: int) -> bool:
        return self._policies.pop(policy_id, None) is not None

    def get_user_policies(self, user_id: int) -> list[PermissionPolicy]:
        return [p for p in self._policies.values() if p.user_id == user_id]

    def get_all_policies(self) -> list[PermissionPolicy]:
        return list(self._policies.values())


permission_service = PermissionService()