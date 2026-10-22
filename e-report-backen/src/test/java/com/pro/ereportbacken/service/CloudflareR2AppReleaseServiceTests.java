package com.pro.ereportbacken.service;

import com.pro.ereportbacken.model.pojo.AppPlatform;
import com.pro.ereportbacken.model.pojo.AppRelease;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class CloudflareR2AppReleaseServiceTests {

    @TempDir
    Path temporaryDirectory;

    private HttpServer server;
    private String apiBaseUrl;
    private final AtomicInteger uploadedBytes = new AtomicInteger();
    private final AtomicInteger deleteRequests = new AtomicInteger();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/client/v4/accounts/account/r2/buckets/bucket/objects", this::handleRequest);
        server.start();
        apiBaseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/client/v4";
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void listsUploadsAndDeletesImmutableAndroidObjects() throws Exception {
        CloudflareR2AppReleaseService service = new CloudflareR2AppReleaseService(
                true,
                "account",
                "bucket",
                "secret-token",
                "https://download.example",
                "",
                apiBaseUrl
        );

        var objects = service.listAndroidObjects();
        assertThat(objects).singleElement().satisfies(object -> {
            assertThat(object.key()).isEqualTo("android/stable/25/app-release.apk");
            assertThat(object.size()).isEqualTo(4L);
        });

        Path apk = temporaryDirectory.resolve("app-release.apk");
        Files.write(apk, "test".getBytes(StandardCharsets.UTF_8));
        AppRelease release = release();
        service.upload(release, apk);
        service.deleteObject(service.objectKey(release));

        assertThat(uploadedBytes).hasValue(4);
        assertThat(deleteRequests).hasValue(1);
        assertThat(service.publicUrl(service.objectKey(release)))
                .isEqualTo("https://download.example/android/stable/25/app-release.apk");
    }

    @Test
    void isolatesTestObjectsBehindDedicatedPrefix() {
        CloudflareR2AppReleaseService service = new CloudflareR2AppReleaseService(
                true,
                "account",
                "bucket",
                "secret-token",
                "https://download.example",
                "e-report-test",
                apiBaseUrl
        );

        assertThat(service.objectKey(release()))
                .isEqualTo("e-report-test/android/stable/25/app-release.apk");
        assertThat(service.listAndroidObjects())
                .extracting(CloudflareR2AppReleaseService.R2Object::key)
                .containsExactly("e-report-test/android/stable/25/app-release.apk");
        assertThat(service.relativeObjectKey("android/stable/25/app-release.apk")).isEmpty();
    }

    private void handleRequest(HttpExchange exchange) throws IOException {
        assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer secret-token");
        String method = exchange.getRequestMethod();
        if ("GET".equals(method)) {
            respond(exchange, 200, """
                    {"success":true,"result":[
                      {"key":"android/stable/25/app-release.apk","etag":"etag","last_modified":"2026-08-05T01:02:03Z","size":4},
                      {"key":"e-report-test/android/stable/25/app-release.apk","etag":"test-etag","last_modified":"2026-08-05T01:02:03Z","size":4},
                      {"key":"other/file.bin","etag":"ignored","last_modified":"2026-08-05T01:02:03Z","size":8}
                    ]}
                    """);
            return;
        }
        if ("PUT".equals(method)) {
            uploadedBytes.set(exchange.getRequestBody().readAllBytes().length);
            assertThat(exchange.getRequestHeaders().getFirst("Cache-Control")).contains("immutable");
            respond(exchange, 200, "{\"success\":true,\"result\":{\"key\":\"android/stable/25/app-release.apk\"}}");
            return;
        }
        if ("DELETE".equals(method)) {
            deleteRequests.incrementAndGet();
            respond(exchange, 200, "{\"success\":true,\"result\":null}");
            return;
        }
        respond(exchange, 405, "{\"success\":false}");
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private AppRelease release() {
        AppRelease release = new AppRelease();
        release.setPlatform(AppPlatform.ANDROID);
        release.setChannel("stable");
        release.setVersionCode(25);
        release.setVersionName("1.0.24");
        release.setFileSize(4L);
        return release;
    }
}
