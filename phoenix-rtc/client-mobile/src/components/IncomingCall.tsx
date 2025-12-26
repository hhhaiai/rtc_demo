import React from 'react';
import {
  View,
  StyleSheet,
  Text,
  TouchableOpacity,
  SafeAreaView,
  StatusBar,
} from 'react-native';
import Icon from 'react-native-vector-icons/MaterialCommunityIcons';
import { CallInvite } from '../types';

interface IncomingCallProps {
  invite: CallInvite;
  onAccept: () => void;
  onReject: () => void;
}

/**
 * 来电接听界面
 */
export const IncomingCall: React.FC<IncomingCallProps> = ({
  invite,
  onAccept,
  onReject,
}) => {
  const isVideo = invite.mode === 'video' || invite.mode === 'live';

  return (
    <SafeAreaView style={styles.container}>
      <StatusBar barStyle="light-content" />

      <View style={styles.content}>
        <View style={styles.avatarContainer}>
          <Icon
            name={isVideo ? 'video' : 'phone'}
            size={60}
            color="#fff"
          />
        </View>

        <Text style={styles.title}>
          {invite.inviterName}
        </Text>

        <Text style={styles.subtitle}>
          {isVideo ? '请求视频通话' : '请求语音通话'}
        </Text>

        <Text style={styles.roomInfo}>
          房间: {invite.roomId}
        </Text>

        <View style={styles.buttonContainer}>
          <TouchableOpacity
            style={[styles.button, styles.rejectButton]}
            onPress={onReject}
          >
            <Icon name="phone-hangup" size={28} color="#fff" />
            <Text style={styles.buttonText}>拒绝</Text>
          </TouchableOpacity>

          <TouchableOpacity
            style={[styles.button, styles.acceptButton]}
            onPress={onAccept}
          >
            <Icon name="phone" size={28} color="#fff" />
            <Text style={styles.buttonText}>接听</Text>
          </TouchableOpacity>
        </View>
      </View>
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#111',
    justifyContent: 'center',
    alignItems: 'center',
  },
  content: {
    alignItems: 'center',
    padding: 32,
  },
  avatarContainer: {
    width: 120,
    height: 120,
    borderRadius: 60,
    backgroundColor: '#3b82f6',
    justifyContent: 'center',
    alignItems: 'center',
    marginBottom: 24,
  },
  title: {
    color: '#fff',
    fontSize: 28,
    fontWeight: 'bold',
    marginBottom: 8,
  },
  subtitle: {
    color: '#9ca3af',
    fontSize: 16,
    marginBottom: 8,
  },
  roomInfo: {
    color: '#6b7280',
    fontSize: 14,
    marginBottom: 32,
  },
  buttonContainer: {
    flexDirection: 'row',
    gap: 24,
  },
  button: {
    width: 80,
    height: 80,
    borderRadius: 40,
    justifyContent: 'center',
    alignItems: 'center',
    gap: 4,
  },
  rejectButton: {
    backgroundColor: '#ef4444',
  },
  acceptButton: {
    backgroundColor: '#10b981',
  },
  buttonText: {
    color: '#fff',
    fontSize: 12,
    fontWeight: '600',
  },
});
