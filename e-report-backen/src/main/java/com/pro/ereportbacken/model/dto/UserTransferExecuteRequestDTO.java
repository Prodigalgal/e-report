package com.pro.ereportbacken.model.dto;

import jakarta.validation.constraints.NotBlank;

public record UserTransferExecuteRequestDTO(
        @NotBlank(message = "预览确认令牌不能为空") String previewToken
) {
}
