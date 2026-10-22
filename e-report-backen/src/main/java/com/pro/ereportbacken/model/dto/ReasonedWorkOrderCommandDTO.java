package com.pro.ereportbacken.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ReasonedWorkOrderCommandDTO {
    @NotBlank(message = "操作原因不能为空")
    @Size(max = 2000, message = "操作原因不能超过2000字符")
    private String reason;

    @NotNull(message = "工单版本不能为空")
    private Long expectedVersion;
}
