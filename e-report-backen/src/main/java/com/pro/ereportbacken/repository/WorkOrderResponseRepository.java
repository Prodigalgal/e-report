package com.pro.ereportbacken.repository;

import java.util.UUID;

import com.pro.ereportbacken.model.pojo.WorkOrderResponse;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorkOrderResponseRepository extends JpaRepository<WorkOrderResponse, UUID> {
    long countByTenantIdAndWorkOrderId(UUID tenantId, UUID workOrderId);

    @EntityGraph(attributePaths = {"submittedBy", "assignment", "assignment.assignee"})
    Optional<WorkOrderResponse> findByTenantIdAndWorkOrderIdAndId(UUID tenantId, UUID workOrderId, UUID id);

    @EntityGraph(attributePaths = {"submittedBy", "assignment", "assignment.assignee"})
    List<WorkOrderResponse> findByTenantIdAndWorkOrderIdOrderByAttemptNoAsc(UUID tenantId, UUID workOrderId);
}


