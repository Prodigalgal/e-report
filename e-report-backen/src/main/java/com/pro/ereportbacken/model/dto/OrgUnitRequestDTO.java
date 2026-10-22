package com.pro.ereportbacken.model.dto;

import java.util.UUID;

import com.pro.ereportbacken.model.pojo.OrgUnitType;
import com.pro.ereportbacken.model.pojo.TenantStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class OrgUnitRequestDTO {
    @NotBlank(message = "组织编码不能为空")
    @Size(max = 50, message = "组织编码不能超过50字符")
    private String code;

    @NotBlank(message = "组织名称不能为空")
    @Size(max = 100, message = "组织名称不能超过100字符")
    private String name;

    private UUID parentId;

    @NotNull(message = "组织类型不能为空")
    private OrgUnitType unitType;

    @NotNull(message = "组织状态不能为空")
    private TenantStatus status = TenantStatus.ACTIVE;
}

