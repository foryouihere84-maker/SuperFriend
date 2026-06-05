from __future__ import annotations

import structlog
from dataclasses import dataclass, field
from typing import Any

from superfriend.services.llm_client import llm_client

logger = structlog.get_logger()


@dataclass
class MessageContent:
    role: str = ""
    text: str = ""
    images: list[str] = field(default_factory=list)
    files: list[str] = field(default_factory=list)
    tool_calls: list[dict[str, Any]] = field(default_factory=list)
    tool_call_id: str = ""
    tool_name: str = ""


class MessageContentBuilder:
    def build_user_message(self, text: str, images: list[str] | None = None,
                           files: list[str] | None = None) -> dict[str, Any]:
        if not images and not files:
            return {"role": "user", "content": text}

        content: list[dict[str, Any]] = [{"type": "text", "text": text}]

        for img_url in (images or []):
            content.append({"type": "image_url", "image_url": {"url": img_url}})

        for file_content in (files or []):
            content.append({"type": "text", "text": f"[File content]:\n{file_content}"})

        return {"role": "user", "content": content}

    def build_assistant_message(self, text: str, tool_calls: list[dict[str, Any]] | None = None) -> dict[str, Any]:
        msg: dict[str, Any] = {"role": "assistant", "content": text}
        if tool_calls:
            msg["tool_calls"] = tool_calls
        return msg

    def build_tool_message(self, tool_call_id: str, content: str, tool_name: str = "") -> dict[str, Any]:
        msg: dict[str, Any] = {"role": "tool", "tool_call_id": tool_call_id, "content": content}
        if tool_name:
            msg["name"] = tool_name
        return msg

    def build_system_message(self, content: str) -> dict[str, Any]:
        return {"role": "system", "content": content}

    def build_messages_from_history(self, history: list[dict[str, str]]) -> list[dict[str, Any]]:
        messages = []
        for h in history:
            role = h.get("role", "")
            content = h.get("content", "")
            if role == "system":
                messages.append(self.build_system_message(content))
            elif role == "user":
                messages.append(self.build_user_message(content))
            elif role == "assistant":
                tool_calls_str = h.get("tool_calls")
                tool_calls = None
                if tool_calls_str:
                    import json
                    try:
                        tool_calls = json.loads(tool_calls_str) if isinstance(tool_calls_str, str) else tool_calls_str
                    except (json.JSONDecodeError, TypeError):
                        pass
                messages.append(self.build_assistant_message(content, tool_calls))
            elif role == "tool":
                messages.append(self.build_tool_message(
                    tool_call_id=h.get("tool_call_id", ""),
                    content=content,
                    tool_name=h.get("name", ""),
                ))
        return messages


message_content_builder = MessageContentBuilder()


class ContextSummarizerService:
    async def summarize(self, messages: list[dict[str, Any]], max_length: int = 500, model: str = "deepseek-chat") -> str:
        if not messages:
            return ""

        combined = self._concat_messages(messages)
        if len(combined) <= max_length:
            return combined

        prompt = (
            f"请将以下对话历史压缩为不超过{max_length}字的摘要，保留关键信息和上下文：\n\n"
            f"{combined[:8000]}\n\n摘要："
        )

        try:
            response = await llm_client.chat(
                messages=[{"role": "user", "content": prompt}],
                model=model, temperature=0.3, max_tokens=max_length * 2,
            )
            content = await llm_client.extract_content(response)
            return content[:max_length] if content else combined[:max_length]
        except Exception as e:
            logger.warning("summarize_error", error=str(e))
            return combined[:max_length]

    def summarize_sync(self, messages: list[dict[str, Any]], max_length: int = 500) -> str:
        if not messages:
            return ""
        combined = self._concat_messages(messages)
        if len(combined) <= max_length:
            return combined
        return combined[:max_length] + "..."

    def _concat_messages(self, messages: list[dict[str, Any]]) -> str:
        parts = []
        for msg in messages:
            role = msg.get("role", "")
            content = msg.get("content", "")
            if isinstance(content, str) and content:
                parts.append(f"[{role}] {content}")
            elif isinstance(content, list):
                for part in content:
                    if isinstance(part, dict) and part.get("type") == "text":
                        parts.append(f"[{role}] {part.get('text', '')}")
        return "\n".join(parts)


context_summarizer_service = ContextSummarizerService()