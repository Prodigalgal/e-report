package com.pro.ereportbacken.notification;

import com.pro.ereportbacken.service.FileStorageService;
import com.pro.ereportbacken.service.MapKeyPoolService;
import com.pro.ereportbacken.service.MapService;
import com.pro.ereportbacken.notification.delivery.EmailDeliveryCategory;
import com.pro.ereportbacken.notification.delivery.EmailDeliveryContext;
import com.pro.ereportbacken.notification.delivery.EmailDeliveryTracker;
import com.pro.ereportbacken.time.DisplayTime;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.log4j.Log4j2;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Component
@Log4j2
public class EmailNotificationChannel implements NotificationChannel {
    public static final String CHANNEL_NAME = "email";

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final NotificationChannelDescriptor DESCRIPTOR = new NotificationChannelDescriptor(
            CHANNEL_NAME,
            "邮件",
            "工单通知发送给对应管理员或当前/待接收处理人；优先使用个人邮箱，未配置时使用手机号@139.com。",
            com.pro.ereportbacken.config.NotificationConstant.EMAIL_ENABLED_KEY,
            false
    );

    private final ConfiguredMailSenderFactory mailSenderFactory;
    private final FileStorageService fileStorageService;
    private final MapService mapService;
    private final MapKeyPoolService mapKeyPoolService;
    private final EmailNotificationRecipientResolver recipientResolver;
    private final EmailDeliveryTracker deliveryTracker;

    public EmailNotificationChannel(ConfiguredMailSenderFactory mailSenderFactory,
                                    FileStorageService fileStorageService,
                                    MapService mapService,
                                    MapKeyPoolService mapKeyPoolService,
                                    EmailNotificationRecipientResolver recipientResolver,
                                    EmailDeliveryTracker deliveryTracker) {
        this.mailSenderFactory = mailSenderFactory;
        this.fileStorageService = fileStorageService;
        this.mapService = mapService;
        this.mapKeyPoolService = mapKeyPoolService;
        this.recipientResolver = recipientResolver;
        this.deliveryTracker = deliveryTracker;
    }

    @Override
    public NotificationChannelDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public void send(WorkOrderNotification notification) {
        EmailNotificationRecipientResolver.RecipientSelection selection = recipientResolver.resolve(notification);
        if (selection.allUnsubscribed()) {
            log.info("邮件通知正常跳过。workOrderId={}, kind={}, reason=PERSONAL_SUBSCRIPTION_DISABLED, unsubscribed={}",
                    notification.id(), notification.kind(), selection.unsubscribedCount());
            return;
        }
        List<EmailNotificationRecipientResolver.EmailRecipient> recipients = selection.recipients();
        if (recipients.isEmpty()) {
            String recipientType = notification.isAssignmentReceived() ? "派单接收人" : "提单组织管理员";
            throw new IllegalStateException("邮件通知已启用，但" + recipientType + "没有可用的收件地址。");
        }

        ConfiguredMailSenderFactory.MailSenderContext mailContext;
        try {
            mailContext = mailSenderFactory.next();
        } catch (RuntimeException failure) {
            recipients.forEach(recipient -> deliveryTracker.configurationFailed(deliveryContext(notification, recipient)));
            throw failure;
        }
        JavaMailSender configuredMailSender = mailContext.mailSender();
        String mailFrom = mailContext.fromAddress();

        int successCount = 0;
        List<Exception> failures = new java.util.ArrayList<>();
        for (EmailNotificationRecipientResolver.EmailRecipient recipient : recipients) {
            try {
                deliveryTracker.send(deliveryContext(notification, recipient), mailFrom, () -> {
                    MimeMessage mimeMessage = configuredMailSender.createMimeMessage();
                    MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
                    helper.setFrom(mailFrom);
                    helper.setSubject(subject(notification));
                    helper.setText(buildHtmlContent(helper, notification), true);
                    helper.setTo(recipient.address());
                    configuredMailSender.send(mimeMessage);
                });
                successCount++;
            } catch (MessagingException | MailException e) {
                failures.add(e);
                log.error("邮件通知投递失败。workOrderId={}, userId={}, address={}, phoneFallback={}, reason={}",
                        notification.id(), recipient.userId(),
                        EmailNotificationRecipientResolver.maskAddress(recipient.address()),
                        recipient.phoneFallback(), e.getMessage(), e);
            }
        }
        log.info("邮件通知投递完成。workOrderId={}, success={}/{}",
                notification.id(), successCount, recipients.size());
        if (!failures.isEmpty()) {
            IllegalStateException deliveryFailure = new IllegalStateException(
                    "邮件通知有 " + failures.size() + " 个收件地址投递失败。"
            );
            failures.forEach(deliveryFailure::addSuppressed);
            throw deliveryFailure;
        }
    }

