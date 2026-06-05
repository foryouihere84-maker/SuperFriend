from datetime import datetime
from sqlalchemy import select, delete, update, func
from sqlalchemy.ext.asyncio import AsyncSession
from typing import Optional

from superfriend.db.models import (
    User, ChatHistory, ChatMessageRecord, ChatCompression,
    AIModelConfigEntity, EmbeddingModelConfig,
    SkillEntity, SkillScript, SkillExecution, UserSkill,
    AgentTaskPlan, AgentTaskStep,
    AgentPermissionPolicy, AgentApprovalLog,
    KnowledgeNodeEntity, KnowledgeRelationEntity,
    MemoryPalace, MemoryEvent, MemoryConnection,
    UserFile, AIProcessHistory, CostRecord, ExecutionCheckpointEntity,
)


class UserRepository:
    def __init__(self, session: AsyncSession):
        self.session = session

    async def get_by_id(self, user_id: int) -> Optional[User]:
        result = await self.session.execute(select(User).where(User.id == user_id))
        return result.scalar_one_or_none()

    async def get_by_username(self, username: str) -> Optional[User]:
        result = await self.session.execute(select(User).where(User.username == username))
        return result.scalar_one_or_none()

    async def create(self, username: str, password: str, email: str | None = None) -> User:
        user = User(username=username, password=password, email=email)
        self.session.add(user)
        await self.session.flush()
        return user


class ChatHistoryRepository:
    def __init__(self, session: AsyncSession):
        self.session = session

    async def create_session(self, session_id: str, user_id: int | None = None, title: str | None = None, model: str | None = None, mode: str | None = None) -> ChatHistory:
        h = ChatHistory(session_id=session_id, user_id=user_id, title=title, model=model, mode=mode)
        self.session.add(h)
        await self.session.flush()
        return h

    async def get_by_session(self, session_id: str) -> Optional[ChatHistory]:
        result = await self.session.execute(
            select(ChatHistory).where(ChatHistory.session_id == session_id)
        )
        return result.scalar_one_or_none()

    async def update_message_count(self, session_id: str) -> None:
        count_result = await self.session.execute(
            select(func.count(ChatMessageRecord.id)).where(ChatMessageRecord.session_id == session_id)
        )
        count = count_result.scalar() or 0
        await self.session.execute(
            update(ChatHistory).where(ChatHistory.session_id == session_id).values(message_count=count)
        )


class ChatMessageRepository:
    def __init__(self, session: AsyncSession):
        self.session = session

    async def add_message(self, session_id: str, role: str, content: str, history_id: int | None = None) -> ChatMessageRecord:
        msg = ChatMessageRecord(session_id=session_id, role=role, content=content, history_id=history_id)
        self.session.add(msg)
        await self.session.flush()
        return msg

    async def get_by_session(self, session_id: str, limit: int = 50) -> list[ChatMessageRecord]:
        result = await self.session.execute(
            select(ChatMessageRecord)
            .where(ChatMessageRecord.session_id == session_id)
            .order_by(ChatMessageRecord.created_time.desc())
            .limit(limit)
        )
        return list(result.scalars().all())

    async def clear_session(self, session_id: str) -> None:
        await self.session.execute(delete(ChatMessageRecord).where(ChatMessageRecord.session_id == session_id))


