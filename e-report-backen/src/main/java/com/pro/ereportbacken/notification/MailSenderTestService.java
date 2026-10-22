package com.pro.ereportbacken.notification;

import com.pro.ereportbacken.model.dto.MailSenderTestRequestDTO;
import com.pro.ereportbacken.model.dto.MailSenderTestResultDTO;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class MailSenderTestService {

    private final MailSenderConfigurationService configurationService;
    private final ConfiguredMailSenderFactory mailSenderFactory;

    public MailSenderTestService(MailSenderConfigurationService configurationService,
                                 ConfiguredMailSenderFactory mailSenderFactory) {
        this.configurationService = configurationService;
        this.mailSenderFactory = mailSenderFactory;
    }

    public MailSenderTestResultDTO send(MailSenderTestRequestDTO request, java.util.UUID orgUnitId) {
        if (request == null || !StringUtils.hasText(request.testRecipient())
                || (request.sender() == null) == (request.inheritedSenderId() == null)) {
            throw new IllegalArgumentException("发件配置和测试收件地址不能为空。");
        }

        var sender = request.inheritedSenderId() == null
                ? configurationService.resolveDraft(request.sender(), orgUnitId)
                : configurationService.resolveInherited(request.inheritedSenderId(), orgUnitId);
        if (!sender.configured()) {
            return new MailSenderTestResultDTO(false, "请先补齐服务器、发件地址和认证信息。", "CONFIGURATION");
        }

        try {
            ConfiguredMailSenderFactory.MailSenderContext context = mailSenderFactory.create(sender);
            JavaMailSender mailSender = context.mailSender();
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(context.fromAddress());
            helper.setTo(request.testRecipient().trim());
            helper.setSubject("邮件发件配置测试");
            helper.setText("这是一封由 e-report 管理员主动发起的 SMTP 配置测试邮件。", false);
            mailSender.send(message);
            return new MailSenderTestResultDTO(true, "SMTP服务器已接受测试邮件。", null);
        } catch (MailException | MessagingException | IllegalStateException | IllegalArgumentException exception) {
            return new MailSenderTestResultDTO(
                    false, "测试邮件发送失败，请检查SMTP服务器、认证方式和加密配置。", "DELIVERY");
        }
    }
}
