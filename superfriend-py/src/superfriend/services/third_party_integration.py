from __future__ import annotations

import time
import structlog
from dataclasses import dataclass, field
from typing import Any
from datetime import datetime

logger = structlog.get_logger()


@dataclass
class NewsItem:
    title: str = ""
    url: str = ""
    source: str = ""
    publish_time: str = ""
    summary: str = ""
    rank: int = 0
    hot_index: int = 0
    category: str = ""


@dataclass
class NewsResponse:
    success: bool = True
    message: str = ""
    news: list[NewsItem] = field(default_factory=list)
    update_time: str = ""
    count: int = 0


@dataclass
class SearchResultItem:
    title: str = ""
    url: str = ""
    content: str = ""
    score: float = 0.0
    raw_content: str = ""
    engine: str = ""


@dataclass
class SearchResult:
    query: str = ""
    answer: str = ""
    results: list[SearchResultItem] = field(default_factory=list)
    images: list[str] = field(default_factory=list)
    response_time_ms: int = 0


class OrioSearchService:
    """OrioSearch 搜索服务 - 自托管搜索聚合器，兼容 Tavily API"""

    def __init__(self, base_url: str = "http://localhost:8000", enabled: bool = True,
                 timeout_ms: int = 15000, max_results: int = 5,
                 include_answer: bool = True, rerank_results: bool = False,
                 include_images: bool = True):
        self.base_url = base_url
        self.enabled = enabled
        self.timeout_ms = timeout_ms
        self.max_results = max_results
        self.include_answer = include_answer
        self.rerank_results = rerank_results
        self.include_images = include_images

    def is_healthy(self) -> bool:
        if not self.enabled:
            return False
        try:
            import httpx
            import asyncio
            async def _check():
                async with httpx.AsyncClient(timeout=5) as client:
                    resp = await client.get(f"{self.base_url}/health")
                    return resp.status_code == 200
            try:
                loop = asyncio.get_event_loop()
                if loop.is_running():
                    return True
                return loop.run_until_complete(_check())
            except RuntimeError:
                return True
        except Exception:
            return False

    async def search(self, query: str, time_range: str | None = None, engines: str | None = None) -> SearchResult | None:
        if not self.enabled:
            logger.debug("oriosearch_disabled")
            return None
        if not query or not query.strip():
            return None

        import httpx
        body: dict[str, Any] = {
            "query": query,
            "max_results": self.max_results,
            "include_answer": self.include_answer,
            "rerank_results": self.rerank_results,
            "include_images": self.include_images,
        }
        if time_range:
            body["time_range"] = time_range
        if engines:
            body["engines"] = engines

        start = time.monotonic()
        try:
            async with httpx.AsyncClient(timeout=self.timeout_ms / 1000) as client:
                resp = await client.post(f"{self.base_url}/search", json=body)
            elapsed = int((time.monotonic() - start) * 1000)

            if resp.status_code == 200:
                data = resp.json()
                results = []
                for item in data.get("results", []):
                    results.append(SearchResultItem(
                        title=item.get("title", ""),
                        url=item.get("url", ""),
                        content=item.get("content", ""),
                        score=item.get("score", 0.0),
                        raw_content=item.get("raw_content", ""),
                        engine=item.get("engine", ""),
                    ))
                return SearchResult(
                    query=query,
                    answer=data.get("answer", ""),
                    results=results,
                    images=data.get("images", []),
                    response_time_ms=elapsed,
                )
            logger.warning("oriosearch_error", status=resp.status_code)
            return None
        except Exception as e:
            logger.error("oriosearch_exception", error=str(e))
            return None

    async def search_news(self, query: str, time_range: str = "day", engines: str = "") -> SearchResult | None:
        return await self.search(query, time_range=time_range, engines=engines or None)


