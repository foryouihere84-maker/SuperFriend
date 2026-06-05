"""
SQLAlchemy ORM 模型 - 完全匹配 Java Spring JPA 实体对应的 MySQL 表结构
表名和列名与 Java 项目保持一致，复用同一个 MySQL 数据库
"""
from datetime import datetime
from decimal import Decimal
from sqlalchemy import (
    String, Integer, BigInteger, Text, DateTime, Float, Boolean,
    Numeric, ForeignKey, Index
)
from sqlalchemy.orm import Mapped, mapped_column
from typing import Optional

from superfriend.db.session import Base


# ==================== 用户与权限 ====================

class User(Base):
    __tablename__ = "users"

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    username: Mapped[str] = mapped_column(String(100), unique=True, nullable=False)
    email: Mapped[Optional[str]] = mapped_column(String(255))
    password: Mapped[str] = mapped_column(String(255), nullable=False)
    display_name: Mapped[Optional[str]] = mapped_column(String(200))
    avatar_url: Mapped[Optional[str]] = mapped_column(String(500))
    status: Mapped[Optional[int]] = mapped_column(Integer, default=1)
    last_login_time: Mapped[Optional[datetime]] = mapped_column(DateTime)
    last_login_ip: Mapped[Optional[str]] = mapped_column(String(50))
    created_time: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow)
    updated_time: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)


class Role(Base):
    __tablename__ = "roles"

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    role_name: Mapped[str] = mapped_column(String(100), nullable=False)
    role_code: Mapped[str] = mapped_column(String(100), unique=True, nullable=False)
    description: Mapped[Optional[str]] = mapped_column(String(500))
    created_time: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow)
    updated_time: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)


class UserRole(Base):
    __tablename__ = "user_roles"

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    user_id: Mapped[int] = mapped_column(BigInteger, nullable=False, index=True)
    role_id: Mapped[int] = mapped_column(BigInteger, nullable=False)
    created_time: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow)


class LoginLog(Base):
    __tablename__ = "login_logs"

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    user_id: Mapped[Optional[int]] = mapped_column(BigInteger)
    username: Mapped[Optional[str]] = mapped_column(String(100))
    login_type: Mapped[Optional[str]] = mapped_column(String(50))
    login_ip: Mapped[Optional[str]] = mapped_column(String(50))
    user_agent: Mapped[Optional[str]] = mapped_column(String(500))
    status: Mapped[Optional[int]] = mapped_column(Integer, default=1)
    error_message: Mapped[Optional[str]] = mapped_column(String(500))
    created_time: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow)


# ==================== 聊天记录 ====================

class ChatHistory(Base):
    __tablename__ = "chat_history"

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    session_id: Mapped[str] = mapped_column(String(100), nullable=False, index=True)
    user_id: Mapped[Optional[int]] = mapped_column(BigInteger)
    title: Mapped[Optional[str]] = mapped_column(String(500))
    model: Mapped[Optional[str]] = mapped_column(String(100))
    mode: Mapped[Optional[str]] = mapped_column(String(50))
    message_count: Mapped[Optional[int]] = mapped_column(Integer, default=0)
    created_time: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow)
    updated_time: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)


class ChatMessageRecord(Base):
    __tablename__ = "chat_message"

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    history_id: Mapped[Optional[int]] = mapped_column(BigInteger)
    session_id: Mapped[str] = mapped_column(String(100), nullable=False, index=True)
    role: Mapped[str] = mapped_column(String(50), nullable=False)
    content: Mapped[str] = mapped_column(Text, nullable=False)
    created_time: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow)


class ChatCompression(Base):
    __tablename__ = "chat_compression"

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    session_id: Mapped[str] = mapped_column(String(100), nullable=False, index=True)
    original_message_count: Mapped[Optional[int]] = mapped_column(Integer)
    compressed_message_count: Mapped[Optional[int]] = mapped_column(Integer)
    summary: Mapped[Optional[str]] = mapped_column(Text)
    preserved_tool_results: Mapped[Optional[str]] = mapped_column(Text)
    compression_level: Mapped[Optional[str]] = mapped_column(String(50))
    original_tokens: Mapped[Optional[int]] = mapped_column(BigInteger)
    compressed_tokens: Mapped[Optional[int]] = mapped_column(BigInteger)
    compression_ratio: Mapped[Optional[float]] = mapped_column(Numeric(10, 4))
    created_time: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow)


# ==================== AI 模型配置 ====================

