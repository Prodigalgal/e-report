package com.pro.ereportbacken.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record DepartmentMoveRequestDTO(
        UUID targetParentId,
        @NotBlank(message = "调整原因不能为空")
        @Size(max = 1000, message = "调整原因不能超过1000字符") String reason
) {
}
