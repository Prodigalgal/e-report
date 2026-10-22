package com.pro.ereportbacken.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DepartmentReasonRequestDTO(
        @NotBlank(message = "操作原因不能为空")
        @Size(max = 1000, message = "操作原因不能超过1000字符") String reason
) {
}
