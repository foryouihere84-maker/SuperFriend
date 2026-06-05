import os

import uvicorn
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles
from fastapi.responses import FileResponse, JSONResponse

from superfriend.config import settings
from superfriend.api import (
    chat_router, ai_router, skill_router, auth_router, mcp_router,
    agent_router, knowledge_router, monitoring_router, health_router,
    permission_router, files_router, model_config_router, tools_api_router,
    integration_api_router, advanced_api_router,
)

# 前端静态资源路径：优先使用环境变量，否则使用默认路径
STATIC_DIR = os.environ.get(
    "STATIC_DIR",
    r"c:\Users\iherefor\Desktop\project\SuperFriend\src\main\resources\static",
)

app = FastAPI(title=settings.app_name, debug=settings.debug)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# ── API 路由 - 对齐 Java 项目的路径 ──
# Java: /api/auth
app.include_router(auth_router, prefix="/api/auth", tags=["auth"])

# Java: /api/v16/chat
app.include_router(chat_router, prefix="/api/v16/chat", tags=["chat"])

# Java: /api/v16/ai
app.include_router(ai_router, prefix="/api/v16/ai", tags=["ai"])

# Java: /api/v16/skills
app.include_router(skill_router, prefix="/api/v16/skills", tags=["skills"])

# Java: /api/v16/mcp
app.include_router(mcp_router, prefix="/api/v16/mcp", tags=["mcp"])

# Java: /api/v16/agent
app.include_router(agent_router, prefix="/api/v16/agent", tags=["agent"])

# Java: /api/v16/knowledge-graph + /api/memory-palace
app.include_router(knowledge_router, prefix="/api/v16", tags=["knowledge-graph"])

# Java: /api/v16/cost
app.include_router(monitoring_router, prefix="/api/v16/cost", tags=["cost-tracking"])

# Java: /api/v16/permission
app.include_router(permission_router, prefix="/api/v16/permission", tags=["permission"])

# Java: /api/v16/files
app.include_router(files_router, prefix="/api/v16/files", tags=["files"])

# Java: /api/v16/model-config
app.include_router(model_config_router, prefix="/api/v16/model-config", tags=["model-config"])

# Java: /api/v16/tools
app.include_router(tools_api_router, prefix="/api/v16/tools", tags=["tools"])

# 其他高级 API
app.include_router(integration_api_router, prefix="/api/v16", tags=["integration"])
app.include_router(advanced_api_router, prefix="/api/v16", tags=["advanced"])

app.include_router(health_router, tags=["health"])


@app.get("/api/health")
async def health_check():
    return {"status": "ok", "app": settings.app_name}


# ── 前端静态资源挂载 ──
if os.path.isdir(STATIC_DIR):
    assets_dir = os.path.join(STATIC_DIR, "assets")
    if os.path.isdir(assets_dir):
        app.mount("/assets", StaticFiles(directory=assets_dir), name="assets")

    @app.get("/")
    async def serve_index():
        return FileResponse(os.path.join(STATIC_DIR, "index.html"))

    @app.get("/vite.svg")
    async def vite_svg():
        svg = os.path.join(STATIC_DIR, "vite.svg")
        if os.path.exists(svg):
            return FileResponse(svg, media_type="image/svg+xml")
        return JSONResponse({"error": "not found"}, status_code=404)

    # SPA fallback：非 /api /docs /openapi.json 的路径返回 index.html
    @app.get("/{full_path:path}")
    async def spa_fallback(full_path: str):
        # 尝试匹配真实静态文件
        file_path = os.path.join(STATIC_DIR, full_path)
        if os.path.isfile(file_path):
            return FileResponse(file_path)
        # 否则返回 index.html，让 Vue Router 处理前端路由
        return FileResponse(os.path.join(STATIC_DIR, "index.html"))

    print(f"[SuperFriend] 前端静态资源已挂载: {STATIC_DIR}")
else:
    @app.get("/")
    async def root():
        return {
            "app": settings.app_name,
            "docs": "/docs",
            "health": "/api/health",
            "warning": "前端静态资源未找到，请设置 STATIC_DIR 环境变量",
        }


def main():
    uvicorn.run(
        "superfriend.main:app",
        host=settings.host,
        port=settings.port,
        reload=settings.debug,
    )


if __name__ == "__main__":
    main()
