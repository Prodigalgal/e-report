package com.pro.ereportbacken.service;

import com.pro.ereportbacken.model.pojo.WorkOrder;
import com.pro.ereportbacken.model.pojo.WorkOrderPriority;
import com.pro.ereportbacken.model.dto.WorkOrderSlaPolicyDTO;
import com.pro.ereportbacken.model.pojo.SlaRiskLevel;
import com.pro.ereportbacken.model.pojo.WorkOrderSlaStage;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
public class WorkOrderSlaService {
    private static final int DUE_SOON_THRESHOLD_DIVISOR = 5;
    private final WorkOrderSlaPolicyService policyService;

    public WorkOrderSlaService(WorkOrderSlaPolicyService policyService) {
        this.policyService = policyService;
    }

    public void initialize(WorkOrder workOrder, LocalDateTime occurredAt) {
        workOrder.setPriority(priorityOf(workOrder));
        workOrder.setTotalHoldSeconds(0L);
        workOrder.setSlaEverBreached(false);
        enterStage(workOrder, WorkOrderSlaStage.CLAIM, occurredAt);
    }

    public void startClaim(WorkOrder workOrder, LocalDateTime occurredAt) {
        enterStage(workOrder, WorkOrderSlaStage.CLAIM, occurredAt);
    }

    public void startAssignmentAcceptance(WorkOrder workOrder, LocalDateTime occurredAt) {
        WorkOrderSlaStage currentStage = stageOf(workOrder);
        if (currentStage == WorkOrderSlaStage.CLAIM
                || currentStage == WorkOrderSlaStage.RESPONSE
                || currentStage == WorkOrderSlaStage.REWORK) {
            workOrder.setSlaResumeStage(currentStage);
            workOrder.setSlaResumeDueAt(workOrder.getSlaDueAt());
            workOrder.setSlaResumeBreachedAt(workOrder.getSlaBreachedAt());
        } else {
            clearResumeContext(workOrder);
        }
        enterStage(workOrder, WorkOrderSlaStage.ASSIGNMENT_ACCEPTANCE, occurredAt, false);
    }

    public void finishAssignmentAcceptance(WorkOrder workOrder, LocalDateTime occurredAt) {
        if (workOrder.getSlaResumeStage() == WorkOrderSlaStage.RESPONSE
                || workOrder.getSlaResumeStage() == WorkOrderSlaStage.REWORK) {
            restoreInterruptedStage(workOrder);
            return;
        }
        startProcessing(workOrder, occurredAt);
    }

    public void rejectAssignment(WorkOrder workOrder, LocalDateTime occurredAt) {
        if (workOrder.getSlaResumeStage() != null) {
            restoreInterruptedStage(workOrder);
            return;
        }
        startClaim(workOrder, occurredAt);
    }

    public void continueAfterImmediateReassignment(WorkOrder workOrder, LocalDateTime occurredAt) {
        if (stageOf(workOrder) == WorkOrderSlaStage.ASSIGNMENT_ACCEPTANCE) {
            finishAssignmentAcceptance(workOrder, occurredAt);
        } else if (stageOf(workOrder) == WorkOrderSlaStage.CLAIM) {
            startProcessing(workOrder, occurredAt);
        }
    }

    public void startProcessing(WorkOrder workOrder, LocalDateTime occurredAt) {
        enterStage(
                workOrder,
                reworkCount(workOrder) > 0 ? WorkOrderSlaStage.REWORK : WorkOrderSlaStage.RESPONSE,
                occurredAt
        );
    }

    public void startVerification(WorkOrder workOrder, LocalDateTime occurredAt) {
        enterStage(workOrder, WorkOrderSlaStage.VERIFICATION, occurredAt);
    }

    public void startRework(WorkOrder workOrder, LocalDateTime occurredAt) {
        enterStage(workOrder, WorkOrderSlaStage.REWORK, occurredAt);
    }

    public void complete(WorkOrder workOrder) {
        workOrder.setSlaStage(WorkOrderSlaStage.COMPLETED);
        workOrder.setSlaDueAt(null);
        workOrder.setSlaPausedAt(null);
        workOrder.setSlaBreachedAt(null);
        clearResumeContext(workOrder);
    }

