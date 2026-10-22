package com.pro.ereportbacken.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RefreshTokenRequestDTO {

    @NotBlank(message = "刷新凭据不能为空")
    @Size(max = 128, message = "刷新凭据格式不正确")
    private String refreshToken;
}
