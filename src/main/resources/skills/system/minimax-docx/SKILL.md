---
name: minimax-docx
license: MIT
metadata:
<<<<<<< HEAD
  version: "2.0.0"
  category: document-processing
  author: MiniMaxAI
description: >
  Professional DOCX document creation, editing, and formatting using OpenXML SDK (.NET).
  Use this skill whenever the user wants to produce, modify, or format a Word document.
  Supports: create new documents, edit/fill existing documents, apply templates.
=======
  version: "1.0.0"
  category: document-processing
  author: MiniMaxAI
  sources:
    - "ECMA-376 Office Open XML File Formats"
    - "GB/T 9704-2012 Layout Standard for Official Documents"
    - "IEEE / ACM / APA / MLA / Chicago / Turabian Style Guides"
    - "Springer LNCS / Nature / HBR Document Templates"
description: >
  Professional DOCX document creation, editing, and formatting using OpenXML SDK (.NET).
  Three pipelines: (A) create new documents from scratch, (B) fill/edit content in existing
  documents, (C) apply template formatting with XSD validation gate-check.
  MUST use this skill whenever the user wants to produce, modify, or format a Word document —
  including when they say "write a report", "draft a proposal", "make a contract",
  "fill in this form", "reformat to match this template", or any task whose final output
  is a .docx file. Even if the user doesn't mention "docx" explicitly, if the task
  implies a printable/formal document, use this skill.
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
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
<<<<<<< HEAD
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
=======
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
---

# minimax-docx

<<<<<<< HEAD
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
=======
Create, edit, and format DOCX documents via CLI tools or direct C# scripts built on OpenXML SDK (.NET).

## Setup

**First time:** `bash scripts/setup.sh` (or `powershell scripts/setup.ps1` on Windows, `--minimal` to skip optional deps).

**First operation in session:** `scripts/env_check.sh` — do not proceed if `NOT READY`. (Skip on subsequent operations within the same session.)

## Quick Start: Direct C# Path

When the task requires structural document manipulation (custom styles, complex tables, multi-section layouts, headers/footers, TOC, images), write C# directly instead of wrestling with CLI limitations. Use this scaffold:

```csharp
// File: scripts/dotnet/task.csx  (or a new .cs in a Console project)
// dotnet run --project scripts/dotnet/MiniMaxAIDocx.Cli -- run-script task.csx
#r "nuget: DocumentFormat.OpenXml, 3.2.0"

using DocumentFormat.OpenXml;
using DocumentFormat.OpenXml.Packaging;
using DocumentFormat.OpenXml.Wordprocessing;

using var doc = WordprocessingDocument.Create("output.docx", WordprocessingDocumentType.Document);
var mainPart = doc.AddMainDocumentPart();
mainPart.Document = new Document(new Body());

// --- Your logic here ---
// Read the relevant Samples/*.cs file FIRST for tested patterns.
// See Samples/ table in References section below.
```

**Before writing any C#, read the relevant `Samples/*.cs` file** — they contain compilable, SDK-version-verified patterns. The Samples table in the References section below maps topics to files.

## CLI shorthand

All CLI commands below use `$CLI` as shorthand for:
```bash
dotnet run --project scripts/dotnet/MiniMaxAIDocx.Cli --
```

## Pipeline routing

Route by checking: does the user have an input .docx file?

```
User task
├─ No input file → Pipeline A: CREATE
│   signals: "write", "create", "draft", "generate", "new", "make a report/proposal/memo"
│   → Read references/scenario_a_create.md
│
└─ Has input .docx
    ├─ Replace/fill/modify content → Pipeline B: FILL-EDIT
    │   signals: "fill in", "replace", "update", "change text", "add section", "edit"
    │   → Read references/scenario_b_edit_content.md
    │
    └─ Reformat/apply style/template → Pipeline C: FORMAT-APPLY
        signals: "reformat", "apply template", "restyle", "match this format", "套模板", "排版"
        ├─ Template is pure style (no content) → C-1: OVERLAY (apply styles to source)
        └─ Template has structure (cover/TOC/example sections) → C-2: BASE-REPLACE
            (use template as base, replace example content with user content)
        → Read references/scenario_c_apply_template.md
```

