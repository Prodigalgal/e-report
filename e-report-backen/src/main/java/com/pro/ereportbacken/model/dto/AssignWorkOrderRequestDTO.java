package com.pro.ereportbacken.model.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AssignWorkOrderRequestDTO {
    @NotNull(message = "目标处理人员不能为空")
    private UUID assigneeMembershipId;

    @NotNull(message = "责任组织不能为空")
    private UUID responsibleOrgId;

    private UUID responsibleDepartmentId;

    @NotBlank(message = "派调单原因不能为空")
    @Size(max = 2000, message = "派调单原因不能超过2000字符")
    private String reason;

    @NotNull(message = "工单版本不能为空")
    private Long expectedVersion;
}

