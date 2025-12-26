package com.phoenix.rtc.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 发起通话请求 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CallRequest {

    /**
     * 目标用户 ID 列表 (单人时传1个，多人时传多个)
     */
    @NotNull(message = "目标用户不能为空")
    private List<String> targetUserIds;

    /**
     * 通话类型: video | audio | live
     */
    @NotBlank(message = "通话类型不能为空")
    private String sessionType;

    /**
     * 房间标题 (可选)
     */
    private String title;

    /**
     * 是否为群聊
     */
    private Boolean isGroup;

    /**
     * 最大参与人数 (直播模式下有效)
     */
    private Integer maxParticipants;
}
