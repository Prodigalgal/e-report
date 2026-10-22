package com.pro.ereportbacken.model.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AssignmentDecisionRequestDTO {
    @NotNull(message = "工单版本不能为空")
    private Long expectedVersion;

    @Size(max = 2000, message = "原因不能超过2000字符")
    private String reason;
}
