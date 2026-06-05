from __future__ import annotations

import json
import structlog
from dataclasses import dataclass, field
from typing import Any
from enum import Enum

from superfriend.services.llm_client import llm_client

logger = structlog.get_logger()


class ReflectionAction(str, Enum):
    CONTINUE = "continue"
    CORRECT = "correct"
    REQUEST_INFO = "request_info"
    ABANDON = "abandon"


@dataclass
class ToolExecutionRecord:
    tool_name: str
    success: bool
    result_summary: str = ""
    execution_time_ms: float = 0.0


@dataclass
class ReflectionContext:
    user_request: str = ""
    assistant_response: str = ""
    tool_executions: list[ToolExecutionRecord] = field(default_factory=list)
    iteration_count: int = 0
    previous_thoughts: str = ""


@dataclass
class ReflectionResult:
    needs_correction: bool = False
    needs_more_info: bool = False
    confidence_score: float = 0.5
    correction_hint: str = ""
    missing_info: list[str] = field(default_factory=list)
    suggested_actions: list[str] = field(default_factory=list)
    reasoning: str = ""
    action: ReflectionAction = ReflectionAction.CONTINUE


REFLECTION_SYSTEM_PROMPT = """你是一个反思评估器。分析AI助手的执行过程和结果，判断是否需要纠正或补充信息。

评估维度：
1. 回答是否完整地解决了用户请求？
2. 工具调用是否成功？结果是否有效？
3. 是否遗漏了关键信息？
4. 是否存在逻辑错误或幻觉？

返回JSON格式：
{
    "needs_correction": true/false,
    "needs_more_info": true/false,
    "confidence_score": 0.0-1.0,
    "correction_hint": "纠正提示",
    "missing_info": ["缺失信息列表"],
    "suggested_actions": ["建议行动列表"],
    "reasoning": "推理过程",
    "action": "continue/correct/request_info/abandon"
}"""


class ReflectionService:
    async def reflect(self, context: ReflectionContext, model: str = "deepseek-chat") -> ReflectionResult:
        prompt = self._build_reflection_prompt(context)

        try:
            response = await llm_client.chat(
                messages=[
                    {"role": "system", "content": REFLECTION_SYSTEM_PROMPT},
                    {"role": "user", "content": prompt},
                ],
                model=model,
                temperature=0.3,
                max_tokens=1000,
            )
            content = await llm_client.extract_content(response)
            return self._parse_result(content)
        except Exception as e:
            logger.error("reflection_error", error=str(e))
            return ReflectionResult(action=ReflectionAction.CONTINUE, reasoning=f"Reflection failed: {e}")

    def reflect_sync(self, context: ReflectionContext) -> ReflectionResult:
        if not context.assistant_response and not context.tool_executions:
            return ReflectionResult(action=ReflectionAction.CONTINUE, confidence_score=0.5)

        failed_tools = [t for t in context.tool_executions if not t.success]
        if failed_tools and len(failed_tools) / max(len(context.tool_executions), 1) > 0.5:
            return ReflectionResult(
                needs_correction=True,
                confidence_score=0.3,
                action=ReflectionAction.CORRECT,
                correction_hint=f"Multiple tool failures: {', '.join(t.tool_name for t in failed_tools)}",
                suggested_actions=["Retry with different parameters", "Try alternative tools"],
            )

        if context.iteration_count > 10 and not context.assistant_response:
            return ReflectionResult(
                needs_more_info=True,
                confidence_score=0.2,
                action=ReflectionAction.ABANDON,
                reasoning="Too many iterations without producing a response",
            )

        if context.assistant_response and len(context.assistant_response) > 50:
            return ReflectionResult(
                confidence_score=0.8,
                action=ReflectionAction.CONTINUE,
            )

        return ReflectionResult(action=ReflectionAction.CONTINUE, confidence_score=0.5)

    def _build_reflection_prompt(self, context: ReflectionContext) -> str:
        parts = [
            f"用户请求: {context.user_request}",
            f"迭代次数: {context.iteration_count}",
        ]

        if context.assistant_response:
            parts.append(f"助手回复: {context.assistant_response[:2000]}")

        if context.tool_executions:
            tool_summaries = []
            for t in context.tool_executions:
                status = "成功" if t.success else "失败"
                tool_summaries.append(f"- {t.tool_name}: {status} ({t.execution_time_ms:.0f}ms)")
            parts.append(f"工具执行记录:\n" + "\n".join(tool_summaries))

        if context.previous_thoughts:
            parts.append(f"之前思考: {context.previous_thoughts[:1000]}")

        return "\n\n".join(parts)

    def _parse_result(self, content: str) -> ReflectionResult:
        try:
            json_str = content
            if "```json" in content:
                json_str = content.split("```json")[1].split("```")[0]
            elif "```" in content:
                json_str = content.split("```")[1].split("```")[0]

            data = json.loads(json_str.strip())

            action_str = data.get("action", "continue")
            action_map = {
                "continue": ReflectionAction.CONTINUE,
                "correct": ReflectionAction.CORRECT,
                "request_info": ReflectionAction.REQUEST_INFO,
                "abandon": ReflectionAction.ABANDON,
            }

            return ReflectionResult(
                needs_correction=data.get("needs_correction", False),
                needs_more_info=data.get("needs_more_info", False),
                confidence_score=float(data.get("confidence_score", 0.5)),
                correction_hint=data.get("correction_hint", ""),
                missing_info=data.get("missing_info", []),
                suggested_actions=data.get("suggested_actions", []),
                reasoning=data.get("reasoning", ""),
                action=action_map.get(action_str, ReflectionAction.CONTINUE),
            )
        except (json.JSONDecodeError, ValueError, KeyError) as e:
            logger.warning("reflection_parse_error", error=str(e))
            return ReflectionResult(action=ReflectionAction.CONTINUE, reasoning=f"Parse failed: {e}")


reflection_service = ReflectionService()