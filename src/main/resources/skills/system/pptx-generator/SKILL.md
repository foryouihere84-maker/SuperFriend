---
name: pptx-generator
description: "Generate, edit, and read PowerPoint presentations. Create from scratch with PptxGenJS, edit existing PPTX via XML workflows, or extract text with markitdown."
license: MIT
metadata:
  version: "2.0"
  category: productivity
executor:
  type: script
  args-format: kebab
---

# PPTX Generator & Editor

**IMPORTANT: Use `run_skill_script` tool to execute scripts.**

## ⚡ Quick Start

```
run_skill_script(
  skill_name="pptx-generator",
  script_name="scripts/create.js",
  parameters={"output": "presentation.pptx", "title": "My Presentation"}
)
```

## 📋 Commands

### Read/Analyze
```
run_skill_script(
  skill_name="pptx-generator",
  script_name="scripts/read.py",
  parameters={"input": "presentation.pptx"}
)
```

### Create from Scratch
```
run_skill_script(
  skill_name="pptx-generator",
  script_name="scripts/create.js",
  parameters={"output": "output.pptx", "slides": "slides.json"}
)
```

### Compile Slides
```
run_skill_script(
  skill_name="pptx-generator",
  script_name="scripts/compile.js",
  parameters={"slides_dir": "slides", "output": "output/presentation.pptx"}
)
```

## 🎨 Design System

| Item | Value |
|------|-------|
| **Dimensions** | 10" x 5.625" (LAYOUT_16x9) |
| **Colors** | 6-char hex without # (e.g., `"FF0000"`) |
| **English font** | Arial (default) |
| **Chinese font** | Microsoft YaHei |
| **Theme keys** | `primary`, `secondary`, `accent`, `light`, `bg` |

## 📝 Workflow

1. **Plan slides** - Classify each as Cover, TOC, Section Divider, Content, or Summary
2. **Create slide JS files** - One file per slide in `slides/` directory
3. **Compile** - Run `compile.js` to combine all slides
4. **QA** - Verify output

## 🎯 Task Routing

| Task | Approach |
|------|----------|
| Read/analyze | `read.py` (markitdown) |
| Create from scratch | `create.js` or `compile.js` |
| Edit template | XML unpack → edit → pack |

## 📦 Dependencies

- `pip install "markitdown[pptx]"` — text extraction
- `npm install pptxgenjs` — creating from scratch
