package com.pro.ereportbacken.notification;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.pro.ereportbacken.notification.MailSenderConfigurationService.MailSenderAccountConfiguration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MailSenderOAuthTokenService {

    private static final int MAX_CACHE_ENTRIES = 256;
    private static final long EXPIRY_BUFFER_SECONDS = 60;
    private static final String GOOGLE_TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token";

    private final RestTemplate restTemplate;
    private final ConcurrentHashMap<String, CachedAccessToken> tokens = new ConcurrentHashMap<>();

    public MailSenderOAuthTokenService(@Qualifier("smtpOAuthRestTemplate") RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public OAuthAccessToken accessToken(MailSenderAccountConfiguration sender) {
        if (!StringUtils.hasText(sender.oauthClientId())
                || !StringUtils.hasText(sender.oauthClientSecret())
                || !StringUtils.hasText(sender.oauthRefreshToken())) {
            throw new IllegalStateException("OAuth2发件配置不完整，请检查客户端和刷新令牌。");
        }

        String cacheKey = cacheKey(sender);
        CachedAccessToken cached = tokens.get(cacheKey);
        if (cached != null && cached.expiresAt().isAfter(Instant.now().plusSeconds(EXPIRY_BUFFER_SECONDS))) {
            return new OAuthAccessToken(cached.value(), cached.priorRefreshToken(), cached.rotatedRefreshToken());
        }

        CachedAccessToken refreshed = refresh(sender);
        tokens.put(cacheKey, refreshed);
        evictExpiredAndExcessEntries();
        return new OAuthAccessToken(refreshed.value(), refreshed.priorRefreshToken(), refreshed.rotatedRefreshToken());
    }

    private CachedAccessToken refresh(MailSenderAccountConfiguration sender) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "refresh_token");
        form.add("client_id", sender.oauthClientId());
        form.add("client_secret", sender.oauthClientSecret());
        form.add("refresh_token", sender.oauthRefreshToken());
        MailSenderProvider provider = MailSenderProvider.valueOf(sender.providerId());
        form.add("scope", provider.resolveOAuthScope(sender.oauthScope()));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        try {
            OAuthTokenResponse response = restTemplate.postForObject(
                    tokenEndpoint(sender), new HttpEntity<>(form, headers), OAuthTokenResponse.class);
            if (response == null || !StringUtils.hasText(response.accessToken()) || response.expiresIn() < 1) {
                throw new IllegalStateException("OAuth2令牌服务返回了无效响应。");
            }
            long usableSeconds = Math.max(1, response.expiresIn() - EXPIRY_BUFFER_SECONDS);
            String rotatedRefreshToken = StringUtils.hasText(response.refreshToken())
                    && !response.refreshToken().equals(sender.oauthRefreshToken())
                    ? response.refreshToken()
                    : null;
            return new CachedAccessToken(response.accessToken(), Instant.now().plusSeconds(usableSeconds),
                    sender.oauthRefreshToken(), rotatedRefreshToken);
        } catch (RestClientException exception) {
            throw new IllegalStateException("无法从邮箱服务商获取OAuth2令牌，请检查OAuth配置和网络连通性。");
        }
    }

    private String tokenEndpoint(MailSenderAccountConfiguration sender) {
        return switch (MailSenderProvider.valueOf(sender.providerId())) {
            case GMAIL -> GOOGLE_TOKEN_ENDPOINT;
            case MICROSOFT_365 -> "https://login.microsoftonline.com/"
                    + microsoftTenant(sender.oauthTenantId()) + "/oauth2/v2.0/token";
            default -> throw new IllegalArgumentException("当前邮箱服务商不支持OAuth2认证。");
        };
    }

    private String microsoftTenant(String tenantId) {
        String value = StringUtils.hasText(tenantId) ? tenantId : "common";
        if (!value.matches("[A-Za-z0-9.-]{1,128}")) {
            throw new IllegalArgumentException("Microsoft OAuth租户标识格式不正确。");
        }
        return value;
    }

    private String cacheKey(MailSenderAccountConfiguration sender) {
        String material = String.join("\u0000",
                sender.providerId(), sender.oauthTenantId() == null ? "" : sender.oauthTenantId(),
                sender.oauthClientId(), sender.oauthClientSecret(), sender.oauthRefreshToken(),
                sender.oauthScope() == null ? "" : sender.oauthScope());
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(material.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前运行环境不支持安全的令牌缓存标识。", exception);
        }
    }

    private void evictExpiredAndExcessEntries() {
        Instant now = Instant.now();
        tokens.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
        while (tokens.size() > MAX_CACHE_ENTRIES) {
            String key = tokens.keySet().stream().findFirst().orElse(null);
            if (key == null) {
                return;
            }
            tokens.remove(key);
        }
    }

    private record OAuthTokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("expires_in") long expiresIn,
            @JsonProperty("refresh_token") String refreshToken
    ) {
    }

    public record OAuthAccessToken(String value, String priorRefreshToken, String rotatedRefreshToken) {
    }

    private record CachedAccessToken(
            String value,
            Instant expiresAt,
            String priorRefreshToken,
            String rotatedRefreshToken
    ) {
    }
}
