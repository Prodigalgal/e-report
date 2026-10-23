package com.pro.ereportbacken.notification.delivery;

import com.pro.ereportbacken.model.pojo.EmailDispatch;
import com.pro.ereportbacken.notification.ConfiguredMailSenderFactory;
import com.pro.ereportbacken.notification.MailSenderConfigurationService;
import com.pro.ereportbacken.notification.MailSenderConfigurationService.MailSenderAccountConfiguration;
import com.pro.ereportbacken.notification.MailSenderOrigin;
import com.pro.ereportbacken.notification.MailSenderPoolStrategy;
import com.pro.ereportbacken.notification.MailSenderRotationService;
import com.pro.ereportbacken.repository.EmailDeliveryRepository;
import com.pro.ereportbacken.repository.EmailDispatchRepository;
import com.pro.ereportbacken.repository.TenantRepository;
import com.pro.ereportbacken.time.UtcTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.net.ConnectException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {"app.outbox.enabled=false", "spring.datasource.url=jdbc:h2:mem:email_dispatch;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1"})
@ActiveProfiles("test")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class EmailDispatchIntegrationTests {
    @MockitoBean private MailAccountGate gate;
    @MockitoBean private MailSenderConfigurationService configuration;
    @MockitoBean private MailSenderRotationService rotation;
    private final EmailDispatchService service;
    private final EmailDispatchStore store;
    private final EmailDispatchRepository dispatches;
    private final EmailDeliveryRepository deliveries;
    private final TenantRepository tenants;
    private final ConfiguredMailSenderFactory factory;
    private final EmailDeliveryTracker tracker;
    private final String notification = UUID.randomUUID().toString();
    private final List<String> keys = new ArrayList<>();

    EmailDispatchIntegrationTests(EmailDispatchService service, EmailDispatchStore store,
                                  EmailDispatchRepository dispatches, EmailDeliveryRepository deliveries,
                                  TenantRepository tenants, ConfiguredMailSenderFactory factory,
                                  EmailDeliveryTracker tracker) {
        this.service = service; this.store = store; this.dispatches = dispatches;
        this.deliveries = deliveries; this.tenants = tenants; this.factory = factory; this.tracker = tracker;
    }

    @BeforeEach
    void prepare() {
        when(gate.acquire(any())).thenAnswer(invocation -> new MailAccountGate.Reservation(
                new MailAccountGate.Lease("test-account", UUID.randomUUID(), Duration.ofSeconds(30), 0), null));
        when(gate.release(any(), any())).thenAnswer(invocation -> Instant.now());
        when(rotation.nextIndex(anyInt())).thenReturn(0);
        configure(2525);
    }

    @AfterEach
    void cleanup() {
        List<UUID> ids = keys.stream().distinct().map(dispatches::findByDispatchKey)
                .flatMap(java.util.Optional::stream).map(EmailDispatch::getId).toList();
        if (ids.isEmpty()) return;
        deliveries.deleteAll(deliveries.findAll((root, query, cb) -> root.get("dispatchId").in(ids)));
        dispatches.deleteAllById(ids);
    }

    @Test
    void actualSmtpAcceptanceSurvivesAnotherServiceInstanceWithoutASecondSubmission() throws Exception {
        var context = context("first@example.test");
        AtomicInteger submissions = new AtomicInteger();
        try (var smtp = new EmailDeliverySmtpIntegrationTests.LocalSmtp(true)) {
            configure(smtp.port());
            assertThat(service.send(service.prepareBatch(), notification, context,
                    sender -> send(sender, context, submissions)).status()).isEqualTo(EmailDeliveryStatus.SMTP_ACCEPTED);
            smtp.await();
            var anotherInstance = new EmailDispatchService(configuration, rotation, factory, gate, store, tracker);
            assertThat(anotherInstance.send(anotherInstance.prepareBatch(), notification, context,
                    sender -> send(sender, context, submissions)).status()).isEqualTo(EmailDeliveryStatus.SMTP_ACCEPTED);
        }
        assertThat(submissions).hasValue(1);
        assertThat(receipt(context).getAttempts()).isEqualTo(1);
        assertThat(attempts(context)).hasSize(1);
    }

    @Test
    void partiallySuccessfulRecipientsRetryOnlyTheConnectionFailureAndRetainBothAttempts() throws Exception {
        var accepted = context("accepted@example.test");
        var failed = context("failed@example.test");
        AtomicInteger acceptedCalls = new AtomicInteger();
        AtomicInteger failedCalls = new AtomicInteger();
        var batch = service.prepareBatch();
        assertThat(service.send(batch, notification, accepted, sender -> acceptedCalls.incrementAndGet()).status())
                .isEqualTo(EmailDeliveryStatus.SMTP_ACCEPTED);
        var failure = service.send(batch, notification, failed, sender -> {
            failedCalls.incrementAndGet();
            throw new MailSendException("connect failed", new ConnectException("connection refused"));
        });
        assertThat(failure.retryAt()).isNotNull();
        assertThat(failure.failureCode()).isEqualTo("CONNECTION");
        assertThat(service.send(batch, notification, accepted, sender -> acceptedCalls.incrementAndGet()).status())
                .isEqualTo(EmailDeliveryStatus.SMTP_ACCEPTED);
        assertThat(service.send(batch, notification, failed, sender -> failedCalls.incrementAndGet()).status())
                .isEqualTo(EmailDeliveryStatus.SMTP_ACCEPTED);
        assertThat(acceptedCalls).hasValue(1);
        assertThat(failedCalls).hasValue(2);
        assertThat(attempts(failed)).extracting(record -> record.getStatus())
                .containsExactlyInAnyOrder(EmailDeliveryStatus.FAILED, EmailDeliveryStatus.SMTP_ACCEPTED);
    }

    @Test
    void acknowledgmentTimeoutAfterDataDoesNotResendTheUnconfirmedMessage() throws Exception {
        var context = context("timeout@example.test");
        AtomicInteger submissions = new AtomicInteger();
        try (var smtp = new EmailDeliverySmtpIntegrationTests.LocalSmtp(true, false)) {
            configure(smtp.port());
            var result = service.send(service.prepareBatch(), notification, context,
                    sender -> send(sender, context, submissions));
            smtp.await();
            assertThat(smtp.receivedMessages()).isEqualTo(1);
            assertThat(result.status()).isEqualTo(EmailDeliveryStatus.FAILED);
            assertThat(result.failureCode()).isEqualTo("TIMEOUT");
            assertThat(result.retryAt()).isNull();
        }
        assertThat(service.send(service.prepareBatch(), notification, context,
                sender -> submissions.incrementAndGet()).status()).isEqualTo(EmailDeliveryStatus.FAILED);
        assertThat(submissions).hasValue(1);
        assertThat(receipt(context).isRetryable()).isFalse();
    }

    @Test
    void expiredInFlightCheckpointStopsAutomaticReplayAfterAnInterruptedProcess() {
        var context = context("interrupted@example.test");
        store.claim(MailDeliveryKeys.dispatch(notification, context), context.tenantId(), Duration.ofSeconds(-1));
        AtomicInteger submissions = new AtomicInteger();
        var result = service.send(service.prepareBatch(), notification, context, sender -> submissions.incrementAndGet());
        assertThat(result.status()).isEqualTo(EmailDeliveryStatus.FAILED);
        assertThat(result.failureCode()).isEqualTo("INCOMPLETE");
        assertThat(submissions).hasValue(0);
        assertThat(attempts(context)).isEmpty();
    }

    @Test
    void renewalExtendsOnlyTheOwnedActiveCheckpointAndNeverReopensACompletedDelivery() {
        var context = context("renewal@example.test");
        String key = MailDeliveryKeys.dispatch(notification, context);
        var claim = store.claim(key, context.tenantId(), Duration.ofSeconds(1));
        store.renew(key, UUID.randomUUID(), Duration.ofHours(1));
        assertThat(receipt(context).getLockedUntil()).isBefore(UtcTime.now().plusSeconds(10));
        store.renew(key, claim.token(), Duration.ofMinutes(3));
        assertThat(receipt(context).getLockedUntil()).isAfter(UtcTime.now().plusMinutes(2));
        store.complete(key, claim.token(), null, Instant.now());
        store.renew(key, claim.token(), Duration.ofMinutes(3));
        assertThat(receipt(context).getLockedUntil()).isNull();
        assertThat(receipt(context).getStatus()).isEqualTo(EmailDeliveryStatus.SMTP_ACCEPTED);
    }

    @Test
    void racingInstancesCanOnlyClaimOnePhysicalSubmission() throws Exception {
        var context = context("racing@example.test");
        CountDownLatch bothSelected = new CountDownLatch(2);
        when(gate.acquire(any())).thenAnswer(invocation -> {
            bothSelected.countDown();
            assertThat(bothSelected.await(5, TimeUnit.SECONDS)).isTrue();
            return new MailAccountGate.Reservation(new MailAccountGate.Lease(
                    "test-account", UUID.randomUUID(), Duration.ofSeconds(30), 0), null);
        });
        AtomicInteger submissions = new AtomicInteger();
        var batch = service.prepareBatch();
        var first = CompletableFuture.supplyAsync(() -> service.send(batch, notification, context,
                sender -> submissions.incrementAndGet()));
        var second = CompletableFuture.supplyAsync(() -> service.send(batch, notification, context,
                sender -> submissions.incrementAndGet()));
        assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                .anyMatch(result -> result.status() == EmailDeliveryStatus.SMTP_ACCEPTED);
        assertThat(submissions).hasValue(1);
        assertThat(receipt(context).getStatus()).isEqualTo(EmailDeliveryStatus.SMTP_ACCEPTED);
        assertThat(attempts(context)).hasSize(1);
    }

    @Test
    void coolingAccountDoesNotConsumeARecipientAttemptOrCreateAFailedLedgerEntry() {
        var context = context("cooling@example.test");
        when(gate.acquire(any())).thenReturn(new MailAccountGate.Reservation(null, Instant.now().plusSeconds(10)));
        AtomicInteger submissions = new AtomicInteger();
        assertThat(service.send(service.prepareBatch(), notification, context,
                sender -> submissions.incrementAndGet()).retryAt()).isNotNull();
        assertThat(submissions).hasValue(0);
        assertThat(dispatches.findByDispatchKey(MailDeliveryKeys.dispatch(notification, context))).isEmpty();
    }

    @Test
    void repeatedConnectionFailuresStopAfterTenActualSubmissions() {
        var context = context("exhausted@example.test");
        AtomicInteger submissions = new AtomicInteger();
        var batch = service.prepareBatch();
        for (int attempt = 0; attempt < 11; attempt++) {
            service.send(batch, notification, context, sender -> {
                submissions.incrementAndGet();
                throw new MailSendException("connect failed", new ConnectException("connection refused"));
            });
        }
        assertThat(submissions).hasValue(10);
        assertThat(receipt(context).getAttempts()).isEqualTo(10);
        assertThat(receipt(context).isRetryable()).isFalse();
        assertThat(attempts(context)).hasSize(10);
    }

    private void configure(int port) {
        var sender = new MailSenderAccountConfiguration(UUID.randomUUID(), "CUSTOM", "127.0.0.1", port,
                null, null, "sender@example.test", false, false, false, false,
                "PASSWORD", "AUTO", "localhost", null, null, "TLSv1.2 TLSv1.3", 3000, 3000, 10000,
                null, null, null, null, null, 0, new MailSenderOrigin(null, null, true));
        when(configuration.requireCurrent()).thenReturn(new MailSenderConfigurationService.MailSenderConfiguration(
                List.of(sender), true, false, "RUNTIME_SECRET", null, null, false, null, true,
                MailSenderPoolStrategy.INHERIT, List.of(), List.of(sender), null));
    }

    private EmailDeliveryContext context(String address) {
        var context = new EmailDeliveryContext(tenants.findAll().getFirst().getId(), null,
                EmailDeliveryCategory.CREATED, UUID.randomUUID(), UUID.randomUUID(), address, "发送协调验证", null);
        keys.add(MailDeliveryKeys.dispatch(notification, context));
        return context;
    }

    private EmailDispatch receipt(EmailDeliveryContext context) {
        return dispatches.findByDispatchKey(MailDeliveryKeys.dispatch(notification, context)).orElseThrow();
    }

    private List<com.pro.ereportbacken.model.pojo.EmailDelivery> attempts(EmailDeliveryContext context) {
        UUID id = receipt(context).getId();
        return deliveries.findAll((root, query, cb) -> cb.equal(root.get("dispatchId"), id));
    }

    private void send(ConfiguredMailSenderFactory.MailSenderContext sender, EmailDeliveryContext context,
                      AtomicInteger submissions) throws jakarta.mail.MessagingException {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        submissions.incrementAndGet();
        var message = sender.mailSender().createMimeMessage();
        ((org.springframework.mail.javamail.JavaMailSenderImpl) sender.mailSender()).getJavaMailProperties()
                .setProperty("mail.host", "localhost");
        var helper = new MimeMessageHelper(message);
        helper.setFrom(sender.fromAddress()); helper.setTo(context.recipientAddress());
        helper.setSubject("邮件发送协调验证"); helper.setText("Local SMTP acceptance test.");
        sender.mailSender().send(message);
    }
}
