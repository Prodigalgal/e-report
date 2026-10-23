package com.pro.ereportbacken.notification;

import static com.pro.ereportbacken.notification.delivery.EmailDeliveryTestSupport.tracker;
import static com.pro.ereportbacken.notification.delivery.EmailDeliveryTestSupport.dispatcher;

import com.pro.ereportbacken.model.dto.MailSenderAccountUpdateRequestDTO;
import com.pro.ereportbacken.model.dto.MailSenderTestRequestDTO;
import com.pro.ereportbacken.notification.MailSenderConfigurationService.MailSenderAccountConfiguration;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.MailSendException;

import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MailSenderTestServiceTests {

    @Test
    void testsInheritedSenderByIdWithoutAcceptingTransportOrCredentialOverrides() {
        var configurationService = mock(MailSenderConfigurationService.class);
        var factory = mock(ConfiguredMailSenderFactory.class);
        UUID senderId = UUID.randomUUID();
        UUID countyId = UUID.randomUUID();
        var account = new MailSenderAccountConfiguration(senderId, "CUSTOM", "smtp.parent.example", 465,
                "parent", "parent-secret", "parent@example.com", true, true, false, false,
                "PASSWORD", "AUTO", null, null, null, "TLSv1.2 TLSv1.3", 10000, 30000, 30000,
                null, null, null, null, null, 10, new MailSenderOrigin(null, null, true));
        var mailSender = mock(JavaMailSender.class);
        var message = new MimeMessage(Session.getInstance(new Properties()));
        when(configurationService.resolveInherited(senderId, countyId)).thenReturn(account);
        when(factory.createConfigured(account)).thenReturn(new ConfiguredMailSenderFactory.MailSenderContext(
                mailSender, "parent@example.com"));
        when(mailSender.createMimeMessage()).thenReturn(message);

        var result = new MailSenderTestService(configurationService, dispatcher(factory), tracker()).send(
                new MailSenderTestRequestDTO(null, "test@example.com", senderId), countyId);

        assertThat(result.success()).isTrue();
        verify(mailSender).send(message);
        verify(configurationService, never()).resolveDraft(any(), any());
        verify(configurationService, never()).update(any(), any());
    }

    @Test
    void rejectsMissingSenderSelectionBeforeAnySmtpAttempt() {
        var configurationService = mock(MailSenderConfigurationService.class);
        var factory = mock(ConfiguredMailSenderFactory.class);
        assertThatThrownBy(() -> new MailSenderTestService(configurationService, dispatcher(factory), tracker()).send(
                new MailSenderTestRequestDTO(null, "test@example.com", null), UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class);
        org.mockito.Mockito.verifyNoInteractions(configurationService, factory);
    }

    @Test
    void sendsDraftWithoutPersistingTheSenderConfiguration() {
        MailSenderConfigurationService configurationService = mock(MailSenderConfigurationService.class);
        ConfiguredMailSenderFactory factory = mock(ConfiguredMailSenderFactory.class);
        MailSenderAccountUpdateRequestDTO request = new MailSenderAccountUpdateRequestDTO(
                null, "smtp.example.com", 465, "sender", "secret", "sender@example.com",
                true, true, false, "CUSTOM", "PASSWORD", "AUTO", false, null, null, null,
                "TLSv1.2 TLSv1.3", 10000, 30000, 30000, null, null, null, null, null, null);
        UUID orgUnitId = UUID.randomUUID();
        MailSenderAccountConfiguration account = new MailSenderAccountConfiguration(
                UUID.randomUUID(), "CUSTOM", "smtp.example.com", 465, "sender", "secret",
                "sender@example.com", true, true, false, false, "PASSWORD", "AUTO",
                null, null, null, "TLSv1.2 TLSv1.3", 10000, 30000, 30000,
                null, null, null, null, null, 10, new MailSenderOrigin(null, null, false));
        JavaMailSender javaMailSender = mock(JavaMailSender.class);
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        when(configurationService.resolveDraft(request, orgUnitId)).thenReturn(account);
        when(factory.create(account)).thenReturn(
                new ConfiguredMailSenderFactory.MailSenderContext(javaMailSender, "sender@example.com"));
        when(javaMailSender.createMimeMessage()).thenReturn(message);

        MailSenderTestService service = new MailSenderTestService(configurationService, dispatcher(factory), tracker());
        var result = service.send(new MailSenderTestRequestDTO(request, "test@example.com", null), orgUnitId);

        assertThat(result.success()).isTrue();
        assertThat(result.message()).contains("已接受");
        verify(javaMailSender).send(message);
        verify(configurationService, never()).update(any(), any());
        verify(configurationService).resolveDraft(request, orgUnitId);
    }

    @Test
    void hidesSmtpExceptionDetailsFromTestResult() {
        MailSenderConfigurationService configurationService = mock(MailSenderConfigurationService.class);
        ConfiguredMailSenderFactory factory = mock(ConfiguredMailSenderFactory.class);
        MailSenderAccountUpdateRequestDTO request = new MailSenderAccountUpdateRequestDTO(
                null, "smtp.example.com", 465, "sender", "private-secret", "sender@example.com",
                true, true, false, "CUSTOM", "PASSWORD", "AUTO", false, null, null, null,
                "TLSv1.2 TLSv1.3", 10000, 30000, 30000, null, null, null, null, null, null);
        UUID orgUnitId = UUID.randomUUID();
        MailSenderAccountConfiguration account = new MailSenderAccountConfiguration(
                UUID.randomUUID(), "CUSTOM", "smtp.example.com", 465, "sender", "private-secret",
                "sender@example.com", true, true, false, false, "PASSWORD", "AUTO",
                null, null, null, "TLSv1.2 TLSv1.3", 10000, 30000, 30000,
                null, null, null, null, null, 10, new MailSenderOrigin(null, null, false));
        JavaMailSender javaMailSender = mock(JavaMailSender.class);
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        when(configurationService.resolveDraft(request, orgUnitId)).thenReturn(account);
        when(factory.create(account)).thenReturn(
                new ConfiguredMailSenderFactory.MailSenderContext(javaMailSender, "sender@example.com"));
        when(javaMailSender.createMimeMessage()).thenReturn(message);
        org.mockito.Mockito.doThrow(new MailSendException("private-secret was rejected"))
                .when(javaMailSender).send(message);

        var result = new MailSenderTestService(configurationService, dispatcher(factory), tracker())
                .send(new MailSenderTestRequestDTO(request, "test@example.com", null), orgUnitId);

        assertThat(result.success()).isFalse();
        assertThat(result.message()).doesNotContain("private-secret");
        verify(configurationService, never()).update(any(), any());
    }
}
