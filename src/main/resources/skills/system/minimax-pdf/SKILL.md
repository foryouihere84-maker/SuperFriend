---
name: minimax-pdf
description: >
  Use this skill when visual quality and design identity matter for a PDF.
  CREATE (generate from scratch), FILL (complete form fields), REFORMAT (apply design).
license: MIT
metadata:
  version: "2.0"
  category: document-generation
executor:
  type: command
  command-template: 'python -u "{skill_path}/scripts/{script_name}.py" {args}'
  args-format: kebab
  environment:
    PYTHONIOENCODING: utf-8
  platforms:
    windows:
      command-template: 'python -u "{skill_path}\scripts\{script_name}.py" {args}'
---

# minimax-pdf

**IMPORTANT: Use `run_skill_script` tool to execute scripts.**

## ⚡ Quick Start

```
run_skill_script(
  skill_name="minimax-pdf",
  script_name="scripts/make.sh",
  parameters={"action": "run", "title": "My Report", "type": "report", "out": "output.pdf"}
)
```

## 📋 Routes

| User Intent | Script | Example |
|-------------|--------|---------|
| Create new PDF | `make.sh run` | `run_skill_script(skill="minimax-pdf", script_name="scripts/make.sh", params={"action": "run", "title": "Report", "type": "report", "out": "report.pdf"})` |
| Fill form fields | `fill_write.py` | `run_skill_script(skill="minimax-pdf", script_name="scripts/fill_write.py", params={"input": "form.pdf", "out": "filled.pdf", "values": "{\"Name\":\"John\"}"})` |
| Inspect form | `fill_inspect.py` | `run_skill_script(skill="minimax-pdf", script_name="scripts/fill_inspect.py", params={"input": "form.pdf"})` |
| Reformat doc | `make.sh reformat` | `run_skill_script(skill="minimax-pdf", script_name="scripts/make.sh", params={"action": "reformat", "input": "source.md", "out": "output.pdf"})` |

## 🎨 Doc Types

`report` · `proposal` · `resume` · `portfolio` · `academic` · `general` · `minimal` · `stripe` · `diagonal` · `frame` · `editorial` · `magazine` · `darkroom` · `terminal` · `poster`

## 📝 Create Parameters

| Parameter | Description |
|-----------|-------------|
| `--title` | Document title |
| `--type` | Document type (report, proposal, resume, etc.) |
| `--author` | Author name |
| `--date` | Date string |
| `--accent` | Accent color (hex, e.g., #2D5F8A) |
| `--content` | JSON file with content |
| `--out` | Output file path |

## 🎯 Decision Guide

| User Request | Action |
|--------------|--------|
| "Create a PDF/report" | `make.sh run` |
| "Fill this form" | `fill_inspect.py` → `fill_write.py` |
| "Reformat this document" | `make.sh reformat` |
