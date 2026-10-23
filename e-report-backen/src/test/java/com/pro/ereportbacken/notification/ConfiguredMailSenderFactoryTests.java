package com.pro.ereportbacken.notification;

import com.pro.ereportbacken.notification.MailSenderConfigurationService.MailSenderAccountConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConfiguredMailSenderFactoryTests {

    @Test
    void constructsTheSpecifiedSenderWithoutSelectingAnotherPoolAccount() {
        MailSenderConfigurationService configurationService = mock(MailSenderConfigurationService.class);
        MailSenderOAuthTokenService oauthTokenService = mock(MailSenderOAuthTokenService.class);
        ConfiguredMailSenderFactory factory = new ConfiguredMailSenderFactory(
                configurationService, oauthTokenService);

        var context = factory.createConfigured(sender("smtp.second.example", "second", "second@example.com"));

        JavaMailSenderImpl mailSender = (JavaMailSenderImpl) context.mailSender();
        assertThat(mailSender.getHost()).isEqualTo("smtp.second.example");
        assertThat(mailSender.getPort()).isEqualTo(465);
        assertThat(mailSender.getUsername()).isEqualTo("second");
        assertThat(mailSender.getPassword()).isEqualTo("secret-second");
        assertThat(context.fromAddress()).isEqualTo("second@example.com");
        assertThat(mailSender.getJavaMailProperties())
                .containsEntry("mail.smtp.ssl.enable", "true")
                .containsEntry("mail.smtp.ssl.checkserveridentity", "true")
                .containsEntry("mail.smtp.ssl.protocols", "TLSv1.2 TLSv1.3")
                .containsEntry("mail.smtp.starttls.enable", "false")
                .containsEntry("mail.smtp.starttls.required", "false")
                .containsEntry("mail.smtp.connectiontimeout", "10000");
    }

    @Test
    void mapsProviderSpecificSaslEhloTlsAndTimeoutOptions() {
        var account = new MailSenderAccountConfiguration(
                UUID.randomUUID(), "CUSTOM", "smtp.example.com", 587, "sender", "secret",
                "sender@example.com", true, false, true, true, "PASSWORD", "NTLM",
                "mail.example.com", "CORP", null, "TLSv1.3", 4000, 5000, 6000,
                null, null, null, null, null, 10, new MailSenderOrigin(null, null, false));
        ConfiguredMailSenderFactory factory = new ConfiguredMailSenderFactory(
                mock(MailSenderConfigurationService.class),
                mock(MailSenderOAuthTokenService.class));

        JavaMailSenderImpl mailSender = (JavaMailSenderImpl) factory.create(account).mailSender();

        assertThat(mailSender.getJavaMailProperties())
                .containsEntry("mail.smtp.auth.mechanisms", "NTLM")
                .containsEntry("mail.smtp.localhost", "mail.example.com")
                .containsEntry("mail.smtp.auth.ntlm.domain", "CORP")
                .containsEntry("mail.smtp.ssl.protocols", "TLSv1.3")
                .containsEntry("mail.smtp.starttls.required", "true")
                .containsEntry("mail.smtp.connectiontimeout", "4000")
                .containsEntry("mail.smtp.timeout", "5000")
                .containsEntry("mail.smtp.writetimeout", "6000");
    }

    @Test
    void mapsAuthenticationMechanismNamesToSmtpProtocolNames() {
        var account = new MailSenderAccountConfiguration(
                UUID.randomUUID(), "CUSTOM", "smtp.example.com", 465, "sender", "secret",
                "sender@example.com", true, true, false, false, "PASSWORD", "DIGEST_MD5",
                null, null, null, "TLSv1.2 TLSv1.3", 10000, 30000, 30000,
                null, null, null, null, null, 10, new MailSenderOrigin(null, null, false));
        ConfiguredMailSenderFactory factory = new ConfiguredMailSenderFactory(
                mock(MailSenderConfigurationService.class),
                mock(MailSenderOAuthTokenService.class));

        JavaMailSenderImpl mailSender = (JavaMailSenderImpl) factory.create(account).mailSender();

        assertThat(mailSender.getJavaMailProperties()).containsEntry(
                "mail.smtp.auth.mechanisms", "DIGEST-MD5");
    }

    @Test
    void configuresDigestSaslRealmThroughSaslProvider() {
        var account = new MailSenderAccountConfiguration(
                UUID.randomUUID(), "CUSTOM", "smtp.example.com", 465, "sender", "secret",
                "sender@example.com", true, true, false, false, "PASSWORD", "DIGEST_MD5",
                null, null, "mail-realm", "TLSv1.2 TLSv1.3", 10000, 30000, 30000,
                null, null, null, null, null, 10, new MailSenderOrigin(null, null, false));
        ConfiguredMailSenderFactory factory = new ConfiguredMailSenderFactory(
                mock(MailSenderConfigurationService.class),
                mock(MailSenderOAuthTokenService.class));

        JavaMailSenderImpl mailSender = (JavaMailSenderImpl) factory.create(account).mailSender();

        assertThat(mailSender.getJavaMailProperties())
                .containsEntry("mail.smtp.sasl.enable", "true")
                .containsEntry("mail.smtp.sasl.mechanisms", "DIGEST-MD5")
                .containsEntry("mail.smtp.sasl.realm", "mail-realm")
                .doesNotContainKey("mail.smtp.auth.mechanisms");
    }

    @Test
    void configuresCramMd5ThroughSaslProvider() {
        var account = new MailSenderAccountConfiguration(
                UUID.randomUUID(), "CUSTOM", "smtp.example.com", 465, "sender", "secret",
                "sender@example.com", true, true, false, false, "PASSWORD", "CRAM_MD5",
                null, null, null, "TLSv1.2 TLSv1.3", 10000, 30000, 30000,
                null, null, null, null, null, 10, new MailSenderOrigin(null, null, false));
        ConfiguredMailSenderFactory factory = new ConfiguredMailSenderFactory(
                mock(MailSenderConfigurationService.class),
                mock(MailSenderOAuthTokenService.class));

        JavaMailSenderImpl mailSender = (JavaMailSenderImpl) factory.create(account).mailSender();

        assertThat(mailSender.getJavaMailProperties())
                .containsEntry("mail.smtp.sasl.enable", "true")
                .containsEntry("mail.smtp.sasl.mechanisms", "CRAM-MD5")
                .doesNotContainKey("mail.smtp.auth.mechanisms");
    }

    @Test
    void forcesXoauth2AndDisablesPasswordMechanismsForOAuthAccounts() {
        MailSenderOAuthTokenService oauthTokenService = mock(MailSenderOAuthTokenService.class);
        var account = new MailSenderAccountConfiguration(
                UUID.randomUUID(), "GMAIL", "smtp.gmail.com", 587, "sender@gmail.com", null,
                "sender@gmail.com", true, false, true, true, "OAUTH2", "XOAUTH2",
                null, null, null, "TLSv1.2 TLSv1.3", 10000, 30000, 30000,
                "client", "client-secret", "refresh-token", null, "https://mail.google.com/", 10, new MailSenderOrigin(null, null, false));
        when(oauthTokenService.accessToken(account)).thenReturn(
                new MailSenderOAuthTokenService.OAuthAccessToken("access-token", "refresh", null));
        ConfiguredMailSenderFactory factory = new ConfiguredMailSenderFactory(
                mock(MailSenderConfigurationService.class), oauthTokenService);

        JavaMailSenderImpl mailSender = (JavaMailSenderImpl) factory.create(account).mailSender();

        assertThat(mailSender.getPassword()).isEqualTo("access-token");
        assertThat(mailSender.getJavaMailProperties())
                .containsEntry("mail.smtp.auth.mechanisms", "XOAUTH2")
                .containsEntry("mail.smtp.auth.login.disable", "true")
                .containsEntry("mail.smtp.auth.plain.disable", "true");
        verify(oauthTokenService).accessToken(account);
    }

    @Test
    void persistsRotatedRefreshTokenAtTheAccountSourceWithinAMergedPool() {
        MailSenderConfigurationService configurationService = mock(MailSenderConfigurationService.class);
        MailSenderOAuthTokenService oauthTokenService = mock(MailSenderOAuthTokenService.class);
        UUID senderId = UUID.randomUUID();
        UUID sourceOrgUnitId = UUID.randomUUID();
        var account = new MailSenderAccountConfiguration(
                senderId, "MICROSOFT_365", "smtp.office365.com", 587, "sender@example.com", null,
                "sender@example.com", true, false, true, true, "OAUTH2", "XOAUTH2",
                null, null, null, "TLSv1.2 TLSv1.3", 10000, 30000, 30000,
                "client", "secret", "old-refresh", "tenant", null, 10, new MailSenderOrigin(sourceOrgUnitId, "南靖县", false));
        when(oauthTokenService.accessToken(account)).thenReturn(
                new MailSenderOAuthTokenService.OAuthAccessToken("access", "old-refresh", "new-refresh"));
        ConfiguredMailSenderFactory factory = new ConfiguredMailSenderFactory(
                configurationService, oauthTokenService);

        factory.createConfigured(account);

        verify(configurationService).persistRotatedOAuthRefreshToken(
                sourceOrgUnitId, senderId, "old-refresh", "new-refresh");
    }

    private MailSenderAccountConfiguration sender(String host, String username, String fromAddress) {
        return new MailSenderAccountConfiguration(
                UUID.randomUUID(), "CUSTOM", host, 465, username, "secret-" + username, fromAddress,
                true, true, false, false, "PASSWORD", "AUTO", null, null, null,
                "TLSv1.2 TLSv1.3", 10000, 30000, 30000, null, null, null, null, null, 10, new MailSenderOrigin(null, null, false));
    }
}
