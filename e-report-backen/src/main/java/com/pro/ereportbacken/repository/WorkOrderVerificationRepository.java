package com.pro.ereportbacken.repository;

import java.util.UUID;

import com.pro.ereportbacken.model.pojo.WorkOrderVerification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorkOrderVerificationRepository extends JpaRepository<WorkOrderVerification, UUID> {
    Optional<WorkOrderVerification> findByTenantIdAndResponseId(UUID tenantId, UUID responseId);

    @EntityGraph(attributePaths = {"verifiedBy", "response"})
    List<WorkOrderVerification> findByTenantIdAndWorkOrderIdOrderByVerifiedAtAsc(UUID tenantId, UUID workOrderId);
}


