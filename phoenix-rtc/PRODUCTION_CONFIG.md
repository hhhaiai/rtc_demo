# Phoenix RTC 生产环境配置指南

本指南详细说明生产环境的安全配置、性能优化和高可用部署。

---

## 🔒 安全配置

### 1.1 环境变量安全

#### 强密码生成

```bash
# 生成 JWT 密钥 (256位)
openssl rand -base64 32

# 生成 MySQL 强密码
openssl rand -base64 16

# 生成 Redis 强密码
openssl rand -base64 16

# 生成 LiveKit 密钥对
# 从 LiveKit 服务端获取，或使用 LiveKit CLI 生成
livekit-cli create-api-key --name production
```

#### 生产环境 .env 文件

```bash
# ============================================
# 数据库配置 (生产环境)
# ============================================
MYSQL_HOST=mysql-cluster.phoenix-rtc.svc.cluster.local
MYSQL_PORT=3306
MYSQL_DATABASE=phoenix_rtc
MYSQL_USER=phoenix
MYSQL_PASSWORD=Prod_MySQL_2025_#Secure123

# ============================================
# Redis 配置 (生产环境 - 集群模式)
# ============================================
REDIS_HOST=redis-cluster.phoenix-rtc.svc.cluster.local
REDIS_PORT=6379
REDIS_PASSWORD=Prod_Redis_2025_#Secure123
REDIS_DATABASE=0

# ============================================
# LiveKit 配置 (生产环境 - 集群)
# ============================================
LIVEKIT_URL=ws://livekit-cluster.phoenix-rtc.svc.cluster.local:7880
LIVEKIT_API_KEY=PLAK_livekit_prod_key_2025
LIVEKIT_API_SECRET=PLS_livekit_prod_secret_2025_very_long_string

# ============================================
# JWT 安全配置 (生产环境)
# ============================================
JWT_SECRET_KEY=Prod_JWT_2025_very_long_secret_key_min_32_chars_required
JWT_EXPIRATION=7200000  # 2小时

# ============================================
# 认证配置 (生产环境 - 集成真实用户系统)
# ============================================
# 注意: 生产环境应移除 demo 认证，集成 OAuth2/OIDC
DEMO_AUTH_PASSWORD=Prod_Demo_2025_#Secure

# ============================================
# 应用配置
# ============================================
SERVER_PORT=8080
SPRING_PROFILES_ACTIVE=prod

# ============================================
# 监控配置
# ============================================
MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,info,metrics,prometheus
MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=when-authorized

# ============================================
# CORS 配置 (生产环境)
# ============================================
CORS_ALLOWED_ORIGINS=https://app.yourdomain.com,https://admin.yourdomain.com
CORS_ALLOWED_METHODS=GET,POST,PUT,DELETE,OPTIONS
CORS_ALLOWED_HEADERS=*
CORS_ALLOW_CREDENTIALS=true
```

### 1.2 数据库安全

#### MySQL 安全配置

```sql
-- 1. 创建专用用户 (不要使用 root)
CREATE USER 'phoenix'@'%' IDENTIFIED BY 'Prod_MySQL_2025_#Secure123';

-- 2. 最小权限原则
GRANT SELECT, INSERT, UPDATE, DELETE ON phoenix_rtc.* TO 'phoenix'@'%';

-- 3. 启用 SSL
ALTER USER 'phoenix'@'%' REQUIRE SSL;

-- 4. 设置密码策略
SET GLOBAL validate_password.policy = 'STRONG';
SET GLOBAL validate_password.length = 12;
SET GLOBAL validate_password.mixed_case_count = 1;
SET GLOBAL validate_password.number_count = 1;
SET GLOBAL validate_password.special_char_count = 1;

-- 5. 审计日志
SET GLOBAL general_log = 'ON';
SET GLOBAL log_output = 'TABLE';
```

#### 数据库连接池配置

```yaml
# application-prod.yml
spring:
  datasource:
    hikari:
      maximum-pool-size: 50
      minimum-idle: 10
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
      leak-detection-threshold: 60000
      pool-name: PhoenixHikariCP
```

### 1.3 Redis 安全

