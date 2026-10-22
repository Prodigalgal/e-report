package com.pro.ereportbacken.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PasswordChangeRequestDTO {

    @NotBlank(message = "当前密码不能为空")
    @Size(max = 100, message = "当前密码不能超过100字符")
    private String currentPassword;

    @NotBlank(message = "新密码不能为空")
    @Size(min = 8, max = 100, message = "新密码长度必须在8到100字符之间")
    private String newPassword;
}
