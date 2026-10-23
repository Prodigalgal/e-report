package com.pro.ereportbacken.repository;

import com.pro.ereportbacken.model.pojo.EmailDispatch;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface EmailDispatchRepository extends JpaRepository<EmailDispatch, UUID> {
    Optional<EmailDispatch> findByDispatchKey(String dispatchKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT dispatch FROM EmailDispatch dispatch WHERE dispatch.dispatchKey = :key")
    Optional<EmailDispatch> findForUpdate(@Param("key") String dispatchKey);

    @Modifying
    @Query("""
            UPDATE EmailDispatch dispatch SET dispatch.lockedUntil = :until
            WHERE dispatch.dispatchKey = :key AND dispatch.leaseToken = :token
              AND dispatch.status = com.pro.ereportbacken.notification.delivery.EmailDeliveryStatus.SENDING
            """)
    int renew(@Param("key") String dispatchKey, @Param("token") UUID token,
              @Param("until") java.time.LocalDateTime until);
}
