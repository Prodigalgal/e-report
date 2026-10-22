package com.pro.ereportbacken.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pro.ereportbacken.service.AppConfigService;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

import static com.pro.ereportbacken.config.NotificationConstant.DINGTALK_SECRET_KEY;
import static com.pro.ereportbacken.config.NotificationConstant.DINGTALK_WEBHOOK_URL_KEY;

@Component
@Log4j2
public class DingTalkNotificationChannel implements NotificationChannel {
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);
    private static final NotificationChannelDescriptor DESCRIPTOR = new NotificationChannelDescriptor(
            "dingtalk",
            "钉钉",
            "通过当前作用域的钉钉机器人回调地址发送。",
            com.pro.ereportbacken.config.NotificationConstant.DINGTALK_ENABLED_KEY,
            false
    );

    private final AppConfigService appConfigService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public DingTalkNotificationChannel(AppConfigService appConfigService) {
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
        String webhookUrl = appConfigService.getConfigValue(DINGTALK_WEBHOOK_URL_KEY);
        if (!StringUtils.hasText(webhookUrl)) {
            throw new IllegalStateException("DingTalk notification is enabled but webhook URL is empty.");
        }

        Map<String, Object> payload = Map.of(
                "msgtype", "markdown",
                "markdown", Map.of(
                        "title", (notification.isSlaBreach() ? "工单处理时限超时 #" : "新工单通知 #") + notification.id(),
                        "text", NotificationMarkdownFormatter.format(notification)
                )
        );
        HttpResponse<String> response = postJson(buildWebhookUri(webhookUrl), payload);
        JsonNode root = objectMapper.readTree(response.body());
        if (root.path("errcode").asInt(-1) != 0) {
            throw new IllegalStateException("DingTalk webhook returned error: " + root.path("errmsg").asText(response.body()));
        }
        log.info("DingTalk notification sent for work order {}.", notification.id());
    }

    private URI buildWebhookUri(String webhookUrl) throws Exception {
        String secret = appConfigService.getConfigValue(DINGTALK_SECRET_KEY);
        if (!StringUtils.hasText(secret)) {
            return URI.create(webhookUrl);
        }

        long timestamp = System.currentTimeMillis();
        String signText = timestamp + "\n" + secret;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String sign = URLEncoder.encode(
                Base64.getEncoder().encodeToString(mac.doFinal(signText.getBytes(StandardCharsets.UTF_8))),
                StandardCharsets.UTF_8
        );
        String delimiter = webhookUrl.contains("?") ? "&" : "?";
        return URI.create(webhookUrl + delimiter + "timestamp=" + timestamp + "&sign=" + sign);
    }

    private HttpResponse<String> postJson(URI webhookUri, Map<String, Object> payload) throws Exception {
        String body = objectMapper.writeValueAsString(payload);
        HttpRequest request = HttpRequest.newBuilder(webhookUri)
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("DingTalk webhook HTTP status: " + response.statusCode());
        }
        return response;
    }
}
