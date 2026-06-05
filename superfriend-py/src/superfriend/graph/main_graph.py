import structlog
from langgraph.graph import StateGraph, END
from langgraph.checkpoint.memory import MemorySaver

from superfriend.graph.state import AgentState
from superfriend.nodes import (
    analyze_task, decompose_task, select_next_subtask,
    mark_subtask_complete, mark_subtask_failed, check_plan_status,
    generate_thought, decide_action, resolve_action,
    check_should_continue, generate_final_answer,
    execute_tool, process_observation,
    diagnose_error, recover_from_error, handle_error_decision,
    reflect_on_progress, evaluate_result_quality, suggest_replan,
    detect_skills,
)

logger = structlog.get_logger()


def _route_after_planning(state: AgentState) -> str:
    status = check_plan_status(state)
    if status == "complete":
        return "generate_final_answer"
    if status == "failed":
        return "generate_final_answer"
    return "select_subtask"


def _route_after_subtask_selection(state: AgentState) -> str:
    if state.current_sub_task is None:
        return "generate_final_answer"
    return "detect_skills"


def _route_after_reasoning(state: AgentState) -> str:
    return resolve_action(state)


def _route_after_tool_execution(state: AgentState) -> str:
    if state.current_tool_success:
        return "reflect"
    else:
        return "diagnose_error"


def _route_after_error_diagnosis(state: AgentState) -> str:
    return handle_error_decision(state)


def _route_after_reflection(state: AgentState) -> str:
    next_check = check_should_continue(state)
    if next_check == "end":
        return "generate_final_answer"
    return "reason"


def _route_after_skill_detection(state: AgentState) -> str:
    if state.detected_skills:
        return "reason"
    return "reason"


def _increment_iteration(state: AgentState) -> dict:
    return {"current_iteration": state.current_iteration + 1}


def build_agent_graph(
    tool_registry: dict | None = None,
    skill_registry: dict | None = None,
    checkpointer: MemorySaver | None = None,
) -> StateGraph:
    workflow = StateGraph(AgentState)

    workflow.add_node("plan_analyze", analyze_task)
    workflow.add_node("plan_decompose", decompose_task)
    workflow.add_node("select_subtask", select_next_subtask)
    workflow.add_node("detect_skills", detect_skills)
    workflow.add_node("reason", generate_thought)
    workflow.add_node("decide_action", decide_action)
    workflow.add_node("execute_tool", _make_execute_tool(tool_registry))
    workflow.add_node("process_observation", process_observation)
    workflow.add_node("diagnose_error", diagnose_error)
    workflow.add_node("recover_error", recover_from_error)
    workflow.add_node("reflect", reflect_on_progress)
    workflow.add_node("evaluate_quality", evaluate_result_quality)
    workflow.add_node("suggest_replan", suggest_replan)
    workflow.add_node("mark_complete", mark_subtask_complete)
    workflow.add_node("mark_failed", mark_subtask_failed)
    workflow.add_node("increment_iteration", _increment_iteration)
    workflow.add_node("generate_final_answer", generate_final_answer)

    workflow.set_entry_point("plan_analyze")

    workflow.add_edge("plan_analyze", "plan_decompose")
    workflow.add_edge("plan_decompose", "select_subtask")

    workflow.add_conditional_edges(
        "select_subtask",
        _route_after_subtask_selection,
        {
            "detect_skills": "detect_skills",
            "generate_final_answer": "generate_final_answer",
        },
    )

    workflow.add_edge("detect_skills", "reason")

    workflow.add_conditional_edges(
        "reason",
        _route_after_reasoning,
        {
            "execute_tool": "execute_tool",
            "reason": "reason",
            "finish": "mark_complete",
        },
    )

    workflow.add_conditional_edges(
        "execute_tool",
        _route_after_tool_execution,
        {
            "reflect": "reflect",
            "diagnose_error": "diagnose_error",
        },
    )

    workflow.add_conditional_edges(
        "diagnose_error",
        _route_after_error_diagnosis,
        {
            "recover": "recover_error",
            "continue": "reflect",
            "abort": "generate_final_answer",
        },
    )

    workflow.add_edge("recover_error", "reflect")

    workflow.add_edge("reflect", "evaluate_quality")

    workflow.add_conditional_edges(
        "evaluate_quality",
        lambda s: "suggest_replan" if getattr(s, "needs_replan", False) else "increment_iteration",
        {
            "suggest_replan": "suggest_replan",
            "increment_iteration": "increment_iteration",
        },
    )

    workflow.add_edge("suggest_replan", "increment_iteration")

    workflow.add_conditional_edges(
        "increment_iteration",
        _route_after_reflection,
        {
            "reason": "reason",
            "generate_final_answer": "generate_final_answer",
        },
    )

    workflow.add_edge("mark_complete", "select_subtask")
    workflow.add_edge("mark_failed", "select_subtask")

    workflow.add_edge("generate_final_answer", END)

    if checkpointer:
        return workflow.compile(checkpointer=checkpointer)
    return workflow.compile()


def _make_execute_tool(tool_registry: dict | None = None):
    async def _execute_tool_wrapper(state: AgentState) -> dict:
        return await execute_tool(state, tool_registry)
    return _execute_tool_wrapper


_default_checkpointer = None


def get_default_checkpointer() -> MemorySaver:
    global _default_checkpointer
    if _default_checkpointer is None:
        _default_checkpointer = MemorySaver()
    return _default_checkpointer


def create_default_agent(
    tool_registry: dict | None = None,
    skill_registry: dict | None = None,
) -> StateGraph:
    return build_agent_graph(
        tool_registry=tool_registry,
        skill_registry=skill_registry,
        checkpointer=get_default_checkpointer(),
    )