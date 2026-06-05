import structlog

from superfriend.graph.state import AgentState, ReflectionState

logger = structlog.get_logger()


def reflect_on_progress(state: AgentState) -> dict:
    if not state.enable_reflection:
        return {}

    reflection_parts = []

    if state.current_observation:
        reflection_parts.append(f"Latest observation: {state.current_observation[:300]}")

    completed_tools = sum(1 for v in state.tool_call_counts.values() if v > 0)
    failed_tools = sum(state.tool_failure_counts.values())
    reflection_parts.append(f"Tools executed: {completed_tools}, failures: {failed_tools}")

    if state.task_plan:
        completed = sum(1 for st in state.task_plan.sub_tasks if st.status.value == "completed")
        total = len(state.task_plan.sub_tasks)
        reflection_parts.append(f"Plan progress: {completed}/{total} subtasks")

    if state.consecutive_errors > 0:
        reflection_parts.append(f"Warning: {state.consecutive_errors} consecutive errors")

    reflection = "\n".join(reflection_parts)

    confidence = max(0.0, min(1.0, 1.0 - (state.consecutive_errors * 0.2) - (failed_tools * 0.1)))

    logger.info("reflection_complete", iteration=state.current_iteration, confidence=confidence)

    return {
        "current_observation": state.current_observation + f"\n\n[Reflection]\n{reflection}",
    }


def evaluate_result_quality(state: AgentState) -> dict:
    if not state.current_observation:
        return {"needs_replan": False}

    observation = state.current_observation.lower()

    low_quality_indicators = [
        "no results found", "error", "failed", "empty", "null",
        "could not", "unable to", "not available",
    ]

    if any(indicator in observation for indicator in low_quality_indicators):
        logger.warning("low_quality_result", iteration=state.current_iteration)
        if state.consecutive_errors >= 2:
            return {"needs_replan": True}

    return {"needs_replan": False}


def suggest_replan(state: AgentState) -> dict:
    logger.info("suggesting_replan", session_id=state.session_id)

    new_observation = (
        f"{state.current_observation}\n\n"
        "The previous approach did not yield good results. "
        "Consider an alternative strategy or tool."
    )

    return {"current_observation": new_observation, "needs_replan": False}