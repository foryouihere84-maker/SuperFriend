from __future__ import annotations

import structlog
from fastapi import APIRouter, HTTPException, Query
from pydantic import BaseModel
from typing import Any

from superfriend.services.knowledge_graph_service import (
    knowledge_graph_service, KnowledgeNode,
)
from superfriend.services.memory_palace_service import (
    memory_palace_service, MemoryPalace, MemoryType,
)

router = APIRouter(prefix="/knowledge-graph", tags=["knowledge-graph"])
logger = structlog.get_logger()


class CreateNodeRequest(BaseModel):
    user_id: int
    name: str
    node_type: str = "concept"
    content: str = ""
    session_id: str = ""
    tags: list[str] | None = None
    importance: float = 0.5


class CreateRelationRequest(BaseModel):
    source_id: int
    target_id: int
    relation_type: str = "related_to"
    strength: float = 0.5
    description: str = ""


class CreateMemoryRequest(BaseModel):
    user_id: int
    title: str
    content: str
    memory_type: str = "fact"
    session_id: str = ""
    tags: list[str] | None = None
    trigger_keywords: list[str] | None = None
    importance: float = 0.5


# ==================== Knowledge Graph ====================

@router.get("/nodes")
async def get_all_nodes(user_id: int = Query(...)):
    nodes = knowledge_graph_service.get_user_nodes(user_id)
    return {"success": True, "nodes": [_node_to_dict(n) for n in nodes]}


@router.get("/nodes/type/{node_type}")
async def get_nodes_by_type(user_id: int = Query(...), node_type: str = ""):
    nodes = knowledge_graph_service.get_user_nodes(user_id, node_type=node_type)
    return {"success": True, "nodes": [_node_to_dict(n) for n in nodes]}


@router.get("/nodes/session/{session_id}")
async def get_nodes_by_session(user_id: int = Query(...), session_id: str = ""):
    nodes = knowledge_graph_service.get_session_nodes(user_id, session_id)
    return {"success": True, "nodes": [_node_to_dict(n) for n in nodes]}


@router.post("/nodes")
async def create_node(request: CreateNodeRequest):
    node = knowledge_graph_service.add_node(
        user_id=request.user_id, name=request.name,
        node_type=request.node_type, content=request.content,
        session_id=request.session_id, tags=request.tags,
        importance=request.importance,
    )
    return {"success": True, "node": _node_to_dict(node)}


@router.get("/nodes/{node_id}")
async def get_node(node_id: int):
    node = knowledge_graph_service.get_node(node_id)
    if not node:
        raise HTTPException(status_code=404, detail="Node not found")
    return {"success": True, "node": _node_to_dict(node)}


@router.put("/nodes/{node_id}")
async def update_node(node_id: int, name: str | None = None, content: str | None = None,
                       tags: list[str] | None = None, importance: float | None = None):
    kwargs = {}
    if name is not None: kwargs["name"] = name
    if content is not None: kwargs["content"] = content
    if tags is not None: kwargs["tags"] = tags
    if importance is not None: kwargs["importance"] = importance
    node = knowledge_graph_service.update_node(node_id, **kwargs)
    if not node:
        raise HTTPException(status_code=404, detail="Node not found")
    return {"success": True, "node": _node_to_dict(node)}


@router.delete("/nodes/{node_id}")
async def delete_node(node_id: int):
    success = knowledge_graph_service.delete_node(node_id)
    return {"success": success}


@router.post("/relations")
async def create_relation(request: CreateRelationRequest):
    rel = knowledge_graph_service.add_relation(
        source_id=request.source_id, target_id=request.target_id,
        relation_type=request.relation_type, strength=request.strength,
        description=request.description,
    )
    if not rel:
        raise HTTPException(status_code=400, detail="Invalid source or target node")
    return {"success": True, "relation": {"id": rel.id, "source_id": rel.source_id, "target_id": rel.target_id, "type": rel.relation_type}}


@router.get("/context")
async def get_graph_context(user_id: int = Query(...), query: str = ""):
    ctx = knowledge_graph_service.get_graph_context(user_id, query=query)
    return {
        "success": True,
        "nodes": [_node_to_dict(n) for n in ctx.nodes],
        "relations": len(ctx.relations),
        "context_text": ctx.context_text,
    }


@router.get("/search")
async def search_nodes(user_id: int = Query(...), keyword: str = Query(...), limit: int = 10):
    nodes = knowledge_graph_service.search_nodes(user_id, keyword, limit=limit)
    return {"success": True, "nodes": [_node_to_dict(n) for n in nodes]}


# ==================== Memory Palace ====================

@router.post("/memories")
async def create_memory(request: CreateMemoryRequest):
    memory = memory_palace_service.create_memory(
        user_id=request.user_id, title=request.title,
        content=request.content, memory_type=MemoryType(request.memory_type),
        session_id=request.session_id, tags=request.tags,
        trigger_keywords=request.trigger_keywords, importance=request.importance,
    )
    return {"success": True, "memory": _memory_to_dict(memory)}


@router.get("/memories")
async def get_user_memories(user_id: int = Query(...), memory_type: str | None = None):
    mt = MemoryType(memory_type) if memory_type else None
    memories = memory_palace_service.get_user_memories(user_id, memory_type=mt)
    return {"success": True, "memories": [_memory_to_dict(m) for m in memories]}


@router.get("/memories/search")
async def search_memories(user_id: int = Query(...), query: str = Query(...), limit: int = 10):
    memories = memory_palace_service.search_memories(user_id, query, limit=limit)
    return {"success": True, "memories": [_memory_to_dict(m) for m in memories]}


@router.get("/memories/{memory_id}")
async def get_memory(memory_id: int):
    memory = memory_palace_service.get_memory(memory_id)
    if not memory:
        raise HTTPException(status_code=404, detail="Memory not found")
    return {"success": True, "memory": _memory_to_dict(memory)}


@router.delete("/memories/{memory_id}")
async def delete_memory(memory_id: int):
    success = memory_palace_service.delete_memory(memory_id)
    return {"success": success}


def _node_to_dict(node: KnowledgeNode) -> dict[str, Any]:
    return {
        "id": node.id, "userId": node.user_id, "sessionId": node.session_id,
        "name": node.name, "type": node.node_type, "content": node.content,
        "summary": node.summary, "tags": node.tags, "importance": node.importance,
    }


def _memory_to_dict(memory: MemoryPalace) -> dict[str, Any]:
    return {
        "id": memory.id, "userId": memory.user_id, "sessionId": memory.session_id,
        "title": memory.title, "content": memory.content,
        "type": memory.memory_type.value, "strength": memory.strength.value,
        "tags": memory.tags, "triggerKeywords": memory.trigger_keywords,
        "importance": memory.importance, "accessCount": memory.access_count,
    }