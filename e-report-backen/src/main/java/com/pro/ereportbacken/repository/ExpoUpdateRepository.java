package com.pro.ereportbacken.repository;

import com.pro.ereportbacken.model.pojo.ExpoUpdate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface ExpoUpdateRepository extends JpaRepository<ExpoUpdate, UUID> {
    Optional<ExpoUpdate> findFirstByEnvironmentAndRuntimeVersionAndStatusOrderByCreatedAtDescPublishedAtDesc(
            String environment, String runtimeVersion, String status);
    Page<ExpoUpdate> findByEnvironment(String environment, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select release from ExpoUpdate release where release.id = :id")
    Optional<ExpoUpdate> findForPublication(@Param("id") UUID id);

    @Modifying
    @Query("update ExpoUpdate set status = :status, failureMessage = :message, publishedAt = :publishedAt, lastAttemptAt = :now where id = :id and attemptId = :attemptId and status = 'PUBLISHING'")
    int finishPublication(@Param("id") UUID id, @Param("attemptId") UUID attemptId,
            @Param("status") String status, @Param("message") String message,
            @Param("publishedAt") LocalDateTime publishedAt, @Param("now") LocalDateTime now);
}
