package com.pro.ereportbacken.repository;

import java.util.UUID;

import com.pro.ereportbacken.model.pojo.OutboxEvent;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT event FROM OutboxEvent event
            WHERE event.availableAt <= :now
              AND (event.state = 'PENDING'
                   OR (event.state = 'PROCESSING' AND event.lockedAt < :staleBefore))
            ORDER BY event.id ASC
            """)
    List<OutboxEvent> findDeliverable(
            @Param("now") LocalDateTime now,
            @Param("staleBefore") LocalDateTime staleBefore,
            Pageable pageable
    );
}


