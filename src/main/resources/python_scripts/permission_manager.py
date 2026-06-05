#!/usr/bin/env python3
"""
Permission Manager MCP Server - 权限访问设置工具
提供文件系统权限管理功能，允许大模型临时解锁受限路径的访问权限
"""

import asyncio
import os
import json
import platform
from pathlib import Path
from typing import Dict, List, Set
from mcp.server.fastmcp import FastMCP

mcp = FastMCP("permission_manager")

# 权限配置文件路径
PERMISSION_CONFIG_FILE = Path.home() / ".harmonynotes" / "permission_config.json"

# 默认允许访问的路径（白名单）
DEFAULT_ALLOWED_PATHS = [
    str(Path.home()),  # 用户主目录
    os.getcwd(),       # 当前工作目录
]

# 默认禁止访问的路径（黑名单）
DEFAULT_DENIED_PATHS = [
    "C:\\Windows",
    "C:\\Program Files",
    "C:\\Program Files (x86)",
    "C:\\ProgramData",
    "/System",
    "/usr/bin",
    "/usr/sbin",
]


class PermissionConfig:
    """权限配置管理类"""
    
    def __init__(self):
        self.allowed_paths: Set[str] = set(DEFAULT_ALLOWED_PATHS)
        self.denied_paths: Set[str] = set(DEFAULT_DENIED_PATHS)
        self.temporary_unlocks: Dict[str, float] = {}  # path -> unlock_time
        self.load_config()
    
    def load_config(self):
        """从配置文件加载权限设置"""
        try:
            if PERMISSION_CONFIG_FILE.exists():
                with open(PERMISSION_CONFIG_FILE, 'r', encoding='utf-8') as f:
                    data = json.load(f)
                    self.allowed_paths = set(data.get('allowed_paths', DEFAULT_ALLOWED_PATHS))
                    self.denied_paths = set(data.get('denied_paths', DEFAULT_DENIED_PATHS))
                    self.temporary_unlocks = data.get('temporary_unlocks', {})
        except Exception as e:
            print(f"加载权限配置失败：{e}")
    
    def save_config(self):
        """保存权限配置到文件"""
        try:
            PERMISSION_CONFIG_FILE.parent.mkdir(parents=True, exist_ok=True)
            with open(PERMISSION_CONFIG_FILE, 'w', encoding='utf-8') as f:
                json.dump({
                    'allowed_paths': list(self.allowed_paths),
                    'denied_paths': list(self.denied_paths),
                    'temporary_unlocks': self.temporary_unlocks
                }, f, indent=2, ensure_ascii=False)
        except Exception as e:
            return f"保存权限配置失败：{e}"
        return "权限配置已保存"
    
    def check_permission(self, path: str) -> tuple[bool, str]:
        """检查路径是否有访问权限"""
        # 规范化路径
        try:
            normalized_path = os.path.normpath(os.path.abspath(path))
        except Exception:
            return False, "路径格式无效"
        
        # 检查临时解锁
        if normalized_path in self.temporary_unlocks:
            return True, "临时解锁访问"
        
        # 检查黑名单
        for denied in self.denied_paths:
            try:
                if normalized_path.startswith(os.path.normpath(denied)):
                    return False, f"路径在黑名单中（匹配：{denied}）"
            except Exception:
                continue
        
        # 检查白名单
        for allowed in self.allowed_paths:
            try:
                if normalized_path.startswith(os.path.normpath(allowed)):
                    return True, f"路径在白名单中（匹配：{allowed}）"
            except Exception:
                continue
        
        # 默认拒绝
        return False, "路径不在允许列表中"
    
    def add_allowed_path(self, path: str) -> str:
        """添加允许访问的路径"""
        try:
            normalized_path = os.path.normpath(os.path.abspath(path))
            if not os.path.exists(normalized_path):
                return f"警告：路径不存在：{normalized_path}"
            self.allowed_paths.add(normalized_path)
            self.save_config()
            return f"已添加允许访问的路径：{normalized_path}"
        except Exception as e:
            return f"添加路径失败：{e}"
    
    def add_denied_path(self, path: str) -> str:
        """添加禁止访问的路径"""
        try:
            normalized_path = os.path.normpath(os.path.abspath(path))
            self.denied_paths.add(normalized_path)
            self.save_config()
            return f"已添加禁止访问的路径：{normalized_path}"
        except Exception as e:
            return f"添加路径失败：{e}"
    
    def remove_allowed_path(self, path: str) -> str:
        """移除允许访问的路径"""
        try:
            normalized_path = os.path.normpath(os.path.abspath(path))
            if normalized_path in self.allowed_paths:
                self.allowed_paths.remove(normalized_path)
                self.save_config()
                return f"已移除允许访问的路径：{normalized_path}"
            return f"路径不在允许列表中：{normalized_path}"
        except Exception as e:
            return f"移除路径失败：{e}"
    
    def remove_denied_path(self, path: str) -> str:
        """移除禁止访问的路径"""
        try:
            normalized_path = os.path.normpath(os.path.abspath(path))
            if normalized_path in self.denied_paths:
                self.denied_paths.remove(normalized_path)
                self.save_config()
                return f"已移除禁止访问的路径：{normalized_path}"
            return f"路径不在禁止列表中：{normalized_path}"
        except Exception as e:
            return f"移除路径失败：{e}"
    
    def temporary_unlock(self, path: str, duration_minutes: int = 5) -> str:
        """临时解锁路径访问权限"""
        try:
            normalized_path = os.path.normpath(os.path.abspath(path))
            import time
            self.temporary_unlocks[normalized_path] = time.time() + (duration_minutes * 60)
            self.save_config()
            return f"已临时解锁路径：{normalized_path}（有效期：{duration_minutes}分钟）"
        except Exception as e:
            return f"临时解锁失败：{e}"
    
    def clear_temporary_unlocks(self) -> str:
        """清除所有临时解锁"""
        count = len(self.temporary_unlocks)
        self.temporary_unlocks.clear()
        self.save_config()
        return f"已清除 {count} 个临时解锁"
    
    def get_status(self) -> str:
        """获取当前权限状态"""
        status = [
            "=== 权限访问设置 ===",
            f"\n✅ 允许访问的路径（{len(self.allowed_paths)} 个）：",
        ]
        for path in sorted(self.allowed_paths):
            status.append(f"  - {path}")
        
        status.append(f"\n❌ 禁止访问的路径（{len(self.denied_paths)} 个）：")
        for path in sorted(self.denied_paths):
            status.append(f"  - {path}")
        
        if self.temporary_unlocks:
            status.append(f"\n🔓 临时解锁的路径（{len(self.temporary_unlocks)} 个）：")
            import time
            for path, unlock_time in self.temporary_unlocks.items():
                remaining = int(unlock_time - time.time())
                status.append(f"  - {path}（剩余：{remaining}秒）")
        
        status.append(f"\n📄 配置文件：{PERMISSION_CONFIG_FILE}")
        return "\n".join(status)


