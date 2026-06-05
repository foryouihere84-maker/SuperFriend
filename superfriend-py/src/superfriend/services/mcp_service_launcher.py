from __future__ import annotations

import asyncio
import json
import os
import shutil
import structlog
from dataclasses import dataclass, field
from typing import Any

logger = structlog.get_logger()


@dataclass
class McpServerConfig:
    command: str = ""
    args: list[str] = field(default_factory=list)
    env: dict[str, str] = field(default_factory=dict)
    cwd: str = ""
    description: str = ""
    auto_start: bool = False


@dataclass
class McpProcessInfo:
    server_name: str = ""
    config: McpServerConfig | None = None
    process: asyncio.subprocess.Process | None = None
    status: str = "stopped"
    pid: int = 0
    started_at: float = 0.0
    restart_count: int = 0


class McpServiceLauncher:
    def __init__(self, local_store_path: str = "mcp-local", npm_registry: str = "", pypi_index: str = ""):
        self._local_store_path = local_store_path
        self._npm_registry = npm_registry
        self._pypi_index = pypi_index
        self._installed_packages: dict[str, bool] = {}
        self._processes: dict[str, McpProcessInfo] = {}

    def prepare_config(self, server_name: str, config: McpServerConfig) -> McpServerConfig:
        command = config.command

        if command == "npx":
            return self._prepare_npx_server(server_name, config)
        elif command == "uvx":
            return self._prepare_uvx_server(server_name, config)
        elif command == "node":
            return self._prepare_node_server(server_name, config)
        elif command in ("python", "python3"):
            return self._prepare_python_server(server_name, config)

        return config

    def _prepare_npx_server(self, server_name: str, config: McpServerConfig) -> McpServerConfig:
        if len(config.args) < 2 or config.args[0] != "-y":
            logger.warning("mcp_npx_format_error", server=server_name)
            return config

        package_name = config.args[1]
        logger.info("mcp_npx_resolve", server=server_name, package=package_name)

        local_path = self._find_local_npm_package(package_name)
        if local_path:
            logger.info("mcp_npx_local", server=server_name, path=local_path)
            return self._create_local_node_config(config, local_path)

        if self._install_npm_package(package_name, server_name):
            local_path = self._find_local_npm_package(package_name)
            if local_path:
                return self._create_local_node_config(config, local_path)

        logger.info("mcp_npx_remote", server=server_name)
        return config

    def _prepare_uvx_server(self, server_name: str, config: McpServerConfig) -> McpServerConfig:
        if not config.args:
            return config

        package_name = config.args[0]
        logger.info("mcp_uvx_resolve", server=server_name, package=package_name)

        local_path = self._find_local_python_package(package_name)
        if local_path:
            return self._create_local_python_config(config, local_path)

        installed_path = self._install_python_package(package_name, server_name)
        if installed_path:
            return self._create_local_python_config(config, installed_path)

        return config

    def _prepare_node_server(self, server_name: str, config: McpServerConfig) -> McpServerConfig:
        if not config.args:
            return config
        script_path = self._replace_placeholders(config.args[0])
        if not os.path.exists(script_path):
            logger.warning("mcp_node_script_not_found", server=server_name, path=script_path)
            return config
        new_config = McpServerConfig(
            command=config.command, args=[script_path] + config.args[1:],
            env=config.env, cwd=config.cwd, description=config.description,
        )
        return new_config

    def _prepare_python_server(self, server_name: str, config: McpServerConfig) -> McpServerConfig:
        if not config.args:
            return config
        script_path = self._replace_placeholders(config.args[0])
        if not os.path.exists(script_path):
            logger.warning("mcp_python_script_not_found", server=server_name, path=script_path)
        return config

    async def start_server(self, server_name: str, config: McpServerConfig) -> McpProcessInfo:
        prepared = self.prepare_config(server_name, config)

        try:
            cmd = [prepared.command] + prepared.args
            env = {**os.environ, **prepared.env}

            process = await asyncio.create_subprocess_exec(
                *cmd, env=env, cwd=prepared.cwd or None,
                stdout=asyncio.subprocess.PIPE, stderr=asyncio.subprocess.PIPE,
            )

            info = McpProcessInfo(
                server_name=server_name, config=prepared,
                process=process, status="running", pid=process.pid or 0,
                started_at=__import__("time").time(),
            )
            self._processes[server_name] = info
            logger.info("mcp_server_started", server=server_name, pid=process.pid)
            return info

        except Exception as e:
            logger.error("mcp_server_start_error", server=server_name, error=str(e))
            return McpProcessInfo(server_name=server_name, status="error")

    async def stop_server(self, server_name: str) -> bool:
        info = self._processes.get(server_name)
        if not info or not info.process:
            return False

        try:
            info.process.terminate()
            await asyncio.wait_for(info.process.wait(), timeout=10)
        except asyncio.TimeoutError:
            info.process.kill()
        except Exception as e:
            logger.error("mcp_server_stop_error", server=server_name, error=str(e))

        info.status = "stopped"
        logger.info("mcp_server_stopped", server=server_name)
        return True

    async def restart_server(self, server_name: str) -> McpProcessInfo | None:
        info = self._processes.get(server_name)
        if not info or not info.config:
            return None
        await self.stop_server(server_name)
        info.restart_count += 1
        return await self.start_server(server_name, info.config)

    def get_server_status(self, server_name: str) -> dict[str, Any]:
        info = self._processes.get(server_name)
        if not info:
            return {"server_name": server_name, "status": "not_found"}
        return {
            "server_name": info.server_name,
            "status": info.status,
            "pid": info.pid,
            "started_at": info.started_at,
            "restart_count": info.restart_count,
        }

    def list_servers(self) -> list[dict[str, Any]]:
        return [self.get_server_status(name) for name in self._processes]

    def _find_local_npm_package(self, package_name: str) -> str | None:
        local_path = os.path.join(self._local_store_path, "node_modules", package_name)
        if os.path.exists(local_path):
            return local_path
        return None

    def _find_local_python_package(self, package_name: str) -> str | None:
        local_path = os.path.join(self._local_store_path, "python", package_name)
        if os.path.exists(local_path):
            return local_path
        return None

    def _install_npm_package(self, package_name: str, server_name: str) -> bool:
        if self._installed_packages.get(package_name):
            return True
        try:
            import subprocess
            npm_cmd = "npm"
            install_args = [npm_cmd, "install", package_name, "--prefix", self._local_store_path]
            if self._npm_registry:
                install_args.extend(["--registry", self._npm_registry])
            result = subprocess.run(install_args, capture_output=True, timeout=120)
            if result.returncode == 0:
                self._installed_packages[package_name] = True
                logger.info("mcp_npm_installed", package=package_name)
                return True
        except Exception as e:
            logger.error("mcp_npm_install_error", package=package_name, error=str(e))
        return False

    def _install_python_package(self, package_name: str, server_name: str) -> str | None:
        if self._installed_packages.get(package_name):
            return self._find_local_python_package(package_name)
        try:
            import subprocess
            target = os.path.join(self._local_store_path, "python")
            os.makedirs(target, exist_ok=True)
            pip_args = ["pip", "install", package_name, "--target", target]
            if self._pypi_index:
                pip_args.extend(["-i", self._pypi_index])
            result = subprocess.run(pip_args, capture_output=True, timeout=120)
            if result.returncode == 0:
                self._installed_packages[package_name] = True
                logger.info("mcp_python_installed", package=package_name)
                return self._find_local_python_package(package_name)
        except Exception as e:
            logger.error("mcp_python_install_error", package=package_name, error=str(e))
        return None

    def _create_local_node_config(self, original: McpServerConfig, local_path: str) -> McpServerConfig:
        entry = os.path.join(local_path, "index.js")
        if not os.path.exists(entry):
            entry = local_path
        return McpServerConfig(
            command="node", args=[entry] + original.args[2:],
            env=original.env, cwd=original.cwd, description=original.description,
        )

    def _create_local_python_config(self, original: McpServerConfig, local_path: str) -> McpServerConfig:
        return McpServerConfig(
            command="python", args=[local_path] + original.args[1:],
            env=original.env, cwd=original.cwd, description=original.description,
        )

    def _replace_placeholders(self, path: str) -> str:
        return path.replace("${localStorePath}", self._local_store_path)


mcp_service_launcher = McpServiceLauncher()