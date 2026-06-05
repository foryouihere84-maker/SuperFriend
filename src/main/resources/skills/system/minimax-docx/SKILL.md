---
name: minimax-docx
license: MIT
metadata:
  version: "2.0.0"
  category: document-processing
  author: MiniMaxAI
description: >
  Professional DOCX document creation, editing, and formatting using OpenXML SDK (.NET).
  Use this skill whenever the user wants to produce, modify, or format a Word document.
  Supports: create new documents, edit/fill existing documents, apply templates.
triggers:
  - Word
  - docx
  - document
  - 文档
  - Word文档
  - 报告
  - 合同
  - 公文
  - 排版
  - 套模板
# 执行器配置：使用命令模板执行 dotnet CLI 项目
executor:
  type: command
  command-template: 'dotnet run --project "{skill_path}/scripts/dotnet/MiniMaxAIDocx.Cli" -- {script_name} {args}'
  args-format: kebab
  needs-build: false
  environment:
    DOTNET_CLI_UI_LANGUAGE: en
  platforms:
    windows:
      command-template: 'dotnet run --project "{skill_path}\scripts\dotnet\MiniMaxAIDocx.Cli" -- {script_name} {args}'
    linux:
      environment:
        DOTNET_CLI_UI_LANGUAGE: en
    macos:
      environment:
        DOTNET_CLI_UI_LANGUAGE: en
---

# minimax-docx

Create, edit, and format DOCX documents via CLI tools built on OpenXML SDK (.NET).

## ⚡ Quick Start

**IMPORTANT: Use `run_skill_script` tool to execute CLI commands. Do NOT write C# code manually.**

```
run_skill_script(
  skill_name="minimax-docx",
  script_name="create",  // CLI command: create, edit, apply-template, validate, analyze
  parameters={...}       // Command-specific parameters
)
```

## ⚠️ CRITICAL WARNING

**script_name 参数必须是 CLI 子命令，不能是脚本文件路径！**

| ✅ 正确用法 | ❌ 错误用法 |
|------------|------------|
| `script_name="create"` | `script_name="scripts/docx_preview.sh"` |
| `script_name="edit replace-text"` | `script_name="doc_to_docx.sh"` |
| `script_name="apply-template"` | `script_name="setup.sh"` |
| `script_name="validate"` | 任何以 `.sh` 结尾的值 |

**可用的 CLI 子命令（script_name 的有效值）：**
- `create` - 创建新文档
- `edit replace-text` - 替换文本
- `edit fill-placeholders` - 填充占位符
- `apply-template` - 应用模板样式
- `validate` - 验证文档
- `analyze` - 分析文档结构
- `diff` - 对比文档差异
- `merge-runs` - 合并文本块
- `fix-order` - 修复元素顺序

## 📋 Commands

### Create Document
```
run_skill_script(
  skill_name="minimax-docx",
  script_name="create",
  parameters={
    "output": "report.docx",
    "title": "Document Title",
    "type": "report",        // report, letter, memo, academic
    "author": "Author Name",
    "page-size": "a4",       // letter, a4, legal, a3
    "page-numbers": true,
    "toc": false
  }
)
```

### Edit Document
```
run_skill_script(
  skill_name="minimax-docx",
  script_name="edit replace-text",
  parameters={
    "input": "input.docx",
    "output": "output.docx",
    "search": "OLD_TEXT",
    "replace": "NEW_TEXT"
  }
)
```

### Fill Placeholders
```
run_skill_script(
  skill_name="minimax-docx",
  script_name="edit fill-placeholders",
  parameters={
    "input": "template.docx",
    "output": "filled.docx",
    "data": "{\"name\":\"John\",\"date\":\"2025-04-23\"}"
  }
)
```

### Apply Template
```
run_skill_script(
  skill_name="minimax-docx",
  script_name="apply-template",
  parameters={
    "input": "source.docx",
    "template": "template.docx",
    "output": "styled.docx"
  }
)
```

### Validate Document
```
run_skill_script(
  skill_name="minimax-docx",
  script_name="validate",
  parameters={"input": "document.docx"}
)
```

## 📝 Workflow

1. **Create**: Use `create` command with title, type, and options
2. **Edit**: Use `edit replace-text` or `edit fill-placeholders` for modifications
3. **Format**: Use `apply-template` to apply professional styles
4. **Validate**: Run `validate` after operations to ensure document integrity

## 📄 Content JSON (Optional)

For structured content, create a JSON file and pass via `--content-json`:

```json
[
  {"type": "heading", "text": "Introduction", "level": 1},
  {"type": "paragraph", "text": "First paragraph content."},
  {"type": "heading", "text": "Details", "level": 2},
  {"type": "paragraph", "text": "More details here."},
  {"type": "pagebreak"},
  {"type": "heading", "text": "Conclusion", "level": 1}
]
```

## 🎯 Decision Guide

| User Request | Command |
|--------------|---------|
| "Create a document/report" | `create` |
| "Fill in this template" | `edit fill-placeholders` |
| "Replace text in document" | `edit replace-text` |
| "Apply formatting/template" | `apply-template` |
| "Check document validity" | `validate` |
