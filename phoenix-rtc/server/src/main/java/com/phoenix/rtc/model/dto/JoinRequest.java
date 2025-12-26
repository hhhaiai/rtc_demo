package com.phoenix.rtc.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 加入通话请求 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JoinRequest {

    /**
     * 房间名称
     */
    @NotBlank(message = "房间名称不能为空")
    private String roomName;
}
