from __future__ import annotations

from fastapi import APIRouter, HTTPException
from pydantic import BaseModel
from typing import Any

from superfriend.services.mcp_service_launcher import mcp_service_launcher, McpServerConfig
from superfriend.services.compression_config_service import smart_compression_strategy, dynamic_config_manager, CompressionStrategy
from superfriend.services.embedding_service import embedding_service, vector_store
from superfriend.services.memory_knowledge_service import memory_lifecycle_service, node_enrichment_service
from superfriend.services.skill_advanced_service import skill_recommendation_service, skill_package_parser_service
from superfriend.services.file_lifecycle_service import file_lifecycle_manager, session_file_index_service
from superfriend.services.retry_dependency_service import retry_strategy, dependency_manager, RetryConfig
from superfriend.services.intent_history_service import llm_intent_classifier, ai_process_history_service

router = APIRouter()


# ── MCP Service Launcher ──

class McpStartRequest(BaseModel):
    server_name: str
    command: str
    args: list[str] = []
    env: dict[str, str] = {}
    cwd: str = ""
    description: str = ""


@router.post("/mcp/prepare")
async def prepare_mcp_config(request: McpStartRequest):
    config = McpServerConfig(command=request.command, args=request.args, env=request.env, cwd=request.cwd, description=request.description)
    prepared = mcp_service_launcher.prepare_config(request.server_name, config)
    return {"command": prepared.command, "args": prepared.args, "env": prepared.env}


@router.post("/mcp/start")
async def start_mcp_server(request: McpStartRequest):
    config = McpServerConfig(command=request.command, args=request.args, env=request.env, cwd=request.cwd, description=request.description)
    info = await mcp_service_launcher.start_server(request.server_name, config)
    return {"server_name": info.server_name, "status": info.status, "pid": info.pid}


@router.post("/mcp/stop/{server_name}")
async def stop_mcp_server(server_name: str):
    ok = await mcp_service_launcher.stop_server(server_name)
    return {"success": ok}


@router.post("/mcp/restart/{server_name}")
async def restart_mcp_server(server_name: str):
    info = await mcp_service_launcher.restart_server(server_name)
    if info:
        return {"server_name": info.server_name, "status": info.status, "pid": info.pid}
    raise HTTPException(404, f"Server {server_name} not found")


@router.get("/mcp/status")
async def list_mcp_servers():
    return {"servers": mcp_service_launcher.list_servers()}


@router.get("/mcp/status/{server_name}")
async def get_mcp_status(server_name: str):
    return mcp_service_launcher.get_server_status(server_name)


# ── Compression & Config ──

class CompressRequest(BaseModel):
    messages: list[dict[str, Any]]
    strategy: str = "hybrid"
    max_context_tokens: int = 8000


@router.post("/compression/compress")
async def compress_messages(request: CompressRequest):
    smart_compression_strategy.max_context_tokens = request.max_context_tokens
    result_msgs, result = smart_compression_strategy.compress(request.messages, CompressionStrategy(request.strategy))
    return {
        "messages": result_msgs,
        "stats": {
            "original_tokens": result.original_token_count,
            "compressed_tokens": result.compressed_token_count,
            "ratio": result.compression_ratio,
            "strategy": result.strategy_used,
            "removed": result.messages_removed,
            "summarized": result.messages_summarized,
        },
    }


@router.get("/config/{key:path}")
async def get_config(key: str):
    value = dynamic_config_manager.get(key)
    return {"key": key, "value": value, "version": dynamic_config_manager.get_version(key)}


@router.post("/config/{key:path}")
async def set_config(key: str, value: Any = None):
    dynamic_config_manager.set(key, value)
    return {"key": key, "version": dynamic_config_manager.get_version(key)}


@router.delete("/config/{key:path}")
async def delete_config(key: str):
    ok = dynamic_config_manager.delete(key)
    return {"success": ok}


@router.get("/config/list")
async def list_configs(prefix: str = ""):
    return {"keys": dynamic_config_manager.list_keys(prefix)}


# ── Embedding & Vector ──

class EmbedRequest(BaseModel):
    text: str


class VectorSearchRequest(BaseModel):
    query: str
    top_k: int = 5


class VectorAddRequest(BaseModel):
    text: str
    metadata: dict[str, Any] = {}


@router.post("/embedding/embed")
async def embed_text(request: EmbedRequest):
    result = await embedding_service.embed(request.text)
    return {"dimension": result.dimension, "model": result.model, "token_count": result.token_count, "vector_preview": result.vector[:10]}


@router.post("/vector/add")
async def add_vector(request: VectorAddRequest):
    emb = await embedding_service.embed(request.text)
    if not emb.vector:
        raise HTTPException(500, "Embedding failed")
    doc_id = vector_store.add(request.text, emb.vector, request.metadata)
    return {"doc_id": doc_id}


