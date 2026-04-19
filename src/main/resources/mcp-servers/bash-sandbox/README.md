# Bash Sandbox MCP Server

在隔离环境中执行 Shell 命令的 MCP Server，为 AI Agent 提供安全的命令执行能力。

## 特性

- **安全隔离**：白名单/黑名单双重过滤，防止危险命令执行
- **资源限制**：动态计算 CPU、内存、文件大小等限制
- **会话管理**：支持多会话，每个会话独立的工作目录和环境变量
- **跨平台**：支持 Linux 和 Windows（Windows 功能受限）
- **可观测性**：完整的日志记录和审计追踪

## 安装

```bash
cd src/main/resources/mcp-servers/bash-sandbox
npm install
npm run build
```

## 配置

在 MCP 配置文件中添加：

```json
{
  "mcpServers": {
    "bash-sandbox": {
      "command": "node",
      "args": ["/path/to/bash-sandbox/dist/index.js"],
      "env": {
        "SANDBOX_ROOT_DIR": "/tmp/superfriend_sandbox",
        "SANDBOX_ROOT_DIR_WIN": "C:\\Temp\\superfriend_sandbox"
      }
    }
  }
}
```

## 工具接口

### execute

在沙箱中执行 bash 命令。

**参数：**
- `command` (string, 必需): 要执行的命令
- `workingDirectory` (string, 可选): 工作目录
- `timeout` (integer, 可选): 超时时间（毫秒）
- `environment` (object, 可选): 额外的环境变量
- `sessionId` (string, 可选): 会话 ID

**返回：**
```json
{
  "success": true,
  "stdout": "...",
  "stderr": "",
  "exitCode": 0,
  "executionTimeMs": 45,
  "sessionId": "sess_xxx",
  "workingDirectory": "/tmp/sandbox/...",
  "blocked": false
}
```

### create_session

创建新的沙箱会话。

**参数：**
- `workingDirectory` (string, 可选): 初始工作目录
- `environment` (object, 可选): 初始环境变量
- `name` (string, 可选): 会话名称

### close_session

关闭指定会话。

**参数：**
- `sessionId` (string, 必需): 会话 ID
- `cleanup` (boolean, 可选): 是否清理会话目录，默认 true

### get_session_info

获取会话详细信息。

**参数：**
- `sessionId` (string, 必需): 会话 ID

### list_sessions

列出所有活跃会话。

## 安全机制

### 白名单

只允许以下类型的命令：

- **文件操作**: ls, cat, head, tail, mkdir, cp, mv, rm, find 等
- **文本处理**: grep, sed, awk, cut, sort, uniq, wc 等
- **开发工具**: git, mvn, npm, node, python, java 等
- **网络工具**: curl, wget
- **系统查询**: pwd, whoami, uname, ps, df 等

### 黑名单

阻止以下类型的命令：

- 文件系统破坏：`rm -rf /`, `dd`, `mkfs` 等
- 权限提升：`sudo`, `su`, `chmod 777` 等
- 网络攻击：`nc -e`, `nmap` 等
- 系统修改：`systemctl`, `crontab` 等
- 敏感信息读取：`/etc/passwd`, `/etc/shadow` 等

### 资源限制

- CPU 时间：10-120 秒（动态计算）
- 内存：64MB-1GB（动态计算）
- 文件大小：最大 100MB
- 进程数：最大 64
- 打开文件数：最大 256
- 命令超时：5-60 秒

## 开发

```bash
# 安装依赖
npm install

# 开发模式
npm run dev

# 构建
npm run build

# 运行测试
npm test

# 测试覆盖率
npm run test:coverage
```

## 已知限制

| 限制 | 说明 |
|------|------|
| Windows ulimit 不可用 | Windows 不支持 ulimit，仅使用进程超时 |
| chroot 需要 root | Linux 隔离需要权限 |
| 网络隔离缺失 | 当前版本未实现网络隔离 |
| 符号链接逃逸 | 可能通过符号链接访问外部文件 |

## License

MIT
