package com.pro.ereportbacken.model.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import com.pro.ereportbacken.service.UserContactPolicy;

@Data
public class TenantCreateRequestDTO {
    @NotNull(message = "必须选择省级租户组")
    private UUID tenantGroupId;

    @NotBlank(message = "租户编码不能为空")
    @Size(max = 50, message = "租户编码不能超过50字符")
    private String code;

    @NotBlank(message = "市公司名称不能为空")
    @Size(max = 100, message = "市公司名称不能超过100字符")
    private String name;

    @NotBlank(message = "根组织名称不能为空")
    @Size(max = 100, message = "根组织名称不能超过100字符")
    private String rootOrgName;

    @NotBlank(message = "根组织编码不能为空")
    @Size(max = 50, message = "根组织编码不能超过50字符")
    private String rootOrgCode;

    @NotBlank(message = "初始管理员用户名不能为空")
    @Size(min = 3, max = 50, message = "初始管理员用户名长度必须在3到50字符之间")
    private String adminUsername;

    @NotBlank(message = "初始管理员密码不能为空")
    @Size(min = 8, max = 100, message = "初始管理员密码长度必须在8到100字符之间")
    private String adminPassword;

    @NotBlank(message = "初始管理员姓名不能为空")
    @Size(max = 100, message = "初始管理员姓名不能超过100字符")
    private String adminName;

    @NotBlank(message = "初始管理员手机号不能为空")
    @Pattern(regexp = UserContactPolicy.PHONE_REGEX, message = UserContactPolicy.PHONE_MESSAGE)
    private String adminPhone;

    @Email(message = "初始管理员邮箱格式不正确")
    @Size(max = UserContactPolicy.MAX_EMAIL_LENGTH, message = "初始管理员邮箱不能超过254字符")
    private String adminEmail;
}

