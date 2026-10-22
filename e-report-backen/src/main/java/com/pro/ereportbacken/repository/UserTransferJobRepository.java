package com.pro.ereportbacken.repository;

import com.pro.ereportbacken.model.pojo.UserTransferJob;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.Optional;
import java.util.UUID;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface UserTransferJobRepository extends JpaRepository<UserTransferJob, UUID> {
    Optional<UserTransferJob> findByTenantIdAndIdAndCreatedByMembershipId(
            UUID tenantId, UUID id, UUID createdByMembershipId);

    Optional<UserTransferJob> findByTenantIdAndCreatedByMembershipIdAndIdempotencyKey(
            UUID tenantId, UUID createdByMembershipId, String idempotencyKey);

    Page<UserTransferJob> findByTenantIdAndCreatedByMembershipIdOrderByCreatedAtDesc(
            UUID tenantId, UUID createdByMembershipId, Pageable pageable);

    Page<UserTransferJob> findByTenantIdAndCreatedByMembershipIdAndJobTypeOrderByCreatedAtDesc(
            UUID tenantId,
            UUID createdByMembershipId,
            com.pro.ereportbacken.model.pojo.UserTransferJobType jobType,
            Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select job from UserTransferJob job where job.id = :id")
    Optional<UserTransferJob> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select job from UserTransferJob job where job.tenantId = :tenantId and job.id = :id")
    Optional<UserTransferJob> findByTenantIdAndIdForUpdate(
            @Param("tenantId") UUID tenantId,
            @Param("id") UUID id);

    List<UserTransferJob> findTop100ByStatusInAndUpdatedAtBeforeOrderByUpdatedAtAsc(
            Collection<com.pro.ereportbacken.model.pojo.UserTransferJobStatus> statuses,
            LocalDateTime cutoff);

    List<UserTransferJob> findTop100ByExpiresAtBeforeOrderByExpiresAtAsc(LocalDateTime cutoff);

    long countByTenantIdAndStatusIn(
            UUID tenantId,
            Collection<com.pro.ereportbacken.model.pojo.UserTransferJobStatus> statuses);

    long countByTenantIdAndCreatedByMembershipIdAndStatusIn(
            UUID tenantId,
            UUID createdByMembershipId,
            Collection<com.pro.ereportbacken.model.pojo.UserTransferJobStatus> statuses);
}
