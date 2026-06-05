from dataclasses import dataclass, field
from enum import Enum
from typing import Any


class ErrorType(Enum):
    NETWORK_ERROR = "network_error"
    TIMEOUT_ERROR = "timeout_error"
    AUTHENTICATION_ERROR = "authentication_error"
    AUTHORIZATION_ERROR = "authorization_error"
    VALIDATION_ERROR = "validation_error"
    RATE_LIMIT_ERROR = "rate_limit_error"
    SERVER_ERROR = "server_error"
    RESOURCE_NOT_FOUND = "resource_not_found"
    TOOL_ERROR = "tool_error"
    UNKNOWN_ERROR = "unknown_error"


class ErrorSeverity(Enum):
    LOW = "low"
    MEDIUM = "medium"
    HIGH = "high"
    CRITICAL = "critical"


class RecoveryAction(Enum):
    RETRY = "retry"
    RETRY_WITH_DELAY = "retry_with_delay"
    SWITCH_TOOL = "switch_tool"
    SKIP = "skip"
    FALLBACK = "fallback"
    ABORT = "abort"


@dataclass
class ErrorRecord:
    error_type: ErrorType
    message: str
    component_name: str
    timestamp: float = 0.0

    def __post_init__(self):
        if self.timestamp == 0.0:
            import time
            self.timestamp = time.time() * 1000


@dataclass
class ErrorDiagnosis:
    record: ErrorRecord
    severity: ErrorSeverity = ErrorSeverity.MEDIUM
    root_cause: str = ""
    recovery_strategies: list[str] = field(default_factory=list)
    prevention_measures: list[str] = field(default_factory=list)


@dataclass
class RecoveryResult:
    action: RecoveryAction
    message: str
    delay_ms: int = 0
    alternative_tool: str = ""
    context: dict[str, Any] = field(default_factory=dict)

    @classmethod
    def retry(cls, message: str, delay_ms: int = 1000) -> "RecoveryResult":
        return cls(action=RecoveryAction.RETRY_WITH_DELAY, message=message, delay_ms=delay_ms)

    @classmethod
    def with_alternative(cls, message: str, alternative_tool: str) -> "RecoveryResult":
        return cls(action=RecoveryAction.SWITCH_TOOL, message=message, alternative_tool=alternative_tool)

    @classmethod
    def skip(cls, message: str) -> "RecoveryResult":
        return cls(action=RecoveryAction.SKIP, message=message)

    @classmethod
    def abort(cls, message: str) -> "RecoveryResult":
        return cls(action=RecoveryAction.ABORT, message=message)