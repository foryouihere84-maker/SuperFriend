from dataclasses import dataclass, field
from enum import Enum
from typing import Any


class EventType(Enum):
    SKILL_DETECTED = "skill_detected"
    REACT_THOUGHT = "react_thought"
    REACT_ACTION = "react_action"
    TOOL_CALL_START = "tool_call_start"
    TOOL_CALL_RESULT = "tool_call_result"
    SKILL_EXECUTION_START = "skill_execution_start"
    SKILL_EXECUTION_PROGRESS = "skill_execution_progress"
    SKILL_EXECUTION_RESULT = "skill_execution_result"
    PROGRESSIVE_DISCLOSURE = "progressive_disclosure"
    CONTEXT_UPDATE = "context_update"
    STREAMING_CHUNK = "streaming_chunk"
    STEP_COMPLETED = "step_completed"
    ERROR = "error"
    COMPLETED = "completed"
    INTERRUPTED = "interrupted"


@dataclass
class AgentEvent:
    event_id: str
    type: EventType
    timestamp: float
    step_index: int
    session_id: str
    content: str = ""
    data: dict[str, Any] = field(default_factory=dict)
    metadata: dict[str, Any] = field(default_factory=dict)

    @classmethod
    def thought(cls, session_id: str, step_index: int, thought: str) -> "AgentEvent":
        import uuid
        return cls(
            event_id=uuid.uuid4().hex,
            type=EventType.REACT_THOUGHT,
            timestamp=__import__("time").time() * 1000,
            step_index=step_index,
            session_id=session_id,
            content=thought,
        )

    @classmethod
    def action(cls, session_id: str, step_index: int, tool_name: str, params: dict) -> "AgentEvent":
        import uuid
        return cls(
            event_id=uuid.uuid4().hex,
            type=EventType.REACT_ACTION,
            timestamp=__import__("time").time() * 1000,
            step_index=step_index,
            session_id=session_id,
            content=tool_name,
            data=params,
        )

    @classmethod
    def tool_call_start(cls, session_id: str, step_index: int, tool_name: str, params: dict) -> "AgentEvent":
        import uuid
        return cls(
            event_id=uuid.uuid4().hex,
            type=EventType.TOOL_CALL_START,
            timestamp=__import__("time").time() * 1000,
            step_index=step_index,
            session_id=session_id,
            content=tool_name,
            data=params,
        )

    @classmethod
    def tool_call_result(cls, session_id: str, step_index: int, tool_name: str, result: Any) -> "AgentEvent":
        import uuid
        return cls(
            event_id=uuid.uuid4().hex,
            type=EventType.TOOL_CALL_RESULT,
            timestamp=__import__("time").time() * 1000,
            step_index=step_index,
            session_id=session_id,
            content=tool_name,
            data={"result": result},
        )