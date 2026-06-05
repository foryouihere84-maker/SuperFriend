from __future__ import annotations

import base64
import structlog
from dataclasses import dataclass, field
from typing import Any
from enum import Enum

logger = structlog.get_logger()


class GeneratorProvider(str, Enum):
    SD = "stable_diffusion"
    ZHIPU = "zhipu"
    OPENAI = "openai"


# ── Shared Models ──────────────────────────────────────────

@dataclass
class GenerationResult:
    success: bool = False
    base64_image: str = ""
    image_url: str = ""
    audio_url: str = ""
    video_url: str = ""
    error_message: str = ""
    generation_time_ms: int = 0
    metadata: dict[str, Any] = field(default_factory=dict)

    @classmethod
    def ok(cls, base64_image: str = "", image_url: str = "", audio_url: str = "", video_url: str = "", **kwargs: Any) -> GenerationResult:
        return cls(success=True, base64_image=base64_image, image_url=image_url, audio_url=audio_url, video_url=video_url, **kwargs)

    @classmethod
    def fail(cls, error: str) -> GenerationResult:
        return cls(success=False, error_message=error)

    def has_content(self) -> bool:
        return bool(self.base64_image or self.image_url or self.audio_url or self.video_url)

    def get_data_url(self) -> str | None:
        if self.base64_image:
            if self.base64_image.startswith("data:image"):
                return self.base64_image
            return f"data:image/png;base64,{self.base64_image}"
        return self.image_url or None


@dataclass
class ImageGenerationOptions:
    negative_prompt: str = ""
    steps: int = 30
    cfg_scale: float = 7.0
    width: int = 512
    height: int = 512
    sampler: str = "Euler a"
    seed: int | None = None
    model: str = ""
    size: str = "1024x1024"

    @classmethod
    def defaults(cls) -> ImageGenerationOptions:
        return cls()


@dataclass
class AudioGenerationOptions:
    voice: str = "female"
    speed: float = 1.0
    response_format: str = "mp3"

    @classmethod
    def defaults(cls) -> AudioGenerationOptions:
        return cls()


@dataclass
class VideoGenerationOptions:
    duration: int = 6
    resolution: str = "720p"
    fps: int = 24

    @classmethod
    def defaults(cls) -> VideoGenerationOptions:
        return cls()


# ── Stable Diffusion Image Generator ──────────────────────

ZHIPU_PRESET_SIZES = {"1024x1024", "768x1344", "864x1152", "1344x768", "1152x864", "1440x720", "720x1440"}


