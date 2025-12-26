

## 一、系统全景架构

```mermaid
graph TB
    subgraph Client["客户端层"]
        RN["React Native App<br/>RTC SDK + WebSocket"]
        Electron["Electron App<br/>RTC SDK + WebSocket"]
    end
    
    subgraph Gateway["网关层 - Module 1"]
        WSGateway["WebSocket Gateway<br/>• 连接管理<br/>• 消息路由<br/>• 身份验证"]
    end
    
    subgraph Application["应用层"]
        IMService["IM Service<br/>Module 2<br/>现有业务"]
        
        RTCSignal["RTC Signaling<br/>Module 3<br/>信令协调"]
        
        RTCRoom["RTC Room Manager<br/>Module 4<br/>房间生命周期"]
    end
    
    subgraph MediaAbstraction["媒体抽象层"]
        MediaAPI["Media Capability API<br/>Module 5<br/>媒体能力统一接口"]
    end
    
    subgraph MediaImpl["媒体实现层"]
        KurentoAdapter["Kurento Adapter<br/>Module 6<br/>KMS适配器"]
    end
    
    subgraph Infrastructure["基础设施"]
        Redis["Redis<br/>会话/房间/临时状态"]
        MySQL["MySQL<br/>历史/用户/配置"]
        KMS["Kurento Media Server"]
    end
    
    subgraph Frontend["前端模块 - Module 7"]
        RNSDK["RN WebRTC SDK"]
        ElectronSDK["Electron WebRTC SDK"]
    end
    
    RN --> WSGateway
    Electron --> WSGateway
    
    WSGateway --> IMService
    WSGateway --> RTCSignal
    
    RTCSignal --> RTCRoom
    RTCRoom --> MediaAPI
    MediaAPI --> KurentoAdapter
    KurentoAdapter --> KMS
    
    IMService --> Redis
    RTCSignal --> Redis
    RTCRoom --> Redis
    RTCRoom --> MySQL
    
    RN -.->|WebRTC媒体流| KMS
    Electron -.->|WebRTC媒体流| KMS
    
    RNSDK -.-> RN
    ElectronSDK -.-> Electron
```

---

## 二、模块分解与 AI 任务编排

### **Module 1: WebSocket Gateway（网关层）**

**职责边界**：
- 管理 WebSocket 连接生命周期
- 按消息类型路由到对应服务
- 统一的异常处理和限流

**数据结构设计**：

```mermaid
graph LR
    subgraph Messages["消息协议"]
        IM["IM消息<br/>{type:'im', ...}"]
        RTC["RTC消息<br/>{type:'rtc', ...}"]
        State["状态消息<br/>{type:'state', ...}"]
    end
    
    subgraph Router["路由器"]
        Validator["1. 格式验证"]
        Auth["2. 身份校验"]
        RateLimit["3. 限流检查"]
        Dispatch["4. 分发处理"]
    end
    
    subgraph Handlers["处理器"]
        IMHandler["IM处理器"]
        RTCHandler["RTC处理器"]
        StateHandler["状态处理器"]
    end
    
    IM --> Validator
    RTC --> Validator
    State --> Validator
    
    Validator --> Auth
    Auth --> RateLimit
    RateLimit --> Dispatch
    
    Dispatch --> IMHandler
    Dispatch --> RTCHandler
    Dispatch --> StateHandler
```

**AI 任务清单**：
```yaml
Task 1.1: 实现 WebSocket 连接管理器
  - 输入: Spring Boot WebSocket 配置
  - 输出: WebSocketHandler.java（连接/断开/心跳）
  - 验证: 1000 并发连接压测

Task 1.2: 实现消息路由器
  - 输入: 消息协议 JSON Schema
  - 输出: MessageRouter.java（路由逻辑 + 限流）
  - 验证: 单元测试覆盖率 > 90%

Task 1.3: 实现会话管理器
  - 输入: Redis 会话结构设计
  - 输出: SessionManager.java（创建/恢复/清理）
  - 验证: 断线重连测试用例
```

---

### **Module 2: IM Service（现有服务保持不变）**

**设计原则**：
- 只需要增加一个 **RTC 通话邀请消息类型**
- 不改动现有消息处理逻辑

**集成点设计**：

```mermaid
sequenceDiagram
    participant User as 用户A
    participant IM as IM Service
    participant RTC as RTC Signaling
    participant Target as 用户B

    User->>IM: 发起通话请求
    IM->>IM: 验证好友关系
    IM->>RTC: 调用创建房间
    RTC-->>IM: 返回 roomId
    IM->>Target: 推送通话邀请<br/>(含 roomId)
    Target-->>IM: 接听/拒绝
    IM->>IM: 记录通话消息
```