    public void pause(WorkOrder workOrder, LocalDateTime occurredAt) {
        if (stageOf(workOrder) == WorkOrderSlaStage.COMPLETED || workOrder.getSlaPausedAt() != null) {
            return;
        }
        WorkOrderSlaPolicyDTO policy = policy(workOrder);
        if (Boolean.TRUE.equals(policy.pauseOnHold())) {
            workOrder.setSlaPausedAt(occurredAt);
        }
    }

    public void resume(WorkOrder workOrder, LocalDateTime occurredAt) {
        if (workOrder.getSlaPausedAt() == null) {
            return;
        }
        long allowedPauseSeconds = allowedPauseSeconds(workOrder, occurredAt);
        if (workOrder.getSlaDueAt() != null && allowedPauseSeconds > 0) {
            workOrder.setSlaDueAt(workOrder.getSlaDueAt().plusSeconds(allowedPauseSeconds));
        }
        if (workOrder.getSlaResumeDueAt() != null && allowedPauseSeconds > 0) {
            workOrder.setSlaResumeDueAt(workOrder.getSlaResumeDueAt().plusSeconds(allowedPauseSeconds));
        }
        workOrder.setTotalHoldSeconds(totalHoldSeconds(workOrder) + allowedPauseSeconds);
        workOrder.setSlaPausedAt(null);
    }

    public SlaSnapshot snapshot(WorkOrder workOrder, LocalDateTime now) {
        LocalDateTime effectiveDueAt = effectiveDueAt(workOrder, now);
        long remainingSeconds = effectiveDueAt == null ? 0L : Duration.between(now, effectiveDueAt).getSeconds();
        long breachSeconds = effectiveDueAt == null || now.isBefore(effectiveDueAt)
                ? 0L
                : Duration.between(effectiveDueAt, now).getSeconds();
        boolean deadlineReached = effectiveDueAt != null && !now.isBefore(effectiveDueAt);
        boolean breached = workOrder.getSlaBreachedAt() != null || deadlineReached;
        LocalDateTime elapsedEnd = terminalAt(workOrder, now);
        long elapsedSeconds = workOrder.getSubmittedAt() == null
                ? 0L
                : Math.max(0L, Duration.between(workOrder.getSubmittedAt(), elapsedEnd).getSeconds());
        WorkOrderPriority priority = priorityOf(workOrder);
        WorkOrderSlaStage stage = stageOf(workOrder);
        SlaRiskLevel riskLevel = riskLevel(workOrder, stage, remainingSeconds, breached);
        return new SlaSnapshot(
                priority,
                stage,
                riskLevel,
                effectiveDueAt,
                workOrder.getSlaPausedAt() != null,
                breached,
                Boolean.TRUE.equals(workOrder.getSlaEverBreached()) || breached,
                remainingSeconds,
                breachSeconds,
                elapsedSeconds,
                totalHoldSeconds(workOrder)
        );
    }

    public LocalDateTime effectiveDueAt(WorkOrder workOrder, LocalDateTime now) {
        if (workOrder.getSlaDueAt() == null) {
            return null;
        }
        if (workOrder.getSlaPausedAt() == null) {
            return workOrder.getSlaDueAt();
        }
        return workOrder.getSlaDueAt().plusSeconds(allowedPauseSeconds(workOrder, now));
    }

    private void enterStage(WorkOrder workOrder, WorkOrderSlaStage stage, LocalDateTime occurredAt) {
        enterStage(workOrder, stage, occurredAt, true);
    }

    private void enterStage(WorkOrder workOrder,
                            WorkOrderSlaStage stage,
                            LocalDateTime occurredAt,
                            boolean clearResumeContext) {
        WorkOrderSlaPolicyDTO policy = policy(workOrder);
        int minutes = switch (stage) {
            case CLAIM -> policy.claimMinutes();
            case ASSIGNMENT_ACCEPTANCE -> policy.assignmentAcceptanceMinutes();
            case RESPONSE -> policy.responseMinutes();
            case VERIFICATION -> policy.verificationMinutes();
            case REWORK -> policy.reworkMinutes();
            case COMPLETED -> 0;
        };
        workOrder.setSlaStage(stage);
        workOrder.setSlaDueAt(stage == WorkOrderSlaStage.COMPLETED ? null : occurredAt.plusMinutes(minutes));
        workOrder.setSlaPausedAt(null);
        workOrder.setSlaBreachedAt(null);
        if (clearResumeContext) {
            clearResumeContext(workOrder);
        }
    }

