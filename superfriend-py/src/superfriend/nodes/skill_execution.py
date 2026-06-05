import structlog

from superfriend.graph.state import AgentState
from superfriend.models.agent_event import AgentEvent
from superfriend.models.skill import SkillResult, SkillContext

logger = structlog.get_logger()


def detect_skills(state: AgentState) -> dict:
    if not state.enable_skills:
        return {}

    detected = []
    message_lower = state.user_message.lower()

    skill_patterns = {
        "excel": ["excel", "spreadsheet", "xlsx", "xls", "表格", "工作表"],
        "pdf": ["pdf", "document", "文档"],
        "pptx": ["pptx", "ppt", "presentation", "slides", "演示", "幻灯片"],
        "data_analysis": ["analyze", "statistics", "chart", "graph", "分析", "统计", "图表"],
        "code_generation": ["code", "program", "script", "代码", "程序", "脚本"],
        "web_search": ["search", "find", "look up", "查询", "搜索", "查找"],
        "file_operations": ["file", "folder", "directory", "文件", "目录"],
    }

    for skill_name, keywords in skill_patterns.items():
        if any(kw in message_lower for kw in keywords):
            detected.append({
                "name": skill_name,
                "confidence": 0.8,
                "matched_keywords": [kw for kw in keywords if kw in message_lower],
            })

    if detected:
        event = AgentEvent(
            event_id=__import__("uuid").uuid4().hex,
            type=AgentEvent.EventType.SKILL_DETECTED,
            timestamp=__import__("time").time() * 1000,
            step_index=state.current_iteration,
            session_id=state.session_id,
            content=f"Detected skills: {[s['name'] for s in detected]}",
        )
        state.emit_event(event)
        logger.info("skills_detected", skills=[s["name"] for s in detected])

    return {"detected_skills": detected}


async def execute_skill(state: AgentState, skill_registry: dict | None = None) -> dict:
    if not state.active_skill:
        return {"current_observation": "No active skill to execute"}

    skill_name = state.active_skill.get("name", "unknown")

    event = AgentEvent(
        event_id=__import__("uuid").uuid4().hex,
        type=AgentEvent.EventType.SKILL_EXECUTION_START,
        timestamp=__import__("time").time() * 1000,
        step_index=state.current_iteration,
        session_id=state.session_id,
        content=skill_name,
    )
    state.emit_event(event)

    try:
        if skill_registry and skill_name in skill_registry:
            result = await skill_registry[skill_name](state.user_message, state.session_id)
            skill_result = SkillResult(success=True, data=result)
        else:
            skill_result = SkillResult(success=True, data=f"[Simulated] Skill '{skill_name}' executed")

        state.working_memory[f"skill_{skill_name}"] = skill_result.data

        progress_event = AgentEvent(
            event_id=__import__("uuid").uuid4().hex,
            type=AgentEvent.EventType.SKILL_EXECUTION_RESULT,
            timestamp=__import__("time").time() * 1000,
            step_index=state.current_iteration,
            session_id=state.session_id,
            content=f"Skill '{skill_name}' completed",
        )
        state.emit_event(progress_event)

        logger.info("skill_executed", skill=skill_name)

        return {"current_observation": f"Skill '{skill_name}' executed: {str(skill_result.data)[:500]}"}

    except Exception as e:
        logger.error("skill_execution_failed", skill=skill_name, error=str(e))
        return {"current_observation": f"Skill '{skill_name}' failed: {str(e)}"}