from __future__ import annotations

from enum import Enum
from dataclasses import dataclass, field


class IntentType(str, Enum):
    CHAT = "chat"
    GENERATE_IMAGE = "generate_image"
    GENERATE_AUDIO = "generate_audio"
    GENERATE_VIDEO = "generate_video"
    GENERATE_DOCUMENT = "generate_document"
    PARSE_FILE = "parse_file"
    PARSE_IMAGE = "parse_image"
    PARSE_AUDIO = "parse_audio"
    PARSE_VIDEO = "parse_video"
    MULTIMODAL_CHAT = "multimodal_chat"
    UNKNOWN = "unknown"


@dataclass
class UserIntent:
    intent_type: IntentType = IntentType.CHAT
    confidence: float = 1.0
    file_url: str | None = None
    mime_type: str | None = None
    model_preference: str | None = None

    @classmethod
    def chat(cls) -> UserIntent:
        return cls(intent_type=IntentType.CHAT, confidence=1.0)

    @classmethod
    def multimodal_chat(cls) -> UserIntent:
        return cls(intent_type=IntentType.MULTIMODAL_CHAT, confidence=1.0)

    @classmethod
    def generate_image(cls, confidence: float = 0.9) -> UserIntent:
        return cls(intent_type=IntentType.GENERATE_IMAGE, confidence=confidence)

    @classmethod
    def generate_audio(cls, confidence: float = 0.9) -> UserIntent:
        return cls(intent_type=IntentType.GENERATE_AUDIO, confidence=confidence)

    @classmethod
    def generate_video(cls, confidence: float = 0.9) -> UserIntent:
        return cls(intent_type=IntentType.GENERATE_VIDEO, confidence=confidence)

    @classmethod
    def generate_document(cls, confidence: float = 0.9) -> UserIntent:
        return cls(intent_type=IntentType.GENERATE_DOCUMENT, confidence=confidence)

    @classmethod
    def parse_file(cls, mime_type: str | None = None, file_url: str | None = None) -> UserIntent:
        return cls(intent_type=IntentType.PARSE_FILE, confidence=0.9, mime_type=mime_type, file_url=file_url)

    @property
    def is_generation(self) -> bool:
        return self.intent_type in (
            IntentType.GENERATE_IMAGE,
            IntentType.GENERATE_AUDIO,
            IntentType.GENERATE_VIDEO,
            IntentType.GENERATE_DOCUMENT,
        )

    @property
    def is_parse(self) -> bool:
        return self.intent_type in (
            IntentType.PARSE_FILE,
            IntentType.PARSE_IMAGE,
            IntentType.PARSE_AUDIO,
            IntentType.PARSE_VIDEO,
        )

    @property
    def is_multimodal(self) -> bool:
        return self.intent_type == IntentType.MULTIMODAL_CHAT