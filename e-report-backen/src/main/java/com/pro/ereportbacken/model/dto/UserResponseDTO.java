// src/main/java/.../model/dto/UserResponseDTO.java
package com.pro.ereportbacken.model.dto;

import java.util.UUID;

import com.pro.ereportbacken.model.pojo.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserResponseDTO {
    private UUID id;
    private String username;
    private String name;
    private String phone;
    private String email;
    private UserStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private UUID tenantId;
    private UUID orgUnitId;
    private String orgUnitName;
    private UUID departmentId;
    private String departmentName;
    private Long authorizationVersion;
    private boolean systemProtected;

}