class AIModelConfigRepository:
    def __init__(self, session: AsyncSession):
        self.session = session

    async def get_default(self, user_id: int = 0) -> Optional[AIModelConfigEntity]:
        result = await self.session.execute(
            select(AIModelConfigEntity).where(
                AIModelConfigEntity.is_default == True,
                AIModelConfigEntity.is_enabled == True,
            ).limit(1)
        )
        return result.scalar_one_or_none()

    async def get_by_model_id(self, model_id: str, user_id: int = 0) -> Optional[AIModelConfigEntity]:
        result = await self.session.execute(
            select(AIModelConfigEntity).where(
                AIModelConfigEntity.model_id == model_id,
                AIModelConfigEntity.is_enabled == True,
            ).limit(1)
        )
        return result.scalar_one_or_none()

    async def get_available(self, user_id: int = 0) -> list[AIModelConfigEntity]:
        result = await self.session.execute(
            select(AIModelConfigEntity).where(
                AIModelConfigEntity.is_enabled == True,
            ).order_by(AIModelConfigEntity.is_default.desc())
        )
        return list(result.scalars().all())

    async def create(self, **kwargs) -> AIModelConfigEntity:
        config = AIModelConfigEntity(**kwargs)
        self.session.add(config)
        await self.session.flush()
        return config

    async def update(self, config_id: int, **kwargs) -> Optional[AIModelConfigEntity]:
        config = await self.session.get(AIModelConfigEntity, config_id)
        if not config:
            return None
        for k, v in kwargs.items():
            if hasattr(config, k) and v is not None:
                setattr(config, k, v)
        await self.session.flush()
        return config

    async def delete(self, config_id: int) -> bool:
        config = await self.session.get(AIModelConfigEntity, config_id)
        if not config:
            return False
        await self.session.delete(config)
        await self.session.flush()
        return True


class EmbeddingModelConfigRepository:
    def __init__(self, session: AsyncSession):
        self.session = session

    async def get_default(self) -> Optional[EmbeddingModelConfig]:
        result = await self.session.execute(
            select(EmbeddingModelConfig).where(
                EmbeddingModelConfig.is_default == True,
                EmbeddingModelConfig.is_enabled == True,
            ).limit(1)
        )
        return result.scalar_one_or_none()

    async def get_available(self) -> list[EmbeddingModelConfig]:
        result = await self.session.execute(
            select(EmbeddingModelConfig).where(EmbeddingModelConfig.is_enabled == True)
        )
        return list(result.scalars().all())

    async def create(self, **kwargs) -> EmbeddingModelConfig:
        config = EmbeddingModelConfig(**kwargs)
        self.session.add(config)
        await self.session.flush()
        return config


class SkillRepository:
    def __init__(self, session: AsyncSession):
        self.session = session

    async def get_all(self) -> list[SkillEntity]:
        result = await self.session.execute(select(SkillEntity))
        return list(result.scalars().all())

    async def get_by_name(self, name: str) -> Optional[SkillEntity]:
        result = await self.session.execute(select(SkillEntity).where(SkillEntity.name == name))
        return result.scalar_one_or_none()

    async def get_by_id(self, skill_id: int) -> Optional[SkillEntity]:
        return await self.session.get(SkillEntity, skill_id)

    async def create(self, **kwargs) -> SkillEntity:
        skill = SkillEntity(**kwargs)
        self.session.add(skill)
        await self.session.flush()
        return skill

    async def update(self, skill_id: int, **kwargs) -> Optional[SkillEntity]:
        skill = await self.session.get(SkillEntity, skill_id)
        if not skill:
            return None
        for k, v in kwargs.items():
            if hasattr(skill, k) and v is not None:
                setattr(skill, k, v)
        await self.session.flush()
        return skill

    async def delete(self, skill_id: int) -> bool:
        skill = await self.session.get(SkillEntity, skill_id)
        if not skill:
            return False
        await self.session.delete(skill)
        await self.session.flush()
        return True

    async def log_execution(self, **kwargs) -> SkillExecution:
        log = SkillExecution(**kwargs)
        self.session.add(log)
        await self.session.flush()
        return log


