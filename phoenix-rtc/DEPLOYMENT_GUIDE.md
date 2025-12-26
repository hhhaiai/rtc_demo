# Phoenix RTC 部署指南

本指南提供从开发环境到生产环境的完整部署流程。

---

## 📋 目录

1. [环境准备](#环境准备)
2. [开发环境部署](#开发环境部署)
3. [生产环境部署](#生产环境部署)
4. [Docker 部署](#docker-部署)
5. [Kubernetes 部署](#kubernetes-部署)
6. [监控与维护](#监控与维护)
7. [故障排查](#故障排查)

---

## 环境准备

### 1.1 系统要求

| 组件 | 开发环境 | 生产环境 | 推荐配置 |
|------|----------|----------|----------|
| **操作系统** | macOS/Linux/Windows | Linux | Ubuntu 22.04 LTS |
| **CPU** | 4核 | 8核+ | 16核 |
| **内存** | 8GB | 16GB+ | 32GB |
| **磁盘** | 20GB | 100GB+ | SSD 500GB |

### 1.2 软件依赖

#### 后端依赖
```bash
# Java 17+
java -version
# 应显示: openjdk version "17.x"

# Maven 3.8+
mvn -version
# 应显示: Apache Maven 3.8.x

# Docker (可选)
docker --version
# 应显示: Docker version 24.x

# Docker Compose (可选)
docker-compose --version
# 应显示: docker-compose version 2.x
```

#### 前端依赖
```bash
# Node.js 18+
node --version
# 应显示: v18.x

# npm 9+
npm --version
# 应显示: 9.x
```

### 1.3 依赖服务

#### 必需服务
- **MySQL 8.0+**: 数据库
- **Redis 7+**: 缓存和会话管理
- **LiveKit 1.5+**: WebRTC 媒体服务器

#### 可选服务
- **Coturn**: NAT穿透服务器
- **Prometheus**: 监控
- **Grafana**: 可视化
- **Nginx**: 负载均衡

---

## 开发环境部署

### 2.1 快速启动 (推荐)

```bash
# 1. 克隆项目
git clone https://github.com/phoenix-rtc/phoenix-rtc.git
cd phoenix-rtc

# 2. 配置环境变量
cp .env.example .env
# 编辑 .env 文件，填入配置

# 3. 一键启动
./deploy.sh dev
```

### 2.2 手动部署

#### 步骤 1: 启动依赖服务

```bash
# 启动 Redis, MySQL, LiveKit
docker-compose up -d redis mysql livekit

# 等待服务就绪 (约 30秒)
sleep 30

# 检查服务状态
docker-compose ps
```

预期输出:
```
NAME                COMMAND                  STATUS
phoenix_livekit     "/livekit-server"        Up
phoenix_mysql       "docker-entrypoint.s…"   Up
phoenix_redis       "docker-entrypoint.s…"   Up
```

#### 步骤 2: 配置环境变量

```bash
# 导出环境变量
export JWT_SECRET_KEY="dev-jwt-secret-key-min-32-chars"
export LIVEKIT_URL="ws://localhost:7880"
export LIVEKIT_API_KEY="devkey"
export LIVEKIT_API_SECRET="secret"
export DEMO_AUTH_PASSWORD="dev123"

# 验证配置
env | grep -E "(JWT|LIVEKIT|DEMO)"
```

#### 步骤 3: 构建后端

```bash
cd server

# 清理并构建
mvn clean package -DskipTests

# 验证构建
ls -lh target/phoenix-rtc-1.0.0.jar
```

#### 步骤 4: 启动后端

```bash
# 方式 1: 直接运行 JAR
java -jar target/phoenix-rtc-1.0.0.jar

# 方式 2: 使用 Maven 运行
mvn spring-boot:run

# 方式 3: 后台运行
nohup java -jar target/phoenix-rtc-1.0.0.jar > app.log 2>&1 &
```

#### 步骤 5: 验证部署

```bash
# 检查健康状态
curl http://localhost:8080/actuator/health

# 预期响应
{"status":"UP"}

# 测试认证
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"user1","password":"dev123"}'
```

### 2.3 前端开发环境

#### 移动端 (React Native)

```bash
cd client-mobile

# 安装依赖
npm install

# iOS 开发
npm run ios

# Android 开发
npm run android
```

#### 桌面端 (Electron)

```bash
cd client-pc

# 安装依赖
npm install

# 开发模式
npm run dev

# 构建应用
npm run build
```

---

## 生产环境部署

### 3.1 环境变量配置

创建生产环境配置文件 `.env.prod`:

```bash
# ============================================
# 数据库配置 (生产环境)
# ============================================
MYSQL_HOST=mysql-service
MYSQL_PORT=3306
MYSQL_DATABASE=phoenix_rtc
MYSQL_USER=phoenix
MYSQL_PASSWORD=your_very_strong_mysql_password

# ============================================
# Redis 配置 (生产环境)
# ============================================
REDIS_HOST=redis-service
REDIS_PORT=6379
REDIS_PASSWORD=your_very_strong_redis_password
REDIS_DATABASE=0

# ============================================
# LiveKit 配置 (生产环境)
# ============================================
LIVEKIT_URL=ws://livekit-service:7880
LIVEKIT_API_KEY=your_production_livekit_key
LIVEKIT_API_SECRET=your_production_livekit_secret

# ============================================
# JWT 安全配置 (生产环境必须使用强密钥)
# ============================================
# 使用: openssl rand -base64 32 生成强密钥
JWT_SECRET_KEY=your_very_strong_jwt_secret_key_min_32_chars

# ============================================
# 认证配置 (生产环境应集成真实用户系统)
# ============================================
DEMO_AUTH_PASSWORD=your_production_password

# ============================================
# 应用配置
# ============================================
SERVER_PORT=8080
SPRING_PROFILES_ACTIVE=prod
```

### 3.2 数据库初始化

```bash
# 连接 MySQL
mysql -h localhost -u root -p

# 创建数据库和用户
CREATE DATABASE phoenix_rtc CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'phoenix'@'%' IDENTIFIED BY 'your_password';
GRANT ALL PRIVILEGES ON phoenix_rtc.* TO 'phoenix'@'%';
FLUSH PRIVILEGES;

# 导入表结构
USE phoenix_rtc;
SOURCE /path/to/phoenix-rtc/server/src/main/resources/db/schema.sql;
```

### 3.3 构建生产环境镜像

```bash
# 构建后端镜像
cd server
docker build -t phoenix-rtc-server:latest .

# 验证镜像
docker images | grep phoenix-rtc-server
```

### 3.4 使用 Docker Compose 生产部署

```bash
# 1. 配置环境变量
export $(cat .env.prod | xargs)

# 2. 启动所有服务
docker-compose -f docker-compose.prod.yml up -d

# 3. 等待服务就绪
sleep 60

# 4. 检查状态
docker-compose -f docker-compose.prod.yml ps

# 5. 查看日志
docker-compose -f docker-compose.prod.yml logs -f app
```

### 3.5 手动生产部署

#### 步骤 1: 准备服务器

```bash
# 更新系统
sudo apt update && sudo apt upgrade -y

# 安装 Java 17
sudo apt install openjdk-17-jdk -y

# 安装 Docker
curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker $USER

# 安装 Docker Compose
sudo apt install docker-compose-plugin -y
```

#### 步骤 2: 部署依赖服务

```bash
# 创建部署目录
mkdir -p /opt/phoenix-rtc/{data/mysql,data/redis,logs}

# 启动 MySQL
docker run -d \
  --name mysql \
  -p 3306:3306 \
  -e MYSQL_ROOT_PASSWORD=strong_root_password \
  -e MYSQL_DATABASE=phoenix_rtc \
  -e MYSQL_USER=phoenix \
  -e MYSQL_PASSWORD=your_mysql_password \
  -v /opt/phoenix-rtc/data/mysql:/var/lib/mysql \
  mysql:8.0 \
  --character-set-server=utf8mb4 \
  --collation-server=utf8mb4_unicode_ci

# 启动 Redis
docker run -d \
  --name redis \
  -p 6379:6379 \
  -e REDIS_PASSWORD=your_redis_password \
  -v /opt/phoenix-rtc/data/redis:/data \
  redis:7-alpine \
  redis-server --appendonly yes --requirepass your_redis_password

# 启动 LiveKit
docker run -d \
  --name livekit \
  -p 7880:7880 \
  -p 7881:7881 \
  -p 7882:7882/udp \
  -p 9000:9000 \
  -e LIVEKIT_KEYS=your_livekit_key:your_livekit_secret \
  -v /opt/phoenix-rtc/livekit-config.yaml:/config/livekit.yaml \
  livekit/livekit-server:latest
```

#### 步骤 3: 部署应用

```bash
# 上传 JAR 包
scp server/target/phoenix-rtc-1.0.0.jar user@server:/opt/phoenix-rtc/

# 创建启动脚本
cat > /opt/phoenix-rtc/start.sh << 'EOF'
#!/bin/bash
export JWT_SECRET_KEY="your_jwt_secret"
export LIVEKIT_URL="ws://localhost:7880"
export LIVEKIT_API_KEY="your_key"
export LIVEKIT_API_SECRET="your_secret"
export DEMO_AUTH_PASSWORD="your_password"
export MYSQL_PASSWORD="your_mysql_password"
export REDIS_PASSWORD="your_redis_password"

cd /opt/phoenix-rtc
nohup java -jar phoenix-rtc-1.0.0.jar \
  --spring.profiles.active=prod \
  > logs/app.log 2>&1 &
echo "Phoenix RTC started. PID: $!"
EOF

chmod +x /opt/phoenix-rtc/start.sh

# 启动应用
/opt/phoenix-rtc/start.sh
```

#### 步骤 4: 配置系统服务

```bash
# 创建 systemd 服务
sudo tee /etc/systemd/system/phoenix-rtc.service << 'EOF'
[Unit]
Description=Phoenix RTC Application
After=network.target mysql.service redis.service

[Service]
Type=simple
User=phoenix
WorkingDirectory=/opt/phoenix-rtc
Environment=JWT_SECRET_KEY=your_jwt_secret
Environment=LIVEKIT_URL=ws://localhost:7880
Environment=LIVEKIT_API_KEY=your_key
Environment=LIVEKIT_API_SECRET=your_secret
Environment=DEMO_AUTH_PASSWORD=your_password
Environment=MYSQL_PASSWORD=your_mysql_password
Environment=REDIS_PASSWORD=your_redis_password
ExecStart=/usr/bin/java -jar phoenix-rtc-1.0.0.jar --spring.profiles.active=prod
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
EOF

# 启用并启动服务
sudo systemctl daemon-reload
sudo systemctl enable phoenix-rtc
sudo systemctl start phoenix-rtc

# 检查状态
sudo systemctl status phoenix-rtc
```

---

## Docker 部署

### 4.1 Dockerfile

```dockerfile
# server/Dockerfile
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine

WORKDIR /app
COPY --from=builder /app/target/phoenix-rtc-1.0.0.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
```

### 4.2 Docker Compose 开发环境

```yaml
# docker-compose.yml
version: '3.8'

services:
  app:
    build: ./server
    container_name: phoenix_app
    ports:
      - "8080:8080"
    environment:
      - SPRING_REDIS_HOST=redis
      - SPRING_DATASOURCE_URL=jdbc:mysql://mysql:3306/phoenix_rtc
      - LIVEKIT_URL=ws://livekit:7880
      - LIVEKIT_API_KEY=devkey
      - LIVEKIT_API_SECRET=secret
      - JWT_SECRET_KEY=dev-jwt-secret-key-min-32-chars
      - DEMO_AUTH_PASSWORD=dev123
    depends_on:
      - redis
      - mysql
      - livekit
    networks:
      - phoenix-net

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"
    networks:
      - phoenix-net

  mysql:
    image: mysql:8.0
    ports:
      - "3306:3306"
    environment:
      - MYSQL_ROOT_PASSWORD=rootpass
      - MYSQL_DATABASE=phoenix_rtc
      - MYSQL_USER=phoenix
      - MYSQL_PASSWORD=phoenix123
    volumes:
      - mysql-data:/var/lib/mysql
    networks:
      - phoenix-net

  livekit:
    image: livekit/livekit-server:latest
    ports:
      - "7880:7880"
      - "7881:7881"
      - "7882:7882/udp"
    environment:
      - LIVEKIT_KEYS=devkey:secret
    networks:
      - phoenix-net

networks:
  phoenix-net:
    driver: bridge

volumes:
  mysql-data:
```

### 4.3 Docker Compose 生产环境

```yaml
# docker-compose.prod.yml
version: '3.8'

services:
  app:
    build: ./server
    container_name: phoenix_app
    restart: unless-stopped
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=prod
      - SPRING_REDIS_HOST=redis
      - SPRING_REDIS_PASSWORD=${REDIS_PASSWORD}
      - SPRING_DATASOURCE_URL=jdbc:mysql://mysql:3306/phoenix_rtc
      - SPRING_DATASOURCE_USERNAME=phoenix
      - SPRING_DATASOURCE_PASSWORD=${MYSQL_PASSWORD}
      - LIVEKIT_URL=ws://livekit:7880
      - LIVEKIT_API_KEY=${LIVEKIT_API_KEY}
      - LIVEKIT_API_SECRET=${LIVEKIT_API_SECRET}
      - JWT_SECRET_KEY=${JWT_SECRET_KEY}
      - DEMO_AUTH_PASSWORD=${DEMO_AUTH_PASSWORD}
    depends_on:
      - redis
      - mysql
      - livekit
    networks:
      - phoenix-net
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3

  redis:
    image: redis:7-alpine
    container_name: phoenix_redis
    restart: unless-stopped
    command: redis-server --requirepass ${REDIS_PASSWORD} --appendonly yes
    volumes:
      - redis-data:/data
    networks:
      - phoenix-net
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 10s
      timeout: 3s
      retries: 3

  mysql:
    image: mysql:8.0
    container_name: phoenix_mysql
    restart: unless-stopped
    environment:
      - MYSQL_ROOT_PASSWORD=${MYSQL_ROOT_PASSWORD}
      - MYSQL_DATABASE=phoenix_rtc
      - MYSQL_USER=phoenix
      - MYSQL_PASSWORD=${MYSQL_PASSWORD}
    volumes:
      - mysql-data:/var/lib/mysql
      - ./server/src/main/resources/db/schema.sql:/docker-entrypoint-initdb.d/schema.sql
    networks:
      - phoenix-net
    command: --default-authentication-plugin=mysql_native_password --character-set-server=utf8mb4 --collation-server=utf8mb4_unicode_ci
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost"]
      interval: 10s
      timeout: 5s
      retries: 3

  livekit:
    image: livekit/livekit-server:latest
    container_name: phoenix_livekit
    restart: unless-stopped
    ports:
      - "7880:7880"
      - "7881:7881"
      - "7882:7882/udp"
    environment:
      - LIVEKIT_KEYS=${LIVEKIT_API_KEY}:${LIVEKIT_API_SECRET}
      - LIVEKIT_CONFIG=/config/livekit.yaml
    volumes:
      - ./livekit-config.yaml:/config/livekit.yaml
    networks:
      - phoenix-net
    depends_on:
      - redis

  # 可选: Nginx 负载均衡
  nginx:
    image: nginx:alpine
    container_name: phoenix_nginx
    restart: unless-stopped
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - ./nginx.conf:/etc/nginx/nginx.conf
      - ./ssl:/etc/nginx/ssl
    networks:
      - phoenix-net
    depends_on:
      - app

networks:
  phoenix-net:
    driver: bridge

volumes:
  redis-data:
  mysql-data:
```

---

## Kubernetes 部署

### 5.1 Kubernetes 清单

```yaml
# k8s/namespace.yaml
apiVersion: v1
kind: Namespace
metadata:
  name: phoenix-rtc
```

```yaml
# k8s/mysql-deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: mysql
  namespace: phoenix-rtc
spec:
  replicas: 1
  selector:
    matchLabels:
      app: mysql
  template:
    metadata:
      labels:
        app: mysql
    spec:
      containers:
      - name: mysql
        image: mysql:8.0
        ports:
        - containerPort: 3306
        env:
        - name: MYSQL_ROOT_PASSWORD
          valueFrom:
            secretKeyRef:
              name: mysql-secret
              key: root-password
        - name: MYSQL_DATABASE
          value: "phoenix_rtc"
        - name: MYSQL_USER
          value: "phoenix"
        - name: MYSQL_PASSWORD
          valueFrom:
            secretKeyRef:
              name: mysql-secret
              key: password
        volumeMounts:
        - name: mysql-storage
          mountPath: /var/lib/mysql
      volumes:
      - name: mysql-storage
        persistentVolumeClaim:
          claimName: mysql-pvc

---
apiVersion: v1
kind: Service
metadata:
  name: mysql-service
  namespace: phoenix-rtc
spec:
  selector:
    app: mysql
  ports:
  - port: 3306
  type: ClusterIP
```

```yaml
# k8s/app-deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: phoenix-rtc-app
  namespace: phoenix-rtc
spec:
  replicas: 3
  selector:
    matchLabels:
      app: phoenix-rtc
  template:
    metadata:
      labels:
        app: phoenix-rtc
    spec:
      containers:
      - name: app
        image: phoenix-rtc-server:latest
        ports:
        - containerPort: 8080
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "prod"
        - name: JWT_SECRET_KEY
          valueFrom:
            secretKeyRef:
              name: app-secret
              key: jwt-secret
        - name: LIVEKIT_API_KEY
          valueFrom:
            secretKeyRef:
              name: app-secret
              key: livekit-key
        - name: LIVEKIT_API_SECRET
          valueFrom:
            secretKeyRef:
              name: app-secret
              key: livekit-secret
        - name: SPRING_DATASOURCE_PASSWORD
          valueFrom:
            secretKeyRef:
              name: mysql-secret
              key: password
        - name: SPRING_REDIS_PASSWORD
          valueFrom:
            secretKeyRef:
              name: redis-secret
              key: password
        livenessProbe:
          httpGet:
            path: /actuator/health
            port: 8080
          initialDelaySeconds: 60
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /actuator/health
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 5
        resources:
          requests:
            memory: "1Gi"
            cpu: "500m"
          limits:
            memory: "2Gi"
            cpu: "1000m"

---
apiVersion: v1
kind: Service
metadata:
  name: phoenix-rtc-service
  namespace: phoenix-rtc
spec:
  selector:
    app: phoenix-rtc
  ports:
  - port: 80
    targetPort: 8080
  type: LoadBalancer
```

### 5.2 部署命令

```bash
# 创建命名空间
kubectl apply -f k8s/namespace.yaml

# 创建密钥
kubectl create secret generic mysql-secret \
  --from-literal=root-password=strong_root_pass \
  --from-literal=password=strong_mysql_pass \
  -n phoenix-rtc

kubectl create secret generic redis-secret \
  --from-literal=password=strong_redis_pass \
  -n phoenix-rtc

kubectl create secret generic app-secret \
  --from-literal=jwt-secret=strong_jwt_secret \
  --from-literal=livekit-key=your_livekit_key \
  --from-literal=livekit-secret=your_livekit_secret \
  -n phoenix-rtc

# 部署服务
kubectl apply -f k8s/mysql-deployment.yaml
kubectl apply -f k8s/redis-deployment.yaml
kubectl apply -f k8s/livekit-deployment.yaml
kubectl apply -f k8s/app-deployment.yaml

# 检查状态
kubectl get all -n phoenix-rtc

# 查看日志
kubectl logs -f deployment/phoenix-rtc-app -n phoenix-rtc
```

---

## 监控与维护

### 6.1 健康检查

```bash
# 应用健康检查
curl http://localhost:8080/actuator/health

# 数据库连接检查
mysql -h localhost -u phoenix -p -e "SELECT 1;"

# Redis 连接检查
redis-cli -a your_redis_password ping

# LiveKit 连接检查
curl http://localhost:9000/health
```

### 6.2 日志管理

```bash
# 查看应用日志
tail -f /opt/phoenix-rtc/logs/phoenix-rtc.log

# Docker 日志
docker logs -f phoenix_app

# 系统服务日志
journalctl -u phoenix-rtc -f

# 日志轮转配置 (logrotate)
sudo tee /etc/logrotate.d/phoenix-rtc << 'EOF'
/opt/phoenix-rtc/logs/phoenix-rtc.log {
    daily
    rotate 30
    compress
    delaycompress
    missingok
    notifempty
    create 0640 phoenix phoenix
}
EOF
```

### 6.3 性能监控

```bash
# 系统资源监控
htop
iostat -x 1
netstat -tulpn

# JVM 监控
jstat -gcutil $(pgrep -f phoenix-rtc) 1000
jmap -heap $(pgrep -f phoenix-rtc)

# Prometheus 指标
curl http://localhost:8080/actuator/prometheus
```

### 6.4 备份策略

```bash
# MySQL 备份
mysqldump -h localhost -u phoenix -p phoenix_rtc > backup_$(date +%Y%m%d).sql

# Redis 备份
redis-cli -a your_redis_password BGSAVE

# 自动化备份脚本
cat > /opt/phoenix-rtc/backup.sh << 'EOF'
#!/bin/bash
BACKUP_DIR="/opt/phoenix-rtc/backups"
DATE=$(date +%Y%m%d_%H%M%S)

mkdir -p $BACKUP_DIR

# MySQL
mysqldump -h mysql -u phoenix -p$MYSQL_PASSWORD phoenix_rtc > $BACKUP_DIR/mysql_$DATE.sql

# Redis
redis-cli -a $REDIS_PASSWORD BGSAVE
cp /var/lib/redis/dump.rdb $BACKUP_DIR/redis_$DATE.rdb

# 压缩
tar -czf $BACKUP_DIR/full_$DATE.tar.gz $BACKUP_DIR/mysql_$DATE.sql $BACKUP_DIR/redis_$DATE.rdb
rm $BACKUP_DIR/mysql_$DATE.sql $BACKUP_DIR/redis_$DATE.rdb

# 保留最近7天
find $BACKUP_DIR -name "*.tar.gz" -mtime +7 -delete

echo "Backup completed: $BACKUP_DIR/full_$DATE.tar.gz"
EOF

chmod +x /opt/phoenix-rtc/backup.sh

# 添加到 crontab (每天凌晨2点)
0 2 * * * /opt/phoenix-rtc/backup.sh
```

---

## 故障排查

### 7.1 常见问题

#### 问题 1: 应用启动失败

```bash
# 检查日志
tail -n 100 /opt/phoenix-rtc/logs/phoenix-rtc.log

# 常见原因:
# 1. 环境变量未设置
env | grep -E "(JWT|LIVEKIT|MYSQL|REDIS)"

# 2. 端口被占用
netstat -tulpn | grep 8080

# 3. 数据库连接失败
telnet localhost 3306
```

#### 问题 2: LiveKit 连接失败

```bash
# 检查 LiveKit 服务
docker ps | grep livekit
docker logs livekit

# 测试连接
curl http://localhost:9000/health

# 检查配置
echo $LIVEKIT_URL
echo $LIVEKIT_API_KEY
```

#### 问题 3: Redis 连接失败

```bash
# 检查 Redis 状态
systemctl status redis
redis-cli ping

# 测试密码
redis-cli -a your_password ping
```

#### 问题 4: WebSocket 连接失败

```bash
# 检查端口
netstat -tulpn | grep 8080

# 测试 WebSocket
curl -i -N -H "Connection: Upgrade" -H "Upgrade: websocket" \
  -H "Host: localhost:8080" \
  -H "Origin: http://localhost:8080" \
  http://localhost:8080/ws/rtc
```

### 7.2 性能调优

#### JVM 调优

```bash
# 修改启动参数
java -Xms2g -Xmx4g -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=200 \
  -XX:+UnlockExperimentalVMOptions \
  -XX:+UseCGroupMemoryLimitForHeap \
  -jar phoenix-rtc-1.0.0.jar
```

#### MySQL 调优

```ini
# my.cnf
[mysqld]
innodb_buffer_pool_size = 4G
innodb_log_file_size = 512M
max_connections = 500
query_cache_size = 128M
```

#### Redis 调优

```bash
# redis.conf
maxmemory 4gb
maxmemory-policy allkeys-lru
tcp-keepalive 300
timeout 0
```

---

## 部署检查清单

### 部署前检查

- [ ] 环境变量已配置
- [ ] 依赖服务已启动
- [ ] 数据库已创建
- [ ] 端口未被占用
- [ ] 防火墙已配置
- [ ] SSL 证书已准备 (生产环境)
- [ ] 备份策略已设置
- [ ] 监控已配置

### 部署后验证

- [ ] 应用健康检查通过
- [ ] 数据库连接正常
- [ ] Redis 连接正常
- [ ] LiveKit 连接正常
- [ ] WebSocket 连接正常
- [ ] API 接口可访问
- [ ] 认证功能正常
- [ ] 通话功能正常

### 生产环境额外检查

- [ ] 使用强密码和密钥
- [ ] 配置 HTTPS/WSS
- [ ] 限制 CORS 域名
- [ ] 配置日志轮转
- [ ] 设置监控告警
- [ ] 配置负载均衡
- [ ] 准备回滚方案

---

## 相关文档

- **[README.md](README.md)** - 项目介绍
- **[SECURITY_FIXES.md](SECURITY_FIXES.md)** - 安全修复报告
- **[QUICK_REFERENCE.md](QUICK_REFERENCE.md)** - 快速参考
- **[.env.example](.env.example)** - 环境变量模板

---

**最后更新**: 2025-12-26
**版本**: v2.0.0
