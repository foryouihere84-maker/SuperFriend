from __future__ import annotations

import json
import structlog
from dataclasses import dataclass, field
from typing import Any
from enum import Enum

from superfriend.services.llm_client import llm_client

logger = structlog.get_logger()


class EmbeddingProvider(str, Enum):
    OPENAI = "openai"
    ZHIPU = "zhipu"
    LOCAL = "local"


@dataclass
class EmbeddingResult:
    text: str = ""
    vector: list[float] = field(default_factory=list)
    model: str = ""
    dimension: int = 0
    token_count: int = 0


@dataclass
class SearchResult:
    text: str = ""
    score: float = 0.0
    metadata: dict[str, Any] = field(default_factory=dict)
    vector: list[float] = field(default_factory=list)


class EmbeddingService:
    def __init__(self, provider: str = EmbeddingProvider.OPENAI.value,
                 model: str = "text-embedding-3-small", api_url: str = "",
                 api_key: str = "", dimension: int = 1536):
        self.provider = provider
        self.model = model
        self.api_url = api_url
        self.api_key = api_key
        self.dimension = dimension
        self._cache: dict[str, list[float]] = {}

    async def embed(self, text: str) -> EmbeddingResult:
        if not text.strip():
            return EmbeddingResult(text=text, model=self.model, dimension=self.dimension)

        cache_key = f"{self.model}:{text[:200]}"
        if cache_key in self._cache:
            return EmbeddingResult(text=text, vector=self._cache[cache_key], model=self.model, dimension=self.dimension)

        if self.provider == EmbeddingProvider.OPENAI.value:
            result = await self._embed_openai(text)
        elif self.provider == EmbeddingProvider.ZHIPU.value:
            result = await self._embed_zhipu(text)
        else:
            result = await self._embed_local(text)

        if result.vector:
            self._cache[cache_key] = result.vector
            if len(self._cache) > 10000:
                oldest = list(self._cache.keys())[:5000]
                for k in oldest:
                    del self._cache[k]

        return result

    async def embed_batch(self, texts: list[str]) -> list[EmbeddingResult]:
        return [await self.embed(t) for t in texts]

    async def _embed_openai(self, text: str) -> EmbeddingResult:
        import httpx
        url = self.api_url or "https://api.openai.com/v1/embeddings"
        headers = {"Authorization": f"Bearer {self.api_key}", "Content-Type": "application/json"}
        body = {"model": self.model, "input": text}

        try:
            async with httpx.AsyncClient(timeout=30) as client:
                resp = await client.post(url, json=body, headers=headers)
            if resp.status_code == 200:
                data = resp.json()
                vector = data["data"][0]["embedding"]
                return EmbeddingResult(text=text, vector=vector, model=self.model, dimension=len(vector), token_count=data.get("usage", {}).get("total_tokens", 0))
            logger.error("embedding_openai_error", status=resp.status_code)
        except Exception as e:
            logger.error("embedding_openai_exception", error=str(e))
        return EmbeddingResult(text=text, model=self.model, dimension=self.dimension)

    async def _embed_zhipu(self, text: str) -> EmbeddingResult:
        import httpx
        url = self.api_url or "https://open.bigmodel.cn/api/paas/v4/embeddings"
        headers = {"Authorization": f"Bearer {self.api_key}", "Content-Type": "application/json"}
        body = {"model": self.model, "input": text}

        try:
            async with httpx.AsyncClient(timeout=30) as client:
                resp = await client.post(url, json=body, headers=headers)
            if resp.status_code == 200:
                data = resp.json()
                vector = data["data"][0]["embedding"]
                return EmbeddingResult(text=text, vector=vector, model=self.model, dimension=len(vector))
        except Exception as e:
            logger.error("embedding_zhipu_exception", error=str(e))
        return EmbeddingResult(text=text, model=self.model, dimension=self.dimension)

    async def _embed_local(self, text: str) -> EmbeddingResult:
        vector = [hash(text + str(i)) % 1000 / 1000.0 for i in range(self.dimension)]
        return EmbeddingResult(text=text, vector=vector, model="local-hash", dimension=self.dimension)


class VectorStore:
    def __init__(self, dimension: int = 1536):
        self._dimension = dimension
        self._documents: list[dict[str, Any]] = []

    def add(self, text: str, vector: list[float], metadata: dict[str, Any] | None = None) -> int:
        doc_id = len(self._documents)
        self._documents.append({"id": doc_id, "text": text, "vector": vector, "metadata": metadata or {}})
        return doc_id

    def search(self, query_vector: list[float], top_k: int = 5) -> list[SearchResult]:
        if not self._documents:
            return []

        results = []
        for doc in self._documents:
            score = self._cosine_similarity(query_vector, doc["vector"])
            results.append(SearchResult(
                text=doc["text"], score=score, metadata=doc["metadata"], vector=doc["vector"],
            ))

        results.sort(key=lambda r: r.score, reverse=True)
        return results[:top_k]

    def delete(self, doc_id: int) -> bool:
        if 0 <= doc_id < len(self._documents):
            self._documents[doc_id] = {"id": doc_id, "text": "", "vector": [], "metadata": {"deleted": True}}
            return True
        return False

    def count(self) -> int:
        return len([d for d in self._documents if not d.get("metadata", {}).get("deleted")])

    def _cosine_similarity(self, a: list[float], b: list[float]) -> float:
        if not a or not b or len(a) != len(b):
            return 0.0
        dot = sum(x * y for x, y in zip(a, b))
        norm_a = sum(x * x for x in a) ** 0.5
        norm_b = sum(x * x for x in b) ** 0.5
        if norm_a == 0 or norm_b == 0:
            return 0.0
        return dot / (norm_a * norm_b)


embedding_service = EmbeddingService()
vector_store = VectorStore()