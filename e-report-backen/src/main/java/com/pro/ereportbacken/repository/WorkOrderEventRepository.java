package com.pro.ereportbacken.repository;

import java.util.UUID;

import com.pro.ereportbacken.model.pojo.WorkOrderEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkOrderEventRepository extends JpaRepository<WorkOrderEvent, UUID> {
    Page<WorkOrderEvent> findByTenantIdAndWorkOrderIdOrderByOccurredAtDesc(UUID tenantId, UUID workOrderId, Pageable pageable);
}


