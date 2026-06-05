from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any, Callable, Awaitable

from superfriend.tools.models import ToolMetadata

ToolFn = Callable[..., Awaitable[Any]]


@dataclass
class ToolDefinition:
    name: str
    description: str
    parameters: dict[str, Any] = field(default_factory=dict)
    required: list[str] = field(default_factory=list)
    category: str = "general"
    reliability: float = 1.0
    cost: float = 0.0

    def to_openai_function(self) -> dict[str, Any]:
        return {
            "type": "function",
            "function": {
                "name": self.name,
                "description": self.description,
                "parameters": {
                    "type": "object",
                    "properties": self.parameters,
                    "required": self.required,
                },
            },
        }


class ToolRegistry:
    def __init__(self):
        self._tools: dict[str, ToolDefinition] = {}
        self._executors: dict[str, ToolFn] = {}
        self._category_index: dict[str, set[str]] = {}
        self._metadata_map: dict[str, ToolMetadata] = {}

    def register(
        self,
        name: str,
        description: str,
        executor: ToolFn,
        parameters: dict[str, Any] | None = None,
        required: list[str] | None = None,
        category: str = "general",
        cost: float = 0.0,
        reliability: float = 1.0,
    ) -> None:
        self._tools[name] = ToolDefinition(
            name=name,
            description=description,
            parameters=parameters or {},
            required=required or [],
            category=category,
            reliability=reliability,
            cost=cost,
        )
        self._executors[name] = executor

        self._category_index.setdefault(category, set()).add(name)

        self._metadata_map[name] = ToolMetadata(
            name=name,
            description=description,
            category=category,
            cost=cost,
            reliability=reliability,
        )

    def get_tool(self, name: str) -> ToolDefinition | None:
        return self._tools.get(name)

    def get_executor(self, name: str) -> ToolFn | None:
        return self._executors.get(name)

    def get_all_tools(self) -> list[ToolDefinition]:
        return list(self._tools.values())

    def get_tools_by_category(self, category: str) -> list[ToolDefinition]:
        names = self._category_index.get(category, set())
        return [self._tools[n] for n in names if n in self._tools]

    def get_categories(self) -> set[str]:
        return set(self._category_index.keys())

    def get_metadata(self, tool_name: str) -> ToolMetadata | None:
        return self._metadata_map.get(tool_name)

    def update_tool_reliability(self, tool_name: str, success: bool) -> None:
        metadata = self._metadata_map.get(tool_name)
        if metadata:
            metadata.record_execution(success)
            if tool_name in self._tools:
                self._tools[tool_name].reliability = metadata.reliability

    def search_tools(self, keyword: str) -> list[ToolDefinition]:
        lower = keyword.lower()
        results: list[ToolDefinition] = []
        for tool in self._tools.values():
            if (lower in tool.name.lower()
                    or lower in tool.description.lower()
                    or lower in tool.category.lower()):
                results.append(tool)
        return results

    def list_tools(self, category: str | None = None) -> list[ToolDefinition]:
        if category:
            return self.get_tools_by_category(category)
        return self.get_all_tools()

    def to_openai_functions(self, category: str | None = None) -> list[dict[str, Any]]:
        return [t.to_openai_function() for t in self.list_tools(category)]

    def has_tool(self, name: str) -> bool:
        return name in self._executors

    def __contains__(self, name: str) -> bool:
        return name in self._executors

    def __getitem__(self, name: str) -> ToolFn:
        return self._executors[name]


default_registry = ToolRegistry()