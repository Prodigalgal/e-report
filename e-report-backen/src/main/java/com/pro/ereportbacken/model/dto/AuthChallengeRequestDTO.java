package com.pro.ereportbacken.model.dto;

import com.pro.ereportbacken.model.pojo.AuthClientType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.LinkedHashSet;
import java.util.Set;

@Data
public class AuthChallengeRequestDTO {

    @NotBlank(message = "用户名不能为空")
    @Size(max = 50, message = "用户名不能超过50字符")
    private String username;

    @NotNull(message = "客户端类型不能为空")
    private AuthClientType clientType;

    @Size(max = 64, message = "设备公钥指纹过长")
    private String deviceKeyId;

    @Size(max = 100, message = "客户端实例标识过长")
    private String clientInstanceId;

    @Pattern(regexp = "^$|[a-f0-9]{64}$", message = "WEB 环境摘要格式不正确")
    private String webFingerprintHash;

    @Size(max = 20)
    private Set<@Size(max = 50) String> webSecuritySignals = new LinkedHashSet<>();
}
