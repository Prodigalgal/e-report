// src/main/java/.../model/dto/UserUpdateRequestDTO.java
package com.pro.ereportbacken.model.dto;

import java.util.UUID;

import com.pro.ereportbacken.service.UserContactPolicy;
import com.pro.ereportbacken.model.pojo.UserOrganizationMoveStrategy;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserUpdateRequestDTO {
    @Size(max = 100, message = "姓名不能超过100字符")
    private String name;
    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = UserContactPolicy.PHONE_REGEX, message = UserContactPolicy.PHONE_MESSAGE)
    private String phone;
    @Email(message = "邮箱格式不正确")
    @Size(max = UserContactPolicy.MAX_EMAIL_LENGTH, message = "邮箱不能超过254字符")
    private String email;
    private UUID orgUnitId;
    private UserOrganizationMoveStrategy organizationMoveStrategy;
    private Long expectedAuthorizationVersion;
}


