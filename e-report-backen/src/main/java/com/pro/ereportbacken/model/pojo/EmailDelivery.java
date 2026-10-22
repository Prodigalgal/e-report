package com.pro.ereportbacken.model.pojo;

import com.pro.ereportbacken.notification.delivery.EmailDeliveryCategory;
import com.pro.ereportbacken.notification.delivery.EmailDeliveryStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "email_deliveries", indexes = {
        @Index(name = "idx_email_delivery_time", columnList = "tenant_id,started_at,id"),
        @Index(name = "idx_email_delivery_scope_time", columnList = "tenant_id,org_unit_id,started_at,id"),
        @Index(name = "idx_email_delivery_status_time", columnList = "tenant_id,status,started_at,id")
})
@Getter
@Setter
public class EmailDelivery extends UuidV7Entity {
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(name = "org_unit_id")
    private UUID orgUnitId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private EmailDeliveryCategory category;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private EmailDeliveryStatus status;
    @Column(name = "related_id")
    private UUID relatedId;
    @Column(name = "recipient_user_id")
    private UUID recipientUserId;
    @Column(name = "recipient_address", nullable = false, length = 320)
    private String recipientAddress;
    @Column(name = "sender_address", length = 320)
    private String senderAddress;
    @Column(nullable = false, length = 255)
    private String subject;
    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;
    @Column(name = "completed_at")
    private LocalDateTime completedAt;
    @Column(name = "duration_ms")
    private Long durationMs;
    @Column(name = "failure_code", length = 40)
    private String failureCode;
}
