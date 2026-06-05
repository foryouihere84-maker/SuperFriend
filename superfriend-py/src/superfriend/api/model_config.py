from __future__ import annotations

from fastapi import APIRouter, HTTPException, Query
from pydantic import BaseModel
from typing import Any

from superfriend.services.model_config_service import ai_model_config_service, model_capability_service

router = APIRouter()


class ModelConfigDTO(BaseModel):
    """对齐 Java AIModelConfigDTO"""
    name: str = ""
    model_id: str = ""
    provider: str = "openai"
    api_url: str = ""
    api_key: str = ""
    max_tokens: int = 4096
    temperature: float = 0.7
    supported_modalities: str | None = None


class ModelConfigCreateRequest(BaseModel):
    model_id: str
    model_name: str = ""
    provider: str = "openai"
    api_url: str = ""
    api_key: str = ""
    is_default: bool = False
    max_tokens: int = 4096
    temperature: float = 0.7
    supports_vision: bool = False
    supports_function_calling: bool = True
    supports_streaming: bool = True
    cost_per_1k_input: float = 0.0
    cost_per_1k_output: float = 0.0
    scope: int = 0


class ModelConfigUpdateRequest(BaseModel):
    model_name: str | None = None
    provider: str | None = None
    api_url: str | None = None
    api_key: str | None = None
    is_default: bool | None = None
    is_enabled: bool | None = None
    max_tokens: int | None = None
    temperature: float | None = None
    supports_vision: bool | None = None
    supports_function_calling: bool | None = None
    supports_streaming: bool | None = None
    cost_per_1k_input: float | None = None
    cost_per_1k_output: float | None = None


# ── 对齐 Java AIModelConfigController 的端点 ──

@router.get("/available")
async def get_available_models(user_id: int | None = None):
    """Java: GET /api/v16/model-config/available"""
    uid = user_id or 0
    models = await ai_model_config_service.get_available_models(uid)
    return {"success": True, "data": [
        {
            "id": m.id, "modelId": m.model_id, "name": m.model_name,
            "provider": m.provider, "apiUrl": m.api_url,
            "isDefault": m.is_default, "isEnabled": m.is_enabled,
            "maxTokens": m.max_tokens, "temperature": m.temperature,
            "supportsVision": m.supports_vision,
            "supportsFunctionCalling": m.supports_function_calling,
            "supportsStreaming": m.supports_streaming,
            "costPer1kInput": m.cost_per_1k_input,
            "costPer1kOutput": m.cost_per_1k_output,
        }
        for m in models
    ]}


@router.get("/user")
async def get_user_models(user_id: int = Query(...)):
    """Java: GET /api/v16/model-config/user"""
    models = await ai_model_config_service.get_available_models(user_id)
    return {"success": True, "data": [
        {
            "id": m.id, "modelId": m.model_id, "name": m.model_name,
            "provider": m.provider, "apiUrl": m.api_url,
            "isDefault": m.is_default, "isEnabled": m.is_enabled,
            "maxTokens": m.max_tokens, "temperature": m.temperature,
        }
        for m in models
    ]}


@router.get("/{config_id}")
async def get_model(config_id: str, user_id: int | None = None):
    """Java: GET /api/v16/model-config/{configId}"""
    try:
        config = await ai_model_config_service.resolve_model_config(config_id, user_id or 0)
        if not config:
            return {"success": False, "message": "模型配置不存在"}
        return {"success": True, "data": {
            "id": config.id, "modelId": config.model_id, "name": config.model_name,
            "provider": config.provider, "apiUrl": config.api_url,
            "isDefault": config.is_default, "isEnabled": config.is_enabled,
            "maxTokens": config.max_tokens, "temperature": config.temperature,
            "supportsVision": config.supports_vision,
            "supportsFunctionCalling": config.supports_function_calling,
            "supportsStreaming": config.supports_streaming,
        }}
    except Exception as e:
        return {"success": False, "message": str(e)}


@router.post("")
async def create_model(dto: ModelConfigDTO, user_id: int = Query(0)):
    """Java: POST /api/v16/model-config"""
    try:
        config = await ai_model_config_service.add_config(
            model_id=dto.model_id, model_name=dto.name or dto.model_id,
            provider=dto.provider, api_url=dto.api_url, api_key=dto.api_key,
            max_tokens=dto.max_tokens, temperature=dto.temperature,
            scope=1,  # 用户自定义
        )
        return {"success": True, "data": {
            "id": config.id, "modelId": config.model_id, "name": config.model_name,
            "provider": config.provider, "apiUrl": config.api_url,
        }}
    except Exception as e:
        return {"success": False, "message": str(e)}


