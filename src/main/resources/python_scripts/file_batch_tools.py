"""
文件批处理工具集 - 提供批量重命名、文件搜索、文件管理等功能
"""
from mcp.server.fastmcp import FastMCP
import os
import shutil
from pathlib import Path
from typing import Optional, List
import fnmatch

# 创建 MCP服务器实例
mcp = FastMCP("File Batch Tools")


@mcp.tool()
async def batch_rename_files(directory: str, pattern: str, replacement: str, file_extension: Optional[str] = None) -> str:
    """
    批量重命名文件（基于文本替换）
    
    Args:
        directory: 目标目录路径
        pattern: 要替换的文本模式
        replacement: 替换后的文本
        file_extension: 可选的文件扩展名过滤（如".txt"）
        
    Returns:
        重命名结果统计
    """
    try:
        dir_path = Path(directory)
        if not dir_path.exists():
            return f"❌ 错误：目录不存在\n路径：{directory}"
        
        renamed_count = 0
        results = []
        
        for file in dir_path.iterdir():
            if file.is_file():
                # 检查扩展名
                if file_extension and file.suffix != file_extension:
                    continue
                
                old_name = file.name
                new_name = old_name.replace(pattern, replacement)
                
                if old_name != new_name:
                    new_path = dir_path / new_name
                    # 避免文件名冲突
                    counter = 1
                    while new_path.exists():
                        name_parts = new_name.rsplit('.', 1)
                        if len(name_parts) == 2:
                            new_name = f"{name_parts[0]}_{counter}.{name_parts[1]}"
                        else:
                            new_name = f"{new_name}_{counter}"
                        new_path = dir_path / new_name
                    
                    file.rename(new_path)
                    renamed_count += 1
                    results.append(f"  {old_name} → {new_name}")
        
        return f"✅ 批量重命名完成\n目录：{directory}\n成功重命名：{renamed_count} 个文件\n\n详细列表:\n" + "\n".join(results[:50])
    except Exception as e:
        return f"❌ 批量重命名失败\n错误：{str(e)}"


@mcp.tool()
async def search_files(directory: str, pattern: str, recursive: bool = True) -> str:
    """
    搜索匹配指定模式的文件
    
    Args:
        directory: 搜索起始目录
        pattern: 搜索模式（支持通配符，如*.txt, *.py, data_?.csv）
        recursive: 是否递归搜索子目录
        
    Returns:
        匹配的文件列表
    """
    try:
        dir_path = Path(directory)
        if not dir_path.exists():
            return f"❌ 错误：目录不存在\n路径：{directory}"
        
        matched_files = []
        
        if recursive:
            file_iter = dir_path.rglob(pattern)
        else:
            file_iter = dir_path.glob(pattern)
        
        for file in file_iter:
            if file.is_file():
                rel_path = file.relative_to(dir_path)
                size_kb = file.stat().st_size / 1024
                size_str = f"{size_kb:.2f} KB" if size_kb < 1024 else f"{size_kb/1024:.2f} MB"
                matched_files.append(f"📄 {rel_path} ({size_str})")
        
        if not matched_files:
            return f"未找到匹配的文件\n目录：{directory}\n模式：{pattern}"
        
        return f"找到 {len(matched_files)} 个匹配的文件\n目录：{directory}\n模式：{pattern}\n\n文件列表:\n" + "\n".join(matched_files[:100])
    except Exception as e:
        return f"❌ 搜索失败\n错误：{str(e)}"


@mcp.tool()
async def list_large_files(directory: str, min_size_mb: float = 10.0, limit: int = 20) -> str:
    """
    列出目录下的大文件
    
    Args:
        directory: 目标目录
        min_size_mb: 最小文件大小（MB），默认 10MB
        limit: 最多返回的文件数量
        
    Returns:
        大文件列表（按大小排序）
    """
    try:
        dir_path = Path(directory)
        if not dir_path.exists():
            return f"❌ 错误：目录不存在\n路径：{directory}"
        
        large_files = []
        
        for file in dir_path.rglob('*'):
            if file.is_file():
                size_mb = file.stat().st_size / (1024 * 1024)
                if size_mb >= min_size_mb:
                    rel_path = file.relative_to(dir_path)
                    large_files.append((rel_path, size_mb))
        
        # 按大小降序排序
        large_files.sort(key=lambda x: x[1], reverse=True)
        
        if not large_files:
            return f"未找到大于 {min_size_mb}MB 的文件\n目录：{directory}"
        
        result_lines = [f"找到 {len(large_files)} 个大于 {min_size_mb}MB 的文件\n\n前 {min(len(large_files), limit)} 个最大的文件:"]
        for i, (file_path, size_mb) in enumerate(large_files[:limit], 1):
            result_lines.append(f"{i}. 📁 {file_path} ({size_mb:.2f} MB)")
        
        return "\n".join(result_lines)
    except Exception as e:
        return f"❌ 获取大文件列表失败\n错误：{str(e)}"


