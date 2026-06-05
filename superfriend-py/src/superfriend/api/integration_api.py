from __future__ import annotations

from fastapi import APIRouter, HTTPException
from pydantic import BaseModel
from typing import Any

from superfriend.services.third_party_integration import (
    orio_search_service, tencent_news_cache_service, oss_service,
)

router = APIRouter()


class SearchRequest(BaseModel):
    query: str
    time_range: str | None = None
    engines: str | None = None


class OssUploadRequest(BaseModel):
    object_key: str
    content_type: str = "application/octet-stream"
    content_base64: str = ""


class OssDeleteRequest(BaseModel):
    object_key: str


class PresignedUrlRequest(BaseModel):
    object_key: str
    expires_seconds: int = 3600


@router.post("/oriosearch/search")
async def search(request: SearchRequest):
    result = await orio_search_service.search(request.query, request.time_range, request.engines)
    if not result:
        return {"success": False, "message": "搜索服务不可用"}
    return {
        "success": True,
        "query": result.query,
        "answer": result.answer,
        "results": [
            {"title": r.title, "url": r.url, "content": r.content, "score": r.score, "engine": r.engine}
            for r in result.results
        ],
        "images": result.images,
        "response_time_ms": result.response_time_ms,
    }


@router.get("/oriosearch/health")
async def oriosearch_health():
    return {"healthy": orio_search_service.is_healthy(), "enabled": orio_search_service.enabled}


@router.get("/news/cached")
async def get_cached_news():
    return tencent_news_cache_service.get_cached_news().__dict__


@router.post("/news/refresh")
async def refresh_news():
    result = await tencent_news_cache_service.fetch_and_cache()
    return result.__dict__


@router.post("/oss/upload")
async def oss_upload(request: OssUploadRequest):
    import base64
    try:
        file_data = base64.b64decode(request.content_base64)
    except Exception:
        raise HTTPException(400, "Invalid base64 content")

    result = await oss_service.upload_file(file_data, request.object_key, request.content_type)
    return {"success": True, "result": result}


@router.post("/oss/download")
async def oss_download(request: OssDeleteRequest):
    data = await oss_service.download_file(request.object_key)
    if data is None:
        raise HTTPException(404, "File not found")
    import base64
    return {"success": True, "content_base64": base64.b64encode(data).decode(), "size": len(data)}


@router.post("/oss/delete")
async def oss_delete(request: OssDeleteRequest):
    ok = await oss_service.delete_file(request.object_key)
    return {"success": ok}


@router.post("/oss/presigned-url")
async def oss_presigned_url(request: PresignedUrlRequest):
    url = await oss_service.generate_presigned_url(request.object_key, request.expires_seconds)
    return {"url": url}


@router.get("/oss/status")
async def oss_status():
    return {"enabled": oss_service.is_enabled()}