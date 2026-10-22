package com.pro.ereportbacken.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DepartmentUpdateRequestDTO(
        @NotBlank(message = "部门名称不能为空")
        @Size(max = 100, message = "部门名称不能超过100字符") String name
) {
}