class StableDiffusionImageGenerator:
    def __init__(self, api_url: str = "http://localhost:7860", api_key: str = "", enabled: bool = False):
        self.api_url = api_url
        self.api_key = api_key
        self.enabled = enabled

    async def txt2img(self, prompt: str, options: ImageGenerationOptions | None = None) -> GenerationResult:
        if not self.enabled:
            return GenerationResult.fail("Stable Diffusion 服务未启用")

        import httpx
        opts = options or ImageGenerationOptions.defaults()
        body = {
            "prompt": prompt,
            "negative_prompt": opts.negative_prompt,
            "steps": opts.steps,
            "cfg_scale": opts.cfg_scale,
            "width": opts.width,
            "height": opts.height,
            "sampler_name": opts.sampler,
        }
        if opts.seed is not None:
            body["seed"] = opts.seed

        headers = {"Content-Type": "application/json"}
        if self.api_key:
            headers["Authorization"] = f"Bearer {self.api_key}"

        try:
            import time
            start = time.monotonic()
            async with httpx.AsyncClient(timeout=120) as client:
                resp = await client.post(f"{self.api_url}/sdapi/v1/txt2img", json=body, headers=headers)
            elapsed = int((time.monotonic() - start) * 1000)

            if resp.status_code == 200:
                data = resp.json()
                images = data.get("images", [])
                if images:
                    result = GenerationResult.ok(base64_image=images[0])
                    result.generation_time_ms = elapsed
                    result.metadata = {"provider": "stable_diffusion", "steps": opts.steps}
                    return result
                return GenerationResult.fail("未生成图片")
            return GenerationResult.fail(f"HTTP {resp.status_code}")
        except Exception as e:
            logger.error("sd_txt2img_error", error=str(e))
            return GenerationResult.fail(str(e))

    async def img2img(self, prompt: str, init_image: str, denoising_strength: float = 0.75,
                      options: ImageGenerationOptions | None = None) -> GenerationResult:
        if not self.enabled:
            return GenerationResult.fail("Stable Diffusion 服务未启用")

        import httpx
        opts = options or ImageGenerationOptions.defaults()
        body = {
            "prompt": prompt,
            "init_images": [init_image],
            "denoising_strength": denoising_strength,
            "steps": opts.steps,
            "cfg_scale": opts.cfg_scale,
            "width": opts.width,
            "height": opts.height,
        }

        headers = {"Content-Type": "application/json"}
        if self.api_key:
            headers["Authorization"] = f"Bearer {self.api_key}"

        try:
            async with httpx.AsyncClient(timeout=120) as client:
                resp = await client.post(f"{self.api_url}/sdapi/v1/img2img", json=body, headers=headers)
            if resp.status_code == 200:
                data = resp.json()
                images = data.get("images", [])
                if images:
                    return GenerationResult.ok(base64_image=images[0])
                return GenerationResult.fail("未生成图片")
            return GenerationResult.fail(f"HTTP {resp.status_code}")
        except Exception as e:
            return GenerationResult.fail(str(e))

    async def get_available_models(self) -> list[str]:
        if not self.enabled:
            return []
        import httpx
        try:
            async with httpx.AsyncClient(timeout=10) as client:
                resp = await client.get(f"{self.api_url}/sdapi/v1/sd-models")
            if resp.status_code == 200:
                return [m.get("title", "") for m in resp.json()]
        except Exception:
            pass
        return []


# ── Zhipu CogView Image Generator ────────────────────────

class ZhipuImageGenerator:
    API_URL = "https://open.bigmodel.cn/api/paas/v4/images/generations"

    def __init__(self, api_key: str = "", enabled: bool = False, model: str = "cogview-4", default_size: str = "1024x1024"):
        self.api_key = api_key
        self.enabled = enabled
        self.model = model
        self.default_size = default_size

    async def generate(self, prompt: str, options: ImageGenerationOptions | None = None) -> GenerationResult:
        if not self.enabled:
            return GenerationResult.fail("智谱图片生成服务未启用")
        if not self.api_key:
            return GenerationResult.fail("智谱 API Key 未配置")

        opts = options or ImageGenerationOptions.defaults()
        size = opts.size or self.default_size
        validated = self._validate_size(size)
        if not validated:
            return GenerationResult.fail(f"图片尺寸无效: {size}")

        import httpx
        body = {"model": self.model, "prompt": prompt, "size": validated}
        headers = {"Content-Type": "application/json", "Authorization": f"Bearer {self.api_key}"}

        try:
            import time
            start = time.monotonic()
            async with httpx.AsyncClient(timeout=60) as client:
                resp = await client.post(self.API_URL, json=body, headers=headers)
            elapsed = int((time.monotonic() - start) * 1000)

            if resp.status_code == 200:
                data = resp.json()
                if "error" in data:
                    return GenerationResult.fail(f"API 错误: {data['error'].get('message', '未知')}")
                images = data.get("data", [])
                if images:
                    img = images[0]
                    url = img.get("url", "")
                    b64 = img.get("b64_image", "")
                    result = GenerationResult.ok(base64_image=b64, image_url=url)
                    result.generation_time_ms = elapsed
                    result.metadata = {"provider": "zhipu", "model": self.model}
                    return result
                return GenerationResult.fail("响应中未包含图片数据")
            return GenerationResult.fail(f"HTTP {resp.status_code}")
        except Exception as e:
            logger.error("zhipu_image_error", error=str(e))
            return GenerationResult.fail(str(e))

    def _validate_size(self, size: str) -> str | None:
        if not size:
            return self.default_size
        if size in ZHIPU_PRESET_SIZES:
            return size
        parts = size.lower().split("x")
        if len(parts) != 2:
            return None
        try:
            w, h = int(parts[0].strip()), int(parts[1].strip())
            if not (512 <= w <= 2048 and 512 <= h <= 2048):
                return None
            if w % 16 != 0 or h % 16 != 0:
                return None
            if w * h > (1 << 21):
                return None
            return f"{w}x{h}"
        except ValueError:
            return None