```bash
# redis.conf
requirepass Prod_Redis_2025_#Secure123

# 禁用危险命令
rename-command FLUSHDB ""
rename-command FLUSHALL ""
rename-command DEBUG ""

# 启用 TLS
tls-port 6379
port 0
tls-cert-file /path/to/redis.crt
tls-key-file /path/to/redis.key
tls-ca-cert-file /path/to/ca.crt

# 绑定 IP (仅允许内网)
bind 10.0.0.0/8 172.16.0.0/12 192.168.0.0/16
```

### 1.4 LiveKit 安全

```yaml
# livekit-config.yaml (生产环境)
port: 7880

# TLS 配置
tls:
  cert: /path/to/livekit.crt
  key: /path/to/livekit.key

# API 密钥
keys:
  production_key: production_secret

# 限流
limits:
  # 每个房间最大参与者
  max_participants_per_room: 10000
  # 每个房间最大发布者
  max_publishers_per_room: 1000
  # 连接超时
  connection_timeout: 30s

# Redis (用于集群)
redis:
  address: redis-cluster:6379
  password: Prod_Redis_2025_#Secure123
  db: 0

# WebRTC 配置
webrtc:
  # 端口范围
  port_range_start: 50000
  port_range_end: 60000
  # TCP 复用
  tcp_port: 7881
  # 外部 IP (用于 NAT 穿透)
  external_ip: your-public-ip
```

---

## ⚡ 性能优化

### 2.1 JVM 调优

```bash
# 生产环境 JVM 参数
java -Xms4g -Xmx4g \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=200 \
  -XX:+UnlockExperimentalVMOptions \
  -XX:+UseCGroupMemoryLimitForHeap \
  -XX:+AlwaysPreTouch \
  -XX:+UseStringDeduplication \
  -XX:+OptimizeStringConcat \
  -jar phoenix-rtc-1.0.0.jar \
  --spring.profiles.active=prod
```

### 2.2 数据库优化

```sql
-- 1. 索引优化
CREATE INDEX idx_rtc_session_room_name ON rtc_session(room_name);
CREATE INDEX idx_rtc_session_initiator ON rtc_session(initiator_id);
CREATE INDEX idx_rtc_participant_session ON rtc_participant(session_id);
CREATE INDEX idx_rtc_participant_user ON rtc_participant(user_id);

-- 2. 查询缓存
SET GLOBAL query_cache_size = 268435456;  -- 256MB
SET GLOBAL query_cache_limit = 1048576;   -- 1MB

-- 3. 连接数优化
SET GLOBAL max_connections = 500;
SET GLOBAL thread_cache_size = 50;
```

### 2.3 Redis 优化

```bash
# redis.conf
maxmemory 8gb
maxmemory-policy allkeys-lru
tcp-keepalive 300
timeout 0
tcp-backlog 511

# 启用 AOF 持久化
appendonly yes
appendfsync everysec

# 禁用 RDB (如果使用 AOF)
save ""
```

### 2.4 应用层优化

```yaml
# application-prod.yml
spring:
  # 压缩响应
  compression:
    enabled: true
    mime-types: application/json,application/xml,text/html,text/xml,text/plain
    min-response-size: 1024

  # HTTP/2
  http2:
    enabled: true

  # 异步处理
  task:
    execution:
      pool:
        core-size: 10
        max-size: 50
        queue-capacity: 100
      shutdown:
        await-termination: true
        await-termination-period: 30s

# 服务器优化
server:
  tomcat:
    threads:
      max: 200
      min-spare: 20
    max-connections: 10000
    connection-timeout: 20000
    keep-alive-timeout: 60000
    keep-alive-requests: 100
```

---

## 🏗️ 高可用架构

### 3.1 负载均衡

#### Nginx 配置

