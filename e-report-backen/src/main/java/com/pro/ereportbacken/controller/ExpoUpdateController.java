package com.pro.ereportbacken.controller;

import com.pro.ereportbacken.service.CloudflareR2AppReleaseService;
import com.pro.ereportbacken.service.ExpoUpdateConfiguration;
import com.pro.ereportbacken.service.ExpoUpdateService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@RestController
@RequestMapping("/app-updates/ota")
public class ExpoUpdateController {
    private final ExpoUpdateService service;
    private final ExpoUpdateConfiguration configuration;
    private final CloudflareR2AppReleaseService storage;
    public ExpoUpdateController(ExpoUpdateService service, ExpoUpdateConfiguration configuration,
            CloudflareR2AppReleaseService storage) {
        this.service = service; this.configuration = configuration; this.storage = storage;
    }

    @GetMapping
    public ResponseEntity<byte[]> manifest(
            @RequestHeader("expo-protocol-version") String protocol,
            @RequestHeader("expo-platform") String platform,
            @RequestHeader("expo-runtime-version") String runtime,
            @RequestHeader(value = "Accept", defaultValue = "*/*") String accept,
            @RequestHeader(value = "expo-current-update-id", required = false) String currentId,
            @RequestHeader(value = "expo-embedded-update-id", required = false) String embeddedId,
            @RequestHeader(value = "e-report-environment", required = false) String environment) {
        if (!"1".equals(protocol) || !"android".equals(platform)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的 OTA 协议或平台。");
        if (environment != null && !configuration.environment().equals(environment)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OTA 环境不一致。");
        var update = service.latest(runtime);
        HttpHeaders headers = responseHeaders();
        if (update.isEmpty() || update.get().getId().toString().equals(currentId)
                || ("ROLLBACK".equals(update.get().getKind()) && currentId != null && currentId.equals(embeddedId))) {
            return ResponseEntity.status(HttpStatus.NO_CONTENT).headers(headers).build();
        }
        var release = update.get();
        boolean multipart = MediaType.parseMediaTypes(accept).stream().anyMatch(type -> type.getQualityValue() > 0
                && type.isCompatibleWith(MediaType.parseMediaType("multipart/mixed")));
        String signature = "sig=\"" + release.getSignature() + "\", keyid=\"main\", alg=\"rsa-v1_5-sha256\"";
        if (multipart) {
            String boundary = "e-report-" + UUID.randomUUID();
            String name = "ROLLBACK".equals(release.getKind()) ? "directive" : "manifest";
            String body = "--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name + "\"\r\n"
                    + "Content-Type: application/json; charset=utf-8\r\nexpo-signature: " + signature
                    + "\r\n\r\n" + release.getPayload() + "\r\n--" + boundary + "--\r\n";
            headers.set(HttpHeaders.CONTENT_TYPE, "multipart/mixed; boundary=" + boundary);
            return ResponseEntity.ok().headers(headers).body(body.getBytes(StandardCharsets.UTF_8));
        }
        boolean json = MediaType.parseMediaTypes(accept).stream().anyMatch(type -> type.getQualityValue() > 0
                && (type.isCompatibleWith(MediaType.APPLICATION_JSON) || type.isCompatibleWith(MediaType.parseMediaType("application/expo+json"))));
        if (!json || "ROLLBACK".equals(release.getKind())) throw new ResponseStatusException(HttpStatus.NOT_ACCEPTABLE, "此 OTA 响应需要 multipart/mixed。");
        headers.set(HttpHeaders.CONTENT_TYPE, "application/expo+json; charset=utf-8");
        headers.set("expo-signature", signature);
        return ResponseEntity.ok().headers(headers).body(release.getPayload().getBytes(StandardCharsets.UTF_8));
    }

    @RequestMapping(value = "/assets/{runtime}/{sha256}", method = {RequestMethod.GET, RequestMethod.HEAD})
    public ResponseEntity<Void> asset(@PathVariable String runtime, @PathVariable String sha256) {
        configuration.requireEnabled();
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(storage.publicExpoAssetUrl(runtime, sha256)))
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=31536000, immutable").build();
    }

    private HttpHeaders responseHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("expo-protocol-version", "1"); headers.set("expo-sfv-version", "0");
        headers.set("expo-manifest-filters", "environment=\"" + configuration.environment() + "\"");
        headers.set("expo-server-defined-headers", "");
        headers.setCacheControl("private, no-store");
        headers.setVary(java.util.List.of("expo-runtime-version", "expo-platform", "expo-current-update-id", "Accept", "e-report-environment"));
        return headers;
    }
}
