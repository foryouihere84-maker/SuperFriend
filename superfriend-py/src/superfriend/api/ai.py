from __future__ import annotations

import json
import structlog
from fastapi import APIRouter, HTTPException, Query
from fastapi.responses import StreamingResponse
from pydantic import BaseModel

from superfriend.services.ai_service import ai_service
from superfriend.services.context_manager import context_manager

router = APIRouter()
logger = structlog.get_logger()


class AIChatRequest(BaseModel):
    """对齐 Java AIChatRequest"""
    session_id: str
    message: str
    model: str = "deepseek-chat"
    history: list[dict] = []
    user_id: int | None = None


@router.post("/chat")
async def ai_chat_stream(request: AIChatRequest):
    """Java: POST /api/v16/ai/chat (SSE)"""
    import asyncio

    event_queue: asyncio.Queue = asyncio.Queue()

    async def event_generator():
        try:
            def on_event(event: dict) -> None:
                event_queue.put_nowait(event)

            async def run_dispatch():
                try:
                    await ai_service.chat_stream(
                        message=request.message,
                        session_id=request.session_id,
                        model=request.model,
                        history=request.history,
                        user_id=request.user_id,
                        on_response=on_event,
                    )
                finally:
                    event_queue.put_nowait(None)

            dispatch_task = asyncio.create_task(run_dispatch())

            while True:
                event = await event_queue.get()
                if event is None:
                    break
                event_str = json.dumps(event, ensure_ascii=False)
                yield f"data: {event_str}\n\n"

            await dispatch_task
            yield "data: [DONE]\n\n"

        except Exception as e:
            logger.error("ai_stream_error", error=str(e))
            yield f"data: {json.dumps({'type': 'error', 'content': str(e)})}\n\n"

    return StreamingResponse(
        event_generator(),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "Connection": "keep-alive",
            "X-Accel-Buffering": "no",
        },
    )


@router.post("/func")
async def ai_func_stream(request: AIChatRequest):
    """Java: POST /api/v16/ai/func (SSE) - function calling"""
    import asyncio

    event_queue: asyncio.Queue = asyncio.Queue()

    async def event_generator():
        try:
            def on_event(event: dict) -> None:
                event_queue.put_nowait(event)

            async def run_dispatch():
                try:
                    await ai_service.chat_stream(
                        message=request.message,
                        session_id=request.session_id,
                        model=request.model,
                        history=request.history,
                        user_id=request.user_id,
                        on_response=on_event,
                    )
                finally:
                    event_queue.put_nowait(None)

            dispatch_task = asyncio.create_task(run_dispatch())

            while True:
                event = await event_queue.get()
                if event is None:
                    break
                event_str = json.dumps(event, ensure_ascii=False)
                yield f"data: {event_str}\n\n"

            await dispatch_task
            yield "data: [DONE]\n\n"

        except Exception as e:
            logger.error("ai_func_error", error=str(e))
            yield f"data: {json.dumps({'type': 'error', 'content': str(e)})}\n\n"

    return StreamingResponse(
        event_generator(),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "Connection": "keep-alive",
            "X-Accel-Buffering": "no",
        },
    )


@router.get("/chat-sessions")
async def get_chat_sessions(user_id: int | None = None, mode: str | None = None):
    """Java: GET /api/v16/ai/chat-sessions"""
    try:
        from superfriend.db.session import async_session_factory
        from superfriend.db.models import ChatHistory
        from sqlalchemy import select

        async with async_session_factory() as session:
            q = select(ChatHistory)
            if user_id:
                q = q.where(ChatHistory.user_id == user_id)
            if mode:
                q = q.where(ChatHistory.mode == mode)
            q = q.order_by(ChatHistory.updated_time.desc())
            result = await session.execute(q)
            histories = list(result.scalars().all())
            return {"success": True, "data": [
                {
                    "id": h.id,
                    "sessionId": h.session_id,
                    "userId": h.user_id,
                    "title": h.title,
                    "model": h.model,
                    "mode": h.mode,
                    "messageCount": h.message_count,
                    "createdTime": str(h.created_time) if h.created_time else None,
                    "updatedTime": str(h.updated_time) if h.updated_time else None,
                }
                for h in histories
            ]}
    except Exception as e:
        logger.error("get_chat_sessions_error", error=str(e))
        return {"success": False, "message": str(e), "data": []}