@router.post("/vector/search")
async def search_vectors(request: VectorSearchRequest):
    emb = await embedding_service.embed(request.query)
    if not emb.vector:
        raise HTTPException(500, "Embedding failed")
    results = vector_store.search(emb.vector, request.top_k)
    return {"results": [{"text": r.text, "score": r.score, "metadata": r.metadata} for r in results]}


@router.get("/vector/count")
async def vector_count():
    return {"count": vector_store.count()}


# ── Memory Lifecycle ──

@router.get("/memory/lifecycle/events")
async def get_memory_events(memory_id: int | None = None, limit: int = 50):
    return {"events": memory_lifecycle_service.get_events(memory_id, limit)}


# ── Node Enrichment ──

class EnrichNodeRequest(BaseModel):
    node: dict[str, Any]
    model: str = "deepseek-chat"


@router.post("/knowledge/enrich")
async def enrich_node(request: EnrichNodeRequest):
    enriched = await node_enrichment_service.enrich_node(request.node, request.model)
    return {"enriched_node": enriched}


# ── Skill Recommendation ──

class SkillRecommendRequest(BaseModel):
    user_message: str
    top_k: int = 5


@router.post("/skill/recommend")
async def recommend_skills(request: SkillRecommendRequest):
    recs = await skill_recommendation_service.recommend(request.user_message, top_k=request.top_k)
    return {"recommendations": [{"skill_id": r.skill_id, "skill_name": r.skill_name, "score": r.score, "reason": r.reason, "match_type": r.match_type} for r in recs]}


class SkillIndexRequest(BaseModel):
    skill_id: str
    name: str
    description: str
    tags: list[str] = []


@router.post("/skill/index")
async def index_skill(request: SkillIndexRequest):
    await skill_recommendation_service.index_skill(request.skill_id, request.name, request.description, request.tags)
    return {"success": True}


# ── Skill Package Parser ──

class ParsePackageRequest(BaseModel):
    package: dict[str, Any]


@router.post("/skill/parse-package")
async def parse_skill_package(request: ParsePackageRequest):
    result = skill_package_parser_service.parse_package(request.package)
    errors = skill_package_parser_service.validate_package(result)
    return {
        "name": result.name, "version": result.version, "description": result.description,
        "skills": result.skills, "dependencies": result.dependencies,
        "errors": errors,
    }


# ── File Lifecycle ──

@router.get("/files/stats")
async def file_stats():
    return file_lifecycle_manager.get_stats()


@router.post("/files/cleanup")
async def cleanup_files():
    count = file_lifecycle_manager.cleanup_expired()
    return {"cleaned": count}


@router.get("/files/session/{session_id}")
async def session_file_summary(session_id: str):
    return session_file_index_service.get_file_summary(session_id)


@router.get("/files/session/{session_id}/search")
async def search_session_files(session_id: str, query: str = "", mime_type: str = ""):
    files = session_file_index_service.search_files(session_id, query, mime_type)
    return {"files": [{"file_id": f.file_id, "original_name": f.original_name, "size": f.file_size, "mime_type": f.mime_type} for f in files]}


# ── Intent Classification ──

class ClassifyIntentRequest(BaseModel):
    message: str
    context: dict[str, Any] | None = None


@router.post("/intent/classify")
async def classify_intent(request: ClassifyIntentRequest):
    result = await llm_intent_classifier.classify(request.message, request.context)
    return {
        "category": result.category, "confidence": result.confidence,
        "sub_intents": result.sub_intents, "entities": result.entities,
        "requires_tools": result.requires_tools, "suggested_tools": result.suggested_tools,
        "complexity": result.complexity,
    }


# ── Process History ──

@router.get("/history/{session_id}")
async def get_process_history(session_id: str, step: str | None = None, limit: int = 50):
    return {"entries": ai_process_history_service.get_history(session_id, step, limit)}


@router.get("/history/{session_id}/summary")
async def get_process_summary(session_id: str):
    return ai_process_history_service.get_session_summary(session_id)


# ── Dependency Manager ──

class AddDependencyRequest(BaseModel):
    name: str
    dependencies: list[str] = []


@router.post("/dependency/add")
async def add_dependency(request: AddDependencyRequest):
    dependency_manager.add_node(request.name, request.dependencies)
    return {"success": True}


@router.get("/dependency/resolve")
async def resolve_dependencies():
    result = dependency_manager.resolve_order()
    return {"execution_order": result.execution_order, "cycles": result.cycles, "independent_groups": result.independent_groups}


@router.get("/dependency/graph")
async def dependency_graph():
    result = dependency_manager.resolve_order()
    return {
        "nodes": [{"name": n.name, "dependencies": n.dependencies} for n in dependency_manager._nodes.values()],
        "execution_order": result.execution_order,
    }