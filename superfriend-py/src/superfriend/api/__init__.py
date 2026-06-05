from superfriend.api.chat import router as chat_router
from superfriend.api.ai import router as ai_router
from superfriend.api.skill import router as skill_router
from superfriend.api.auth import router as auth_router
from superfriend.api.mcp import router as mcp_router
from superfriend.api.agent import router as agent_router
from superfriend.api.knowledge import router as knowledge_router
from superfriend.api.monitoring import router as monitoring_router
from superfriend.api.health import router as health_router
from superfriend.api.permission import router as permission_router
from superfriend.api.files import router as files_router
from superfriend.api.model_config import router as model_config_router
from superfriend.api.tools_api import router as tools_api_router
from superfriend.api.integration_api import router as integration_api_router
from superfriend.api.advanced_api import router as advanced_api_router

__all__ = [
    "chat_router", "ai_router", "skill_router", "auth_router", "mcp_router",
    "agent_router", "knowledge_router", "monitoring_router", "health_router",
    "permission_router", "files_router", "model_config_router", "tools_api_router",
    "integration_api_router", "advanced_api_router",
]
