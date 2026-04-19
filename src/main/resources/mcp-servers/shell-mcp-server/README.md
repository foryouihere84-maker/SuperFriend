# Shell MCP Server

本地化的 Shell 命令执行 MCP 工具，支持 bash/sh/cmd/powershell。

## 功能

- 🔒 安全执行 - 命令只能在指定目录内执行
- 🐚 多 Shell 支持 - 支持 bash、sh、cmd、powershell
- ⏱️ 超时控制 - 自动终止长时间运行的命令
- 🌍 跨平台 - 支持 Windows 和 Unix/Linux/Mac 系统
- 🛡️ 安全保护 - 内置危险命令黑名单

## 工具列表

1. **execute_command** - 执行 shell 命令
   - `command`: 要执行的命令
   - `cwd`: 工作目录（可选）
   - `shell`: 使用的 shell（可选）
   - `timeout`: 超时时间（秒，默认 30）

2. **list_allowed_directories** - 列出允许的目录

3. **get_system_info** - 获取系统信息

## 安装

```bash
# 使用项目虚拟环境
pip install -r requirements.txt
```

## 配置

在 `mcp-servers-config.json` 中添加：

```json
{
  "shell": {
    "command": "${python.venv.exec}",
    "args": ["${mcp.servers-path}/shell-mcp-server/server.py"],
    "env": {
      "SHELL_ALLOWED_DIRECTORIES": "C:\\Users\\user\\project,C:\\temp"
    },
    "disabled": false,
    "timeout": 60000,
    "description": "【Shell 命令工具】本地 shell 命令执行服务，支持 bash/powershell/cmd。"
  }
}
```

## 安全说明

- 默认允许所有目录（当 SHELL_ALLOWED_DIRECTORIES 未设置时）
- 设置 SHELL_ALLOWED_DIRECTORIES 环境变量限制可执行目录
- 危险命令（如 rm -rf /）会被自动拦截
- 建议在生产环境设置目录限制