**AI 任务清单**：
```yaml
Task 2.1: 扩展 IM 消息类型
  - 输入: 现有消息类型枚举
  - 输出: 新增 RTC_CALL_INVITE / RTC_CALL_ANSWER
  - 验证: 消息能正常发送和接收

Task 2.2: 实现 RTC 服务调用接口
  - 输入: RTC Service API 定义
  - 输出: RTCServiceClient.java
  - 验证: Mock 测试
```

---

### **Module 3: RTC Signaling（信令协调器）**

**核心职责**：
- 处理 SDP Offer/Answer
- 转发 ICE Candidate
- 协调房间成员加入/离开

**状态机设计**：

```mermaid
stateDiagram-v2
    [*] --> Idle: 初始状态
    
    Idle --> Calling: 发起通话
    Calling --> Connecting: 对方接听
    Calling --> Idle: 对方拒绝/超时
    
    Connecting --> Connected: ICE连接成功
    Connecting --> Failed: ICE失败
    
    Connected --> Closing: 一方挂断
    Closing --> Idle: 资源清理完成
    
    Failed --> Idle: 清理失败状态
```

**数据流设计**：

```mermaid
graph TB
    subgraph Input["输入"]
        Offer["SDP Offer<br/>from Client"]
        Answer["SDP Answer<br/>from Client"]
        Ice["ICE Candidate<br/>from Client"]
    end
    
    subgraph Processing["处理逻辑"]
        ValidateRoom["验证房间状态"]
        CheckEndpoint["检查 Endpoint 是否存在"]
        CallKMS["调用 KMS API"]
        UpdateRedis["更新 Redis 状态"]
    end
    
    subgraph Output["输出"]
        ToClient["返回给客户端"]
        ToOthers["广播给房间其他成员"]
        ToKMS["转发给 KMS"]
    end
    
    Offer --> ValidateRoom
    Answer --> ValidateRoom
    Ice --> CheckEndpoint
    
    ValidateRoom --> CallKMS
    CheckEndpoint --> CallKMS
    
    CallKMS --> UpdateRedis
    UpdateRedis --> ToClient
    UpdateRedis --> ToOthers
    CallKMS --> ToKMS
```

**AI 任务清单**：
```yaml
Task 3.1: 实现 SDP 协商处理器
  - 输入: WebRTC SDP 格式规范
  - 输出: SDPNegotiator.java（offer/answer 处理）
  - 验证: 单元测试 + Postman 模拟客户端

Task 3.2: 实现 ICE Candidate 转发器
  - 输入: ICE 协议文档
  - 输出: IceCandidateHandler.java
  - 验证: 测试 P2P 和 TURN 场景

Task 3.3: 实现状态机管理器
  - 输入: 上述状态图
  - 输出: CallStateMachine.java
  - 验证: 状态转换测试用例
```

---

### **Module 4: RTC Room Manager（房间生命周期）**

**职责边界**：
- 创建/销毁房间
- 管理房间成员列表
- 处理房间类型切换（1v1 → 多人）

**房间类型设计**：

```mermaid
graph TB
    subgraph RoomTypes["房间类型"]
        P2P["1v1 点对点<br/>2人专用"]
        MCU["多人会议<br/>混流模式"]
        SFU["多人会议<br/>选择转发"]
        Broadcast["直播模式<br/>1播N看"]
    end
    
    subgraph Capabilities["媒体能力"]
        P2PMedia["双向音视频"]
        MCUMedia["音视频混流<br/>一路输出"]
        SFUMedia["N路独立流<br/>客户端选择"]
        BroadMedia["单向推流<br/>低延迟"]
    end
    
    P2P --> P2PMedia
    MCU --> MCUMedia
    SFU --> SFUMedia
    Broadcast --> BroadMedia
```

**Redis 数据结构**：

```yaml
# 房间元数据
rtc:room:{roomId}:meta
  roomType: "p2p" | "mcu" | "sfu" | "broadcast"
  creatorId: "user123"
  maxMembers: 50
  createdAt: timestamp
  status: "active" | "ended"
  pipelineId: "kms_pipeline_id"

# 房间成员（使用 Redis Set）
rtc:room:{roomId}:members
  → Set { "user1", "user2", "user3" }

# 成员详细信息（使用 Redis Hash）
rtc:room:{roomId}:member:{userId}
  endpointId: "kms_endpoint_abc"
  role: "publisher" | "subscriber"
  joinedAt: timestamp
  audioEnabled: true
  videoEnabled: false

# 房间临时状态（TTL 1 hour）
rtc:room:{roomId}:temp
  lastActivity: timestamp
  recordingPath: "/recordings/room123.webm"
```

**AI 任务清单**：
```yaml
Task 4.1: 实现房间创建器
  - 输入: 房间类型枚举 + Redis 结构
  - 输出: RoomFactory.java
  - 验证: 创建各类型房间并检查 Redis

Task 4.2: 实现成员管理器
  - 输入: 成员加入/离开事件
  - 输出: MemberManager.java
  - 验证: 并发加入/离开测试

Task 4.3: 实现房间清理器
  - 输入: 房间空闲超时策略
  - 输出: RoomCleaner.java（定时任务）
  - 验证: 空房间自动销毁测试
```