@router.get("/chat-sessions/{session_id}")
async def get_chat_session_messages(session_id: str):
    """Java: GET /api/v16/ai/chat-sessions/{sessionId}"""
    try:
        from superfriend.db.session import async_session_factory
        from superfriend.db.repository import ChatHistoryRepository, ChatMessageRepository

        async with async_session_factory() as session:
            history_repo = ChatHistoryRepository(session)
            msg_repo = ChatMessageRepository(session)

            history = await history_repo.get_by_session(session_id)
            messages = await msg_repo.get_by_session(session_id, limit=100)

            return {
                "success": True,
                "data": {
                    "session": {
                        "id": history.id if history else None,
                        "sessionId": session_id,
                        "title": history.title if history else None,
                        "model": history.model if history else None,
                        "mode": history.mode if history else None,
                        "messageCount": history.message_count if history else 0,
                    } if history else None,
                    "messages": [
                        {
                            "id": m.id,
                            "role": m.role,
                            "content": m.content,
                            "createdTime": str(m.created_time) if m.created_time else None,
                        }
                        for m in reversed(messages)
                    ],
                },
            }
    except Exception as e:
        logger.error("get_chat_session_messages_error", error=str(e))
        return {"success": False, "message": str(e), "data": {}}


@router.delete("/chat-sessions/{session_id}")
async def delete_chat_session(session_id: str):
    """Java: DELETE /api/v16/ai/chat-sessions/{sessionId}"""
    try:
        from superfriend.db.session import async_session_factory
        from superfriend.db.repository import ChatMessageRepository

        async with async_session_factory() as session:
            msg_repo = ChatMessageRepository(session)
            await msg_repo.clear_session(session_id)
            await session.commit()

        context_manager.clear_session(session_id)
        return {"success": True, "message": "删除成功"}
    except Exception as e:
        logger.error("delete_chat_session_error", error=str(e))
        return {"success": False, "message": str(e)}


@router.delete("/chat-sessions/batch")
async def batch_delete_chat_sessions(body: dict = None):
    """Java: DELETE /api/v16/ai/chat-sessions/batch"""
    try:
        session_ids = (body or {}).get("sessionIds", [])
        from superfriend.db.session import async_session_factory
        from superfriend.db.repository import ChatMessageRepository

        async with async_session_factory() as session:
            msg_repo = ChatMessageRepository(session)
            for sid in session_ids:
                await msg_repo.clear_session(sid)
            await session.commit()

        return {"success": True, "message": f"批量删除成功，数量: {len(session_ids)}"}
    except Exception as e:
        return {"success": False, "message": str(e)}


@router.get("/history")
async def get_ai_history(user_id: int = Query(1), limit: int = 20):
    """Java: GET /api/v16/ai/history"""
    try:
        from superfriend.db.session import async_session_factory
        from superfriend.db.models import AIProcessHistory
        from sqlalchemy import select

        async with async_session_factory() as session:
            q = select(AIProcessHistory).where(
                AIProcessHistory.user_id == user_id
            ).order_by(AIProcessHistory.created_time.desc()).limit(limit)
            result = await session.execute(q)
            records = list(result.scalars().all())
            return {"success": True, "data": [
                {
                    "id": r.id,
                    "sessionId": r.session_id,
                    "userId": r.user_id,
                    "model": r.model,
                    "inputTokens": r.input_tokens,
                    "outputTokens": r.output_tokens,
                    "totalCost": r.total_cost,
                    "createdTime": str(r.created_time) if r.created_time else None,
                }
                for r in records
            ]}
    except Exception as e:
        logger.error("get_ai_history_error", error=str(e))
        return {"success": False, "message": str(e), "data": []}


@router.post("/compress-history")
async def compress_history(session_id: str = ""):
    """Java: POST /api/v16/ai/compress-history"""
    return {"success": True, "message": "History compression not yet implemented"}
