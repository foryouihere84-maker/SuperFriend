# Excel 工具使用指南

## 📋 功能概述

这是一个功能完整的 Excel 文件处理工具，提供以下功能：

### ✅ 支持的功能

1. **读取 Excel 文件** - 以表格形式查看内容
2. **读取为 JSON 格式** - 将 Excel 数据转换为结构化格式
3. **写入 Excel 文件** - 创建新文件或覆盖现有文件
4. **追加数据** - 在现有文件末尾添加数据
5. **更新单元格** - 修改指定单元格的值
6. **获取文件信息** - 查看工作表列表、行列数等
7. **创建模板** - 生成带表头的 Excel 模板

---

## 🔧 可用工具列表

### 1. `read_excel` - 读取 Excel 文件

```python
# 读取第一个工作表
read_excel(file_path="C:/data/test.xlsx")

# 读取指定工作表
read_excel(file_path="C:/data/test.xlsx", sheet_name="Sheet1")
```

**返回格式：** 表格形式的文本内容

---

### 2. `read_excel_as_json` - 读取为 JSON 格式

```python
# 将第一行作为表头，其余行作为数据
read_excel_as_json(file_path="C:/data/test.xlsx")
```

**返回格式：** JSON 数组，每个对象代表一行数据

---

### 3. `write_excel` - 写入 Excel 文件

```python
# 方式 1: 使用 Python 列表格式（推荐）
write_excel(
    file_path="C:/data/output.xlsx",
    data="[['姓名', '年龄', '城市'], ['张三', 25, '北京'], ['李四', 30, '上海']]",
    sheet_name="Sheet1"
)

# 方式 2: 使用 CSV 格式（制表符分隔）
write_excel(
    file_path="C:/data/output.xlsx",
    data="姓名\t年龄\t城市\n张三\t25\t北京\n李四\t30\t上海",
    sheet_name="Sheet1"
)
```

**注意：** 如果文件已存在会被覆盖

---

### 4. `append_to_excel` - 追加数据到 Excel

```python
# 追加到新行
append_to_excel(
    file_path="C:/data/test.xlsx",
    data="[['王五', 28, '广州'], ['赵六', 35, '深圳']]"
)
```

---

### 5. `update_excel_cell` - 更新单元格

```python
# 更新 A1 单元格
update_excel_cell(
    file_path="C:/data/test.xlsx",
    cell_address="A1",
    value="新的值"
)

# 更新指定工作表的单元格
update_excel_cell(
    file_path="C:/data/test.xlsx",
    cell_address="B2",
    value="30",
    sheet_name="Sheet1"
)
```

---

### 6. `get_excel_info` - 获取 Excel 文件信息

```python
get_excel_info(file_path="C:/data/test.xlsx")
```

**返回内容：**
- 文件大小
- 工作表数量
- 每个工作表的名称和行列数

---

### 7. `create_excel_template` - 创建 Excel 模板

```python
# 创建带表头的模板
create_excel_template(
    file_path="C:/data/template.xlsx",
    headers="姓名，年龄，邮箱，电话",
    sheet_name="员工信息"
)
```

**特点：** 表头会自动加粗显示

---

## 💡 使用示例

### 示例 1：创建员工信息表

```python
# 创建模板
create_excel_template(
    file_path="C:/employees.xlsx",
    headers="工号，姓名，部门，入职日期"
)

# 添加数据
append_to_excel(
    file_path="C:/employees.xlsx",
    data="""[
        ['001', '张三', '技术部', '2024-01-15'],
        ['002', '李四', '市场部', '2024-02-20'],
        ['003', '王五', '人事部', '2024-03-10']
    ]"""
)
```

---

### 示例 2：读取并分析数据

```python
# 查看文件信息
get_excel_info(file_path="C:/sales_data.xlsx")

# 读取数据
read_excel(file_path="C:/sales_data.xlsx")

# 读取为 JSON 格式便于处理
read_excel_as_json(file_path="C:/sales_data.xlsx")
```

---

### 示例 3：更新数据

```python
# 更新某个单元格
update_excel_cell(
    file_path="C:/inventory.xlsx",
    cell_address="B5",
    value="100"
)

# 追加新库存记录
append_to_excel(
    file_path="C:/inventory.xlsx",
    data="[['产品 C', 50, '仓库 A'], ['产品 D', 75, '仓库 B']]"
)
```

---

## ⚠️ 注意事项

1. **文件格式支持：**
   - ✅ 支持 `.xlsx` 格式（Excel 2007+）
   - ✅ 支持 `.xlsm` 格式（启用宏的工作簿）
   - ❌ 不支持 `.xls` 格式（旧版 Excel）

2. **数据格式要求：**
   - `write_excel` 和 `append_to_excel` 接受两种格式：
     - Python 列表的字符串表示：`[['A1','B1'],['A2','B2']]`
     - CSV 格式（制表符或逗号分隔）

3. **文件路径：**
   - 使用正斜杠 `/` 或双反斜杠 `\\`
   - 例如：`C:/data/file.xlsx` 或 `C:\\data\\file.xlsx`

4. **工作表名称：**
   - 如果不指定 `sheet_name`，默认操作第一个工作表
   - 工作表名称不能包含字符：`[]:*?/\`

---

## 🎯 最佳实践

### ✅ 推荐做法

1. **使用列表格式写入数据** - 更清晰、不易出错
```python
data = "[['标题 1', '标题 2'], ['数据 1', '数据 2']]"
```

2. **先创建模板再填充数据** - 适合批量导入场景
```python
create_excel_template(file_path="template.xlsx", headers="字段 1，字段 2")
append_to_excel(file_path="template.xlsx", data=your_data)
```

3. **使用 JSON 格式读取** - 便于后续数据处理
```python
json_data = read_excel_as_json(file_path="data.xlsx")
```

### ❌ 避免做法

1. 不要在文件打开时写入（可能导致冲突）
2. 不要一次写入过多数据（建议分批处理）
3. 不要混用不同格式的 Excel 文件

---

## 🔍 故障排除

### 问题 1：提示"文件不存在"
**解决方案：** 检查文件路径是否正确，确保使用绝对路径

### 问题 2：提示"不支持的文件格式"
**解决方案：** 确认文件是 `.xlsx` 或 `.xlsm` 格式，不是 `.xls`

### 问题 3：中文乱码
**解决方案：** openpyxl 原生支持 UTF-8，不会出现乱码问题

### 问题 4：追加数据后看不到
**解决方案：** 确认是否选择了正确的工作表

---

## 📦 依赖安装

如果缺少 openpyxl 库，运行以下命令安装：

```bash
pip install openpyxl
```

或使用项目中的 requirements.txt：

```bash
cd python_scripts
pip install -r requirements.txt
```

---

## 📝 版本信息

- **当前版本：** 1.0.0
- **openpyxl 版本：** 3.1.5
- **Python 版本要求：** Python 3.7+

---

## 🎉 快速开始

1. 确保已安装 openpyxl
2. MCP 配置已自动添加到 `mcp-servers-small-config.json`
3. 直接使用上述工具即可操作 Excel 文件

**测试脚本：** 运行 `python test_excel.py` 可以验证所有功能是否正常工作