class AIModelConfigEntity(Base):
    __tablename__ = "ai_model_config"

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    config_id: Mapped[Optional[str]] = mapped_column(String(100))
    user_id: Mapped[Optional[int]] = mapped_column(BigInteger)
    name: Mapped[Optional[str]] = mapped_column(String(200))
    provider: Mapped[Optional[str]] = mapped_column(String(50))
    api_url: Mapped[Optional[str]] = mapped_column(String(500))
    api_key: Mapped[Optional[str]] = mapped_column(String(500))
    model_id: Mapped[str] = mapped_column(String(100), nullable=False)
    max_tokens: Mapped[Optional[int]] = mapped_column(Integer)
    temperature: Mapped[Optional[float]] = mapped_column(Numeric(5, 2))
    is_default: Mapped[Optional[bool]] = mapped_column(Boolean, default=False)
    is_enabled: Mapped[Optional[bool]] = mapped_column(Boolean, default=True)
    sort_order: Mapped[Optional[int]] = mapped_column(Integer, default=0)
    extra_params: Mapped[Optional[str]] = mapped_column(Text)
    supported_modalities: Mapped[Optional[str]] = mapped_column(String(500))
    output_modalities: Mapped[Optional[str]] = mapped_column(String(500))
    capabilities: Mapped[Optional[str]] = mapped_column(Text)
    created_time: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow)
    updated_time: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)


class EmbeddingModelConfig(Base):
    __tablename__ = "embedding_model_config"

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    config_id: Mapped[Optional[str]] = mapped_column(String(100))
    name: Mapped[Optional[str]] = mapped_column(String(200))
    provider: Mapped[Optional[str]] = mapped_column(String(50))
    api_url: Mapped[Optional[str]] = mapped_column(String(500))
    api_key: Mapped[Optional[str]] = mapped_column(String(500))
    model_id: Mapped[str] = mapped_column(String(100), nullable=False)
    dimensions: Mapped[Optional[int]] = mapped_column(Integer, default=1536)
    max_input_tokens: Mapped[Optional[int]] = mapped_column(Integer)
    is_default: Mapped[Optional[bool]] = mapped_column(Boolean, default=False)
    is_enabled: Mapped[Optional[bool]] = mapped_column(Boolean, default=True)
    sort_order: Mapped[Optional[int]] = mapped_column(Integer, default=0)
    created_time: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow)
    updated_time: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)


# ==================== 技能系统 ====================

class SkillEntity(Base):
    __tablename__ = "skills"

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    name: Mapped[str] = mapped_column(String(200), unique=True, nullable=False)
    description: Mapped[Optional[str]] = mapped_column(Text)
    category: Mapped[Optional[str]] = mapped_column(String(100))
    version: Mapped[Optional[str]] = mapped_column(String(50))
    author: Mapped[Optional[str]] = mapped_column(String(200))
    license: Mapped[Optional[str]] = mapped_column(String(100))
    compatibility: Mapped[Optional[str]] = mapped_column(String(200))
    priority: Mapped[Optional[int]] = mapped_column(Integer, default=0)
    scope: Mapped[Optional[int]] = mapped_column(Integer, default=0)
    instructions: Mapped[Optional[str]] = mapped_column(Text)
    parameters: Mapped[Optional[str]] = mapped_column(Text)  # JSON
    allowed_tools: Mapped[Optional[str]] = mapped_column(Text)  # JSON array
    tags: Mapped[Optional[str]] = mapped_column(Text)  # JSON array
    timeout: Mapped[Optional[int]] = mapped_column(BigInteger)
    scripts_json: Mapped[Optional[str]] = mapped_column(Text)
    resources_json: Mapped[Optional[str]] = mapped_column(Text)
    status: Mapped[Optional[int]] = mapped_column(Integer, default=1)
    is_selected: Mapped[Optional[bool]] = mapped_column(Boolean, default=False)
    skill_file_path: Mapped[Optional[str]] = mapped_column(String(500))
    execution_count: Mapped[Optional[int]] = mapped_column(BigInteger, default=0)
    success_rate: Mapped[Optional[float]] = mapped_column(Numeric(5, 2))
    average_execution_time: Mapped[Optional[int]] = mapped_column(Integer)
    last_used_time: Mapped[Optional[datetime]] = mapped_column(DateTime)
    created_time: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow)
    updated_time: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)