---

### **Module 5: Media Capability API（媒体能力抽象层）**

**设计目标**：
- 屏蔽底层媒体服务器差异
- 未来可切换到 LiveKit/SRS

**接口设计**：

```mermaid
graph TB
    subgraph API["统一 API"]
        CreatePipeline["createPipeline()<br/>创建媒体管道"]
        CreateEndpoint["createEndpoint()<br/>创建端点"]
        ConnectEndpoints["connect()<br/>连接端点"]
        ProcessSDP["processSDP()<br/>处理 SDP"]
        AddIceCandidate["addIceCandidate()<br/>添加 ICE"]
        StartRecording["startRecording()<br/>开始录制"]
        Release["release()<br/>释放资源"]
    end
    
    subgraph Adapters["适配器实现"]
        KurentoImpl["Kurento Adapter"]
        LiveKitImpl["LiveKit Adapter<br/>(future)"]
        SRSImpl["SRS Adapter<br/>(future)"]
    end
    
    API --> KurentoImpl
    API -.-> LiveKitImpl
    API -.-> SRSImpl
```

**接口定义示例**：

```java
public interface MediaService {
    
    // 创建媒体管道
    CompletableFuture<String> createPipeline(PipelineConfig config);
    
    // 创建端点
    CompletableFuture<Endpoint> createEndpoint(String pipelineId, EndpointType type);
    
    // 连接两个端点
    CompletableFuture<Void> connectEndpoints(String endpoint1, String endpoint2);
    
    // 处理 SDP
    CompletableFuture<String> processSdpOffer(String endpointId, String sdpOffer);
    
    // 添加 ICE Candidate
    CompletableFuture<Void> addIceCandidate(String endpointId, IceCandidate candidate);
    
    // 开始录制
    CompletableFuture<String> startRecording(String pipelineId, RecordingConfig config);
    
    // 释放资源
    CompletableFuture<Void> releaseEndpoint(String endpointId);
    CompletableFuture<Void> releasePipeline(String pipelineId);
}
```

**AI 任务清单**：
```yaml
Task 5.1: 定义 Media Service 接口
  - 输入: 上述 UML 图
  - 输出: MediaService.java 接口定义
  - 验证: 接口方法签名评审

Task 5.2: 定义数据传输对象
  - 输入: Kurento API 文档
  - 输出: DTO 类（PipelineConfig, Endpoint, IceCandidate 等）
  - 验证: JSON 序列化/反序列化测试
```

---

### **Module 6: Kurento Adapter（KMS 适配器）**

**职责边界**：
- 实现 Media Service 接口
- 管理与 KMS 的 WebSocket 连接
- 处理 KMS 事件回调

**连接管理设计**：

```mermaid
graph TB
    subgraph Adapter["Kurento Adapter"]
        Client["KurentoClient<br/>连接管理"]
        Pipeline["Pipeline Manager<br/>管道池"]
        Endpoint["Endpoint Manager<br/>端点池"]
        Event["Event Listener<br/>事件处理"]
    end
    
    subgraph KMS["Kurento Media Server"]
        KMSWS["WebSocket<br/>JSON-RPC"]
        Media["Media Pipeline"]
    end
    
    Client -->|create| Pipeline
    Pipeline -->|create| Endpoint
    Endpoint -->|process| Media
    
    KMSWS -.->|events| Event
    Event -.->|callback| Adapter
    
    Client <-->|JSON-RPC| KMSWS
```

**错误处理策略**：

```mermaid
graph TB
    subgraph Errors["KMS 错误类型"]
        Timeout["请求超时"]
        NotFound["资源不存在"]
        ServerError["服务器内部错误"]
        NetworkError["网络连接失败"]
    end
    
    subgraph Handling["处理策略"]
        Retry["重试机制<br/>最多 3 次"]
        Fallback["降级方案<br/>返回错误给客户端"]
        Cleanup["清理资源<br/>释放 Endpoint"]
        Alert["告警通知<br/>记录日志"]
    end
    
    Timeout --> Retry
    NotFound --> Cleanup
    ServerError --> Fallback
    NetworkError --> Retry
    
    Retry -->|失败| Alert
    Fallback --> Alert
```

**AI 任务清单**：
```yaml
Task 6.1: 实现 Kurento Client 封装
  - 输入: KurentoClient Java SDK 文档
  - 输出: KurentoClientWrapper.java
  - 验证: 连接/断线/重连测试

Task 6.2: 实现 MediaService 接口
  - 输入: Module 5 的接口定义
  - 输出: KurentoMediaServiceImpl.java
  - 验证: 所有接口方法单元测试

Task 6.3: 实现事件监听器
  - 输入: KMS 事件文档（IceCandidateFound, MediaStateChanged）
  - 输出: KurentoEventHandler.java
  - 验证: Mock KMS 事件触发测试

Task 6.4: 实现资源池管理
  - 输入: 对象池设计模式
  - 输出: PipelinePool.java, EndpointPool.java
  - 验证: 并发创建/释放压测
```

