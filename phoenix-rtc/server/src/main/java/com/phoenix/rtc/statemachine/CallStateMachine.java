package com.phoenix.rtc.statemachine;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 通话状态机
 * 管理通话的生命周期状态转换
 * 参考 n.md 状态机设计
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CallStateMachine {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String STATE_KEY = "rtc:state:%s:%s";  // rtc:state:{roomName}:{userId}
    private static final String STATE_HISTORY_KEY = "rtc:state:history:%s:%s";  // 历史记录

    /**
     * 状态转换规则
     * 定义允许的状态转换路径
     */
    private static final Map<CallState, CallState[]> ALLOWED_TRANSITIONS = Map.of(
        CallState.IDLE, new CallState[]{CallState.CALLING},
        CallState.CALLING, new CallState[]{CallState.CONNECTING, CallState.REJECTED, CallState.NO_ANSWER},
        CallState.CONNECTING, new CallState[]{CallState.CONNECTED, CallState.ENDED},
        CallState.CONNECTED, new CallState[]{CallState.ENDED},
        CallState.ENDED, new CallState[]{},  // 终态
        CallState.REJECTED, new CallState[]{},  // 终态
        CallState.NO_ANSWER, new CallState[]{}  // 终态
    );

    /**
     * 设置通话状态
     *
     * @param roomName 房间名称
     * @param userId 用户ID
     * @param newState 新状态
     * @return 是否成功
     */
    public boolean setState(String roomName, String userId, CallState newState) {
        String stateKey = String.format(STATE_KEY, roomName, userId);

        // 获取当前状态
        CallState currentState = getCurrentState(roomName, userId);

        // 验证状态转换是否允许
        if (!isValidTransition(currentState, newState)) {
            log.warn("无效的状态转换 - 房间: {}, 用户: {}, 当前状态: {}, 目标状态: {}",
                roomName, userId, currentState, newState);
            return false;
        }

        // 保存状态到 Redis
        redisTemplate.opsForHash().put(stateKey, "state", newState.getCode());
        redisTemplate.opsForHash().put(stateKey, "timestamp", System.currentTimeMillis());
        redisTemplate.opsForHash().put(stateKey, "roomName", roomName);
        redisTemplate.expire(stateKey, 2, TimeUnit.HOURS);

        // 记录状态历史
        recordStateHistory(roomName, userId, currentState, newState);

        log.info("状态转换成功 - 房间: {}, 用户: {}, {} -> {}",
            roomName, userId, currentState.getDescription(), newState.getDescription());

        return true;
    }

    /**
     * 获取当前状态
     */
    public CallState getCurrentState(String roomName, String userId) {
        String stateKey = String.format(STATE_KEY, roomName, userId);

        String stateCode = (String) redisTemplate.opsForHash().get(stateKey, "state");

        if (stateCode == null) {
            return CallState.IDLE;
        }

        return CallState.fromCode(stateCode);
    }

    /**
     * 检查状态转换是否有效
     */
    private boolean isValidTransition(CallState from, CallState to) {
        if (from == null) {
            return to == CallState.IDLE || to == CallState.CALLING;
        }

        CallState[] allowed = ALLOWED_TRANSITIONS.get(from);
        if (allowed == null) {
            return false;
        }

        for (CallState state : allowed) {
            if (state == to) {
                return true;
            }
        }

        return false;
    }

    /**
     * 记录状态历史
     */
    private void recordStateHistory(String roomName, String userId, CallState from, CallState to) {
        String historyKey = String.format(STATE_HISTORY_KEY, roomName, userId);

        // 使用 List 保存历史记录，最多保留 10 条
        String entry = String.format("%d|%s->%s",
            System.currentTimeMillis(),
            from != null ? from.getCode() : "null",
            to.getCode());

        redisTemplate.opsForList().leftPush(historyKey, entry);
        redisTemplate.opsForList().trim(historyKey, 0, 9);  // 保留最近 10 条
        redisTemplate.expire(historyKey, 2, TimeUnit.HOURS);
    }

    /**
     * 获取状态历史
     */
    public java.util.List<String> getStateHistory(String roomName, String userId) {
        String historyKey = String.format(STATE_HISTORY_KEY, roomName, userId);
        Long size = redisTemplate.opsForList().size(historyKey);

        if (size == null || size == 0) {
            return java.util.List.of();
        }

        return redisTemplate.opsForList().range(historyKey, 0, size - 1)
            .stream()
            .map(obj -> (String) obj)
            .toList();
    }

    /**
     * 清除状态
     */
    public void clearState(String roomName, String userId) {
        String stateKey = String.format(STATE_KEY, roomName, userId);
        String historyKey = String.format(STATE_HISTORY_KEY, roomName, userId);

        redisTemplate.delete(stateKey);
        redisTemplate.delete(historyKey);

        log.debug("清除状态 - 房间: {}, 用户: {}", roomName, userId);
    }

    /**
     * 批量清除房间状态
     */
    public void clearRoomStates(String roomName) {
        // 查找所有用户的状态键
        String pattern = String.format("rtc:state:%s:*", roomName);
        String historyPattern = String.format("rtc:state:history:%s:*", roomName);

        // 删除所有匹配的键
        var keys = redisTemplate.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }

        var historyKeys = redisTemplate.keys(historyPattern);
        if (historyKeys != null && !historyKeys.isEmpty()) {
            redisTemplate.delete(historyKeys);
        }

        log.info("清除房间所有状态 - 房间: {}", roomName);
    }
}
