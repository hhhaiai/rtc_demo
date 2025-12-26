import React, { useState } from 'react';
import {
  View,
  StyleSheet,
  Text,
  TouchableOpacity,
  TextInput,
  SafeAreaView,
  ScrollView,
  Alert,
} from 'react-native';
import Icon from 'react-native-vector-icons/MaterialCommunityIcons';

interface HomeScreenProps {
  navigation: any;
}

/**
 * 主页 - 演示界面
 */
export const HomeScreen: React.FC<HomeScreenProps> = ({ navigation }) => {
  const [targetUserIds, setTargetUserIds] = useState('');
  const [roomName, setRoomName] = useState('');

  // 发起视频通话
  const startVideoCall = () => {
    const ids = targetUserIds.split(',').map(id => id.trim()).filter(id => id);
    if (ids.length === 0) {
      Alert.alert('提示', '请输入目标用户ID，多个用户用逗号分隔');
      return;
    }

    navigation.navigate('Call', {
      targetUserIds: ids,
      sessionType: 'video',
    });
  };

  // 发起语音通话
  const startAudioCall = () => {
    const ids = targetUserIds.split(',').map(id => id.trim()).filter(id => id);
    if (ids.length === 0) {
      Alert.alert('提示', '请输入目标用户ID');
      return;
    }

    navigation.navigate('Call', {
      targetUserIds: ids,
      sessionType: 'audio',
    });
  };

  // 发起直播
  const startLive = () => {
    const ids = targetUserIds.split(',').map(id => id.trim()).filter(id => id);
    if (ids.length === 0) {
      Alert.alert('提示', '请输入观众用户ID');
      return;
    }

    navigation.navigate('Call', {
      targetUserIds: ids,
      sessionType: 'live',
    });
  };

  // 加入房间
  const joinRoom = () => {
    if (!roomName.trim()) {
      Alert.alert('提示', '请输入房间名称');
      return;
    }

    navigation.navigate('Call', {
      roomName: roomName.trim(),
    });
  };

  // 模拟接收来电
  const simulateIncomingCall = () => {
    navigation.navigate('IncomingCall', {
      invite: {
        roomId: 'room_demo_' + Date.now(),
        inviterId: 'user_demo_001',
        inviterName: '测试用户',
        mode: 'video',
        timestamp: Date.now(),
      },
    });
  };

  return (
    <SafeAreaView style={styles.container}>
      <ScrollView contentContainerStyle={styles.scrollContent}>
        <View style={styles.header}>
          <Icon name="video" size={40} color="#3b82f6" />
          <Text style={styles.title}>Phoenix RTC</Text>
          <Text style={styles.subtitle}>音视频通话组件演示</Text>
        </View>

        {/* 发起通话区域 */}
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>发起通话</Text>

          <View style={styles.inputGroup}>
            <Text style={styles.label}>目标用户ID (多个用逗号分隔)</Text>
            <TextInput
              style={styles.input}
              placeholder="例如: user2,user3"
              placeholderTextColor="#6b7280"
              value={targetUserIds}
              onChangeText={setTargetUserIds}
            />
          </View>

          <View style={styles.buttonRow}>
            <TouchableOpacity
              style={[styles.button, styles.videoButton]}
              onPress={startVideoCall}
            >
              <Icon name="video" size={20} color="#fff" />
              <Text style={styles.buttonText}>视频通话</Text>
            </TouchableOpacity>

            <TouchableOpacity
              style={[styles.button, styles.audioButton]}
              onPress={startAudioCall}
            >
              <Icon name="phone" size={20} color="#fff" />
              <Text style={styles.buttonText}>语音通话</Text>
            </TouchableOpacity>
          </View>

          <TouchableOpacity
            style={[styles.button, styles.liveButton]}
            onPress={startLive}
          >
            <Icon name="broadcast" size={20} color="#fff" />
            <Text style={styles.buttonText}>发起直播</Text>
          </TouchableOpacity>
        </View>

        {/* 加入房间区域 */}
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>加入房间</Text>

          <View style={styles.inputGroup}>
            <Text style={styles.label}>房间名称</Text>
            <TextInput
              style={styles.input}
              placeholder="输入房间名称"
              placeholderTextColor="#6b7280"
              value={roomName}
              onChangeText={setRoomName}
            />
          </View>

          <TouchableOpacity
            style={[styles.button, styles.joinButton]}
            onPress={joinRoom}
          >
            <Icon name="login" size={20} color="#fff" />
            <Text style={styles.buttonText}>加入房间</Text>
          </TouchableOpacity>
        </View>

        {/* 演示区域 */}
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>演示功能</Text>

          <TouchableOpacity
            style={[styles.button, styles.demoButton]}
            onPress={simulateIncomingCall}
          >
            <Icon name="phone-incoming" size={20} color="#fff" />
            <Text style={styles.buttonText}>模拟来电</Text>
          </TouchableOpacity>
        </View>

        {/* 说明 */}
        <View style={styles.infoSection}>
          <Text style={styles.infoTitle}>使用说明</Text>
          <Text style={styles.infoText}>
            1. 确保已启动 Docker 服务 (LiveKit, Redis, MySQL){'\n'}
            2. 确保 Spring Boot 后端运行在 8080 端口{'\n'}
            3. 输入目标用户ID发起通话{'\n'}
            4. 或输入房间名称加入现有房间{'\n'}
            5. 本演示支持 1v1、群聊、直播模式
          </Text>
        </View>
      </ScrollView>
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#0a0a0a',
  },
  scrollContent: {
    padding: 20,
  },
  header: {
    alignItems: 'center',
    marginBottom: 32,
    paddingTop: 20,
  },
  title: {
    color: '#fff',
    fontSize: 28,
    fontWeight: 'bold',
    marginTop: 12,
  },
  subtitle: {
    color: '#9ca3af',
    fontSize: 14,
    marginTop: 4,
  },
  section: {
    backgroundColor: '#1a1a1a',
    borderRadius: 12,
    padding: 16,
    marginBottom: 16,
  },
  sectionTitle: {
    color: '#fff',
    fontSize: 18,
    fontWeight: '600',
    marginBottom: 12,
  },
  inputGroup: {
    marginBottom: 12,
  },
  label: {
    color: '#9ca3af',
    fontSize: 12,
    marginBottom: 6,
  },
  input: {
    backgroundColor: '#2a2a2a',
    color: '#fff',
    padding: 12,
    borderRadius: 8,
    fontSize: 14,
    borderWidth: 1,
    borderColor: '#3a3a3a',
  },
  buttonRow: {
    flexDirection: 'row',
    gap: 8,
    marginBottom: 8,
  },
  button: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    padding: 14,
    borderRadius: 10,
    gap: 8,
  },
  videoButton: {
    backgroundColor: '#3b82f6',
  },
  audioButton: {
    backgroundColor: '#10b981',
  },
  liveButton: {
    backgroundColor: '#8b5cf6',
  },
  joinButton: {
    backgroundColor: '#f59e0b',
  },
  demoButton: {
    backgroundColor: '#6b7280',
  },
  buttonText: {
    color: '#fff',
    fontSize: 14,
    fontWeight: '600',
  },
  infoSection: {
    backgroundColor: '#1a1a1a',
    borderRadius: 12,
    padding: 16,
    marginTop: 8,
  },
  infoTitle: {
    color: '#fff',
    fontSize: 16,
    fontWeight: '600',
    marginBottom: 8,
  },
  infoText: {
    color: '#9ca3af',
    fontSize: 12,
    lineHeight: 18,
  },
});
