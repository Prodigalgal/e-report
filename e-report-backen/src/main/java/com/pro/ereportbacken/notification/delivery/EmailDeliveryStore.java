package com.pro.ereportbacken.notification.delivery;

import com.pro.ereportbacken.model.pojo.EmailDelivery;
import com.pro.ereportbacken.notification.EmailNotificationRecipientResolver;
import com.pro.ereportbacken.repository.EmailDeliveryRepository;
import com.pro.ereportbacken.time.UtcTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

@Service
public class EmailDeliveryStore {
    private final EmailDeliveryRepository repository;

    public EmailDeliveryStore(EmailDeliveryRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UUID start(EmailDeliveryContext context, String senderAddress) {
        EmailDelivery delivery = new EmailDelivery();
        delivery.setTenantId(Objects.requireNonNull(context.tenantId(), "邮件租户不能为空"));
        delivery.setOrgUnitId(context.orgUnitId());
        delivery.setCategory(Objects.requireNonNull(context.category(), "邮件类型不能为空"));
        delivery.setRelatedId(context.relatedId());
        delivery.setRecipientUserId(context.recipientUserId());
        delivery.setRecipientAddress(EmailNotificationRecipientResolver.maskAddress(context.recipientAddress()));
        delivery.setSenderAddress(senderAddress == null ? null : EmailNotificationRecipientResolver.maskAddress(senderAddress));
        delivery.setSubject(Objects.requireNonNull(context.subject(), "邮件主题不能为空"));
        delivery.setStatus(EmailDeliveryStatus.SENDING);
        delivery.setStartedAt(UtcTime.now());
        return repository.saveAndFlush(delivery).getId();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(UUID id, EmailDeliveryStatus status, long durationMs, String failureCode) {
        if (status != EmailDeliveryStatus.SMTP_ACCEPTED && status != EmailDeliveryStatus.FAILED && status != EmailDeliveryStatus.UNKNOWN) {
            throw new IllegalArgumentException("邮件结束状态不合法");
        }
        if (repository.complete(id, status, UtcTime.now(), Math.max(0, durationMs), failureCode) != 1) {
            throw new IllegalStateException("邮件记录不存在或已经结束");
        }
    }
}
