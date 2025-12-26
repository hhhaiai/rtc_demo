# Phoenix RTC 测试指南

## 🧪 测试清单

### 1. 基础设施测试

```bash
# 测试 LiveKit
curl http://localhost:7880/health

# 测试 Redis
redis-cli ping

# 测试 MySQL
mysql -u phoenix -pphoenix123 -h localhost -e "SELECT 1"

# 测试后端健康检查
curl http://localhost:8080/actuator/health
```

### 2. API 接口测试

#### 2.1 发起通话

```bash
curl -X POST http://localhost:8080/api/rtc/call/start \
  -H "Content-Type: application/json" \
  -d '{
    "targetUserIds": ["user2"],
    "sessionType": "video",
    "title": "测试通话"
  }'
```

**预期响应：**
```json
{
  "success": true,
  "data": {
    "url": "ws://localhost:7880",
    "token": "eyJhbGciOi...",
    "roomName": "room_xxx",
    "expiresAt": 1704067200
  }
}
```

#### 2.2 加入通话

```bash
curl -X POST http://localhost:8080/api/rtc/call/join \
  -H "Content-Type: application/json" \
  -d '{"roomName": "room_xxx"}'
```

#### 2.3 Webhook 测试

```bash
curl -X POST http://localhost:8080/api/rtc/webhook \
  -H "Content-Type: application/json" \
  -d '{
    "event": "room_finished",
    "room": {"name": "room_xxx"},
    "created_at": 1704067200
  }'
```

### 3. WebSocket 信令测试

使用 `wscat` 工具：

```bash
npm install -g wscat

# 连接 WebSocket
wscat -c ws://localhost:8080/ws/rtc

# 发送消息
> {"type":"rtc","cmd":"invite","data":{"roomId":"room_123","inviterId":"user1","mode":"video"}}
```

### 4. 端到端测试流程

#### 场景 1: 1v1 视频通话

**步骤：**
1. 用户 A 发起通话给用户 B
2. 用户 B 收到邀请
3. 用户 B 接听
4. 双方建立连接
5. 通话 10 秒
6. 用户 A 挂断

**验证点：**
- ✅ 用户 B 收到 `ringing` 消息
- ✅ Token 生成成功
- ✅ LiveKit 房间创建成功
- ✅ 双方能听到/看到对方
- ✅ 挂断后数据库记录正确

#### 场景 2: 多人会议

**步骤：**
1. 用户 A 创建群聊房间
2. 用户 B、C 加入
3. 三人通话 30 秒
4. 用户 B 离开
5. 用户 A 结束通话

**验证点：**
- ✅ 房间成员列表正确
- ✅ 音视频流正常分发
- ✅ 离开后资源正确释放

#### 场景 3: 直播模式

**步骤：**
1. 用户 A 创建直播 (host)
2. 用户 B、C 作为观众加入
3. 用户 A 推流
4. 用户 B 申请连麦
5. 用户 A 同意连麦

**验证点：**
- ✅ 观众只能接收不能发送
- ✅ 连麦后权限更新
- ✅ 主播能控制观众权限

### 5. 性能测试

使用 k6 进行压力测试：

```javascript
// test-load.js
import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  stages: [
    { duration: '30s', target: 100 },  // 100 用户
    { duration: '1m', target: 500 },   // 500 用户
    { duration: '2m', target: 1000 },  // 1000 用户
  ],
};

export default function () {
  const res = http.post('http://localhost:8080/api/rtc/call/start', JSON.stringify({
    targetUserIds: ['user2'],
    sessionType: 'video',
  }), {
    headers: { 'Content-Type': 'application/json' },
  });

  check(res, {
    'status is 200': (r) => r.status === 200,
    'has token': (r) => JSON.parse(r.body).data.token !== undefined,
  });

  sleep(1);
}
```

运行测试：
```bash
k6 run test-load.js
```

### 6. 客户端测试

#### React Native

```bash
cd client-mobile
npm test

# iOS 测试
npm run ios

# Android 测试
npm run android
```

#### Electron

```bash
cd client-pc
npm test
npm run start
```

### 7. 自动化测试脚本

创建 `scripts/test-e2e.sh`：

```bash
#!/bin/bash

echo "开始端到端测试..."

# 启动服务
docker-compose up -d
sleep 10

# 启动后端
cd server
java -jar target/phoenix-rtc-server-1.0.0.jar &
BACKEND_PID=$!
sleep 15

# 测试 API
echo "测试 API..."
curl -f -X POST http://localhost:8080/api/rtc/call/start \
  -H "Content-Type: application/json" \
  -d '{"targetUserIds":["test"],"sessionType":"video"}' || exit 1

echo "✓ API 测试通过"

# 清理
kill $BACKEND_PID
docker-compose down

echo "所有测试通过！"
```

### 8. 测试覆盖率目标

| 模块 | 覆盖率目标 |
|------|-----------|
| 服务端业务逻辑 | > 80% |
| WebSocket 处理 | > 70% |
| LiveKit 集成 | > 90% |
| 客户端 Hooks | > 75% |
| UI 组件 | > 60% |

### 9. 常见测试问题

**问题 1: WebSocket 连接失败**
- 检查端口是否被占用
- 验证 CORS 配置
- 查看浏览器控制台错误

**问题 2: 音视频无法工作**
- 检查浏览器权限
- 验证 TURN 服务器配置
- 查看 LiveKit 日志

**问题 3: Token 过期**
- 检查时间戳计算
- 验证 JWT 签名
- 检查 Redis TTL

### 10. 测试报告

每次测试后生成报告：

```bash
# 服务端测试报告
cd server
mvn test jacoco:report
open target/site/jacoco/index.html

# 性能测试报告
k6 run --out json=test-results.json test-load.js
```

---

**测试通过标准：**
- ✅ 所有 API 返回正确
- ✅ WebSocket 信令正常
- ✅ 音视频流建立成功
- ✅ 通话状态转换正确
- ✅ 资源清理无泄漏
- ✅ 并发测试通过