@mcp.tool()
async def delete_empty_directories(directory: str, recursive: bool = False) -> str:
    """
    删除空目录
    
    Args:
        directory: 目标目录
        recursive: 是否递归删除（包括嵌套的空目录）
        
    Returns:
        删除结果统计
    """
    try:
        dir_path = Path(directory)
        if not dir_path.exists():
            return f"❌ 错误：目录不存在\n路径：{directory}"
        
        deleted_count = 0
        
        if recursive:
            # 自底向上遍历，先删除最深层的空目录
            for root, dirs, files in os.walk(directory, topdown=False):
                for dir_name in dirs:
                    dir_full_path = Path(root) / dir_name
                    if not any(dir_full_path.iterdir()):
                        dir_full_path.rmdir()
                        deleted_count += 1
        else:
            # 只删除直接子目录中的空目录
            for item in dir_path.iterdir():
                if item.is_dir() and not any(item.iterdir()):
                    item.rmdir()
                    deleted_count += 1
        
        return f"{'✅' if deleted_count > 0 else 'ℹ️'} 空目录清理完成\n目录：{directory}\n删除了 {deleted_count} 个空目录"
    except Exception as e:
        return f"❌ 删除空目录失败\n错误：{str(e)}"


@mcp.tool()
async def copy_files_by_pattern(source_dir: str, dest_dir: str, pattern: str) -> str:
    """
    按模式复制文件到目标目录
    
    Args:
        source_dir: 源目录
        dest_dir: 目标目录
        pattern: 文件匹配模式（如*.txt, *.jpg）
        
    Returns:
        复制结果统计
    """
    try:
        source_path = Path(source_dir)
        dest_path = Path(dest_dir)
        
        if not source_path.exists():
            return f"❌ 错误：源目录不存在\n路径：{source_dir}"
        
        # 创建目标目录
        dest_path.mkdir(parents=True, exist_ok=True)
        
        copied_count = 0
        failed_count = 0
        results = []
        
        for file in source_path.glob(pattern):
            if file.is_file():
                try:
                    dest_file = dest_path / file.name
                    shutil.copy2(file, dest_file)
                    copied_count += 1
                    results.append(f"✅ {file.name}")
                except Exception as e:
                    failed_count += 1
                    results.append(f"❌ {file.name}: {str(e)}")
        
        summary = f"✅ 文件复制完成\n源目录：{source_dir}\n目标目录：{dest_dir}\n模式：{pattern}\n成功：{copied_count} 个"
        if failed_count > 0:
            summary += f"\n失败：{failed_count} 个"
        
        return summary + "\n\n详细列表:\n" + "\n".join(results[:50])
    except Exception as e:
        return f"❌ 文件复制失败\n错误：{str(e)}"


@mcp.tool()
async def get_directory_stats(directory: str) -> str:
    """
    获取目录统计信息（文件数、文件夹数、总大小等）
    
    Args:
        directory: 目标目录
        
    Returns:
        详细的统计信息
    """
    try:
        dir_path = Path(directory)
        if not dir_path.exists():
            return f"❌ 错误：目录不存在\n路径：{directory}"
        
        total_files = 0
        total_dirs = 0
        total_size = 0
        file_types = {}
        
        for item in dir_path.rglob('*'):
            if item.is_file():
                total_files += 1
                total_size += item.stat().st_size
                
                # 统计文件类型
                ext = item.suffix.lower()
                file_types[ext] = file_types.get(ext, 0) + 1
            elif item.is_dir():
                total_dirs += 1
        
        # 格式化输出
        size_gb = total_size / (1024 ** 3)
        size_mb = total_size / (1024 ** 2)
        
        if size_gb >= 1:
            size_str = f"{size_gb:.2f} GB"
        else:
            size_str = f"{size_mb:.2f} MB"
        
        result = [
            f"📊 目录统计信息",
            f"目录：{directory}",
            f"",
            f"总计:",
            f"  📁 文件夹数：{total_dirs}",
            f"  📄 文件总数：{total_files}",
            f"  💾 总大小：{size_str}",
            f"",
            f"文件类型分布（前 10 个）:"
        ]
        
        # 按数量排序文件类型
        sorted_types = sorted(file_types.items(), key=lambda x: x[1], reverse=True)[:10]
        for ext, count in sorted_types:
            type_name = ext if ext else "无扩展名"
            result.append(f"  {type_name}: {count} 个")
        
        return "\n".join(result)
    except Exception as e:
        return f"❌ 获取统计信息失败\n错误：{str(e)}"


if __name__ == "__main__":
    mcp.run()
