# 🚀 Phoenix RTC Demo - WebRTC 真实 P2P 通信演示

## ⚠️ 重要说明

**本演示已更新为真实 P2P 连接模式，不再使用虚拟视频！**

所有三个演示页面都使用 **WebRTC 真实网络连接**，支持：
- ✅ 真实的摄像头和麦克风
- ✅ P2P 端到端加密连接
- ✅ 多人实时音视频通话
- ✅ 实时直播和互动
- ✅ 一键自动复制信令
- ✅ 移动端浏览器支持

---

## 📋 目录结构

```
demo/
├── 1_video_call.html          # 1对1视频通话（手动信令）
├── 2_multi_person_meeting.html # 多人会议（信令服务器）
├── 3_live_streaming.html      # 直播+互动（信令服务器）
├── signaling-server.js        # Node.js 信令服务器
├── start.sh                   # 一键启动脚本（Mac/Linux）
├── start_server.bat           # Windows 启动脚本
├── 环境检查.html              # 环境检测工具
├── 测试指南.md                # 详细测试指南
├── 快速开始.txt               # 快速参考卡片
└── README.md                  # 本文件
```

---

## 🎯 三个演示功能

### 1️⃣ 视频通话测试 (`1_video_call.html`)

**功能：** 真实的 1对1 P2P 视频通话

**特点：**
- ✅ 无需信令服务器
- ✅ 一键自动复制信令
- ✅ 简化3步流程
- ✅ 支持音频/视频开关
- ✅ 实时统计信息

**使用步骤（简化版）：**
1. **A方**：输入用户名 → 点击"开始通话" → 点击"🎯 创建连接"（自动复制）
2. **B方**：输入用户名 → 点击"开始通话" → 粘贴信令 → 点击"🎯 加入通话"（自动复制）
3. **A方**：粘贴应答 → 点击"✅ 完成连接"

**测试方法：**
- 在两个浏览器窗口打开此页面
- 信令会自动复制到剪贴板
- 看到对方的真实摄像头画面

---

### 2️⃣ 多人会议测试 (`2_multi_person_meeting.html`)

**功能：** 多人实时视频会议（需要信令服务器）

**特点：**
- ✅ 支持多人同时在线
- ✅ 自动连接新加入的用户
- ✅ 显示参与者列表
- ✅ 实时统计信息
- ✅ 防止重复连接

**使用步骤：**
1. **启动信令服务器**：`node signaling-server.js`
2. **所有用户**：输入服务器地址 `ws://localhost:8080`
3. **所有用户**：输入用户名和相同的房间号
4. **点击"加入房间"**：系统自动连接所有在线用户

**关键改进：**
- 新用户加入时，会自动为已存在的用户创建Offer
- 已存在的用户会自动为新用户创建Offer
- 防止重复创建PeerConnection

---

### 3️⃣ 直播测试 (`3_live_streaming.html`)

**功能：** 真实的 P2P 直播 + 互动（需要信令服务器）

**特点：**
- ✅ 主播/观众模式切换
- ✅ 浮动评论（从左到右飘过）
- ✅ 点赞动画（❤️）
- ✅ 礼物特效（🎁）
- ✅ 实时互动
- ✅ 防止重复连接

**使用步骤：**

**主播端：**
1. 输入服务器地址 `ws://localhost:8080`
2. 输入用户名、直播间号
3. 点击"🎤 主播模式"
4. 等待观众加入

**观众端：**
1. 输入相同的服务器地址
2. 输入用户名、相同的直播间号
3. 点击"👁️ 观众模式"
4. 看到主播的实时画面
5. 发送评论、点赞、送礼物

**互动功能：**
- **评论**：输入后回车发送，双方都能看到
- **点赞**：点击❤️按钮，屏幕显示心形动画
- **礼物**：点击🎁按钮，屏幕显示礼物特效
- **浮动评论**：评论会从屏幕左侧飘到右侧

---

## 🚀 快速启动

### Mac/Linux

```bash
cd /Users/sanbo/Desktop/rtc_demo/demo
bash start.sh
```

### Windows

```bash
cd C:\Users\YourName\Desktop\rtc_demo\demo
python -m http.server 8000
```

