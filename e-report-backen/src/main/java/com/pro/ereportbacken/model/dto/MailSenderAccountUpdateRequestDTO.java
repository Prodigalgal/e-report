package com.pro.ereportbacken.model.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record MailSenderAccountUpdateRequestDTO(
        UUID id,
        @NotBlank(message = "邮件服务器地址不能为空")
        @Size(max = 255, message = "邮件服务器地址不能超过255个字符")
        String host,
        @NotNull(message = "邮件服务器端口不能为空")
        @Min(value = 1, message = "邮件服务器端口必须大于0")
        @Max(value = 65535, message = "邮件服务器端口不能超过65535")
        Integer port,
        @Size(max = 255, message = "邮件服务器用户名不能超过255个字符")
        String username,
        @Size(max = 1024, message = "邮件授权码不能超过1024个字符")
        String password,
        @NotBlank(message = "发件地址不能为空")
        @Email(message = "发件地址格式不正确")
        @Size(max = 255, message = "发件地址不能超过255个字符")
        String fromAddress,
        @NotNull(message = "请选择是否启用SMTP认证")
        Boolean smtpAuth,
        @NotNull(message = "请选择是否启用SSL")
        Boolean sslEnabled,
        @NotNull(message = "请选择是否启用STARTTLS")
        Boolean startTlsEnabled,
        @Size(max = 40, message = "服务商标识不能超过40个字符")
        String providerId,
        @Size(max = 20, message = "认证方式无效")
        String authenticationMode,
        @Size(max = 20, message = "SMTP认证机制无效")
        String authMechanism,
        Boolean startTlsRequired,
        @Size(max = 255, message = "EHLO域名不能超过255个字符")
        @Pattern(regexp = "^$|^[A-Za-z0-9](?:[A-Za-z0-9.-]{0,251}[A-Za-z0-9])?$",
                message = "EHLO域名格式不正确")
        String ehloDomain,
        @Size(max = 255, message = "NTLM认证域不能超过255个字符")
        String authDomain,
        @Size(max = 255, message = "SASL Realm不能超过255个字符")
        String saslRealm,
        @Size(max = 64, message = "TLS协议配置不能超过64个字符")
        String sslProtocols,
        @Min(value = 1000, message = "连接超时不能少于1秒")
        @Max(value = 120000, message = "连接超时不能超过120秒")
        Integer connectionTimeoutMs,
        @Min(value = 1000, message = "读取超时不能少于1秒")
        @Max(value = 120000, message = "读取超时不能超过120秒")
        Integer readTimeoutMs,
        @Min(value = 1000, message = "写入超时不能少于1秒")
        @Max(value = 120000, message = "写入超时不能超过120秒")
        Integer writeTimeoutMs,
        @Size(max = 255, message = "OAuth Client ID不能超过255个字符")
        String oauthClientId,
        @Size(max = 2048, message = "OAuth Client Secret不能超过2048个字符")
        String oauthClientSecret,
        @Size(max = 4096, message = "OAuth Refresh Token不能超过4096个字符")
        String oauthRefreshToken,
        @Size(max = 128, message = "OAuth租户标识不能超过128个字符")
        String oauthTenantId,
        @Size(max = 1024, message = "OAuth Scope不能超过1024个字符")
        String oauthScope,
        @Min(value = 0, message = "单封发送间隔不能小于0秒")
        @Max(value = 3600, message = "单封发送间隔不能超过3600秒")
        Integer sendIntervalSeconds
) {
}
