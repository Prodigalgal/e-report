// src/main/java/.../model/dto/PasswordResetRequestDTO.java
package com.pro.ereportbacken.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PasswordResetRequestDTO {
    @NotBlank(message = "新密码不能为空")
    @Size(min = 8, max = 100, message = "新密码长度必须在8到100字符之间")
    private String newPassword;

    @NotBlank(message = "确认密码不能为空")
    @Size(min = 8, max = 100, message = "确认密码长度必须在8到100字符之间")
    private String confirmPassword;

    @JsonIgnore
    @AssertTrue(message = "两次输入的新密码不一致")
    public boolean isPasswordConfirmed() {
        return newPassword != null && newPassword.equals(confirmPassword);
    }
}