然后在浏览器访问：`http://localhost:8000`

---

## 🔧 环境要求

### 浏览器要求
- ✅ **Chrome** (推荐)
- ✅ **Edge**
- ✅ **Safari** (iOS/Mac)
- ✅ **Firefox**

### 访问方式（重要！）
- ✅ **http://localhost:8000** (正确)
- ✅ **http://127.0.0.1:8000** (正确)
- ❌ **直接双击 HTML 文件** (错误，无法使用摄像头)
- ❌ **file:// 协议** (错误，无法使用摄像头)

### 摄像头权限
首次使用时，浏览器会弹出摄像头权限请求，请务必点击 **"允许"** 或 **"Allow"**

---

## 📱 移动端测试

### 同一 WiFi 下测试

1. **电脑启动服务器**
   ```bash
   python -m http.server 8000
   ```

2. **查看电脑 IP**
   ```bash
   # Mac/Linux
   ifconfig | grep "inet "

   # Windows
   ipconfig
   ```

3. **手机访问**
   - 打开手机浏览器
   - 输入：`http://[电脑IP]:8000/1_video_call.html`
   - 例如：`http://192.168.1.100:8000/1_video_call.html`

---

## 🔍 故障排查

### 问题1：无法访问摄像头

**错误提示：** `undefined is not an object (evaluating 'navigator.mediaDevices.getUserMedia')`

**解决方案：**
1. 确保使用 `http://localhost:8000` 访问
2. 检查浏览器地址栏是否有 🔒 或 📹 图标
3. 点击图标，选择"允许"摄像头和麦克风
4. 刷新页面

### 问题2：Chrome 提示不安全

**原因：** Chrome 对摄像头权限有严格限制

**解决方案：**
1. 在地址栏输入：`chrome://settings/content/camera`
2. 添加例外：`http://localhost:8000` → 允许
3. 或者：点击地址栏左侧的"不安全"图标 → 选择"允许"

### 问题3：两个浏览器看不到对方

**原因：** 信令服务器未启动或配置错误

**解决方案：**
1. 检查信令服务器是否运行
2. 检查服务器地址是否正确（`ws://localhost:8080`）
3. 检查两个浏览器是否使用相同的房间号
4. 查看浏览器控制台（F12）是否有错误信息

### 问题4：移动端无法打开

**原因：** iOS Safari 或微信内置浏览器限制

**解决方案：**
1. iOS：使用 Safari 浏览器
2. 微信：点击右上角"..." → 选择"在浏览器打开"
3. 确保手机和电脑在同一 WiFi

---

## 🛠️ 信令服务器示例

如果需要测试多人功能，可以使用以下简单的 Node.js 信令服务器：

```javascript
// signaling-server.js
const WebSocket = require('ws');
const wss = new WebSocket.Server({ port: 8080 });

const rooms = new Map();

wss.on('connection', (ws) => {
    ws.on('message', (message) => {
        const data = JSON.parse(message);

        if (data.type === 'join') {
            ws.roomId = data.roomId;
            ws.username = data.username;

            if (!rooms.has(data.roomId)) {
                rooms.set(data.roomId, new Set());
            }
            rooms.get(data.roomId).add(ws);

            // 通知其他用户
            broadcast(data.roomId, {
                type: 'peerJoined',
                username: data.username
            });

            // 发送当前用户列表
            const peers = Array.from(rooms.get(data.roomId))
                .map(client => client.username)
                .filter(name => name !== data.username);

            ws.send(JSON.stringify({
                type: 'peers',
                peers: peers
            }));
        }
        else if (data.type === 'offer' || data.type === 'answer' || data.type === 'iceCandidate' || data.type === 'comment' || data.type === 'like' || data.type === 'gift') {
            broadcast(ws.roomId, data);
        }
    });

    ws.on('close', () => {
        if (ws.roomId && rooms.has(ws.roomId)) {
            rooms.get(ws.roomId).delete(ws);
            broadcast(ws.roomId, {
                type: 'peerLeft',
                username: ws.username
            });
        }
    });
});

function broadcast(roomId, message) {
    if (!rooms.has(roomId)) return;
    rooms.get(roomId).forEach(client => {
        if (client.readyState === WebSocket.OPEN) {
            client.send(JSON.stringify(message));
        }
    });
}

console.log('信令服务器已启动: ws://localhost:8080');
```

