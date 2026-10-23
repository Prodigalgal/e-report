package com.pro.ereportbacken.notification;

import com.pro.ereportbacken.model.dto.MailSenderTestRequestDTO;
import com.pro.ereportbacken.model.dto.MailSenderTestResultDTO;
import com.pro.ereportbacken.notification.delivery.EmailDeliveryCategory;
import com.pro.ereportbacken.notification.delivery.EmailDeliveryTracker;
import com.pro.ereportbacken.notification.delivery.EmailDispatchService;
import com.pro.ereportbacken.notification.delivery.EmailDispatchResult;
import com.pro.ereportbacken.notification.delivery.EmailDeliveryStatus;
import java.time.Duration;
import java.time.Instant;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import lombok.extern.log4j.Log4j2;

@Service
@Log4j2
public class MailSenderTestService {

    private final MailSenderConfigurationService configurationService;
    private final EmailDispatchService dispatcher;
    private final EmailDeliveryTracker deliveryTracker;

    public MailSenderTestService(MailSenderConfigurationService configurationService,
                                 EmailDispatchService dispatcher,
                                 EmailDeliveryTracker deliveryTracker) {
        this.configurationService = configurationService;
        this.dispatcher = dispatcher;
        this.deliveryTracker = deliveryTracker;
    }

    public MailSenderTestResultDTO send(MailSenderTestRequestDTO request, java.util.UUID orgUnitId) {
        if (request == null || !StringUtils.hasText(request.testRecipient())
                || (request.sender() == null) == (request.inheritedSenderId() == null)) {
            throw new IllegalArgumentException("发件配置和测试收件地址不能为空。");
        }

        var sender = request.inheritedSenderId() == null
                ? configurationService.resolveDraft(request.sender(), orgUnitId)
                : configurationService.resolveInherited(request.inheritedSenderId(), orgUnitId);
        var deliveryContext = deliveryTracker.context(EmailDeliveryCategory.CONFIGURATION_TEST, orgUnitId,
                null, null, request.testRecipient().trim(), "邮件发件配置测试");
        if (!sender.configured()) {
            deliveryTracker.configurationFailed(deliveryContext);
            return new MailSenderTestResultDTO(false, "请先补齐服务器、发件地址和认证信息。", "CONFIGURATION");
        }

        try {
            EmailDispatchResult result = dispatcher.test(deliveryContext, sender, context -> {
                JavaMailSender mailSender = context.mailSender();
                MimeMessage message = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
                helper.setFrom(context.fromAddress());
                helper.setTo(request.testRecipient().trim());
                helper.setSubject("邮件发件配置测试");
                helper.setText("这是一封由 e-report 管理员主动发起的 SMTP 配置测试邮件。", false);
                mailSender.send(message);
            }, request.inheritedSenderId() != null);
            if (result.status() == EmailDeliveryStatus.SMTP_ACCEPTED) {
                return new MailSenderTestResultDTO(true, "SMTP服务器已接受测试邮件。", null);
            }
            if (result.retryAt() != null && result.failureCode() == null) {
                long seconds = Math.max(1, (Duration.between(Instant.now(), result.retryAt()).toMillis() + 999) / 1000);
                return new MailSenderTestResultDTO(false, "该发件账号正在发送或冷却，请在" + seconds + "秒后重试。", "COOLDOWN");
            }
            return new MailSenderTestResultDTO(false, "测试邮件发送失败，请查看邮件台账；暂态错误会触发账号退避。", "DELIVERY");
        } catch (IllegalStateException | IllegalArgumentException exception) {
            log.warn("发件配置测试未完成。senderKey={}, exceptionType={}",
                    com.pro.ereportbacken.notification.delivery.MailDeliveryKeys.account(sender), exception.getClass().getSimpleName());
            return new MailSenderTestResultDTO(
                    false, "测试邮件发送失败，请检查SMTP服务器、认证方式和加密配置。", "DELIVERY");
        }
    }
}