class TaskPlanRepository:
    def __init__(self, session: AsyncSession):
        self.session = session

    async def create(self, **kwargs) -> AgentTaskPlan:
        plan = AgentTaskPlan(**kwargs)
        self.session.add(plan)
        await self.session.flush()
        return plan

    async def update_status(self, plan_id: int, status: str) -> None:
        values: dict = {"status": status}
        if status == "running" or status == "executing":
            values["started_time"] = datetime.utcnow()
        elif status in ("completed", "failed", "cancelled"):
            values["completed_time"] = datetime.utcnow()
        await self.session.execute(
            update(AgentTaskPlan).where(AgentTaskPlan.id == plan_id).values(**values)
        )

    async def get_by_session(self, session_id: str) -> Optional[AgentTaskPlan]:
        result = await self.session.execute(
            select(AgentTaskPlan).where(AgentTaskPlan.session_id == session_id).order_by(AgentTaskPlan.created_time.desc())
        )
        return result.scalar_one_or_none()

    async def get_by_id(self, plan_id: int) -> Optional[AgentTaskPlan]:
        return await self.session.get(AgentTaskPlan, plan_id)

    async def add_step(self, **kwargs) -> AgentTaskStep:
        step = AgentTaskStep(**kwargs)
        self.session.add(step)
        await self.session.flush()
        return step

    async def update_step_status(self, step_id: int, status: str, result: str | None = None, error: str | None = None) -> None:
        values: dict = {"status": status}
        if result is not None:
            values["result"] = result
        if error is not None:
            values["error"] = error
        if status in ("completed", "failed"):
            values["completed_time"] = datetime.utcnow()
        await self.session.execute(update(AgentTaskStep).where(AgentTaskStep.id == step_id).values(**values))

    async def get_steps(self, plan_id: str) -> list[AgentTaskStep]:
        result = await self.session.execute(
            select(AgentTaskStep).where(AgentTaskStep.plan_id == plan_id).order_by(AgentTaskStep.step_number)
        )
        return list(result.scalars().all())


class PermissionPolicyRepository:
    def __init__(self, session: AsyncSession):
        self.session = session

    async def find_matching(self, user_id: int, tool_name: str, operation_type: str) -> list[AgentPermissionPolicy]:
        result = await self.session.execute(
            select(AgentPermissionPolicy).where(AgentPermissionPolicy.user_id == user_id)
        )
        policies = list(result.scalars().all())
        return [
            p for p in policies
            if (p.tool_name == "*" or p.tool_name == tool_name or tool_name.startswith(p.tool_name))
            and (p.operation_type == "*" or p.operation_type == operation_type)
        ]

    async def find_by_user(self, user_id: int) -> list[AgentPermissionPolicy]:
        result = await self.session.execute(
            select(AgentPermissionPolicy).where(AgentPermissionPolicy.user_id == user_id)
        )
        return list(result.scalars().all())

    async def create(self, **kwargs) -> AgentPermissionPolicy:
        policy = AgentPermissionPolicy(**kwargs)
        self.session.add(policy)
        await self.session.flush()
        return policy

    async def update(self, policy_id: int, **kwargs) -> Optional[AgentPermissionPolicy]:
        policy = await self.session.get(AgentPermissionPolicy, policy_id)
        if not policy:
            return None
        for k, v in kwargs.items():
            if hasattr(policy, k) and v is not None:
                setattr(policy, k, v)
        await self.session.flush()
        return policy

    async def delete(self, policy_id: int) -> bool:
        policy = await self.session.get(AgentPermissionPolicy, policy_id)
        if not policy:
            return False
        await self.session.delete(policy)
        await self.session.flush()
        return True


class ApprovalLogRepository:
    def __init__(self, session: AsyncSession):
        self.session = session

    async def create(self, **kwargs) -> AgentApprovalLog:
        log = AgentApprovalLog(**kwargs)
        self.session.add(log)
        await self.session.flush()
        return log

    async def find_by_user(self, user_id: int, limit: int = 50) -> list[AgentApprovalLog]:
        result = await self.session.execute(
            select(AgentApprovalLog).where(AgentApprovalLog.user_id == user_id)
            .order_by(AgentApprovalLog.created_time.desc()).limit(limit)
        )
        return list(result.scalars().all())

    async def find_by_session(self, session_id: str, limit: int = 50) -> list[AgentApprovalLog]:
        result = await self.session.execute(
            select(AgentApprovalLog).where(AgentApprovalLog.session_id == session_id)
            .order_by(AgentApprovalLog.created_time.desc()).limit(limit)
        )
        return list(result.scalars().all())


