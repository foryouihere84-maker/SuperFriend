from __future__ import annotations

import asyncio
import structlog
from dataclasses import dataclass, field
from typing import Any
from enum import Enum

logger = structlog.get_logger()


class RetryPolicy(str, Enum):
    FIXED = "fixed"
    EXPONENTIAL = "exponential"
    LINEAR = "linear"


@dataclass
class RetryConfig:
    max_retries: int = 3
    initial_delay_ms: int = 1000
    max_delay_ms: int = 30000
    backoff_multiplier: float = 2.0
    policy: str = RetryPolicy.EXPONENTIAL.value
    retryable_errors: list[str] = field(default_factory=lambda: ["timeout", "rate_limit", "connection_error"])


@dataclass
class RetryResult:
    success: bool = False
    attempts: int = 0
    last_error: str = ""
    total_delay_ms: int = 0
    result: Any = None


class RetryStrategy:
    def __init__(self, config: RetryConfig | None = None):
        self._config = config or RetryConfig()

    async def execute(self, func: Any, *args: Any, **kwargs: Any) -> RetryResult:
        result = RetryResult()
        delay_ms = self._config.initial_delay_ms

        for attempt in range(1, self._config.max_retries + 1):
            result.attempts = attempt
            try:
                if asyncio.iscoroutinefunction(func):
                    ret = await func(*args, **kwargs)
                else:
                    ret = func(*args, **kwargs)
                result.success = True
                result.result = ret
                return result
            except Exception as e:
                error_name = type(e).__name__.lower()
                error_msg = str(e).lower()
                result.last_error = str(e)

                is_retryable = any(
                    err in error_name or err in error_msg
                    for err in self._config.retryable_errors
                )

                if not is_retryable or attempt >= self._config.max_retries:
                    logger.warning("retry_exhausted", attempts=attempt, error=str(e))
                    return result

                logger.info("retry_attempt", attempt=attempt, delay_ms=delay_ms, error=str(e))
                await asyncio.sleep(delay_ms / 1000)
                result.total_delay_ms += delay_ms
                delay_ms = self._calculate_next_delay(delay_ms)

        return result

    def _calculate_next_delay(self, current_delay_ms: int) -> int:
        if self._config.policy == RetryPolicy.EXPONENTIAL.value:
            next_delay = int(current_delay_ms * self._config.backoff_multiplier)
        elif self._config.policy == RetryPolicy.LINEAR.value:
            next_delay = current_delay_ms + self._config.initial_delay_ms
        else:
            next_delay = self._config.initial_delay_ms
        return min(next_delay, self._config.max_delay_ms)


@dataclass
class DependencyNode:
    name: str = ""
    dependencies: list[str] = field(default_factory=list)
    status: str = "pending"


@dataclass
class DependencyResult:
    execution_order: list[str] = field(default_factory=list)
    cycles: list[list[str]] = field(default_factory=list)
    independent_groups: list[list[str]] = field(default_factory=list)


class DependencyManager:
    def __init__(self):
        self._nodes: dict[str, DependencyNode] = {}

    def add_node(self, name: str, dependencies: list[str] | None = None) -> None:
        self._nodes[name] = DependencyNode(name=name, dependencies=dependencies or [])

    def remove_node(self, name: str) -> None:
        self._nodes.pop(name, None)
        for node in self._nodes.values():
            if name in node.dependencies:
                node.dependencies.remove(name)

    def resolve_order(self) -> DependencyResult:
        result = DependencyResult()

        in_degree: dict[str, int] = {name: 0 for name in self._nodes}
        graph: dict[str, list[str]] = {name: [] for name in self._nodes}

        for name, node in self._nodes.items():
            for dep in node.dependencies:
                if dep in self._nodes:
                    graph[dep].append(name)
                    in_degree[name] += 1

        queue = [name for name, deg in in_degree.items() if deg == 0]
        visited_count = 0

        while queue:
            current = queue.pop(0)
            result.execution_order.append(current)
            visited_count += 1

            for neighbor in graph[current]:
                in_degree[neighbor] -= 1
                if in_degree[neighbor] == 0:
                    queue.append(neighbor)

        if visited_count != len(self._nodes):
            remaining = set(self._nodes.keys()) - set(result.execution_order)
            result.cycles = self._find_cycles(remaining)
            for name in result.execution_order:
                if not self._nodes[name].dependencies:
                    pass

        result.independent_groups = self._find_independent_groups()
        return result

    def _find_cycles(self, nodes: set[str]) -> list[list[str]]:
        cycles = []
        visited: set[str] = set()
        path: list[str] = []

        def dfs(node: str) -> None:
            if node in visited:
                return
            if node in set(path):
                cycle_start = path.index(node)
                cycles.append(path[cycle_start:] + [node])
                return

            path.append(node)
            for dep in self._nodes.get(node, DependencyNode()).dependencies:
                if dep in nodes:
                    dfs(dep)
            path.pop()
            visited.add(node)

        for node in nodes:
            dfs(node)

        return cycles

    def _find_independent_groups(self) -> list[list[str]]:
        groups: list[set[str]] = []

        for name, node in self._nodes.items():
            related = {name}
            related.update(node.dependencies)
            for other_name, other_node in self._nodes.items():
                if name in other_node.dependencies or other_name in node.dependencies:
                    related.add(other_name)

            merged = False
            for i, group in enumerate(groups):
                if related & group:
                    groups[i] = group | related
                    merged = True
                    break
            if not merged:
                groups.append(related)

        return [list(g) for g in groups]

    def get_dependencies(self, name: str) -> list[str]:
        node = self._nodes.get(name)
        return node.dependencies if node else []

    def get_dependents(self, name: str) -> list[str]:
        return [n for n, node in self._nodes.items() if name in node.dependencies]


retry_strategy = RetryStrategy()
dependency_manager = DependencyManager()