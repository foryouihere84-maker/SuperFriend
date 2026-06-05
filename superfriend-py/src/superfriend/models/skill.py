from dataclasses import dataclass, field
from enum import Enum
from typing import Any


class SkillTriggerType(Enum):
    TOOL = "tool"
    SCRIPT = "script"
    HYBRID = "hybrid"


@dataclass
class SkillConfig:
    name: str
    description: str = ""
    trigger_type: SkillTriggerType = SkillTriggerType.TOOL
    has_scripts: bool = False
    main_script: str = ""
    tools: list[str] = field(default_factory=list)
    parameters: dict[str, Any] = field(default_factory=dict)


@dataclass
class SkillMetadata:
    name: str
    version: str = "1.0.0"
    author: str = ""
    category: str = ""
    tags: list[str] = field(default_factory=list)


@dataclass
class SkillScript:
    script_name: str
    script_content: str = ""
    is_main: bool = False
    language: str = "python"


@dataclass
class SkillResult:
    success: bool
    data: Any = None
    error: str = ""
    execution_time_ms: float = 0.0
    outputs: dict[str, Any] = field(default_factory=dict)


@dataclass
class SkillContext:
    session_id: str
    user_id: int | None = None
    variables: dict[str, Any] = field(default_factory=dict)
    working_dir: str = ""
    env_vars: dict[str, str] = field(default_factory=dict)