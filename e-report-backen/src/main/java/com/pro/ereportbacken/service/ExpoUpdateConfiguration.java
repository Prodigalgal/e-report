package com.pro.ereportbacken.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.net.URI;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Set;

@Component
public class ExpoUpdateConfiguration {
    private final boolean enabled;
    private final String environment;
    private final String publicBaseUrl;
    private final PublicKey publicKey;

    public ExpoUpdateConfiguration(@Value("${app.update.ota.enabled:false}") boolean enabled,
            @Value("${app.update.ota.environment:production}") String environment,
            @Value("${app.public-base-url:}") String publicBaseUrl,
            @Value("${app.update.ota.certificate:classpath:ota/certificate.crt}") Resource certificate) {
        this.enabled = enabled;
        this.environment = environment;
        this.publicBaseUrl = publicBaseUrl.trim().replaceAll("/+$", "");
        if (!Set.of("test", "production").contains(environment)) {
            throw new IllegalStateException("OTA 环境必须为 test 或 production。");
        }
        if (enabled) {
            URI uri = URI.create(this.publicBaseUrl);
            if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                    || uri.getQuery() != null || uri.getFragment() != null) {
                throw new IllegalStateException("OTA 需要明确的 HTTPS 公开 API 地址。");
            }
        }
        try (var input = certificate.getInputStream()) {
            X509Certificate parsed = (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(input);
            parsed.checkValidity();
            this.publicKey = parsed.getPublicKey();
            if (!"RSA".equals(publicKey.getAlgorithm())) throw new IllegalStateException("OTA 签名证书必须使用 RSA。");
            if (parsed.getKeyUsage() == null || !parsed.getKeyUsage()[0] || parsed.getExtendedKeyUsage() == null
                    || !parsed.getExtendedKeyUsage().contains("1.3.6.1.5.5.7.3.3")) {
                throw new IllegalStateException("OTA 证书必须声明数字签名和代码签名用途。");
            }
        } catch (IOException | GeneralSecurityException exception) {
            throw new IllegalStateException("无法读取 OTA 签名证书。", exception);
        }
    }

    public boolean enabled() { return enabled; }
    public String environment() { return environment; }
    public String publicBaseUrl() { return publicBaseUrl; }
    public PublicKey publicKey() { return publicKey; }
    public void requireEnabled() {
        if (!enabled) throw new IllegalStateException("当前环境尚未启用 OTA 发布。");
    }
    public String assetUrl(String runtime, String sha256) {
        return publicBaseUrl + "/app-updates/ota/assets/" + runtime + "/" + sha256;
    }
}
