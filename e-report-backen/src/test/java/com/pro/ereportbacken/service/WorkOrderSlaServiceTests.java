package com.pro.ereportbacken.service;

import com.pro.ereportbacken.model.pojo.WorkOrder;
import com.pro.ereportbacken.model.pojo.WorkOrderPriority;
import com.pro.ereportbacken.model.dto.WorkOrderSlaPolicyDTO;
import com.pro.ereportbacken.model.pojo.SlaRiskLevel;
import com.pro.ereportbacken.model.pojo.WorkOrderSlaStage;
import com.pro.ereportbacken.model.pojo.WorkOrderType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorkOrderSlaServiceTests {
    private final WorkOrderSlaPolicyService policyService = mock(WorkOrderSlaPolicyService.class);
    private final WorkOrderSlaService slaService = new WorkOrderSlaService(policyService);
    private WorkOrder workOrder;

    @BeforeEach
    void setUp() {
        WorkOrderSlaPolicyDTO policy = new WorkOrderSlaPolicyDTO(
                com.pro.ereportbacken.TestIds.uuid(2),
                WorkOrderType.NETWORK_FAULT,
                WorkOrderType.NETWORK_FAULT.getDisplayName(),
                WorkOrderPriority.ROUTINE,
                WorkOrderPriority.ROUTINE.getDisplayName(),
                120,
                60,
                1440,
                480,
                720,
                true,
                1440,
                4320,
                LocalDateTime.of(2026, 8, 6, 0, 0)
        );
        when(policyService.requirePolicy(com.pro.ereportbacken.TestIds.uuid(1), WorkOrderType.NETWORK_FAULT, WorkOrderPriority.ROUTINE))
                .thenReturn(policy);

        workOrder = new WorkOrder();
        workOrder.setTenantId(com.pro.ereportbacken.TestIds.uuid(1));
        workOrder.setReportType(WorkOrderType.NETWORK_FAULT);
        workOrder.setPriority(WorkOrderPriority.ROUTINE);
    }

    @Test
    void advancesStageUsingPolicySnapshotAtEachTransition() {
        LocalDateTime submittedAt = LocalDateTime.of(2026, 8, 6, 8, 0);
        workOrder.setSubmittedAt(submittedAt);

        slaService.initialize(workOrder, submittedAt);
        assertThat(workOrder.getSlaStage()).isEqualTo(WorkOrderSlaStage.CLAIM);
        assertThat(workOrder.getSlaDueAt()).isEqualTo(submittedAt.plusMinutes(120));

        LocalDateTime assignedAt = submittedAt.plusMinutes(5);
        slaService.startAssignmentAcceptance(workOrder, assignedAt);
        assertThat(workOrder.getSlaStage()).isEqualTo(WorkOrderSlaStage.ASSIGNMENT_ACCEPTANCE);
        assertThat(workOrder.getSlaDueAt()).isEqualTo(assignedAt.plusMinutes(60));
        assertThat(workOrder.getSlaResumeStage()).isEqualTo(WorkOrderSlaStage.CLAIM);
        assertThat(workOrder.getSlaResumeDueAt()).isEqualTo(submittedAt.plusMinutes(120));

        LocalDateTime acceptedAt = submittedAt.plusMinutes(10);
        slaService.finishAssignmentAcceptance(workOrder, acceptedAt);
        assertThat(workOrder.getSlaStage()).isEqualTo(WorkOrderSlaStage.RESPONSE);
        assertThat(workOrder.getSlaDueAt()).isEqualTo(acceptedAt.plusMinutes(1440));
        assertThat(workOrder.getSlaResumeStage()).isNull();

        LocalDateTime respondedAt = acceptedAt.plusHours(2);
        slaService.startVerification(workOrder, respondedAt);
        assertThat(workOrder.getSlaStage()).isEqualTo(WorkOrderSlaStage.VERIFICATION);
        assertThat(workOrder.getSlaDueAt()).isEqualTo(respondedAt.plusMinutes(480));

        workOrder.setReworkCount(1);
        LocalDateTime rejectedAt = respondedAt.plusHours(1);
        slaService.startRework(workOrder, rejectedAt);
        assertThat(workOrder.getSlaStage()).isEqualTo(WorkOrderSlaStage.REWORK);
        assertThat(workOrder.getSlaDueAt()).isEqualTo(rejectedAt.plusMinutes(720));

        slaService.complete(workOrder);
        assertThat(workOrder.getSlaStage()).isEqualTo(WorkOrderSlaStage.COMPLETED);
        assertThat(workOrder.getSlaDueAt()).isNull();
    }

    @Test
    void preservesInterruptedStageDeadlineAndBreachStateAcrossAssignmentDecision() {
        LocalDateTime startedAt = LocalDateTime.of(2026, 8, 6, 8, 0);
        workOrder.setSubmittedAt(startedAt);
        slaService.initialize(workOrder, startedAt);
        slaService.startProcessing(workOrder, startedAt.plusMinutes(10));
        LocalDateTime originalDueAt = workOrder.getSlaDueAt();
        LocalDateTime originalBreachedAt = startedAt.plusMinutes(20);
        workOrder.setSlaBreachedAt(originalBreachedAt);

        slaService.startAssignmentAcceptance(workOrder, startedAt.plusMinutes(30));
        assertThat(workOrder.getSlaResumeStage()).isEqualTo(WorkOrderSlaStage.RESPONSE);
        assertThat(workOrder.getSlaResumeDueAt()).isEqualTo(originalDueAt);
        assertThat(workOrder.getSlaResumeBreachedAt()).isEqualTo(originalBreachedAt);
        assertThat(workOrder.getSlaBreachedAt()).isNull();

        slaService.rejectAssignment(workOrder, startedAt.plusMinutes(40));
        assertThat(workOrder.getSlaStage()).isEqualTo(WorkOrderSlaStage.RESPONSE);
        assertThat(workOrder.getSlaDueAt()).isEqualTo(originalDueAt);
        assertThat(workOrder.getSlaBreachedAt()).isEqualTo(originalBreachedAt);
        assertThat(workOrder.getSlaResumeStage()).isNull();

        workOrder.setSlaBreachedAt(null);
        slaService.startAssignmentAcceptance(workOrder, startedAt.plusMinutes(50));
        slaService.finishAssignmentAcceptance(workOrder, startedAt.plusMinutes(60));
        assertThat(workOrder.getSlaStage()).isEqualTo(WorkOrderSlaStage.RESPONSE);
        assertThat(workOrder.getSlaDueAt()).isEqualTo(originalDueAt);
        assertThat(workOrder.getSlaResumeDueAt()).isNull();
    }

    @Test
    void capsSingleHoldAndReportsBreachAgainstEffectiveDueTime() {
        LocalDateTime submittedAt = LocalDateTime.of(2026, 8, 6, 8, 0);
        workOrder.setSubmittedAt(submittedAt);
        slaService.initialize(workOrder, submittedAt);
        slaService.startProcessing(workOrder, submittedAt);
        LocalDateTime originalDueAt = workOrder.getSlaDueAt();

        slaService.pause(workOrder, submittedAt.plusHours(1));
        WorkOrderSlaService.SlaSnapshot paused = slaService.snapshot(workOrder, submittedAt.plusHours(30));
        assertThat(paused.paused()).isTrue();
        assertThat(paused.riskLevel()).isEqualTo(SlaRiskLevel.PAUSED);
        assertThat(paused.dueAt()).isEqualTo(originalDueAt.plusHours(24));

        slaService.resume(workOrder, submittedAt.plusHours(30));
        assertThat(workOrder.getTotalHoldSeconds()).isEqualTo(24 * 60 * 60L);
        assertThat(workOrder.getSlaDueAt()).isEqualTo(originalDueAt.plusHours(24));

        WorkOrderSlaService.SlaSnapshot breached = slaService.snapshot(workOrder, originalDueAt.plusHours(25));
        assertThat(breached.breached()).isTrue();
        assertThat(breached.riskLevel()).isEqualTo(SlaRiskLevel.BREACHED);
        assertThat(breached.breachSeconds()).isEqualTo(3600L);
    }

    @Test
    void classifiesRiskAtTwentyPercentBoundaryAndTerminalState() {
        LocalDateTime submittedAt = LocalDateTime.of(2026, 8, 6, 8, 0);
        workOrder.setSubmittedAt(submittedAt);
        slaService.initialize(workOrder, submittedAt);

        LocalDateTime dueAt = workOrder.getSlaDueAt();
        assertThat(slaService.snapshot(workOrder, dueAt.minusMinutes(24).minusSeconds(1)).riskLevel())
                .isEqualTo(SlaRiskLevel.NORMAL);
        assertThat(slaService.snapshot(workOrder, dueAt.minusMinutes(24)).riskLevel())
                .isEqualTo(SlaRiskLevel.DUE_SOON);
        assertThat(slaService.snapshot(workOrder, dueAt).riskLevel())
                .isEqualTo(SlaRiskLevel.BREACHED);
        assertThat(slaService.snapshot(workOrder, dueAt.plusSeconds(1)).riskLevel())
                .isEqualTo(SlaRiskLevel.BREACHED);

        slaService.complete(workOrder);
        assertThat(slaService.snapshot(workOrder, dueAt.plusHours(1)).riskLevel())
                .isEqualTo(SlaRiskLevel.COMPLETED);
    }
}