---

### **Module 7: Frontend SDK（前端模块）**

**React Native SDK 设计**：

```mermaid
graph TB
    subgraph RN["React Native"]
        UI["UI Components"]
        Manager["Call Manager"]
        WebRTC["react-native-webrtc"]
        WS["WebSocket Client"]
    end
    
    subgraph States["状态管理"]
        CallState["通话状态"]
        MediaState["媒体状态<br/>音视频开关"]
        NetworkState["网络状态"]
    end
    
    UI --> Manager
    Manager --> CallState
    Manager --> WebRTC
    Manager --> WS
    
    CallState --> MediaState
    MediaState --> NetworkState
```

**前端状态机**：

```mermaid
stateDiagram-v2
    [*] --> Idle: 应用启动
    
    Idle --> Ringing: 收到来电
    Idle --> Dialing: 发起通话
    
    Ringing --> Idle: 拒绝接听
    Ringing --> Connecting: 接听
    
    Dialing --> Connecting: 对方接听
    Dialing --> Idle: 取消/对方拒绝
    
    Connecting --> Connected: 媒体连接成功
    Connecting --> Failed: 连接失败
    
    Connected --> Reconnecting: 网络中断
    Reconnecting --> Connected: 恢复连接
    Reconnecting --> Failed: 重连失败
    
    Connected --> Ending: 挂断
    Failed --> Ending: 清理资源
    
    Ending --> Idle: 完成
```

**AI 任务清单**：
```yaml
Task 7.1: 实现 RN WebRTC 封装
  - 输入: react-native-webrtc 文档
  - 输出: RTCManager.ts（createOffer/setRemoteDescription 等）
  - 验证: iOS/Android 真机测试

Task 7.2: 实现 Call Manager
  - 输入: 前端状态机图
  - 输出: CallManager.ts（状态转换逻辑）
  - 验证: 状态转换单元测试

Task 7.3: 实现信令客户端
  - 输入: WebSocket 消息协议
  - 输出: SignalingClient.ts
  - 验证: Mock 服务器测试

Task 7.4: 实现 UI 组件
  - 输入: 设计稿
  - 输出: CallScreen.tsx, VideoView.tsx
  - 验证: UI 截图对比测试

Task 7.5: Electron 适配
  - 输入: RN 实现代码
  - 输出: Electron 版本适配代码
  - 验证: Windows/macOS 测试
```

---

## 三、数据存储详细设计

### **Redis 数据结构完整定义**

```yaml
# WebSocket 会话（TTL: 90秒，心跳刷新）
ws:session:{sessionId}
  userId: "user123"
  connectedAt: timestamp
  lastHeartbeat: timestamp
  clientType: "rn" | "electron"
  clientVersion: "1.0.0"

# 用户在线状态（TTL: 动态更新）
user:{userId}:online
  sessionId: "session_abc"
  status: "online" | "busy" | "offline"
  lastSeen: timestamp

# RTC 房间元数据（TTL: 1小时）
rtc:room:{roomId}:meta
  roomType: "p2p" | "mcu" | "sfu" | "broadcast"
  creatorId: "user123"
  maxMembers: 50
  createdAt: timestamp
  status: "waiting" | "active" | "ended"
  pipelineId: "kms_pipeline_xyz"

# RTC 房间成员列表（Set）
rtc:room:{roomId}:members
  → Set { "user1", "user2", "user3" }

# 成员详细信息（Hash）
rtc:room:{roomId}:member:{userId}
  endpointId: "endpoint_abc"
  role: "publisher" | "subscriber"
  joinedAt: timestamp
  audioEnabled: true
  videoEnabled: true
  screenShareEnabled: false

# RTC 会话映射（双向索引）
rtc:session:{sessionId}
  userId: "user123"
  roomId: "room456"
  endpointId: "endpoint_abc"

rtc:user:{userId}:session
  → Set { "session1", "session2" }  # 支持多端同时在线

# 通话邀请缓存（TTL: 60秒）
rtc:invite:{inviteId}
  fromUserId: "user1"
  toUserId: "user2"
  roomId: "room123"
  createdAt: timestamp
  status: "pending" | "accepted" | "rejected" | "timeout"

# 限流计数器（TTL: 1分钟）
ratelimit:rtc:{userId}
  count: 10  # 1分钟内发起通话次数
```

### **MySQL 表结构设计**

