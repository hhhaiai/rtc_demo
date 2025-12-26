#!/bin/bash

# 清屏
clear

echo ""
echo "╔══════════════════════════════════════════════════════════════╗"
echo "║                                                              ║"
echo "║          🚀 Phoenix RTC Demo - 一键启动工具                  ║"
echo "║                                                              ║"
echo "╚══════════════════════════════════════════════════════════════╝"
echo ""

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 检查操作系统
OS="unknown"
if [[ "$OSTYPE" == "darwin"* ]]; then
    OS="mac"
    echo -e "${BLUE}[系统]${NC} 检测到 macOS"
elif [[ "$OSTYPE" == "linux-gnu"* ]]; then
    OS="linux"
    echo -e "${BLUE}[系统]${NC} 检测到 Linux"
else
    echo -e "${RED}[错误]${NC} 不支持的操作系统"
    exit 1
fi

echo ""
echo -e "${BLUE}[1/5]${NC} 检查 Python 环境..."
if command -v python3 &> /dev/null; then
    PYTHON="python3"
    echo -e "${GREEN}✅${NC} Python 3 已安装"
elif command -v python &> /dev/null; then
    PYTHON="python"
    echo -e "${GREEN}✅${NC} Python 已安装"
else
    echo -e "${RED}❌${NC} 未找到 Python，请先安装"
    echo "Mac: brew install python3"
    echo "Linux: sudo apt-get install python3"
    exit 1
fi

echo ""
echo -e "${BLUE}[2/5]${NC} 检查端口 8000..."
if lsof -Pi :8000 -sTCP:LISTEN -t >/dev/null 2>&1 ; then
    echo -e "${YELLOW}⚠️${NC} 端口 8000 已被占用，正在尝试停止..."
    kill $(lsof -t -i:8000) 2>/dev/null
    sleep 1
fi
echo -e "${GREEN}✅${NC} 端口检查完成"

echo ""
echo -e "${BLUE}[3/5]${NC} 启动本地服务器..."
cd "$(dirname "$0")"
$PYTHON -m http.server 8000 > /dev/null 2>&1 &
SERVER_PID=$!
sleep 2

if ps -p $SERVER_PID > /dev/null 2>&1; then
    echo -e "${GREEN}✅${NC} 服务器已启动 (PID: $SERVER_PID)"
else
    echo -e "${RED}❌${NC} 服务器启动失败"
    exit 1
fi

echo ""
echo -e "${BLUE}[4/5]${NC} 检查服务器状态..."
sleep 1
curl -s http://localhost:8000 > /dev/null 2>&1
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✅${NC} 服务器运行正常"
else
    echo -e "${RED}❌${NC} 服务器无法访问"
    kill $SERVER_PID 2>/dev/null
    exit 1
fi

echo ""
echo -e "${BLUE}[5/5]${NC} 正在打开浏览器..."
echo ""
echo -e "${YELLOW}┌─────────────────────────────────────────────────────────────┐${NC}"
echo -e "${YELLOW}│${NC} 重要提示：浏览器会弹出摄像头权限请求                          ${YELLOW}│${NC}"
echo -e "${YELLOW}│${NC} 请务必点击"允许"或"Allow"                                   ${YELLOW}│${NC}"
echo -e "${YELLOW}└─────────────────────────────────────────────────────────────┘${NC}"
echo ""

# 尝试打开浏览器
if [[ "$OS" == "mac" ]]; then
    open -a "Google Chrome" "http://localhost:8000/1_video_call.html" 2>/dev/null || \
    open "http://localhost:8000/1_video_call.html"
elif [[ "$OS" == "linux" ]]; then
    xdg-open "http://localhost:8000/1_video_call.html" 2>/dev/null || \
    google-chrome "http://localhost:8000/1_video_call.html" 2>/dev/null || \
    chromium-browser "http://localhost:8000/1_video_call.html" 2>/dev/null || \
    firefox "http://localhost:8000/1_video_call.html" 2>/dev/null
fi

echo -e "${GREEN}✅${NC} 浏览器已启动"
echo ""

echo "╔══════════════════════════════════════════════════════════════╗"
echo "║                                                              ║"
echo "║  🎉 服务器启动成功！                                         ║"
echo "║                                                              ║"
echo "║  访问地址: http://localhost:8000                            ║"
echo "║                                                              ║"
echo "║  所有演示页面:                                               ║"
echo "║  • 1_video_call.html        - 视频通话测试                   ║"
echo "║  • 2_multi_person_meeting.html - 多人会议测试                ║"
echo "║  • 3_live_streaming.html    - 直播+浮动评论测试              ║"
echo "║                                                              ║"
echo "║  如果浏览器未自动打开，请手动访问上述地址                     ║"
echo "║                                                              ║"
echo "╚══════════════════════════════════════════════════════════════╝"
echo ""
echo -e "${BLUE}服务器进程 PID: $SERVER_PID${NC}"
echo ""
echo -e "${YELLOW}提示:${NC} 按 Ctrl+C 停止服务器"
echo -e "${YELLOW}提示:${NC} 服务器在后台运行，关闭终端不会停止服务器"
echo ""

# 保持脚本运行，显示服务器日志
echo "正在显示服务器日志（按 Ctrl+C 停止）..."
echo "─────────────────────────────────────────────────────────────"
wait $SERVER_PID