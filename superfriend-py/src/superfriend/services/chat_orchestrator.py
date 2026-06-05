from __future__ import annotations

import structlog
from typing import Any

from superfriend.constant.chat_mode import ChatMode
from superfriend.models.intent import UserIntent, IntentType
from superfriend.services.intent_service import user_intent_service
from superfriend.strategy.base import ChatModeStrategy, OnResponseFn
from superfriend.strategy.lite import LiteChatStrategy
from superfriend.strategy.medium import MediumChatStrategy
from superfriend.strategy.complex import ComplexChatStrategy

logger = structlog.get_logger()


class ChatOrchestrator:
    def __init__(self):
        self._strategies: dict[str, ChatModeStrategy] = {
            ChatMode.LITE_TASK: LiteChatStrategy(),
            ChatMode.MEDIUM_TASK: MediumChatStrategy(),
            ChatMode.COMPLEX_TASK: ComplexChatStrategy(),
        }
        logger.info("chat_orchestrator_initialized", strategies=list(self._strategies.keys()))

    async def dispatch(
        self,
        session_id: str,
        message: str,
        model: str,
        history: list[dict[str, Any]],
        on_response: OnResponseFn,
        mode: str = ChatMode.DEFAULT,
        intent: UserIntent | None = None,
        user_id: int | None = None,
        file_urls: list[str] | None = None,
    ) -> None:
        mode = ChatMode.normalize(mode)
        strategy = self._strategies.get(mode)
        if strategy is None:
            self._send_error(on_response, session_id, model, f"Unknown chat mode: {mode}")
            return

        if intent is None:
            intent = user_intent_service.analyze_intent(message, file_urls)

        logger.info("chat_dispatch", session_id=session_id, mode=mode, intent=intent.intent_type.value)

        if intent.is_generation:
            if intent.intent_type == IntentType.GENERATE_DOCUMENT:
                if mode == ChatMode.LITE_TASK:
                    logger.info("doc_generation_upgrade_to_medium")
                    medium = self._strategies.get(ChatMode.MEDIUM_TASK)
                    if medium:
                        await medium.execute_chat(
                            session_id, message, model, history, intent, on_response, user_id
                        )
                        return
                await strategy.execute_chat(
                    session_id, message, model, history, intent, on_response, user_id
                )
                return

            await self._handle_multimodal_generation(
                session_id, message, model, history, intent, on_response, user_id
            )
            return

        if intent.is_parse:
            await self._handle_file_parse_and_chat(
                session_id, message, model, history, intent, strategy, on_response, user_id, file_urls
            )
            return

        if intent.is_multimodal:
            await strategy.execute_multimodal_chat(
                session_id, message, model, history, intent, on_response, user_id
            )
            return

        await strategy.execute_chat(
            session_id, message, model, history, intent, on_response, user_id
        )

    async def _handle_file_parse_and_chat(
        self,
        session_id: str,
        message: str,
        model: str,
        history: list[dict[str, Any]],
        intent: UserIntent,
        strategy: ChatModeStrategy,
        on_response: OnResponseFn,
        user_id: int | None,
        file_urls: list[str] | None,
    ) -> None:
        files = user_intent_service.extract_files(message, file_urls)

        if intent.file_url:
            existing_urls = {f[0] for f in files}
            if intent.file_url not in existing_urls:
                files.insert(0, (intent.file_url, intent.mime_type or "application/octet-stream"))

        if not files:
            self._send_error(on_response, session_id, model, "No files found to parse")
            return

        on_response({
            "type": "thinking",
            "content": f"Processing {len(files)} file(s)...",
            "session_id": session_id,
            "model": model,
            "done": False,
        })

        file_list = []
        for idx, (url, mime) in enumerate(files):
            file_name = url.split("/")[-1].split("?")[0] or f"file_{idx}"
            file_id = f"{idx}_{file_name[:8]}"
            file_list.append({
                "file_id": file_id,
                "file_name": file_name,
                "url": url,
                "mime_type": mime,
            })

        file_prompt = self._build_file_prompt(session_id, file_list)
        enhanced_message = f"{message}\n\n{file_prompt}" if message else file_prompt

        await strategy.execute_file_parsed_chat(
            session_id, enhanced_message, model, history, intent, on_response, user_id
        )

    def _build_file_prompt(self, session_id: str, files: list[dict]) -> str:
        sb = ["\n\n**Available Files:**\n"]
        for f in files:
            sb.append(f"- `{f['file_id']}`: {f['file_name']} ({f['mime_type']})\n")

        sb.append("\n**Available tools:**\n")
        sb.append("- `list_files`: List all available files\n")
        sb.append("- `read_file`: Read file content\n")
        sb.append("- `search_file`: Search within files\n")
        sb.append("- `get_file_info`: Get file details\n")
        sb.append("\n")

        sb.append("**Important:** When using file tools, always pass `session_id`: `")
        sb.append(session_id)
        sb.append("`\n")
        return "".join(sb)

    async def _handle_multimodal_generation(
        self,
        session_id: str,
        message: str,
        model: str,
        history: list[dict[str, Any]],
        intent: UserIntent,
        on_response: OnResponseFn,
        user_id: int | None,
    ) -> None:
        on_response({
            "type": "thinking",
            "content": f"Processing {intent.intent_type.value} request...",
            "session_id": session_id,
            "model": model,
            "done": False,
        })

        from superfriend.services.llm_client import llm_client

        try:
            generation_prompt = self._build_generation_prompt(intent, message)

            async for chunk in llm_client.chat_stream(
                messages=[
                    {"role": "system", "content": generation_prompt},
                    {"role": "user", "content": message},
                ],
                model=model,
                temperature=0.9,
            ):
                on_response({
                    "type": "content",
                    "content": chunk,
                    "session_id": session_id,
                    "model": model,
                    "done": False,
                })

            on_response({
                "type": "done",
                "content": "",
                "session_id": session_id,
                "model": model,
                "done": True,
            })

        except Exception as e:
            logger.error("generation_error", error=str(e))
            self._send_error(on_response, session_id, model, str(e))

    def _build_generation_prompt(self, intent: UserIntent, message: str) -> str:
        prompts = {
            IntentType.GENERATE_IMAGE: "You are an image generation assistant. Describe the image to generate in detail.",
            IntentType.GENERATE_AUDIO: "You are an audio generation assistant. Help create audio content.",
            IntentType.GENERATE_VIDEO: "You are a video generation assistant. Help create video content.",
            IntentType.GENERATE_DOCUMENT: "You are a document generation assistant. Help create documents.",
        }
        return prompts.get(intent.intent_type, "You are a helpful AI assistant.")

    def _send_error(self, on_response: OnResponseFn, session_id: str, model: str, error_msg: str) -> None:
        on_response({
            "type": "error",
            "content": error_msg,
            "session_id": session_id,
            "model": model,
            "done": True,
            "error": error_msg,
        })


chat_orchestrator = ChatOrchestrator()