class SkillScript(Base):
    __tablename__ = "skill_scripts"

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    skill_id: Mapped[int] = mapped_column(BigInteger, nullable=False, index=True)
    script_type: Mapped[str] = mapped_column(String(50), nullable=False)
    script_name: Mapped[str] = mapped_column(String(200), nullable=False)
    script_content: Mapped[Optional[str]] = mapped_column(Text)
    is_main: Mapped[Optional[int]] = mapped_column(Integer, default=0)
    execution_order: Mapped[Optional[int]] = mapped_column(Integer, default=0)
    created_time: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow)
    updated_time: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)


class SkillExecution(Base):
    __tablename__ = "skill_executions"

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    skill_id: Mapped[int] = mapped_column(BigInteger, nullable=False, index=True)
    session_id: Mapped[Optional[str]] = mapped_column(String(100))
    user_id: Mapped[Optional[int]] = mapped_column(BigInteger)
    user_request: Mapped[Optional[str]] = mapped_column(Text)
    result: Mapped[Optional[str]] = mapped_column(Text)
    success: Mapped[Optional[int]] = mapped_column(Integer)
    execution_time: Mapped[Optional[int]] = mapped_column(Integer)
    error_message: Mapped[Optional[str]] = mapped_column(Text)
    created_time: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow)


class UserSkill(Base):
    __tablename__ = "user_skills"

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    user_id: Mapped[int] = mapped_column(BigInteger, nullable=False, index=True)
    skill_id: Mapped[int] = mapped_column(BigInteger, nullable=False)
    is_enabled: Mapped[Optional[int]] = mapped_column(Integer, default=1)
    is_selected: Mapped[Optional[bool]] = mapped_column(Boolean, default=False)
    custom_priority: Mapped[Optional[int]] = mapped_column(Integer)
    custom_tags: Mapped[Optional[str]] = mapped_column(String(500))
    created_time: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow)
    updated_time: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)


# ==================== Agent 任务规划 ====================

class AgentTaskPlan(Base):
    __tablename__ = "agent_task_plan"

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    plan_id: Mapped[str] = mapped_column(String(100), nullable=False, unique=True)
    session_id: Mapped[str] = mapped_column(String(100), nullable=False, index=True)
    user_id: Mapped[Optional[int]] = mapped_column(BigInteger)
    original_request: Mapped[Optional[str]] = mapped_column(Text)
    plan_summary: Mapped[Optional[str]] = mapped_column(Text)
    status: Mapped[str] = mapped_column(String(50), default="pending")
    total_steps: Mapped[Optional[int]] = mapped_column(Integer)
    completed_steps: Mapped[Optional[int]] = mapped_column(Integer, default=0)
    created_time: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow)
    started_time: Mapped[Optional[datetime]] = mapped_column(DateTime)
    completed_time: Mapped[Optional[datetime]] = mapped_column(DateTime)


class AgentTaskStep(Base):
    __tablename__ = "agent_task_step"

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    step_id: Mapped[str] = mapped_column(String(100), nullable=False)
    plan_id: Mapped[str] = mapped_column(String(100), nullable=False, index=True)
    step_number: Mapped[int] = mapped_column(Integer, nullable=False)
    description: Mapped[Optional[str]] = mapped_column(Text)
    status: Mapped[str] = mapped_column(String(50), default="pending")
    tool_name: Mapped[Optional[str]] = mapped_column(String(200))
    tool_arguments: Mapped[Optional[str]] = mapped_column(Text)
    result: Mapped[Optional[str]] = mapped_column(Text)
    error: Mapped[Optional[str]] = mapped_column(Text)
    execution_time_ms: Mapped[Optional[int]] = mapped_column(Integer)
    created_time: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow)
    completed_time: Mapped[Optional[datetime]] = mapped_column(DateTime)


# ==================== 权限与审批 ====================

class AgentPermissionPolicy(Base):
    __tablename__ = "agent_permission_policy"

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    user_id: Mapped[int] = mapped_column(BigInteger, nullable=False, index=True)
    tool_name: Mapped[str] = mapped_column(String(200), default="*")
    operation_type: Mapped[str] = mapped_column(String(50), default="*")
    resource_pattern: Mapped[str] = mapped_column(String(500), default="")
    permission_level: Mapped[str] = mapped_column(String(50), default="auto_allow")
    description: Mapped[Optional[str]] = mapped_column(Text)
    created_time: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow)
    updated_time: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)


