from __future__ import annotations

import json
import time
import structlog
from dataclasses import dataclass, field
from typing import Any
from enum import Enum

from superfriend.services.llm_client import llm_client

logger = structlog.get_logger()

CACHE_MAX_SIZE = 1000
CACHE_TTL_MS = 5 * 60 * 1000
COMPLEXITY_CHECK_MAX_RETRIES = 3
COMPLEXITY_CHECK_RETRY_DELAY_MS = 1000

SIMPLE_GREETING_PATTERNS = [
    "你是谁", "你叫什么", "你是什么", "自我介绍", "你好", "hello", "hi ",
    "嗨", "早上好", "下午好", "晚上好", "早安", "晚安",
    "谢谢", "感谢", "thanks", "thank you", "再见", "拜拜",
    "帮我", "能做什么", "你会什么", "你有什么功能", "你能做什么",
    "怎么用", "如何使用",
]

OBVIOUSLY_COMPLEX_PATTERNS = [
    "分析", "对比", "比较", "调研", "研究", "报告", "总结",
    "爬取", "抓取", "批量", "多个", "所有", "全部",
    "规划", "计划", "方案", "策略", "设计", "架构",
    "步骤", "流程", "依次", "然后", "接着", "之后",
    "搜索", "查找", "收集", "整理", "汇总", "归纳",
]


class TaskComplexity(str, Enum):
    SIMPLE = "simple"
    MEDIUM = "medium"
    COMPLEX = "complex"


@dataclass
class SubTask:
    id: str = ""
    title: str = ""
    description: str = ""
    tool_name: str = ""
    parameters: dict[str, Any] = field(default_factory=dict)
    dependencies: list[str] = field(default_factory=list)
    status: str = "pending"
    result: str = ""
    order: int = 0


@dataclass
class TaskPlan:
    plan_id: str = ""
    session_id: str = ""
    user_message: str = ""
    complexity: TaskComplexity = TaskComplexity.SIMPLE
    subtasks: list[SubTask] = field(default_factory=list)
    status: str = "pending"
    created_at: float = 0.0
    error: str = ""


class _CacheEntry:
    __slots__ = ("is_complex", "timestamp")

    def __init__(self, is_complex: bool):
        self.is_complex = is_complex
        self.timestamp = time.time()

    def is_expired(self) -> bool:
        return (time.time() - self.timestamp) * 1000 > CACHE_TTL_MS


