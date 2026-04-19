"""
系统监控工具集 - 提供 CPU/内存使用率、进程管理、磁盘空间等系统信息
"""
from mcp.server.fastmcp import FastMCP
import psutil
from datetime import datetime
from typing import Optional, List

# 创建 MCP服务器实例
mcp = FastMCP("System Monitor")


@mcp.tool()
async def get_cpu_usage(interval: float = 1.0) -> str:
    """
    获取 CPU 使用率信息
    
    Args:
        interval: 采样间隔（秒），默认 1 秒
        
    Returns:
        CPU 使用详情
    """
    try:
        # 获取每个核心的使用率
        cpu_percent_per_core = psutil.cpu_percent(interval=interval, percpu=True)
        total_percent = psutil.cpu_percent(interval=0)
        
        result = [
            "🖥️ CPU 使用率",
            f"总使用率：{total_percent}%",
            f"物理核心数：{psutil.cpu_count(logical=False)}",
            f"逻辑核心数：{psutil.cpu_count(logical=True)}",
            "",
            "各核心使用率:"
        ]
        
        for i, percent in enumerate(cpu_percent_per_core):
            bar_length = 20
            filled_length = int(bar_length * percent / 100)
            bar = '█' * filled_length + '-' * (bar_length - filled_length)
            result.append(f"  核心{i+1}: [{bar}] {percent}%")
        
        return "\n".join(result)
    except Exception as e:
        return f"❌ 获取 CPU 信息失败\n错误：{str(e)}"


@mcp.tool()
async def get_memory_usage() -> str:
    """
    获取内存使用率信息
    
    Returns:
        内存使用详情
    """
    try:
        memory = psutil.virtual_memory()
        
        used_gb = memory.used / (1024 ** 3)
        available_gb = memory.available / (1024 ** 3)
        total_gb = memory.total / (1024 ** 3)
        
        # 计算进度条
        bar_length = 30
        filled_length = int(bar_length * memory.percent / 100)
        bar = '█' * filled_length + '-' * (bar_length - filled_length)
        
        result = [
            "💾 内存使用情况",
            f"",
            f"总内存：{total_gb:.2f} GB",
            f"已使用：{used_gb:.2f} GB ({memory.percent}%)",
            f"可用：{available_gb:.2f} GB",
            f"",
            f"使用进度：[{bar}] {memory.percent}%",
            f"",
            f"详细信息:",
            f"  可用内存：{available_gb:.2f} GB",
            f"  已用内存：{used_gb:.2f} GB",
            f"  总内存：{total_gb:.2f} GB",
            f"  使用百分比：{memory.percent}%"
        ]
        
        return "\n".join(result)
    except Exception as e:
        return f"❌ 获取内存信息失败\n错误：{str(e)}"


@mcp.tool()
async def get_disk_usage(path: str = "C:\\") -> str:
    """
    获取磁盘分区使用情况
    
    Args:
        path: 要检查的磁盘路径，默认 C 盘
        
    Returns:
        磁盘使用详情
    """
    try:
        usage = psutil.disk_usage(path)
        
        total_gb = usage.total / (1024 ** 3)
        used_gb = usage.used / (1024 ** 3)
        free_gb = usage.free / (1024 ** 3)
        
        # 计算进度条
        bar_length = 30
        filled_length = int(bar_length * usage.percent / 100)
        bar = '█' * filled_length + '-' * (bar_length - filled_length)
        
        result = [
            f"💿 磁盘使用情况 - {path}",
            f"",
            f"总容量：{total_gb:.2f} GB",
            f"已使用：{used_gb:.2f} GB",
            f"可用：{free_gb:.2f} GB",
            f"",
            f"使用进度：[{bar}] {usage.percent}%",
            f"",
            f"详细信息:",
            f"  使用百分比：{usage.percent}%",
            f"  剩余空间：{free_gb:.2f} GB"
        ]
        
        return "\n".join(result)
    except Exception as e:
        return f"❌ 获取磁盘信息失败\n错误：{str(e)}"


