import React from 'react';
import {
  View,
  StyleSheet,
  Text,
  TouchableOpacity,
  Dimensions,
} from 'react-native';
import { Participant, Track } from 'livekit-client';
import { VideoView as LiveKitVideoView } from '@livekit/react-native';

const { width: SCREEN_WIDTH, height: SCREEN_HEIGHT } = Dimensions.get('window');

interface VideoViewProps {
  participant: Participant;
  isLocal?: boolean;
  isAudioMuted?: boolean;
  isVideoMuted?: boolean;
  style?: any;
}

/**
 * 视频视图组件
 */
export const VideoView: React.FC<VideoViewProps> = ({
  participant,
  isLocal = false,
  isAudioMuted = false,
  isVideoMuted = false,
  style,
}) => {
  const displayName = participant.name || participant.identity;

  return (
    <View style={[styles.container, style]}>
      {/* LiveKit 视频渲染 */}
      <LiveKitVideoView
        participant={participant}
        track={Track.Source.Camera}
        style={styles.video}
        zOrderOnTop={isLocal}
      />

      {/* 视频未开启时的占位符 */}
      {isVideoMuted && (
        <View style={styles.placeholder}>
          <Text style={styles.placeholderText}>
            {displayName.substring(0, 2).toUpperCase()}
          </Text>
        </View>
      )}

      {/* 用户信息覆盖层 */}
      <View style={styles.overlay}>
        <View style={styles.infoBadge}>
          <Text style={styles.infoText}>
            {isLocal ? '我' : displayName}
          </Text>
          {isAudioMuted && (
            <Text style={[styles.infoText, styles.mutedText]}>🔇</Text>
          )}
        </View>
      </View>

      {/* 本地预览标识 */}
      {isLocal && (
        <View style={styles.localBadge}>
          <Text style={styles.localBadgeText}>预览</Text>
        </View>
      )}
    </View>
  );
};

/**
 * 视频网格布局组件
 */
interface VideoGridProps {
  participants: Participant[];
  localParticipant?: Participant;
  isAudioMuted?: boolean;
  isVideoMuted?: boolean;
}

export const VideoGrid: React.FC<VideoGridProps> = ({
  participants,
  localParticipant,
  isAudioMuted = false,
  isVideoMuted = false,
}) => {
  const allParticipants = [...participants];
  if (localParticipant && !allParticipants.find(p => p.identity === localParticipant.identity)) {
    allParticipants.unshift(localParticipant);
  }

  const participantCount = allParticipants.length;

  // 根据人数计算布局
  const getGridStyle = () => {
    if (participantCount === 1) {
      return styles.gridOne;
    } else if (participantCount === 2) {
      return styles.gridTwo;
    } else if (participantCount <= 4) {
      return styles.gridFour;
    } else {
      return styles.gridMany;
    }
  };

  return (
    <View style={[styles.gridContainer, getGridStyle()]}>
      {allParticipants.map((participant, index) => {
        const isLocal = participant.identity === localParticipant?.identity;
        return (
          <VideoView
            key={participant.identity}
            participant={participant}
            isLocal={isLocal}
            isAudioMuted={isLocal ? isAudioMuted : false}
            isVideoMuted={isLocal ? isVideoMuted : false}
            style={styles.gridItem}
          />
        );
      })}
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#1a1a1a',
    borderRadius: 12,
    overflow: 'hidden',
  },
  video: {
    flex: 1,
    width: '100%',
    height: '100%',
  },
  placeholder: {
    ...StyleSheet.absoluteFillObject,
    backgroundColor: '#2d2d2d',
    justifyContent: 'center',
    alignItems: 'center',
  },
  placeholderText: {
    fontSize: 48,
    fontWeight: 'bold',
    color: '#fff',
  },
  overlay: {
    position: 'absolute',
    bottom: 8,
    left: 8,
    right: 8,
  },
  infoBadge: {
    backgroundColor: 'rgba(0, 0, 0, 0.6)',
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 16,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  infoText: {
    color: '#fff',
    fontSize: 14,
    fontWeight: '600',
  },
  mutedText: {
    fontSize: 12,
  },
  localBadge: {
    position: 'absolute',
    top: 8,
    right: 8,
    backgroundColor: 'rgba(59, 130, 246, 0.9)',
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 8,
  },
  localBadgeText: {
    color: '#fff',
    fontSize: 10,
    fontWeight: '600',
  },
  gridContainer: {
    flex: 1,
    backgroundColor: '#000',
  },
  gridItem: {
    flex: 1,
    margin: 2,
  },
  gridOne: {},
  gridTwo: {
    flexDirection: 'row',
  },
  gridFour: {
    flexDirection: 'row',
    flexWrap: 'wrap',
  },
  gridMany: {
    flexDirection: 'row',
    flexWrap: 'wrap',
  },
});
