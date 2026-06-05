"""
代码分析与 AST 工具集 - 对标 Cline 的代码理解能力
提供代码结构分析、函数/类提取、依赖关系分析等功能
"""
from mcp.server.fastmcp import FastMCP
import ast
from pathlib import Path
from typing import List, Dict, Any, Optional
import json

# 创建 MCP服务器实例
mcp = FastMCP("Code Analysis Tools")


@mcp.tool()
async def list_code_definitions(file_path: str) -> str:
    """
    列出 Python 文件中的所有代码定义（类、函数、方法）
    
    Args:
        file_path: Python 文件路径
        
    Returns:
        代码定义列表
    """
    try:
        path = Path(file_path)
        if not path.exists():
            return f"❌ 错误：文件不存在\n路径：{file_path}"
        
        if path.suffix != '.py':
            return "⚠️ 警告：这不是 Python 文件"
        
        with open(path, 'r', encoding='utf-8') as f:
            source = f.read()
        
        tree = ast.parse(source)
        
        definitions = []
        
        for node in ast.walk(tree):
            if isinstance(node, ast.ClassDef):
                # 类定义
                methods = [n.name for n in node.body if isinstance(n, ast.FunctionDef)]
                definitions.append({
                    'type': 'class',
                    'name': node.name,
                    'line': node.lineno,
                    'methods': methods,
                    'docstring': ast.get_docstring(node)
                })
            
            elif isinstance(node, ast.FunctionDef) or isinstance(node, ast.AsyncFunctionDef):
                # 函数定义
                args = []
                for arg in node.args.args:
                    args.append(arg.arg)
                
                definitions.append({
                    'type': 'function',
                    'name': node.name,
                    'line': node.lineno,
                    'args': args,
                    'docstring': ast.get_docstring(node)
                })
        
        # 格式化输出
        result = [f"📋 代码定义分析", f"", f"文件：{file_path}", f"总计：{len(definitions)} 个定义", ""]
        
        classes = [d for d in definitions if d['type'] == 'class']
        functions = [d for d in definitions if d['type'] == 'function']
        
        if classes:
            result.append(f"📦 类 ({len(classes)} 个):")
            for cls in classes:
                result.append(f"  📘 {cls['name']} (第{cls['line']}行)")
                if cls['methods']:
                    result.append(f"     方法：{', '.join(cls['methods'])}")
                if cls.get('docstring'):
                    doc_first_line = cls['docstring'].split('\n')[0]
                    result.append(f"     说明：{doc_first_line[:80]}")
                result.append("")
        
        if functions:
            result.append(f"🔧 函数 ({len(functions)} 个):")
            for func in functions:
                args_str = ', '.join(func['args'])
                result.append(f"  🔹 {func['name']}({args_str}) (第{func['line']}行)")
                if func.get('docstring'):
                    doc_first_line = func['docstring'].split('\n')[0]
                    result.append(f"     说明：{doc_first_line[:80]}")
        
        return "\n".join(result)
    
    except SyntaxError as e:
        return f"❌ Python 语法错误\n文件：{file_path}\n详情：{str(e)}"
    except Exception as e:
        return f"❌ 分析失败\n错误：{str(e)}"


@mcp.tool()
async def analyze_imports(file_path: str) -> str:
    """
    分析 Python 文件的导入依赖
    
    Args:
        file_path: Python 文件路径
        
    Returns:
        导入依赖分析
    """
    try:
        path = Path(file_path)
        if not path.exists():
            return f"❌ 错误：文件不存在\n路径：{file_path}"
        
        with open(path, 'r', encoding='utf-8') as f:
            source = f.read()
        
        tree = ast.parse(source)
        
        imports = []
        from_imports = []
        
        for node in ast.walk(tree):
            if isinstance(node, ast.Import):
                for alias in node.names:
                    imports.append({
                        'module': alias.name,
                        'alias': alias.asname
                    })
            
            elif isinstance(node, ast.ImportFrom):
                module = node.module or ''
                names = [alias.name for alias in node.names]
                from_imports.append({
                    'module': module,
                    'names': names,
                    'level': node.level
                })
        
        result = [f"📦 导入依赖分析", f"", f"文件：{file_path}", ""]
        
        if imports:
            result.append(f"直接导入 ({len(imports)} 个):")
            for imp in imports:
                if imp['alias']:
                    result.append(f"  import {imp['module']} as {imp['alias']}")
                else:
                    result.append(f"  import {imp['module']}")
            result.append("")
        
        if from_imports:
            result.append(f"从模块导入 ({len(from_imports)} 个):")
            for fi in from_imports:
                names_str = ', '.join(fi['names'][:10])  # 限制显示数量
                if len(fi['names']) > 10:
                    names_str += f"... (共{len(fi['names'])}个)"
                
                dots = '.' * fi['level']
                result.append(f"  from {dots}{fi['module']} import {names_str}")
        
        # 统计第三方库
        third_party = []
        stdlib = ['os', 'sys', 'json', 'ast', 'pathlib', 'typing', 'datetime', 're', 'collections']
        
        all_modules = [imp['module'] for imp in imports] + [fi['module'] for fi in from_imports]
        for module in all_modules:
            base_module = module.split('.')[0]
            if base_module not in stdlib and base_module not in third_party:
                third_party.append(base_module)
        
        if third_party:
            result.append(f"\n📚 第三方库依赖:")
            for lib in sorted(third_party):
                result.append(f"  - {lib}")
        
        return "\n".join(result)
    
    except Exception as e:
        return f"❌ 分析失败\n错误：{str(e)}"


