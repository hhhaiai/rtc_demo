#!/bin/bash

# Phoenix RTC 快速启动脚本

set -e

echo "=========================================="
echo "  Phoenix RTC 快速启动脚本"
echo "=========================================="
echo ""

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 检查命令
check_command() {
    if ! command -v $1 &> /dev/null; then
        echo -e "${RED}错误: 未安装 $1${NC}"
        exit 1
    fi
}

# 检查依赖
echo "步骤 1/5: 检查依赖..."
check_command docker
check_command docker-compose
check_command java
check_command mvn
check_command node
check_command npm
echo -e "${GREEN}✓ 所有依赖已就绪${NC}"
echo ""

# 启动基础设施
echo "步骤 2/5: 启动 Docker 服务 (LiveKit, Redis, MySQL)..."
cd /Users/sanbo/Desktop/调研/phoenix-rtc
docker-compose up -d

echo "等待服务启动..."
sleep 10

# 检查服务状态
if docker-compose ps | grep -q "Up"; then
    echo -e "${GREEN}✓ Docker 服务已启动${NC}"
else
    echo -e "${RED}✗ Docker 服务启动失败${NC}"
    docker-compose logs
    exit 1
fi
echo ""

# 编译后端
echo "步骤 3/5: 编译 Spring Boot 服务端..."
cd server
mvn clean package -DskipTests -q
echo -e "${GREEN}✓ 服务端编译完成${NC}"
echo ""

# 启动后端
echo "步骤 4/5: 启动 Spring Boot 服务端..."
java -jar target/phoenix-rtc-server-1.0.0.jar &
BACKEND_PID=$!
echo "后端 PID: $BACKEND_PID"

# 等待后端启动
echo "等待后端启动..."
sleep 15

# 检查后端是否运行
if curl -s http://localhost:8080/actuator/health > /dev/null 2>&1; then
    echo -e "${GREEN}✓ 后端服务已启动 (http://localhost:8080)${NC}"
else
    echo -e "${RED}✗ 后端服务启动失败${NC}"
    kill $BACKEND_PID 2>/dev/null
    exit 1
fi
echo ""

# 显示信息
echo "步骤 5/5: 启动完成！"
echo ""
echo -e "${GREEN}==========================================${NC}"
echo -e "${GREEN}  所有服务已启动${NC}"
echo -e "${GREEN}==========================================${NC}"
echo ""
echo "服务端口信息:"
echo "  - 后端 API:     http://localhost:8080"
echo "  - LiveKit WS:   ws://localhost:7880"
echo "  - Redis:        localhost:6379"
echo "  - MySQL:        localhost:3306"
echo ""
echo "API 测试:"
echo "  curl http://localhost:8080/api/rtc/health"
echo ""
echo "启动客户端:"
echo "  移动端:  cd client-mobile && npm start"
echo "  桌面端:  cd client-pc && npm run start"
echo ""
echo "查看日志:"
echo "  docker-compose logs -f"
echo ""
echo "停止服务:"
echo "  docker-compose down"
echo "  kill $BACKEND_PID"
echo ""
echo -e "${YELLOW}注意: 请确保 8080, 7880, 6379, 3306 端口未被占用${NC}"
