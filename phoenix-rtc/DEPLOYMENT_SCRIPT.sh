#!/bin/bash

# Phoenix RTC 生产部署脚本
# 支持10000+用户会议，高并发，生产就绪

set -e

echo "╔══════════════════════════════════════════════════╗"
echo "║     Phoenix RTC 生产部署脚本 v2.0.0              ║"
echo "║     支持10000+用户会议，高并发                   ║"
echo "╚══════════════════════════════════════════════════╝"
echo ""

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 日志函数
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# 检查环境变量
check_env() {
    log_info "检查环境变量..."

    local required_vars=(
        "JWT_SECRET"
        "LIVEKIT_API_KEY"
        "LIVEKIT_API_SECRET"
        "REDIS_PASSWORD"
        "DB_PASSWORD"
    )

    local missing=()

    for var in "${required_vars[@]}"; do
        if [ -z "${!var}" ]; then
            missing+=("$var")
        fi
    done

    if [ ${#missing[@]} -ne 0 ]; then
        log_error "缺少环境变量: ${missing[*]}"
        log_info "请设置以下环境变量:"
        for var in "${missing[@]}"; do
            echo "  export $var=your-value"
        done
        exit 1
    fi

    log_success "环境变量检查通过"
}

# 检查依赖服务
check_dependencies() {
    log_info "检查依赖服务..."

    # Redis
    if ! command -v redis-cli &> /dev/null; then
        log_error "Redis 未安装"
        exit 1
    fi

    if ! redis-cli -a "$REDIS_PASSWORD" ping > /dev/null 2>&1; then
        log_error "Redis 连接失败"
        exit 1
    fi
    log_success "Redis 运行正常"

    # MySQL (如果使用)
    if command -v mysql &> /dev/null; then
        if ! mysql -u root -p"$DB_PASSWORD" -e "SELECT 1" > /dev/null 2>&1; then
            log_warning "MySQL 连接失败，但继续部署"
        else
            log_success "MySQL 运行正常"
        fi
    fi

    # Docker (可选)
    if command -v docker &> /dev/null; then
        log_success "Docker 可用"
    else
        log_warning "Docker 未安装，跳过容器部署"
    fi
}

# 构建服务端
build_server() {
    log_info "构建服务端..."

    cd server

    # 清理旧构建
    if [ -d "target" ]; then
        rm -rf target/*
    fi

    # 构建 (跳过测试以加快速度)
    mvn clean package -DskipTests -Dmaven.test.skip=true

    if [ ! -f "target/phoenix-rtc-1.0.0.jar" ]; then
        log_error "服务端构建失败"
        exit 1
    fi

    log_success "服务端构建完成: target/phoenix-rtc-1.0.0.jar"
    cd ..
}

# 构建移动端
build_mobile() {
    log_info "构建移动端..."

    cd client-mobile

    # 安装依赖
    if [ ! -d "node_modules" ]; then
        npm install --production
    fi

    # iOS 构建
    if [ "$1" == "ios" ] || [ "$1" == "all" ]; then
        log_info "构建 iOS..."
        if [ -d "ios" ]; then
            cd ios
            pod install --repo-update
            cd ..

            # 构建 IPA (需要开发者账号)
            log_warning "iOS 构建需要开发者账号，请手动执行:"
            echo "  cd client-mobile/ios"
            echo "  xcodebuild -workspace PhoenixRTC.xcworkspace -scheme PhoenixRTC -configuration Release -archivePath PhoenixRTC.xcarchive archive"
        fi
    fi

    # Android 构建
    if [ "$1" == "android" ] || [ "$1" == "all" ]; then
        log_info "构建 Android..."
        if [ -d "android" ]; then
            cd android
            ./gradlew assembleRelease
            if [ -f "app/build/outputs/apk/release/app-release.apk" ]; then
                log_success "APK 生成: android/app/build/outputs/apk/release/app-release.apk"
            fi
            cd ..
        fi
    fi

    log_success "移动端构建完成"
    cd ..
}

# 构建桌面端
build_desktop() {
    log_info "构建桌面端..."

    cd client-pc

    # 安装依赖
    if [ ! -d "node_modules" ]; then
        npm install --production
    fi

    # 构建所有平台
    if [ "$1" == "win" ]; then
        npm run build:win
    elif [ "$1" == "mac" ]; then
        npm run build:mac
    elif [ "$1" == "linux" ]; then
        npm run build:linux
    else
        npm run build
    fi

    log_success "桌面端构建完成: dist/"
    cd ..
}

# 运行测试
run_tests() {
    log_info "运行测试..."

    # 服务端测试
    log_info "服务端测试..."
    cd server
    mvn test
    cd ..

    # 移动端测试
    log_info "移动端测试..."
    cd client-mobile
    npm test -- --passWithNoTests
    cd ..

    # 桌面端测试
    log_info "桌面端测试..."
    cd client-pc
    npm test -- --passWithNoTests
    cd ..

    log_success "所有测试完成"
}

# 启动服务端
start_server() {
    log_info "启动服务端..."

    if [ ! -f "server/target/phoenix-rtc-1.0.0.jar" ]; then
        log_error "服务端 JAR 文件不存在，请先构建"
        exit 1
    fi

    # 检查是否已运行
    if pgrep -f "phoenix-rtc-1.0.0.jar" > /dev/null; then
        log_warning "服务端已在运行"
        return 0
    fi

    # 启动
    java -jar server/target/phoenix-rtc-1.0.0.jar \
        --spring.profiles.active=prod \
        --jwt.secret="$JWT_SECRET" \
        --livekit.api-key="$LIVEKIT_API_KEY" \
        --livekit.api-secret="$LIVEKIT_API_SECRET" \
        --spring.redis.password="$REDIS_PASSWORD" \
        --spring.datasource.password="$DB_PASSWORD" \
        > server.log 2>&1 &

    local pid=$!
    echo $pid > server.pid

    log_info "服务端启动中 (PID: $pid)..."
    sleep 10

    # 检查是否启动成功
    if curl -s http://localhost:8080/api/health > /dev/null; then
        log_success "服务端启动成功: http://localhost:8080"
    else
        log_error "服务端启动失败，请检查日志: server.log"
        exit 1
    fi
}

# 启动 LiveKit
start_livekit() {
    log_info "启动 LiveKit Server..."

    if ! command -v livekit-server &> /dev/null; then
        log_error "LiveKit Server 未安装"
        log_info "请安装: https://docs.livekit.io/deployment/"
        exit 1
    fi

    # 检查是否已运行
    if pgrep -f "livekit-server" > /dev/null; then
        log_warning "LiveKit 已在运行"
        return 0
    fi

    # 使用环境变量配置
    export LIVEKIT_API_KEY="$LIVEKIT_API_KEY"
    export LIVEKIT_API_SECRET="$LIVEKIT_API_SECRET"

    livekit-server --config config/livekit.yaml > livekit.log 2>&1 &
    local pid=$!
    echo $pid > livekit.pid

    sleep 5

    if curl -s http://localhost:7880 > /dev/null; then
        log_success "LiveKit 启动成功: http://localhost:7880"
    else
        log_error "LiveKit 启动失败"
        exit 1
    fi
}

# 使用 Docker 部署
deploy_docker() {
    log_info "Docker 部署..."

    if ! command -v docker-compose &> /dev/null; then
        log_error "Docker Compose 未安装"
        exit 1
    fi

    cd deployment

    if [ ! -f "docker-compose.prod.yml" ]; then
        log_error "docker-compose.prod.yml 不存在"
        exit 1
    fi

    # 设置环境变量
    export JWT_SECRET
    export LIVEKIT_API_KEY
    export LIVEKIT_API_SECRET
    export REDIS_PASSWORD
    export DB_PASSWORD

    # 启动
    docker-compose -f docker-compose.prod.yml up -d

    log_success "Docker 部署完成"

    # 检查状态
    docker-compose -f docker-compose.prod.yml ps

    cd ..
}

# 停止服务
stop_services() {
    log_info "停止服务..."

    # 停止服务端
    if [ -f "server.pid" ]; then
        pid=$(cat server.pid)
        kill $pid 2>/dev/null || true
        rm server.pid
        log_success "服务端已停止"
    fi

    # 停止 LiveKit
    if [ -f "livekit.pid" ]; then
        pid=$(cat livekit.pid)
        kill $pid 2>/dev/null || true
        rm livekit.pid
        log_success "LiveKit 已停止"
    fi

    # 停止 Docker
    if [ -d "deployment" ]; then
        cd deployment
        if [ -f "docker-compose.prod.yml" ]; then
            docker-compose -f docker-compose.prod.yml down 2>/dev/null || true
        fi
        cd ..
        log_success "Docker 容器已停止"
    fi
}

# 查看日志
view_logs() {
    log_info "查看日志..."

    if [ -f "server.log" ]; then
        echo "=== 服务端日志 (最后50行) ==="
        tail -n 50 server.log
    fi

    if [ -f "livekit.log" ]; then
        echo ""
        echo "=== LiveKit 日志 (最后50行) ==="
        tail -n 50 livekit.log
    fi
}

# 性能优化
optimize_performance() {
    log_info "应用性能优化..."

    # JVM 优化
    log_info "JVM 调优..."
    export JAVA_OPTS="-Xms4g -Xmx4g -XX:+UseG1GC -XX:MaxGCPauseMillis=200"

    # Redis 优化
    log_info "Redis 配置优化..."
    redis-cli -a "$REDIS_PASSWORD" CONFIG SET maxmemory-policy allkeys-lru
    redis-cli -a "$REDIS_PASSWORD" CONFIG SET maxmemory 2gb

    # 系统优化
    if [ "$(uname)" == "Linux" ]; then
        log_info "Linux 系统优化..."
        # 增加文件描述符限制
        ulimit -n 65535

        # TCP 优化
        sysctl -w net.core.somaxconn=4096 2>/dev/null || true
    fi

    log_success "性能优化完成"
}

# 健康检查
health_check() {
    log_info "健康检查..."

    # 检查服务端
    if curl -s http://localhost:8080/api/health > /dev/null; then
        log_success "服务端健康: ✓"
    else
        log_error "服务端不健康: ✗"
        return 1
    fi

    # 检查 LiveKit
    if curl -s http://localhost:7880 > /dev/null; then
        log_success "LiveKit 健康: ✓"
    else
        log_error "LiveKit 不健康: ✗"
        return 1
    fi

    # 检查 Redis
    if redis-cli -a "$REDIS_PASSWORD" ping > /dev/null 2>&1; then
        log_success "Redis 健康: ✓"
    else
        log_error "Redis 不健康: ✗"
        return 1
    fi

    log_success "所有服务健康检查通过"
}

# 压力测试
stress_test() {
    log_info "运行压力测试..."

    if [ ! -f "server/target/phoenix-rtc-1.0.0.jar" ]; then
        log_error "请先构建服务端"
        exit 1
    fi

    cd server

    log_info "运行10000用户压力测试..."
    mvn test -Dtest=LoadTest#stressTest_10000UsersInOneMeeting

    log_info "运行混合压力测试..."
    mvn test -Dtest=LoadTest#stressTest_MixedOperations

    cd ..

    log_success "压力测试完成"
}

# 主菜单
show_menu() {
    echo ""
    echo "可用命令:"
    echo "  $0 check-env        - 检查环境变量"
    echo "  $0 build            - 构建所有组件"
    echo "  $0 build-server     - 仅构建服务端"
    echo "  $0 build-mobile     - 构建移动端"
    echo "  $0 build-desktop    - 构建桌面端"
    echo "  $0 test             - 运行所有测试"
    echo "  $0 start            - 启动所有服务"
    echo "  $0 start-server     - 仅启动服务端"
    echo "  $0 start-livekit    - 仅启动 LiveKit"
    echo "  $0 deploy-docker    - Docker 部署"
    echo "  $0 stop             - 停止所有服务"
    echo "  $0 logs             - 查看日志"
    echo "  $0 optimize         - 性能优化"
    echo "  $0 health           - 健康检查"
    echo "  $0 stress           - 压力测试"
    echo "  $0 full-deploy      - 完整部署"
    echo ""
}

# 完整部署流程
full_deploy() {
    log_info "开始完整部署流程..."

    check_env
    check_dependencies
    build_server
    run_tests
    optimize_performance
    start_livekit
    start_server
    health_check

    log_success "完整部署完成!"
    log_info "服务端: http://localhost:8080"
    log_info "LiveKit: http://localhost:7880"
}

# 参数解析
case "${1:-}" in
    check-env)
        check_env
        ;;
    build)
        build_server
        build_mobile all
        build_desktop all
        ;;
    build-server)
        build_server
        ;;
    build-mobile)
        build_mobile "${2:-all}"
        ;;
    build-desktop)
        build_desktop "${2:-all}"
        ;;
    test)
        run_tests
        ;;
    start)
        start_livekit
        start_server
        ;;
    start-server)
        start_server
        ;;
    start-livekit)
        start_livekit
        ;;
    deploy-docker)
        deploy_docker
        ;;
    stop)
        stop_services
        ;;
    logs)
        view_logs
        ;;
    optimize)
        optimize_performance
        ;;
    health)
        health_check
        ;;
    stress)
        stress_test
        ;;
    full-deploy)
        full_deploy
        ;;
    *)
        show_menu
        exit 1
        ;;
esac
