---
name: minimax-xlsx
description: "Open, create, read, analyze, edit, or validate Excel/spreadsheet files (.xlsx, .xlsm, .csv, .tsv). Use when the user asks to create, build, modify, analyze, read, validate, or format any Excel spreadsheet, financial model, pivot table, or tabular data file."
license: MIT
metadata:
  version: "2.0"
  category: productivity
executor:
  type: script
  args-format: kebab
---

# MiniMax XLSX Skill

**IMPORTANT: Use `run_skill_script` tool to execute scripts. Do NOT write Python code manually.**

## ⚡ Quick Start

```
run_skill_script(
  skill_name="minimax-xlsx",
  script_name="scripts/xlsx_reader.py",
  parameters={"input": "data.xlsx"}
)
```

## 📋 Commands

### Read/Analyze
```
run_skill_script(
  skill_name="minimax-xlsx",
  script_name="scripts/xlsx_reader.py",
  parameters={"input": "input.xlsx"}
)
```

### Create from Template
```
run_skill_script(
  skill_name="minimax-xlsx",
  script_name="scripts/xlsx_pack.py",
  parameters={"input_dir": "/tmp/work", "output": "output.xlsx"}
)
```

### Edit (Unpack → Edit → Pack)
```
# Step 1: Unpack
run_skill_script(
  skill_name="minimax-xlsx",
  script_name="scripts/xlsx_unpack.py",
  parameters={"input": "input.xlsx", "output_dir": "/tmp/work"}
)
# Step 2: Edit XML files via bash commands
# Step 3: Repack
run_skill_script(
  skill_name="minimax-xlsx",
  script_name="scripts/xlsx_pack.py",
  parameters={"input_dir": "/tmp/work", "output": "output.xlsx"}
)
```

### Validate Formulas
```
run_skill_script(
  skill_name="minimax-xlsx",
  script_name="scripts/formula_check.py",
  parameters={"input": "file.xlsx", "json": true}
)
```

### Add Column
```
run_skill_script(
  skill_name="minimax-xlsx",
  script_name="scripts/xlsx_add_column.py",
  parameters={
    "work_dir": "/tmp/work",
    "col": "G",
    "sheet": "Sheet1",
    "header": "% of Total",
    "formula": "=F{row}/$F$10",
    "numfmt": "0.0%"
  }
)
```

### Insert Row
```
run_skill_script(
  skill_name="minimax-xlsx",
  script_name="scripts/xlsx_insert_row.py",
  parameters={
    "work_dir": "/tmp/work",
    "at": 5,
    "sheet": "Budget",
    "text": {"A": "Utilities"},
    "values": {"B": 3000, "C": 3000}
  }
)
```

## 🎯 Task Routing

| Task | Scripts |
|------|---------|
| READ - analyze data | `xlsx_reader.py` |
| CREATE - new xlsx | `xlsx_pack.py` (after editing template) |
| EDIT - modify existing | `xlsx_unpack.py` → edit → `xlsx_pack.py` |
| VALIDATE - check formulas | `formula_check.py` |

## 📝 Key Rules

1. **Formula-First**: Every calculated cell MUST use an Excel formula
2. **EDIT → XML**: Never use openpyxl round-trip. Use unpack/edit/pack
3. **Always produce the output file**

## 🎨 Financial Color Standard

| Cell Role | Font Color | Hex |
|-----------|-----------|-----|
| Hard-coded input | Blue | `0000FF` |
| Formula result | Black | `000000` |
| Cross-sheet reference | Green | `00B050` |
