package com.pro.ereportbacken.model.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.pro.ereportbacken.model.pojo.AccessMode;
import com.pro.ereportbacken.security.PermissionRiskLevel;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class PermissionDTO {
    private UUID id; // 用于响应

    @NotBlank(message = "权限名称不能为空")
    @Size(max = 100, message = "权限名称不能超过100字符")
    private String name; // e.g., "work-order:network-fault:report"

    @NotBlank(message = "权限中文名不能为空")
    @Size(max = 100, message = "权限中文名不能超过100字符")
    private String displayName;

    @Size(max = 255, message = "描述不能超过255字符")
    private String description;

    private AccessMode accessMode;

    private PermissionRiskLevel riskLevel;

    private boolean apiTokenAssignable;
}