class KnowledgeNodeRepository:
    def __init__(self, session: AsyncSession):
        self.session = session

    async def create(self, **kwargs) -> KnowledgeNodeEntity:
        node = KnowledgeNodeEntity(**kwargs)
        self.session.add(node)
        await self.session.flush()
        return node

    async def get_by_id(self, node_id: int) -> Optional[KnowledgeNodeEntity]:
        return await self.session.get(KnowledgeNodeEntity, node_id)

    async def find_by_user(self, user_id: int, limit: int = 50) -> list[KnowledgeNodeEntity]:
        result = await self.session.execute(
            select(KnowledgeNodeEntity).where(KnowledgeNodeEntity.user_id == user_id)
            .order_by(KnowledgeNodeEntity.importance.desc()).limit(limit)
        )
        return list(result.scalars().all())

    async def search(self, user_id: int, query: str, limit: int = 20) -> list[KnowledgeNodeEntity]:
        result = await self.session.execute(
            select(KnowledgeNodeEntity).where(
                KnowledgeNodeEntity.user_id == user_id,
            ).order_by(KnowledgeNodeEntity.importance.desc()).limit(limit)
        )
        return list(result.scalars().all())

    async def delete(self, node_id: int) -> bool:
        node = await self.session.get(KnowledgeNodeEntity, node_id)
        if not node:
            return False
        await self.session.delete(node)
        await self.session.flush()
        return True


class KnowledgeRelationRepository:
    def __init__(self, session: AsyncSession):
        self.session = session

    async def create(self, **kwargs) -> KnowledgeRelationEntity:
        rel = KnowledgeRelationEntity(**kwargs)
        self.session.add(rel)
        await self.session.flush()
        return rel

    async def find_by_node(self, node_id: int) -> list[KnowledgeRelationEntity]:
        result = await self.session.execute(
            select(KnowledgeRelationEntity).where(
                (KnowledgeRelationEntity.source_node_id == node_id) |
                (KnowledgeRelationEntity.target_node_id == node_id)
            )
        )
        return list(result.scalars().all())

    async def delete(self, relation_id: int) -> bool:
        rel = await self.session.get(KnowledgeRelationEntity, relation_id)
        if not rel:
            return False
        await self.session.delete(rel)
        await self.session.flush()
        return True


class MemoryRepository:
    def __init__(self, session: AsyncSession):
        self.session = session

    async def create(self, **kwargs) -> MemoryPalace:
        memory = MemoryPalace(**kwargs)
        self.session.add(memory)
        await self.session.flush()
        return memory

    async def get_by_id(self, memory_id: int) -> Optional[MemoryPalace]:
        return await self.session.get(MemoryPalace, memory_id)

    async def find_by_user(self, user_id: int, memory_type: str | None = None, status: str | None = None, limit: int = 50) -> list[MemoryPalace]:
        q = select(MemoryPalace).where(MemoryPalace.user_id == user_id)
        if memory_type:
            q = q.where(MemoryPalace.memory_type == memory_type)
        if status:
            q = q.where(MemoryPalace.status == status)
        else:
            q = q.where(MemoryPalace.status == "ACTIVE")
        q = q.order_by(MemoryPalace.importance.desc()).limit(limit)
        result = await self.session.execute(q)
        return list(result.scalars().all())

    async def search(self, user_id: int, query: str, limit: int = 10) -> list[MemoryPalace]:
        result = await self.session.execute(
            select(MemoryPalace).where(MemoryPalace.user_id == user_id, MemoryPalace.status == "ACTIVE")
            .order_by(MemoryPalace.importance.desc()).limit(limit)
        )
        return list(result.scalars().all())

    async def update_access(self, memory_id: int) -> None:
        await self.session.execute(
            update(MemoryPalace).where(MemoryPalace.id == memory_id).values(
                access_count=MemoryPalace.access_count + 1,
                last_access_time=datetime.utcnow(),
            )
        )

    async def delete(self, memory_id: int) -> bool:
        memory = await self.session.get(MemoryPalace, memory_id)
        if not memory:
            return False
        await self.session.delete(memory)
        await self.session.flush()
        return True


