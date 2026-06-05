from dataclasses import dataclass, field
from enum import Enum
from typing import Any


class TaskIntent(Enum):
    SEARCH = "search"
    ANALYZE = "analyze"
    CREATE = "create"
    MODIFY = "modify"
    COMBINE = "combine"
    UNKNOWN = "unknown"


class TaskComplexity(Enum):
    SIMPLE = "simple"
    MODERATE = "moderate"
    COMPLEX = "complex"
    VERY_COMPLEX = "very_complex"


class SubTaskStatus(Enum):
    PENDING = "pending"
    IN_PROGRESS = "in_progress"
    COMPLETED = "completed"
    FAILED = "failed"
    SKIPPED = "skipped"


class TaskStatus(Enum):
    PENDING = "pending"
    IN_PROGRESS = "in_progress"
    COMPLETED = "completed"
    FAILED = "failed"


@dataclass
class SubTask:
    sub_task_id: str
    description: str
    assigned_tool: str = ""
    parameters: dict[str, Any] = field(default_factory=dict)
    dependencies: set[str] = field(default_factory=set)
    status: SubTaskStatus = SubTaskStatus.PENDING
    result: Any = None
    error: str = ""

    def add_dependency(self, dependency: str) -> None:
        self.dependencies.add(dependency)

    def add_parameter(self, key: str, value: Any) -> None:
        self.parameters[key] = value


@dataclass
class TaskAnalysis:
    task_id: str
    original_request: str
    intent: TaskIntent = TaskIntent.UNKNOWN
    complexity: TaskComplexity = TaskComplexity.SIMPLE
    summary: str = ""
    estimated_steps: int = 1
    requires_multiple_tools: bool = False


@dataclass
class TaskPlan:
    task_id: str
    original_request: str
    sub_tasks: list[SubTask] = field(default_factory=list)
    status: TaskStatus = TaskStatus.PENDING
    created_at: float = 0.0
    started_at: float | None = None
    completed_at: float | None = None

    def get_ready_sub_tasks(self) -> list[SubTask]:
        completed_ids = {
            st.sub_task_id
            for st in self.sub_tasks
            if st.status == SubTaskStatus.COMPLETED
        }
        return [
            st
            for st in self.sub_tasks
            if st.status == SubTaskStatus.PENDING
            and st.dependencies.issubset(completed_ids)
        ]

    def all_sub_tasks_completed(self) -> bool:
        return all(
            st.status == SubTaskStatus.COMPLETED for st in self.sub_tasks
        )

    def has_failed_sub_task(self) -> bool:
        return any(st.status == SubTaskStatus.FAILED for st in self.sub_tasks)

    def get_sub_task(self, sub_task_id: str) -> SubTask | None:
        for st in self.sub_tasks:
            if st.sub_task_id == sub_task_id:
                return st
        return None