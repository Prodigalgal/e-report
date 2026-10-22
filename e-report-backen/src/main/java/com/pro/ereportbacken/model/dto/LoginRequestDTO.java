package com.pro.ereportbacken.model.dto;

import com.pro.ereportbacken.model.pojo.AuthClientType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LoginRequestDTO {
    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;

    @NotBlank(message = "登录挑战不能为空")
    private String challengeId;

    private String captchaAnswer;

    @NotBlank(message = "安全挑战结果不能为空")
    private String powNonce;

    @NotNull(message = "客户端类型不能为空")
    private AuthClientType clientType;

    @Size(max = 64, message = "设备公钥指纹过长")
    private String deviceKeyId;

    @Size(max = 1024, message = "设备公钥过长")
    private String devicePublicKey;

    @Size(max = 256, message = "设备签名过长")
    private String deviceSignature;

    @Size(max = 100, message = "客户端实例标识过长")
    private String clientInstanceId;

    @Valid
    private DeviceContextDTO deviceContext;

    @Valid
    private WebEnvironmentDTO webEnvironment;

    private Boolean rememberMe;
}
