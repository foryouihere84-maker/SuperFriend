import json
import time
import httpx
import structlog
from typing import Any, AsyncIterator

from superfriend.config import settings

logger = structlog.get_logger()


class LLMClient:
    """LLM 客户端 - 支持从数据库读取模型配置"""

    def __init__(self):
        self._base_url = settings.llm_api_url.rstrip("/")
        self._api_key = settings.llm_api_key
        self._default_model = settings.llm_default_model

    async def _resolve_config(self, model: str) -> tuple[str, str, str]:
        """从数据库解析模型配置，返回 (api_url, api_key, model_id)"""
        try:
            from superfriend.db.session import async_session_factory
            from superfriend.db.repository import AIModelConfigRepository

            async with async_session_factory() as session:
                repo = AIModelConfigRepository(session)
                # 先按 model_id 查找
                config = await repo.get_by_model_id(model)
                if config:
                    api_url = config.api_url or self._base_url
                    # 确保 URL 以 /chat/completions 结尾（如果需要）
                    if not api_url.endswith("/chat/completions") and "/chat/completions" not in api_url:
                        if api_url.endswith("/v1"):
                            api_url = f"{api_url}/chat/completions"
                        elif not api_url.endswith("/"):
                            api_url = f"{api_url}/chat/completions"
                    return api_url, config.api_key, config.model_id

                # 没找到则用默认配置
                return self._base_url, self._api_key, model
        except Exception as e:
            logger.warning("resolve_config_failed", error=str(e), model=model)
            return self._base_url, self._api_key, model

    async def chat(
        self,
        messages: list[dict[str, Any]],
        model: str | None = None,
        tools: list[dict[str, Any]] | None = None,
        temperature: float = 0.7,
        max_tokens: int = 4096,
        stream: bool = False,
    ) -> dict[str, Any]:
        model = model or self._default_model
        api_url, api_key, model_id = await self._resolve_config(model)

        # 如果 api_url 已经包含 /chat/completions 就直接用，否则拼接
        if "/chat/completions" in api_url:
            url = api_url
        else:
            url = f"{api_url.rstrip('/')}/chat/completions"

        payload = {
            "model": model_id,
            "messages": messages,
            "temperature": temperature,
            "max_tokens": max_tokens,
            "stream": stream,
        }

        if tools:
            payload["tools"] = tools
            payload["tool_choice"] = "auto"

        async with httpx.AsyncClient(timeout=120.0) as client:
            response = await client.post(
                url,
                json=payload,
                headers={
                    "Authorization": f"Bearer {api_key}",
                    "Content-Type": "application/json",
                },
            )
            response.raise_for_status()
            return response.json()

    async def chat_stream(
        self,
        messages: list[dict[str, Any]],
        model: str | None = None,
        tools: list[dict[str, Any]] | None = None,
        temperature: float = 0.7,
        max_tokens: int = 4096,
    ) -> AsyncIterator[str]:
        model = model or self._default_model
        api_url, api_key, model_id = await self._resolve_config(model)

        if "/chat/completions" in api_url:
            url = api_url
        else:
            url = f"{api_url.rstrip('/')}/chat/completions"

        payload = {
            "model": model_id,
            "messages": messages,
            "temperature": temperature,
            "max_tokens": max_tokens,
            "stream": True,
        }

        if tools:
            payload["tools"] = tools
            payload["tool_choice"] = "auto"

        async with httpx.AsyncClient(timeout=300.0) as client:
            async with client.stream(
                "POST",
                url,
                json=payload,
                headers={
                    "Authorization": f"Bearer {api_key}",
                    "Content-Type": "application/json",
                },
            ) as response:
                response.raise_for_status()
                async for line in response.aiter_lines():
                    if line.startswith("data: "):
                        data_str = line[6:]
                        if data_str == "[DONE]":
                            break
                        try:
                            data = json.loads(data_str)
                            delta = data.get("choices", [{}])[0].get("delta", {})
                            content = delta.get("content", "")
                            if content:
                                yield content
                        except json.JSONDecodeError:
                            continue

    async def extract_tool_calls(self, response: dict) -> list[dict[str, Any]]:
        tool_calls = []
        for choice in response.get("choices", []):
            message = choice.get("message", {})
            for tc in message.get("tool_calls", []):
                func = tc.get("function", {})
                tool_calls.append({
                    "id": tc.get("id", ""),
                    "name": func.get("name", ""),
                    "arguments": func.get("arguments", "{}"),
                })
        return tool_calls

    async def extract_content(self, response: dict) -> str:
        for choice in response.get("choices", []):
            message = choice.get("message", {})
            content = message.get("content", "")
            if content:
                return content
        return ""

    def estimate_tokens(self, text: str) -> int:
        return len(text) // 4


llm_client = LLMClient()