@mcp.tool()
async def list_running_processes(limit: int = 20, sort_by: str = "cpu") -> str:
    """
    列出正在运行的进程
    
    Args:
        limit: 最多显示的进程数量
        sort_by: 排序方式 ("cpu" 或 "memory")
        
    Returns:
        进程列表
    """
    try:
        processes = []
        
        for proc in psutil.process_iter(['pid', 'name', 'cpu_percent', 'memory_percent']):
            try:
                info = proc.info
                processes.append({
                    'pid': info['pid'],
                    'name': info['name'],
                    'cpu': info.get('cpu_percent', 0),
                    'memory': info.get('memory_percent', 0)
                })
            except (psutil.NoSuchProcess, psutil.AccessDenied):
                continue
        
        # 排序
        if sort_by == "memory":
            processes.sort(key=lambda x: x['memory'], reverse=True)
        else:  # default to cpu
            processes.sort(key=lambda x: x['cpu'], reverse=True)
        
        result = [
            f"🔄 运行中的进程 (按{sort_by.upper()}排序)",
            f"",
            f"前 {min(len(processes), limit)} 个占用最高的进程:",
            f"",
            f"{'PID':<8} {'名称':<30} {'CPU%':<8} {'内存%':<8}"
        ]
        
        for proc in processes[:limit]:
            result.append(
                f"{proc['pid']:<8} {proc['name'][:29]:<30} {proc['cpu']:<8.1f} {proc['memory']:<8.1f}"
            )
        
        return "\n".join(result)
    except Exception as e:
        return f"❌ 获取进程信息失败\n错误：{str(e)}"


@mcp.tool()
async def get_system_info() -> str:
    """
    获取完整的系统信息概览
    
    Returns:
        综合系统信息
    """
    try:
        boot_time = datetime.fromtimestamp(psutil.boot_time())
        uptime = datetime.now() - boot_time
        
        result = [
            "🖥️ 系统信息概览",
            f"",
            f"操作系统：{psutil.system()}",
            f"计算机名：{psutil.node()}",
            f"启动时间：{boot_time.strftime('%Y-%m-%d %H:%M:%S')}",
            f"运行时长：{str(uptime).split('.')[0]}",
            f"",
            f"CPU 核心数：{psutil.cpu_count(logical=False)} 物理 / {psutil.cpu_count(logical=True)} 逻辑",
            f"内存总量：{psutil.virtual_memory().total / (1024**3):.2f} GB",
            f"",
            f"当前 CPU 使用率：{psutil.cpu_percent(interval=0.5)}%",
            f"当前内存使用率：{psutil.virtual_memory().percent}%",
            f"",
            f"Python 版本：{psutil.__version__}"
        ]
        
        return "\n".join(result)
    except Exception as e:
        return f"❌ 获取系统信息失败\n错误：{str(e)}"


@mcp.tool()
async def get_network_connections() -> str:
    """
    获取网络连接信息
    
    Returns:
        网络连接列表
    """
    try:
        connections = psutil.net_connections(kind='inet')
        
        # 过滤出监听状态的连接
        listening = [conn for conn in connections if conn.status == psutil.CONN_LISTEN]
        established = [conn for conn in connections if conn.status == psutil.CONN_ESTABLISHED]
        
        result = [
            "🌐 网络连接统计",
            f"",
            f"总连接数：{len(connections)}",
            f"监听中：{len(listening)}",
            f"已建立：{len(established)}",
            f"",
            f"监听的端口 (前 20 个):"
        ]
        
        for conn in listening[:20]:
            laddr = conn.laddr
            if laddr:
                result.append(f"  {laddr.ip}:{laddr.port}")
        
        if established:
            result.append(f"\n已建立的连接 (前 10 个):")
            for conn in established[:10]:
                laddr = conn.laddr
                raddr = conn.raddr
                if laddr and raddr:
                    result.append(f"  {laddr.ip}:{laddr.port} -> {raddr.ip}:{raddr.port}")
        
        return "\n".join(result)
    except Exception as e:
        return f"❌ 获取网络连接信息失败\n错误：{str(e)}"


if __name__ == "__main__":
    mcp.run()
