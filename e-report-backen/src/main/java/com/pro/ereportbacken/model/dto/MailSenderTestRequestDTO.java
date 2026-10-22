package com.pro.ereportbacken.model.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record MailSenderTestRequestDTO(
        @Valid MailSenderAccountUpdateRequestDTO sender,
        @NotBlank(message = "测试收件地址不能为空")
        @Email(message = "测试收件地址格式不正确")
        @Size(max = 255, message = "测试收件地址不能超过255个字符")
        String testRecipient,
        UUID inheritedSenderId
) {
}