If the request spans multiple pipelines, run them sequentially (e.g., Create then Format-Apply).

## Pre-processing

Convert `.doc` → `.docx` if needed: `scripts/doc_to_docx.sh input.doc output_dir/`

Preview before editing (avoids reading raw XML): `scripts/docx_preview.sh document.docx`

Analyze structure for editing scenarios: `$CLI analyze --input document.docx`

## Scenario A: Create

Read `references/scenario_a_create.md`, `references/typography_guide.md`, and `references/design_principles.md` first. Pick an aesthetic recipe from `Samples/AestheticRecipeSamples.cs` that matches the document type — do not invent formatting values. For CJK, also read `references/cjk_typography.md`.

**Choose your path:**
- **Simple** (plain text, minimal formatting): use CLI — `$CLI create --type report --output out.docx --config content.json`
- **Structural** (custom styles, multi-section, TOC, images, complex tables): write C# directly. Read the relevant `Samples/*.cs` first.

CLI options: `--type` (report|letter|memo|academic), `--title`, `--author`, `--page-size` (letter|a4|legal|a3), `--margins` (standard|narrow|wide), `--header`, `--footer`, `--page-numbers`, `--toc`, `--content-json`.

Then run the **validation pipeline** (below).

## Scenario B: Edit / Fill

Read `references/scenario_b_edit_content.md` first. Preview → analyze → edit → validate.

**Choose your path:**
- **Simple** (text replacement, placeholder fill): use CLI subcommands.
- **Structural** (add/reorganize sections, modify styles, manipulate tables, insert images): write C# directly. Read `references/openxml_element_order.md` and the relevant `Samples/*.cs`.

Available CLI edit subcommands:
- `replace-text --find "X" --replace "Y"`
- `fill-placeholders --data '{"key":"value"}'`
- `fill-table --data table.json`
- `insert-section`, `remove-section`, `update-header-footer`

```bash
$CLI edit replace-text --input in.docx --output out.docx --find "OLD" --replace "NEW"
$CLI edit fill-placeholders --input in.docx --output out.docx --data '{"name":"John"}'
```

Then run the **validation pipeline**. Also run diff to verify minimal changes:
```bash
$CLI diff --before in.docx --after out.docx
```

## Scenario C: Apply Template

Read `references/scenario_c_apply_template.md` first. Preview and analyze both source and template.

```bash
$CLI apply-template --input source.docx --template template.docx --output out.docx
```

For complex template operations (multi-template merge, per-section headers/footers, style merging), write C# directly — see Critical Rules below for required patterns.

Run the **validation pipeline**, then the **hard gate-check**:
```bash
$CLI validate --input out.docx --gate-check assets/xsd/business-rules.xsd
```
Gate-check is a **hard requirement**. Do NOT deliver until it passes. If it fails: diagnose, fix, re-run.

Also diff to verify content preservation: `$CLI diff --before source.docx --after out.docx`

## Validation pipeline

Run after every write operation. For Scenario C the full pipeline is **mandatory**; for A/B it is **recommended** (skip only if the operation was trivially simple).

```bash
$CLI merge-runs --input doc.docx                                    # 1. consolidate runs
$CLI validate --input doc.docx --xsd assets/xsd/wml-subset.xsd     # 2. XSD structure
$CLI validate --input doc.docx --business                           # 3. business rules
```

If XSD fails, auto-repair and retry:
```bash
$CLI fix-order --input doc.docx
$CLI validate --input doc.docx --xsd assets/xsd/wml-subset.xsd
```

If XSD still fails, fall back to business rules + preview:
```bash
$CLI validate --input doc.docx --business
scripts/docx_preview.sh doc.docx
# Verify: font contamination=0, table count correct, drawing count correct, sectPr count correct
```

Final preview: `scripts/docx_preview.sh doc.docx`

## Critical rules

These prevent file corruption — OpenXML is strict about element ordering.

**Element order** (properties always first):

