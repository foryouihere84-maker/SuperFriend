#!/usr/bin/env python3
"""
Excel 文件操作工具 - 提供 Excel 文件的读写功能
使用 openpyxl 库处理 .xlsx 格式的 Excel 文件
"""

import asyncio
from pathlib import Path
from mcp.server.fastmcp import FastMCP
from openpyxl import Workbook, load_workbook
from openpyxl.utils import get_column_letter
from datetime import datetime

mcp = FastMCP("excel_tools")


@mcp.tool()
async def read_excel(file_path: str, sheet_name: str = None) -> str:
    """
    读取 Excel 文件内容
    
    参数:
        file_path: Excel 文件路径 (.xlsx 格式)
        sheet_name: 工作表名称，默认为第一个工作表
    
    返回:
        Excel 文件的内容（表格形式）
    """
    try:
        path = Path(file_path)
        if not path.exists():
            return f"错误：文件不存在：{file_path}"
        if not path.suffix.lower() in ['.xlsx', '.xlsm']:
            return f"错误：不支持的文件格式，仅支持 .xlsx/.xlsm 格式"
        
        # 加载工作簿
        wb = load_workbook(filename=str(path), data_only=True)
        
        # 选择工作表
        if sheet_name:
            if sheet_name not in wb.sheetnames:
                return f"错误：工作表 '{sheet_name}' 不存在。可用的工作表：{', '.join(wb.sheetnames)}"
            ws = wb[sheet_name]
        else:
            ws = wb.active
        
        # 读取数据
        result = []
        result.append(f"文件：{file_path}")
        result.append(f"工作表：{ws.title}")
        result.append(f"总行数：{ws.max_row}, 总列数：{ws.max_column}")
        result.append("=" * 80)
        
        # 读取所有行
        for row_idx, row in enumerate(ws.iter_rows(values_only=True), start=1):
            # 将 None 转换为空字符串
            cells = [str(cell) if cell is not None else "" for cell in row]
            result.append(" | ".join(cells))
        
        wb.close()
        return "\n".join(result)
    
    except PermissionError:
        return f"错误：没有权限访问文件：{file_path}"
    except Exception as e:
        return f"读取 Excel 失败：{str(e)}"


@mcp.tool()
async def read_excel_as_json(file_path: str, sheet_name: str = None) -> str:
    """
    读取 Excel 文件并转换为 JSON 格式（第一行为表头）
    
    参数:
        file_path: Excel 文件路径
        sheet_name: 工作表名称，默认为第一个工作表
    
    返回:
        JSON 格式的数据
    """
    try:
        path = Path(file_path)
        if not path.exists():
            return f"错误：文件不存在：{file_path}"
        
        wb = load_workbook(filename=str(path), data_only=True)
        
        # 选择工作表
        if sheet_name:
            if sheet_name not in wb.sheetnames:
                return f"错误：工作表 '{sheet_name}' 不存在"
            ws = wb[sheet_name]
        else:
            ws = wb.active
        
        # 获取表头（第一行）
        headers = []
        for cell in ws[1]:
            headers.append(str(cell.value) if cell.value is not None else "")
        
        # 读取数据行
        data_rows = []
        for row_idx, row in enumerate(ws.iter_rows(min_row=2, values_only=True), start=2):
            row_data = {}
            for col_idx, value in enumerate(row):
                if col_idx < len(headers):
                    row_data[headers[col_idx]] = value
            data_rows.append(row_data)
        
        wb.close()
        
        result = [
            f"文件：{file_path}",
            f"工作表：{ws.title}",
            f"字段：{', '.join(headers)}",
            f"记录数：{len(data_rows)}",
            "=" * 80,
            str(data_rows)
        ]
        
        return "\n".join(result)
    
    except Exception as e:
        return f"读取 Excel 为 JSON 失败：{str(e)}"


@mcp.tool()
async def write_excel(file_path: str, data: str, sheet_name: str = "Sheet1") -> str:
    """
    写入 Excel 文件（如果文件存在则覆盖）
    
    参数:
        file_path: Excel 文件路径
        data: 数据内容，支持两种格式：
              1. CSV 格式：每行用换行符分隔，每列用制表符或逗号分隔
              2. JSON 格式：Python 列表的字符串表示，如 [['A1','B1'],['A2','B2']]
        sheet_name: 工作表名称，默认"Sheet1"
    
    返回:
        操作结果
    """
    try:
        path = Path(file_path)
        
        # 解析数据
        rows = []
        
        # 尝试解析为 Python 列表
        try:
            import ast
            data_parsed = ast.literal_eval(data)
            if isinstance(data_parsed, list):
                rows = data_parsed
            else:
                return "错误：数据必须是列表格式，例如：[['A1','B1'],['A2','B2']]"
        except:
            # 如果不是列表格式，按 CSV 格式处理
            lines = data.strip().split('\n')
            for line in lines:
                # 优先使用制表符分隔，其次使用逗号
                if '\t' in line:
                    row = line.split('\t')
                elif ',' in line:
                    row = line.split(',')
                else:
                    row = [line]
                rows.append(row)
        
        if not rows:
            return "错误：数据为空"
        
        # 创建工作簿
        wb = Workbook()
        ws = wb.active
        ws.title = sheet_name
        
        # 写入数据
        for row_idx, row in enumerate(rows, start=1):
            for col_idx, value in enumerate(row, start=1):
                cell = ws.cell(row=row_idx, column=col_idx, value=value)
        
        # 确保父目录存在
        path.parent.mkdir(parents=True, exist_ok=True)
        
        # 保存文件
        wb.save(str(path))
        wb.close()
        
        return f"✓ 成功写入 Excel 文件：{file_path}\n工作表：{sheet_name}\n行数：{len(rows)}, 列数：{max(len(row) for row in rows)}"
    
    except PermissionError:
        return f"错误：没有权限写入文件：{file_path}"
    except Exception as e:
        return f"写入 Excel 失败：{str(e)}"


