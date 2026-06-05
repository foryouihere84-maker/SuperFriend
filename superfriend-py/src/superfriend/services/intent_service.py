from __future__ import annotations

import re
import structlog
from typing import Any

from superfriend.models.intent import UserIntent, IntentType
from superfriend.services.llm_client import llm_client

logger = structlog.get_logger()

GENERATION_PATTERNS: dict[IntentType, list[str]] = {
    IntentType.GENERATE_IMAGE: [
        r"(生成|画|绘制|创建|制作|帮我做).*(图|照片|图片|图像|插画|海报)",
        r"(generate|create|draw|make|design).*(image|picture|photo|illustration|poster)",
        r"(图|照片|图片|图像|插画|海报).*(生成|画|绘制|创建)",
    ],
    IntentType.GENERATE_AUDIO: [
        r"(生成|制作|创建|录制).*(音频|声音|音乐|语音|配音)",
        r"(generate|create|make|produce).*(audio|sound|music|voice|speech)",
        r"(音频|声音|音乐|语音|配音).*(生成|制作|创建)",
        r"文字转语音|文本转语音|tts|text.to.speech",
    ],
    IntentType.GENERATE_VIDEO: [
        r"(生成|制作|创建|剪辑).*(视频|影片|动画|短片)",
        r"(generate|create|make|produce).*(video|animation|clip|film)",
        r"(视频|影片|动画|短片).*(生成|制作|创建)",
    ],
    IntentType.GENERATE_DOCUMENT: [
        r"(生成|创建|写|撰写|制作|帮我做).*(文档|报告|PPT|演示文稿|Word|Excel|PDF|表格|简历|合同|方案|文章|论文)",
        r"(generate|create|write|draft|make).*(document|report|ppt|presentation|word|excel|pdf|table|resume|contract|article|paper)",
        r"(文档|报告|PPT|演示文稿|Word|Excel|PDF|表格|简历|合同).*(生成|创建|写)",
    ],
}

PARSE_PATTERNS: dict[IntentType, list[str]] = {
    IntentType.PARSE_IMAGE: [
        r"(识别|分析|提取|解析|读取|看懂).*(图|照片|图片|图像|截图)",
        r"(图片|照片|图像|截图).*(里|中|上).*(有|是|写).*什么",
        r"ocr.*(识别|提取)",
    ],
    IntentType.PARSE_FILE: [
        r"(分析|解析|读取|查看|总结).*(文件|文档|PDF|Word|Excel|PPT|表格|txt)",
        r"(上传|发|给).*(文件|文档|PDF|Word|Excel|PPT|表格)",
        r"帮我.*(分析|看看|看下|总结|提取|解析).*(文件|文档|PDF|Word|Excel)",
    ],
    IntentType.PARSE_AUDIO: [
        r"(识别|分析|提取|转录|转文字).*(音频|声音|语音|录音)",
        r"(音频|录音).*(里|中).*(说|讲).*什么",
    ],
    IntentType.PARSE_VIDEO: [
        r"(识别|分析|提取|解析).*(视频|影片|录像)",
        r"(视频|影片|录像).*(里|中).*(有|是).*什么",
    ],
}

