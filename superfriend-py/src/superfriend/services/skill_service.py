from __future__ import annotations

import json
import structlog
from dataclasses import dataclass, field
from typing import Any
from enum import Enum

from superfriend.services.llm_client import llm_client

logger = structlog.get_logger()


class SkillType(str, Enum):
    BUILTIN = "builtin"
    SCRIPT = "script"
    WORKFLOW = "workflow"
    DYNAMIC = "dynamic"
    DATABASE = "database"


class SkillStatus(str, Enum):
    ENABLED = "enabled"
    DISABLED = "disabled"


@dataclass
class SkillScript:
    id: int | None = None
    skill_id: int | None = None
    language: str = "python"
    code: str = ""
    entry_point: str = "main"


@dataclass
class SkillExecution:
    id: int | None = None
    skill_id: int | None = None
    user_id: int | None = None
    session_id: str = ""
    input_data: str = ""
    output_data: str = ""
    status: str = "pending"
    error: str = ""
    duration_ms: int = 0


@dataclass
class SkillHook:
    hook_type: str = "pre"
    action: str = ""
    config: dict[str, Any] = field(default_factory=dict)


@dataclass
class SkillWorkflowStep:
    name: str = ""
    tool_name: str = ""
    parameters: dict[str, Any] = field(default_factory=dict)
    depends_on: list[str] = field(default_factory=list)
    condition: str = ""


@dataclass
class SkillWorkflow:
    steps: list[SkillWorkflowStep] = field(default_factory=list)
    on_error: str = "stop"
    max_retries: int = 1


@dataclass
class SkillResource:
    name: str = ""
    type: str = "text"
    content: str = ""
    url: str = ""


@dataclass
class SkillContext:
    user_id: int | None = None
    session_id: str = ""
    parameters: dict[str, Any] = field(default_factory=dict)
    variables: dict[str, Any] = field(default_factory=dict)
    history: list[dict[str, Any]] = field(default_factory=list)


@dataclass
class SkillResult:
    success: bool = True
    output: str = ""
    data: dict[str, Any] = field(default_factory=dict)
    error: str = ""


@dataclass
class Skill:
    id: int | None = None
    name: str = ""
    description: str = ""
    skill_type: SkillType = SkillType.BUILTIN
    status: SkillStatus = SkillStatus.ENABLED
    scope: int = 0
    icon: str = ""
    category: str = "general"
    tags: list[str] = field(default_factory=list)
    parameters_schema: dict[str, Any] = field(default_factory=dict)
    script: SkillScript | None = None
    workflow: SkillWorkflow | None = None
    hooks: list[SkillHook] = field(default_factory=list)
    resources: list[SkillResource] = field(default_factory=list)


class SkillScriptRunner:
    async def execute(self, script: SkillScript, context: SkillContext) -> SkillResult:
        if script.language == "python":
            return await self._execute_python(script, context)
        elif script.language == "javascript":
            return await self._execute_javascript(script, context)
        return SkillResult(success=False, error=f"Unsupported language: {script.language}")

    async def _execute_python(self, script: SkillScript, context: SkillContext) -> SkillResult:
        try:
            local_vars: dict[str, Any] = {
                "context": context,
                "parameters": context.parameters,
                "variables": context.variables,
                "result": None,
            }
            exec(script.code, {"__builtins__": {}}, local_vars)
            result = local_vars.get("result")
            if isinstance(result, SkillResult):
                return result
            return SkillResult(success=True, output=str(result) if result else "")
        except Exception as e:
            logger.error("script_execution_error", error=str(e))
            return SkillResult(success=False, error=str(e))

    async def _execute_javascript(self, script: SkillScript, context: SkillContext) -> SkillResult:
        return SkillResult(success=False, error="JavaScript execution not supported in Python runtime")


class WorkflowExecutor:
    async def execute(self, workflow: SkillWorkflow, context: SkillContext) -> SkillResult:
        completed: dict[str, Any] = {}
        step_results: list[dict[str, Any]] = []

        for step in workflow.steps:
            if step.depends_on:
                unmet = [d for d in step.depends_on if d not in completed]
                if unmet:
                    if workflow.on_error == "skip":
                        continue
                    return SkillResult(success=False, error=f"Unmet dependencies: {unmet}")

            if step.condition:
                try:
                    if not eval(step.condition, {"__builtins__": {}}, {**context.variables, **completed}):
                        continue
                except Exception:
                    continue

            step_result = await self._execute_step(step, context, completed)
            completed[step.name] = step_result
            step_results.append({"step": step.name, "success": step_result.get("success", True), "output": step_result.get("output", "")})

            if not step_result.get("success", True) and workflow.on_error == "stop":
                return SkillResult(success=False, error=step_result.get("error", "Step failed"), data={"steps": step_results})

        return SkillResult(success=True, output="Workflow completed", data={"steps": step_results})

    async def _execute_step(self, step: SkillWorkflowStep, context: SkillContext, completed: dict[str, Any]) -> dict[str, Any]:
        try:
            from superfriend.tools.registry import default_registry
            tool = default_registry.get_tool(step.tool_name)
            if tool:
                params = step.parameters.copy()
                for k, v in params.items():
                    if isinstance(v, str) and v.startswith("$"):
                        ref_key = v[1:]
                        params[k] = completed.get(ref_key, {}).get("output", v)

                executor = default_registry.get_executor(step.tool_name)
                if executor:
                    result = await executor(**params)
                    return {"success": True, "output": str(result)}
            return {"success": False, "error": f"Tool not found: {step.tool_name}"}
        except Exception as e:
            return {"success": False, "error": str(e)}