@mcp.tool()
async def append_to_excel(file_path: str, data: str, sheet_name: str = None) -> str:
    """
    追加数据到现有 Excel 文件末尾
    
    参数:
        file_path: Excel 文件路径
        data: 要追加的数据，格式同 write_excel
        sheet_name: 工作表名称，默认为第一个工作表
    
    返回:
        操作结果
    """
    try:
        path = Path(file_path)
        if not path.exists():
            return f"错误：文件不存在：{file_path}"
        
        # 加载现有工作簿
        wb = load_workbook(filename=str(path))
        
        # 选择工作表
        if sheet_name:
            if sheet_name not in wb.sheetnames:
                return f"错误：工作表 '{sheet_name}' 不存在"
            ws = wb[sheet_name]
        else:
            ws = wb.active
        
        # 解析数据（与 write_excel 相同的逻辑）
        rows = []
        try:
            import ast
            data_parsed = ast.literal_eval(data)
            if isinstance(data_parsed, list):
                rows = data_parsed
        except:
            lines = data.strip().split('\n')
            for line in lines:
                if '\t' in line:
                    row = line.split('\t')
                elif ',' in line:
                    row = line.split(',')
                else:
                    row = [line]
                rows.append(row)
        
        if not rows:
            wb.close()
            return "错误：数据为空"
        
        # 追加数据
        next_row = ws.max_row + 1
        for row_idx, row in enumerate(rows, start=0):
            for col_idx, value in enumerate(row, start=1):
                cell = ws.cell(row=next_row + row_idx, column=col_idx, value=value)
        
        # 保存文件
        wb.save(str(path))
        wb.close()
        
        return f"✓ 成功追加 {len(rows)} 行数据到：{file_path}\n工作表：{ws.title}"
    
    except Exception as e:
        return f"追加数据失败：{str(e)}"


@mcp.tool()
async def update_excel_cell(file_path: str, cell_address: str, value: str, sheet_name: str = None) -> str:
    """
    更新 Excel 中指定单元格的值
    
    参数:
        file_path: Excel 文件路径
        cell_address: 单元格地址，如 "A1", "B2"
        value: 新的值
        sheet_name: 工作表名称，默认为第一个工作表
    
    返回:
        操作结果
    """
    try:
        path = Path(file_path)
        if not path.exists():
            return f"错误：文件不存在：{file_path}"
        
        wb = load_workbook(filename=str(path))
        
        # 选择工作表
        if sheet_name:
            if sheet_name not in wb.sheetnames:
                return f"错误：工作表 '{sheet_name}' 不存在"
            ws = wb[sheet_name]
        else:
            ws = wb.active
        
        # 更新单元格
        ws[cell_address.upper()] = value
        
        # 保存
        wb.save(str(path))
        wb.close()
        
        return f"✓ 成功更新单元格 {cell_address} 的值为：{value}"
    
    except Exception as e:
        return f"更新单元格失败：{str(e)}"


@mcp.tool()
async def get_excel_info(file_path: str) -> str:
    """
    获取 Excel 文件的基本信息
    
    参数:
        file_path: Excel 文件路径
    
    返回:
        文件信息（工作表列表、行列数等）
    """
    try:
        path = Path(file_path)
        if not path.exists():
            return f"错误：文件不存在：{file_path}"
        
        wb = load_workbook(filename=str(path), data_only=True)
        
        result = [
            f"文件：{file_path}",
            f"文件大小：{path.stat().st_size:,} 字节",
            f"工作表数量：{len(wb.sheetnames)}",
            "=" * 80,
            "工作表详情:"
        ]
        
        for idx, sheet_name in enumerate(wb.sheetnames, start=1):
            ws = wb[sheet_name]
            result.append(f"  {idx}. {sheet_name}: {ws.max_row} 行 × {ws.max_column} 列")
        
        wb.close()
        return "\n".join(result)
    
    except Exception as e:
        return f"获取 Excel 信息失败：{str(e)}"


@mcp.tool()
async def create_excel_template(file_path: str, headers: str, sheet_name: str = "Sheet1") -> str:
    """
    创建带表头的 Excel 模板文件
    
    参数:
        file_path: Excel 文件路径
        headers: 表头字段，用逗号或制表符分隔，如 "姓名，年龄，邮箱"
        sheet_name: 工作表名称
    
    返回:
        操作结果
    """
    try:
        # 解析表头
        if ',' in headers:
            header_list = [h.strip() for h in headers.split(',')]
        elif '\t' in headers:
            header_list = [h.strip() for h in headers.split('\t')]
        else:
            header_list = [headers.strip()]
        
        # 创建工作簿
        wb = Workbook()
        ws = wb.active
        ws.title = sheet_name
        
        # 写入表头（加粗样式）
        from openpyxl.styles import Font
        bold_font = Font(bold=True)
        
        for col_idx, header in enumerate(header_list, start=1):
            cell = ws.cell(row=1, column=col_idx, value=header)
            cell.font = bold_font
        
        # 保存文件
        path = Path(file_path)
        path.parent.mkdir(parents=True, exist_ok=True)
        wb.save(str(path))
        wb.close()
        
        return f"✓ 成功创建 Excel 模板：{file_path}\n表头：{', '.join(header_list)}"
    
    except Exception as e:
        return f"创建模板失败：{str(e)}"


if __name__ == "__main__":
    mcp.run()
