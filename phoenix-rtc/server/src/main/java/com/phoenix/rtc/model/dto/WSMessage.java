package com.phoenix.rtc.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * WebSocket 消息格式
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WSMessage {

    /**
     * 消息类型: rtc | message | state
     */
    private String type;

    /**
     * 命令: invite/accept/ringing/leave/token/update
     */
    private String cmd;

    /**
     * 消息数据
     */
    private Object data;

    /**
     * 时间戳
     */
    private Long timestamp;

    /**
     * 构造成功响应
     */
    public static WSMessage success(String cmd, Object data) {
        return WSMessage.builder()
                .type("rtc")
                .cmd(cmd)
                .data(data)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    /**
     * 构造错误响应
     */
    public static WSMessage error(String message) {
        return WSMessage.builder()
                .type("rtc")
                .cmd("error")
                .data(message)
                .timestamp(System.currentTimeMillis())
                .build();
    }
}
