package com.pro.ereportbacken.service;

import com.pro.ereportbacken.model.pojo.AppRelease;
import com.pro.ereportbacken.model.pojo.AppEdition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class AppReleaseUrlBuilder {

    private final String publicBaseUrl;
    private final String stableDownloadUrl;
    private final String r2PublicBaseUrl;
    private final String r2ObjectPrefix;

    public AppReleaseUrlBuilder(@Value("${app.public-base-url:}") String publicBaseUrl,
                                @Value("${app.update.stable-download-url:}") String stableDownloadUrl,
                                @Value("${app.update.r2.public-base-url:}") String r2PublicBaseUrl,
                                @Value("${app.update.r2.object-prefix:}") String r2ObjectPrefix) {
        this.publicBaseUrl = normalizeBaseUrl(publicBaseUrl);
        this.stableDownloadUrl = normalizeBaseUrl(stableDownloadUrl);
        this.r2PublicBaseUrl = normalizeBaseUrl(r2PublicBaseUrl);
        this.r2ObjectPrefix = normalizeObjectPrefix(r2ObjectPrefix);
    }

    public String latestApkUrl(AppEdition edition, String channel) {
        if (edition != AppEdition.LITE && "stable".equals(channel) && StringUtils.hasText(stableDownloadUrl)) {
            return stableDownloadUrl;
        }
        UriComponentsBuilder builder = StringUtils.hasText(publicBaseUrl)
                ? UriComponentsBuilder.fromUriString(publicBaseUrl)
                : ServletUriComponentsBuilder.fromCurrentContextPath();
        if (edition == AppEdition.LITE) {
            builder.queryParam("edition", AppEdition.LITE.name());
        }
        return builder
                .path("/app-updates/latest/apk")
                .queryParam("platform", "ANDROID")
                .queryParam("channel", channel)
                .toUriString();
    }

    public String distributionApkUrl(AppRelease release) {
        if (!StringUtils.hasText(r2PublicBaseUrl)) {
            throw new IllegalStateException("R2 公开下载地址未配置。");
        }
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(r2PublicBaseUrl);
        if (StringUtils.hasText(r2ObjectPrefix)) {
            builder.pathSegment(r2ObjectPrefix.split("/"));
        }
        builder.pathSegment(AppReleaseObjectKey.relative(release).split("/"));
        if (StringUtils.hasText(release.getSha256())) {
            builder.queryParam("v", release.getSha256().trim().toLowerCase());
        }
        return builder.toUriString();
    }

    public String releaseApkUrl(AppRelease release) {
        UriComponentsBuilder builder = StringUtils.hasText(publicBaseUrl)
                ? UriComponentsBuilder.fromUriString(publicBaseUrl)
                : ServletUriComponentsBuilder.fromCurrentContextPath();
        return builder.pathSegment("app-updates", "releases", release.getId().toString(), "apk")
                .toUriString();
    }

    private String normalizeBaseUrl(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim().replaceAll("/+$", "");
    }

    private String normalizeObjectPrefix(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim().replaceAll("^/+|/+$", "");
    }
}
