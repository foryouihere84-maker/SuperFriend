# Agent Skills 协议实现规范

## 概述

本规范定义了 SuperFriend 如何完全符合 [Agent Skills 官方协议](https://agentskills.io/specification)，同时兼容市面上主流 skills（Anthropic Skills、Pi Skills、OpenClaw Skills）。

## 1. 目录结构（标准）

```
skills/
├── system/                          # 系统内置技能
│   ├── minimax-docx/
│   │   ├── SKILL.md                 # 必需：技能定义文件
│   │   ├── scripts/                 # 可选：脚本目录
│   │   │   ├── create.py
│   │   │   ├── edit.py
│   │   │   └── validate.py
│   │   ├── references/              # 可选：参考文档
│   │   │   └── api-reference.md
│   │   └── assets/                  # 可选：资源文件
│   │       └── template.json
│   └── minimax-pdf/
│       └── SKILL.md
└── users/                           # 用户自定义技能
    └── {user_id}/
        └── my-skill/
            └── SKILL.md
```

## 2. SKILL.md 格式（严格符合协议）

```yaml
---
# === Agent Skills 协议标准字段 ===
name: my-skill                       # 必需：技能名称
description: |                       # 必需：技能描述
  详细描述技能功能和何时使用。
  描述决定 LLM 何时加载此技能，请具体。
license: MIT                         # 可选：许可证
compatibility: "Python 3.8+"         # 可选：环境要求
allowed-tools: read write bash       # 可选：预批准工具列表
disable-model-invocation: false      # 可选：禁用自动调用

# === metadata（协议标准字段，存放扩展信息）===
metadata:
  version: "1.0.0"
  author: "Your Name"
  tags: [docx, document, word]
  
  # SuperFriend 扩展：执行器配置
  executor:
    type: script                     # script | command
    runtime: python                  # python | node | bash | dotnet
    entry: scripts/main.py           # 入口脚本（相对路径）
    args-format: kebab               # kebab | equals | short
    timeout: 60000
---

# My Skill Title

技能使用说明，LLM 会读取此内容来理解如何使用技能。

## Quick Start

```bash
./scripts/main.py --input input.txt --output output.docx
```

## Commands

### Create
```bash
./scripts/create.py --title "My Doc" --output output.docx
```

## References

- [API Reference](references/api-reference.md)
- [Examples](references/examples.md)
```

## 3. 技能发现流程

```
1. 启动时扫描技能目录
   ├── 扫描 skills/system/*/
   ├── 扫描 skills/users/{user_id}/*/
   └── 查找每个目录下的 SKILL.md

2. 解析 SKILL.md
   ├── 提取 YAML frontmatter
   ├── 验证 name 和 description
   └── 缓存技能元数据

3. LLM 上下文注入
   ├── 只注入 name + description（渐进式加载）
   └── 按需加载完整 SKILL.md 内容
```

## 4. 技能执行流程

```
LLM 选择技能
    ↓
调用 run_skill_script 工具
    ↓
参数: skill_name, script_name, parameters
    ↓
SkillRegistry.getSkill(skill_name)
    ├── 查找技能目录
    └── 读取 SKILL.md

SkillScriptRunner.executeScript()
    ├── 解析 metadata.executor 配置
    ├── 确定脚本路径: {skill_dir}/{entry}
    ├── 创建唯一输出目录: /tmp/skill_output/{uuid}/
    ├── 注入环境变量:
    │   ├── SKILL_DIR={skill_dir}
    │   ├── SKILL_OUTPUT_DIR={output_dir}
    │   └── SKILL_SESSION_ID={session_id}
    └── 通过 sandbox 执行脚本

BashSandbox.execute()
    ├── 在隔离环境中执行
    └── 返回 stdout/stderr

输出文件检测（O(1) 策略）
    ├── 解析 stdout JSON 中的 outputFile
    └── 或匹配 "Created: /path/to/file" 模式

文件发送
    ├── 读取文件内容
    ├── Base64 编码
    ├── SSE 发送给前端
    └── 清理临时文件
```

## 5. 输出文件处理

### 5.1 固定输出目录

所有技能生成的文件统一输出到：
```
/tmp/skill_output/{session_id}/
```

环境变量自动注入：
- `SKILL_OUTPUT_DIR` - 输出目录
- `SKILL_SESSION_ID` - 会话 ID

### 5.2 脚本输出规范

**推荐：JSON 格式输出**
```json
{
  "success": true,
  "outputFile": "/tmp/skill_output/{session_id}/output.docx",
  "message": "文档生成成功"
}
```

**兼容：文本格式输出**
```
Created: /tmp/skill_output/{session_id}/output.docx
Output file: /tmp/skill_output/{session_id}/output.docx
```

### 5.3 文件生命周期

```
1. 脚本执行前
   └── 创建输出目录: /tmp/skill_output/{session_id}/

2. 脚本执行中
   └── 脚本写入文件到 $SKILL_OUTPUT_DIR

3. 脚本执行后
   ├── 检测输出文件
   ├── 发送给前端
   └── 注册清理任务

4. 清理时机
   ├── 文件发送成功后立即清理
   ├── 或会话超时后清理（默认 30 分钟）
   └── 或应用关闭时清理
```

## 6. 路径解析规则

### 6.1 相对路径解析

LLM 在 SKILL.md 中看到：
```markdown
See [API Reference](references/api-reference.md)
```

系统解析为：
```
{skill_dir}/references/api-reference.md
```

### 6.2 脚本路径解析

SKILL.md 中：
```yaml
metadata:
  executor:
    entry: scripts/main.py
```

系统解析为：
```
{skill_dir}/scripts/main.py
```

### 6.3 输出路径解析

脚本输出：
```json
{"outputFile": "output.docx"}
```

系统解析为：
```
{SKILL_OUTPUT_DIR}/output.docx
```

## 7. 兼容性矩阵

| 来源 | 协议版本 | 兼容性 |
|------|----------|--------|
| Agent Skills 官方 | 1.0 | ✅ 完全兼容 |
| Anthropic Skills | 1.0 | ✅ 完全兼容 |
| Pi Skills | 1.0 | ✅ 完全兼容 |
| OpenClaw Skills | 1.0 | ✅ 完全兼容 |
| Claude Code Skills | 1.0 | ✅ 完全兼容 |

## 8. 验证规则

### 8.1 名称验证
- 长度：1-64 字符
- 字符：小写字母、数字、连字符
- 格式：不能以连字符开头/结尾，不能有连续连字符
- 匹配：必须与父目录名一致

### 8.2 描述验证
- 必需：不能为空
- 长度：最大 1024 字符

### 8.3 兼容性验证
- 长度：最大 500 字符

## 9. 错误处理

| 错误 | 处理 |
|------|------|
| SKILL.md 不存在 | 跳过该目录，记录警告 |
| name 缺失 | 不加载该技能 |
| description 缺失 | 不加载该技能 |
| name 格式无效 | 加载但记录警告 |
| name 与目录不匹配 | 加载但记录警告 |
| 脚本执行失败 | 返回错误，清理临时文件 |
| 输出文件不存在 | 记录警告，返回执行结果 |

## 10. 示例：完整技能

```
minimax-docx/
├── SKILL.md
├── scripts/
│   ├── create.py
│   ├── edit.py
│   └── validate.py
├── references/
│   └── openxml-reference.md
└── assets/
    └── templates/
        ├── report.json
        └── letter.json
```

**SKILL.md:**
```yaml
---
name: minimax-docx
description: |
  Create, edit, and format DOCX documents using OpenXML.
  Use when user wants to generate Word documents, reports, or letters.
license: MIT
compatibility: "Python 3.8+ or .NET 8.0"
allowed-tools: read write bash
metadata:
  version: "2.0.0"
  author: "MiniMaxAI"
  tags: [docx, word, document]
  executor:
    type: script
    runtime: python
    entry: scripts/create.py
    args-format: kebab
    timeout: 120000
---

# minimax-docx

Professional DOCX document generation and manipulation.

## Quick Start

```bash
./scripts/create.py --title "Report" --output report.docx
```

## Commands

| Command | Script | Description |
|---------|--------|-------------|
| create | scripts/create.py | Create new document |
| edit | scripts/edit.py | Edit existing document |
| validate | scripts/validate.py | Validate document |

## Output Format

Scripts output JSON for automatic file detection:

```json
{
  "success": true,
  "outputFile": "/path/to/output.docx",
  "message": "Document created"
}
```
```
