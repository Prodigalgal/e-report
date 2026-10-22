package com.pro.ereportbacken.model.dto;

import org.springframework.util.StringUtils;

import java.util.Objects;

public record UserTransferFieldChangeDTO(
        String field,
        String beforeValue,
        String afterValue
) {
    public UserTransferFieldChangeDTO {
        if (!StringUtils.hasText(field)) {
            throw new IllegalArgumentException("变更字段名称不能为空。");
        }
        if (Objects.equals(beforeValue, afterValue)) {
            throw new IllegalArgumentException("字段 Diff 必须包含实际变化。");
        }
    }
}
