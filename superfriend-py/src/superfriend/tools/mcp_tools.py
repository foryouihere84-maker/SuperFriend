import json
import httpx
import structlog

from superfriend.config import settings
from superfriend.tools.registry import ToolRegistry, ToolDefinition

logger = structlog.get_logger()


class McpToolManager:
    def __init__(self, registry: ToolRegistry | None = None):
        self.registry = registry or ToolRegistry()
        self._servers: dict[str, dict] = {}
        self._tool_cache: dict[str, list[ToolDefinition]] = {}

    async def load_servers(self, config_path: str | None = None) -> None:
        path = config_path or settings.mcp_config_path
        if not path:
            logger.info("mcp_no_config_path", message="No MCP config path specified")
            return

        try:
            async with httpx.AsyncClient() as client:
                if path.startswith(("http://", "https://")):
                    resp = await client.get(path)
                    data = resp.json()
                else:
                    import os
                    if os.path.exists(path):
                        with open(path, encoding="utf-8") as f:
                            data = json.load(f)
                    else:
                        logger.warning("mcp_config_not_found", path=path)
                        return

            servers = data.get("mcpServers", data.get("servers", {}))
            for name, config in servers.items():
                self._servers[name] = config
                logger.info("mcp_server_loaded", name=name)

        except Exception as e:
            logger.error("mcp_load_failed", error=str(e))

    async def discover_tools(self, server_name: str) -> list[ToolDefinition]:
        if server_name in self._tool_cache:
            return self._tool_cache[server_name]

        config = self._servers.get(server_name)
        if not config:
            return []

        try:
            command = config.get("command", "")
            args = config.get("args", [])
            env = config.get("env", {})

            import subprocess
            import os

            full_env = {**os.environ, **env}
            cmd = [command] + args

            result = subprocess.run(
                cmd,
                capture_output=True,
                text=True,
                timeout=30,
                env=full_env,
            )

            if result.returncode == 0:
                try:
                    tools_data = json.loads(result.stdout)
                    tools = self._parse_tools(tools_data)
                    self._tool_cache[server_name] = tools
                    return tools
                except json.JSONDecodeError:
                    logger.warning("mcp_tool_parse_failed", server=server_name)

        except Exception as e:
            logger.error("mcp_tool_discovery_failed", server=server_name, error=str(e))

        return []

    def _parse_tools(self, data: dict) -> list[ToolDefinition]:
        tools = []
        tool_list = data.get("tools", data.get("functions", []))

        for tool_data in tool_list:
            if isinstance(tool_data, str):
                tools.append(ToolDefinition(name=tool_data, description=tool_data))
            elif isinstance(tool_data, dict):
                tools.append(ToolDefinition(
                    name=tool_data.get("name", "unknown"),
                    description=tool_data.get("description", ""),
                    parameters=tool_data.get("parameters", tool_data.get("input_schema", {}).get("properties", {})),
                    required=tool_data.get("required", tool_data.get("input_schema", {}).get("required", [])),
                ))

        return tools

    async def call_tool(self, server_name: str, tool_name: str, **params) -> str:
        config = self._servers.get(server_name)
        if not config:
            return f"Server '{server_name}' not found"

        try:
            import subprocess
            import os

            command = config.get("command", "")
            args = config.get("args", [])
            env = {**os.environ, **config.get("env", {})}

            call_args = {**params, "tool_name": tool_name, "server_name": server_name}
            cmd = [command] + args + [json.dumps(call_args)]

            result = subprocess.run(
                cmd,
                capture_output=True,
                text=True,
                timeout=120,
                env=env,
            )

            if result.returncode == 0:
                return result.stdout.strip()
            else:
                return f"Error: {result.stderr.strip()}"

        except Exception as e:
            return f"Error calling tool: {str(e)}"

    def get_all_tools(self) -> list[dict]:
        return [
            {
                "name": t.name,
                "description": t.description,
                "category": t.category,
            }
            for t in self.registry.list_tools()
        ]


mcp_tool_manager = McpToolManager()