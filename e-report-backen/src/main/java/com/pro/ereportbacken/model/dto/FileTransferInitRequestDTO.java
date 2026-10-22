package com.pro.ereportbacken.model.dto;

import com.pro.ereportbacken.model.pojo.FileTransferPurpose;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record FileTransferInitRequestDTO(
        @NotNull(message = "文件用途不能为空")
        FileTransferPurpose purpose,

        @NotBlank(message = "文件名不能为空")
        @Size(max = 255, message = "文件名不能超过255字符")
        String fileName,

        @Size(max = 100, message = "文件类型不能超过100字符")
        String contentType,

        @NotNull(message = "文件大小不能为空")
        @Positive(message = "文件大小必须大于0")
        Long totalSize,

        @Size(max = 64, message = "SHA-256 长度不正确")
        String sha256,

        Long chunkSize
) {
}