class TaskPlannerService:
    def __init__(self):
        self._complexity_cache: dict[str, _CacheEntry] = {}
        self._plans: dict[str, TaskPlan] = {}
        self._next_plan_id: int = 1

    def _get_cache_key(self, user_message: str) -> str:
        if not user_message:
            return "0_0"
        return f"{len(user_message)}_{hash(user_message)}"

    def is_simple_question(self, message: str) -> bool:
        lower = message.lower().strip()
        return any(p in lower for p in SIMPLE_GREETING_PATTERNS)

    def is_obviously_complex(self, message: str) -> bool:
        return any(p in message for p in OBVIOUSLY_COMPLEX_PATTERNS)

    def is_complex_task(self, user_message: str, user_id: int | None = None, model: str = "deepseek-chat") -> bool:
        if self.is_simple_question(user_message):
            logger.info("task_complexity_simple_question", message=user_message[:50])
            return False

        if self.is_obviously_complex(user_message):
            logger.info("task_complexity_obviously_complex", message=user_message[:50])
            return True

        cache_key = self._get_cache_key(user_message)
        cached = self._complexity_cache.get(cache_key)
        if cached and not cached.is_expired():
            logger.info("task_complexity_cached", is_complex=cached.is_complex)
            return cached.is_complex

        if len(self._complexity_cache) > CACHE_MAX_SIZE:
            self._complexity_cache = {
                k: v for k, v in self._complexity_cache.items() if not v.is_expired()
            }

        result = self._llm_complexity_check(user_message, model)
        self._complexity_cache[cache_key] = _CacheEntry(result)
        return result

    async def is_complex_task_async(self, user_message: str, user_id: int | None = None, model: str = "deepseek-chat") -> bool:
        if self.is_simple_question(user_message):
            return False
        if self.is_obviously_complex(user_message):
            return True

        cache_key = self._get_cache_key(user_message)
        cached = self._complexity_cache.get(cache_key)
        if cached and not cached.is_expired():
            return cached.is_complex

        result = await self._llm_complexity_check_async(user_message, model)
        self._complexity_cache[cache_key] = _CacheEntry(result)
        return result

    def _llm_complexity_check(self, user_message: str, model: str) -> bool:
        import asyncio
        try:
            loop = asyncio.get_event_loop()
            if loop.is_running():
                return self._heuristic_check(user_message)
            return loop.run_until_complete(self._llm_complexity_check_async(user_message, model))
        except RuntimeError:
            return self._heuristic_check(user_message)

    async def _llm_complexity_check_async(self, user_message: str, model: str) -> bool:
        prompt = (
            "判断以下用户请求是否需要多步骤执行计划。\n\n"
            "如果只需要一次工具调用或直接回答，返回 {\"complex\": false, \"reason\": \"简要原因\"}\n"
            "如果需要多次工具调用、信息收集、多步处理、数据分析等，返回 {\"complex\": true, \"reason\": \"简要原因\"}\n\n"
            f"用户请求：{user_message}\n\n"
            "请严格按 JSON 格式返回，不要包含其他内容。"
        )

        for attempt in range(1, COMPLEXITY_CHECK_MAX_RETRIES + 1):
            try:
                response = await llm_client.chat(
                    messages=[{"role": "user", "content": prompt}],
                    model=model, temperature=0.1, max_tokens=200,
                )
                content = await llm_client.extract_content(response)
                if content:
                    if '"complex": true' in content:
                        return True
                    if '"complex": false' in content:
                        return False
            except Exception as e:
                logger.warning("complexity_check_failed", attempt=attempt, error=str(e))

        return self._heuristic_check(user_message)

    def _heuristic_check(self, message: str) -> bool:
        score = 0
        for p in OBVIOUSLY_COMPLEX_PATTERNS:
            if p in message:
                score += 1
        return score >= 2 or len(message) > 200

    async def decompose_task(self, user_message: str, model: str = "deepseek-chat") -> TaskPlan:
        plan_id = f"plan_{self._next_plan_id}"
        self._next_plan_id += 1

        plan = TaskPlan(
            plan_id=plan_id, user_message=user_message,
            complexity=TaskComplexity.COMPLEX, created_at=time.time(),
        )

        prompt = (
            "将以下用户请求分解为具体的执行步骤。\n\n"
            f"用户请求：{user_message}\n\n"
            "返回 JSON 格式：\n"
            '{"subtasks": [{"title": "步骤标题", "description": "详细描述", '
            '"tool_name": "推荐工具名", "parameters": {}, "dependencies": []}]}\n\n'
            "工具名可选：web_search, fetch, puppeteer__navigate, file_read, file_write, "
            "code_execute, image_generate, translate, calculate\n\n"
            "请严格按 JSON 格式返回。"
        )

        try:
            response = await llm_client.chat(
                messages=[{"role": "user", "content": prompt}],
                model=model, temperature=0.3, max_tokens=2000,
            )
            content = await llm_client.extract_content(response)

            if content:
                plan.subtasks = self._parse_subtasks(content, plan_id)
                plan.status = "ready"
        except Exception as e:
            logger.error("task_decompose_error", error=str(e))
            plan.error = str(e)
            plan.status = "failed"

        self._plans[plan_id] = plan
        return plan

    def _parse_subtasks(self, content: str, plan_id: str) -> list[SubTask]:
        try:
            json_str = content
            if "```json" in content:
                json_str = content.split("```json")[1].split("```")[0]
            elif "```" in content:
                json_str = content.split("```")[1].split("```")[0]

            data = json.loads(json_str.strip())
            subtasks_data = data.get("subtasks", [])

            result = []
            for i, st in enumerate(subtasks_data):
                result.append(SubTask(
                    id=f"{plan_id}_step_{i + 1}",
                    title=st.get("title", f"Step {i + 1}"),
                    description=st.get("description", ""),
                    tool_name=st.get("tool_name", ""),
                    parameters=st.get("parameters", {}),
                    dependencies=st.get("dependencies", []),
                    order=i + 1,
                ))
            return result
        except (json.JSONDecodeError, KeyError, TypeError) as e:
            logger.warning("parse_subtasks_error", error=str(e))
            return []

    def get_plan(self, plan_id: str) -> TaskPlan | None:
        return self._plans.get(plan_id)

    def update_subtask_status(self, plan_id: str, subtask_id: str, status: str, result: str = "") -> None:
        plan = self._plans.get(plan_id)
        if not plan:
            return
        for st in plan.subtasks:
            if st.id == subtask_id:
                st.status = status
                if result:
                    st.result = result
                break

    def get_ready_subtasks(self, plan_id: str) -> list[SubTask]:
        plan = self._plans.get(plan_id)
        if not plan:
            return []
        completed_ids = {st.id for st in plan.subtasks if st.status == "completed"}
        return [
            st for st in plan.subtasks
            if st.status == "pending" and all(dep in completed_ids for dep in st.dependencies)
        ]


task_planner_service = TaskPlannerService()