    private EmailDeliveryContext deliveryContext(WorkOrderNotification notification,
                                                 EmailNotificationRecipientResolver.EmailRecipient recipient) {
        return deliveryTracker.context(EmailDeliveryCategory.valueOf(notification.kind().name()),
                notification.reportingOrgUnitId(), notification.id(), recipient.userId(), recipient.address(), subject(notification));
    }

    private String buildHtmlContent(MimeMessageHelper helper, WorkOrderNotification notification) {
        StringBuilder html = new StringBuilder();
        html.append("<html><body style='font-family: Arial, sans-serif; line-height: 1.6;'>");
        html.append("<h3>").append(escapeHtml(title(notification))).append("</h3>");
        html.append("<table border='1' cellpadding='5' style='border-collapse: collapse; width: 100%; max-width: 680px;'>");
        appendRow(html, "工单标识", String.valueOf(notification.id()));
        appendRow(html, "工单类型", notification.reportTypeName());
        if (notification.isAssignmentReceived() && notification.assignee() != null) {
            if (notification.assignmentPending()) {
                appendRow(html, "派单接收人", notification.assignee().name());
                appendRow(html, "待办", "请登录系统接受或拒绝此派单。");
            } else {
                appendRow(html, "当前处理人", notification.assignee().name());
            }
        }
        if (notification.isSlaBreach()) {
            appendRow(html, "优先级", notification.priorityName());
            appendRow(html, "超时阶段", notification.slaStageName());
            appendRow(html, "阶段截止", DisplayTime.formatUtc(notification.slaDueAt(), DATE_TIME_FORMATTER));
            appendRow(html, "超时确认", DisplayTime.formatUtc(notification.slaBreachedAt(), DATE_TIME_FORMATTER));
        }
        appendRow(html, "故障地点", notification.location());
        appendRow(html, "定位坐标（经度,纬度）", notification.gpsCoordinates());
        appendRow(html, "问题描述", notification.reason());
        appendRow(html, "联系人", notification.contactPerson());
        appendRow(html, "联系电话", notification.phone());
        appendRow(html, "上报时间", DisplayTime.formatUtc(notification.reportTime(), DATE_TIME_FORMATTER));
        html.append("</table>");

        if (!notification.isSlaBreach()) {
            appendStaticMap(helper, notification, html);
            appendAttachments(helper, notification, html);
        }

        html.append("<hr/><p>请尽快登录系统查看并处理此工单。</p>");
        html.append("<p style='font-size: 0.9em; color: #555;'>此邮件为系统自动发送，请勿直接回复。</p>");
        html.append("</body></html>");
        return html.toString();
    }

    private String subject(WorkOrderNotification notification) {
        if (notification.isSlaBreach()) {
            return "【处理时限超时】" + notification.reportTypeName() + " - 工单：" + notification.id();
        }
        if (notification.isAssignmentReceived()) {
            return notification.assignmentPending()
                    ? "【待接受派单】请处理" + notification.reportTypeName() + "工单 - " + notification.id()
                    : "【工单调单通知】您已成为" + notification.reportTypeName() + "工单处理人 - " + notification.id();
        }
        return "【工单通知】新的" + notification.reportTypeName() + " - ID: " + notification.id();
    }

    private String title(WorkOrderNotification notification) {
        if (notification.isSlaBreach()) {
            return "工单处理时限超时";
        }
        if (!notification.isAssignmentReceived()) {
            return "新的工单通知";
        }
        return notification.assignmentPending() ? "您有一张新的工单待接收" : "您已成为此工单的当前处理人";
    }

    private void appendStaticMap(MimeMessageHelper helper, WorkOrderNotification notification, StringBuilder html) {
        if (!StringUtils.hasText(notification.gpsCoordinates())) {
            return;
        }
        StaticMapImage staticMapImage = fetchStaticMap(notification);
        if (staticMapImage == null) {
            html.append("<p style='color: orange;'><i>未能生成位置地图预览。</i></p>");
            return;
        }
        try {
            String mapContentId = "static_map_" + UUID.randomUUID().toString().substring(0, 8);
            helper.addInline(mapContentId, new ByteArrayResource(staticMapImage.content()), staticMapImage.contentType());
            html.append("<br/><h4>工单位置地图预览：</h4>");
            html.append("<p><img src='cid:").append(mapContentId)
                    .append("' alt='工单位置地图' style='max-width: 600px; height: auto; border: 1px solid #ddd;'/></p>");
        } catch (MessagingException e) {
            log.error("Failed to embed static map for work order {}: {}", notification.id(), e.getMessage(), e);
        }
    }

