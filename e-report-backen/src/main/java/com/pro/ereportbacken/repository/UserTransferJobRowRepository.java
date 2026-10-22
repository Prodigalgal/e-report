package com.pro.ereportbacken.repository;

import com.pro.ereportbacken.model.pojo.UserTransferJobRow;
import com.pro.ereportbacken.model.pojo.UserTransferValidationStatus;
import com.pro.ereportbacken.model.pojo.UserTransferExecutionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;

import java.util.UUID;
import java.util.List;

public interface UserTransferJobRowRepository extends JpaRepository<UserTransferJobRow, UUID> {
    Page<UserTransferJobRow> findByTenantIdAndJobId(
            UUID tenantId, UUID jobId, Pageable pageable);

    Page<UserTransferJobRow> findByTenantIdAndJobIdAndValidationStatus(
            UUID tenantId, UUID jobId, UserTransferValidationStatus validationStatus, Pageable pageable);

    @Query("""
            select row from UserTransferJobRow row
            where row.tenantId = :tenantId
              and row.jobId = :jobId
              and (:validationStatus is null or row.validationStatus = :validationStatus)
              and (:executionStatus is null or row.executionStatus = :executionStatus)
            """)
    Page<UserTransferJobRow> findFiltered(
            @Param("tenantId") UUID tenantId,
            @Param("jobId") UUID jobId,
            @Param("validationStatus") UserTransferValidationStatus validationStatus,
            @Param("executionStatus") UserTransferExecutionStatus executionStatus,
            Pageable pageable);

    @Query("""
            select row from UserTransferJobRow row
            where row.tenantId = :tenantId
              and row.jobId = :jobId
              and (row.validationStatus = com.pro.ereportbacken.model.pojo.UserTransferValidationStatus.INVALID
                   or row.executionStatus = com.pro.ereportbacken.model.pojo.UserTransferExecutionStatus.FAILED)
            """)
    Page<UserTransferJobRow> findFailures(
            @Param("tenantId") UUID tenantId,
            @Param("jobId") UUID jobId,
            Pageable pageable);

    @Modifying(flushAutomatically = true)
    @Query("delete from UserTransferJobRow row where row.tenantId = :tenantId and row.jobId = :jobId")
    int deleteAllByTenantIdAndJobId(
            @Param("tenantId") UUID tenantId,
            @Param("jobId") UUID jobId);

    List<UserTransferJobRow> findByTenantIdAndJobIdAndSubjectKeyHash(
            UUID tenantId, UUID jobId, String subjectKeyHash);
}
