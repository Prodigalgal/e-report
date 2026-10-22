package com.pro.ereportbacken.model.dto;

import java.util.UUID;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;
import com.pro.ereportbacken.model.pojo.AuthClientType;

@Data
@NoArgsConstructor
// @AllArgsConstructor // 如果字段变化，这个可能需要调整或手动创建构造函数
public class LoginResponseDTO {
    private String token;
    private String refreshToken;
    private Long accessTokenExpiresInSeconds;
    private UUID sessionId;
    private AuthClientType clientType;
    private Boolean passwordChangeRequired;
    private String username;
    private String name;
    private String phone;
    private String email;
    private UUID membershipId;
    private UUID tenantId;
    private String tenantCode;
    private String tenantName;
    private UUID orgUnitId;
    private Long authorizationVersion;
    private Set<String> permissions;
}

