from __future__ import annotations

import json
import structlog
from fastapi import APIRouter, HTTPException
from fastapi.responses import StreamingResponse
from pydantic import BaseModel

from superfriend.constant.chat_mode import ChatMode
from superfriend.services.chat_orchestrator import chat_orchestrator
from superfriend.services.context_manager import context_manager
from superfriend.services.intent_service import user_intent_service
from superfriend.models.intent import UserIntent, IntentType

router = APIRouter()
logger = structlog.get_logger()


class ChatRequest(BaseModel):
    session_id: str
    message: str
    model: str = "deepseek-chat"
    history: list[dict] = []
    user_id: int | None = None
    mode: str = ChatMode.DEFAULT
    enable_knowledge_extraction: bool = False
    file_urls: list[str] | None = None


# ── 对齐 Java ChatController 的端点 ──

@router.post("/lite-task")
async def chat_lite_task(request: ChatRequest):
    """Java: POST /api/v16/chat/lite-task (SSE)"""
    request.mode = ChatMode.LITE_TASK
    return await _stream_chat(request)


@router.post("/lite-task-multimodal")
async def chat_lite_multimodal(request: ChatRequest):
    """Java: POST /api/v16/chat/lite-task-multimodal (SSE)"""
    request.mode = ChatMode.LITE_TASK
    return await _stream_chat(request)


@router.post("/medium-task")
async def chat_medium_task(request: ChatRequest):
    """Java: POST /api/v16/chat/medium-task (SSE)"""
    request.mode = ChatMode.MEDIUM_TASK
    return await _stream_chat(request)


@router.post("/complex-task")
async def chat_complex_task(request: ChatRequest):
    """Java: POST /api/v16/chat/complex-task (SSE)"""
    request.mode = ChatMode.COMPLEX_TASK
    return await _stream_chat(request)


@router.post("/mcp")
async def chat_mcp(request: ChatRequest):
    """Java: POST /api/v16/chat/mcp (SSE)"""
    request.mode = ChatMode.COMPLEX_TASK
    return await _stream_chat(request)


@router.post("/cancel/{session_id}")
async def cancel_chat(session_id: str):
    """Java: POST /api/v16/chat/cancel/{sessionId}"""
    return {"success": True, "message": f"Session {session_id} cancelled"}


@router.get("/status/{session_id}")
async def get_chat_status(session_id: str):
    """Java: GET /api/v16/chat/status/{sessionId}"""
    session = context_manager.get_session(session_id)
    if session:
        return {"sessionId": session_id, "status": "active", "messageCount": session.get("message_count", 0)}
    return {"sessionId": session_id, "status": "not_found"}


@router.post("/finalize/{session_id}")
async def finalize_session(session_id: str):
    """Java: POST /api/v16/chat/finalize/{sessionId}"""
    return {"success": True, "sessionId": session_id}


@router.post("/extract/{session_id}")
async def extract_knowledge(session_id: str):
    """Java: POST /api/v16/chat/extract/{sessionId}"""
    return {"success": True, "sessionId": session_id, "extracted": False, "message": "Knowledge extraction not yet implemented"}


@router.get("/extraction-status/{session_id}")
async def get_extraction_status(session_id: str):
    """Java: GET /api/v16/chat/extraction-status/{sessionId}"""
    return {"sessionId": session_id, "status": "not_started"}


# ── 通用聊天端点 ──

@router.post("/send")
async def chat_send(request: ChatRequest):
    """非流式聊天"""
    events = []

    def collect(event: dict) -> None:
        events.append(event)

    try:
        intent = user_intent_service.analyze_intent(request.message, request.file_urls)

        await chat_orchestrator.dispatch(
            session_id=request.session_id,
            message=request.message,
            model=request.model,
            history=request.history,
            on_response=collect,
            mode=request.mode,
            intent=intent,
            user_id=request.user_id,
            file_urls=request.file_urls,
        )

        final_content = ""
        for evt in events:
            content = evt.get("content", "")
            if content and evt.get("type") != "error":
                final_content += content

        return {
            "session_id": request.session_id,
            "content": final_content or "Task completed",
            "events": events,
            "intent": intent.intent_type.value,
            "mode": request.mode,
        }

    except Exception as e:
        logger.error("chat_error", error=str(e))
        raise HTTPException(status_code=500, detail=str(e))


@router.post("/stream")
async def chat_stream(request: ChatRequest):
    """通用流式聊天"""
    return await _stream_chat(request)


# ── 内部辅助 ──

async def _stream_chat(request: ChatRequest):
    import asyncio

    event_queue: asyncio.Queue = asyncio.Queue()

    async def event_generator():
        try:
            intent = user_intent_service.analyze_intent(request.message, request.file_urls)
            yield f"data: {json.dumps({'type': 'intent', 'content': intent.intent_type.value, 'mode': request.mode})}\n\n"

            def on_event(event: dict) -> None:
                event_queue.put_nowait(event)

            async def run_dispatch():
                try:
                    await chat_orchestrator.dispatch(
                        session_id=request.session_id,
                        message=request.message,
                        model=request.model,
                        history=request.history,
                        on_response=on_event,
                        mode=request.mode,
                        intent=intent,
                        user_id=request.user_id,
                        file_urls=request.file_urls,
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
            logger.error("stream_error", error=str(e))
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
