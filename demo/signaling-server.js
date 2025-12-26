/**
 * Phoenix RTC - 简单信令服务器
 *
 * 使用说明：
 * 1. 安装 Node.js
 * 2. 运行: npm install ws
 * 3. 启动: node signaling-server.js
 * 4. 在HTML中配置服务器地址: ws://localhost:8080
 */

const WebSocket = require('ws');
const wss = new WebSocket.Server({ port: 8080 });

// 存储所有房间和用户
const rooms = new Map();

console.log('╔══════════════════════════════════════════════════════════════╗');
console.log('║                                                              ║');
console.log('║          🚀 Phoenix RTC - 信令服务器已启动                   ║');
console.log('║                                                              ║');
console.log('╚══════════════════════════════════════════════════════════════╝');
console.log('');
console.log('服务器地址: ws://localhost:8080');
console.log('按 Ctrl+C 停止服务器');
console.log('');

wss.on('connection', (ws) => {
    console.log('✅ 新连接建立');

    ws.on('message', (message) => {
        try {
            const data = JSON.parse(message);
            console.log(`📨 收到消息: ${data.type} (房间: ${data.roomId || 'N/A'}, 用户: ${data.username || 'N/A'})`);

            switch (data.type) {
                case 'join':
                    handleJoin(ws, data);
                    break;

                case 'offer':
                case 'answer':
                case 'iceCandidate':
                    forwardMessage(ws, data);
                    break;

                case 'comment':
                case 'like':
                case 'gift':
                    broadcastMessage(ws, data);
                    break;

                default:
                    console.log(`⚠️ 未知消息类型: ${data.type}`);
            }
        } catch (error) {
            console.error('❌ 消息处理错误:', error);
        }
    });

    ws.on('close', () => {
        handleDisconnect(ws);
    });

    ws.on('error', (error) => {
        console.error('❌ WebSocket错误:', error);
    });
});

// 处理用户加入
function handleJoin(ws, data) {
    const { roomId, username, role } = data;

    if (!roomId || !username) {
        ws.send(JSON.stringify({
            type: 'error',
            message: '缺少房间ID或用户名'
        }));
        return;
    }

    // 创建房间（如果不存在）
    if (!rooms.has(roomId)) {
        rooms.set(roomId, new Set());
        console.log(`🏠 创建新房间: ${roomId}`);
    }

    // 保存用户信息
    ws.roomId = roomId;
    ws.username = username;
    ws.role = role || 'participant';

    // 添加到房间
    rooms.get(roomId).add(ws);

    // 通知其他用户
    broadcastToRoom(roomId, {
        type: 'peerJoined',
        username: username,
        role: role
    }, ws);

    // 发送当前房间用户列表
    const peers = Array.from(rooms.get(roomId))
        .filter(client => client !== ws)
        .map(client => client.username);

    ws.send(JSON.stringify({
        type: 'peers',
        peers: peers
    }));

    console.log(`✅ ${username} 加入房间 ${roomId} (${role || 'participant'})`);
    console.log(`   当前房间人数: ${rooms.get(roomId).size}`);
}

// 转发点对点消息
function forwardMessage(ws, data) {
    if (!ws.roomId || !rooms.has(ws.roomId)) return;

    const targetUsername = data.to;
    const roomClients = rooms.get(ws.roomId);

    for (const client of roomClients) {
        if (client.username === targetUsername && client.readyState === WebSocket.OPEN) {
            client.send(JSON.stringify(data));
            console.log(`🔄 转发 ${data.type} 从 ${data.from} 到 ${targetUsername}`);
            return;
        }
    }

    console.log(`⚠️ 未找到目标用户: ${targetUsername}`);
}

// 广播消息到房间
function broadcastMessage(ws, data) {
    if (!ws.roomId || !rooms.has(ws.roomId)) return;

    broadcastToRoom(ws.roomId, data, ws);
    console.log(`📢 广播 ${data.type} 从 ${ws.username}`);
}

// 广播辅助函数
function broadcastToRoom(roomId, message, excludeWs = null) {
    if (!rooms.has(roomId)) return;

    rooms.get(roomId).forEach(client => {
        if (client !== excludeWs && client.readyState === WebSocket.OPEN) {
            client.send(JSON.stringify(message));
        }
    });
}

// 处理断开连接
function handleDisconnect(ws) {
    if (!ws.roomId || !rooms.has(ws.roomId)) return;

    const roomId = ws.roomId;
    const roomClients = rooms.get(roomId);

    // 从房间移除
    roomClients.delete(ws);

    // 通知其他用户
    if (ws.username) {
        broadcastToRoom(roomId, {
            type: 'peerLeft',
            username: ws.username
        });

        console.log(`❌ ${ws.username} 离开房间 ${roomId}`);
    }

    // 如果房间为空，删除房间
    if (roomClients.size === 0) {
        rooms.delete(roomId);
        console.log(`🗑️ 房间 ${roomId} 已销毁`);
    } else {
        console.log(`   当前房间人数: ${roomClients.size}`);
    }
}