@mcp.tool()
async def get_function_source(file_path: str, function_name: str) -> str:
    """
    获取指定函数的完整源代码
    
    Args:
        file_path: Python 文件路径
        function_name: 函数名
        
    Returns:
        函数源代码
    """
    try:
        path = Path(file_path)
        if not path.exists():
            return f"❌ 错误：文件不存在\n路径：{file_path}"
        
        with open(path, 'r', encoding='utf-8') as f:
            lines = f.readlines()
        
        source = ''.join(lines)
        tree = ast.parse(source)
        
        for node in ast.walk(tree):
            if isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef)) and node.name == function_name:
                # 获取函数的起始和结束行
                start_line = node.lineno - 1
                end_line = node.end_lineno if hasattr(node, 'end_lineno') else start_line + 1
                
                # 向后查找直到下一个定义或空行
                for i in range(end_line, len(lines)):
                    if lines[i].strip() and not lines[i].startswith(' ') and not lines[i].startswith('\t'):
                        end_line = i
                        break
                
                function_source = ''.join(lines[start_line:end_line])
                
                return f"✅ 找到函数：{function_name}\n\n文件：{file_path}\n位置：第{node.lineno}行\n\n源代码:\n{function_source}"
        
        return f"❌ 未找到函数 '{function_name}'\n文件：{file_path}\n\n提示：请检查函数名是否正确"
    
    except Exception as e:
        return f"❌ 获取失败\n错误：{str(e)}"


@mcp.tool()
async def find_all_callers(file_path: str, function_name: str) -> str:
    """
    查找所有调用指定函数的位置
    
    Args:
        file_path: Python 文件路径
        function_name: 要查找的函数名
        
    Returns:
        调用位置列表
    """
    try:
        path = Path(file_path)
        if not path.exists():
            return f"❌ 错误：文件不存在\n路径：{file_path}"
        
        with open(path, 'r', encoding='utf-8') as f:
            source = f.read()
        
        tree = ast.parse(source)
        callers = []
        
        for node in ast.walk(tree):
            if isinstance(node, ast.Call):
                # 检查是否是目标函数调用
                if isinstance(node.func, ast.Name) and node.func.id == function_name:
                    callers.append({
                        'line': node.lineno,
                        'type': 'direct_call'
                    })
                elif isinstance(node.func, ast.Attribute) and node.func.attr == function_name:
                    callers.append({
                        'line': node.lineno,
                        'type': 'method_call'
                    })
        
        if not callers:
            return f"ℹ️ 未找到调用函数 '{function_name}' 的位置\n文件：{file_path}"
        
        result = [f"🔍 函数调用分析", f"", f"目标函数：{function_name}", f"文件：{file_path}", f"", f"找到 {len(callers)} 处调用:", ""]
        
        for i, caller in enumerate(callers[:50], 1):
            result.append(f"{i}. 第{caller['line']}行 ({'直接调用' if caller['type'] == 'direct_call' else '方法调用'})")
        
        if len(callers) > 50:
            result.append(f"\n... 还有 {len(callers) - 50} 处调用未显示")
        
        return "\n".join(result)
    
    except Exception as e:
        return f"❌ 分析失败\n错误：{str(e)}"


