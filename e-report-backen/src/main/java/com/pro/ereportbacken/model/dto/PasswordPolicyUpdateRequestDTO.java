package com.pro.ereportbacken.model.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record PasswordPolicyUpdateRequestDTO(
        @NotNull(message = "密码最小长度不能为空")
        @Min(value = 8, message = "密码最小长度不能低于8位")
        @Max(value = 100, message = "密码最小长度不能超过100位")
        Integer minLength,
        @NotNull(message = "大写字母要求不能为空")
        Boolean requireUppercase,
        @NotNull(message = "小写字母要求不能为空")
        Boolean requireLowercase,
        @NotNull(message = "数字要求不能为空")
        Boolean requireDigit,
        @NotNull(message = "特殊字符要求不能为空")
        Boolean requireSpecial
) {
}
