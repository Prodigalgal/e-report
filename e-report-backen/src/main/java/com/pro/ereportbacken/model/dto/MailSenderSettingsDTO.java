package com.pro.ereportbacken.model.dto;

import java.util.UUID;
import java.util.List;
import com.pro.ereportbacken.notification.MailSenderPoolStrategy;

public record MailSenderSettingsDTO(
        boolean configured,
        boolean localOverride,
        List<MailSenderAccountDTO> senders,
        String sourceScope,
        UUID sourceOrgUnitId,
        String sourceOrgUnitName,
        boolean inherited,
        String configurationError,
        boolean managedExternally,
        MailSenderPoolStrategy strategy,
        List<MailSenderAccountDTO> localSenders,
        List<MailSenderAccountDTO> inheritedSenders,
        String inheritedConfigurationError
) {
}
