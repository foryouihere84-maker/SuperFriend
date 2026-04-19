@echo off
chcp 65001 >nul
echo ============================================================
echo MCP Python 工具集 - 依赖安装脚本
echo ============================================================
echo.
echo 正在安装必要的 Python 包...
echo.

echo [1/3] 安装 MCP SDK...
pip install mcp>=1.0.0
if errorlevel 1 (
    echo ❌ MCP SDK 安装失败
) else (
    echo ✅ MCP SDK 安装成功
)
echo.

echo [2/3] 安装 requests(网络请求库)...
pip install requests>=2.31.0
if errorlevel 1 (
    echo ❌ requests 安装失败
) else (
    echo ✅ requests 安装成功
)
echo.

echo [3/3] 安装 psutil(系统监控库)...
pip install psutil>=5.9.0
if errorlevel 1 (
    echo ❌ psutil 安装失败
) else (
    echo ✅ psutil 安装成功
)
echo.

echo ============================================================
echo 安装完成！
echo ============================================================
echo.
echo 下一步操作:
echo 1. 运行测试脚本验证安装：python test_all_tools.py
echo 2. 配置 mcp-servers-config.json 添加新的服务器
echo 3. 重启 MCP 客户端以加载新工具
echo.
pause
