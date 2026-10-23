package com.pro.ereportbacken.notification.delivery;

import com.pro.ereportbacken.model.pojo.EmailDispatch;
import com.pro.ereportbacken.repository.EmailDispatchRepository;
import com.pro.ereportbacken.time.UtcTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

@Service
public class EmailDispatchStore {
    private static final int MAX_ATTEMPTS = 10;
    private final EmailDispatchRepository repository;

    public EmailDispatchStore(EmailDispatchRepository repository) {
        this.repository = repository;
    }

    public Optional<EmailDispatchResult> inspect(String key) {
        return repository.findByDispatchKey(key).map(this::result);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Claim claim(String key, UUID tenantId, Duration leaseDuration) {
        LocalDateTime now = UtcTime.now();
        EmailDispatch dispatch = repository.findForUpdate(key).orElse(null);
        if (dispatch != null) {
            if (dispatch.getStatus() == EmailDeliveryStatus.SENDING && !dispatch.getLockedUntil().isAfter(now)) {
                dispatch.setStatus(EmailDeliveryStatus.FAILED);
                dispatch.setRetryable(false);
                dispatch.setFailureCode("INCOMPLETE");
                dispatch.setCompletedAt(now);
            }
            EmailDispatchResult previous = result(dispatch);
            if (previous != null) return new Claim(dispatch.getId(), null, previous);
        } else {
            dispatch = new EmailDispatch();
            dispatch.setTenantId(tenantId);
            dispatch.setDispatchKey(key);
            dispatch.setCreatedAt(now);
        }
        UUID token = UUID.randomUUID();
        dispatch.setStatus(EmailDeliveryStatus.SENDING);
        dispatch.setLeaseToken(token);
        dispatch.setLockedUntil(now.plus(leaseDuration));
        dispatch.setAttempts(dispatch.getAttempts() + 1);
        dispatch.setRetryable(false);
        dispatch.setNextAttemptAt(null);
        dispatch.setFailureCode(null);
        dispatch.setCompletedAt(null);
        repository.saveAndFlush(dispatch);
        return new Claim(dispatch.getId(), token, null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void renew(String key, UUID token, Duration leaseDuration) {
        repository.renew(key, token, UtcTime.now().plus(leaseDuration));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public EmailDispatchResult complete(String key, UUID token, MailFailurePolicy failure, Instant retryAt) {
        EmailDispatch dispatch = repository.findForUpdate(key).orElseThrow();
        if (!token.equals(dispatch.getLeaseToken())) {
            throw new IllegalStateException("邮件投递执行权已变化。");
        }
        dispatch.setCompletedAt(UtcTime.now());
        if (failure == null) {
            dispatch.setStatus(EmailDeliveryStatus.SMTP_ACCEPTED);
            dispatch.setRetryable(false);
            dispatch.setNextAttemptAt(null);
            dispatch.setFailureCode(null);
        } else {
            dispatch.setStatus(EmailDeliveryStatus.FAILED);
            dispatch.setRetryable(failure.retryable() && dispatch.getAttempts() < MAX_ATTEMPTS);
            dispatch.setNextAttemptAt(dispatch.isRetryable() ? LocalDateTime.ofInstant(retryAt, ZoneOffset.UTC) : null);
            dispatch.setFailureCode(failure.failureCode());
        }
        dispatch.setLockedUntil(null);
        return failure == null ? EmailDispatchResult.accepted()
                : dispatch.isRetryable() ? new EmailDispatchResult(EmailDeliveryStatus.FAILED, retryAt, failure.failureCode())
                : EmailDispatchResult.failed(failure.failureCode());
    }

    private EmailDispatchResult result(EmailDispatch dispatch) {
        if (dispatch.getStatus() == EmailDeliveryStatus.SMTP_ACCEPTED) return EmailDispatchResult.accepted();
        if (dispatch.getStatus() == EmailDeliveryStatus.SENDING) {
            Instant retryAt = dispatch.getLockedUntil().toInstant(ZoneOffset.UTC);
            // 过期执行权由 claim 持久化为失败；不自动重新提交这封邮件。
            return retryAt.isAfter(Instant.now()) ? EmailDispatchResult.deferred(retryAt) : null;
        }
        if (!dispatch.isRetryable()) return EmailDispatchResult.failed(dispatch.getFailureCode());
        if (dispatch.getNextAttemptAt() != null && dispatch.getNextAttemptAt().isAfter(UtcTime.now())) {
            return new EmailDispatchResult(EmailDeliveryStatus.FAILED, dispatch.getNextAttemptAt().toInstant(ZoneOffset.UTC), dispatch.getFailureCode());
        }
        return null;
    }

    public record Claim(UUID id, UUID token, EmailDispatchResult previous) {
    }
}