    private void restoreInterruptedStage(WorkOrder workOrder) {
        workOrder.setSlaStage(workOrder.getSlaResumeStage());
        workOrder.setSlaDueAt(workOrder.getSlaResumeDueAt());
        workOrder.setSlaBreachedAt(workOrder.getSlaResumeBreachedAt());
        clearResumeContext(workOrder);
    }

    private void clearResumeContext(WorkOrder workOrder) {
        workOrder.setSlaResumeStage(null);
        workOrder.setSlaResumeDueAt(null);
        workOrder.setSlaResumeBreachedAt(null);
    }

    private long allowedPauseSeconds(WorkOrder workOrder, LocalDateTime now) {
        WorkOrderSlaPolicyDTO policy = policy(workOrder);
        long requested = Math.max(0L, Duration.between(workOrder.getSlaPausedAt(), now).getSeconds());
        long singleLimit = Duration.ofMinutes(policy.maxSingleHoldMinutes()).getSeconds();
        long remainingTotal = Math.max(0L,
                Duration.ofMinutes(policy.maxTotalHoldMinutes()).getSeconds() - totalHoldSeconds(workOrder));
        return Math.min(requested, Math.min(singleLimit, remainingTotal));
    }

    private WorkOrderSlaPolicyDTO policy(WorkOrder workOrder) {
        return policyService.requirePolicy(workOrder.getTenantId(), workOrder.type(), priorityOf(workOrder));
    }

    private WorkOrderPriority priorityOf(WorkOrder workOrder) {
        return workOrder.getPriority() != null ? workOrder.getPriority() : WorkOrderPriority.ROUTINE;
    }

    private SlaRiskLevel riskLevel(WorkOrder workOrder,
                                   WorkOrderSlaStage stage,
                                   long remainingSeconds,
                                   boolean breached) {
        if (stage == WorkOrderSlaStage.COMPLETED) {
            return SlaRiskLevel.COMPLETED;
        }
        if (workOrder.getSlaPausedAt() != null) {
            return SlaRiskLevel.PAUSED;
        }
        if (breached) {
            return SlaRiskLevel.BREACHED;
        }
        if (workOrder.getSlaDueAt() == null || remainingSeconds <= 0) {
            return SlaRiskLevel.NORMAL;
        }
        long budgetSeconds = Duration.ofMinutes(stageBudgetMinutes(policy(workOrder), stage)).getSeconds();
        long dueSoonThresholdSeconds = Math.max(1L,
                (budgetSeconds + DUE_SOON_THRESHOLD_DIVISOR - 1) / DUE_SOON_THRESHOLD_DIVISOR);
        return remainingSeconds <= dueSoonThresholdSeconds ? SlaRiskLevel.DUE_SOON : SlaRiskLevel.NORMAL;
    }

    private int stageBudgetMinutes(WorkOrderSlaPolicyDTO policy, WorkOrderSlaStage stage) {
        return switch (stage) {
            case CLAIM -> policy.claimMinutes();
            case ASSIGNMENT_ACCEPTANCE -> policy.assignmentAcceptanceMinutes();
            case RESPONSE -> policy.responseMinutes();
            case VERIFICATION -> policy.verificationMinutes();
            case REWORK -> policy.reworkMinutes();
            case COMPLETED -> 0;
        };
    }

    private WorkOrderSlaStage stageOf(WorkOrder workOrder) {
        return workOrder.getSlaStage() != null ? workOrder.getSlaStage() : WorkOrderSlaStage.CLAIM;
    }

    private int reworkCount(WorkOrder workOrder) {
        return workOrder.getReworkCount() != null ? workOrder.getReworkCount() : 0;
    }

    private long totalHoldSeconds(WorkOrder workOrder) {
        return workOrder.getTotalHoldSeconds() != null ? workOrder.getTotalHoldSeconds() : 0L;
    }

    private LocalDateTime terminalAt(WorkOrder workOrder, LocalDateTime fallback) {
        if (workOrder.getClosedAt() != null) {
            return workOrder.getClosedAt();
        }
        if (workOrder.getCancelledAt() != null) {
            return workOrder.getCancelledAt();
        }
        return fallback;
    }

    public record SlaSnapshot(
            WorkOrderPriority priority,
            WorkOrderSlaStage stage,
            SlaRiskLevel riskLevel,
            LocalDateTime dueAt,
            boolean paused,
            boolean breached,
            boolean everBreached,
            long remainingSeconds,
            long breachSeconds,
            long elapsedSeconds,
            long totalHoldSeconds
    ) {
    }
}
