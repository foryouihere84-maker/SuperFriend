from __future__ import annotations

import os
import hashlib
import shutil
import uuid
from datetime import datetime, timedelta
from fastapi import APIRouter, HTTPException, UploadFile, File, Form
from pydantic import BaseModel
from typing import Any

router = APIRouter()

UPLOAD_DIR = os.environ.get("UPLOAD_DIR", "/tmp/superfriend/uploads")
MAX_FILE_SIZE = 100 * 1024 * 1024  # 100MB


@router.post("/files/upload")
async def upload_file(
    file: UploadFile = File(...),
    session_id: str = Form(""),
    user_id: int = Form(0),
):
    content = await file.read()
    if len(content) > MAX_FILE_SIZE:
        raise HTTPException(400, f"File too large (max {MAX_FILE_SIZE // 1024 // 1024}MB)")

    file_hash = hashlib.sha256(content).hexdigest()
    session_dir = os.path.join(UPLOAD_DIR, session_id) if session_id else UPLOAD_DIR
    os.makedirs(session_dir, exist_ok=True)

    ext = os.path.splitext(file.filename or "")[1]
    stored_name = f"{uuid.uuid4().hex}{ext}"
    stored_path = os.path.join(session_dir, stored_name)

    with open(stored_path, "wb") as f:
        f.write(content)

    return {
        "success": True,
        "file": {
            "original_name": file.filename,
            "stored_path": stored_path,
            "file_size": len(content),
            "mime_type": file.content_type or "application/octet-stream",
            "file_hash": file_hash,
            "session_id": session_id,
        },
    }


@router.get("/files/{file_id}")
async def get_file(file_id: int):
    return {"success": False, "error": "File metadata not yet persisted to DB"}


@router.get("/files/session/{session_id}")
async def list_session_files(session_id: str):
    session_dir = os.path.join(UPLOAD_DIR, session_id)
    if not os.path.exists(session_dir):
        return {"files": []}
    files = []
    for name in os.listdir(session_dir):
        path = os.path.join(session_dir, name)
        if os.path.isfile(path):
            stat = os.stat(path)
            files.append({
                "name": name,
                "path": path,
                "size": stat.st_size,
                "modified": datetime.fromtimestamp(stat.st_mtime).isoformat(),
            })
    return {"files": files}


@router.delete("/files/{file_id}")
async def delete_file(file_id: int):
    return {"success": False, "error": "File metadata not yet persisted to DB"}