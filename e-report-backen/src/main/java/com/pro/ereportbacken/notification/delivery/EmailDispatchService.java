package com.pro.ereportbacken.notification.delivery;

import com.pro.ereportbacken.notification.ConfiguredMailSenderFactory;
import com.pro.ereportbacken.notification.ConfiguredMailSenderFactory.MailSenderContext;
import com.pro.ereportbacken.notification.MailSenderConfigurationService;
import com.pro.ereportbacken.notification.MailSenderConfigurationService.MailSenderAccountConfiguration;
import com.pro.ereportbacken.notification.MailSenderRotationService;
import jakarta.mail.MessagingException;
import lombok.extern.log4j.Log4j2;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@Log4j2
public class EmailDispatchService {
    private final MailSenderConfigurationService configuration;
    private final MailSenderRotationService rotation;
    private final ConfiguredMailSenderFactory factory;
    private final MailAccountGate gate;
    private final EmailDispatchStore store;
    private final EmailDeliveryTracker tracker;

    public EmailDispatchService(MailSenderConfigurationService configuration, MailSenderRotationService rotation,
                                ConfiguredMailSenderFactory factory, MailAccountGate gate,
                                EmailDispatchStore store, EmailDeliveryTracker tracker) {
        this.configuration = configuration;
        this.rotation = rotation;
        this.factory = factory;
        this.gate = gate;
        this.store = store;
        this.tracker = tracker;
    }

    public Batch prepareBatch() {
        return new Batch(configuration.requireCurrent().senders());
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public EmailDispatchResult send(Batch batch, String notificationKey, EmailDeliveryContext context, MailContent content) {
        String key = MailDeliveryKeys.dispatch(notificationKey, context);
        var previous = store.inspect(key);
        if (previous.isPresent()) return previous.get();
        List<MailSenderAccountConfiguration> senders = batch.senders;
        int start = rotation.nextIndex(senders.size());
        List<MailSenderAccountConfiguration> ordered = new ArrayList<>(senders.size());
        for (int offset = 0; offset < senders.size(); offset++) ordered.add(senders.get((start + offset) % senders.size()));
        return sendUsing(key, context, ordered, content, true);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public EmailDispatchResult test(EmailDeliveryContext context, MailSenderAccountConfiguration sender,
                                     MailContent content, boolean inherited) {
        return sendUsing(MailDeliveryKeys.dispatch(UUID.randomUUID().toString(), context),
                context, List.of(sender), content, inherited);
    }

    private EmailDispatchResult sendUsing(String key, EmailDeliveryContext context,
                                           List<MailSenderAccountConfiguration> senders,
                                           MailContent content, boolean configured) {
        Instant earliest = null;
        for (MailSenderAccountConfiguration sender : senders) {
            MailAccountGate.Reservation reservation = gate.acquire(sender);
            if (reservation.lease() == null) {
                if (earliest == null || reservation.retryAt().isBefore(earliest)) earliest = reservation.retryAt();
                continue;
            }
            return sendClaimed(key, context, sender, reservation.lease(), content, configured);
        }
        return EmailDispatchResult.deferred(earliest == null ? Instant.now().plusSeconds(30) : earliest);
    }

    private EmailDispatchResult sendClaimed(String key, EmailDeliveryContext context,
                                             MailSenderAccountConfiguration sender, MailAccountGate.Lease lease,
                                             MailContent content, boolean configured) {
        EmailDispatchStore.Claim claim;
        try {
            claim = store.claim(key, context.tenantId(), lease.duration());
        } catch (DataIntegrityViolationException conflict) {
            gate.release(lease, MailFailurePolicy.AccountEffect.NONE);
            return store.inspect(key).orElseThrow(() -> conflict);
        } catch (RuntimeException failure) {
            gate.release(lease, MailFailurePolicy.AccountEffect.NONE);
            throw failure;
        }
        if (claim.previous() != null) {
            gate.release(lease, MailFailurePolicy.AccountEffect.NONE);
            return claim.previous();
        }
        MailFailurePolicy failurePolicy = null;
        AtomicBoolean operationStarted = new AtomicBoolean();
        AtomicBoolean transportStarted = new AtomicBoolean();
        long started = System.nanoTime();
        try (var renewal = gate.maintain(lease, () -> store.renew(key, claim.token(), lease.duration()))) {
            tracker.send(context.withDispatchId(claim.id()), sender.fromAddress(), () -> {
                operationStarted.set(true);
                MailSenderContext mailContext = configured ? factory.createConfigured(sender) : factory.create(sender);
                transportStarted.set(true);
                content.send(mailContext);
            });
        } catch (MessagingException | RuntimeException failure) {
            failurePolicy = !operationStarted.get()
                    ? new MailFailurePolicy(true, MailFailurePolicy.AccountEffect.NONE, "STORAGE")
                    : !transportStarted.get()
                        ? new MailFailurePolicy(true, MailFailurePolicy.AccountEffect.AUTHENTICATION_FAILURE, "CONFIGURATION")
                        : MailFailurePolicy.classify(failure);
            log.warn("邮件发送尝试失败。dispatchId={}, senderKey={}, provider={}, code={}, retryable={}, exceptionType={}",
                    claim.id(), MailDeliveryKeys.account(sender), sender.providerId(),
                    failurePolicy.failureCode(), failurePolicy.retryable(), failure.getClass().getSimpleName());
        }
        Instant retryAt = gate.release(lease, failurePolicy == null
                ? MailFailurePolicy.AccountEffect.SUCCESS : failurePolicy.accountEffect());
        EmailDispatchResult result = store.complete(key, claim.token(), failurePolicy, retryAt);
        log.info("邮件发送尝试结束。dispatchId={}, senderKey={}, provider={}, status={}, elapsedMs={}",
                claim.id(), MailDeliveryKeys.account(sender), sender.providerId(), result.status(),
                (System.nanoTime() - started) / 1_000_000L);
        return result;
    }

    @FunctionalInterface
    public interface MailContent {
        void send(MailSenderContext context) throws MessagingException;
    }

    public static final class Batch {
        private final List<MailSenderAccountConfiguration> senders;

        private Batch(List<MailSenderAccountConfiguration> senders) {
            this.senders = List.copyOf(senders);
        }
    }
}
