from __future__ import annotations

import structlog
from dataclasses import dataclass, field
from typing import Any

from superfriend.services.knowledge_graph_service import knowledge_graph_service
from superfriend.services.memory_palace_service import memory_palace_service

logger = structlog.get_logger()

MAX_SYSTEM_PROMPT_LENGTH = 32000
MAX_KNOWLEDGE_GRAPH_LENGTH = 800
MAX_MEMORY_PALACE_LENGTH = 1000


@dataclass
class BuildConfig:
    prompt_mode: str = "medium_task"
    user_id: int | None = None
    session_id: str = ""
    user_message: str = ""
    enable_user_profile: bool = True
    enable_knowledge_graph: bool = True
    enable_memory_palace: bool = True
    enable_skill_catalog: bool = True
    enable_file_index: bool = True


MODE_TEMPLATES = {
    "lite_task": {
        "name": "轻量模式",
        "description": "适用于简单对话和快速问答",
        "max_iterations": 3,
        "enable_tools": False,
        "enable_planning": False,
        "enable_reflection": False,
    },
    "medium_task": {
        "name": "标准模式",
        "description": "适用于需要工具调用的中等复杂度任务",
        "max_iterations": 8,
        "enable_tools": True,
        "enable_planning": True,
        "enable_reflection": False,
    },
    "complex_task": {
        "name": "深度模式",
        "description": "适用于需要多步骤规划和执行复杂任务",
        "max_iterations": 15,
        "enable_tools": True,
        "enable_planning": True,
        "enable_reflection": True,
    },
}


class SystemContextBuilder:
    def build(self, config: BuildConfig) -> str:
        parts: list[str] = []

        mode = MODE_TEMPLATES.get(config.prompt_mode, MODE_TEMPLATES["medium_task"])
        parts.append(f"# 当前模式: {mode['name']}\n{mode['description']}")
        parts.append(f"最大迭代次数: {mode['max_iterations']}")

        if config.enable_knowledge_graph and config.user_id:
            graph_ctx = knowledge_graph_service.get_graph_context(
                config.user_id, query=config.user_message
            )
            if graph_ctx.context_text:
                kg_text = graph_ctx.context_text[:MAX_KNOWLEDGE_GRAPH_LENGTH]
                parts.append(f"\n## 知识图谱上下文\n{kg_text}")

        if config.enable_memory_palace and config.user_id:
            memories = memory_palace_service.search_memories(
                config.user_id, config.user_message, limit=5
            )
            if memories:
                mem_parts = []
                for mem in memories:
                    mem_parts.append(f"- [{mem.memory_type.value}] {mem.title}: {mem.content[:100]}")
                mem_text = "\n".join(mem_parts)[:MAX_MEMORY_PALACE_LENGTH]
                parts.append(f"\n## 用户记忆\n{mem_text}")

        if config.enable_skill_catalog:
            parts.append("\n## 可用技能\n请根据用户需求选择合适的工具和技能来完成任务。")

        system_prompt = "\n".join(parts)
        if len(system_prompt) > MAX_SYSTEM_PROMPT_LENGTH:
            system_prompt = system_prompt[:MAX_SYSTEM_PROMPT_LENGTH]

        return system_prompt

    def build_mode_config(self, prompt_mode: str) -> dict[str, Any]:
        return MODE_TEMPLATES.get(prompt_mode, MODE_TEMPLATES["medium_task"])


system_context_builder = SystemContextBuilder()