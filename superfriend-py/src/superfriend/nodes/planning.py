import time
import uuid
import structlog

from superfriend.graph.state import AgentState
from superfriend.models.task import (
    TaskAnalysis, TaskPlan, TaskIntent, TaskComplexity,
    SubTask, SubTaskStatus, TaskStatus,
)
from superfriend.models.agent_event import AgentEvent

logger = structlog.get_logger()


def _determine_intent(request: str) -> TaskIntent:
    lower = request.lower()

    if any(kw in lower for kw in ("search", "find", "look for", "查询")):
        return TaskIntent.SEARCH
    if any(kw in lower for kw in ("analyze", "analysis", "统计", "分析")):
        return TaskIntent.ANALYZE
    if any(kw in lower for kw in ("create", "generate", "make", "创建", "生成")):
        return TaskIntent.CREATE
    if any(kw in lower for kw in ("modify", "change", "update", "修改", "更新")):
        return TaskIntent.MODIFY
    if any(kw in lower for kw in ("and", "then", "after", "然后", "之后")):
        return TaskIntent.COMBINE

    return TaskIntent.UNKNOWN


def _determine_complexity(request: str) -> TaskComplexity:
    lower = request.lower()
    score = 0

    if any(kw in lower for kw in ("and", "then", "after", "然后")):
        score += 2
    if any(kw in lower for kw in ("analyze", "create", "generate", "分析", "创建", "生成")):
        score += 1
    if any(kw in lower for kw in ("multiple", "several", "various", "多个", "各种")):
        score += 2
    if len(request) > 200:
        score += 1

    if score <= 1:
        return TaskComplexity.SIMPLE
    elif score <= 3:
        return TaskComplexity.MODERATE
    elif score <= 5:
        return TaskComplexity.COMPLEX
    else:
        return TaskComplexity.VERY_COMPLEX


def _create_subtask(sub_id: str, description: str) -> SubTask:
    return SubTask(sub_task_id=sub_id, description=description)


def _decompose_simple(analysis: TaskAnalysis) -> TaskPlan:
    plan = TaskPlan(task_id=analysis.task_id, original_request=analysis.original_request)
    st = _create_subtask("subtask_1", analysis.original_request)
    plan.sub_tasks.append(st)
    return plan


def _decompose_moderate(analysis: TaskAnalysis) -> TaskPlan:
    plan = TaskPlan(task_id=analysis.task_id, original_request=analysis.original_request)
    parts = analysis.original_request.split(" and ") if " and " in analysis.original_request else [analysis.original_request]
    for i, part in enumerate(parts):
        st = _create_subtask(f"subtask_{i + 1}", part.strip())
        if i > 0:
            st.add_dependency(f"subtask_{i}")
        plan.sub_tasks.append(st)
    return plan


def _decompose_complex(analysis: TaskAnalysis) -> TaskPlan:
    plan = TaskPlan(task_id=analysis.task_id, original_request=analysis.original_request)

    st1 = _create_subtask("subtask_analysis", f"Analyze the request: {analysis.original_request}")
    st1.assigned_tool = "data_analysis"
    plan.sub_tasks.append(st1)

    st2 = _create_subtask("subtask_search", "Search for relevant information")
    st2.assigned_tool = "web_search"
    st2.add_dependency("subtask_analysis")
    plan.sub_tasks.append(st2)

    st3 = _create_subtask("subtask_process", "Process the search results")
    st3.assigned_tool = "data_analysis"
    st3.add_dependency("subtask_search")
    plan.sub_tasks.append(st3)

    st4 = _create_subtask("subtask_synthesis", "Synthesize final answer")
    st4.assigned_tool = "data_analysis"
    st4.add_dependency("subtask_process")
    plan.sub_tasks.append(st4)

    return plan


