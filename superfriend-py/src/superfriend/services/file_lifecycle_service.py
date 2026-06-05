from __future__ import annotations

import os
import time
import structlog
from dataclasses import dataclass, field
from typing import Any
from enum import Enum

logger = structlog.get_logger()


class FileStatus(str, Enum):
    ACTIVE = "active"
    EXPIRED = "expired"
    DELETED = "deleted"


@dataclass
class FileMetadata:
    file_id: str = ""
    original_name: str = ""
    stored_path: str = ""
    file_size: int = 0
    mime_type: str = ""
    file_hash: str = ""
    session_id: str = ""
    user_id: int = 0
    status: str = FileStatus.ACTIVE.value
    created_at: float = 0.0
    last_accessed: float = 0.0
    access_count: int = 0
    tags: list[str] = field(default_factory=list)


@dataclass
class SessionFileIndex:
    session_id: str = ""
    files: list[FileMetadata] = field(default_factory=list)
    total_size: int = 0
    file_types: dict[str, int] = field(default_factory=dict)


class FileLifecycleManager:
    def __init__(self, max_file_age_days: int = 30, max_session_files: int = 50,
                 max_total_size_mb: int = 500, cleanup_interval_hours: int = 24):
        self._max_file_age_days = max_file_age_days
        self._max_session_files = max_session_files
        self._max_total_size_mb = max_total_size_mb
        self._cleanup_interval_hours = cleanup_interval_hours
        self._files: dict[str, FileMetadata] = {}
        self._last_cleanup = time.time()

    def register_file(self, metadata: FileMetadata) -> None:
        metadata.created_at = metadata.created_at or time.time()
        metadata.last_accessed = time.time()
        self._files[metadata.file_id] = metadata

    def get_file(self, file_id: str) -> FileMetadata | None:
        f = self._files.get(file_id)
        if f and f.status == FileStatus.ACTIVE.value:
            f.last_accessed = time.time()
            f.access_count += 1
            return f
        return None

    def delete_file(self, file_id: str) -> bool:
        f = self._files.get(file_id)
        if not f:
            return False

        f.status = FileStatus.DELETED.value
        if f.stored_path and os.path.exists(f.stored_path):
            try:
                os.remove(f.stored_path)
            except OSError as e:
                logger.warning("file_delete_error", path=f.stored_path, error=str(e))
        return True

    def check_expiry(self) -> list[str]:
        now = time.time()
        expired = []
        for fid, f in self._files.items():
            if f.status != FileStatus.ACTIVE.value:
                continue
            age_days = (now - f.created_at) / 86400
            if age_days > self._max_file_age_days:
                f.status = FileStatus.EXPIRED.value
                expired.append(fid)
        return expired

    def cleanup_expired(self) -> int:
        expired = self.check_expiry()
        for fid in expired:
            self.delete_file(fid)
        if expired:
            logger.info("file_cleanup", expired_count=len(expired))
        return len(expired)

    def get_session_files(self, session_id: str) -> list[FileMetadata]:
        return [f for f in self._files.values() if f.session_id == session_id and f.status == FileStatus.ACTIVE.value]

    def get_stats(self) -> dict[str, Any]:
        active = [f for f in self._files.values() if f.status == FileStatus.ACTIVE.value]
        return {
            "total_files": len(active),
            "total_size": sum(f.file_size for f in active),
            "by_type": self._count_by_type(active),
            "by_session": len(set(f.session_id for f in active if f.session_id)),
        }

    def _count_by_type(self, files: list[FileMetadata]) -> dict[str, int]:
        counts: dict[str, int] = {}
        for f in files:
            main_type = f.mime_type.split("/")[0] if "/" in f.mime_type else "other"
            counts[main_type] = counts.get(main_type, 0) + 1
        return counts


class SessionFileIndexService:
    def __init__(self, lifecycle_manager: FileLifecycleManager):
        self._lifecycle = lifecycle_manager

    def build_index(self, session_id: str) -> SessionFileIndex:
        files = self._lifecycle.get_session_files(session_id)
        total_size = sum(f.file_size for f in files)
        type_counts: dict[str, int] = {}
        for f in files:
            main_type = f.mime_type.split("/")[0] if "/" in f.mime_type else "other"
            type_counts[main_type] = type_counts.get(main_type, 0) + 1

        return SessionFileIndex(
            session_id=session_id, files=files,
            total_size=total_size, file_types=type_counts,
        )

    def search_files(self, session_id: str, query: str = "", mime_type: str = "") -> list[FileMetadata]:
        files = self._lifecycle.get_session_files(session_id)
        results = files
        if query:
            q = query.lower()
            results = [f for f in results if q in f.original_name.lower() or q in " ".join(f.tags).lower()]
        if mime_type:
            results = [f for f in results if f.mime_type.startswith(mime_type)]
        return results

    def get_file_summary(self, session_id: str) -> dict[str, Any]:
        index = self.build_index(session_id)
        return {
            "session_id": session_id,
            "file_count": len(index.files),
            "total_size": index.total_size,
            "total_size_mb": round(index.total_size / 1024 / 1024, 2),
            "file_types": index.file_types,
        }


file_lifecycle_manager = FileLifecycleManager()
session_file_index_service = SessionFileIndexService(file_lifecycle_manager)