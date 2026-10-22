package com.pro.ereportbacken.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record WorkOrderTransferDecisionRequestDTO(
        @NotNull(message = "工单版本不能为空") Long expectedWorkOrderVersion,
        @NotBlank(message = "处理意见不能为空")
        @Size(max = 2000, message = "处理意见不能超过2000字符") String comment
) {
}
