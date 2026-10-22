package com.pro.ereportbacken.model.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import com.pro.ereportbacken.notification.MailSenderPoolStrategy;

import java.util.List;

public record MailSenderSettingsUpdateRequestDTO(
        @NotNull(message = "本级发件方列表不能为空")
        List<@Valid MailSenderAccountUpdateRequestDTO> senders,
        MailSenderPoolStrategy strategy
) {
    public MailSenderSettingsUpdateRequestDTO {
        strategy = strategy == null ? MailSenderPoolStrategy.REPLACE : strategy;
    }
}