class TencentNewsCacheService:
    def __init__(self, orio_search: OrioSearchService | None = None,
                 time_range: str = "day",
                 queries: str = "AI 人工智能,科技新闻,互联网热点",
                 engines: str = ""):
        self._orio_search = orio_search
        self._time_range = time_range
        self._queries = [q.strip() for q in queries.split(",") if q.strip()]
        self._engines = engines
        self._cached_news: list[NewsItem] = []
        self._last_update_time: datetime | None = None
        self._max_news = 20

    async def fetch_and_cache(self) -> NewsResponse:
        if not self._orio_search or not self._orio_search.is_healthy():
            return NewsResponse(success=False, message="OrioSearch 服务不可用")

        all_news: list[NewsItem] = []
        global_rank = 1

        for query in self._queries:
            if len(all_news) >= self._max_news:
                break
            result = await self._orio_search.search_news(query, self._time_range, self._engines)
            if result and result.results:
                for item in result.results:
                    if len(all_news) >= self._max_news:
                        break
                    all_news.append(NewsItem(
                        title=item.title, url=item.url, source="OrioSearch",
                        publish_time=datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
                        summary=item.content[:200], rank=global_rank,
                        category=query,
                    ))
                    global_rank += 1

        self._cached_news = all_news
        self._last_update_time = datetime.now()

        return NewsResponse(
            success=True, news=all_news,
            update_time=self._last_update_time.strftime("%Y-%m-%d %H:%M:%S"),
            count=len(all_news),
        )

    def get_cached_news(self) -> NewsResponse:
        return NewsResponse(
            success=bool(self._cached_news),
            news=self._cached_news,
            update_time=self._last_update_time.strftime("%Y-%m-%d %H:%M:%S") if self._last_update_time else "",
            count=len(self._cached_news),
        )


class OssService:
    """对象存储服务 - 支持阿里云 OSS 和本地存储"""

    def __init__(self, endpoint: str = "", access_key_id: str = "", access_key_secret: str = "",
                 bucket_name: str = "", enabled: bool = False, local_backup_dir: str = "/tmp/superfriend/oss"):
        self.endpoint = endpoint
        self.access_key_id = access_key_id
        self.access_key_secret = access_key_secret
        self.bucket_name = bucket_name
        self.enabled = enabled
        self._local_backup_dir = local_backup_dir
        self._local_files: dict[str, dict[str, Any]] = {}

    def is_enabled(self) -> bool:
        return self.enabled

    async def upload_file(self, file_data: bytes, object_key: str, content_type: str = "application/octet-stream") -> dict[str, Any]:
        if not self.enabled:
            return await self._upload_local(file_data, object_key, content_type)

        try:
            import httpx
            # 简化的 OSS 上传 - 实际生产环境应使用 oss2 SDK
            logger.info("oss_upload", key=object_key, size=len(file_data))
            return {"object_key": object_key, "url": f"https://{self.bucket_name}.{self.endpoint}/{object_key}"}
        except Exception as e:
            logger.error("oss_upload_error", error=str(e))
            return await self._upload_local(file_data, object_key, content_type)

    async def _upload_local(self, file_data: bytes, object_key: str, content_type: str) -> dict[str, Any]:
        import os
        os.makedirs(self._local_backup_dir, exist_ok=True)
        local_path = os.path.join(self._local_backup_dir, object_key.replace("/", "_"))
        with open(local_path, "wb") as f:
            f.write(file_data)
        self._local_files[object_key] = {"path": local_path, "size": len(file_data), "content_type": content_type}
        return {"object_key": object_key, "url": f"file://{local_path}", "storage": "local"}

    async def download_file(self, object_key: str) -> bytes | None:
        if object_key in self._local_files:
            path = self._local_files[object_key]["path"]
            with open(path, "rb") as f:
                return f.read()
        return None

    async def delete_file(self, object_key: str) -> bool:
        if object_key in self._local_files:
            import os
            os.remove(self._local_files[object_key]["path"])
            del self._local_files[object_key]
            return True
        return False

    async def generate_presigned_url(self, object_key: str, expires_seconds: int = 3600) -> str:
        if object_key in self._local_files:
            return f"file://{self._local_files[object_key]['path']}"
        return f"https://{self.bucket_name}.{self.endpoint}/{object_key}"


# 全局实例
orio_search_service = OrioSearchService()
tencent_news_cache_service = TencentNewsCacheService(orio_search_service)
oss_service = OssService()