class AgentApprovalLog(Base):
    __tablename__ = "agent_approval_log"

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    user_id: Mapped[Optional[int]] = mapped_column(BigInteger)
    session_id: Mapped[str] = mapped_column(String(100), default="")
    tool_name: Mapped[str] = mapped_column(String(200), default="")
    operation: Mapped[str] = mapped_column(String(50), default="")
    arguments: Mapped[Optional[str]] = mapped_column(Text)
    permission_level: Mapped[str] = mapped_column(String(50), default="")
    decision: Mapped[str] = mapped_column(String(50), default="")
    decided_by: Mapped[str] = mapped_column(String(50), default="")
    reason: Mapped[Optional[str]] = mapped_column(Text)
    response_time_ms: Mapped[Optional[int]] = mapped_column(Integer, default=0)
    created_time: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow)


# ==================== 知识图谱 ====================

class KnowledgeNodeEntity(Base):
    __tablename__ = "knowledge_node"

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    user_id: Mapped[Optional[int]] = mapped_column(BigInteger, index=True)
    node_type: Mapped[str] = mapped_column(String(50), default="CONCEPT")
    scope: Mapped[Optional[str]] = mapped_column(String(50), default="GLOBAL")
    name: Mapped[str] = mapped_column(String(500), nullable=False)
    description: Mapped[Optional[str]] = mapped_column(Text)
    avatar: Mapped[Optional[str]] = mapped_column(String(500))
    image: Mapped[Optional[str]] = mapped_column(String(500))
    detailed_description: Mapped[Optional[str]] = mapped_column(Text)
    keywords: Mapped[Optional[str]] = mapped_column(String(1000))
    importance: Mapped[Optional[int]] = mapped_column(Integer, default=5)
    properties: Mapped[Optional[str]] = mapped_column(Text)  # JSON
    source_session_id: Mapped[Optional[str]] = mapped_column(String(100))
    confidence: Mapped[Optional[float]] = mapped_column(Numeric(5, 4))
    access_count: Mapped[Optional[int]] = mapped_column(Integer, default=0)
    last_accessed_time: Mapped[Optional[datetime]] = mapped_column(DateTime)
    created_time: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow)
    updated_time: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)


class KnowledgeRelationEntity(Base):
    __tablename__ = "knowledge_relation"

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    user_id: Mapped[Optional[int]] = mapped_column(BigInteger)
    source_node_id: Mapped[int] = mapped_column(BigInteger, nullable=False, index=True)
    target_node_id: Mapped[int] = mapped_column(BigInteger, nullable=False, index=True)
    relation_type: Mapped[str] = mapped_column(String(100), default="RELATED_TO")
    properties: Mapped[Optional[str]] = mapped_column(Text)  # JSON
    weight: Mapped[Optional[float]] = mapped_column(Numeric(5, 4))
    source_session_id: Mapped[Optional[str]] = mapped_column(String(100))
    created_time: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow)


# ==================== 记忆宫殿 ====================

class MemoryPalace(Base):
    __tablename__ = "memory_palace"

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    user_id: Mapped[Optional[int]] = mapped_column(BigInteger, index=True)
    memory_type: Mapped[str] = mapped_column(String(50), default="NORMAL")
    category: Mapped[Optional[str]] = mapped_column(String(50))
    title: Mapped[Optional[str]] = mapped_column(String(500))
    content: Mapped[Optional[str]] = mapped_column(Text)
    keywords: Mapped[Optional[str]] = mapped_column(String(1000))
    memory_time: Mapped[Optional[datetime]] = mapped_column(DateTime)
    session_id: Mapped[Optional[str]] = mapped_column(String(100))
    importance: Mapped[Optional[int]] = mapped_column(Integer, default=5)
    confidence: Mapped[Optional[float]] = mapped_column(Numeric(5, 4))
    access_count: Mapped[Optional[int]] = mapped_column(Integer, default=0)
    reinforce_count: Mapped[Optional[int]] = mapped_column(Integer, default=0)
    decay_rate: Mapped[Optional[float]] = mapped_column(Numeric(8, 4))
    last_access_time: Mapped[Optional[datetime]] = mapped_column(DateTime)
    effective_score: Mapped[Optional[float]] = mapped_column(Numeric(8, 4))
    related_memory_ids: Mapped[Optional[str]] = mapped_column(Text)  # JSON
    source_node_id: Mapped[Optional[int]] = mapped_column(BigInteger)
    emotion_tag: Mapped[Optional[str]] = mapped_column(String(100))
    context_tags: Mapped[Optional[str]] = mapped_column(Text)  # JSON
    source_text: Mapped[Optional[str]] = mapped_column(Text)
    status: Mapped[str] = mapped_column(String(50), default="ACTIVE")
    created_time: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow)
    updated_time: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)


