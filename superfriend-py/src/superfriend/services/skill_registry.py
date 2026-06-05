from __future__ import annotations

import structlog
from typing import Any

from superfriend.models.skill import SkillConfig, SkillMetadata, SkillScript, SkillResult, SkillContext, SkillTriggerType

logger = structlog.get_logger()


class SkillRegistry:
    def __init__(self):
        self._skills: dict[str, SkillConfig] = {}
        self._executors: dict[str, Any] = {}
        self._metadata: dict[str, SkillMetadata] = {}
        self._scripts: dict[str, dict[str, SkillScript]] = {}

    def register(
        self,
        name: str,
        description: str,
        executor: Any = None,
        trigger_type: SkillTriggerType = SkillTriggerType.TOOL,
        tools: list[str] | None = None,
        metadata: SkillMetadata | None = None,
    ) -> None:
        config = SkillConfig(
            name=name,
            description=description,
            trigger_type=trigger_type,
            tools=tools or [],
        )
        self._skills[name] = config
        if executor:
            self._executors[name] = executor
        if metadata:
            self._metadata[name] = metadata

    def get_skill(self, name: str) -> SkillConfig | None:
        return self._skills.get(name)

    def get_executor(self, name: str) -> Any | None:
        return self._executors.get(name)

    def list_skills(self) -> list[SkillConfig]:
        return list(self._skills.values())

    def match_skills(self, query: str) -> list[tuple[SkillConfig, float]]:
        query_lower = query.lower()
        matches = []

        for name, config in self._skills.items():
            score = 0.0
            if name.lower() in query_lower:
                score += 0.5
            if config.description.lower() in query_lower:
                score += 0.3
            for tool in config.tools:
                if tool.lower() in query_lower:
                    score += 0.2
            if score > 0:
                matches.append((config, min(score, 1.0)))

        matches.sort(key=lambda x: x[1], reverse=True)
        return matches

    def register_script(self, skill_name: str, script: SkillScript) -> None:
        if skill_name not in self._scripts:
            self._scripts[skill_name] = {}
        self._scripts[skill_name][script.script_name] = script

    def get_scripts(self, skill_name: str) -> dict[str, SkillScript]:
        return self._scripts.get(skill_name, {})

    async def execute(self, skill_name: str, context: SkillContext) -> SkillResult:
        import time

        start = time.time() * 1000

        executor = self._executors.get(skill_name)
        if executor is None:
            return SkillResult(
                success=False,
                error=f"Skill '{skill_name}' not found",
                execution_time_ms=0,
            )

        try:
            if callable(executor):
                import asyncio
                if asyncio.iscoroutinefunction(executor):
                    result = await executor(context)
                else:
                    result = executor(context)
            else:
                result = executor

            elapsed = time.time() * 1000 - start
            return SkillResult(
                success=True,
                data=result,
                execution_time_ms=elapsed,
            )

        except Exception as e:
            elapsed = time.time() * 1000 - start
            logger.error("skill_execution_error", skill=skill_name, error=str(e))
            return SkillResult(
                success=False,
                error=str(e),
                execution_time_ms=elapsed,
            )

    def __contains__(self, name: str) -> bool:
        return name in self._skills

    def __getitem__(self, name: str) -> Any:
        return self._executors[name]


skill_registry = SkillRegistry()