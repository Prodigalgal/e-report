package com.pro.ereportbacken.model.dto;

/**
 * 登录会话分页结果，并返回当前账号仍处于有效状态的其他会话数量。
 */
public record AuthSessionPageDTO(
        PageResponseDTO<AuthSessionViewDTO> page,
        long activeOtherSessionCount
) {
}