@mcp.tool()
async def count_code_metrics(directory: str, file_pattern: str = "*.py") -> str:
    """
    统计代码指标（行数、函数数、类数等）
    
    Args:
        directory: 目标目录
        file_pattern: 文件匹配模式，默认*.py
        
    Returns:
        代码统计信息
    """
    try:
        dir_path = Path(directory)
        if not dir_path.exists():
            return f"❌ 错误：目录不存在\n路径：{directory}"
        
        files = list(dir_path.rglob(file_pattern))
        
        total_lines = 0
        total_code_lines = 0
        total_blank_lines = 0
        total_comments = 0
        total_functions = 0
        total_classes = 0
        
        file_stats = []
        
        for file in files:
            try:
                with open(file, 'r', encoding='utf-8') as f:
                    lines = f.readlines()
                
                total_lines += len(lines)
                
                code_lines = 0
                blank_lines = 0
                comments = 0
                
                for line in lines:
                    stripped = line.strip()
                    if not stripped:
                        blank_lines += 1
                    elif stripped.startswith('#'):
                        comments += 1
                    else:
                        code_lines += 1
                
                total_code_lines += code_lines
                total_blank_lines += blank_lines
                total_comments += comments
                
                # AST 分析
                source = ''.join(lines)
                try:
                    tree = ast.parse(source)
                    
                    functions = sum(1 for node in ast.walk(tree) 
                                  if isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef)))
                    classes = sum(1 for node in ast.walk(tree) 
                                 if isinstance(node, ast.ClassDef))
                    
                    total_functions += functions
                    total_classes += classes
                    
                    rel_path = file.relative_to(dir_path)
                    file_stats.append({
                        'path': str(rel_path),
                        'lines': len(lines),
                        'code': code_lines,
                        'functions': functions,
                        'classes': classes
                    })
                except SyntaxError:
                    pass
            
            except Exception as e:
                continue
        
        result = [
            f"📊 代码统计报告",
            f"",
            f"目录：{directory}",
            f"文件模式：{file_pattern}",
            f"扫描文件数：{len(files)}",
            f"",
            f"📈 总体指标:",
            f"  总行数：{total_lines:,}",
            f"  代码行：{total_code_lines:,}",
            f"  空行：{total_blank_lines:,}",
            f"  注释行：{total_comments:,}",
            f"  函数数：{total_functions:,}",
            f"  类数：{total_classes:,}",
            f"",
            f"平均每文件:",
            f"  行数：{total_lines // len(files) if files else 0:,}",
            f"  函数：{total_functions // len(files) if files else 0:,}",
            f"  类：{total_classes // len(files) if files else 0:,}",
        ]
        
        # 显示前 10 个最大的文件
        if file_stats:
            file_stats.sort(key=lambda x: x['lines'], reverse=True)
            result.append(f"\n📁 最大的 10 个文件:")
            for i, stat in enumerate(file_stats[:10], 1):
                result.append(
                    f"{i}. {stat['path']} ({stat['lines']}行 / {stat['functions']}函数 / {stat['classes']}类)"
                )
        
        return "\n".join(result)
    
    except Exception as e:
        return f"❌ 统计失败\n错误：{str(e)}"


@mcp.tool()
async def extract_docstrings(file_path: str) -> str:
    """
    提取 Python 文件中的所有文档字符串
    
    Args:
        file_path: Python 文件路径
        
    Returns:
        文档字符串列表
    """
    try:
        path = Path(file_path)
        if not path.exists():
            return f"❌ 错误：文件不存在\n路径：{file_path}"
        
        with open(path, 'r', encoding='utf-8') as f:
            source = f.read()
        
        tree = ast.parse(source)
        
        docstrings = []
        
        for node in ast.walk(tree):
            docstring = ast.get_docstring(node)
            if docstring:
                if isinstance(node, ast.Module):
                    docstrings.append({
                        'type': 'module',
                        'name': path.name,
                        'docstring': docstring
                    })
                elif isinstance(node, ast.ClassDef):
                    docstrings.append({
                        'type': 'class',
                        'name': node.name,
                        'docstring': docstring
                    })
                elif isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef)):
                    docstrings.append({
                        'type': 'function',
                        'name': node.name,
                        'docstring': docstring
                    })
        
        if not docstrings:
            return f"ℹ️ 未找到文档字符串\n文件：{file_path}"
        
        result = [f"📝 文档字符串提取", f"", f"文件：{file_path}", f"总计：{len(docstrings)} 个文档", ""]
        
        for ds in docstrings:
            icon = {'module': '📄', 'class': '📘', 'function': '🔹'}.get(ds['type'], '📌')
            result.append(f"{icon} [{ds['type']}] {ds['name']}")
            
            # 显示第一行
            first_line = ds['docstring'].split('\n')[0]
            result.append(f"   {first_line[:100]}{'...' if len(first_line) > 100 else ''}")
            result.append("")
        
        return "\n".join(result)
    
    except Exception as e:
        return f"❌ 提取失败\n错误：{str(e)}"


if __name__ == "__main__":
    mcp.run()
