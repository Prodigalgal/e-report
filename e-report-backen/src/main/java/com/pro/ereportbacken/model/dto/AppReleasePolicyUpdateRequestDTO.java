package com.pro.ereportbacken.model.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AppReleasePolicyUpdateRequestDTO(
        @NotNull(message = "请选择是否强制更新。")
        Boolean forceUpdate,
        @NotNull(message = "请填写最低支持构建号。")
        @Min(value = 0, message = "最低支持构建号不能小于0。")
        Integer minSupportedVersionCode,
        @Size(max = 2000, message = "更新说明不能超过2000字符。")
        String releaseNotes
) {
}
