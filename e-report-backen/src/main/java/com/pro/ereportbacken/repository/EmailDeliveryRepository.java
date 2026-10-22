package com.pro.ereportbacken.repository;

import com.pro.ereportbacken.model.pojo.EmailDelivery;
import com.pro.ereportbacken.notification.delivery.EmailDeliveryStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.UUID;

public interface EmailDeliveryRepository extends JpaRepository<EmailDelivery, UUID>, JpaSpecificationExecutor<EmailDelivery> {
    @Modifying
    @Query("""
            UPDATE EmailDelivery delivery SET delivery.status = :status, delivery.completedAt = :completedAt,
                delivery.durationMs = :durationMs, delivery.failureCode = :failureCode
            WHERE delivery.id = :id AND delivery.status = com.pro.ereportbacken.notification.delivery.EmailDeliveryStatus.SENDING
            """)
    int complete(@Param("id") UUID id, @Param("status") EmailDeliveryStatus status,
                 @Param("completedAt") LocalDateTime completedAt, @Param("durationMs") long durationMs,
                 @Param("failureCode") String failureCode);
}
