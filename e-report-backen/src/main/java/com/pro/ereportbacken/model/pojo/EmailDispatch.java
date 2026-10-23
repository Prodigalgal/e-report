package com.pro.ereportbacken.model.pojo;

import com.pro.ereportbacken.notification.delivery.EmailDeliveryStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "email_dispatches")
@Getter
@Setter
public class EmailDispatch extends UuidV7Entity {
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(name = "dispatch_key", nullable = false, unique = true, length = 64)
    private String dispatchKey;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private EmailDeliveryStatus status;
    @Column(nullable = false)
    private int attempts;
    @Column(nullable = false)
    private boolean retryable;
    @Column(name = "next_attempt_at")
    private LocalDateTime nextAttemptAt;
    @Column(name = "lease_token")
    private UUID leaseToken;
    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;
    @Column(name = "failure_code", length = 40)
    private String failureCode;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    @Column(name = "completed_at")
    private LocalDateTime completedAt;
}
