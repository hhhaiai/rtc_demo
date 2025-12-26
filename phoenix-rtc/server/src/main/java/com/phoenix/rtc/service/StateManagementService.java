package com.phoenix.rtc.service;

import com.phoenix.rtc.statemachine.CallState;
import com.phoenix.rtc.statemachine.CallStateMachine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 状态管理服务
 * 集成状态机，管理通话业务状态
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StateManagementService {

    private final CallStateMachine stateMachine;

    /**
     * 开始呼叫
     */
    public boolean startCalling(String roomName, String userId) {
        return stateMachine.setState(roomName, userId, CallState.CALLING);
    }

    /**
     * 接听呼叫，进入连接中
     */
    public boolean acceptCall(String roomName, String userId) {
        return stateMachine.setState(roomName, userId, CallState.CONNECTING);
    }

    /**
     * 拒绝呼叫
     */
    public boolean rejectCall(String roomName, String userId) {
        return stateMachine.setState(roomName, userId, CallState.REJECTED);
    }

    /**
     * 无应答
     */
    public boolean noAnswer(String roomName, String userId) {
        return stateMachine.setState(roomName, userId, CallState.NO_ANSWER);
    }

    /**
     * 连接成功
     */
    public boolean connected(String roomName, String userId) {
        return stateMachine.setState(roomName, userId, CallState.CONNECTED);
    }

    /**
     * 结束通话
     */
    public boolean endCall(String roomName, String userId) {
        boolean success = stateMachine.setState(roomName, userId, CallState.ENDED);
        // 清除状态
        stateMachine.clearState(roomName, userId);
        return success;
    }

    /**
     * 获取当前状态
     */
    public CallState getCurrentState(String roomName, String userId) {
        return stateMachine.getCurrentState(roomName, userId);
    }

    /**
     * 检查是否可以发起呼叫
     */
    public boolean canStartCall(String roomName, String userId) {
        CallState state = getCurrentState(roomName, userId);
        return state == CallState.IDLE || state == CallState.ENDED || state == CallState.REJECTED || state == CallState.NO_ANSWER;
    }

    /**
     * 检查是否正在通话中
     */
    public boolean isInCall(String roomName, String userId) {
        CallState state = getCurrentState(roomName, userId);
        return state == CallState.CONNECTING || state == CallState.CONNECTED;
    }

    /**
     * 获取状态历史
     */
    public java.util.List<String> getStateHistory(String roomName, String userId) {
        return stateMachine.getStateHistory(roomName, userId);
    }

    /**
     * 清除房间所有状态
     */
    public void clearRoomStates(String roomName) {
        stateMachine.clearRoomStates(roomName);
    }
}