| Parent | Order |
|--------|-------|
| `w:p`  | `pPr` → runs |
| `w:r`  | `rPr` → `t`/`br`/`tab` |
| `w:tbl`| `tblPr` → `tblGrid` → `tr` |
| `w:tr` | `trPr` → `tc` |
| `w:tc` | `tcPr` → `p` (min 1 `<w:p/>`) |
| `w:body` | block content → `sectPr` (LAST child) |

**Direct format contamination:** When copying content from a source document, inline `rPr` (fonts, color) and `pPr` (borders, shading, spacing) override template styles. Always strip direct formatting — keep only `pStyle` reference and `t` text. Clean tables too (including `pPr/rPr` inside cells).

**Track changes:** `<w:del>` uses `<w:delText>`, never `<w:t>`. `<w:ins>` uses `<w:t>`, never `<w:delText>`.

**Font size:** `w:sz` = points × 2 (12pt → `sz="24"`). Margins/spacing in DXA (1 inch = 1440, 1cm ≈ 567).

**Heading styles MUST have OutlineLevel:** When defining heading styles (Heading1, ThesisH1, etc.), always include `new OutlineLevel { Val = N }` in `StyleParagraphProperties` (H1→0, H2→1, H3→2). Without this, Word sees them as plain styled text — TOC and navigation pane won't work.

**Multi-template merge:** When given multiple template files (font, heading, breaks), read `references/scenario_c_apply_template.md` section "Multi-Template Merge" FIRST. Key rules:
- Merge styles from all templates into one styles.xml. Structure (sections/breaks) comes from the breaks template.
- Each content paragraph must appear exactly ONCE — never duplicate when inserting section breaks.
- NEVER insert empty/blank paragraphs as padding or section separators. Output paragraph count must equal input. Use section break properties (`w:sectPr` inside `w:pPr`) and style spacing (`w:spacing` before/after) for visual separation.
- Insert oddPage section breaks before EVERY chapter heading, not just the first. Even if a chapter has dual-column content, it MUST start with oddPage; use a second continuous break after the heading for column switching.
- Dual-column chapters need THREE section breaks: (1) oddPage in preceding para's pPr, (2) continuous+cols=2 in the chapter HEADING's pPr, (3) continuous+cols=1 in the last body para's pPr to revert.
- Copy `titlePg` settings from the breaks template for EACH section. Abstract and TOC sections typically need `titlePg=true`.

**Multi-section headers/footers:** Templates with 10+ sections (e.g., Chinese thesis) have DIFFERENT headers/footers per section (Roman vs Arabic page numbers, different header text per zone). Rules:
- Use C-2 Base-Replace: copy the TEMPLATE as output base, then replace body content. This preserves all sections, headers, footers, and titlePg settings automatically.
- NEVER recreate headers/footers from scratch — copy template header/footer XML byte-for-byte.
- NEVER add formatting (borders, alignment, font size) not present in the template header XML.
- Non-cover sections MUST have header/footer XML files (at least empty header + page number footer).
- See `references/scenario_c_apply_template.md` section "Multi-Section Header/Footer Transfer".

## References

Load as needed — don't load all at once. Pick the most relevant files for the task.

**The C# samples and design references below are the project's knowledge base ("encyclopedia").** When writing OpenXML code, ALWAYS read the relevant sample file first — it contains compilable, SDK-version-verified patterns that prevent common errors. When making aesthetic decisions, read the design principles and recipe files — they encode tested, harmonious parameter sets from authoritative sources (IEEE, ACM, APA, Nature, etc.), not guesses.

### Scenario guides (read first for each pipeline)

| File | When |
|------|------|
| `references/scenario_a_create.md` | Pipeline A: creating from scratch |
| `references/scenario_b_edit_content.md` | Pipeline B: editing existing content |
| `references/scenario_c_apply_template.md` | Pipeline C: applying template formatting |

### C# code samples (compilable, heavily commented — read when writing code)

