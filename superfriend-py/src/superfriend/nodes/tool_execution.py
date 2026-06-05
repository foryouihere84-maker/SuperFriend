import time
import structlog

from superfriend.graph.state import AgentState
from superfriend.models.agent_event import AgentEvent
from superfriend.models.error import ErrorType, ErrorDiagnosis, ErrorRecord, ErrorSeverity, RecoveryResult, RecoveryAction

logger = structlog.get_logger()


async def execute_tool(state: AgentState, tool_registry: dict | None = None) -> dict:
    tool_name = state.current_action
    params = state.current_action_params

    if not tool_name:
        return {"current_observation": "No tool specified", "current_tool_success": False}

    event = AgentEvent.tool_call_start(state.session_id, state.current_iteration, tool_name, params)
    state.emit_event(event)

    logger.info("tool_execution_start", tool=tool_name, iteration=state.current_iteration)

    try:
        if tool_registry and tool_name in tool_registry:
            result = await tool_registry[tool_name](**params)
        else:
            result = f"[Simulated] Tool '{tool_name}' executed with params: {params}"

        state.record_tool_result(tool_name, result, True)
        state.evidence_store[tool_name] = result

        result_event = AgentEvent.tool_call_result(state.session_id, state.current_iteration, tool_name, result)
        state.emit_event(result_event)

        observation = f"Tool '{tool_name}' executed successfully. Result: {str(result)[:500]}"
        logger.info("tool_execution_success", tool=tool_name)

        return {"current_observation": observation, "current_tool_success": True}

    except Exception as e:
        state.record_tool_result(tool_name, str(e), False)
        state.error_type = ErrorType.TOOL_ERROR
        state.error_message = str(e)

        error_event = AgentEvent(
            event_id=__import__("uuid").uuid4().hex,
            type=AgentEvent.EventType.ERROR,
            timestamp=time.time() * 1000,
            step_index=state.current_iteration,
            session_id=state.session_id,
            content=f"Tool '{tool_name}' failed: {str(e)}",
        )
        state.emit_event(error_event)

        logger.error("tool_execution_failed", tool=tool_name, error=str(e))

        return {"current_observation": f"Error: {str(e)}", "current_tool_success": False}


def process_observation(state: AgentState) -> dict:
    observation = state.current_observation
    if not observation:
        return {}

    if len(observation) > 2000:
        observation = observation[:2000] + "...(truncated)"

    state.working_memory[f"obs_{state.current_iteration}"] = observation

    logger.debug("observation_processed", iteration=state.current_iteration, length=len(observation))

    return {"current_observation": observation}


def classify_error(error_message: str, component_name: str) -> ErrorType:
    msg = error_message.lower()

    if any(kw in msg for kw in ("network", "connection", "refused", "unreachable", "dns")):
        return ErrorType.NETWORK_ERROR
    if any(kw in msg for kw in ("timeout", "timed out", "deadline")):
        return ErrorType.TIMEOUT_ERROR
    if any(kw in msg for kw in ("unauthorized", "authentication", "login", "credential")):
        return ErrorType.AUTHENTICATION_ERROR
    if any(kw in msg for kw in ("forbidden", "permission", "access denied")):
        return ErrorType.AUTHORIZATION_ERROR
    if any(kw in msg for kw in ("validation", "invalid", "required", "malformed")):
        return ErrorType.VALIDATION_ERROR
    if any(kw in msg for kw in ("rate limit", "too many", "throttle", "quota")):
        return ErrorType.RATE_LIMIT_ERROR
    if any(kw in msg for kw in ("500", "internal server", "service unavailable")):
        return ErrorType.SERVER_ERROR
    if any(kw in msg for kw in ("not found", "404", "missing")):
        return ErrorType.RESOURCE_NOT_FOUND
    if any(kw in msg for kw in ("tool", "execute", "runtime")):
        return ErrorType.TOOL_ERROR

    return ErrorType.UNKNOWN_ERROR


def determine_severity(error_type: ErrorType) -> ErrorSeverity:
    severity_map = {
        ErrorType.NETWORK_ERROR: ErrorSeverity.MEDIUM,
        ErrorType.TIMEOUT_ERROR: ErrorSeverity.MEDIUM,
        ErrorType.AUTHENTICATION_ERROR: ErrorSeverity.HIGH,
        ErrorType.AUTHORIZATION_ERROR: ErrorSeverity.HIGH,
        ErrorType.VALIDATION_ERROR: ErrorSeverity.HIGH,
        ErrorType.RATE_LIMIT_ERROR: ErrorSeverity.MEDIUM,
        ErrorType.SERVER_ERROR: ErrorSeverity.MEDIUM,
        ErrorType.RESOURCE_NOT_FOUND: ErrorSeverity.LOW,
        ErrorType.TOOL_ERROR: ErrorSeverity.HIGH,
        ErrorType.UNKNOWN_ERROR: ErrorSeverity.MEDIUM,
    }
    return severity_map.get(error_type, ErrorSeverity.MEDIUM)


