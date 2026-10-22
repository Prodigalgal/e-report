package com.pro.ereportbacken.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pro.ereportbacken.service.AppConfigService;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

import static com.pro.ereportbacken.config.NotificationConstant.WECOM_WEBHOOK_URL_KEY;

@Component
@Log4j2
public class WeComNotificationChannel implements NotificationChannel {
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);
    private static final NotificationChannelDescriptor DESCRIPTOR = new NotificationChannelDescriptor(
            "wecom",
            "企业微信",
            "通过当前作用域的企业微信机器人回调地址发送。",
            com.pro.ereportbacken.config.NotificationConstant.WECOM_ENABLED_KEY,
            false
    );

    private final AppConfigService appConfigService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public WeComNotificationChannel(AppConfigService appConfigService) {
        this.appConfigService = appConfigService;
        this.objectMapper = new ObjectMapper();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(REQUEST_TIMEOUT)
                .build();
    }

    @Override
    public NotificationChannelDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public void send(WorkOrderNotification notification) throws Exception {
        String webhookUrl = appConfigService.getConfigValue(WECOM_WEBHOOK_URL_KEY);
        if (!StringUtils.hasText(webhookUrl)) {
            throw new IllegalStateException("WeCom notification is enabled but webhook URL is empty.");
        }

        Map<String, Object> payload = Map.of(
                "msgtype", "markdown",
                "markdown", Map.of("content", NotificationMarkdownFormatter.format(notification))
        );
        HttpResponse<String> response = postJson(webhookUrl, payload);
        JsonNode root = objectMapper.readTree(response.body());
        if (root.path("errcode").asInt(-1) != 0) {
            throw new IllegalStateException("WeCom webhook returned error: " + root.path("errmsg").asText(response.body()));
        }
        log.info("WeCom notification sent for work order {}.", notification.id());
    }

    private HttpResponse<String> postJson(String webhookUrl, Map<String, Object> payload) throws Exception {
        String body = objectMapper.writeValueAsString(payload);
        HttpRequest request = HttpRequest.newBuilder(URI.create(webhookUrl))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("WeCom webhook HTTP status: " + response.statusCode());
        }
        return response;
    }
}
