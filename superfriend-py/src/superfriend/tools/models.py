from __future__ import annotations

import time
from dataclasses import dataclass, field
from typing import Any


@dataclass
class ToolResult:
    success: bool
    data: Any = None
    error: str | None = None
    execution_time: float = 0.0

    @classmethod
    def ok(cls, data: Any, execution_time: float = 0.0) -> ToolResult:
        return cls(success=True, data=data, execution_time=execution_time)

    @classmethod
    def fail(cls, error: str, execution_time: float = 0.0) -> ToolResult:
        return cls(success=False, error=error, execution_time=execution_time)

    def is_success(self) -> bool:
        return self.success


@dataclass
class ToolMetadata:
    name: str
    description: str = ""
    category: str = "general"
    cost: float = 0.0
    reliability: float = 1.0
    total_executions: int = 0
    successful_executions: int = 0

    def record_execution(self, success: bool) -> None:
        self.total_executions += 1
        if success:
            self.successful_executions += 1
        self._update_reliability()

    def _update_reliability(self) -> None:
        if self.total_executions > 0:
            self.reliability = self.successful_executions / self.total_executions


@dataclass
class TaskContext:
    variables: dict[str, Any] = field(default_factory=dict)
    history: dict[str, Any] = field(default_factory=dict)
    tool_scores: dict[str, float] = field(default_factory=dict)

    def set_variable(self, key: str, value: Any) -> None:
        self.variables[key] = value

    def get_variable(self, key: str) -> Any:
        return self.variables.get(key)

    def record_tool_usage(self, tool_name: str, score: float) -> None:
        self.tool_scores[tool_name] = score

    def get_tool_score(self, tool_name: str) -> float:
        return self.tool_scores.get(tool_name, 0.5)