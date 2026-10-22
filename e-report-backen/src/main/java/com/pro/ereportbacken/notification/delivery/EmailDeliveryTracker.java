package com.pro.ereportbacken.notification.delivery;

import com.pro.ereportbacken.security.TenantContext;
import jakarta.mail.MessagingException;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@Log4j2
public class EmailDeliveryTracker {
    private final EmailDeliveryStore store;
    private final TenantContext tenantContext;

    public EmailDeliveryTracker(EmailDeliveryStore store, TenantContext tenantContext) {
        this.store = store;
        this.tenantContext = tenantContext;
    }

    public EmailDeliveryContext context(EmailDeliveryCategory category, UUID orgUnitId, UUID relatedId,
                                        UUID recipientUserId, String recipientAddress, String subject) {
        return new EmailDeliveryContext(tenantContext.currentTenantIdOrDefault(), orgUnitId, category,
                relatedId, recipientUserId, recipientAddress, subject);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void send(EmailDeliveryContext context, String senderAddress, SendingOperation operation) throws MessagingException {
        UUID id = store.start(context, senderAddress);
        long started = System.nanoTime();
        try {
            operation.run();
        } catch (MessagingException | RuntimeException failure) {
            EmailDeliveryFailure reason = EmailDeliveryFailure.classify(failure);
            finish(id, reason == EmailDeliveryFailure.TIMEOUT ? EmailDeliveryStatus.UNKNOWN : EmailDeliveryStatus.FAILED,
                    started, reason.name());
            throw failure;
        }
        finish(id, EmailDeliveryStatus.SMTP_ACCEPTED, started, null);
    }

    public void configurationFailed(EmailDeliveryContext context) {
        UUID id = store.start(context, null);
        finish(id, EmailDeliveryStatus.FAILED, System.nanoTime(), EmailDeliveryFailure.CONFIGURATION.name());
    }

    private void finish(UUID id, EmailDeliveryStatus status, long started, String failureCode) {
        try {
            store.complete(id, status, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started), failureCode);
        } catch (RuntimeException failure) {
            // SMTP 结果不能因台账收尾失败而重新发送；未收尾记录在查询时显示“结果未确认”。
            log.error("邮件台账收尾失败。deliveryId={}, smtpResult={}", id, status, failure);
        }
    }

    @FunctionalInterface
    public interface SendingOperation {
        void run() throws MessagingException;
    }
}