**启动命令：**
```bash
npm install ws
node signaling-server.js
```

---

## 📊 功能对比

| 功能 | 1_video_call.html | 2_multi_person_meeting.html | 3_live_streaming.html |
| :--- | :--- | :--- | :--- |
| **连接方式** | 手动信令 | 信令服务器 | 信令服务器 |
| **支持人数** | 2人 | 多人 | 主播+观众 |
| **需要服务器** | ❌ 否 | ✅ 是 | ✅ 是 |
| **实时互动** | ✅ 视频通话 | ✅ 视频会议 | ✅ 直播+评论 |
| **测试难度** | 简单 | 中等 | 中等 |
| **适合场景** | P2P测试 | 团队会议 | 直播演示 |

---

## 💡 使用建议

1. **初次测试**：从 `1_video_call.html` 开始，了解 P2P 原理
2. **多人测试**：搭建信令服务器后测试 `2_multi_person_meeting.html`
3. **直播演示**：体验完整的直播功能 `3_live_streaming.html`

---

## 🔄 更新日志

**2025-12-26**
- ✅ 所有页面改为真实 P2P 连接
- ✅ 移除所有虚拟视频/模拟内容
- ✅ 增加详细的错误提示
- ✅ 优化移动端兼容性
- ✅ 添加环境检测功能

---

## 🧪 环境检查工具

如果不确定环境是否正常，先运行环境检查：
```bash
http://localhost:8000/环境检查.html
```

检查项目：
- ✅ 访问协议检查
- ✅ WebRTC 支持检查
- ✅ 媒体设备访问
- ✅ 摄像头权限
- ✅ 麦克风权限
- ✅ 实时视频预览

---

## 📚 详细文档

- **测试指南** (`测试指南.md`)：详细的测试步骤和故障排查
- **快速开始** (`快速开始.txt`)：命令行快速参考卡片
- **项目说明** (`项目说明.md`)：完整的项目介绍

---

## 🔄 最新改进（2025-12-26）

### 1. 1_video_call.html
- ✅ 简化信令流程（3步完成）
- ✅ 一键自动复制信令
- ✅ 更好的错误提示
- ✅ 优化移动端UI

### 2. 2_multi_person_meeting.html
- ✅ 修复"只能看到自己"的问题
- ✅ 双向自动连接逻辑
- ✅ 防止重复创建PeerConnection
- ✅ 更好的连接状态反馈

### 3. 3_live_streaming.html
- ✅ 优化主播/观众连接逻辑
- ✅ 防止重复Offer
- ✅ 增强互动功能
- ✅ 浮动评论优化

### 4. 信令服务器
- ✅ 支持角色字段（broadcaster/viewer）
- ✅ 更好的错误处理
- ✅ 详细的日志输出

### 5. 新增工具
- ✅ 环境检查.html - 快速诊断工具
- ✅ 测试指南.md - 详细测试文档

---

## 🎯 核心改进总结

**之前的问题：**
- ❌ 使用虚拟视频，不是真实P2P
- ❌ 复杂的手动信令流程
- ❌ 多人会议只能看到自己
- ❌ 移动端兼容性差

**现在的改进：**
- ✅ 真实P2P连接（真实摄像头）
- ✅ 一键自动复制信令
- ✅ 修复多人连接逻辑
- ✅ 完整的移动端支持
- ✅ 环境检查工具
- ✅ 详细测试指南

---

## 📞 技术支持

如遇到问题，请按以下顺序排查：

1. **运行环境检查**：访问 `环境检查.html`
2. **检查访问地址**：必须是 `http://localhost:8000`
3. **检查摄像头权限**：浏览器是否允许访问
4. **检查控制台错误**：按 F12 查看
5. **检查信令服务器**：多人功能需要服务器运行
6. **查看测试指南**：`测试指南.md`

**祝测试愉快！** 🎉