```nginx
# /etc/nginx/nginx.conf
upstream phoenix_backend {
    ip_hash;  # 保持会话粘性
    server app1.phoenix-rtc.svc.cluster.local:8080 max_fails=3 fail_timeout=30s;
    server app2.phoenix-rtc.svc.cluster.local:8080 max_fails=3 fail_timeout=30s;
    server app3.phoenix-rtc.svc.cluster.local:8080 max_fails=3 fail_timeout=30s;
}

upstream livekit_backend {
    server livekit1:7880 weight=1;
    server livekit2:7880 weight=1;
}

server {
    listen 80;
    server_name api.yourdomain.com;

    # 重定向到 HTTPS
    return 301 https://$server_name$request_uri;
}

server {
    listen 443 ssl http2;
    server_name api.yourdomain.com;

    # SSL 证书
    ssl_certificate /etc/nginx/ssl/api.yourdomain.com.crt;
    ssl_certificate_key /etc/nginx/ssl/api.yourdomain.com.key;
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers HIGH:!aNULL:!MD5;

    # WebSocket 支持
    map $http_upgrade $connection_upgrade {
        default upgrade;
        '' close;
    }

    location /api/ {
        proxy_pass http://phoenix_backend;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        # WebSocket 支持
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection $connection_upgrade;
        proxy_read_timeout 86400;
    }

    location /ws/ {
        proxy_pass http://phoenix_backend;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_read_timeout 86400;
    }

    # 健康检查
    location /health {
        access_log off;
        proxy_pass http://phoenix_backend/actuator/health;
    }
}
```

### 3.2 数据库集群

#### MySQL 主从复制

```sql
-- 主库配置 (my.cnf)
[mysqld]
server-id = 1
log_bin = mysql-bin
binlog_format = ROW
expire_logs_days = 7

-- 创建复制用户
CREATE USER 'repl'@'%' IDENTIFIED BY 'repl_password';
GRANT REPLICATION SLAVE ON *.* TO 'repl'@'%';

-- 从库配置
[mysqld]
server-id = 2
relay-log = mysql-relay-bin
read_only = 1

-- 启动复制
CHANGE MASTER TO
  MASTER_HOST='mysql-master',
  MASTER_USER='repl',
  MASTER_PASSWORD='repl_password',
  MASTER_LOG_FILE='mysql-bin.000001',
  MASTER_LOG_POS=154;

START SLAVE;
```

### 3.3 Redis 集群

```bash
# 创建 Redis 集群 (6节点: 3主3从)
redis-cli --cluster create \
  redis1:6379 redis2:6379 redis3:6379 \
  redis4:6379 redis5:6379 redis6:6379 \
  --cluster-replicas 1 \
  -a Prod_Redis_2025_#Secure123
```

### 3.4 LiveKit 集群

```yaml
# livekit-cluster.yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: livekit-config
data:
  livekit.yaml: |
    port: 7880
    keys:
      production_key: production_secret
    redis:
      address: redis-cluster:6379
      password: Prod_Redis_2025_#Secure123
    webrtc:
      port_range_start: 50000
      port_range_end: 60000
      external_ip: your-public-ip
    limits:
      max_participants_per_room: 10000
```

---

## 📊 监控告警

### 4.1 Prometheus 配置

```yaml
# prometheus.yml
global:
  scrape_interval: 15s
  evaluation_interval: 15s

scrape_configs:
  - job_name: 'phoenix-rtc'
    static_configs:
      - targets: ['app1:8080', 'app2:8080', 'app3:8080']
    metrics_path: '/actuator/prometheus'
    scrape_interval: 10s

  - job_name: 'mysql'
    static_configs:
      - targets: ['mysql-exporter:9104']

  - job_name: 'redis'
    static_configs:
      - targets: ['redis-exporter:9121']

  - job_name: 'livekit'
    static_configs:
      - targets: ['livekit1:9000', 'livekit2:9000']
```

### 4.2 Grafana 仪表板

```json
{
  "dashboard": {
    "title": "Phoenix RTC Production Monitor",
    "panels": [
      {
        "title": "API 请求速率",
        "targets": [{"expr": "rate(http_requests_total[5m])"}]
      },
      {
        "title": "活跃通话数",
        "targets": [{"expr": "phoenix_rtc_active_calls"}]
      },
      {
        "title": "JVM 内存使用",
        "targets": [{"expr": "jvm_memory_used_bytes"}]
      },
      {
        "title": "数据库连接池",
        "targets": [{"expr": "hikaricp_connections_active"}]
      }
    ]
  }
}
```

### 4.3 告警规则

