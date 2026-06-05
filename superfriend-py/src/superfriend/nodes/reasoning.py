import json
import structlog

from superfriend.graph.state import AgentState
from superfriend.models.agent_event import AgentEvent

logger = structlog.get_logger()


def build_reasoning_prompt(state: AgentState) -> str:
    tool_descriptions = "\n".join(
        f"- {t.get('name', t.get('function', {}).get('name', 'unknown'))}: "
        f"{t.get('description', t.get('function', {}).get('description', ''))}"
        for t in state.visible_tools
    ) if state.visible_tools else "No tools available"

    return f"""You are an AI assistant with tool-calling capabilities.

## Current Task
{state.user_message}

## Available Tools
{tool_descriptions}

## Task Analysis
- Intent: {state.task_analysis.intent.value if state.task_analysis else 'N/A'}
- Complexity: {state.task_analysis.complexity.value if state.task_analysis else 'N/A'}

## Previous Observations
{state.current_observation[:2000] if state.current_observation else 'None'}

## Working Memory
{json.dumps(state.working_memory, ensure_ascii=False, default=str)[:1000]}

## Instructions
Think step by step about what action to take next. Respond in JSON format:
{{"thought": "your reasoning", "action": "tool_name or finish", "action_params": {{}} }}"""


def generate_thought(state: AgentState) -> dict:
    if state.current_iteration == 0:
        thought = f"Analyzing request: {state.user_message[:200]}"
    else:
        thought = state.current_thought or "Considering next action based on observations..."

    event = AgentEvent.thought(state.session_id, state.current_iteration, thought)
    state.emit_event(event)

    logger.debug("thought_generated", iteration=state.current_iteration, thought=thought[:100])

    return {"current_thought": thought}


def decide_action(state: AgentState) -> dict:
    if not state.current_thought:
        return {"current_action": "finish", "current_action_params": {}}

    if state.error_type and state.consecutive_errors >= state.max_consecutive_errors:
        logger.warning("max_errors_reached", errors=state.consecutive_errors)
        return {"current_action": "finish", "current_action_params": {"reason": "max errors reached"}}

    return {}


def resolve_action(state: AgentState) -> str:
    action = state.current_action.lower().strip() if state.current_action else ""

    if action in ("", "finish", "done", "complete", "answer"):
        return "finish"

    if action in ("think", "thought", "reason", "analyze"):
        return "reason"

    available_names = {
        t.get("name", t.get("function", {}).get("name", ""))
        for t in state.available_tools
    }
    if action in available_names:
        return "execute_tool"

    if state.available_tools:
        return "execute_tool"
    return "finish"


def check_should_continue(state: AgentState) -> str:
    if state.force_terminated:
        return "end"
    if state.execution_ended:
        return "end"
    if state.current_iteration >= state.max_iterations:
        logger.warning("max_iterations_reached", iterations=state.current_iteration)
        return "end"
    if state.consecutive_errors >= state.max_consecutive_errors:
        logger.warning("max_consecutive_errors", errors=state.consecutive_errors)
        return "end"
    return "continue"


def generate_final_answer(state: AgentState) -> dict:
    observations = state.evidence_store
    tool_results = state.tool_results

    if tool_results:
        results_text = "\n".join(f"{k}: {str(v)[:500]}" for k, v in tool_results.items())
        answer = f"Based on the tool results:\n\n{results_text}"
    else:
        answer = state.current_observation or "I have processed your request."

    event = AgentEvent(
        event_id=__import__("uuid").uuid4().hex,
        type=AgentEvent.EventType.COMPLETED,
        timestamp=__import__("time").time() * 1000,
        step_index=state.current_iteration,
        session_id=state.session_id,
        content=answer,
    )
    state.emit_event(event)

    logger.info("final_answer_generated", session_id=state.session_id)

    return {
        "final_answer": answer,
        "answer_complete": True,
        "execution_ended": True,
    }