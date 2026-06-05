import pytest
from superfriend.graph.state import AgentState
from superfriend.models.task import TaskAnalysis, TaskPlan, TaskIntent, TaskComplexity, SubTask, SubTaskStatus
from superfriend.models.error import ErrorType, ErrorSeverity, ErrorDiagnosis, ErrorRecord, RecoveryResult, RecoveryAction
from superfriend.models.agent_event import AgentEvent, EventType


class TestAgentState:
    def test_initial_state(self):
        state = AgentState(
            session_id="test-session",
            user_id=1,
            user_message="Hello, world!",
        )
        assert state.session_id == "test-session"
        assert state.user_id == 1
        assert state.user_message == "Hello, world!"
        assert state.current_iteration == 0
        assert state.consecutive_errors == 0
        assert not state.should_terminate()

    def test_should_terminate_max_iterations(self):
        state = AgentState(session_id="test", user_message="test")
        state.current_iteration = 100
        state.max_iterations = 80
        assert state.should_terminate()

    def test_should_terminate_max_errors(self):
        state = AgentState(session_id="test", user_message="test")
        state.consecutive_errors = 10
        state.max_consecutive_errors = 8
        assert state.should_terminate()

    def test_record_tool_result(self):
        state = AgentState(session_id="test", user_message="test")
        state.record_tool_result("web_search", "result123", True)
        assert state.tool_call_counts["web_search"] == 1
        assert state.consecutive_errors == 0

    def test_record_tool_result_failure(self):
        state = AgentState(session_id="test", user_message="test")
        state.record_tool_result("web_search", "error", False)
        assert state.tool_failure_counts["web_search"] == 1
        assert state.consecutive_errors == 1


class TestTaskAnalysis:
    def test_determine_intent(self):
        from superfriend.nodes.planning import _determine_intent
        assert _determine_intent("search for cats") == TaskIntent.SEARCH
        assert _determine_intent("分析数据") == TaskIntent.ANALYZE
        assert _determine_intent("create a report") == TaskIntent.CREATE
        assert _determine_intent("update the file") == TaskIntent.MODIFY
        assert _determine_intent("hello world") == TaskIntent.UNKNOWN

    def test_determine_complexity(self):
        from superfriend.nodes.planning import _determine_complexity
        assert _determine_complexity("hello") == TaskComplexity.SIMPLE
        assert _determine_complexity("search and analyze") == TaskComplexity.MODERATE
        assert _determine_complexity("search multiple sources and analyze and create a report") == TaskComplexity.COMPLEX

    def test_task_plan_subtasks(self):
        plan = TaskPlan(task_id="t1", original_request="test")
        st1 = SubTask("st1", "task 1")
        st2 = SubTask("st2", "task 2")
        st2.add_dependency("st1")
        plan.sub_tasks = [st1, st2]

        assert plan.get_sub_task("st1") == st1
        assert plan.get_sub_task("st3") is None

        ready = plan.get_ready_sub_tasks()
        assert len(ready) == 1
        assert ready[0].sub_task_id == "st1"


class TestErrorHandling:
    def test_classify_error(self):
        from superfriend.nodes.tool_execution import classify_error
        assert classify_error("connection refused", "tool") == ErrorType.NETWORK_ERROR
        assert classify_error("timeout error", "tool") == ErrorType.TIMEOUT_ERROR
        assert classify_error("unauthorized", "auth") == ErrorType.AUTHENTICATION_ERROR
        assert classify_error("rate limit exceeded", "api") == ErrorType.RATE_LIMIT_ERROR

    def test_recovery_result(self):
        result = RecoveryResult.retry("retry now", 1000)
        assert result.action == RecoveryAction.RETRY_WITH_DELAY
        assert result.delay_ms == 1000

        result = RecoveryResult.with_alternative("use other", "fallback_tool")
        assert result.action == RecoveryAction.SWITCH_TOOL
        assert result.alternative_tool == "fallback_tool"


class TestAgentEvent:
    def test_thought_event(self):
        event = AgentEvent.thought("session-1", 0, "thinking...")
        assert event.type == EventType.REACT_THOUGHT
        assert event.session_id == "session-1"
        assert event.step_index == 0
        assert event.content == "thinking..."

    def test_action_event(self):
        event = AgentEvent.action("session-1", 1, "web_search", {"query": "test"})
        assert event.type == EventType.REACT_ACTION
        assert event.content == "web_search"
        assert event.data == {"query": "test"}