```yaml
# alert-rules.yml
groups:
- name: phoenix-rtc
  rules:
  - alert: HighErrorRate
    expr: rate(http_requests_total{status=~"5.."}[5m]) > 0.05
    for: 5m
    labels:
      severity: critical
    annotations:
      summary: "High error rate detected"

  - alert: SlowResponse
    expr: histogram_quantile(0.95, rate(http_request_duration_seconds_bucket[5m])) > 1
    for: 5m
    labels:
      severity: warning
    annotations:
      summary: "95th percentile response time > 1s"

  - alert: DatabaseConnectionExhaustion
    expr: hikaricp_connections_active / hikaricp_connections_max > 0.8
    for: 5m
    labels:
      severity: critical
    annotations:
      summary: "Database connection pool > 80%"

  - alert: RedisMemoryHigh
    expr: redis_memory_used_bytes / redis_memory_max_bytes > 0.8
    for: 5m
    labels:
      severity: warning
    annotations:
      summary: "Redis memory usage > 80%"
```

---

## 🛡️ 安全加固

### 5.1 网络安全

```bash
# 配置防火墙 (UFW)
sudo ufw allow 22/tcp    # SSH
sudo ufw allow 80/tcp    # HTTP
sudo ufw allow 443/tcp   # HTTPS
sudo ufw allow 7880/tcp  # LiveKit WebSocket
sudo ufw allow 7881/tcp  # LiveKit TCP
sudo ufw allow 50000:60000/udp  # LiveKit UDP
sudo ufw enable

# 限制访问来源
sudo ufw allow from 10.0.0.0/8 to any port 8080
sudo ufw allow from 192.168.0.0/16 to any port 3306
```

### 5.2 SSL/TLS 配置

```bash
# 使用 Let's Encrypt
sudo apt install certbot python3-certbot-nginx
sudo certbot --nginx -d api.yourdomain.com

# 自动续期
sudo crontab -e
# 添加: 0 12 * * * /usr/bin/certbot renew --quiet
```

### 5.3 应用安全

```yaml
# application-prod.yml
security:
  # CORS 限制
  cors:
    allowed-origins: https://app.yourdomain.com,https://admin.yourdomain.com
    allowed-methods: GET,POST,PUT,DELETE,OPTIONS
    allowed-headers: "Content-Type,Authorization,X-Requested-With"
    allow-credentials: true
    max-age: 3600

  # JWT 配置
  jwt:
    secret: ${JWT_SECRET_KEY}
    expiration: 7200
    refresh-expiration: 86400

  # 限流
  rate-limit:
    enabled: true
    requests-per-minute: 60
    burst-size: 10
```

### 5.4 审计日志

```yaml
# application-prod.yml
logging:
  level:
    com.phoenix.rtc: INFO
    org.springframework.security: INFO
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n"
    file: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n"
  file:
    name: /var/log/phoenix-rtc/app.log
    max-size: 100MB
    max-history: 30
  logback:
    rollingpolicy:
      max-file-size: 100MB
      max-history: 30
      total-size-cap: 10GB
```

---

## 🚀 部署流程

### 6.1 蓝绿部署

```bash
# 1. 部署新版本 (Blue)
kubectl apply -f k8s/app-deployment-blue.yaml

# 2. 等待健康检查通过
kubectl wait --for=condition=available deployment/phoenix-rtc-blue --timeout=300s

# 3. 切换流量
kubectl patch service phoenix-rtc-service -p \
  '{"spec":{"selector":{"app":"phoenix-rtc-blue"}}}'

# 4. 验证新版本
curl https://api.yourdomain.com/actuator/health

# 5. 如果失败，回滚
kubectl patch service phoenix-rtc-service -p \
  '{"spec":{"selector":{"app":"phoenix-rtc-green"}}}'

# 6. 删除旧版本
kubectl delete deployment phoenix-rtc-green
```

### 6.2 滚动更新

```yaml
# app-deployment.yaml
spec:
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 1
      maxUnavailable: 0
  minReadySeconds: 30
  template:
    spec:
      containers:
      - name: app
        image: phoenix-rtc-server:latest
        readinessProbe:
          httpGet:
            path: /actuator/health
            port: 8080
          initialDelaySeconds: 60
          periodSeconds: 5
          failureThreshold: 3
```