# ── Zhipu Audio Generator ─────────────────────────────────

class ZhipuAudioGenerator:
    API_URL = "https://open.bigmodel.cn/api/paas/v4/audio/speech"

    def __init__(self, api_key: str = "", enabled: bool = False, model: str = "tts-1"):
        self.api_key = api_key
        self.enabled = enabled
        self.model = model

    async def generate(self, text: str, options: AudioGenerationOptions | None = None) -> GenerationResult:
        if not self.enabled:
            return GenerationResult.fail("智谱音频生成服务未启用")
        if not self.api_key:
            return GenerationResult.fail("智谱 API Key 未配置")

        opts = options or AudioGenerationOptions.defaults()
        import httpx
        body = {"model": self.model, "input": text, "voice": opts.voice, "speed": opts.speed, "response_format": opts.response_format}
        headers = {"Content-Type": "application/json", "Authorization": f"Bearer {self.api_key}"}

        try:
            async with httpx.AsyncClient(timeout=60) as client:
                resp = await client.post(self.API_URL, json=body, headers=headers)
            if resp.status_code == 200:
                content_type = resp.headers.get("content-type", "")
                if "audio" in content_type:
                    b64 = base64.b64encode(resp.content).decode()
                    return GenerationResult.ok(audio_url=f"data:audio/{opts.response_format};base64,{b64}")
                data = resp.json()
                url = data.get("url", "")
                return GenerationResult.ok(audio_url=url)
            return GenerationResult.fail(f"HTTP {resp.status_code}")
        except Exception as e:
            return GenerationResult.fail(str(e))


# ── Generic Video Generator ───────────────────────────────

class GenericVideoGenerator:
    def __init__(self, api_key: str = "", enabled: bool = False, model: str = "cogvideox", api_url: str = ""):
        self.api_key = api_key
        self.enabled = enabled
        self.model = model
        self.api_url = api_url or "https://open.bigmodel.cn/api/paas/v4/videos/generations"

    async def generate(self, prompt: str, options: VideoGenerationOptions | None = None) -> GenerationResult:
        if not self.enabled:
            return GenerationResult.fail("视频生成服务未启用")

        opts = options or VideoGenerationOptions.defaults()
        import httpx
        body = {"model": self.model, "prompt": prompt, "duration": opts.duration, "fps": opts.fps}
        headers = {"Content-Type": "application/json", "Authorization": f"Bearer {self.api_key}"}

        try:
            async with httpx.AsyncClient(timeout=300) as client:
                resp = await client.post(self.api_url, json=body, headers=headers)
            if resp.status_code == 200:
                data = resp.json()
                video_url = data.get("data", [{}])[0].get("url", "") if data.get("data") else ""
                return GenerationResult.ok(video_url=video_url)
            return GenerationResult.fail(f"HTTP {resp.status_code}")
        except Exception as e:
            return GenerationResult.fail(str(e))


# ── Generator Factory ─────────────────────────────────────

class GeneratorFactory:
    def __init__(self):
        self._image_generators: dict[str, Any] = {}
        self._audio_generators: dict[str, Any] = {}
        self._video_generators: dict[str, Any] = {}

    def register_image(self, name: str, generator: Any) -> None:
        self._image_generators[name] = generator

    def register_audio(self, name: str, generator: Any) -> None:
        self._audio_generators[name] = generator

    def register_video(self, name: str, generator: Any) -> None:
        self._video_generators[name] = generator

    def get_image_generator(self, name: str = "zhipu") -> Any:
        return self._image_generators.get(name)

    def get_audio_generator(self, name: str = "zhipu") -> Any:
        return self._audio_generators.get(name)

    def get_video_generator(self, name: str = "zhipu") -> Any:
        return self._video_generators.get(name)

    def list_generators(self) -> dict[str, list[str]]:
        return {
            "image": list(self._image_generators.keys()),
            "audio": list(self._audio_generators.keys()),
            "video": list(self._video_generators.keys()),
        }


generator_factory = GeneratorFactory()