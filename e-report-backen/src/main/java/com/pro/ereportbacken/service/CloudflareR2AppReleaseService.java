package com.pro.ereportbacken.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pro.ereportbacken.model.pojo.AppRelease;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

@Service
@Log4j2
public class CloudflareR2AppReleaseService {

    private static final String RELATIVE_ANDROID_OBJECT_PREFIX = "android/";
    private static final int MAX_ATTEMPTS = 3;

    private final boolean enabled;
    private final String accountId;
    private final String bucketName;
    private final String apiToken;
    private final String publicBaseUrl;
    private final String objectPrefix;
    private final String apiBaseUrl;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public CloudflareR2AppReleaseService(
            @Value("${app.update.r2.enabled:false}") boolean enabled,
            @Value("${app.update.r2.account-id:}") String accountId,
            @Value("${app.update.r2.bucket-name:}") String bucketName,
            @Value("${app.update.r2.api-token:}") String apiToken,
            @Value("${app.update.r2.public-base-url:}") String publicBaseUrl,
            @Value("${app.update.r2.object-prefix:}") String objectPrefix,
            @Value("${app.update.r2.api-base-url:https://api.cloudflare.com/client/v4}") String apiBaseUrl) {
        this.enabled = enabled;
        this.accountId = normalize(accountId);
        this.bucketName = normalize(bucketName);
        this.apiToken = normalize(apiToken);
        this.publicBaseUrl = normalizeBaseUrl(publicBaseUrl);
        this.objectPrefix = normalizeObjectPrefix(objectPrefix);
        this.apiBaseUrl = normalizeBaseUrl(apiBaseUrl);
        this.objectMapper = new ObjectMapper();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        validateConfiguration();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String bucketName() {
        return bucketName;
    }

    public String publicBaseUrl() {
        return publicBaseUrl;
    }

    public List<R2Object> listAndroidObjects() {
        requireEnabled();
        List<R2Object> objects = new ArrayList<>();
        Set<String> cursors = new HashSet<>();
        String cursor = "";
        boolean truncated;
        do {
            String requestUrl = objectsEndpoint()
                    + "?per_page=1000&prefix=" + encodeSegment(prefixed(RELATIVE_ANDROID_OBJECT_PREFIX))
                    + (StringUtils.hasText(cursor) ? "&cursor=" + encodeSegment(cursor) : "");
            HttpResponse<String> response = sendWithRetry(() -> authorizedRequest(requestUrl)
                    .GET()
                    .timeout(Duration.ofSeconds(60))
                    .build(), HttpResponse.BodyHandlers.ofString());
            JsonNode root = requireSuccessJson(response, "读取对象存储安装包列表失败");
            JsonNode result = root.path("result");
            if (!result.isArray()) {
                throw new IllegalStateException("对象存储返回的安装包列表格式不正确，请联系管理员排查。");
            }
            result.forEach(node -> {
                String key = node.path("key").asText("");
                if (AppReleaseObjectKey.parse(relativeObjectKey(key)).isPresent()) {
                    objects.add(toObject(node));
                }
            });
            JsonNode page = root.path("result_info");
            truncated = page.path("is_truncated").asBoolean(false);
            cursor = page.path("cursor").asText("");
            if (truncated && (!StringUtils.hasText(cursor) || !cursors.add(cursor))) {
                throw new IllegalStateException("对象存储分页游标无效，无法完整读取安装包列表。");
            }
        } while (truncated);
        return List.copyOf(objects);
    }

    public void upload(AppRelease release, Path apkPath) {
        requireEnabled();
        String objectKey = objectKey(release);
        HttpResponse<String> response = sendWithRetry(() -> {
            HttpRequest.Builder builder = authorizedRequest(objectEndpoint(objectKey))
                    .timeout(Duration.ofMinutes(20))
                    .header("Content-Type", "application/vnd.android.package-archive")
                    .header("Cache-Control", "public, max-age=31536000, immutable")
                    .header("Content-Disposition", "attachment; filename=\"" + downloadFileName(release) + "\"");
            try {
                return builder.PUT(HttpRequest.BodyPublishers.ofFile(apkPath)).build();
            } catch (IOException ex) {
                throw new R2RequestBuildException(ex);
            }
        }, HttpResponse.BodyHandlers.ofString());
        requireSuccessJson(response, "上传安装包到对象存储失败");
        log.info("R2 app release uploaded. key={}, size={}", objectKey, release.getFileSize());
    }

    public void deleteObject(String objectKey) {
        requireEnabled();
        validateAndroidObjectKey(objectKey);
        HttpResponse<String> response = sendWithRetry(() -> authorizedRequest(objectEndpoint(objectKey))
                .DELETE()
                .timeout(Duration.ofSeconds(60))
                .build(), HttpResponse.BodyHandlers.ofString());
        requireSuccessJson(response, "删除对象存储安装包失败");
        log.info("R2 app release deleted. key={}", objectKey);
    }

    public void uploadExpoAsset(String runtime, String sha256, String contentType, Path file) {
        requireEnabled();
        String key = expoAssetKey(runtime, sha256);
        if (!contentType.matches("[A-Za-z0-9.+-]+/[A-Za-z0-9.+-]+")) {
            throw new IllegalArgumentException("OTA 资源类型无效。");
        }
        HttpResponse<String> response = sendWithRetry(() -> {
            try {
                return authorizedRequest(objectsEndpoint() + "/" + encodeObjectKey(key))
                        .timeout(Duration.ofMinutes(2)).header("Content-Type", contentType)
                        .header("Cache-Control", "public, max-age=31536000, immutable, no-transform")
                        .PUT(HttpRequest.BodyPublishers.ofFile(file)).build();
            } catch (IOException exception) { throw new R2RequestBuildException(exception); }
        }, HttpResponse.BodyHandlers.ofString());
        requireSuccessJson(response, "分发 OTA 资源失败");
    }

    public String publicExpoAssetUrl(String runtime, String sha256) {
        requireEnabled();
        return publicBaseUrl + "/" + expoAssetKey(runtime, sha256);
    }

    private String expoAssetKey(String runtime, String sha256) {
        ExpoUpdateArchive.requireRuntime(runtime);
        if (!sha256.matches("[a-f0-9]{64}")) throw new IllegalArgumentException("OTA 资源哈希无效。");
        return prefixed("expo/android/" + runtime + "/" + sha256);
    }

    public String objectKey(AppRelease release) {
        return prefixed(AppReleaseObjectKey.relative(release));
    }

    public String publicUrl(String objectKey) {
        validateAndroidObjectKey(objectKey);
        return publicBaseUrl + "/" + objectKey;
    }

    public void validateAndroidObjectKey(String objectKey) {
        if (!StringUtils.hasText(objectKey)
                || AppReleaseObjectKey.parse(relativeObjectKey(objectKey)).isEmpty()) {
            throw new IllegalArgumentException("对象存储安装包路径不合法。");
        }
    }

    public String relativeObjectKey(String objectKey) {
        String prefix = StringUtils.hasText(objectPrefix) ? objectPrefix + "/" : "";
        if (!StringUtils.hasText(objectKey) || !objectKey.startsWith(prefix)) {
            return "";
        }
        return objectKey.substring(prefix.length());
    }

    private HttpRequest.Builder authorizedRequest(String uri) {
        return HttpRequest.newBuilder(URI.create(uri))
                .header("Authorization", "Bearer " + apiToken)
                .header("User-Agent", "e-report-release-center/1.0");
    }

    private String objectsEndpoint() {
        return apiBaseUrl + "/accounts/" + encodeSegment(accountId)
                + "/r2/buckets/" + encodeSegment(bucketName) + "/objects";
    }

    private String objectEndpoint(String objectKey) {
        validateAndroidObjectKey(objectKey);
        return objectsEndpoint() + "/" + encodeObjectKey(objectKey);
    }

    private JsonNode requireSuccessJson(HttpResponse<String> response, String operationMessage) {
        JsonNode root;
        try {
            root = StringUtils.hasText(response.body()) ? objectMapper.readTree(response.body()) : objectMapper.createObjectNode();
        } catch (IOException ex) {
            throw new IllegalStateException(operationMessage + "：服务返回内容无法识别。", ex);
        }
        if (response.statusCode() >= 200 && response.statusCode() < 300 && root.path("success").asBoolean(true)) {
            return root;
        }
        String cloudflareMessage = firstErrorMessage(root);
        log.warn("R2 request failed. status={}, message={}", response.statusCode(), cloudflareMessage);
        throw new IllegalStateException(operationMessage + "，请稍后重试或联系管理员。上游服务状态码：" + response.statusCode());
    }

    private String firstErrorMessage(JsonNode root) {
        JsonNode errors = root.path("errors");
        if (errors.isArray() && !errors.isEmpty()) {
            return errors.get(0).path("message").asText("unknown");
        }
        return "unknown";
    }

    private R2Object toObject(JsonNode node) {
        String lastModifiedText = node.path("last_modified").asText("");
        Instant lastModified = null;
        if (StringUtils.hasText(lastModifiedText)) {
            try {
                lastModified = Instant.parse(lastModifiedText);
            } catch (DateTimeParseException ignored) {
                log.warn("R2 object last_modified is invalid. key={}", node.path("key").asText(""));
            }
        }
        return new R2Object(
                node.path("key").asText(),
                node.path("size").asLong(),
                node.path("etag").asText(""),
                lastModified
        );
    }

    private <T> HttpResponse<T> sendWithRetry(Supplier<HttpRequest> requestSupplier,
                                               HttpResponse.BodyHandler<T> bodyHandler) {
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                HttpResponse<T> response = httpClient.send(requestSupplier.get(), bodyHandler);
                if (response.statusCode() < 500 || attempt == MAX_ATTEMPTS) {
                    return response;
                }
                lastFailure = new IllegalStateException("对象存储服务暂时不可用，状态码：" + response.statusCode());
            } catch (R2RequestBuildException ex) {
                throw new IllegalStateException("读取本地安装包失败，请检查发布文件。", ex.getCause());
            } catch (IOException ex) {
                lastFailure = new IllegalStateException("连接对象存储失败，请检查网络后重试。", ex);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("对象存储请求已被中断，请稍后重试。", ex);
            }
            sleepBeforeRetry(attempt);
        }
        throw lastFailure == null
                ? new IllegalStateException("对象存储请求失败，请稍后重试。")
                : lastFailure;
    }

    private void sleepBeforeRetry(int attempt) {
        try {
            Thread.sleep(Duration.ofSeconds(attempt * 2L).toMillis());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("对象存储重试等待已被中断。", ex);
        }
    }

    private String downloadFileName(AppRelease release) {
        String safeVersionName = release.getVersionName().replaceAll("[^A-Za-z0-9._-]", "-");
        return "e-report-" + safeVersionName + "-code" + release.getVersionCode() + ".apk";
    }

    private void validateConfiguration() {
        if (!enabled) {
            return;
        }
        if (!StringUtils.hasText(accountId)
                || !StringUtils.hasText(bucketName)
                || !StringUtils.hasText(apiToken)
                || !StringUtils.hasText(publicBaseUrl)) {
            throw new IllegalStateException("对象存储已启用但配置不完整，请检查集群密钥。");
        }
    }

    private void requireEnabled() {
        if (!enabled) {
            throw new IllegalStateException("对象存储安装包功能尚未启用。");
        }
    }

    private String encodeObjectKey(String objectKey) {
        return String.join("/", java.util.Arrays.stream(objectKey.split("/"))
                .map(this::encodeSegment)
                .toList());
    }

    private String encodeSegment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeBaseUrl(String value) {
        return normalize(value).replaceAll("/+$", "");
    }

    private String normalizeObjectPrefix(String value) {
        String normalized = normalize(value).replaceAll("^/+|/+$", "");
        if (StringUtils.hasText(normalized)
                && !normalized.matches("[a-z0-9][a-z0-9._/-]{0,127}")) {
            throw new IllegalStateException("安装包对象存储前缀格式不正确。");
        }
        return normalized;
    }

    private String prefixed(String relativeKey) {
        return StringUtils.hasText(objectPrefix) ? objectPrefix + "/" + relativeKey : relativeKey;
    }

    public record R2Object(String key, long size, String etag, Instant lastModified) {
    }

    private static final class R2RequestBuildException extends RuntimeException {
        private R2RequestBuildException(IOException cause) {
            super(cause);
        }
    }
}