def diagnose_error(state: AgentState) -> dict:
    if not state.error_message:
        return {}

    error_type = state.error_type or classify_error(state.error_message, state.current_action or "unknown")
    severity = determine_severity(error_type)

    record = ErrorRecord(error_type=error_type, message=state.error_message, component_name=state.current_action or "unknown")
    diagnosis = ErrorDiagnosis(record=record, severity=severity)

    diagnosis.root_cause = _identify_root_cause(error_type, state.error_message)
    diagnosis.recovery_strategies = _suggest_recovery_strategies(error_type)
    diagnosis.prevention_measures = _suggest_prevention_measures(error_type)

    logger.warning("error_diagnosed", type=error_type.value, severity=severity.value)

    return {"last_error": diagnosis}


def _identify_root_cause(error_type: ErrorType, message: str) -> str:
    causes = {
        ErrorType.NETWORK_ERROR: "Network connectivity issue or service unreachable",
        ErrorType.TIMEOUT_ERROR: "Operation exceeded time limit",
        ErrorType.AUTHENTICATION_ERROR: "Invalid or expired credentials",
        ErrorType.AUTHORIZATION_ERROR: "Insufficient permissions",
        ErrorType.VALIDATION_ERROR: "Invalid input parameters",
        ErrorType.RATE_LIMIT_ERROR: "API rate limit exceeded",
        ErrorType.SERVER_ERROR: "Remote server error",
        ErrorType.RESOURCE_NOT_FOUND: "Requested resource does not exist",
        ErrorType.TOOL_ERROR: "Tool execution failure",
        ErrorType.UNKNOWN_ERROR: "Unclassified error",
    }
    return causes.get(error_type, "Unknown cause")


def _suggest_recovery_strategies(error_type: ErrorType) -> list[str]:
    strategies = {
        ErrorType.NETWORK_ERROR: ["retry with exponential backoff", "switch to alternative endpoint"],
        ErrorType.TIMEOUT_ERROR: ["increase timeout", "retry with backoff", "split into smaller operations"],
        ErrorType.AUTHENTICATION_ERROR: ["refresh credentials", "check API key configuration"],
        ErrorType.VALIDATION_ERROR: ["validate and fix input parameters", "use default values"],
        ErrorType.RATE_LIMIT_ERROR: ["wait and retry", "reduce request frequency"],
        ErrorType.SERVER_ERROR: ["retry with backoff", "use fallback service"],
        ErrorType.TOOL_ERROR: ["retry with different parameters", "use alternative tool"],
    }
    return strategies.get(error_type, ["retry operation", "skip and continue"])


def _suggest_prevention_measures(error_type: ErrorType) -> list[str]:
    return ["add input validation", "implement circuit breaker", "add monitoring alerts"]


def recover_from_error(state: AgentState) -> dict:
    if state.last_error is None:
        return {}

    error_type = state.last_error.record.error_type
    attempt = state.consecutive_errors

    if error_type in (ErrorType.NETWORK_ERROR, ErrorType.TIMEOUT_ERROR, ErrorType.SERVER_ERROR):
        if attempt < 3:
            delay = 1000 * (2 ** attempt)
            result = RecoveryResult.retry(f"Retrying after {delay}ms", delay)
            return {"current_observation": result.message}
        else:
            result = RecoveryResult.with_alternative("Switching to alternative tool", "fetch")
            return {"current_observation": result.message, "current_action": result.alternative_tool}

    if error_type == ErrorType.RATE_LIMIT_ERROR:
        result = RecoveryResult.retry("Rate limited, waiting before retry", 5000)
        return {"current_observation": result.message}

    if error_type in (ErrorType.VALIDATION_ERROR, ErrorType.TOOL_ERROR):
        if attempt < 3:
            result = RecoveryResult.retry("Retrying with corrected parameters", 1000)
            return {"current_observation": result.message}

    result = RecoveryResult.skip("Skipping after repeated failures")
    return {"current_observation": result.message}


def handle_error_decision(state: AgentState) -> str:
    if state.last_error is None:
        return "continue"

    severity = state.last_error.severity
    if severity == ErrorSeverity.CRITICAL:
        return "abort"
    if state.consecutive_errors >= state.max_consecutive_errors:
        return "abort"
    return "recover"