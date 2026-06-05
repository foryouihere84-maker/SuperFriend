from __future__ import annotations

import structlog
from fastapi import APIRouter, HTTPException, Query, Request
from pydantic import BaseModel

from superfriend.services.skill_registry import skill_registry
from superfriend.models.skill import SkillConfig, SkillMetadata, SkillTriggerType

router = APIRouter()
logger = structlog.get_logger()


class SkillRegisterRequest(BaseModel):
    name: str
    description: str = ""
    trigger_type: str = "tool"
    tools: list[str] = []


class SkillExecuteRequest(BaseModel):
    skill_name: str
    session_id: str = ""
    user_request: str = ""
    user_id: str | None = None
    parameters: dict = {}
    variables: dict = {}


class SkillSuggestRequest(BaseModel):
    userRequest: str = ""


# ── 对齐 Java SkillController 的端点 ──

@router.get("")
async def get_all_skills(request: Request):
    """Java: GET /api/v16/skills"""
    try:
        skills = skill_registry.list_skills()
        result = []
        for s in skills:
            result.append({
                "name": s.name,
                "description": s.description,
                "triggerType": s.trigger_type.value,
                "tools": s.tools,
                "category": getattr(s, "category", "general"),
                "scope": "SYSTEM",
                "isSelected": True,
            })

        # 从数据库加载用户自定义技能
        try:
            from superfriend.db.session import async_session_factory
            from superfriend.db.models import SkillEntity
            from sqlalchemy import select

            user_id = request.headers.get("X-User-Id")
            async with async_session_factory() as session:
                q = select(SkillEntity).where(SkillEntity.scope == 3)
                db_result = await session.execute(q)
                db_skills = list(db_result.scalars().all())
                for s in db_skills:
                    result.append({
                        "name": s.name,
                        "description": s.description or "",
                        "triggerType": "tool",
                        "tools": [],
                        "category": s.category or "custom",
                        "scope": "USER",
                        "isSelected": True,
                    })
        except Exception as e:
            logger.warning("load_user_skills_failed", error=str(e))

        return {"success": True, "data": result}
    except Exception as e:
        logger.error("get_skills_error", error=str(e))
        return {"success": False, "message": str(e), "data": []}


@router.get("/{name}")
async def get_skill(name: str):
    """Java: GET /api/v16/skills/{name}"""
    skill = skill_registry.get_skill(name)
    if skill:
        return {"success": True, "data": {
            "name": skill.name,
            "description": skill.description,
            "triggerType": skill.trigger_type.value,
            "tools": skill.tools,
        }}
    # 尝试从数据库查找
    try:
        from superfriend.db.session import async_session_factory
        from superfriend.db.models import SkillEntity
        from sqlalchemy import select

        async with async_session_factory() as session:
            result = await session.execute(
                select(SkillEntity).where(SkillEntity.name == name)
            )
            entity = result.scalar_one_or_none()
            if entity:
                return {"success": True, "data": {
                    "name": entity.name,
                    "description": entity.description or "",
                    "category": entity.category or "custom",
                    "scope": "USER",
                }}
    except Exception as e:
        logger.warning("get_skill_db_failed", error=str(e))

    return {"success": False, "message": f"技能不存在: {name}"}


@router.get("/categories")
async def get_categories():
    """Java: GET /api/v16/skills/categories"""
    skills = skill_registry.list_skills()
    categories = set()
    for s in skills:
        cat = getattr(s, "category", "general")
        if cat:
            categories.add(cat)
    return {"success": True, "data": list(categories)}


@router.get("/search")
async def search_skills(keyword: str = Query("")):
    """Java: GET /api/v16/skills/search"""
    matches = skill_registry.match_skills(keyword)
    return {"success": True, "data": [
        {"name": config.name, "description": config.description, "score": score}
        for config, score in matches
    ]}


@router.post("/suggest")
async def suggest_skills(request: SkillSuggestRequest):
    """Java: POST /api/v16/skills/suggest"""
    matches = skill_registry.match_skills(request.userRequest)
    return {"success": True, "data": [
        {"name": config.name, "description": config.description}
        for config, _ in matches[:5]
    ]}


@router.post("/execute")
async def execute_skill(request: SkillExecuteRequest):
    """Java: POST /api/v16/skills/execute"""
    try:
        result = skill_registry.execute_skill(
            name=request.skill_name,
            session_id=request.session_id,
            user_request=request.user_request,
            parameters=request.parameters,
        )
        return {"success": True, "data": {
            "success": True,
            "data": result,
            "executionTime": 0,
        }}
    except Exception as e:
        logger.error("execute_skill_error", error=str(e))
        return {"success": True, "data": {
            "success": False,
            "error": str(e),
            "executionTime": 0,
        }}


@router.post("/create")
async def create_skill(request: SkillRegisterRequest):
    """Java: POST /api/v16/skills/create"""
    try:
        trigger_type = SkillTriggerType.TOOL
        if request.trigger_type == "script":
            trigger_type = SkillTriggerType.SCRIPT
        elif request.trigger_type == "hybrid":
            trigger_type = SkillTriggerType.HYBRID

        skill_registry.register(
            name=request.name,
            description=request.description,
            trigger_type=trigger_type,
            tools=request.tools,
        )

        # 同时保存到数据库
        try:
            from superfriend.db.session import async_session_factory
            from superfriend.db.models import SkillEntity

            async with async_session_factory() as session:
                entity = SkillEntity(
                    name=request.name,
                    description=request.description,
                    trigger_type=request.trigger_type,
                    scope=3,  # 用户自定义
                )
                session.add(entity)
                await session.commit()
        except Exception as e:
            logger.warning("save_skill_to_db_failed", error=str(e))

        return {"success": True, "data": f"技能 {request.name} 创建成功"}
    except Exception as e:
        return {"success": False, "message": str(e)}


@router.delete("/{name}")
async def delete_skill(name: str):
    """Java: DELETE /api/v16/skills/{name}"""
    try:
        skill_registry.unregister(name)
        # 从数据库删除
        try:
            from superfriend.db.session import async_session_factory
            from superfriend.db.models import SkillEntity
            from sqlalchemy import select, delete

            async with async_session_factory() as session:
                await session.execute(
                    delete(SkillEntity).where(SkillEntity.name == name)
                )
                await session.commit()
        except Exception as e:
            logger.warning("delete_skill_from_db_failed", error=str(e))

        return {"success": True, "data": f"技能 {name} 删除成功"}
    except Exception as e:
        return {"success": False, "message": str(e)}


@router.get("/stats")
async def get_skill_stats():
    """Java: GET /api/v16/skills/stats"""
    skills = skill_registry.list_skills()
    return {"success": True, "data": {
        "totalSkills": len(skills),
        "categories": len(set(getattr(s, "category", "general") for s in skills)),
    }}


# ── 保留原有端点 ──

@router.post("/register")
async def register_skill(request: SkillRegisterRequest):
    try:
        trigger_type = SkillTriggerType.TOOL
        if request.trigger_type == "script":
            trigger_type = SkillTriggerType.SCRIPT
        elif request.trigger_type == "hybrid":
            trigger_type = SkillTriggerType.HYBRID

        skill_registry.register(
            name=request.name,
            description=request.description,
            trigger_type=trigger_type,
            tools=request.tools,
        )
        return {"status": "registered", "name": request.name}
    except Exception as e:
        raise HTTPException(status_code=400, detail=str(e))


@router.post("/match")
async def match_skills(query: str = ""):
    matches = skill_registry.match_skills(query)
    return {
        "matches": [
            {"name": config.name, "score": score}
            for config, score in matches
        ]
    }