def _decompose_very_complex(analysis: TaskAnalysis) -> TaskPlan:
    plan = TaskPlan(task_id=analysis.task_id, original_request=analysis.original_request)

    st1 = _create_subtask("subtask_planning", "Create detailed execution plan")
    st1.assigned_tool = "data_analysis"
    plan.sub_tasks.append(st1)

    st2 = _create_subtask("subtask_research", "Conduct comprehensive research")
    st2.assigned_tool = "web_search"
    st2.add_dependency("subtask_planning")
    plan.sub_tasks.append(st2)

    st3 = _create_subtask("subtask_analysis", "Analyze research findings")
    st3.assigned_tool = "data_analysis"
    st3.add_dependency("subtask_research")
    plan.sub_tasks.append(st3)

    st4 = _create_subtask("subtask_evaluation", "Evaluate different approaches")
    st4.assigned_tool = "data_analysis"
    st4.add_dependency("subtask_analysis")
    plan.sub_tasks.append(st4)

    st5 = _create_subtask("subtask_synthesis", "Synthesize comprehensive solution")
    st5.assigned_tool = "data_analysis"
    st5.add_dependency("subtask_evaluation")
    plan.sub_tasks.append(st5)

    return plan


def analyze_task(state: AgentState) -> dict:
    if not state.enable_planning:
        return {}

    task_id = f"task_{int(time.time() * 1000)}_{abs(hash(state.user_message)) % 10000}"
    analysis = TaskAnalysis(task_id=task_id, original_request=state.user_message)
    analysis.intent = _determine_intent(state.user_message)
    analysis.complexity = _determine_complexity(state.user_message)
    analysis.summary = state.user_message[:100] + "..." if len(state.user_message) > 100 else state.user_message
    analysis.estimated_steps = {
        TaskComplexity.SIMPLE: 1,
        TaskComplexity.MODERATE: 2,
        TaskComplexity.COMPLEX: 4,
        TaskComplexity.VERY_COMPLEX: 5,
    }.get(analysis.complexity, 1)
    analysis.requires_multiple_tools = analysis.complexity in (TaskComplexity.COMPLEX, TaskComplexity.VERY_COMPLEX)

    logger.info("task_analysis", intent=analysis.intent.value, complexity=analysis.complexity.value)

    return {"task_analysis": analysis}


def decompose_task(state: AgentState) -> dict:
    if not state.enable_planning or state.task_analysis is None:
        return {}

    analysis = state.task_analysis
    decompose_fn = {
        TaskComplexity.SIMPLE: _decompose_simple,
        TaskComplexity.MODERATE: _decompose_moderate,
        TaskComplexity.COMPLEX: _decompose_complex,
        TaskComplexity.VERY_COMPLEX: _decompose_very_complex,
    }.get(analysis.complexity, _decompose_simple)

    plan = decompose_fn(analysis)
    plan.status = TaskStatus.IN_PROGRESS
    plan.started_at = time.time() * 1000

    ready = plan.get_ready_sub_tasks()
    logger.info("task_decomposed", sub_tasks=len(plan.sub_tasks), ready=len(ready))

    return {
        "task_plan": plan,
        "pending_sub_tasks": ready,
    }


def select_next_subtask(state: AgentState) -> dict:
    if state.task_plan is None:
        return {}

    ready = state.task_plan.get_ready_sub_tasks()
    if ready:
        next_st = ready[0]
        next_st.status = SubTaskStatus.IN_PROGRESS
        return {"current_sub_task": next_st, "pending_sub_tasks": ready[1:]}
    return {"current_sub_task": None}


def mark_subtask_complete(state: AgentState) -> dict:
    if state.current_sub_task is None:
        return {}
    state.current_sub_task.status = SubTaskStatus.COMPLETED
    return {"current_sub_task": None}


def mark_subtask_failed(state: AgentState) -> dict:
    if state.current_sub_task is None:
        return {}
    state.current_sub_task.status = SubTaskStatus.FAILED
    return {"current_sub_task": None}


def check_plan_status(state: AgentState) -> str:
    if state.task_plan is None:
        return "execute"
    if state.task_plan.all_sub_tasks_completed():
        return "complete"
    if state.task_plan.has_failed_sub_task():
        return "failed"
    return "continue"