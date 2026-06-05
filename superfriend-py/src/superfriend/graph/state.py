from dataclasses import dataclass, field
from typing import Annotated, Any, Optional
from operator import add

from langgraph.graph.message import add_messages
from langchain_core.messages import BaseMessage

from superfriend.models.task import TaskAnalysis, TaskPlan, SubTask, TaskStatus
from superfriend.models.agent_event import AgentEvent
from superfriend.models.error import ErrorDiagnosis, RecoveryResult, ErrorType


@dataclass
class AgentState:
    session_id: str = ""
    user_id: int | None = None
    model: str = "deepseek-chat"

    messages: Annotated[list[BaseMessage], add_messages] = field(default_factory=list)

    user_message: str = ""
    system_prompt: str = ""

    current_iteration: int = 0
    max_iterations: int = 80
    max_consecutive_errors: int = 8
    consecutive_errors: int = 0
    step_timeout_ms: int = 120000
    total_timeout_ms: int = 600000

    current_thought: str = ""
    current_action: str = ""
    current_action_params: dict[str, Any] = field(default_factory=dict)
    current_observation: str = ""
    current_tool_success: bool = True

    task_analysis: TaskAnalysis | None = None
    task_plan: TaskPlan | None = None
    pending_sub_tasks: list[SubTask] = field(default_factory=list)
    current_sub_task: SubTask | None = None

    tool_results: dict[str, Any] = field(default_factory=dict)
    evidence_store: dict[str, Any] = field(default_factory=dict)
    working_memory: dict[str, Any] = field(default_factory=dict)

    tool_call_counts: dict[str, int] = field(default_factory=dict)
    tool_failure_counts: dict[str, int] = field(default_factory=dict)
    visited_urls: set[str] = field(default_factory=set)

    available_tools: list[dict[str, Any]] = field(default_factory=list)
    visible_tools: list[dict[str, Any]] = field(default_factory=list)

    detected_skills: list[dict[str, Any]] = field(default_factory=list)
    active_skill: dict[str, Any] | None = None

    events: list[AgentEvent] = field(default_factory=list)
    event_queue: list[AgentEvent] = field(default_factory=list)

    last_error: ErrorDiagnosis | None = None
    error_type: ErrorType | None = None
    error_message: str = ""

    cost_input_tokens: int = 0
    cost_output_tokens: int = 0

    progress: float = 0.0
    current_phase: str = "initializing"

    execution_ended: bool = False
    force_terminated: bool = False
    termination_reason: str = ""

    final_answer: str = ""
    answer_complete: bool = False

    enable_planning: bool = True
    enable_reflection: bool = True
    enable_skills: bool = True
    enable_parallel_execution: bool = True
    enable_intelligent_tool_selection: bool = True

    pending_approvals: list[dict[str, Any]] = field(default_factory=list)

    def get_effective_user_message(self) -> Any:
        return self.user_message

    def emit_event(self, event: AgentEvent) -> None:
        self.event_queue.append(event)
        self.events.append(event)

    def poll_event(self) -> AgentEvent | None:
        if self.event_queue:
            return self.event_queue.pop(0)
        return None

    def record_tool_result(self, tool_name: str, result: Any, success: bool) -> None:
        self.tool_results[tool_name] = result
        self.tool_call_counts[tool_name] = self.tool_call_counts.get(tool_name, 0) + 1
        if not success:
            self.tool_failure_counts[tool_name] = self.tool_failure_counts.get(tool_name, 0) + 1
            self.consecutive_errors += 1
        else:
            self.consecutive_errors = 0

    def should_terminate(self) -> bool:
        if self.execution_ended or self.force_terminated:
            return True
        if self.current_iteration >= self.max_iterations:
            return True
        if self.consecutive_errors >= self.max_consecutive_errors:
            return True
        return False


@dataclass
class PlannerState(AgentState):
    planning_complete: bool = False
    decomposition_result: dict[str, Any] = field(default_factory=dict)
    dependency_graph: dict[str, list[str]] = field(default_factory=dict)
    parallel_groups: list[list[SubTask]] = field(default_factory=list)


@dataclass
class ReflectionState(AgentState):
    reflection_text: str = ""
    reflection_confidence: float = 0.0
    reflection_suggestions: list[str] = field(default_factory=list)
    needs_replan: bool = False