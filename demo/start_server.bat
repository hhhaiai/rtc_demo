@echo off
chcp 65001 >nul
echo.
echo ╔══════════════════════════════════════════════════════════════╗
echo ║                                                              ║
echo ║          🚀 Phoenix RTC Demo - 一键启动工具                  ║
echo ║                                                              ║
echo ╚══════════════════════════════════════════════════════════════╝
echo.

echo [1/3] 检查 Python 环境...
python --version >nul 2>&1
if %errorlevel% equ 0 (
    echo ✅ Python 已安装
    set PYTHON=python
) else (
    echo ❌ 未找到 Python，请先安装
    echo    下载地址: https://www.python.org/downloads/
    pause
    exit /b 1
)

echo.
echo [2/3] 检查端口 8000...
netstat -ano | findstr ":8000" >nul 2>&1
if %errorlevel% equ 0 (
    echo ⚠️  端口 8000 已被占用，正在尝试停止...
    for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":8000"') do taskkill /F /PID %%a >nul 2>&1
    timeout /t 1 /nobreak >nul
)
echo ✅ 端口检查完成

echo.
echo [3/3] 启动本地服务器...
cd /d "%~dp0"
start "" "http://localhost:8000"
%PYTHON% -m http.server 8000

echo.
echo ╔══════════════════════════════════════════════════════════════╗
echo ║                                                              ║
echo ║  🎉 服务器启动成功！                                         ║
echo ║                                                              ║
echo ║  访问地址: http://localhost:8000                            ║
echo ║                                                              ║
echo ║  所有演示页面:                                               ║
echo ║  • 1_video_call.html        - 视频通话测试                   ║
echo ║  • 2_multi_person_meeting.html - 多人会议测试                ║
echo ║  • 3_live_streaming.html    - 直播+浮动评论测试              ║
echo ║                                                              ║
echo ║  如果浏览器未自动打开，请手动访问上述地址                     ║
echo ║                                                              ║
echo ╚══════════════════════════════════════════════════════════════╝
echo.
echo 提示: 按 Ctrl+C 停止服务器
echo.

pause
