import structlog
from fastapi import APIRouter, HTTPException
from pydantic import BaseModel

from superfriend.tools.mcp_tools import mcp_tool_manager

router = APIRouter()
logger = structlog.get_logger()


class McpServerConfig(BaseModel):
    name: str
    command: str
    args: list[str] = []
    env: dict[str, str] = {}


@router.get("/tools")
async def list_tools():
    tools = mcp_tool_manager.get_all_tools()
    return {"tools": tools}


@router.post("/config/reload")
async def reload_config():
    try:
        await mcp_tool_manager.load_servers()
        return {"status": "reloaded"}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.get("/servers")
async def list_servers():
    return {"servers": list(mcp_tool_manager._servers.keys())}