class MemoryEvent(Base):
    __tablename__ = "memory_event"

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    user_id: Mapped[Optional[int]] = mapped_column(BigInteger)
    memory_id: Mapped[Optional[int]] = mapped_column(BigInteger)
    event_type: Mapped[str] = mapped_column(String(50), nullable=False)
    event_time: Mapped[Optional[datetime]] = mapped_column(DateTime)
    event_data: Mapped[Optional[str]] = mapped_column(Text)
    session_id: Mapped[Optional[str]] = mapped_column(String(100))


class MemoryConnection(Base):
    __tablename__ = "memory_connection"

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    user_id: Mapped[Optional[int]] = mapped_column(BigInteger)
    source_memory_id: Mapped[int] = mapped_column(BigInteger, nullable=False)
    target_memory_id: Mapped[int] = mapped_column(BigInteger, nullable=False)
    connection_type: Mapped[str] = mapped_column(String(50), default="RELATED")
    strength: Mapped[Optional[float]] = mapped_column(Numeric(5, 4))
    created_time: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow)


# ==================== 文件管理 ====================

class UserFile(Base):
    __tablename__ = "user_file"

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    user_id: Mapped[Optional[int]] = mapped_column(BigInteger)
    session_id: Mapped[Optional[str]] = mapped_column(String(100))
    file_id: Mapped[Optional[str]] = mapped_column(String(100))
    file_name: Mapped[str] = mapped_column(String(500), nullable=False)
    stored_name: Mapped[Optional[str]] = mapped_column(String(500))
    file_path: Mapped[Optional[str]] = mapped_column(String(1000))
    file_size: Mapped[Optional[int]] = mapped_column(BigInteger)
    mime_type: Mapped[Optional[str]] = mapped_column(String(100))
    file_type: Mapped[Optional[str]] = mapped_column(String(50))
    status: Mapped[Optional[str]] = mapped_column(String(50), default="active")
    is_sensitive: Mapped[Optional[bool]] = mapped_column(Boolean, default=False)
    upload_time: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow)
    last_access_time: Mapped[Optional[datetime]] = mapped_column(DateTime)
    expire_time: Mapped[Optional[datetime]] = mapped_column(DateTime)
    file_metadata: Mapped[Optional[str]] = mapped_column("metadata", Text)  # JSON


# ==================== AI 处理历史 ====================

class AIProcessHistory(Base):
    __tablename__ = "ai_process_history"

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    name: Mapped[Optional[str]] = mapped_column(String(500))
    create_time: Mapped[Optional[datetime]] = mapped_column(DateTime)
    related_notes: Mapped[Optional[str]] = mapped_column(Text)
    content: Mapped[Optional[str]] = mapped_column(Text)
    process_type: Mapped[Optional[str]] = mapped_column(String(100))
    user_id: Mapped[Optional[int]] = mapped_column(BigInteger)
    session_id: Mapped[Optional[str]] = mapped_column(String(100))
    model: Mapped[Optional[str]] = mapped_column(String(100))
    input_tokens: Mapped[Optional[int]] = mapped_column(Integer)
    output_tokens: Mapped[Optional[int]] = mapped_column(Integer)
    total_cost: Mapped[Optional[float]] = mapped_column(Numeric(10, 6))
    created_time: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow)
    updated_time: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)


# ==================== 成本追踪 ====================

class CostRecord(Base):
    __tablename__ = "cost_record"

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    user_id: Mapped[Optional[int]] = mapped_column(BigInteger)
    session_id: Mapped[Optional[str]] = mapped_column(String(100))
    model: Mapped[Optional[str]] = mapped_column(String(100))
    input_tokens: Mapped[Optional[int]] = mapped_column(Integer, default=0)
    output_tokens: Mapped[Optional[int]] = mapped_column(Integer, default=0)
    cost_usd: Mapped[Optional[float]] = mapped_column(Numeric(10, 6), default=0)
    created_time: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow)


# ==================== 执行检查点 ====================

class ExecutionCheckpointEntity(Base):
    __tablename__ = "execution_checkpoint"

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    session_id: Mapped[str] = mapped_column(String(100), nullable=False, index=True)
    plan_id: Mapped[Optional[str]] = mapped_column(String(100))
    step_index: Mapped[Optional[int]] = mapped_column(Integer, default=0)
    step_name: Mapped[Optional[str]] = mapped_column(String(200))
    state_json: Mapped[Optional[str]] = mapped_column(Text)
    created_time: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow)