```sql
-- 房间历史表
CREATE TABLE rtc_rooms (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    room_id VARCHAR(64) UNIQUE NOT NULL,
    room_type ENUM('p2p', 'mcu', 'sfu', 'broadcast') NOT NULL,
    creator_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL,
    ended_at DATETIME,
    duration_seconds INT,
    status ENUM('active', 'ended', 'error') NOT NULL,
    max_members INT DEFAULT 50,
    metadata JSON,
    INDEX idx_creator (creator_id),
    INDEX idx_created (created_at),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 房间成员表
CREATE TABLE rtc_room_members (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    room_id VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    role ENUM('publisher', 'subscriber') NOT NULL,
    joined_at DATETIME NOT NULL,
    left_at DATETIME,
    duration_seconds INT,
    audio_enabled BOOLEAN DEFAULT TRUE,
    video_enabled BOOLEAN DEFAULT TRUE,
    INDEX idx_room (room_id),
    INDEX idx_user (user_id),
    INDEX idx_joined (joined_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 录制文件表
CREATE TABLE rtc_recordings (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    room_id VARCHAR(64) NOT NULL,
    file_path VARCHAR(512) NOT NULL,
    file_size_bytes BIGINT,
    duration_seconds INT,
    started_at DATETIME NOT NULL,
    ended_at DATETIME,
    status ENUM('recording', 'completed', 'failed') NOT NULL,
    codec VARCHAR(32),
    resolution VARCHAR(16),
    participants JSON,
    INDEX idx_room (room_id),
    INDEX idx_started (started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 通话质量统计
CREATE TABLE rtc_quality_stats (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    room_id VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    timestamp DATETIME NOT NULL,
    packet_loss_rate DECIMAL(5,2),
    jitter_ms INT,
    rtt_ms INT,
    bitrate_kbps INT,
    INDEX idx_room_time (room_id, timestamp),
    INDEX idx_user_time (user_id, timestamp)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

---

## 四、关键流程设计

### **流程 1: 1v1 通话完整流程**

```mermaid
sequenceDiagram
    participant A as 用户A<br/>(RN)
    participant GW as WebSocket<br/>Gateway
    participant Signal as RTC<br/>Signaling
    participant Room as Room<br/>Manager
    participant Media as Media<br/>API
    participant KMS as Kurento<br/>Adapter
    participant B as 用户B<br/>(Electron)

    Note over A: 1. 准备阶段
    A->>GW: 已连接 WebSocket
    B->>GW: 已连接 WebSocket

    Note over A,B: 2. 发起通话
    A->>GW: {type:'rtc', action:'call', to:'userB'}
    GW->>Signal: 路由 RTC 消息
    Signal->>Room: 创建房间(type=p2p)
    Room->>Media: createPipeline()
    Media->>KMS: 创建 MediaPipeline
    KMS-->>Media: pipelineId
    Media->>KMS: createEndpoint(pipelineId)
    KMS-->>Media: endpointA
    Media-->>Room: 返回 endpointA
    Room->>Room: 保存到 Redis<br/>rtc:room:{roomId}
    Room-->>Signal: roomId + endpointA
    Signal->>GW: 通过 IM 推送邀请
    GW-->>B: {type:'rtc_invite', roomId, from:'userA'}

    Note over B: 3. 接听通话
    B->>GW: {type:'rtc', action:'answer', roomId}
    GW->>Signal: 路由响应
    Signal->>Room: 添加成员 B
    Room->>Media: createEndpoint(pipelineId)
    Media->>KMS: 创建 endpointB
    KMS-->>Media: endpointB
    Media->>KMS: connect(endpointA, endpointB)
    KMS-->>Media: 连接成功
    Media-->>Room: endpointB
    Room->>Room: 更新 Redis
    Room-->>Signal: endpointB
    Signal-->>GW: 通知 A 已接听
    GW-->>A: {type:'rtc', action:'accepted', roomId}

    Note over A,B: 4. SDP 协商
    A->>A: 创建 RTCPeerConnection
    A->>A: createOffer()
    A->>GW: {type:'rtc', action:'offer', sdp}
    GW->>Signal: 路由 SDP
    Signal->>Media: processSdpOffer(endpointA, sdp)
    Media->>KMS: processOffer(endpointA, sdp)
    KMS-->>Media: sdpAnswer
    Media-->>Signal: sdpAnswer
    Signal-->>GW: 返回 answer
    GW-->>A: {type:'rtc', action:'answer', sdp}
    A->>A: setRemoteDescription(sdp)

    B->>B: 创建 RTCPeerConnection
    B->>B: createOffer()
    B->>GW: {type:'rtc', action:'offer', sdp}
    GW->>Signal: 路由
    Signal->>Media: processSdpOffer(endpointB, sdp)
    Media->>KMS: processOffer(endpointB, sdp)
    KMS-->>Media: sdpAnswer
    Media-->>Signal: sdpAnswer
    Signal-->>GW: 返回
    GW-->>B: {type:'rtc', action:'answer', sdp}
    B->>B: setRemoteDescription(sdp)

    Note over A,B: 5. ICE 连接
    A->>A: 收集 ICE candidates
    loop 每个 candidate