    private void appendAttachments(MimeMessageHelper helper, WorkOrderNotification notification, StringBuilder html) {
        if (notification.attachments().isEmpty()) {
            html.append("<p><i>无附件。</i></p>");
            return;
        }

        html.append("<br/><h4>附件图片预览：</h4>");
        int imageCount = 0;
        for (WorkOrderNotification.AttachmentInfo attachment : notification.attachments()) {
            try {
                Resource resource = fileStorageService.loadFileAsResource(attachment.storedFileName());
                if (attachment.fileType().startsWith("image/") && imageCount < 3) {
                    String contentId = "image_" + UUID.randomUUID().toString().substring(0, 8);
                    helper.addInline(contentId, resource, attachment.fileType());
                    html.append("<p><img src='cid:")
                            .append(contentId)
                            .append("' alt='")
                            .append(escapeHtml(attachment.fileName()))
                            .append("' style='max-width: 400px; height: auto; border: 1px solid #ddd;'/><br/>")
                            .append("<i>")
                            .append(escapeHtml(attachment.fileName()))
                            .append("</i></p>");
                    imageCount++;
                } else {
                    helper.addAttachment(attachment.fileName(), resource);
                }
            } catch (IOException | MessagingException e) {
                log.error("Failed to attach file {} for work order {}: {}",
                        attachment.storedFileName(), notification.id(), e.getMessage(), e);
                html.append("<p style='color: red;'>无法加载附件：")
                        .append(escapeHtml(attachment.fileName()))
                        .append("</p>");
            }
        }
        if (imageCount == 0) {
            html.append("<p><i>无图片附件预览。其他附件（如有）已附在邮件中。</i></p>");
        }
    }

    private StaticMapImage fetchStaticMap(WorkOrderNotification notification) {
        String amapKey = mapKeyPoolService.nextAmapKey();
        if (!StringUtils.hasText(amapKey)) {
            log.warn("AMap API key pool is not configured. Static map is skipped for work order {}.", notification.id());
            return null;
        }

        String[] coords = notification.gpsCoordinates().split(",");
        if (coords.length != 2) {
            log.warn("Invalid GPS coordinates for work order {}: {}", notification.id(), notification.gpsCoordinates());
            return null;
        }

        try {
            double longitude = Double.parseDouble(coords[0].trim());
            double latitude = Double.parseDouble(coords[1].trim());
            if (Math.abs(longitude) <= 90 && Math.abs(latitude) > 90) {
                double temp = longitude;
                longitude = latitude;
                latitude = temp;
            }
            if (Math.abs(longitude) > 180 || Math.abs(latitude) > 90) {
                log.warn("GPS coordinates are out of range for work order {}: {}", notification.id(), notification.gpsCoordinates());
                return null;
            }

            MapService.Point point = mapService.wgs84ToGcj02(latitude, longitude);
            String locationParam = point.lon + "," + point.lat;
            String mapUrl = String.format(
                    "https://restapi.amap.com/v3/staticmap?location=%s&zoom=15&size=750*750&markers=mid,,A:%s&key=%s",
                    locationParam,
                    locationParam,
                    amapKey
            );
            return fetchStaticMapImage(mapUrl);
        } catch (NumberFormatException e) {
            log.warn("GPS coordinates are not numeric for work order {}: {}", notification.id(), notification.gpsCoordinates());
            return null;
        }
    }

    private StaticMapImage fetchStaticMapImage(String mapUrl) {
        HttpURLConnection connection = null;
        try {
            URL url = URI.create(mapUrl).toURL();
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(8000);

            int responseCode = connection.getResponseCode();
            String contentType = connection.getContentType();
            if (responseCode != HttpURLConnection.HTTP_OK || contentType == null || !contentType.startsWith("image/")) {
                log.warn("AMap static map request failed. status={}, contentType={}, body={}",
                        responseCode, contentType, readResponseBody(connection));
                return null;
            }
            try (InputStream inputStream = new BufferedInputStream(connection.getInputStream())) {
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                inputStream.transferTo(output);
                return new StaticMapImage(output.toByteArray(), contentType);
            }
        } catch (IOException | IllegalArgumentException e) {
            log.warn("AMap static map request failed: {}", e.getMessage());
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String readResponseBody(HttpURLConnection connection) {
        try (InputStream stream = connection.getErrorStream() != null ? connection.getErrorStream() : connection.getInputStream()) {
            if (stream == null) {
                return "";
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    private void appendRow(StringBuilder html, String label, String value) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        html.append("<tr><td style='width: 30%;'><strong>")
                .append(escapeHtml(label))
                .append(":</strong></td><td>")
                .append(escapeHtml(value))
                .append("</td></tr>");
    }

    private String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private record StaticMapImage(byte[] content, String contentType) {
    }
}
