package com.phoenix.rtc.statemachine;

/**
 * 通话状态枚举
 * 参考 n.md 状态机设计
 */
public enum CallState {
    /** 空闲状态 */
    IDLE("idle", "空闲"),

    /** 呼叫中 - 等待对方接听 */
    CALLING("calling", "呼叫中"),

    /** 连接中 - 正在建立 WebRTC 连接 */
    CONNECTING("connecting", "连接中"),

    /** 已连接 - 通话正常进行 */
    CONNECTED("connected", "已连接"),

    /** 已结束 - 通话终止 */
    ENDED("ended", "已结束"),

    /** 拒绝 - 被叫拒绝 */
    REJECTED("rejected", "已拒绝"),

    /** 无应答 - 被叫未接听 */
    NO_ANSWER("no_answer", "无应答");

    private final String code;
    private final String description;

    CallState(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public static CallState fromCode(String code) {
        for (CallState state : values()) {
            if (state.code.equals(code)) {
                return state;
            }
        }
        return IDLE;
    }
}