### 6.3 回滚方案

```bash
# 查看历史版本
kubectl rollout history deployment/phoenix-rtc-app

# 回滚到上一个版本
kubectl rollout undo deployment/phoenix-rtc-app

# 回滚到指定版本
kubectl rollout undo deployment/phoenix-rtc-app --to-revision=3

# 查看回滚状态
kubectl rollout status deployment/phoenix-rtc-app
```

---

## 📈 容量规划

### 7.1 资源估算

| 用户规模 | 应用实例 | CPU | 内存 | 数据库 | Redis | LiveKit |
|----------|----------|-----|------|--------|-------|---------|
| **1000** | 1-2 | 2核 | 4GB | 2核4GB | 1核2GB | 2核4GB |
| **10000** | 3-5 | 4核 | 8GB | 4核8GB | 2核4GB | 4核8GB |
| **100000** | 10-15 | 8核 | 16GB | 8核16GB | 4核8GB | 8核16GB |

### 7.2 自动扩缩容

```yaml
# hpa.yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: phoenix-rtc-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: phoenix-rtc-app
  minReplicas: 3
  maxReplicas: 20
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
  - type: Resource
    resource:
      name: memory
      target:
        type: Utilization
        averageUtilization: 80
  - type: Pods
    pods:
      metric:
        name: http_requests_per_second
      target:
        type: AverageValue
        averageValue: "1000"
```

---

## 🎯 生产环境检查清单

### 部署前

- [ ] **安全配置**
  - [ ] 所有密码已使用强密码
  - [ ] JWT 密钥至少 32 字符
  - [ ] LiveKit 密钥已更新
  - [ ] CORS 已限制域名
  - [ ] HTTPS 已配置

- [ ] **基础设施**
  - [ ] 数据库集群已搭建
  - [ ] Redis 集群已配置
  - [ ] LiveKit 集群已部署
  - [ ] 负载均衡已配置
  - [ ] 防火墙已设置

- [ ] **监控告警**
  - [ ] Prometheus 已部署
  - [ ] Grafana 仪表板已创建
  - [ ] 告警规则已配置
  - [ ] 日志收集已设置

- [ ] **备份策略**
  - [ ] 数据库自动备份
  - [ ] Redis 持久化配置
  - [ ] 配置文件备份
  - [ ] 灾难恢复计划

### 部署后

- [ ] **功能验证**
  - [ ] 认证功能正常
  - [ ] 通话创建正常
  - [ ] 媒体连接正常
  - [ ] WebSocket 连接正常
  - [ ] 离线重连正常

- [ ] **性能测试**
  - [ ] 压力测试通过
  - [ ] P99 延迟 < 1s
  - [ ] CPU 使用率 < 70%
  - [ ] 内存使用率 < 80%
  - [ ] 数据库连接池正常

- [ ] **高可用验证**
  - [ ] 节点故障转移测试
  - [ ] 数据库主从切换测试
  - [ ] Redis 故障转移测试
  - [ ] 负载均衡健康检查

---

## 🔧 维护操作

### 8.1 日常维护

```bash
# 每日检查
./scripts/daily-check.sh

# 每周清理旧日志
find /var/log/phoenix-rtc/ -name "*.log" -mtime +7 -delete

# 每月备份
./scripts/monthly-backup.sh
```

### 8.2 紧急响应

```bash
# 1. 查看状态
kubectl get all -n phoenix-rtc

# 2. 查看日志
kubectl logs -f deployment/phoenix-rtc-app -n phoenix-rtc

# 3. 快速扩容
kubectl scale deployment/phoenix-rtc-app --replicas=5 -n phoenix-rtc

# 4. 重启服务
kubectl rollout restart deployment/phoenix-rtc-app -n phoenix-rtc
```

---

## 📞 技术支持

如需技术支持，请提供以下信息：

1. **环境信息**: 版本、部署方式
2. **日志文件**: 相关错误日志
3. **监控数据**: Grafana 截图或导出
4. **配置文件**: 相关配置 (脱敏后)
5. **复现步骤**: 问题重现方法

---

**最后更新**: 2025-12-26
**版本**: v2.0.0
**维护**: Phoenix RTC 团队
