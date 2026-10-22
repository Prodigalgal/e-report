package com.pro.ereportbacken.notification;

import com.pro.ereportbacken.notification.MailSenderConfigurationService.MailSenderAccountConfiguration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Properties;

@Component
public class ConfiguredMailSenderFactory {

    private final MailSenderConfigurationService configurationService;
    private final MailSenderRotationService rotationService;
    private final MailSenderOAuthTokenService oauthTokenService;

    public ConfiguredMailSenderFactory(MailSenderConfigurationService configurationService,
                                      MailSenderRotationService rotationService,
                                      MailSenderOAuthTokenService oauthTokenService) {
        this.configurationService = configurationService;
        this.rotationService = rotationService;
        this.oauthTokenService = oauthTokenService;
    }

    public MailSenderContext next() {
        MailSenderConfigurationService.MailSenderConfiguration configuration = configurationService.requireCurrent();
        int senderIndex = rotationService.nextIndex(configuration.senders().size());
        MailSenderAccountConfiguration sender = configuration.senders().get(senderIndex);
        return create(sender, sender.origin().orgUnitId(), !sender.origin().runtimeDefault());
    }

    public MailSenderContext create(MailSenderAccountConfiguration sender) {
        return create(sender, null, false);
    }

    private MailSenderContext create(MailSenderAccountConfiguration sender,
                                     java.util.UUID sourceOrgUnitId,
                                     boolean persistRotatedRefreshToken) {
        boolean oauthAuthentication = MailSenderAuthenticationMode.OAUTH2.name()
                .equals(sender.authenticationMode());
        JavaMailSenderImpl configured = new JavaMailSenderImpl();
        configured.setHost(sender.host());
        configured.setPort(sender.port());
        configured.setUsername(sender.username());
        if (oauthAuthentication) {
            MailSenderOAuthTokenService.OAuthAccessToken accessToken = oauthTokenService.accessToken(sender);
            configured.setPassword(accessToken.value());
            if (persistRotatedRefreshToken && accessToken.rotatedRefreshToken() != null) {
                configurationService.persistRotatedOAuthRefreshToken(
                        sourceOrgUnitId, sender.id(), accessToken.priorRefreshToken(),
                        accessToken.rotatedRefreshToken());
            }
        } else {
            configured.setPassword(sender.password());
        }
        configured.setProtocol("smtp");
        configured.setDefaultEncoding("UTF-8");

        Properties properties = new Properties();
        properties.put("mail.smtp.auth", String.valueOf(sender.smtpAuth()));
        properties.put("mail.smtp.ssl.enable", String.valueOf(sender.sslEnabled()));
        properties.put("mail.smtp.ssl.checkserveridentity", "true");
        properties.put("mail.smtp.ssl.protocols", sender.sslProtocols());
        properties.put("mail.smtp.starttls.enable", String.valueOf(sender.startTlsEnabled()));
        properties.put("mail.smtp.starttls.required", String.valueOf(sender.startTlsRequired()));
        properties.put("mail.smtp.connectiontimeout", String.valueOf(sender.connectionTimeoutMs()));
        properties.put("mail.smtp.timeout", String.valueOf(sender.readTimeoutMs()));
        properties.put("mail.smtp.writetimeout", String.valueOf(sender.writeTimeoutMs()));
        if (sender.smtpAuth()
                && !MailSenderAuthenticationMechanism.AUTO.name().equals(sender.authMechanism())) {
            String mechanism = MailSenderAuthenticationMechanism.valueOf(sender.authMechanism()).protocolName();
            boolean saslMechanism = !oauthAuthentication
                    && (MailSenderAuthenticationMechanism.CRAM_MD5.name().equals(sender.authMechanism())
                        || (MailSenderAuthenticationMechanism.DIGEST_MD5.name().equals(sender.authMechanism())
                            && StringUtils.hasText(sender.saslRealm())));
            if (saslMechanism) {
                properties.put("mail.smtp.sasl.enable", "true");
                properties.put("mail.smtp.sasl.mechanisms", mechanism);
            } else {
                properties.put("mail.smtp.auth.mechanisms", mechanism);
            }
        }
        if (StringUtils.hasText(sender.ehloDomain())) {
            properties.put("mail.smtp.localhost", sender.ehloDomain());
        }
        if (StringUtils.hasText(sender.authDomain())) {
            properties.put("mail.smtp.auth.ntlm.domain", sender.authDomain());
        }
        if (!oauthAuthentication && StringUtils.hasText(sender.saslRealm())) {
            properties.put("mail.smtp.sasl.realm", sender.saslRealm());
        }
        if (oauthAuthentication) {
            properties.put("mail.smtp.auth.mechanisms", MailSenderAuthenticationMechanism.XOAUTH2.name());
            properties.put("mail.smtp.auth.login.disable", "true");
            properties.put("mail.smtp.auth.plain.disable", "true");
        }
        configured.setJavaMailProperties(properties);
        return new MailSenderContext(configured, sender.fromAddress());
    }

    public record MailSenderContext(JavaMailSender mailSender, String fromAddress) {
    }
}