| File | Topic |
|------|-------|
| `Samples/DocumentCreationSamples.cs` | Document lifecycle: create, open, save, streams, doc defaults, settings, properties, page setup, multi-section |
| `Samples/StyleSystemSamples.cs` | Styles: Normal/Heading chain, character/table/list styles, DocDefaults, latentStyles, CJK 公文, APA 7th, import, resolve inheritance |
| `Samples/CharacterFormattingSamples.cs` | RunProperties: fonts, size, bold/italic, all underlines, color, highlight, strike, sub/super, caps, spacing, shading, border, emphasis marks |
| `Samples/ParagraphFormattingSamples.cs` | ParagraphProperties: justification, indentation, line/paragraph spacing, keep/widow, outline level, borders, tabs, numbering, bidi, frame |
| `Samples/TableSamples.cs` | Tables: borders, grid, cell props, margins, row height, header repeat, merge (H+V), nested, floating, three-line 三线表, zebra striping |
| `Samples/HeaderFooterSamples.cs` | Headers/footers: page numbers, "Page X of Y", first/even/odd, logo image, table layout, 公文 "-X-", per-section |
| `Samples/ImageSamples.cs` | Images: inline, floating, text wrapping, border, alt text, in header/table, replace, SVG fallback, dimension calc |
| `Samples/ListAndNumberingSamples.cs` | Numbering: bullets, multi-level decimal, custom symbols, outline→headings, legal, Chinese 一/（一）/1./(1), restart/continue |
| `Samples/FieldAndTocSamples.cs` | Fields: TOC, SimpleField vs complex field, DATE/PAGE/REF/SEQ/MERGEFIELD/IF/STYLEREF, TOC styles |
| `Samples/FootnoteAndCommentSamples.cs` | Footnotes, endnotes, comments (4-file system), bookmarks, hyperlinks (internal + external) |
| `Samples/TrackChangesSamples.cs` | Revisions: insertions (w:t), deletions (w:delText!), formatting changes, accept/reject all, move tracking |
| `Samples/AestheticRecipeSamples.cs` | 13 aesthetic recipes from authoritative sources: ModernCorporate, AcademicThesis, ExecutiveBrief, ChineseGovernment (GB/T 9704), MinimalModern, IEEE Conference, ACM sigconf, APA 7th, MLA 9th, Chicago/Turabian, Springer LNCS, Nature, HBR — each with exact values from official style guides |

Note: `Samples/` path is relative to `scripts/dotnet/MiniMaxAIDocx.Core/`.

### Markdown references (read when you need specifications or design rules)

| File | When |
|------|------|
| `references/openxml_element_order.md` | XML element ordering rules (prevents corruption) |
| `references/openxml_units.md` | Unit conversion: DXA, EMU, half-points, eighth-points |
| `references/openxml_encyclopedia_part1.md` | Detailed C# encyclopedia: document creation, styles, character & paragraph formatting |
| `references/openxml_encyclopedia_part2.md` | Detailed C# encyclopedia: page setup, tables, headers/footers, sections, doc properties |
| `references/openxml_encyclopedia_part3.md` | Detailed C# encyclopedia: TOC, footnotes, fields, track changes, comments, images, math, numbering, protection |
| `references/typography_guide.md` | Font pairing, sizes, spacing, page layout, table design, color schemes |
| `references/cjk_typography.md` | CJK fonts, 字号 sizes, RunFonts mapping, GB/T 9704 公文 standard |
| `references/cjk_university_template_guide.md` | Chinese university thesis templates: numeric styleIds (1/2/3 vs Heading1), document zone structure (cover→abstract→TOC→body→references), font expectations, common mistakes |
| `references/design_principles.md` | **Aesthetic foundations**: 6 design principles (white space, contrast/scale, proximity, alignment, repetition, hierarchy) — teaches WHY, not just WHAT |
| `references/design_good_bad_examples.md` | **Good vs Bad comparisons**: 10 categories of typography mistakes with OpenXML values, ASCII mockups, and fixes |
| `references/track_changes_guide.md` | Revision marks deep dive |
| `references/troubleshooting.md` | **Symptom-driven fixes**: 13 common problems indexed by what you SEE (headings wrong, images missing, TOC broken, etc.) — search by symptom, find the fix |
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