class FileRepository:
    def __init__(self, session: AsyncSession):
        self.session = session

    async def create(self, **kwargs) -> UserFile:
        f = UserFile(**kwargs)
        self.session.add(f)
        await self.session.flush()
        return f

    async def get_by_id(self, file_id: int) -> Optional[UserFile]:
        return await self.session.get(UserFile, file_id)

    async def find_by_session(self, session_id: str) -> list[UserFile]:
        result = await self.session.execute(
            select(UserFile).where(UserFile.session_id == session_id)
            .order_by(UserFile.upload_time.desc())
        )
        return list(result.scalars().all())

    async def find_by_file_id(self, file_id: str) -> Optional[UserFile]:
        result = await self.session.execute(
            select(UserFile).where(UserFile.file_id == file_id).limit(1)
        )
        return result.scalar_one_or_none()

    async def delete(self, file_id: int) -> bool:
        f = await self.session.get(UserFile, file_id)
        if not f:
            return False
        await self.session.delete(f)
        await self.session.flush()
        return True


class CostRecordRepository:
    def __init__(self, session: AsyncSession):
        self.session = session

    async def create(self, **kwargs) -> CostRecord:
        record = CostRecord(**kwargs)
        self.session.add(record)
        await self.session.flush()
        return record

    async def get_by_session(self, session_id: str) -> list[CostRecord]:
        result = await self.session.execute(
            select(CostRecord).where(CostRecord.session_id == session_id)
            .order_by(CostRecord.created_time.desc())
        )
        return list(result.scalars().all())

    async def get_by_user(self, user_id: int, limit: int = 100) -> list[CostRecord]:
        result = await self.session.execute(
            select(CostRecord).where(CostRecord.user_id == user_id)
            .order_by(CostRecord.created_time.desc()).limit(limit)
        )
        return list(result.scalars().all())

    async def get_daily_summary(self, user_id: int | None = None) -> list[dict]:
        q = select(
            CostRecord.model,
            func.sum(CostRecord.input_tokens).label("total_input"),
            func.sum(CostRecord.output_tokens).label("total_output"),
            func.sum(CostRecord.cost_usd).label("total_cost"),
            func.count(CostRecord.id).label("call_count"),
        ).group_by(CostRecord.model)
        if user_id:
            q = q.where(CostRecord.user_id == user_id)
        result = await self.session.execute(q)
        return [
            {"model": row.model, "total_input": row.total_input, "total_output": row.total_output,
             "total_cost": row.total_cost, "call_count": row.call_count}
            for row in result
        ]


class CheckpointRepository:
    def __init__(self, session: AsyncSession):
        self.session = session

    async def create(self, **kwargs) -> ExecutionCheckpointEntity:
        cp = ExecutionCheckpointEntity(**kwargs)
        self.session.add(cp)
        await self.session.flush()
        return cp

    async def get_latest(self, session_id: str) -> Optional[ExecutionCheckpointEntity]:
        result = await self.session.execute(
            select(ExecutionCheckpointEntity).where(ExecutionCheckpointEntity.session_id == session_id)
            .order_by(ExecutionCheckpointEntity.step_index.desc()).limit(1)
        )
        return result.scalar_one_or_none()

    async def get_all(self, session_id: str) -> list[ExecutionCheckpointEntity]:
        result = await self.session.execute(
            select(ExecutionCheckpointEntity).where(ExecutionCheckpointEntity.session_id == session_id)
            .order_by(ExecutionCheckpointEntity.step_index)
        )
        return list(result.scalars().all())

    async def delete_by_session(self, session_id: str) -> int:
        result = await self.session.execute(
            delete(ExecutionCheckpointEntity).where(ExecutionCheckpointEntity.session_id == session_id)
        )
        return result.rowcount