class SkillService:
    def __init__(self):
        self._skills: dict[int, Skill] = {}
        self._user_skills: dict[int, list[int]] = {}
        self._executions: list[SkillExecution] = []
        self._script_runner = SkillScriptRunner()
        self._workflow_executor = WorkflowExecutor()
        self._next_id: int = 1
        self._next_exec_id: int = 1

    def register_skill(self, name: str, description: str = "", skill_type: SkillType = SkillType.BUILTIN,
                       scope: int = 0, category: str = "general", tags: list[str] | None = None,
                       parameters_schema: dict[str, Any] | None = None,
                       script: SkillScript | None = None, workflow: SkillWorkflow | None = None) -> Skill:
        skill_id = self._next_id
        self._next_id += 1
        skill = Skill(
            id=skill_id, name=name, description=description,
            skill_type=skill_type, scope=scope, category=category,
            tags=tags or [], parameters_schema=parameters_schema or {},
            script=script, workflow=workflow,
        )
        self._skills[skill_id] = skill
        logger.info("skill_registered", skill_id=skill_id, name=name)
        return skill

    def find_by_name(self, name: str) -> Skill | None:
        for skill in self._skills.values():
            if skill.name == name:
                return skill
        return None

    def find_all(self) -> list[Skill]:
        return [s for s in self._skills.values() if s.status == SkillStatus.ENABLED]

    def find_by_scope(self, scope: int) -> list[Skill]:
        return [s for s in self._skills.values() if s.scope == scope and s.status == SkillStatus.ENABLED]

    async def execute_skill(self, skill_id: int, context: SkillContext) -> SkillResult:
        skill = self._skills.get(skill_id)
        if not skill:
            return SkillResult(success=False, error=f"Skill not found: {skill_id}")

        if skill.status != SkillStatus.ENABLED:
            return SkillResult(success=False, error=f"Skill is disabled: {skill.name}")

        for hook in skill.hooks:
            if hook.hook_type == "pre":
                logger.info("skill_hook_pre", skill=skill.name, action=hook.action)

        import time
        start = time.monotonic()
        try:
            if skill.skill_type == SkillType.SCRIPT and skill.script:
                result = await self._script_runner.execute(skill.script, context)
            elif skill.skill_type == SkillType.WORKFLOW and skill.workflow:
                result = await self._workflow_executor.execute(skill.workflow, context)
            else:
                result = SkillResult(success=True, output=f"Skill {skill.name} executed (builtin)")
        except Exception as e:
            result = SkillResult(success=False, error=str(e))

        elapsed = int((time.monotonic() - start) * 1000)
        exec_record = SkillExecution(
            id=self._next_exec_id, skill_id=skill_id, user_id=context.user_id,
            session_id=context.session_id, input_data=str(context.parameters)[:1000],
            output_data=result.output[:1000], status="success" if result.success else "failed",
            error=result.error[:500], duration_ms=elapsed,
        )
        self._next_exec_id += 1
        self._executions.append(exec_record)

        for hook in skill.hooks:
            if hook.hook_type == "post":
                logger.info("skill_hook_post", skill=skill.name, action=hook.action)

        return result

    def get_skill(self, skill_id: int) -> Skill | None:
        return self._skills.get(skill_id)

    def delete_skill(self, skill_id: int) -> bool:
        return self._skills.pop(skill_id, None) is not None

    def get_executions(self, skill_id: int | None = None, limit: int = 50) -> list[dict[str, Any]]:
        execs = self._executions
        if skill_id:
            execs = [e for e in execs if e.skill_id == skill_id]
        return [
            {"id": e.id, "skill_id": e.skill_id, "status": e.status,
             "duration_ms": e.duration_ms, "error": e.error}
            for e in execs[-limit:]
        ]


skill_service = SkillService()