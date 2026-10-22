package com.pro.ereportbacken.repository;

import java.util.UUID;

import com.pro.ereportbacken.model.pojo.WorkOrderPriority;
import com.pro.ereportbacken.model.pojo.WorkOrderSlaPolicy;
import com.pro.ereportbacken.model.pojo.WorkOrderType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorkOrderSlaPolicyRepository extends JpaRepository<WorkOrderSlaPolicy, UUID> {
    Optional<WorkOrderSlaPolicy> findByTenantIdAndReportTypeAndPriority(
            UUID tenantId,
            WorkOrderType reportType,
            WorkOrderPriority priority
    );

    List<WorkOrderSlaPolicy> findByTenantIdOrderByReportTypeAscPriorityAsc(UUID tenantId);
}


