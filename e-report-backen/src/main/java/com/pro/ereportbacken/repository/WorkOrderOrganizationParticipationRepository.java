package com.pro.ereportbacken.repository;

import com.pro.ereportbacken.model.pojo.WorkOrderOrganizationParticipation;
import com.pro.ereportbacken.model.pojo.WorkOrderOrganizationRelationType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface WorkOrderOrganizationParticipationRepository
        extends JpaRepository<WorkOrderOrganizationParticipation, UUID> {

    List<WorkOrderOrganizationParticipation> findByTenantIdAndWorkOrderIdOrderByStartedAtAsc(
            UUID tenantId, UUID workOrderId);

    boolean existsByTenantIdAndWorkOrderIdAndOrgUnitIdInAndActiveTrue(
            UUID tenantId, UUID workOrderId, Collection<UUID> orgUnitIds);

    boolean existsByTenantIdAndWorkOrderIdAndOrgUnitIdAndRelationTypeAndActiveTrue(
            UUID tenantId, UUID workOrderId, UUID orgUnitId, WorkOrderOrganizationRelationType relationType);

    List<WorkOrderOrganizationParticipation>
    findByTenantIdAndWorkOrderIdAndRelationTypeAndActiveTrueOrderByStartedAtAsc(
            UUID tenantId, UUID workOrderId, WorkOrderOrganizationRelationType relationType);
}