@router.put("/{config_id}")
async def update_model(config_id: str, dto: ModelConfigDTO, user_id: int = Query(0)):
    """Java: PUT /api/v16/model-config/{configId}"""
    try:
        update_data = {}
        if dto.name: update_data["model_name"] = dto.name
        if dto.provider: update_data["provider"] = dto.provider
        if dto.api_url: update_data["api_url"] = dto.api_url
        if dto.api_key: update_data["api_key"] = dto.api_key
        if dto.max_tokens: update_data["max_tokens"] = dto.max_tokens
        if dto.temperature is not None: update_data["temperature"] = dto.temperature

        config = await ai_model_config_service.update_config(int(config_id), **update_data)
        if not config:
            return {"success": False, "message": "模型配置不存在"}
        return {"success": True, "data": {
            "id": config.id, "modelId": config.model_id, "name": config.model_name,
        }}
    except Exception as e:
        return {"success": False, "message": str(e)}


@router.delete("/{config_id}")
async def delete_model(config_id: str, user_id: int = Query(0)):
    """Java: DELETE /api/v16/model-config/{configId}"""
    deleted = await ai_model_config_service.delete_config(int(config_id))
    return {"success": deleted}


@router.put("/{config_id}/default")
async def set_default_model(config_id: str, user_id: int = Query(0)):
    """Java: PUT /api/v16/model-config/{configId}/default"""
    try:
        config = await ai_model_config_service.update_config(
            int(config_id), is_default=True
        )
        return {"success": True}
    except Exception as e:
        return {"success": False, "message": str(e)}


@router.post("/test")
async def test_connection(dto: ModelConfigDTO):
    """Java: POST /api/v16/model-config/test"""
    try:
        import httpx
        async with httpx.AsyncClient(timeout=10) as client:
            url = dto.api_url
            if not url.endswith("/chat/completions"):
                url = url.rstrip("/") + "/chat/completions"
            resp = await client.post(
                url,
                headers={"Authorization": f"Bearer {dto.api_key}", "Content-Type": "application/json"},
                json={"model": dto.model_id, "messages": [{"role": "user", "content": "hi"}], "max_tokens": 5},
            )
            if resp.status_code == 200:
                return {"success": True, "data": {"success": True, "message": "连接成功"}}
            return {"success": True, "data": {"success": False, "message": f"HTTP {resp.status_code}"}}
    except Exception as e:
        return {"success": True, "data": {"success": False, "message": str(e)}}


@router.get("/test/{config_id}")
async def test_connection_by_config_id(config_id: str):
    """Java: GET /api/v16/model-config/test/{configId}"""
    try:
        config = await ai_model_config_service.resolve_model_config(config_id)
        if not config:
            return {"success": True, "data": {"success": False, "message": "配置不存在"}}

        import httpx
        async with httpx.AsyncClient(timeout=10) as client:
            url = config.api_url or ""
            if not url.endswith("/chat/completions"):
                url = url.rstrip("/") + "/chat/completions"
            resp = await client.post(
                url,
                headers={"Authorization": f"Bearer {config.api_key}", "Content-Type": "application/json"},
                json={"model": config.model_id, "messages": [{"role": "user", "content": "hi"}], "max_tokens": 5},
            )
            if resp.status_code == 200:
                return {"success": True, "data": {"success": True, "message": "连接成功"}}
            return {"success": True, "data": {"success": False, "message": f"HTTP {resp.status_code}"}}
    except Exception as e:
        return {"success": True, "data": {"success": False, "message": str(e)}}


# ── 保留原有端点 ──

@router.get("/models")
async def list_models(user_id: int = 0):
    models = await ai_model_config_service.get_available_models(user_id)
    return {"models": [
        {
            "id": m.id, "model_id": m.model_id, "model_name": m.model_name,
            "provider": m.provider, "is_default": m.is_default, "is_enabled": m.is_enabled,
            "max_tokens": m.max_tokens, "temperature": m.temperature,
            "supports_vision": m.supports_vision, "supports_function_calling": m.supports_function_calling,
            "supports_streaming": m.supports_streaming,
        }
        for m in models
    ]}


@router.get("/capabilities")
async def list_capabilities():
    caps = model_capability_service.get_all_capabilities()
    return {"capabilities": {
        model_id: {
            "supports_vision": cap.supports_vision,
            "supports_function_calling": cap.supports_function_calling,
            "supports_streaming": cap.supports_streaming,
            "supports_json_mode": cap.supports_json_mode,
            "supports_parallel_tool_calls": cap.supports_parallel_tool_calls,
            "max_context_tokens": cap.max_context_tokens,
            "max_output_tokens": cap.max_output_tokens,
        }
        for model_id, cap in caps.items()
    }}