A->>GW: {type:'rtc', action:'candidate', candidate}
        GW->>Signal: 路由
        Signal->>Media: addIceCandidate(endpointA, candidate)
        Media->>KMS: addIceCandidate()
    end

    B->>B: 收集 ICE candidates
    loop 每个 candidate
        B->>GW: {type:'rtc', action:'candidate', candidate}
        GW->>Signal: 路由
        Signal->>Media: addIceCandidate(endpointB, candidate)
        Media->>KMS: addIceCandidate()
    end

    Note over A,KMS: 6. 媒体流建立
    A->>KMS: RTP 音视频流
    KMS->>B: 转发给 B
    B->>KMS: RTP 音视频流
    KMS->>A: 转发给 A

    Note over A,B: 7. 通话中（心跳维持）
    loop 每 30 秒
        A->>GW: {type:'heartbeat'}
        GW->>GW: 刷新 Redis TTL
    end

    Note over A: 8. 挂断通话
    A->>GW: {type:'rtc', action:'leave', roomId}
    GW->>Signal: 路由
    Signal->>Room: 移除成员 A
    Room->>Media: releaseEndpoint(endpointA)
    Media->>KMS: release(endpointA)
    Room->>Room: 检查房间是否为空
    Room->>Media: releasePipeline(pipelineId)
    Media->>KMS: release(pipeline)
    Room->>Room: 标记房间结束
    Room->>Room: 持久化到 MySQL
    Room->>Room: 删除 Redis 数据
    Signal-->>GW: 通知 B 断开
    GW-->>B: {type:'rtc', action:'peer_left'}
    B->>B: 关闭 RTCPeerConnection
```

---

### **流程 2: 多人会议（MCU 模式）**

```mermaid
graph TB
    subgraph Participants["参与者"]
        User1["用户1<br/>Publisher"]
        User2["用户2<br/>Publisher"]
        User3["用户3<br/>Publisher"]
        User4["用户4<br/>Subscriber"]
    end
    
    subgraph KMS["Kurento Media Server"]
        Composite["Composite Hub<br/>音视频混流"]
    end
    
    User1 -->|推流| Composite
    User2 -->|推流| Composite
    User3 -->|推流| Composite
    
    Composite -->|混流后| User1
    Composite -->|混流后| User2
    Composite -->|混流后| User3
    Composite -->|混流后| User4
```

**关键差异点**：
- 所有成员连接到同一个 Composite Hub
- 服务器端混流，每个客户端只接收一路流
- 适合 5 人以上会议

---

### **流程 3: 直播模式**

```mermaid
sequenceDiagram
    participant Anchor as 主播
    participant Room as Room Manager
    participant Media as Media API
    participant KMS as Kurento
    participant Viewers as 观众们

    Anchor->>Room: 创建直播房间
    Room->>Media: createPipeline(type=broadcast)
    Media->>KMS: 创建 Pipeline
    Room->>Media: createEndpoint(role=publisher)
    Media->>KMS: 创建 Publisher Endpoint
    Room-->>Anchor: roomId + endpointId

    Anchor->>Anchor: 开始推流
    Anchor->>KMS: RTP 音视频流

    loop 观众加入
        Viewers->>Room: 加入直播间
        Room->>Media: createEndpoint(role=subscriber)
        Media->>KMS: 创建 Subscriber Endpoint
        Media->>KMS: connect(publisher, subscriber)
        KMS-->>Viewers: 拉取直播流
    end

    Anchor->>Room: 结束直播
    Room->>Media: releaseAllEndpoints()
    Media->>KMS: 批量释放
```

---

## 五、错误处理与容错设计

### **错误分类与处理策略**

```mermaid
graph TB
    subgraph ClientErrors["客户端错误"]
        InvalidMsg["消息格式错误<br/>→ 返回 400"]
        Unauthorized["未授权<br/>→ 返回 401"]
        RateLimit["超过限流<br/>→ 返回 429"]
    end
    
    subgraph ServerErrors["服务器错误"]
        KMSDown["KMS 不可用<br/>→ 降级处理"]
        RedisDown["Redis 失败<br/>→ 拒绝新连接"]
        Timeout["处理超时<br/>→ 重试 3 次"]
    end
    
    subgraph MediaErrors["媒体错误"]
        ICEFailed["ICE 连接失败<br/>→ 引导用户检查网络"]
        NoMedia["无媒体流<br/>→ 提示打开摄像头"]
        PoorNetwork["网络质量差<br/>→ 降低码率"]
    end
    
    subgraph Recovery["恢复机制"]
        Reconnect["断线重连<br/>30秒内恢复状态"]
        Cleanup["资源清理<br/>超时自动释放"]
        Fallback["服务降级<br/>仅音频模式"]
    end
    
    KMSDown --> Fallback
    RedisDown --> Cleanup
    Timeout --> Reconnect
    
    ICEFailed --> Reconnect
    NoMedia --> Fallback
    PoorNetwork --> Fallback
