package com.pro.ereportbacken.notification.delivery;

import com.pro.ereportbacken.model.pojo.EmailDelivery;
import com.pro.ereportbacken.repository.EmailDeliveryRepository;
import com.pro.ereportbacken.repository.TenantRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestConstructor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class EmailDeliverySmtpIntegrationTests {
    private final EmailDeliveryTracker tracker;
    private final EmailDeliveryRepository repository;
    private final TenantRepository tenants;
    private final PlatformTransactionManager transactions;
    private final List<UUID> records = new ArrayList<>();

    EmailDeliverySmtpIntegrationTests(EmailDeliveryTracker tracker, EmailDeliveryRepository repository,
                                      TenantRepository tenants, PlatformTransactionManager transactions) {
        this.tracker = tracker; this.repository = repository; this.tenants = tenants; this.transactions = transactions;
    }

    @AfterEach
    void cleanup() { repository.deleteAllById(records); }

    @Test
    void realSmtpAcceptanceIsDurableAndExternalIoSuspendsAnEnclosingTransaction() throws Exception {
        try (LocalSmtp smtp = new LocalSmtp(true)) {
            var context = context();
            var sender = sender(smtp.port());
            var message = sender.createMimeMessage();
            var helper = new MimeMessageHelper(message);
            helper.setFrom("sender@example.test"); helper.setTo(context.recipientAddress()); helper.setSubject("邮件台账 SMTP 验收");
            helper.setText("Local protocol acceptance test.");
            new TransactionTemplate(transactions).executeWithoutResult(status -> {
                try {
                    tracker.send(context, "sender@example.test", () -> {
                        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
                        sender.send(message);
                    });
                } catch (Exception failure) { throw new IllegalStateException(failure); }
            });
            smtp.await();
            EmailDelivery delivery = record(context.relatedId());
            assertThat(delivery.getStatus()).isEqualTo(EmailDeliveryStatus.SMTP_ACCEPTED);
            assertThat(delivery.getRecipientAddress()).isEqualTo("re***@example.test");
            assertThat(delivery.getSenderAddress()).isEqualTo("se***@example.test");
            assertThat(delivery.getCompletedAt()).isNotNull();
            assertThat(delivery.getDurationMs()).isNotNegative();
            assertThat(delivery.getFailureCode()).isNull();
        }
    }

    @Test
    void smtpRecipientRejectionIsRecordedWithoutRetainingProviderResponseOrAddress() throws Exception {
        try (LocalSmtp smtp = new LocalSmtp(false)) {
            var context = context();
            var sender = sender(smtp.port());
            var message = sender.createMimeMessage();
            var helper = new MimeMessageHelper(message);
            helper.setFrom("sender@example.test"); helper.setTo(context.recipientAddress()); helper.setSubject("拒收验收"); helper.setText("Rejected message.");
            assertThatThrownBy(() -> tracker.send(context, "sender@example.test", () -> sender.send(message))).isInstanceOf(MailException.class);
            smtp.await();
            EmailDelivery delivery = record(context.relatedId());
            assertThat(delivery.getStatus()).isEqualTo(EmailDeliveryStatus.FAILED);
            assertThat(delivery.getFailureCode()).isEqualTo("DELIVERY");
            assertThat(delivery.getRecipientAddress()).doesNotContain("recipient@");
        }
    }

    private EmailDeliveryContext context() {
        return new EmailDeliveryContext(tenants.findAll().getFirst().getId(), null, EmailDeliveryCategory.CONFIGURATION_TEST,
                UUID.randomUUID(), null, "recipient@example.test", "SMTP integration");
    }

    @Test
    void smtpAcknowledgmentTimeoutRecordsAFailedAttemptWithItsTimeoutReason() throws Exception {
        try (LocalSmtp smtp = new LocalSmtp(true, false)) {
            var context = context();
            var sender = sender(smtp.port());
            var message = sender.createMimeMessage();
            var helper = new MimeMessageHelper(message);
            helper.setFrom("sender@example.test"); helper.setTo(context.recipientAddress());
            helper.setSubject("超时验收"); helper.setText("Timeout acceptance test.");
            assertThatThrownBy(() -> tracker.send(context, "sender@example.test", () -> sender.send(message)))
                    .isInstanceOf(MailException.class);
            smtp.await();
            EmailDelivery delivery = record(context.relatedId());
            assertThat(delivery.getStatus()).isEqualTo(EmailDeliveryStatus.FAILED);
            assertThat(delivery.getFailureCode()).isEqualTo("TIMEOUT");
            assertThat(delivery.getCompletedAt()).isNotNull();
        }
    }

    private EmailDelivery record(UUID relatedId) {
        EmailDelivery delivery = repository.findOne((root, query, cb) -> cb.equal(root.get("relatedId"), relatedId)).orElseThrow();
        records.add(delivery.getId()); return delivery;
    }

    private JavaMailSenderImpl sender(int port) {
        var sender = new JavaMailSenderImpl(); sender.setHost("127.0.0.1"); sender.setPort(port);
        sender.getJavaMailProperties().setProperty("mail.host", "localhost");
        sender.getJavaMailProperties().setProperty("mail.smtp.localhost", "localhost");
        sender.getJavaMailProperties().setProperty("mail.smtp.connectiontimeout", "3000");
        sender.getJavaMailProperties().setProperty("mail.smtp.timeout", "3000");
        return sender;
    }

    private static final class LocalSmtp implements AutoCloseable {
        private final ServerSocket listener;
        private final CompletableFuture<Void> conversation;

        LocalSmtp(boolean acceptsRecipient) throws Exception {
            this(acceptsRecipient, true);
        }

        LocalSmtp(boolean acceptsRecipient, boolean acknowledgesMessage) throws Exception {
            listener = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
            listener.setSoTimeout(5000);
            conversation = CompletableFuture.runAsync(() -> {
                List<String> commands = new ArrayList<>();
                try (var socket = listener.accept();
                     var input = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                     var output = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8)) {
                    socket.setSoTimeout(5000);
                    output.print("220 localhost test SMTP\r\n"); output.flush();
                    for (String line; (line = input.readLine()) != null;) {
                        commands.add(line.split(" ", 2)[0]);
                        if (line.startsWith("DATA")) {
                            output.print("354 End with a dot\r\n"); output.flush();
                            while ((line = input.readLine()) != null && !line.equals(".")) { }
                            if (!acknowledgesMessage) { input.readLine(); break; }
                            output.print("250 accepted\r\n");
                        } else if (line.startsWith("QUIT")) {
                            output.print("221 goodbye\r\n"); output.flush(); break;
                        } else if (line.startsWith("RCPT") && !acceptsRecipient) {
                            output.print("550 recipient rejected\r\n"); output.flush(); break;
                        } else { output.print("250 OK\r\n"); }
                        output.flush();
                    }
                } catch (Exception failure) { throw new IllegalStateException("SMTP commands=" + commands, failure); }
            });
        }

        int port() { return listener.getLocalPort(); }
        void await() throws Exception { conversation.get(6, TimeUnit.SECONDS); }
        @Override public void close() throws Exception { listener.close(); }
    }
}
