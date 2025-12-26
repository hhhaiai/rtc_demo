#!/bin/bash

# Phoenix RTC 生产环境部署脚本
# 使用方法: ./deploy.sh [dev|prod]

set -e  # 遇到错误立即退出

ENV=${1:-dev}
BASE_DIR=$(cd "$(dirname "$0")" && pwd)
PROJECT_NAME="phoenix-rtc"

echo "🚀 开始部署 Phoenix RTC - 环境: $ENV"

# 检查必要环境变量
check_env_vars() {
    local missing=()

    if [ -z "$JWT_SECRET_KEY" ]; then
        missing+=("JWT_SECRET_KEY")
    fi
    if [ -z "$LIVEKIT_API_KEY" ]; then
        missing+=("LIVEKIT_API_KEY")
    fi
    if [ -z "$LIVEKIT_API_SECRET" ]; then
        missing+=("LIVEKIT_API_SECRET")
    fi
    if [ -z "$DEMO_AUTH_PASSWORD" ]; then
        missing+=("DEMO_AUTH_PASSWORD")
    fi

    if [ ${#missing[@]} -gt 0 ]; then
        echo "❌ 缺少必要的环境变量: ${missing[*]}"
        echo "请参考 .env.example 文件配置环境变量"
        exit 1
    fi

    echo "✅ 环境变量检查通过"
}

# 开发环境部署
deploy_dev() {
    echo "📦 开始开发环境部署..."

    # 检查 Docker 环境
    if ! command -v docker &> /dev/null; then
        echo "❌ 未安装 Docker"
        exit 1
    fi

    # 启动依赖服务
    echo "🔄 启动依赖服务 (Redis, MySQL, LiveKit)..."
    docker-compose up -d redis mysql livekit

    # 等待服务就绪
    echo "⏳ 等待服务就绪..."
    sleep 10

    # 检查服务状态
    echo "🔍 检查服务状态..."
    docker-compose ps

    echo "✅ 开发环境部署完成"
    echo "📱 访问: http://localhost:8080"
    echo "📊 LiveKit: ws://localhost:7880"
    echo "🔧 调试: docker-compose logs -f app"
}

# 生产环境部署
deploy_prod() {
    echo "📦 开始生产环境部署..."

    # 检查必要配置
    check_env_vars

    # 构建应用
    echo "🔨 构建 Spring Boot 应用..."
    cd "$BASE_DIR/server"
    mvn clean package -DskipTests

    if [ ! -f "target/phoenix-rtc-1.0.0.jar" ]; then
        echo "❌ 构建失败，找不到 JAR 文件"
        exit 1
    fi

    echo "✅ 构建完成"

    # 启动生产环境
    echo "🔄 启动生产环境服务..."
    cd "$BASE_DIR"

    # 使用 docker-compose.prod.yml
    if [ -f "docker-compose.prod.yml" ]; then
        docker-compose -f docker-compose.prod.yml up -d
    else
        # 如果没有生产环境配置文件，使用默认的
        docker-compose up -d app
    fi

    # 等待应用启动
    echo "⏳ 等待应用启动..."
    sleep 15

    # 健康检查
    echo "🔍 健康检查..."
    if curl -f http://localhost:8080/actuator/health > /dev/null 2>&1; then
        echo "✅ 应用健康检查通过"
    else
        echo "⚠️  应用可能未正常启动，请检查日志"
        docker-compose logs app
        exit 1
    fi

    echo "✅ 生产环境部署完成"
    echo "📱 访问: http://localhost:8080"
    echo "📊 API 文档: http://localhost:8080/actuator"
}

# 查看日志
logs() {
    docker-compose logs -f app
}

# 停止服务
stop() {
    echo "⏹️  停止服务..."
    docker-compose down
    echo "✅ 服务已停止"
}

# 重启服务
restart() {
    echo "🔄 重启服务..."
    docker-compose restart
    echo "✅ 服务已重启"
}

# 显示帮助
show_help() {
    echo "Phoenix RTC 部署脚本"
    echo ""
    echo "用法: ./deploy.sh [命令]"
    echo ""
    echo "命令:"
    echo "  dev          部署开发环境 (包含所有依赖服务)"
    echo "  prod         部署生产环境 (需要配置环境变量)"
    echo "  logs         查看应用日志"
    echo "  stop         停止所有服务"
    echo "  restart      重启服务"
    echo "  help         显示此帮助信息"
    echo ""
    echo "环境变量配置:"
    echo "  请参考 .env.example 文件"
    echo ""
    echo "示例:"
    echo "  export JWT_SECRET_KEY=your-secret-key"
    echo "  export LIVEKIT_API_KEY=your-api-key"
    echo "  export LIVEKIT_API_SECRET=your-api-secret"
    echo "  export DEMO_AUTH_PASSWORD=your-password"
    echo "  ./deploy.sh prod"
}

# 主逻辑
case "$ENV" in
    dev)
        deploy_dev
        ;;
    prod)
        deploy_prod
        ;;
    logs)
        logs
        ;;
    stop)
        stop
        ;;
    restart)
        restart
        ;;
    help|--help|-h)
        show_help
        ;;
    *)
        echo "❌ 未知命令: $ENV"
        echo "使用 ./deploy.sh help 查看帮助"
        exit 1
        ;;
esac