```

---

## 六、性能优化与监控

### **性能指标定义**

```yaml
WebSocket 层:
  - 并发连接数: 目标 10,000
  - 消息处理延迟: P99 < 50ms
  - 心跳超时检测: 90秒

RTC 信令层:
  - SDP 协商时间: P99 < 200ms
  - ICE 连接建立: P99 < 3s
  - 房间创建耗时: P99 < 500ms

媒体层:
  - Endpoint 创建: P99 < 300ms
  - 媒体首帧时间: P99 < 2s
  - Pipeline 复用率: > 80%

数据层:
  - Redis 操作: P99 < 10ms
  - MySQL 写入: P99 < 100ms
```

### **监控指标采集**

```mermaid
graph TB
    subgraph Metrics["指标采集"]
        WSMetric["WebSocket<br/>• 连接数<br/>• 消息 QPS<br/>• 错误率"]
        
        RTCMetric["RTC<br/>• 房间数<br/>• 成员数<br/>• 呼叫成功率"]
        
        MediaMetric["Media<br/>• Pipeline 数<br/>• Endpoint 数<br/>• 媒体码率"]
        
        QualityMetric["Quality<br/>• 丢包率<br/>• 延迟<br/>• 抖动"]
    end
    
    subgraph Export["指标导出"]
        Prometheus["Prometheus"]
        Grafana["Grafana Dashboard"]
        AlertManager["Alert Manager"]
    end
    
    WSMetric --> Prometheus
    RTCMetric --> Prometheus
    MediaMetric --> Prometheus
    QualityMetric --> Prometheus
    
    Prometheus --> Grafana
    Prometheus --> AlertManager
```

---

## 七、部署架构

### **生产环境拓扑**

```mermaid
graph TB
    subgraph Internet["公网"]
        Client["客户端<br/>RN/Electron"]
    end
    
    subgraph LoadBalancer["负载均衡层"]
        LB["Nginx<br/>• SSL 终止<br/>• WebSocket 代理<br/>• Sticky Session"]
    end
    
    subgraph AppCluster["应用集群"]
        App1["Spring Boot 1<br/>WebSocket + RTC"]
        App2["Spring Boot 2<br/>WebSocket + RTC"]
        App3["Spring Boot 3<br/>WebSocket + RTC"]
    end
    
    subgraph DataLayer["数据层"]
        RedisCluster["Redis Cluster<br/>3 主 3 从"]
        MySQLMaster["MySQL Master"]
        MySQLSlave["MySQL Slave"]
    end
    
    subgraph MediaCluster["媒体集群"]
        KMS1["Kurento 1"]
        KMS2["Kurento 2"]
        KMS3["Kurento 3"]
        TURN["Coturn<br/>TURN Server"]
    end
    
    Client --> LB
    LB --> App1
    LB --> App2
    LB --> App3
    
    App1 --> RedisCluster
    App2 --> RedisCluster
    App3 --> RedisCluster
    
    App1 --> MySQLMaster
    MySQLMaster --> MySQLSlave
    
    App1 -.-> KMS1
    App2 -.-> KMS2
    App3 -.-> KMS3
    
    Client -.->|WebRTC| KMS1
    Client -.->|WebRTC| KMS2
    Client -.->|WebRTC| KMS3
    
    Client -.->|TURN| TURN
```

---

## 八、AI 执行任务总清单

### **Phase 1: 基础设施（2 周）**

```yaml
Week 1:
  - Task 1.1: WebSocket 连接管理器
  - Task 1.2: 消息路由器
  - Task 1.3: 会话管理器
  - Task 5.1: Media Service 接口定义
  - Task 5.2: DTO 定义

Week 2:
  - Task 6.1: Kurento Client 封装
  - Task 6.2: MediaService 实现
  - Task 6.3: 事件监听器
  - 集成测试: WebSocket + KMS 连通性
```

### **Phase 2: 核心功能（4 周）**

```yaml
Week 3:
  - Task 3.1: SDP 协商处理器
  - Task 3.2: ICE 转发器
  - Task 4.1: 房间创建器
  - Task 4.2: 成员管理器

Week 4:
  - Task 3.3: 状态机管理器
  - Task 4.3: 房间清理器
  - Task 2.1: IM 消息类型扩展
  - Task 2.2: RTC 服务调用接口

Week 5-6:
  - 完整 1v1 通话流程集成
  - 端到端测试（Mock 客户端）
  - 性能基准测试（100 并发通话）
