package com.pro.ereportbacken.model.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record UserTransferImportRequestDTO(@NotNull(message = "上传文件不能为空") UUID uploadId) {
}
