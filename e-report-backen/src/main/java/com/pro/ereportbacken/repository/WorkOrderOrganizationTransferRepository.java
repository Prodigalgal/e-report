package com.pro.ereportbacken.repository;

import com.pro.ereportbacken.model.pojo.WorkOrderOrganizationTransfer;
import com.pro.ereportbacken.model.pojo.WorkOrderOrganizationTransferStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkOrderOrganizationTransferRepository
        extends JpaRepository<WorkOrderOrganizationTransfer, UUID> {

    List<WorkOrderOrganizationTransfer> findAllByTenantIdAndWorkOrderIdAndStatusOrderByRequestedAtAsc(
            UUID tenantId, UUID workOrderId, WorkOrderOrganizationTransferStatus status);

    List<WorkOrderOrganizationTransfer> findByTenantIdAndWorkOrderIdOrderByRequestedAtDesc(
            UUID tenantId, UUID workOrderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT transfer FROM WorkOrderOrganizationTransfer transfer
            WHERE transfer.tenantId = :tenantId
              AND transfer.workOrderId = :workOrderId
              AND transfer.id = :transferId
            """)
    Optional<WorkOrderOrganizationTransfer> findForUpdate(
            @Param("tenantId") UUID tenantId,
            @Param("workOrderId") UUID workOrderId,
            @Param("transferId") UUID transferId);
}