```

### **Phase 3: 前端开发（3 周）**

```yaml
Week 7:
  - Task 7.1: RN WebRTC 封装
  - Task 7.2: Call Manager
  - Task 7.3: 信令客户端

Week 8:
  - Task 7.4: UI 组件
  - Task 7.5: Electron 适配

Week 9:
  - 前后端联调
  - 真机测试（iOS/Android/Windows/macOS）
```

### **Phase 4: 高级功能（4 周）**

```yaml
Week 10-11:
  - 多人会议（MCU 模式）
  - 直播模式
  - 屏幕共享

Week 12-13:
  - 录制功能
  - 媒体处理（美颜、水印）
  - 通话质量统计
```

### **Phase 5: 测试与优化（2 周）**

```yaml
Week 14:
  - 压力测试（1000+ 并发）
  - 稳定性测试（72 小时）
  - 安全测试

Week 15:
  - 性能优化
  - 监控告警配置
  - 文档编写
```

---

## 九、交付物清单

### **给 AI 的输入文件**

```yaml
/specs
  ├── api-definitions/
  │   ├── websocket-protocol.yaml      # WebSocket 消息协议
  │   ├── media-service-api.yaml       # Media Service 接口
  │   └── rest-api.yaml                # REST API 定义
  ├── data-models/
  │   ├── redis-structures.yaml        # Redis 数据结构
  │   ├── mysql-schema.sql             # MySQL 表结构
  │   └── dto-definitions.yaml         # 数据传输对象
  ├── state-machines/
  │   ├── call-state-machine.mmd       # 通话状态机
  │   ├── room-lifecycle.mmd           # 房间生命周期
  │   └── frontend-states.mmd          # 前端状态机
  └── flows/
      ├── 1v1-call-flow.mmd            # 1v1 通话流程
      ├── group-call-flow.mmd          # 群组通话流程
      └── broadcast-flow.mmd           # 直播流程
```

### **AI 输出的代码结构**

```yaml
/backend
  ├── gateway/                         # Module 1
  │   ├── WebSocketHandler.java
  │   ├── MessageRouter.java
  │   └── SessionManager.java
  ├── im/                              # Module 2
  │   ├── IMMessageHandler.java
  │   └── RTCServiceClient.java
  ├── rtc/                             # Module 3 + 4
  │   ├── signaling/
  │   │   ├── SDPNegotiator.java
  │   │   ├── IceCandidateHandler.java
  │   │   └── CallStateMachine.java
  │   └── room/
  │       ├── RoomFactory.java
  │       ├── MemberManager.java
  │       └── RoomCleaner.java
  ├── media/                           # Module 5 + 6
  │   ├── api/
  │   │   ├── MediaService.java
  │   │   └── dto/
  │   └── kurento/
  │       ├── KurentoClientWrapper.java
  │       ├── KurentoMediaServiceImpl.java
  │       ├── KurentoEventHandler.java
  │       ├── PipelinePool.java
  │       └── EndpointPool.java
  └── tests/
      ├── unit/
      ├── integration/
      └── e2e/

/frontend
  ├── rn/                              # Module 7
  │   ├── src/
  │   │   ├── managers/
  │   │   │   ├── RTCManager.ts
  │   │   │   ├── CallManager.ts
  │   │   │   └── SignalingClient.ts
  │   │   ├── screens/
  │   │   │   ├── CallScreen.tsx
  │   │   │   └── VideoView.tsx
  │   │   └── states/
  │   │       └── callState.ts
  │   └── __tests__/
  └── electron/
      └── src/
          ├── (同 RN 结构)
          └── adapters/
```

---

## 十、关键决策说明

### **为什么选择这个架构？**

1. **模块解耦**：每个模块职责单一，可以独立开发和测试
2. **抽象分层**：Media API 层让你未来可以替换 Kurento
3. **复用现有设施**：WebSocket、Redis、MySQL 都是现成的
4. **渐进式开发**：可以先做 1v1，再扩展到多人和直播
5. **可观测性**：所有消息经过统一网关，易于监控

### **关键技术选型理由**

| 组件  | 选型  | 理由  |
| --- | --- | --- |
| 媒体服务器 | Kurento | 官方 Java SDK 成熟，功能完整 |
| 信令传输 | 复用 WebSocket | 降低复杂度，统一连接管理 |
| 状态存储 | Redis | 高性能，支持 TTL 自动清理 |
| 历史记录 | MySQL | 持久化，支持复杂查询 |
| 前端框架 | RN + Electron | 你已有的技术栈 |

---

这份设计文档可以**直接交给 AI 执行**，每个 Task 都有明确的输入、输出和验证标准。你需要做的是：

1. **按 Phase 顺序执行**，不要跳跃
2. **每个 Task 完成后验证**，再进行下一个
3. **关键节点做集成测试**（如 Week 2、Week 6、Week 9）
4. **保持架构图与代码同步**，代码变更要更新文档