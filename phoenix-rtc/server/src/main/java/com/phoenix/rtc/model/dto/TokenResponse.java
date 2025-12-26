package com.phoenix.rtc.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Token 响应 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenResponse {

    /**
     * LiveKit 服务器 URL
     */
    private String url;

    /**
     * JWT Token
     */
    private String token;

    /**
     * 房间名称
     */
    private String roomName;

    /**
     * 过期时间戳(秒)
     */
    private Long expiresAt;

    /**
     * 房间标题
     */
    private String roomTitle;
}
