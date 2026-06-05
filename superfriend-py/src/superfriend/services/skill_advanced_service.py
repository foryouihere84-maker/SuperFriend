from __future__ import annotations

import json
import structlog
from dataclasses import dataclass, field
from typing import Any
from enum import Enum

from superfriend.services.llm_client import llm_client
from superfriend.services.embedding_service import embedding_service, vector_store

logger = structlog.get_logger()


@dataclass
class SkillRecommendation:
    skill_id: str = ""
    skill_name: str = ""
    score: float = 0.0
    reason: str = ""
    match_type: str = ""


class SkillRecommendationService:
    def __init__(self, max_recommendations: int = 5, min_score: float = 0.3):
        self._max_recommendations = max_recommendations
        self._min_score = min_score
        self._skill_vectors: dict[str, list[float]] = {}
        self._skill_metadata: dict[str, dict[str, Any]] = {}
        self._usage_stats: dict[str, int] = {}

    async def index_skill(self, skill_id: str, name: str, description: str, tags: list[str] | None = None) -> None:
        text = f"{name} {description} {' '.join(tags or [])}"
        result = await embedding_service.embed(text)
        if result.vector:
            self._skill_vectors[skill_id] = result.vector
            self._skill_metadata[skill_id] = {"name": name, "description": description, "tags": tags or []}
            vector_store.add(text, result.vector, {"skill_id": skill_id})

    async def recommend(self, user_message: str, context: dict[str, Any] | None = None, top_k: int = 5) -> list[SkillRecommendation]:
        query_result = await embedding_service.embed(user_message)
        if not query_result.vector:
            return self._recommend_by_keywords(user_message, top_k)

        vector_results = vector_store.search(query_result.vector, top_k=top_k * 2)
        recommendations = []

        for vr in vector_results:
            skill_id = vr.metadata.get("skill_id", "")
            if not skill_id or skill_id not in self._skill_metadata:
                continue

            meta = self._skill_metadata[skill_id]
            usage_boost = min(self._usage_stats.get(skill_id, 0) * 0.01, 0.2)
            final_score = min(vr.score + usage_boost, 1.0)

            if final_score >= self._min_score:
                recommendations.append(SkillRecommendation(
                    skill_id=skill_id, skill_name=meta["name"],
                    score=final_score, reason=f"语义相似度: {vr.score:.2f}",
                    match_type="semantic",
                ))

        recommendations.sort(key=lambda r: r.score, reverse=True)
        return recommendations[:top_k]

    def _recommend_by_keywords(self, user_message: str, top_k: int) -> list[SkillRecommendation]:
        msg_lower = user_message.lower()
        results = []
        for skill_id, meta in self._skill_metadata.items():
            score = 0.0
            name = meta["name"].lower()
            desc = meta["description"].lower()
            tags = [t.lower() for t in meta.get("tags", [])]

            if name in msg_lower:
                score += 0.5
            for tag in tags:
                if tag in msg_lower:
                    score += 0.3
            for word in msg_lower.split():
                if word in desc:
                    score += 0.1

            if score >= self._min_score:
                results.append(SkillRecommendation(
                    skill_id=skill_id, skill_name=meta["name"],
                    score=min(score, 1.0), reason="关键词匹配", match_type="keyword",
                ))

        results.sort(key=lambda r: r.score, reverse=True)
        return results[:top_k]

    def record_usage(self, skill_id: str) -> None:
        self._usage_stats[skill_id] = self._usage_stats.get(skill_id, 0) + 1


@dataclass
class ParsedSkillPackage:
    name: str = ""
    version: str = "1.0.0"
    description: str = ""
    author: str = ""
    skills: list[dict[str, Any]] = field(default_factory=list)
    dependencies: list[str] = field(default_factory=list)
    config_schema: dict[str, Any] = field(default_factory=dict)
    errors: list[str] = field(default_factory=list)


class SkillPackageParserService:
    def parse_package(self, package_data: dict[str, Any]) -> ParsedSkillPackage:
        result = ParsedSkillPackage()

        result.name = package_data.get("name", "")
        if not result.name:
            result.errors.append("缺少 name 字段")

        result.version = package_data.get("version", "1.0.0")
        result.description = package_data.get("description", "")
        result.author = package_data.get("author", "")
        result.dependencies = package_data.get("dependencies", [])
        result.config_schema = package_data.get("config_schema", {})

        skills_data = package_data.get("skills", [])
        for i, skill_data in enumerate(skills_data):
            parsed = self._parse_skill(skill_data, i)
            if parsed:
                result.skills.append(parsed)

        if not result.skills and not result.errors:
            result.errors.append("未找到有效的 skill 定义")

        return result

    def _parse_skill(self, data: dict[str, Any], index: int) -> dict[str, Any] | None:
        name = data.get("name", "")
        if not name:
            return None

        skill: dict[str, Any] = {
            "name": name,
            "description": data.get("description", ""),
            "type": data.get("type", "script"),
            "trigger": data.get("trigger", {}),
            "actions": data.get("actions", []),
            "conditions": data.get("conditions", []),
            "config": data.get("config", {}),
        }

        trigger = data.get("trigger", {})
        if not trigger.get("type") and not trigger.get("patterns"):
            skill["trigger"] = {"type": "manual", "patterns": [name]}

        actions = data.get("actions", [])
        for j, action in enumerate(actions):
            if not action.get("type"):
                action["type"] = "tool_call"

        return skill

    def validate_package(self, package: ParsedSkillPackage) -> list[str]:
        errors = list(package.errors)

        if not package.name:
            errors.append("包名不能为空")

        import re
        if package.name and not re.match(r'^[a-zA-Z][a-zA-Z0-9_-]*$', package.name):
            errors.append(f"包名格式不合法: {package.name}")

        for skill in package.skills:
            if not skill.get("name"):
                errors.append("技能名称不能为空")
            if not skill.get("actions"):
                errors.append(f"技能 {skill.get('name', '?')} 缺少 actions")

        return errors


skill_recommendation_service = SkillRecommendationService()
skill_package_parser_service = SkillPackageParserService()