# 全局权限配置实例
permission_config = PermissionConfig()


@mcp.tool()
async def check_permission(path: str) -> str:
    """检查指定路径的访问权限
    
    Args:
        path: 要检查的文件或目录路径
        
    Returns:
        权限检查结果
    """
    allowed, reason = permission_config.check_permission(path)
    status = "✅ 允许访问" if allowed else "❌ 拒绝访问"
    return f"路径：{path}\n状态：{status}\n原因：{reason}"


@mcp.tool()
async def get_permission_status() -> str:
    """获取当前权限配置状态
    
    Returns:
        权限配置的详细信息
    """
    return permission_config.get_status()


@mcp.tool()
async def add_allowed_path(path: str) -> str:
    """添加允许访问的路径到白名单
    
    Args:
        path: 要允许访问的路径
        
    Returns:
        操作结果
    """
    return permission_config.add_allowed_path(path)


@mcp.tool()
async def remove_allowed_path(path: str) -> str:
    """从白名单移除允许访问的路径
    
    Args:
        path: 要移除的路径
        
    Returns:
        操作结果
    """
    return permission_config.remove_allowed_path(path)


@mcp.tool()
async def add_denied_path(path: str) -> str:
    """添加禁止访问的路径到黑名单
    
    Args:
        path: 要禁止访问的路径
        
    Returns:
        操作结果
    """
    return permission_config.add_denied_path(path)


@mcp.tool()
async def remove_denied_path(path: str) -> str:
    """从黑名单移除禁止访问的路径
    
    Args:
        path: 要移除的路径
        
    Returns:
        操作结果
    """
    return permission_config.remove_denied_path(path)


@mcp.tool()
async def temporary_unlock_path(path: str, duration_minutes: int = 5) -> str:
    """临时解锁路径访问权限
    
    Args:
        path: 要临时解锁的路径
        duration_minutes: 解锁有效期（分钟），默认5分钟
        
    Returns:
        操作结果
    """
    return permission_config.temporary_unlock(path, duration_minutes)


@mcp.tool()
async def clear_temporary_unlocks() -> str:
    """清除所有临时解锁的路径
    
    Returns:
        操作结果
    """
    return permission_config.clear_temporary_unlocks()


@mcp.tool()
async def unlock_for_session(path: str) -> str:
    """解锁路径访问权限（仅在当前会话有效）
    
    Args:
        path: 要解锁的路径
        
    Returns:
        操作结果
    """
    try:
        normalized_path = os.path.normpath(os.path.abspath(path))
        if not os.path.exists(normalized_path):
            return f"警告：路径不存在：{normalized_path}"
        
        # 添加到临时解锁，设置一个较长的时间（1小时）
        return permission_config.temporary_unlock(path, 60)
    except Exception as e:
        return f"解锁失败：{e}"


if __name__ == "__main__":
    mcp.run()