package com.pro.ereportbacken.service;

import com.pro.ereportbacken.model.pojo.Department;
import com.pro.ereportbacken.model.pojo.DepartmentStatus;
import com.pro.ereportbacken.model.pojo.User;
import com.pro.ereportbacken.model.pojo.WorkOrder;
import com.pro.ereportbacken.model.pojo.WorkOrderDepartmentRelationType;
import com.pro.ereportbacken.repository.DepartmentRepository;
import com.pro.ereportbacken.repository.WorkOrderDepartmentParticipationRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkOrderDepartmentParticipationServiceTests {

    private final DepartmentRepository departmentRepository = mock(DepartmentRepository.class);
    private final WorkOrderDepartmentParticipationRepository participationRepository =
            mock(WorkOrderDepartmentParticipationRepository.class);
    private final WorkOrderDepartmentParticipationService service =
            new WorkOrderDepartmentParticipationService(departmentRepository, participationRepository);

    @Test
    void allowsClaimAcrossActiveSiblingDepartmentsInSameOrganization() {
        UUID tenantId = UUID.randomUUID();
        UUID orgUnitId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        Department responsible = department(UUID.randomUUID(), tenantId, orgUnitId, parentId, DepartmentStatus.ACTIVE);
        Department memberDepartment = department(UUID.randomUUID(), tenantId, orgUnitId, parentId, DepartmentStatus.ACTIVE);
        User member = user(tenantId, orgUnitId, memberDepartment.getId());
        when(departmentRepository.findByTenantIdAndId(tenantId, responsible.getId()))
                .thenReturn(Optional.of(responsible));
        when(departmentRepository.findByTenantIdAndId(tenantId, memberDepartment.getId()))
                .thenReturn(Optional.of(memberDepartment));

        assertThat(service.canCollaborateForClaim(tenantId, responsible.getId(), member)).isTrue();
    }

    @Test
    void rejectsCrossOrganizationChildAndInactiveDepartments() {
        UUID tenantId = UUID.randomUUID();
        UUID responsibleOrg = UUID.randomUUID();
        UUID memberOrg = UUID.randomUUID();
        Department responsible = department(UUID.randomUUID(), tenantId, responsibleOrg, null, DepartmentStatus.ACTIVE);
        Department crossOrganization = department(UUID.randomUUID(), tenantId, memberOrg, null, DepartmentStatus.ACTIVE);
        User member = user(tenantId, memberOrg, crossOrganization.getId());
        when(departmentRepository.findByTenantIdAndId(tenantId, responsible.getId()))
                .thenReturn(Optional.of(responsible));
        when(departmentRepository.findByTenantIdAndId(tenantId, crossOrganization.getId()))
                .thenReturn(Optional.of(crossOrganization));

        assertThat(service.canCollaborateForClaim(tenantId, responsible.getId(), member)).isFalse();

        Department child = department(UUID.randomUUID(), tenantId, responsibleOrg, responsible.getId(), DepartmentStatus.ACTIVE);
        member = user(tenantId, responsibleOrg, child.getId());
        when(departmentRepository.findByTenantIdAndId(tenantId, child.getId())).thenReturn(Optional.of(child));
        assertThat(service.canCollaborateForClaim(tenantId, responsible.getId(), member)).isFalse();

        child.setStatus(DepartmentStatus.MERGED);
        assertThat(service.canCollaborateForClaim(tenantId, responsible.getId(), member)).isFalse();
    }

    @Test
    void recordsProcessingDepartmentWithoutChangingResponsibleDepartment() {
        UUID tenantId = UUID.randomUUID();
        UUID orgUnitId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        Department processingDepartment = department(departmentId, tenantId, orgUnitId, null, DepartmentStatus.ACTIVE);
        WorkOrder workOrder = new WorkOrder();
        workOrder.setId(UUID.randomUUID());
        workOrder.setTenantId(tenantId);
        workOrder.setResponsibleOrgId(orgUnitId);

        when(departmentRepository.findByTenantIdAndId(tenantId, departmentId))
                .thenReturn(Optional.of(processingDepartment));

        service.recordProcessingDepartment(workOrder, departmentId, UUID.randomUUID(), "同级部门协作接单");

        verify(participationRepository).endActiveRelation(
                eq(tenantId), eq(workOrder.getId()), eq(WorkOrderDepartmentRelationType.PROCESSING), any());
        verify(participationRepository).save(any());
        assertThat(workOrder.getResponsibleDepartmentId()).isNull();
    }

    private static Department department(UUID id, UUID tenantId, UUID orgUnitId, UUID parentId, DepartmentStatus status) {
        Department department = new Department();
        department.setId(id);
        department.setTenantId(tenantId);
        department.setOrgUnitId(orgUnitId);
        department.setParentId(parentId);
        department.setStatus(status);
        department.setPath(parentId == null ? "/" + id + "/" : "/" + parentId + "/" + id + "/");
        department.setName("测试部门");
        return department;
    }

    private static User user(UUID tenantId, UUID orgUnitId, UUID departmentId) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setTenantId(tenantId);
        user.setOrgUnitId(orgUnitId);
        user.setDepartmentId(departmentId);
        return user;
    }
}
