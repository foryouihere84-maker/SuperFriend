"""
Shell MCP Server - 本地化的 Shell 命令执行工具
基于官方 Shell MCP Server 实现，支持 bash/sh/cmd/powershell
"""
from mcp.server.fastmcp import FastMCP
import subprocess
import os
import platform
from pathlib import Path
from typing import Optional, List
import shlex

# 创建 MCP 服务器实例
mcp = FastMCP("Shell Executor")

# 允许的目录列表（安全限制）
ALLOWED_DIRECTORIES: List[str] = []

# 允许的 shell 列表
ALLOWED_SHELLS = {
    "bash": "bash",
    "sh": "sh",
    "cmd": "cmd",
    "powershell": "powershell",
    "pwsh": "pwsh"
}

# 默认超时时间（秒）
DEFAULT_TIMEOUT = 30

# 危险命令黑名单
DANGEROUS_COMMANDS = [
    "rm -rf /",
    "rm -rf /*",
    "dd if=/dev/zero",
    "mkfs.",
    ":(){:|:&};:",  # fork bomb
    "> /dev/sda",
    "del /f /s /q \\",
    "format ",
    "rd /s /q \\"
]


def set_allowed_directories(directories: List[str]):
    """设置允许的目录列表"""
    global ALLOWED_DIRECTORIES
    ALLOWED_DIRECTORIES = [str(Path(d).resolve()) for d in directories if Path(d).exists()]


def is_path_allowed(path: str) -> bool:
    """检查路径是否在允许的目录内"""
    if not ALLOWED_DIRECTORIES:
        return True  # 如果没有设置限制，则允许所有

    resolved_path = Path(path).resolve()
    for allowed in ALLOWED_DIRECTORIES:
        try:
            resolved_path.relative_to(allowed)
            return True
        except ValueError:
            continue
    return False


def is_dangerous_command(command: str) -> bool:
    """检查命令是否包含危险操作"""
    cmd_lower = command.lower()
    for dangerous in DANGEROUS_COMMANDS:
        if dangerous.lower() in cmd_lower:
            return True
    return False


def get_default_shell() -> str:
    """获取系统默认 shell"""
    system = platform.system()
    if system == "Windows":
        return "powershell"
    else:
        return "bash"


@mcp.tool()
async def execute_command(
    command: str,
    cwd: Optional[str] = None,
    shell: Optional[str] = None,
    timeout: Optional[int] = None
) -> str:
    """
    执行 shell 命令

    Args:
        command: 要执行的命令
        cwd: 工作目录（可选，默认为当前目录）
        shell: 使用的 shell（可选，默认根据系统自动选择：Windows 用 powershell，其他用 bash）
        timeout: 超时时间（秒，默认 30 秒）

    Returns:
        命令执行结果（stdout + stderr）
    """
    try:
        # 检查危险命令
        if is_dangerous_command(command):
            return "❌ 错误：检测到危险命令，执行被拒绝\n为了系统安全，该命令包含潜在危险操作。"

        # 确定工作目录
        if cwd:
            work_dir = Path(cwd).resolve()
            if not work_dir.exists():
                return f"❌ 错误：工作目录不存在\n路径：{cwd}"
            if not is_path_allowed(cwd):
                return f"❌ 错误：工作目录不在允许列表内\n路径：{cwd}\n允许的目录：{ALLOWED_DIRECTORIES}"
        else:
            work_dir = Path.cwd()

        # 确定 shell
        shell_to_use = shell or get_default_shell()
        if shell_to_use not in ALLOWED_SHELLS:
            return f"❌ 错误：不支持的 shell\n支持的 shell：{list(ALLOWED_SHELLS.keys())}"

        # 设置超时
        cmd_timeout = timeout or DEFAULT_TIMEOUT

        # 根据系统和 shell 构建命令
        system = platform.system()
        if system == "Windows":
            if shell_to_use in ["powershell", "pwsh"]:
                # PowerShell 执行
                full_command = [shell_to_use, "-Command", command]
            else:
                # CMD 执行
                full_command = ["cmd", "/c", command]
        else:
            # Unix/Linux/Mac
            full_command = [shell_to_use, "-c", command]

        # 执行命令
        result = subprocess.run(
            full_command,
            cwd=str(work_dir),
            capture_output=True,
            text=True,
            timeout=cmd_timeout,
            encoding='utf-8',
            errors='replace'
        )

        # 构建输出
        output_lines = [
            f"🖥️ 命令执行结果",
            f"",
            f"命令：{command}",
            f"工作目录：{work_dir}",
            f"Shell：{shell_to_use}",
            f"返回码：{result.returncode}",
            f""
        ]

        if result.stdout:
            output_lines.append("📤 标准输出：")
            output_lines.append("```")
            output_lines.append(result.stdout)
            output_lines.append("```")

        if result.stderr:
            output_lines.append("")
            output_lines.append("⚠️ 错误输出：")
            output_lines.append("```")
            output_lines.append(result.stderr)
            output_lines.append("```")

        return "\n".join(output_lines)

    except subprocess.TimeoutExpired:
        return f"⏱️ 错误：命令执行超时（超过 {cmd_timeout} 秒）\n命令：{command}"
    except FileNotFoundError as e:
        return f"❌ 错误：找不到可执行文件\n详情：{str(e)}\n请检查 shell 是否正确安装"
    except Exception as e:
        return f"❌ 命令执行失败\n错误：{str(e)}\n命令：{command}"


@mcp.tool()
async def list_allowed_directories() -> str:
    """
    列出当前允许的目录

    Returns:
        允许的目录列表
    """
    if not ALLOWED_DIRECTORIES:
        return "📂 允许的目录：无限制（所有目录都允许）"

    result = ["📂 允许的目录列表：", ""]
    for i, directory in enumerate(ALLOWED_DIRECTORIES, 1):
        exists = "✅" if Path(directory).exists() else "❌"
        result.append(f"  {i}. {exists} {directory}")

    return "\n".join(result)


@mcp.tool()
async def get_system_info() -> str:
    """
    获取系统信息

    Returns:
        操作系统、Shell 等系统信息
    """
    system = platform.system()
    release = platform.release()
    version = platform.version()
    machine = platform.machine()
    processor = platform.processor()

    result = [
        "🖥️ 系统信息",
        f"",
        f"操作系统：{system}",
        f"版本：{release}",
        f"详细版本：{version}",
        f"架构：{machine}",
        f"处理器：{processor}",
        f"",
        f"默认 Shell：{get_default_shell()}",
        f"当前工作目录：{Path.cwd()}",
        f"Python 版本：{platform.python_version()}"
    ]

    return "\n".join(result)


if __name__ == "__main__":
    # 从环境变量读取允许的目录
    allowed_dirs_env = os.environ.get("SHELL_ALLOWED_DIRECTORIES", "")
    if allowed_dirs_env:
        dirs = [d.strip() for d in allowed_dirs_env.split(",") if d.strip()]
        set_allowed_directories(dirs)

    # 运行 MCP 服务器
    mcp.run()
