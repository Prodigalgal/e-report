package com.pro.ereportbacken.notification;

import com.pro.ereportbacken.notification.MailSenderConfigurationService.MailSenderAccountConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class MailSenderOAuthTokenServiceTests {

    @Test
    void exchangesGoogleRefreshTokenAtFixedEndpointAndCachesAccessToken() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("https://oauth2.googleapis.com/token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("client_secret=client-secret")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("refresh_token=refresh-token")))
                .andRespond(withSuccess("{\"access_token\":\"short-lived-access\",\"expires_in\":3600,"
                                + "\"refresh_token\":\"rotated-refresh-token\"}",
                        MediaType.APPLICATION_JSON));
        MailSenderOAuthTokenService service = new MailSenderOAuthTokenService(restTemplate);

        var first = service.accessToken(sender("GMAIL", null));
        var second = service.accessToken(sender("GMAIL", null));
        assertThat(first.value()).isEqualTo("short-lived-access");
        assertThat(first.priorRefreshToken()).isEqualTo("refresh-token");
        assertThat(first.rotatedRefreshToken()).isEqualTo("rotated-refresh-token");
        assertThat(second.value()).isEqualTo("short-lived-access");
        server.verify();
    }

    @Test
    void microsoftTenantCannotChangeTheOfficialTokenHost() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        MailSenderOAuthTokenService service = new MailSenderOAuthTokenService(restTemplate);

        assertThatThrownBy(() -> service.accessToken(sender("MICROSOFT_365", "tenant/../@evil.example")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("租户标识格式不正确");
        server.verify();
    }

    @Test
    void exchangesMicrosoftRefreshTokenAtTenantScopedOfficialEndpoint() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("https://login.microsoftonline.com/tenant-123/oauth2/v2.0/token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "scope=https%3A%2F%2Foutlook.office.com%2FSMTP.Send")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("offline_access")))
                .andRespond(withSuccess("{\"access_token\":\"microsoft-access\",\"expires_in\":3600}",
                        MediaType.APPLICATION_JSON));
        MailSenderOAuthTokenService service = new MailSenderOAuthTokenService(restTemplate);
        MailSenderAccountConfiguration account = sender("MICROSOFT_365", "tenant-123");

        assertThat(service.accessToken(account).value()).isEqualTo("microsoft-access");
        server.verify();
    }

    @Test
    void tokenErrorsDoNotExposeProviderResponseOrCredentials() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("https://oauth2.googleapis.com/token"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error_description\":\"client-secret-should-not-escape\"}"));
        MailSenderOAuthTokenService service = new MailSenderOAuthTokenService(restTemplate);

        assertThatThrownBy(() -> service.accessToken(sender("GMAIL", null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("无法从邮箱服务商获取OAuth2令牌")
                .hasMessageNotContaining("client-secret-should-not-escape")
                .hasMessageNotContaining("client-secret");
        server.verify();
    }

    private MailSenderAccountConfiguration sender(String provider, String tenantId) {
        return new MailSenderAccountConfiguration(
                UUID.randomUUID(), provider,
                "MICROSOFT_365".equals(provider) ? "smtp.office365.com" : "smtp.gmail.com",
                587, "sender@example.com", null, "sender@example.com", true, false,
                true, true, "OAUTH2", "XOAUTH2", null, null, null,
                "TLSv1.2 TLSv1.3", 10000, 30000, 30000,
                "client-id", "client-secret", "refresh-token", tenantId, null, new MailSenderOrigin(null, null, false));
    }
}
