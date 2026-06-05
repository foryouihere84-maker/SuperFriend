from __future__ import annotations

import asyncio
import structlog
from dataclasses import dataclass, field
from typing import Any

logger = structlog.get_logger()

BLOCKED_COMMANDS = {
    "rm -rf /", "rm -rf /*", "mkfs", "dd if=", ":(){ :|:& };:",
    "wget", "curl -o", "chmod 777", "chown root",
    "shutdown", "reboot", "halt", "poweroff",
    "passwd", "su root", "sudo rm",
    "format c:", "del /f /s /q c:",
}

BLOCKED_PATTERNS = [
    "rm -rf", "mkfs.", "dd if=", "> /dev/sd",
    "chmod 777 /", "chown root /",
    "shutdown", "reboot", "poweroff",
    "wget http", "curl -o",
    "nc -l", "ncat", "socat",
    "python -c 'import os; os.system",
    "eval(", "exec(", "__import__",
]


@dataclass
class SandboxResult:
    success: bool = False
    output: str = ""
    error: str = ""
    exit_code: int = -1
    execution_time_ms: float = 0.0
    blocked: bool = False
    block_reason: str = ""


@dataclass
class SandboxSession:
    session_id: str = ""
    working_directory: str = "/tmp/sandbox"
    environment: dict[str, str] = field(default_factory=dict)
    created_at: float = 0.0
    last_used: float = 0.0
    command_count: int = 0


class BashSandboxService:
    def __init__(self):
        self._sessions: dict[str, SandboxSession] = {}
        self._max_output_length: int = 50000
        self._max_execution_time: float = 30.0
        self._session_cleanup_delay_ms: float = 1800000

    def validate_command(self, command: str) -> tuple[bool, str]:
        if not command or not command.strip():
            return False, "Empty command"

        stripped = command.strip().lower()

        for blocked in BLOCKED_COMMANDS:
            if blocked.lower() in stripped:
                return False, f"Blocked dangerous command: {blocked}"

        for pattern in BLOCKED_PATTERNS:
            if pattern.lower() in stripped:
                return False, f"Blocked dangerous pattern: {pattern}"

        return True, ""

    async def execute(self, command: str, session_id: str = "", working_dir: str = "",
                      timeout: float | None = None) -> SandboxResult:
        is_valid, block_reason = self.validate_command(command)
        if not is_valid:
            logger.warning("sandbox_command_blocked", command=command[:100], reason=block_reason)
            return SandboxResult(blocked=True, block_reason=block_reason)

        session = self._get_or_create_session(session_id, working_dir)
        session.command_count += 1
        session.last_used = time.time() if hasattr(time, 'time') else 0.0

        actual_timeout = timeout or self._max_execution_time

        try:
            import time as _time
            start = _time.monotonic()

            proc = await asyncio.create_subprocess_shell(
                command,
                stdout=asyncio.subprocess.PIPE,
                stderr=asyncio.subprocess.PIPE,
                cwd=working_dir or session.working_directory,
            )

            try:
                stdout, stderr = await asyncio.wait_for(proc.communicate(), timeout=actual_timeout)
            except asyncio.TimeoutError:
                proc.kill()
                await proc.communicate()
                elapsed = (_time.monotonic() - start) * 1000
                return SandboxResult(
                    success=False, error=f"Command timed out after {actual_timeout}s",
                    exit_code=-1, execution_time_ms=elapsed,
                )

            elapsed = (_time.monotonic() - start) * 1000
            output = stdout.decode("utf-8", errors="replace")[:self._max_output_length]
            error_output = stderr.decode("utf-8", errors="replace")[:self._max_output_length]

            return SandboxResult(
                success=proc.returncode == 0,
                output=output,
                error=error_output,
                exit_code=proc.returncode or 0,
                execution_time_ms=elapsed,
            )

        except Exception as e:
            logger.error("sandbox_execute_error", error=str(e), command=command[:100])
            return SandboxResult(success=False, error=str(e))

    def _get_or_create_session(self, session_id: str, working_dir: str = "") -> SandboxSession:
        if session_id and session_id in self._sessions:
            return self._sessions[session_id]

        import time as _time
        session = SandboxSession(
            session_id=session_id or f"sandbox_{id(self)}_{_time.time():.0f}",
            working_directory=working_dir or "/tmp/sandbox",
            created_at=_time.time(),
            last_used=_time.time(),
        )
        if session_id:
            self._sessions[session_id] = session
        return session

    def close_session(self, session_id: str) -> None:
        self._sessions.pop(session_id, None)

    def get_session(self, session_id: str) -> SandboxSession | None:
        return self._sessions.get(session_id)

    def list_sessions(self) -> list[dict[str, Any]]:
        return [
            {
                "session_id": s.session_id,
                "working_directory": s.working_directory,
                "command_count": s.command_count,
                "last_used": s.last_used,
            }
            for s in self._sessions.values()
        ]


bash_sandbox_service = BashSandboxService()