FILE_EXTENSIONS = {
    IntentType.PARSE_IMAGE: {".jpg", ".jpeg", ".png", ".gif", ".bmp", ".webp", ".svg"},
    IntentType.PARSE_FILE: {".pdf", ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx", ".txt", ".csv", ".md"},
    IntentType.PARSE_AUDIO: {".mp3", ".wav", ".ogg", ".flac", ".aac", ".m4a"},
    IntentType.PARSE_VIDEO: {".mp4", ".avi", ".mov", ".mkv", ".wmv", ".flv"},
}


class UserIntentService:
    def __init__(self):
        self._keyword_cache: dict[str, UserIntent] = {}

    def analyze_intent(self, message: str, file_urls: list[str] | None = None) -> UserIntent:
        message_lower = message.lower().strip() if message else ""

        if not message_lower:
            return UserIntent.chat()

        if file_urls:
            return self._detect_file_intent(message_lower, file_urls)

        intent = self._keyword_match(message_lower)
        if intent.intent_type != IntentType.UNKNOWN:
            return intent

        intent = self._keyword_match_generation(message_lower)
        if intent.intent_type != IntentType.UNKNOWN:
            return intent

        return UserIntent.chat()

    def _keyword_match(self, text: str) -> UserIntent:
        for intent_type, patterns in PARSE_PATTERNS.items():
            for pattern in patterns:
                if re.search(pattern, text):
                    return UserIntent(intent_type=intent_type, confidence=0.85)

        return UserIntent(intent_type=IntentType.UNKNOWN, confidence=0.0)

    def _keyword_match_generation(self, text: str) -> UserIntent:
        for intent_type, patterns in GENERATION_PATTERNS.items():
            for pattern in patterns:
                if re.search(pattern, text):
                    return UserIntent(intent_type=intent_type, confidence=0.85)

        return UserIntent(intent_type=IntentType.UNKNOWN, confidence=0.0)

    def _detect_file_intent(self, text: str, file_urls: list[str]) -> UserIntent:
        for url in file_urls:
            url_lower = url.lower()
            for intent_type, extensions in FILE_EXTENSIONS.items():
                for ext in extensions:
                    if ext in url_lower:
                        if intent_type == IntentType.PARSE_IMAGE and any(
                            kw in text for kw in ["图", "照片", "图片", "image", "photo", "picture"]
                        ):
                            return UserIntent(intent_type=IntentType.PARSE_IMAGE, confidence=0.95, file_url=url)
                        return UserIntent(intent_type=intent_type, confidence=0.95, file_url=url)

        has_image = any(
            url.lower().endswith(ext) for url in file_urls
            for ext in FILE_EXTENSIONS[IntentType.PARSE_IMAGE]
        )
        if has_image:
            return UserIntent(intent_type=IntentType.MULTIMODAL_CHAT, confidence=0.9)

        return UserIntent.parse_file(file_url=file_urls[0])

    def extract_files(self, message: str, file_urls: list[str] | None = None) -> list[tuple[str, str]]:
        files: list[tuple[str, str]] = []
        seen: set[str] = set()

        url_pattern = re.compile(r'(https?://[^\s,，]+)')
        for match in url_pattern.finditer(message):
            url = match.group(1)
            if url.lower().endswith(tuple(
                ext for exts in FILE_EXTENSIONS.values() for ext in exts
            )):
                if url not in seen:
                    mime = self._guess_mime(url)
                    files.append((url, mime))
                    seen.add(url)

        if file_urls:
            for url in file_urls:
                if url not in seen:
                    mime = self._guess_mime(url)
                    files.append((url, mime))
                    seen.add(url)

        return files

    def _guess_mime(self, url: str) -> str:
        url_lower = url.lower()
        mime_map = {
            ".pdf": "application/pdf",
            ".doc": "application/msword",
            ".docx": "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            ".xls": "application/vnd.ms-excel",
            ".xlsx": "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            ".ppt": "application/vnd.ms-powerpoint",
            ".pptx": "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            ".txt": "text/plain",
            ".csv": "text/csv",
            ".md": "text/markdown",
            ".jpg": "image/jpeg",
            ".jpeg": "image/jpeg",
            ".png": "image/png",
            ".gif": "image/gif",
            ".webp": "image/webp",
            ".svg": "image/svg+xml",
            ".mp3": "audio/mpeg",
            ".wav": "audio/wav",
            ".ogg": "audio/ogg",
            ".mp4": "video/mp4",
            ".avi": "video/x-msvideo",
            ".mov": "video/quicktime",
        }
        for ext, mime in mime_map.items():
            if url_lower.endswith(ext):
                return mime
        return "application/octet-stream"

    async def classify_with_llm(self, message: str) -> UserIntent:
        prompt = f"""分析以下用户消息的意图类型。只返回一个意图类型名称。

可用的意图类型:
- CHAT: 普通对话
- GENERATE_IMAGE: 生成图片
- GENERATE_AUDIO: 生成音频
- GENERATE_VIDEO: 生成视频
- GENERATE_DOCUMENT: 生成文档
- PARSE_FILE: 解析文件
- PARSE_IMAGE: 解析图片
- PARSE_AUDIO: 解析音频
- MULTIMODAL_CHAT: 多模态对话

用户消息: {message}

意图类型:"""

        try:
            response = await llm_client.chat(
                messages=[{"role": "user", "content": prompt}],
                temperature=0.1,
                max_tokens=50,
            )
            content = await llm_client.extract_content(response)
            content = content.strip().upper()

            for intent_type in IntentType:
                if intent_type.value.upper() in content:
                    return UserIntent(intent_type=intent_type, confidence=0.9)

            return UserIntent.chat()
        except Exception as e:
            logger.warning("llm_intent_classify_failed", error=str(e))
            return UserIntent.chat()


user_intent_service = UserIntentService()