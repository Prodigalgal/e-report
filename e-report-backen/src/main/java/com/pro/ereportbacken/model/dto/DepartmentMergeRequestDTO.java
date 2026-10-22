package com.pro.ereportbacken.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record DepartmentMergeRequestDTO(
        @NotNull(message = "目标部门不能为空") UUID targetDepartmentId,
        @NotBlank(message = "合并原因不能为空")
        @Size(max = 1000, message = "合并原因不能超过1000字符") String reason
) {
}
