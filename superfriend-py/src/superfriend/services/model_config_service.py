from __future__ import annotations

import structlog
from dataclasses import dataclass, field
from typing import Any
from enum import Enum

logger = structlog.get_logger()


class ModelProvider(str, Enum):
    OPENAI = "openai"
    ZHIPU = "zhipu"
    DEEPSEEK = "deepseek"
    ANTHROPIC = "anthropic"
    CUSTOM = "custom"


@dataclass
class AIModelConfig:
    id: int | None = None
    user_id: int = 0
    model_id: str = ""
    model_name: str = ""
    provider: str = ModelProvider.OPENAI.value
    api_url: str = ""
    api_key: str = ""
    is_default: bool = False
    is_enabled: bool = True
    max_tokens: int = 4096
    temperature: float = 0.7
    supports_vision: bool = False
    supports_function_calling: bool = True
    supports_streaming: bool = True
    cost_per_1k_input: float = 0.0
    cost_per_1k_output: float = 0.0
    scope: int = 0


@dataclass
class ModelCapability:
    model_id: str = ""
    supports_vision: bool = False
    supports_function_calling: bool = True
    supports_streaming: bool = True
    supports_json_mode: bool = False
    supports_parallel_tool_calls: bool = False
    max_context_tokens: int = 4096
    max_output_tokens: int = 4096
    supported_languages: list[str] = field(default_factory=lambda: ["zh", "en"])


KNOWN_CAPABILITIES: dict[str, ModelCapability] = {
    "gpt-4": ModelCapability(model_id="gpt-4", supports_vision=False, max_context_tokens=8192, max_output_tokens=4096, supports_json_mode=True, supports_parallel_tool_calls=True),
    "gpt-4-turbo": ModelCapability(model_id="gpt-4-turbo", supports_vision=True, max_context_tokens=128000, max_output_tokens=4096, supports_json_mode=True, supports_parallel_tool_calls=True),
    "gpt-4o": ModelCapability(model_id="gpt-4o", supports_vision=True, max_context_tokens=128000, max_output_tokens=16384, supports_json_mode=True, supports_parallel_tool_calls=True),
    "gpt-4o-mini": ModelCapability(model_id="gpt-4o-mini", supports_vision=True, max_context_tokens=128000, max_output_tokens=16384, supports_json_mode=True, supports_parallel_tool_calls=True),
    "deepseek-chat": ModelCapability(model_id="deepseek-chat", supports_vision=False, max_context_tokens=64000, max_output_tokens=8192, supports_json_mode=True, supports_function_calling=True),
    "deepseek-reasoner": ModelCapability(model_id="deepseek-reasoner", supports_vision=False, max_context_tokens=64000, max_output_tokens=8192, supports_function_calling=False),
    "glm-4": ModelCapability(model_id="glm-4", supports_vision=True, max_context_tokens=128000, max_output_tokens=4096, supports_json_mode=True),
    "glm-4-flash": ModelCapability(model_id="glm-4-flash", supports_vision=True, max_context_tokens=128000, max_output_tokens=4096),
    "claude-3-opus": ModelCapability(model_id="claude-3-opus", supports_vision=True, max_context_tokens=200000, max_output_tokens=4096, supports_parallel_tool_calls=True),
    "claude-3-sonnet": ModelCapability(model_id="claude-3-sonnet", supports_vision=True, max_context_tokens=200000, max_output_tokens=4096, supports_parallel_tool_calls=True),
    "claude-3-haiku": ModelCapability(model_id="claude-3-haiku", supports_vision=True, max_context_tokens=200000, max_output_tokens=4096, supports_parallel_tool_calls=True),
}


def _entity_to_config(entity) -> AIModelConfig:
    """将数据库实体转换为 AIModelConfig dataclass"""
    return AIModelConfig(
        id=entity.id,
        user_id=entity.user_id or 0,
        model_id=entity.model_id,
        model_name=entity.name or entity.model_id,
        provider=entity.provider or "openai",
        api_url=entity.api_url or "",
        api_key=entity.api_key or "",
        is_default=bool(entity.is_default),
        is_enabled=bool(entity.is_enabled),
        max_tokens=entity.max_tokens or 4096,
        temperature=float(entity.temperature) if entity.temperature else 0.7,
        scope=entity.scope if hasattr(entity, 'scope') else 0,
    )


class AIModelConfigService:
    """模型配置服务 - 从数据库读取"""

    async def _get_repo(self):
        from superfriend.db.session import async_session_factory
        from superfriend.db.repository import AIModelConfigRepository
        session = async_session_factory()
        return session, AIModelConfigRepository(session)

    async def add_config(self, model_id: str, model_name: str = "", provider: str = ModelProvider.OPENAI.value,
                         api_url: str = "", api_key: str = "", user_id: int = 0, is_default: bool = False,
                         max_tokens: int = 4096, temperature: float = 0.7, scope: int = 0, **kwargs: Any) -> AIModelConfig:
        session, repo = await self._get_repo()
        try:
            entity = await repo.create(
                model_id=model_id, name=model_name or model_id, provider=provider,
                api_url=api_url, api_key=api_key, user_id=user_id, is_default=is_default,
                max_tokens=max_tokens, temperature=temperature,
            )
            await session.commit()
            return _entity_to_config(entity)
        finally:
            await session.close()

    async def get_default_model(self, user_id: int = 0) -> AIModelConfig | None:
        session, repo = await self._get_repo()
        try:
            entity = await repo.get_default(user_id)
            return _entity_to_config(entity) if entity else None
        finally:
            await session.close()

    async def resolve_model_config(self, model_id: str, user_id: int = 0) -> AIModelConfig | None:
        session, repo = await self._get_repo()
        try:
            entity = await repo.get_by_model_id(model_id, user_id)
            return _entity_to_config(entity) if entity else None
        finally:
            await session.close()

    async def get_available_models(self, user_id: int = 0) -> list[AIModelConfig]:
        session, repo = await self._get_repo()
        try:
            entities = await repo.get_available(user_id)
            return [_entity_to_config(e) for e in entities]
        finally:
            await session.close()

    async def get_available_model_entities(self, user_id: int = 0) -> list[AIModelConfig]:
        return await self.get_available_models(user_id)

    async def update_config(self, config_id: int, **kwargs: Any) -> AIModelConfig | None:
        session, repo = await self._get_repo()
        try:
            entity = await repo.update(config_id, **kwargs)
            await session.commit()
            return _entity_to_config(entity) if entity else None
        finally:
            await session.close()

    async def delete_config(self, config_id: int) -> bool:
        session, repo = await self._get_repo()
        try:
            result = await repo.delete(config_id)
            await session.commit()
            return result
        finally:
            await session.close()


class ModelCapabilityService:
    def get_capability(self, model_id: str) -> ModelCapability:
        if model_id in KNOWN_CAPABILITIES:
            return KNOWN_CAPABILITIES[model_id]
        for key, cap in KNOWN_CAPABILITIES.items():
            if model_id.startswith(key.split("-")[0]):
                return cap
        return ModelCapability(model_id=model_id)

    def supports_vision(self, model_id: str) -> bool:
        return self.get_capability(model_id).supports_vision

    def supports_function_calling(self, model_id: str) -> bool:
        return self.get_capability(model_id).supports_function_calling

    def get_max_context_tokens(self, model_id: str) -> int:
        return self.get_capability(model_id).max_context_tokens

    def get_all_capabilities(self) -> dict[str, ModelCapability]:
        return dict(KNOWN_CAPABILITIES)


ai_model_config_service = AIModelConfigService()
model_capability_service = ModelCapabilityService()
