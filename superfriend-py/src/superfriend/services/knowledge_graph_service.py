from __future__ import annotations

import structlog
from dataclasses import dataclass, field
from typing import Any

logger = structlog.get_logger()


@dataclass
class KnowledgeNode:
    id: int | None = None
    user_id: int | None = None
    session_id: str = ""
    name: str = ""
    node_type: str = "concept"
    content: str = ""
    summary: str = ""
    tags: list[str] = field(default_factory=list)
    importance: float = 0.5
    embedding: list[float] | None = None
    created_at: str = ""
    updated_at: str = ""


@dataclass
class KnowledgeRelation:
    id: int | None = None
    source_id: int | None = None
    target_id: int | None = None
    relation_type: str = "related_to"
    strength: float = 0.5
    description: str = ""


@dataclass
class GraphContext:
    nodes: list[KnowledgeNode] = field(default_factory=list)
    relations: list[KnowledgeRelation] = field(default_factory=list)
    context_text: str = ""


class KnowledgeGraphService:
    def __init__(self):
        self._nodes: dict[int, KnowledgeNode] = {}
        self._relations: dict[int, KnowledgeRelation] = {}
        self._user_nodes: dict[int, list[int]] = {}
        self._next_node_id: int = 1
        self._next_relation_id: int = 1

    def add_node(self, user_id: int, name: str, node_type: str = "concept",
                 content: str = "", session_id: str = "", tags: list[str] | None = None,
                 importance: float = 0.5) -> KnowledgeNode:
        node_id = self._next_node_id
        self._next_node_id += 1

        node = KnowledgeNode(
            id=node_id, user_id=user_id, session_id=session_id,
            name=name, node_type=node_type, content=content,
            tags=tags or [], importance=importance,
        )
        self._nodes[node_id] = node
        self._user_nodes.setdefault(user_id, []).append(node_id)
        return node

    def get_node(self, node_id: int) -> KnowledgeNode | None:
        return self._nodes.get(node_id)

    def get_user_nodes(self, user_id: int, node_type: str | None = None) -> list[KnowledgeNode]:
        node_ids = self._user_nodes.get(user_id, [])
        nodes = [self._nodes[nid] for nid in node_ids if nid in self._nodes]
        if node_type:
            nodes = [n for n in nodes if n.node_type == node_type]
        return nodes

    def get_session_nodes(self, user_id: int, session_id: str) -> list[KnowledgeNode]:
        node_ids = self._user_nodes.get(user_id, [])
        return [
            self._nodes[nid] for nid in node_ids
            if nid in self._nodes and self._nodes[nid].session_id == session_id
        ]

    def update_node(self, node_id: int, **kwargs: Any) -> KnowledgeNode | None:
        node = self._nodes.get(node_id)
        if not node:
            return None
        for k, v in kwargs.items():
            if hasattr(node, k):
                setattr(node, k, v)
        return node

    def delete_node(self, node_id: int) -> bool:
        if node_id not in self._nodes:
            return False
        node = self._nodes.pop(node_id)
        if node.user_id and node.user_id in self._user_nodes:
            self._user_nodes[node.user_id] = [
                nid for nid in self._user_nodes[node.user_id] if nid != node_id
            ]
        self._relations = {
            rid: r for rid, r in self._relations.items()
            if r.source_id != node_id and r.target_id != node_id
        }
        return True

    def add_relation(self, source_id: int, target_id: int, relation_type: str = "related_to",
                     strength: float = 0.5, description: str = "") -> KnowledgeRelation | None:
        if source_id not in self._nodes or target_id not in self._nodes:
            return None
        rel_id = self._next_relation_id
        self._next_relation_id += 1
        rel = KnowledgeRelation(
            id=rel_id, source_id=source_id, target_id=target_id,
            relation_type=relation_type, strength=strength, description=description,
        )
        self._relations[rel_id] = rel
        return rel

    def get_node_relations(self, node_id: int) -> list[KnowledgeRelation]:
        return [
            r for r in self._relations.values()
            if r.source_id == node_id or r.target_id == node_id
        ]

    def get_graph_context(self, user_id: int, query: str = "", max_nodes: int = 20, max_relations: int = 30) -> GraphContext:
        all_nodes = self.get_user_nodes(user_id)
        if query:
            query_lower = query.lower()
            scored_nodes = []
            for node in all_nodes:
                score = 0.0
                if query_lower in node.name.lower():
                    score += 3.0
                if query_lower in node.content.lower():
                    score += 2.0
                if any(query_lower in tag.lower() for tag in node.tags):
                    score += 1.5
                score += node.importance * 0.5
                scored_nodes.append((node, score))
            scored_nodes.sort(key=lambda x: x[1], reverse=True)
            selected_nodes = [n for n, _ in scored_nodes[:max_nodes]]
        else:
            selected_nodes = sorted(all_nodes, key=lambda n: n.importance, reverse=True)[:max_nodes]

        selected_ids = {n.id for n in selected_nodes if n.id}
        selected_relations = [
            r for r in self._relations.values()
            if r.source_id in selected_ids and r.target_id in selected_ids
        ][:max_relations]

        context_parts = []
        for node in selected_nodes:
            context_parts.append(f"[{node.node_type}] {node.name}: {node.summary or node.content[:200]}")
        for rel in selected_relations:
            src = self._nodes.get(rel.source_id)
            tgt = self._nodes.get(rel.target_id)
            if src and tgt:
                context_parts.append(f"{src.name} --[{rel.relation_type}]--> {tgt.name}")

        return GraphContext(
            nodes=selected_nodes,
            relations=selected_relations,
            context_text="\n".join(context_parts),
        )

    def search_nodes(self, user_id: int, keyword: str, limit: int = 10) -> list[KnowledgeNode]:
        all_nodes = self.get_user_nodes(user_id)
        keyword_lower = keyword.lower()
        matches = [
            n for n in all_nodes
            if keyword_lower in n.name.lower()
            or keyword_lower in n.content.lower()
            or any(keyword_lower in tag.lower() for tag in n.tags)
        ]
        return sorted(matches, key=lambda n: n.importance, reverse=True)[:limit]


knowledge_graph_service = KnowledgeGraphService()