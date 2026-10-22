package com.pro.ereportbacken.model.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Set;

public record ApiTokenCreateRequestDTO(
        @NotBlank(message = "Token 名称不能为空")
        @Size(max = 80, message = "Token 名称不能超过80个字符")
        String name,
        @Future(message = "Token 到期时间必须晚于当前时间")
        Instant expiresAt,
        @NotEmpty(message = "请至少选择一项 API 权限")
        @Size(max = 150, message = "API 权限数量超出限制")
        Set<@NotBlank(message = "API 权限不能为空") String> permissionNames
) {
}
