package com.pro.ereportbacken.service;

import com.pro.ereportbacken.model.dto.DepartmentCreateRequestDTO;
import com.pro.ereportbacken.model.dto.DepartmentMergeRequestDTO;
import com.pro.ereportbacken.model.dto.DepartmentMoveRequestDTO;
import com.pro.ereportbacken.model.pojo.AssignmentSlot;
import com.pro.ereportbacken.model.pojo.AssignmentState;
import com.pro.ereportbacken.model.pojo.AssignmentType;
import com.pro.ereportbacken.model.pojo.Department;
import com.pro.ereportbacken.model.pojo.DepartmentStatus;
import com.pro.ereportbacken.model.pojo.User;
import com.pro.ereportbacken.model.pojo.WorkOrder;
import com.pro.ereportbacken.model.pojo.WorkOrderAssignment;
import com.pro.ereportbacken.model.pojo.WorkOrderPriority;
import com.pro.ereportbacken.model.pojo.WorkOrderStatus;
import com.pro.ereportbacken.model.pojo.WorkOrderType;
import com.pro.ereportbacken.repository.DepartmentChangeEventRepository;
import com.pro.ereportbacken.repository.DepartmentRepository;
import com.pro.ereportbacken.repository.UserRepository;
import com.pro.ereportbacken.repository.WorkOrderAssignmentRepository;
import com.pro.ereportbacken.repository.WorkOrderDepartmentParticipationRepository;
import com.pro.ereportbacken.repository.WorkOrderRepository;
import com.pro.ereportbacken.security.TenantContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DepartmentLifecycleIntegrationTests {

    @Autowired
    private DepartmentService departmentService;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private DepartmentChangeEventRepository eventRepository;

    @Autowired
    private DepartmentReferenceMigrationService referenceMigrationService;

    @Autowired
    private WorkOrderDepartmentParticipationService participationService;

    @Autowired
    private WorkOrderDepartmentParticipationRepository participationRepository;

    @Autowired
    private WorkOrderRepository workOrderRepository;

    @Autowired
    private WorkOrderAssignmentRepository assignmentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DefaultTenantService defaultTenantService;

    @Autowired
    private TenantContext tenantContext;

    @Test
    void mergeMovesUsersWorkOrdersAssignmentHistoryAndChildDepartmentsAtomically() {
        UUID tenantId = defaultTenantService.requireDefaultTenant().getId();
        User reporter = userRepository.findByTenantIdAndUsername(tenantId, "nanjing").orElseThrow();
        UUID orgUnitId = reporter.getOrgUnitId();

        tenantContext.runInTenant(tenantId, () -> {
            Department source = create(orgUnitId, null, "SOURCE", "铁通维护单位");
            Department sourceTeam = create(orgUnitId, source.getId(), "SOURCE_TEAM", "铁通乡镇班组");
            Department target = create(orgUnitId, null, "TARGET", "驻地网维护单位");
            reporter.setDepartmentId(source.getId());
            long authorizationVersion = reporter.getAuthorizationVersion();
            userRepository.saveAndFlush(reporter);

            WorkOrder workOrder = workOrder(reporter);
            long workOrderVersionBeforeMigration = workOrder.getVersion();
            participationService.recordSubmitted(workOrder, reporter);
            WorkOrderAssignment assignment = assignment(workOrder, reporter, source.getId());
            workOrder.setCurrentAssignment(assignment);
            workOrderRepository.saveAndFlush(workOrder);

            var preview = departmentService.previewMerge(source.getId(),
                    new DepartmentMergeRequestDTO(target.getId(), "维护职责整合"));
            assertThat(preview.affectedUserCount()).isEqualTo(1);
            assertThat(preview.affectedWorkOrderCount()).isEqualTo(1);
            assertThat(preview.directChildDepartmentCount()).isEqualTo(1);

            departmentService.merge(source.getId(),
                    new DepartmentMergeRequestDTO(target.getId(), "维护职责整合"));

            User movedReporter = userRepository.findById(reporter.getId()).orElseThrow();
            WorkOrder movedWorkOrder = workOrderRepository.findByTenantIdAndId(tenantId, workOrder.getId()).orElseThrow();
            WorkOrderAssignment movedAssignment = assignmentRepository.findById(assignment.getId()).orElseThrow();
            Department movedChild = departmentRepository.findById(sourceTeam.getId()).orElseThrow();
            Department mergedSource = departmentRepository.findById(source.getId()).orElseThrow();

            assertThat(movedReporter.getDepartmentId()).isEqualTo(target.getId());
            assertThat(movedReporter.getAuthorizationVersion()).isEqualTo(authorizationVersion + 1);
            assertThat(movedWorkOrder.getReportingDepartmentId()).isEqualTo(target.getId());
            assertThat(movedWorkOrder.getResponsibleDepartmentId()).isEqualTo(target.getId());
            assertThat(movedWorkOrder.getVersion()).isGreaterThan(workOrderVersionBeforeMigration);
            assertThat(movedAssignment.getResponsibleDepartmentId()).isEqualTo(target.getId());
            assertThat(participationRepository.findByTenantIdAndWorkOrderIdOrderByStartedAtAsc(
                    tenantId, workOrder.getId()))
                    .allSatisfy(participation -> {
                        assertThat(participation.getDepartmentId()).isEqualTo(target.getId());
                        assertThat(participation.getDepartmentNameSnapshot()).isEqualTo(target.getName());
                    });
            assertThat(movedChild.getParentId()).isEqualTo(target.getId());
            assertThat(movedChild.getPath()).startsWith(target.getPath());
            assertThat(mergedSource.getStatus()).isEqualTo(DepartmentStatus.MERGED);
            assertThat(mergedSource.getMergedIntoDepartmentId()).isEqualTo(target.getId());
            assertThat(eventRepository.findAll())
                    .anySatisfy(event -> {
                        assertThat(event.getDepartmentId()).isEqualTo(source.getId());
                        assertThat(event.getAffectedUsers()).isEqualTo(1);
                        assertThat(event.getAffectedWorkOrders()).isEqualTo(1);
                    });
        });
    }

    @Test
    void deleteFallsBackToParentAndMoveRejectsDepartmentTreeCycle() {
        UUID tenantId = defaultTenantService.requireDefaultTenant().getId();
        User reporter = userRepository.findByTenantIdAndUsername(tenantId, "nanjing").orElseThrow();
        UUID orgUnitId = reporter.getOrgUnitId();

        tenantContext.runInTenant(tenantId, () -> {
            Department parent = create(orgUnitId, null, "PARENT", "维护调度中心");
            Department source = create(orgUnitId, parent.getId(), "DELETE_ME", "临时维护班组");
            Department child = create(orgUnitId, source.getId(), "CHILD", "下属小组");
            reporter.setDepartmentId(source.getId());
            userRepository.saveAndFlush(reporter);
            WorkOrder workOrder = workOrder(reporter);
            participationService.recordSubmitted(workOrder, reporter);

            assertThatThrownBy(() -> departmentService.move(parent.getId(),
                    new DepartmentMoveRequestDTO(child.getId(), "制造部门树环")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("下级部门");

            departmentService.disable(source.getId(), "临时班组撤销");
            var preview = departmentService.previewDelete(source.getId());
            assertThat(preview.fallbackDepartmentId()).isEqualTo(parent.getId());
            assertThat(preview.affectedUserCount()).isEqualTo(1);
            assertThat(preview.affectedWorkOrderCount()).isEqualTo(1);

            departmentService.delete(source.getId(), "临时班组撤销");

            assertThat(departmentRepository.findById(source.getId())).isEmpty();
            assertThat(userRepository.findById(reporter.getId()).orElseThrow().getDepartmentId())
                    .isEqualTo(parent.getId());
            WorkOrder migrated = workOrderRepository.findByTenantIdAndId(tenantId, workOrder.getId()).orElseThrow();
            assertThat(migrated.getReportingDepartmentId()).isEqualTo(parent.getId());
            assertThat(migrated.getResponsibleDepartmentId()).isEqualTo(parent.getId());
            Department reparentedChild = departmentRepository.findById(child.getId()).orElseThrow();
            assertThat(reparentedChild.getParentId()).isEqualTo(parent.getId());
            assertThat(reparentedChild.getPath()).startsWith(parent.getPath());
        });
    }

    private Department create(UUID orgUnitId, UUID parentId, String code, String name) {
        var dto = departmentService.create(new DepartmentCreateRequestDTO(orgUnitId, parentId, code, name));
        return departmentRepository.findById(dto.id()).orElseThrow();
    }

    private WorkOrder workOrder(User reporter) {
        WorkOrder workOrder = new WorkOrder();
        workOrder.setTenantId(reporter.getTenantId());
        workOrder.setReportingOrgId(reporter.getOrgUnitId());
        workOrder.setResponsibleOrgId(reporter.getOrgUnitId());
        workOrder.setWorkOrderNo("DEPT-" + UUID.randomUUID());
        workOrder.setLocation("部门迁移测试地点");
        workOrder.setReason("验证部门引用迁移");
        workOrder.setPhone(reporter.getPhone() == null ? "13900000000" : reporter.getPhone());
        workOrder.setContactPerson(reporter.getName() == null ? reporter.getUsername() : reporter.getName());
        workOrder.setReportType(WorkOrderType.NETWORK_FAULT);
        workOrder.setPriority(WorkOrderPriority.ROUTINE);
        workOrder.setSubmittedAt(LocalDateTime.now());
        workOrder.transitionTo(WorkOrderStatus.SUBMITTED);
        workOrder.setReportedBy(reporter);
        return workOrderRepository.saveAndFlush(workOrder);
    }

    private WorkOrderAssignment assignment(WorkOrder workOrder, User assignee, UUID departmentId) {
        WorkOrderAssignment assignment = new WorkOrderAssignment();
        assignment.setTenantId(workOrder.getTenantId());
        assignment.setWorkOrder(workOrder);
        assignment.setAssignee(assignee);
        assignment.setResponsibleOrgId(workOrder.getResponsibleOrgId());
        assignment.setResponsibleDepartmentId(departmentId);
        assignment.setAssignedBy(assignee);
        assignment.setAssignmentType(AssignmentType.CLAIM);
        assignment.setState(AssignmentState.ACCEPTED);
        assignment.setActiveSlot(AssignmentSlot.CURRENT);
        assignment.setAssignedAt(LocalDateTime.now());
        assignment.setAcceptedAt(assignment.getAssignedAt());
        return assignmentRepository.saveAndFlush(assignment);
    }
}
