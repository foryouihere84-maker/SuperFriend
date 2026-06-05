from __future__ import annotations

import structlog
from dataclasses import dataclass, field
from typing import Any
from enum import Enum

from superfriend.services.file_parse_service import file_parse_service

logger = structlog.get_logger()


class ModalityType(str, Enum):
    TEXT = "text"
    IMAGE = "image"
    AUDIO = "audio"
    VIDEO = "video"
    FILE = "file"


@dataclass
class MultimodalContent:
    modality: ModalityType = ModalityType.TEXT
    text: str = ""
    file_path: str = ""
    url: str = ""
    mime_type: str = ""
    metadata: dict[str, Any] = field(default_factory=dict)


@dataclass
class MultimodalContext:
    contents: list[MultimodalContent] = field(default_factory=list)
    has_image: bool = False
    has_audio: bool = False
    has_video: bool = False
    has_file: bool = False
    parsed_texts: list[str] = field(default_factory=list)
    generated_urls: list[str] = field(default_factory=list)
    errors: list[str] = field(default_factory=list)

    def to_messages(self) -> list[dict[str, Any]]:
        messages = []
        for text in self.parsed_texts:
            messages.append({"type": "text", "text": text})
        for content in self.contents:
            if content.modality == ModalityType.IMAGE and content.url:
                messages.append({"type": "image_url", "image_url": {"url": content.url}})
            elif content.modality == ModalityType.TEXT and content.text:
                messages.append({"type": "text", "text": content.text})
        return messages


class MultimodalProcessService:
    async def process(self, request: dict[str, Any]) -> MultimodalContext:
        ctx = MultimodalContext()

        files = request.get("files", [])
        for file_info in files:
            try:
                file_path = file_info.get("path", "")
                mime_type = file_info.get("mime_type", "")

                if mime_type.startswith("image/"):
                    ctx.has_image = True
                    ctx.contents.append(MultimodalContent(
                        modality=ModalityType.IMAGE,
                        file_path=file_path,
                        mime_type=mime_type,
                        url=file_info.get("url", ""),
                    ))
                elif mime_type.startswith("audio/"):
                    ctx.has_audio = True
                    ctx.contents.append(MultimodalContent(
                        modality=ModalityType.AUDIO,
                        file_path=file_path,
                        mime_type=mime_type,
                    ))
                elif mime_type.startswith("video/"):
                    ctx.has_video = True
                    ctx.contents.append(MultimodalContent(
                        modality=ModalityType.VIDEO,
                        file_path=file_path,
                        mime_type=mime_type,
                    ))
                else:
                    ctx.has_file = True
                    parsed = await file_parse_service.parse_file(file_path)
                    if parsed:
                        ctx.parsed_texts.append(parsed)
                        ctx.contents.append(MultimodalContent(
                            modality=ModalityType.FILE,
                            file_path=file_path,
                            mime_type=mime_type,
                            text=parsed,
                        ))
            except Exception as e:
                logger.error("multimodal_process_error", error=str(e), file=file_info.get("path", ""))
                ctx.errors.append(f"Failed to process file: {e}")

        message = request.get("message", "")
        if message:
            ctx.contents.append(MultimodalContent(
                modality=ModalityType.TEXT,
                text=message,
            ))

        return ctx

    def build_llm_messages(self, context: MultimodalContext, system_prompt: str = "") -> list[dict[str, Any]]:
        messages = []
        if system_prompt:
            messages.append({"role": "system", "content": system_prompt})

        content_parts = context.to_messages()
        if content_parts:
            if len(content_parts) == 1 and content_parts[0].get("type") == "text":
                messages.append({"role": "user", "content": content_parts[0]["text"]})
            else:
                messages.append({"role": "user", "content": content_parts})

        return messages


multimodal_process_service = MultimodalProcessService()