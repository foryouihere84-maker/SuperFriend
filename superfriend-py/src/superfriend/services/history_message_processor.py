from __future__ import annotations

import structlog
from dataclasses import dataclass, field
from typing import Any

logger = structlog.get_logger()


class HistoryMessageProcessor:
    def process_history(self, history: list[dict[str, str]]) -> list[dict[str, Any]]:
        if not history:
            return []

        messages = self._convert_to_objects(history)
        messages = self._clean_incomplete_tool_calls(messages)
        return messages

    def _convert_to_objects(self, history: list[dict[str, str]]) -> list[dict[str, Any]]:
        messages: list[dict[str, Any]] = []

        for hist_msg in history:
            role = hist_msg.get("role")
            if not role:
                continue

            msg: dict[str, Any] = {"role": role}

            content = hist_msg.get("content", "")
            if content:
                msg["content"] = content

            tool_calls_str = hist_msg.get("tool_calls")
            if tool_calls_str and role == "assistant":
                import json
                try:
                    if isinstance(tool_calls_str, str):
                        msg["tool_calls"] = json.loads(tool_calls_str)
                    else:
                        msg["tool_calls"] = tool_calls_str
                except (json.JSONDecodeError, TypeError):
                    pass

            tool_call_id = hist_msg.get("tool_call_id")
            if tool_call_id and role == "tool":
                msg["tool_call_id"] = tool_call_id

            name = hist_msg.get("name")
            if name:
                msg["name"] = name

            messages.append(msg)

        return messages

    def _clean_incomplete_tool_calls(self, messages: list[dict[str, Any]]) -> list[dict[str, Any]]:
        cleaned: list[dict[str, Any]] = []
        tool_call_ids: set[str] = set()

        for msg in messages:
            role = msg.get("role", "")

            if role == "assistant" and "tool_calls" in msg:
                tool_calls = msg["tool_calls"]
                valid_calls = []
                for tc in tool_calls:
                    tc_id = tc.get("id", "")
                    if tc_id:
                        tool_call_ids.add(tc_id)
                        valid_calls.append(tc)
                if valid_calls:
                    msg["tool_calls"] = valid_calls
                    cleaned.append(msg)
                elif msg.get("content"):
                    del msg["tool_calls"]
                    cleaned.append(msg)

            elif role == "tool":
                tc_id = msg.get("tool_call_id", "")
                if tc_id and tc_id in tool_call_ids:
                    cleaned.append(msg)
                elif not tc_id:
                    cleaned.append(msg)

            else:
                cleaned.append(msg)

        return cleaned


history_message_processor = HistoryMessageProcessor()