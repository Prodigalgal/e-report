package com.pro.ereportbacken.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record WorkOrderTransferCreateRequestDTO(
        @NotNull(message = "目标组织不能为空") UUID targetOrgUnitId,
        @NotNull(message = "工单版本不能为空") Long expectedWorkOrderVersion,
        @NotBlank(message = "移交原因不能为空")
        @Size(max = 2000, message = "移交原因不能超过2000字符") String reason
) {
}
