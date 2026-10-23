package com.pro.ereportbacken.notification.delivery;

import jakarta.mail.MessagingException;
import org.eclipse.angus.mail.smtp.SMTPAddressFailedException;
import org.eclipse.angus.mail.smtp.SMTPSendFailedException;
import org.eclipse.angus.mail.util.MailConnectException;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;

import java.net.SocketTimeoutException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class MailFailurePolicyTests {
    @Test
    void connectionTimeoutBeforeSubmissionCanRetryAndCoolsTheAccount() {
        var connect = mock(MailConnectException.class);
        when(connect.getCause()).thenReturn(new SocketTimeoutException("connect timeout"));
        var decision = MailFailurePolicy.classify(new MailSendException("connection failed", connect));
        assertThat(decision.retryable()).isTrue();
        assertThat(decision.accountEffect()).isEqualTo(MailFailurePolicy.AccountEffect.TRANSIENT_FAILURE);
        assertThat(decision.failureCode()).isEqualTo("CONNECTION");
    }

    @Test
    void missingAcknowledgmentAfterSubmissionDoesNotAutomaticallyResend() {
        var failure = new MailSendException("send failed", new MessagingException("read timeout",
                new SocketTimeoutException("smtp acknowledgment timeout")));
        var decision = MailFailurePolicy.classify(failure);
        assertThat(decision.retryable()).isFalse();
        assertThat(decision.failureCode()).isEqualTo("TIMEOUT");
    }

    @Test
    void interruptedSocketWithoutASmtpResponseIsNotMistakenForAConfirmedRejection() {
        var failure = new SMTPSendFailedException("DATA", -1, "no acknowledgment", new java.net.SocketException("closed"), null, null, null);
        var decision = MailFailurePolicy.classify(failure);
        assertThat(decision.retryable()).isFalse();
        assertThat(decision.failureCode()).isEqualTo("INCOMPLETE");
    }

    @Test
    void temporarySmtpRejectionRetriesWithAccountBackoff() {
        var rejection = mock(SMTPSendFailedException.class);
        when(rejection.getReturnCode()).thenReturn(450);
        var decision = MailFailurePolicy.classify(new MailSendException("send failed", null, Map.of("mail", rejection)));
        assertThat(decision.retryable()).isTrue();
        assertThat(decision.accountEffect()).isEqualTo(MailFailurePolicy.AccountEffect.TRANSIENT_FAILURE);
    }

    @Test
    void invalidRecipientDoesNotBlockTheWholeAccount() {
        var rejection = mock(SMTPAddressFailedException.class);
        when(rejection.getReturnCode()).thenReturn(550);
        var decision = MailFailurePolicy.classify(rejection);
        assertThat(decision.retryable()).isFalse();
        assertThat(decision.accountEffect()).isEqualTo(MailFailurePolicy.AccountEffect.NONE);
        assertThat(decision.failureCode()).isEqualTo("RECIPIENT_REJECTED");
    }

    @Test
    void contentRejectionIsTerminalWithoutDisablingAnOtherwiseHealthyAccount() {
        var rejection = mock(SMTPSendFailedException.class);
        when(rejection.getReturnCode()).thenReturn(550);
        var decision = MailFailurePolicy.classify(rejection);
        assertThat(decision.retryable()).isFalse();
        assertThat(decision.accountEffect()).isEqualTo(MailFailurePolicy.AccountEffect.NONE);
    }

    @Test
    void authenticationErrorsAllowOtherAccountsAndUseLongAccountBackoff() {
        var decision = MailFailurePolicy.classify(new MailAuthenticationException("authentication failed"));
        assertThat(decision.retryable()).isTrue();
        assertThat(decision.accountEffect()).isEqualTo(MailFailurePolicy.AccountEffect.AUTHENTICATION_FAILURE);
    }
}
