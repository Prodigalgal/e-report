package com.pro.ereportbacken.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record DepartmentCreateRequestDTO(
        @NotNull(message = "所属组织不能为空") UUID orgUnitId,
        UUID parentId,
        @NotBlank(message = "部门编码不能为空")
        @Size(max = 50, message = "部门编码不能超过50字符") String code,
        @NotBlank(message = "部门名称不能为空")
        @Size(max = 100, message = "部门名称不能超过100字符") String name
) {
}
