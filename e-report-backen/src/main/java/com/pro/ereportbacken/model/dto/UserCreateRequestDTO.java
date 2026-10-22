package com.pro.ereportbacken.model.dto;

import java.util.UUID;

import com.pro.ereportbacken.model.pojo.UserStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import com.pro.ereportbacken.service.UserContactPolicy;


@Data
public class UserCreateRequestDTO {
    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 50, message = "用户名长度必须在3到50字符之间")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 8, max = 100, message = "密码长度必须在8到100字符之间")
    private String password;

    @NotBlank(message = "姓名不能为空")
    @Size(max = 100, message = "姓名不能超过100字符")
    private String name;

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = UserContactPolicy.PHONE_REGEX, message = UserContactPolicy.PHONE_MESSAGE)
    private String phone;

    @Email(message = "邮箱格式不正确")
    @Size(max = UserContactPolicy.MAX_EMAIL_LENGTH, message = "邮箱不能超过254字符")
    private String email;

    private UserStatus status; // 可选，默认为 ACTIVE

    private UUID orgUnitId;

    private UUID departmentId;
}

